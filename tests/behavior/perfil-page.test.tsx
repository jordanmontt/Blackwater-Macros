import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import PerfilPage from "@/app/ajustes/perfil/page";
import { t } from "@/i18n";
import { clearCache } from "@/lib/client-cache";
import type { CalorieProfile } from "@/lib/core/types";
import { emptyCalorieProfile } from "../helpers/repos";

/**
 * Requisitos visibles en la pantalla "Perfil" (Ajustes → Perfil):
 *  - elegir un objetivo guarda el perfil calórico con un breve debounce,
 *  - no guarda mientras haya errores de validación,
 *  - con peso y perfil completos muestra las recomendaciones, el factor de
 *    actividad y, sin 4 semanas de datos, qué falta para el gasto medido,
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
    updateSettings: vi.fn(async (calorieProfile: CalorieProfile) => ({ calorieProfile })),
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
      expect(vi.mocked(api.updateSettings)).toHaveBeenCalledWith(
        expect.objectContaining({ calorieGoal: "surplus" }),
      ),
    );
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
    expect(screen.getByText(t.calorias.measuredTdeePending)).toBeInTheDocument();
  });

  it("tiene un enlace para volver a Ajustes", async () => {
    render(<PerfilPage />);
    const back = await screen.findByRole("button", { name: t.perfil.backToSettings });
    expect(back).toHaveAttribute("href", "/ajustes");
  });
});
