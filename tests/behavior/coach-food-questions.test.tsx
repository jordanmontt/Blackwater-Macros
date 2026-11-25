import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import CoachPage from "@/app/coach/page";
import { resetCoach } from "@/lib/ai/coach-chat";
import { addDaysToKey, todayKey } from "@/lib/core/dates";
import type { CalorieProfile, MealDTO, WeightDTO } from "@/lib/core/types";
import { formatTemplate, t } from "@/i18n";

/**
 * Requisitos de las preguntas sobre lo que comes («¿Cuánta leche chocolatada tomo?»):
 *  - el modelo solo convierte la pregunta en una petición; la app hace las cuentas
 *    con tus comidas y el modelo explica esas cifras (nunca calcula),
 *  - debajo de la respuesta, «Cómo se calculó» dice qué se contó, escrito por la app,
 *  - si no hay nada que calcular (no lo encuentra, no entiende la pregunta) responde
 *    la app misma, con palabras sencillas, y eso no se reenvía al modelo,
 *  - una pregunta que no es sobre tus comidas se responde como siempre,
 *  - sin «El coach puede ver lo que como» no hay paso previo,
 *  - una pregunta de seguimiento («¿y el mes pasado?») lleva la petición anterior,
 *  - un ejemplo de pregunta usa uno de tus propios alimentos.
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

/** 14 days of breakfasts with chocolate milk (200 ml) and oats. */
const meals: MealDTO[] = Array.from({ length: 14 }, (_, i) => ({
  id: `m${i}`,
  logDate: addDaysToKey(todayKey(), -i),
  title: "Desayuno",
  notes: null,
  entryMode: "per_ingredient",
  ingredients: [
    { name: "Leche chocolatada", quantity: "200 ml", calories: 150, protein: 6, carbs: 22, fat: 4 },
    { name: "Avena", quantity: "60 g", calories: 230, protein: 8, carbs: 40, fat: 4 },
  ],
  totalCalories: null,
  totalProtein: null,
  totalCarbs: null,
  totalFat: null,
  resolvedCalories: 380,
  resolvedProtein: 14,
  resolvedCarbs: 62,
  resolvedFat: 8,
  updatedAt: "2026-01-01T00:00:00.000Z",
}));

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {},
  api: {
    session: vi.fn(async () => ({ username: "ana", isAdmin: false, calorieProfile: profile })),
    listMeals: vi.fn(async () => meals),
    listWeights: vi.fn(async () => [] as WeightDTO[]),
  },
}));

const fetchMock = vi.fn<typeof fetch>();

/** Gemini's whole answer (step 1: the request). */
function answer(text: string): Response {
  return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text }] }, finishReason: "STOP" }] }));
}

/** Gemini's streamed answer (step 3: the coach explains). */
function sse(text: string): Response {
  const chunk = { candidates: [{ content: { parts: [{ text }] }, finishReason: "STOP" }] };
  return new Response(`data: ${JSON.stringify(chunk)}\n\n`);
}

function configure(extra: Record<string, unknown> = {}) {
  localStorage.setItem("bw:ai", JSON.stringify({ provider: "gemini", apiKeys: { gemini: "fake-key" }, ...extra }));
}

function request(call: number) {
  const [url, init] = fetchMock.mock.calls[call];
  return { url: String(url), body: JSON.parse(String(init?.body)) };
}

async function ask(text: string) {
  const user = userEvent.setup();
  await user.type(screen.getByRole("textbox", { name: t.coach.placeholder }), `${text}{Enter}`);
  return user;
}

describe("Coach: preguntas sobre lo que comes", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();
    resetCoach();
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("la app cuenta con tus comidas y el modelo solo explica sus cifras, con «Cómo se calculó» debajo", async () => {
    configure();
    fetchMock
      .mockResolvedValueOnce(answer('{"analysis":"food_stats","foods":["leche chocolatada","chocolate milk"]}'))
      .mockResolvedValueOnce(sse("Tomas unos **200 ml** los días que la tomas."));
    render(<CoachPage />);
    const user = await ask("¿Cuánta leche chocolatada tomo?");
    expect(await screen.findByText("200 ml", { exact: false })).toBeInTheDocument();

    // Step 1: a short JSON request, not the coach's prompt with your data.
    const first = request(0);
    expect(first.url).toContain(":generateContent");
    expect(first.body.generationConfig.responseMimeType).toBe("application/json");
    expect(first.body.systemInstruction.parts[0].text).toContain("You turn a question about the user's own food log");
    expect(first.body.contents).toEqual([{ role: "user", parts: [{ text: "¿Cuánta leche chocolatada tomo?" }] }]);

    // Step 3: the question with the app's numbers; the model does not compute them.
    const asked = request(1).body.contents.at(-1).parts.at(-1).text as string;
    expect(asked.startsWith("¿Cuánta leche chocolatada tomo?\n\nFOOD LOG RESULT")).toBe(true);
    expect(asked).toContain("Eaten on 14 of the 14 logged days");
    expect(asked).toContain("Amount in ml: 2800 ml in 14 entries; 200 ml per day it was eaten");

    // On screen the question reads as typed; the details are the app's own words.
    expect(screen.getByText("¿Cuánta leche chocolatada tomo?")).toBeInTheDocument();
    await user.click(screen.getByText(t.coach.food.howTitle));
    expect(screen.getByText(t.coach.food.analyses.food_stats)).toBeInTheDocument();
    expect(screen.getByText(formatTemplate(t.coach.food.counted, { names: "Leche chocolatada" }))).toBeInTheDocument();
    expect(screen.getByText(t.coach.food.unlogged)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: t.coach.food.methodology })).toHaveAttribute("href", "/metodologia#food-questions");
  });

  it("si no lo encuentra lo dice la app misma, y eso no se reenvía al modelo", async () => {
    configure();
    fetchMock
      .mockResolvedValueOnce(answer('{"analysis":"food_stats","foods":["kombucha"]}'))
      .mockResolvedValueOnce(answer('{"analysis":"none"}'))
      .mockResolvedValueOnce(sse("Vale."));
    render(<CoachPage />);
    const user = await ask("¿Cuánta kombucha tomo?");
    const reply = await screen.findByText(new RegExp(formatTemplate(t.coach.food.quoted, { text: "kombucha" })));
    expect(reply.textContent).toContain(t.coach.food.noMatch.split(" {foods}")[0]);
    // Only step 1 went to the provider.
    expect(fetchMock).toHaveBeenCalledTimes(1);

    await user.type(screen.getByRole("textbox", { name: t.coach.placeholder }), "Gracias{Enter}");
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
    expect(request(2).body.contents).toEqual([{ role: "user", parts: [{ text: "Gracias" }] }]);
  });

  it("si no entiende la pregunta lo dice con palabras sencillas y un ejemplo con tus alimentos", async () => {
    configure();
    fetchMock.mockResolvedValueOnce(answer("No sé qué quieres decir."));
    render(<CoachPage />);
    await ask("¿Y lo otro?");
    const example = formatTemplate(t.coach.food.example, { food: "Avena" });
    expect(await screen.findByText(formatTemplate(t.coach.food.unclear, { example }))).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("una pregunta que no es sobre tus comidas se responde como siempre", async () => {
    configure();
    fetchMock.mockResolvedValueOnce(answer('{"analysis":"none"}')).mockResolvedValueOnce(sse("Unos 120 g al día."));
    render(<CoachPage />);
    await ask("¿Cuánta proteína necesito?");
    expect(await screen.findByText("Unos 120 g al día.")).toBeInTheDocument();
    expect(request(1).body.contents).toEqual([{ role: "user", parts: [{ text: "¿Cuánta proteína necesito?" }] }]);
    expect(screen.queryByText(t.coach.food.howTitle)).toBeNull();
  });

  it("sin «El coach puede ver lo que como» no hay paso previo", async () => {
    configure({ coachSeesFood: false });
    fetchMock.mockResolvedValueOnce(sse("Hola."));
    render(<CoachPage />);
    await ask("¿Cuánta leche chocolatada tomo?");
    expect(await screen.findByText("Hola.")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(request(0).url).toContain("streamGenerateContent");
  });

  it("una pregunta de seguimiento lleva la petición anterior", async () => {
    configure();
    fetchMock
      .mockResolvedValueOnce(answer('{"analysis":"food_stats","foods":["leche chocolatada"]}'))
      .mockResolvedValueOnce(sse("Unos 200 ml."))
      .mockResolvedValueOnce(answer('{"analysis":"food_stats","foods":["leche chocolatada"],"from":"2026-01-01","to":"2026-01-31"}'))
      .mockResolvedValueOnce(sse("En enero no la registraste."));
    render(<CoachPage />);
    const user = await ask("¿Cuánta leche chocolatada tomo?");
    await screen.findByText("Unos 200 ml.");
    await user.type(screen.getByRole("textbox", { name: t.coach.placeholder }), "¿Y en enero?{Enter}");
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
    expect(request(2).body.systemInstruction.parts[0].text).toContain(
      'The previous request in this conversation was: {"analysis":"food_stats","foods":["leche chocolatada"]}.',
    );
  });

  it("un ejemplo de pregunta usa uno de tus alimentos", async () => {
    configure();
    render(<CoachPage />);
    const examples = await screen.findByRole("button", { name: formatTemplate(t.coach.food.example, { food: "Avena" }) });
    expect(examples).toBeInTheDocument();
    expect(within(examples).queryByText(t.coach.examples[0])).toBeNull();
  });
});
