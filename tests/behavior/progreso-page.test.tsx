import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ProgresoPage from "@/app/progreso/page";
import { addDaysToKey, formatNumberEs, todayKey } from "@/lib/core/dates";
import { buildStatsFromData } from "@/lib/core/stats-builder";
import type { CalorieProfile, MealDTO, StatsRange, WeightDTO } from "@/lib/core/types";
import { t } from "@/i18n";

/**
 * Requisitos visibles en la pantalla "Progreso" (Peso + Estadísticas en una):
 *  - un selector de periodo que manda sobre toda la pantalla,
 *  - resumen de peso (actual, tendencia, cambio, ritmo, grasa) y su gráfico,
 *  - gráfico de calorías y «Promedio de macros» solo sobre los días registrados
 *    («N de M días registrados»), con el reparto de calorías y los objetivos,
 *  - la lista de pesajes del periodo, con añadir, editar y borrar (pregunta
 *    antes y luego ofrece «Deshacer»).
 */

let weights: WeightDTO[] = [];
let meals: MealDTO[] = [];

const profile: CalorieProfile = {
  gender: "male",
  birthYear: 1990,
  heightCm: 178,
  gymDaysPerWeek: 3,
  gymSessionMinutes: 60,
  walkingMinutesPerDay: 30,
  calorieGoal: "maintain",
};

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock("sonner", () => ({ toast: toastMock }));

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
  api: {
    listWeights: vi.fn(async () => weights),
    listMeals: vi.fn(async () => meals),
    session: vi.fn(async () => ({ username: "ana", isAdmin: false, calorieProfile: profile })),
    stats: vi.fn(async (range: StatsRange, today: string) =>
      buildStatsFromData(
        meals,
        weights.map((w) => ({ measuredAt: new Date(w.measuredAt), weightKg: w.weightKg, bodyFatPct: w.bodyFatPct })),
        range,
        today,
      ),
    ),
    createWeight: vi.fn(async () => weightDto({ id: "w-new", weightKg: 77.4 })),
    updateWeight: vi.fn(async () => weightDto({ id: "w-1", weightKg: 78 })),
    deleteWeight: vi.fn(async () => ({ ok: true as const })),
    restoreWeight: vi.fn(async () => undefined),
  } satisfies Pick<
    typeof import("@/lib/api").api,
    "listWeights" | "listMeals" | "session" | "stats" | "createWeight" | "updateWeight" | "deleteWeight" | "restoreWeight"
  >,
  errorText: (error: unknown) => (error instanceof Error ? error.message : "error"),
  UNDO_TOAST_MS: 10_000,
}));

vi.mock("@/components/weight-fat-chart", async () => {
  const React = await import("react");
  return {
    WeightFatChart: ({ data }: { data: unknown[] }) =>
      React.createElement("div", { "data-testid": "grafico-peso", "data-puntos": String(data.length) }),
  };
});

import { api } from "@/lib/api";
import { mealDto, weightDto } from "../helpers/repos";
import { clearCache } from "@/lib/client-cache";

const today = todayKey();
const daysAgo = (n: number) => addDaysToKey(today, -n);
const at = (day: string) => `${day}T08:00:00`;

function meal(day: string, calories: number, protein: number, carbs: number, fat: number): MealDTO {
  return mealDto({
    id: `m-${day}-${calories}`,
    logDate: day,
    resolvedCalories: calories,
    resolvedProtein: protein,
    resolvedCarbs: carbs,
    resolvedFat: fat,
  });
}

describe("pantalla Progreso", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearCache();
    sessionStorage.clear();
    weights = [];
    meals = [];
  });

  it("sin datos invita a registrar el primer pesaje y dice que no hay comidas", async () => {
    render(<ProgresoPage />);
    expect((await screen.findByText(t.peso.emptyList)).tagName).toBe("BUTTON");
    expect(screen.getByText(t.progreso.noMeals)).toBeInTheDocument();
  });

  it("muestra el peso actual, el resumen y el gráfico de peso", async () => {
    weights = [
      weightDto({ id: "w-1", measuredAt: at(daysAgo(5)), weightKg: 80 }),
      weightDto({ id: "w-2", measuredAt: at(daysAgo(1)), weightKg: 79.5, bodyFatPct: 18.5 }),
    ];
    render(<ProgresoPage />);

    expect(await screen.findByTestId("current-weight")).toHaveTextContent(`${formatNumberEs(79.5, 1)} kg`);
    expect(screen.getByText(t.progreso.trend)).toBeInTheDocument();
    expect(screen.getByText(`-${formatNumberEs(0.5, 1)}`)).toBeInTheDocument(); // cambio
    expect(screen.getByText(formatNumberEs(18.5, 1))).toBeInTheDocument(); // grasa
    expect(screen.getByTestId("grafico-peso")).toHaveAttribute("data-puntos", "2");
  });

  it("promedia solo los días registrados y muestra reparto y objetivos", async () => {
    weights = [weightDto({ id: "w-1", measuredAt: at(daysAgo(1)), weightKg: 80 })];
    meals = [meal(daysAgo(1), 2000, 150, 200, 60), meal(daysAgo(3), 2200, 130, 250, 70)];
    render(<ProgresoPage />);

    expect(await screen.findByText(formatTemplateText(t.progreso.loggedDays, { n: 2, m: 30 }))).toBeInTheDocument();
    expect(screen.getByText(`${formatNumberEs(2100)} kcal`)).toBeInTheDocument();
    expect(screen.getByText("140 g")).toBeInTheDocument();
    expect(screen.getByTestId("macro-split")).toHaveTextContent("27 %");
    expect(screen.getByTestId("macro-split")).toHaveTextContent("44 %");
    expect(screen.getByTestId("macro-split")).toHaveTextContent("29 %");
    // Perfil completo → objetivos de calorías y proteína junto a la media.
    expect(await screen.findAllByText(/^objetivo \S+–\S+$/)).toHaveLength(2);
    expect(screen.getByTestId("calories-chart")).toBeInTheDocument();
  });

  it("el periodo manda sobre toda la pantalla", async () => {
    const user = userEvent.setup();
    weights = [
      weightDto({ id: "w-old", measuredAt: at(daysAgo(20)), weightKg: 82, note: "hace 20 días" }),
      weightDto({ id: "w-new", measuredAt: at(daysAgo(2)), weightKg: 80, note: "reciente" }),
    ];
    meals = [meal(daysAgo(2), 2000, 150, 200, 60)];
    render(<ProgresoPage />);

    expect(await screen.findByText("hace 20 días")).toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: t.stats.range7 }));

    expect(await screen.findByText(formatTemplateText(t.progreso.loggedDays, { n: 1, m: 7 }))).toBeInTheDocument();
    expect(vi.mocked(api.stats)).toHaveBeenCalledWith("7d", today);
    expect(screen.queryByText("hace 20 días")).toBeNull();
    expect(screen.getByText("reciente")).toBeInTheDocument();
  });

  it("registra un pesaje nuevo y refresca los datos", async () => {
    const user = userEvent.setup();
    render(<ProgresoPage />);
    await screen.findByText(t.peso.emptyList);

    await user.click(screen.getByRole("button", { name: t.peso.addTitle }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(t.peso.weightLabel), "77,4");
    await user.type(within(dialog).getByLabelText(t.peso.bodyFatLabel), "18,5");
    await user.click(within(dialog).getByRole("button", { name: t.peso.save }));

    expect(vi.mocked(api.createWeight)).toHaveBeenCalledWith(
      expect.objectContaining({ weightKg: 77.4, bodyFatPct: 18.5, measuredAt: expect.any(String), note: null }),
    );
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("rechaza un peso no válido sin llamar a la API", async () => {
    const user = userEvent.setup();
    render(<ProgresoPage />);
    await screen.findByText(t.peso.emptyList);

    await user.click(screen.getByRole("button", { name: t.peso.addTitle }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(t.peso.weightLabel), "0");
    await user.click(within(dialog).getByRole("button", { name: t.peso.save }));

    expect(vi.mocked(api.createWeight)).not.toHaveBeenCalled();
  });

  it("edita un pesaje existente", async () => {
    const user = userEvent.setup();
    weights = [weightDto({ id: "w-1", measuredAt: at(daysAgo(1)), weightKg: 80, bodyFatPct: 18.5 })];
    render(<ProgresoPage />);

    await user.click(await screen.findByRole("button", { name: t.peso.edit }));
    const dialog = await screen.findByRole("dialog");
    const input = within(dialog).getByLabelText(t.peso.weightLabel);
    expect(input).toHaveValue("80");
    await user.clear(input);
    await user.type(input, "79,8");
    await user.click(within(dialog).getByRole("button", { name: t.peso.save }));

    expect(vi.mocked(api.updateWeight)).toHaveBeenCalledWith("w-1", expect.objectContaining({ weightKg: 79.8 }));
  });

  it("borra un pesaje tras confirmar y ofrece deshacerlo", async () => {
    const user = userEvent.setup();
    weights = [weightDto({ id: "w-1", measuredAt: at(daysAgo(1)) })];
    render(<ProgresoPage />);

    await user.click(await screen.findByRole("button", { name: t.peso.delete }));
    const confirm = await screen.findByRole("alertdialog");
    await user.click(within(confirm).getByRole("button", { name: t.peso.delete }));

    expect(vi.mocked(api.deleteWeight)).toHaveBeenCalledWith("w-1");

    await vi.waitFor(() => expect(toastMock.success).toHaveBeenCalled());
    const [message, options] = toastMock.success.mock.calls.at(-1)!;
    expect(message).toBe(t.peso.deleted);
    (options as { action: { label: string; onClick: () => void } }).action.onClick();
    await vi.waitFor(() =>
      expect(vi.mocked(api.restoreWeight)).toHaveBeenCalledWith(expect.objectContaining({ id: "w-1" })),
    );
  });
});

function formatTemplateText(template: string, values: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (_, key: string) => String(values[key]));
}
