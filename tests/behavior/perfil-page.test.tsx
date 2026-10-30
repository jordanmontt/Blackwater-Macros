import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import PerfilPage from "@/app/ajustes/perfil/page";
import { formatTemplate, t } from "@/i18n";
import { clearCache } from "@/lib/client-cache";
import { addDaysToKey, todayKey } from "@/lib/core/dates";
import { buildStatsFromData } from "@/lib/core/stats-builder";
import type { CalorieProfile, MealDTO, WeightDTO } from "@/lib/core/types";
import { emptyCalorieProfile } from "../helpers/repos";

/**
 * Requisitos visibles en la pantalla "Perfil" (Ajustes → Perfil):
 *  - elegir un objetivo guarda el perfil calórico con un breve debounce,
 *  - bajo el objetivo dice qué hace: con gimnasio, también ganar músculo,
 *  - no guarda mientras haya errores de validación,
 *  - con peso y perfil completos muestra las recomendaciones, el factor de
 *    actividad y, sin 4 semanas de datos, qué falta exactamente para comparar
 *    el perfil con el gasto medido; con ellas, el gasto con el que se calcula
 *    el objetivo (perfil y datos mezclados);
 *    pide «introduce tus datos» solo mientras el perfil está incompleto,
 *  - vuelve a Ajustes.
 */

const profile = (overrides: Partial<CalorieProfile> = {}): CalorieProfile => ({
  ...emptyCalorieProfile(),
  ...overrides,
});

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
  api: {
    session: vi.fn(async () => ({
      username: "ana",
      isAdmin: false,
      calorieProfile: profile({ calorieGoal: "maintain" }),
    })),
    listWeights: vi.fn(async () => [{ id: "w-1", measuredAt: "2026-06-15T08:00:00.000Z", weightKg: 80, bodyFatPct: null, note: null, updatedAt: "2026-06-15T08:00:00.000Z" }]),
    stats: vi.fn(),
    updateSettings: vi.fn(async (calorieProfile: CalorieProfile) => ({ calorieProfile, profileHistory: [] })),
  } satisfies Pick<typeof import("@/lib/api").api, "session" | "listWeights" | "stats" | "updateSettings">,
}));

import { api } from "@/lib/api";

/** Perfil reads every day's kcal from the all-time stats, as the server builds them (meals in date order). */
function logMeals(meals: MealDTO[]) {
  vi.mocked(api.stats).mockImplementation(async (range, today) => buildStatsFromData(meals, [], range, today));
}

describe("pantalla Perfil", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearCache();
    // A Monday: this week's measurement reads the 4 weeks before today.
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 2, 2, 12));
    logMeals([]);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("guarda el objetivo tras el debounce de medio segundo", async () => {
    const user = userEvent.setup();
    render(<PerfilPage />);

    await user.click(await screen.findByRole("button", { name: t.ajustes.goalSurplus }));
    expect(vi.mocked(api.updateSettings)).not.toHaveBeenCalled();

    await waitFor(() =>
      // With the saved session, so the change applies from today on (past days keep their targets).
      expect(vi.mocked(api.updateSettings)).toHaveBeenCalledWith(
        expect.objectContaining({ calorieGoal: "surplus" }),
        expect.objectContaining({ calorieProfile: expect.objectContaining({ calorieGoal: "maintain" }) }),
      ),
    );
  });

  it("explica el objetivo: con gimnasio cuenta con ganar músculo, sin él no", async () => {
    const { unmount } = render(<PerfilPage />);
    expect(await screen.findByText(t.ajustes.goalHintMaintainNoTraining)).toBeInTheDocument();
    unmount();

    clearCache();
    vi.mocked(api.session).mockResolvedValueOnce({
      username: "ana",
      isAdmin: false,
      calorieProfile: profile({ calorieGoal: "maintain", gymDaysPerWeek: 3, gymSessionMinutes: 60 }),
    });
    render(<PerfilPage />);
    expect(await screen.findByText(t.ajustes.goalHintMaintain)).toBeInTheDocument();
  });

  it("dice que los cambios cuentan desde hoy y enlaza a la explicación", async () => {
    render(<PerfilPage />);
    const note = await screen.findByTestId("changes-from-today");
    expect(note).toHaveTextContent(t.perfil.changesFromToday);
    expect(note).toHaveAttribute("href", "/metodologia#past-days");
  });

  it("no guarda el perfil y muestra el error si un campo no valida", async () => {
    const user = userEvent.setup();
    render(<PerfilPage />);

    const birthYear = await screen.findByLabelText(t.calorias.birthYearLabel);
    await user.type(birthYear, "1800");

    expect(await screen.findByText("El año debe estar entre 1920 y 2010")).toBeInTheDocument();
    await waitFor(() => {
      expect(vi.mocked(api.updateSettings)).not.toHaveBeenCalled();
    });
  });

  it("muestra la proteína recomendada cuando hay peso y objetivo", async () => {
    render(<PerfilPage />);
    expect(await screen.findByText(t.ajustes.proteinRecLabel)).toBeInTheDocument();
  });

  it("con el perfil completo explica el factor de actividad y qué falta para el gasto medido", async () => {
    vi.mocked(api.session).mockResolvedValueOnce({
      username: "ana",
      isAdmin: false,
      calorieProfile: profile({
        gender: "male",
        birthYear: 1990,
        heightCm: 178,
        gymDaysPerWeek: 3,
        gymSessionMinutes: 60,
        walkingMinutesPerDay: 30,
        calorieGoal: "maintain",
      }),
    });
    render(<PerfilPage />);

    // 3 × 60 min de gym y 30 min de caminata → 1,49 × TMB
    expect(await screen.findByText("Factor de actividad: 1,49 × TMB")).toBeInTheDocument();
    // Sin comidas ni pesajes recientes: dice exactamente qué falta para comparar perfil y datos.
    expect(await screen.findByText(t.expenditureCheck.notEnoughIntro)).toBeInTheDocument();
    expect(screen.getByText(formatTemplate(t.expenditureCheck.missingMeals, { days: 0 }))).toBeInTheDocument();
    expect(screen.getByText(formatTemplate(t.expenditureCheck.missingWeighIns, { n: 0 }))).toBeInTheDocument();
    expect(screen.getByRole("link", { name: t.expenditureCheck.howItWorks })).toHaveAttribute("href", "/metodologia#check");
    // Nothing left to enter: the «introduce tus datos» hint is gone.
    expect(screen.queryByText(t.calorias.noProfile)).not.toBeInTheDocument();
  });

  it("con 4 semanas de datos dice con qué gasto se calcula el objetivo", async () => {
    vi.mocked(api.session).mockResolvedValueOnce({
      username: "ana",
      isAdmin: false,
      calorieProfile: profile({
        gender: "male",
        birthYear: 1990,
        heightCm: 180,
        gymDaysPerWeek: 3,
        gymSessionMinutes: 60,
        walkingMinutesPerDay: 30,
        calorieGoal: "maintain",
      }),
    });
    // 35 days on 2600 kcal, weighed daily, losing 0,7 kg/week → measured 3370 ± 177.
    const days = Array.from({ length: 35 }, (_, i) => addDaysToKey(todayKey(), -(35 - i)));
    const weighIns: WeightDTO[] = days.map((day, i) => ({
      id: `w${i}`,
      measuredAt: `${day}T08:00:00`,
      weightKg: 80 + (0.7 / 7) * (34 - i),
      bodyFatPct: null,
      note: null,
      updatedAt: `${day}T08:00:00`,
    }));
    vi.mocked(api.listWeights).mockResolvedValue(weighIns);
    logMeals(
      days.map((day, i) => ({
        id: `m${i}`,
        logDate: day,
        title: "Comida",
        notes: null,
        entryMode: "total_only",
        ingredients: [],
        totalCalories: 2600,
        totalProtein: 100,
        totalCarbs: null,
        totalFat: null,
        resolvedCalories: 2600,
        resolvedProtein: 100,
        resolvedCarbs: 0,
        resolvedFat: 0,
        updatedAt: `${day}T08:00:00`,
      })),
    );
    render(<PerfilPage />);

    // Profile 2608 ± 10 % and measured 3370 ± 177: the data weigh 89 % → 3288.
    const used = await screen.findByRole("link", { name: (name) => name.startsWith(t.calorias.targetExpenditure) });
    expect(used).toHaveTextContent(formatTemplate(t.calorias.targetExpenditureValue, { n: "3288", share: "89" }));
    expect(screen.getByText("3188 – 3388 kcal/día")).toBeInTheDocument();
  });

  it("con el perfil incompleto pide los datos que faltan", async () => {
    render(<PerfilPage />);
    expect(await screen.findByText(t.calorias.noProfile)).toBeInTheDocument();
  });

  it("tiene un enlace para volver a Ajustes", async () => {
    render(<PerfilPage />);
    const back = await screen.findByRole("button", { name: t.perfil.backToSettings });
    expect(back).toHaveAttribute("href", "/ajustes");
  });
});
