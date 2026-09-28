import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import HoyPage from "@/app/page";
import type { FoodProduct } from "@/lib/core/foods";
import type { MealDTO, MealTemplateDTO } from "@/lib/core/types";
import { fitWithin } from "@/lib/ai/images";
import { formatTemplate, t } from "@/i18n";

/**
 * Requisitos de «Foto o texto» (IA) en «Añadir comida»:
 *  - sin IA configurada, explica cómo configurarla (y no llama a nadie),
 *  - con fotos, con solo texto (p. ej. los datos de una etiqueta) o con ambos, la
 *    IA estima la comida y abre el formulario de
 *    revisión ya relleno, con la línea «Estimación de la IA (confianza …)»,
 *  - las fotos van solo al proveedor elegido, reducidas, y no se guardan,
 *  - «Estimar “…” con IA» desde Buscar estima el texto directamente,
 *  - los errores (límite, respuesta ilegible) se explican y ofrecen «Escribir a mano».
 * `fetch` está simulado: nunca se llama a un proveedor real.
 */

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
  api: {
    listMeals: vi.fn(async () => [] as MealDTO[]),
    listTemplates: vi.fn(async () => [] as MealTemplateDTO[]),
    createMeal: vi.fn(async (payload: { title: string }) => ({ ...payload, id: "m-new" }) as unknown as MealDTO),
    reorderMeals: vi.fn(async () => ({ ok: true as const })),
    listWeights: vi.fn(async () => []),
    searchFoods: vi.fn(async () => [] as FoodProduct[]),
    session: vi.fn(async () => ({
      username: "demo",
      isAdmin: false,
      calorieProfile: {
        gender: null,
        birthYear: null,
        heightCm: null,
        gymDaysPerWeek: null,
        gymSessionMinutes: null,
        walkingMinutesPerDay: null,
        calorieGoal: null,
      },
    })),
  },
}));

// happy-dom has no canvas/createImageBitmap: the downscale itself is a browser API.
vi.mock("@/lib/ai/images", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/ai/images")>()),
  downscalePhoto: vi.fn(async () => ({ mimeType: "image/jpeg", data: "SMALLJPEG" })),
}));

import { api } from "@/lib/api";
import { clearCache } from "@/lib/client-cache";

const ESTIMATE = {
  title: "Pasta boloñesa",
  items: [
    { name: "Espaguetis cocidos", grams: 180, calories: 284, protein: 10.4, carbs: 55.8, fat: 1.7 },
    { name: "Aceite de oliva", grams: 10, calories: 88, protein: 0, carbs: 0, fat: 10 },
  ],
  confidence: "medium",
  notes: "Aceite estimado.",
};

function geminiAnswer(json: unknown): Response {
  return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: JSON.stringify(json) }] } }] }));
}

const fetchMock = vi.fn<typeof fetch>();

function configureGemini() {
  localStorage.setItem("bw:ai", JSON.stringify({ provider: "gemini", apiKeys: { gemini: "fake-key" } }));
}

async function openPhoto(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByText(t.hoy.emptyDay);
  await user.click(screen.getAllByRole("button", { name: t.hoy.addMeal })[0]);
  const sheet = await screen.findByRole("dialog", { name: t.addFood.title });
  await user.click(within(sheet).getByRole("button", { name: new RegExp(`^${t.photo.title}`) }));
  return screen.findByRole("dialog", { name: t.photo.title });
}

describe("Añadir comida: foto con IA", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearCache();
    sessionStorage.clear();
    localStorage.clear();
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("sin IA configurada explica cómo configurarla", async () => {
    const user = userEvent.setup();
    render(<HoyPage />);
    const sheet = await openPhoto(user);
    expect(within(sheet).getByText(t.photo.notConfigured)).toBeInTheDocument();
    expect(within(sheet).getByText(t.photo.configure).closest("a")).toHaveAttribute("href", "/ajustes");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("estima fotos + descripción y abre la revisión ya rellena", async () => {
    configureGemini();
    fetchMock.mockResolvedValue(geminiAnswer(ESTIMATE));
    const user = userEvent.setup();
    render(<HoyPage />);
    const sheet = await openPhoto(user);

    expect(within(sheet).getByText(t.photo.tips[1])).toBeInTheDocument();
    const photo = new File(["x"], "plato.jpg", { type: "image/jpeg" });
    await user.upload(screen.getByTestId("photo-gallery-input"), [photo, photo]);
    expect(await within(sheet).findAllByRole("img")).toHaveLength(2);
    await user.click(within(sheet).getByRole("button", { name: formatTemplate(t.photo.removePhoto, { n: 2 }) }));
    await user.type(within(sheet).getByLabelText(t.photo.describe), "con aceite");
    await user.click(within(sheet).getByRole("button", { name: t.photo.estimate }));

    const form = await screen.findByRole("dialog", { name: t.meal.newTitle });
    expect(within(form).getByRole("note")).toHaveTextContent(
      `${formatTemplate(t.photo.notice, { confidence: t.photo.confidence.medium })} Aceite estimado.`,
    );
    expect(within(form).getByLabelText(t.meal.titleLabel)).toHaveValue("Pasta boloñesa");
    expect(within(form).getByDisplayValue("Aceite de oliva")).toBeInTheDocument();
    expect(within(form).getByDisplayValue("180 g")).toBeInTheDocument();

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain("generativelanguage.googleapis.com");
    const body = JSON.parse(String(init?.body));
    expect(body.contents[0].parts).toEqual([
      { inline_data: { mime_type: "image/jpeg", data: "SMALLJPEG" } },
      { text: "A photo of my meal.\nWhat I can add: con aceite" },
    ]);
    expect(body.generationConfig.responseMimeType).toBe("application/json");

    await user.click(within(form).getByRole("button", { name: t.meal.save }));
    await waitFor(() =>
      expect(vi.mocked(api.createMeal)).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "Pasta boloñesa",
          entryMode: "per_ingredient",
          ingredients: [
            { name: "Espaguetis cocidos", quantity: "180 g", calories: 284, protein: 10.4, carbs: 55.8, fat: 1.7 },
            { name: "Aceite de oliva", quantity: "10 g", calories: 88, protein: 0, carbs: 0, fat: 10 },
          ],
        }),
      ),
    );
  });

  it("con solo texto (los datos de una etiqueta) estima sin foto", async () => {
    configureGemini();
    fetchMock.mockResolvedValue(geminiAnswer(ESTIMATE));
    const user = userEvent.setup();
    render(<HoyPage />);
    const sheet = await openPhoto(user);

    const label = "Por 100 g: 555 kcal, 20 g proteína, 25 g grasa, 12 g carbohidratos. Peso total 350 g.";
    await user.type(within(sheet).getByLabelText(t.photo.describe), label);
    await user.click(within(sheet).getByRole("button", { name: t.photo.estimate }));

    await screen.findByRole("dialog", { name: t.meal.newTitle });
    const parts = JSON.parse(String(fetchMock.mock.calls[0][1]?.body)).contents[0].parts;
    expect(parts).toHaveLength(1);
    expect(parts[0].text).toContain(label);
  });

  it("pide una foto o una descripción antes de estimar", async () => {
    configureGemini();
    const user = userEvent.setup();
    render(<HoyPage />);
    const sheet = await openPhoto(user);
    await user.click(within(sheet).getByRole("button", { name: t.photo.estimate }));
    expect(within(sheet).getByRole("alert")).toHaveTextContent(t.photo.needInput);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("explica los errores y ofrece escribir a mano", async () => {
    configureGemini();
    fetchMock.mockResolvedValueOnce(new Response("{}", { status: 429 }));
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: "No veo comida." }] } }] })));
    const user = userEvent.setup();
    render(<HoyPage />);
    const sheet = await openPhoto(user);
    await user.type(within(sheet).getByLabelText(t.photo.describe), "tortilla");

    await user.click(within(sheet).getByRole("button", { name: t.photo.estimate }));
    expect(await within(sheet).findByText(t.ai.errors.quota)).toBeInTheDocument();
    await user.click(within(sheet).getByRole("button", { name: t.photo.estimate }));
    expect(await within(sheet).findByText(t.ai.errors.unreadable)).toBeInTheDocument();

    await user.click(within(within(sheet).getByRole("alert")).getByRole("button", { name: t.addFood.manual }));
    const form = await screen.findByRole("dialog", { name: t.meal.newTitle });
    expect(within(form).queryByRole("note")).toBeNull();
  });

  it("«Estimar “…” con IA» desde Buscar estima el texto directamente", async () => {
    configureGemini();
    vi.stubGlobal("fetch", async (input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input).endsWith("/foods/generic.json")) return new Response(JSON.stringify({ version: 1, foods: [] }));
      return fetchMock(input, init);
    });
    fetchMock.mockResolvedValue(
      geminiAnswer({
        title: "Plátanos",
        items: [{ name: "Plátano", grams: 360, calories: 320, protein: 4, carbs: 82, fat: 1.2 }],
        confidence: "high",
      }),
    );
    const user = userEvent.setup();
    render(<HoyPage />);
    await screen.findByText(t.hoy.emptyDay);
    await user.click(screen.getAllByRole("button", { name: t.hoy.addMeal })[0]);
    const sheet = await screen.findByRole("dialog", { name: t.addFood.title });
    await user.click(within(sheet).getByRole("button", { name: new RegExp(`^${t.addFood.search}`) }));
    await user.type(await screen.findByRole("searchbox", { name: t.addFood.search }), "3 plátanos");
    await user.click(screen.getByRole("button", { name: formatTemplate(t.photo.estimateQuery, { q: "3 plátanos" }) }));

    const form = await screen.findByRole("dialog", { name: t.meal.newTitle });
    expect(within(form).getByLabelText(t.meal.titleLabel)).toHaveValue("Plátanos");
    const body = JSON.parse(String(fetchMock.mock.calls[0][1]?.body));
    expect(body.contents[0].parts).toEqual([{ text: "Estimate this meal: 3 plátanos" }]);
  });
});

describe("fitWithin", () => {
  it("reduce el lado largo a 1024 px sin agrandar las fotos pequeñas", () => {
    expect(fitWithin(4032, 3024)).toEqual({ width: 1024, height: 768 });
    expect(fitWithin(3000, 4000)).toEqual({ width: 768, height: 1024 });
    expect(fitWithin(800, 600)).toEqual({ width: 800, height: 600 });
  });
});
