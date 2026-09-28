import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import CoachPage from "@/app/coach/page";
import { historyForModel, resetCoach } from "@/lib/ai/coach-chat";
import { todayKey } from "@/lib/core/dates";
import type { CalorieProfile, MealDTO, WeightDTO } from "@/lib/core/types";
import { formatTemplate, t } from "@/i18n";

/**
 * Requisitos de la pestaña «Coach»:
 *  - sin IA configurada, lleva a Ajustes,
 *  - las preguntas de ejemplo se envían con un toque; la respuesta llega por trozos,
 *  - cada pregunta lleva un resumen de tus datos (perfil, comidas, peso) si
 *    «El coach puede ver mis datos» está activado, y ninguno si no,
 *  - la conversación sigue (historial en memoria) hasta «Nueva conversación»,
 *  - los errores se explican y no se reenvían como historial,
 *  - se le pueden mandar fotos (con o sin texto); viajan solo al proveedor y,
 *    en el historial, solo las más recientes.
 * `fetch` está simulado: nunca se llama a un proveedor real.
 */

const profile: CalorieProfile = {
  gender: "female",
  birthYear: 1992,
  heightCm: 165,
  gymDaysPerWeek: 3,
  gymSessionMinutes: 60,
  walkingMinutesPerDay: 30,
  calorieGoal: "cut",
};

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {},
  api: {
    session: vi.fn(async () => ({ username: "ana", isAdmin: false, calorieProfile: profile })),
    listMeals: vi.fn(async () => [
      {
        id: "m1",
        logDate: todayKey(),
        title: "Desayuno",
        ingredients: [{ name: "Avena", quantity: "60 g", calories: 230 }],
        resolvedCalories: 380,
        resolvedProtein: 20,
        resolvedCarbs: 50,
        resolvedFat: 9,
      } as unknown as MealDTO,
    ]),
    listWeights: vi.fn(async () => [
      { id: "w1", measuredAt: new Date().toISOString(), weightKg: 62, bodyFatPct: null } as unknown as WeightDTO,
    ]),
  },
}));

vi.mock("@/lib/ai/images", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/ai/images")>()),
  // jsdom has no canvas: the photo arrives already reduced.
  downscalePhoto: vi.fn(async () => ({ mimeType: "image/jpeg", data: "Zm90bw==" })),
}));

import { api } from "@/lib/api";

const fetchMock = vi.fn<typeof fetch>();

function sse(pieces: string[]): Response {
  const encoder = new TextEncoder();
  return new Response(
    new ReadableStream({
      start(controller) {
        for (const piece of pieces) {
          controller.enqueue(
            encoder.encode(`data: ${JSON.stringify({ candidates: [{ content: { parts: [{ text: piece }] } }] })}\n\n`),
          );
        }
        controller.close();
      },
    }),
  );
}

function configure(extra: Record<string, unknown> = {}) {
  localStorage.setItem("bw:ai", JSON.stringify({ provider: "gemini", apiKeys: { gemini: "fake-key" }, ...extra }));
}

function requestBody(call: number) {
  return JSON.parse(String(fetchMock.mock.calls[call][1]?.body));
}

describe("Coach", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    resetCoach();
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("sin IA configurada lleva a Ajustes", () => {
    render(<CoachPage />);
    expect(screen.getByText(t.coach.notConfigured)).toBeInTheDocument();
    expect(screen.getByText(t.coach.configure).closest("a")).toHaveAttribute("href", "/ajustes/ia");
  });

  it("envía una pregunta de ejemplo con tus datos y muestra la respuesta por trozos", async () => {
    configure();
    fetchMock.mockResolvedValueOnce(sse(["Con tu tendencia, ", "**61,4 kg** en 30 días."]));
    const user = userEvent.setup();
    render(<CoachPage />);

    await user.click(screen.getByRole("button", { name: t.coach.examples[0] }));
    const list = await screen.findByRole("list", { name: t.coach.title });
    expect(within(list).getByText(t.coach.examples[0])).toBeInTheDocument();
    expect(await within(list).findByText("61,4 kg")).toBeInTheDocument();
    expect(within(list).getByText("61,4 kg").tagName).toBe("STRONG");

    const [url] = fetchMock.mock.calls[0];
    expect(String(url)).toContain(":streamGenerateContent?alt=sse");
    const body = requestBody(0);
    const system = body.systemInstruction.parts[0].text as string;
    expect(system).toContain("Always answer in Spanish.");
    expect(system).toContain("USER DATA");
    expect(system).toContain("Desayuno");
    expect(body.contents).toEqual([{ role: "user", parts: [{ text: t.coach.examples[0] }] }]);
  });

  it("recuerda la conversación hasta «Nueva conversación»", async () => {
    configure();
    fetchMock.mockResolvedValueOnce(sse(["Una ensalada con pollo."])).mockResolvedValueOnce(sse(["Unos 40 g."]));
    const user = userEvent.setup();
    render(<CoachPage />);

    await user.type(screen.getByRole("textbox", { name: t.coach.placeholder }), "¿Qué ceno?{Enter}");
    expect(await screen.findByText("Una ensalada con pollo.")).toBeInTheDocument();
    await user.type(screen.getByRole("textbox", { name: t.coach.placeholder }), "¿Cuánta proteína tiene?");
    await user.click(screen.getByRole("button", { name: t.coach.send }));
    expect(await screen.findByText("Unos 40 g.")).toBeInTheDocument();

    expect(requestBody(1).contents).toEqual([
      { role: "user", parts: [{ text: "¿Qué ceno?" }] },
      { role: "model", parts: [{ text: "Una ensalada con pollo." }] },
      { role: "user", parts: [{ text: "¿Cuánta proteína tiene?" }] },
    ]);

    await user.click(screen.getByRole("button", { name: t.coach.newChat }));
    expect(screen.queryByText("Unos 40 g.")).toBeNull();
    expect(screen.getByRole("button", { name: t.coach.examples[1] })).toBeInTheDocument();
  });

  it("sin permiso para ver tus datos no los lee ni los envía", async () => {
    configure({ coachSeesData: false });
    fetchMock.mockResolvedValueOnce(sse(["Hola."]));
    const user = userEvent.setup();
    render(<CoachPage />);
    expect(screen.getByText(new RegExp(t.coach.noData.replace(/[()→]/g, ".")))).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: t.coach.examples[1] }));
    await screen.findByText("Hola.");
    expect(requestBody(0).systemInstruction.parts[0].text).toContain("The user chose not to share their data");
    expect(vi.mocked(api.listMeals)).not.toHaveBeenCalled();
  });

  it("manda una foto sin texto: la ve el modelo, con una pregunta por defecto", async () => {
    configure();
    fetchMock.mockResolvedValueOnce(sse(["Parece una ensalada."]));
    const user = userEvent.setup();
    render(<CoachPage />);

    await user.upload(screen.getByTestId("coach-photo-input"), new File(["x"], "plato.jpg", { type: "image/jpeg" }));
    expect(await screen.findByAltText(formatTemplate(t.photo.photoAlt, { n: 1 }))).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: t.coach.send }));

    expect(await screen.findByText("Parece una ensalada.")).toBeInTheDocument();
    expect(screen.getByAltText(formatTemplate(t.coach.photoSent, { n: 1 }))).toBeInTheDocument();
    expect(requestBody(0).contents).toEqual([
      {
        role: "user",
        parts: [{ inline_data: { mime_type: "image/jpeg", data: "Zm90bw==" } }, { text: t.coach.photoPrompt }],
      },
    ]);
  });

  it("explica los errores y no los reenvía como historial", async () => {
    configure();
    fetchMock.mockResolvedValueOnce(new Response("{}", { status: 429 })).mockResolvedValueOnce(sse(["Vale."]));
    const user = userEvent.setup();
    render(<CoachPage />);

    await user.click(screen.getByRole("button", { name: t.coach.examples[2] }));
    expect(await screen.findByText(t.ai.errors.quota)).toBeInTheDocument();
    await user.type(screen.getByRole("textbox", { name: t.coach.placeholder }), "Otra vez{Enter}");
    await screen.findByText("Vale.");
    await waitFor(() => expect(requestBody(1).contents).toEqual([{ role: "user", parts: [{ text: "Otra vez" }] }]));
  });
});

describe("historyForModel", () => {
  it("quita respuestas fallidas o vacías y empieza siempre por el usuario", () => {
    expect(
      historyForModel([
        { id: 1, role: "assistant", text: "Hola" },
        { id: 2, role: "user", text: "a" },
        { id: 3, role: "assistant", text: "", error: "quota" },
        { id: 4, role: "user", text: "b" },
        { id: 5, role: "assistant", text: "B" },
        { id: 6, role: "user", text: "c" },
        { id: 7, role: "assistant", text: "" },
      ]),
    ).toEqual([
      { role: "user", text: "b" },
      { role: "assistant", text: "B" },
    ]);
  });

  it("reenvía las fotos con su pregunta, pero solo las más recientes", () => {
    const photo = (data: string) => ({ mimeType: "image/jpeg", data });
    const history = historyForModel(
      [
        { id: 1, role: "user", text: "", images: [photo("a"), photo("b"), photo("c")] },
        { id: 2, role: "assistant", text: "Tres platos." },
        { id: 3, role: "user", text: "¿Y este?", images: [photo("d"), photo("e")] },
        { id: 4, role: "assistant", text: "Otro." },
      ],
      1,
    );
    // 1 new photo + 4 earlier ones = MAX_PHOTOS (5): the oldest message keeps only 2.
    expect(history[0]).toEqual({ role: "user", text: t.coach.photoPrompt, images: [photo("a"), photo("b")] });
    expect(history[2]).toEqual({ role: "user", text: "¿Y este?", images: [photo("d"), photo("e")] });
  });
});
