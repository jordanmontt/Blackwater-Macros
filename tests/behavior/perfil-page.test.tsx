import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import PerfilPage from "@/app/ajustes/perfil/page";
import { formatTemplate, t } from "@/i18n";
import { clearCache } from "@/lib/client-cache";
import type { CalorieProfile } from "@/lib/core/types";
import { emptyCalorieProfile } from "../helpers/repos";

/**
 * Requisitos visibles en la pantalla "Perfil" (Ajustes → Perfil):
 *  - elegir un objetivo guarda el perfil calórico con un breve debounce,
 *  - no guarda mientras haya errores de validación,
 *  - con peso y perfil completos muestra las recomendaciones, el factor de
 *    actividad y, sin 4 semanas de datos, qué falta exactamente para comparar
 *    el perfil con el gasto medido;
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
    listMeals: vi.fn(async () => []),
    updateSettings: vi.fn(async (calorieProfile: CalorieProfile) => ({ calorieProfile, profileHistory: [] })),
  } satisfies Pick<typeof import("@/lib/api").api, "session" | "listWeights" | "listMeals" | "updateSettings">,
}));

import { api } from "@/lib/api";

describe("pantalla Perfil", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearCache();
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
