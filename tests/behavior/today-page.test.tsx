import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import HoyPage from "@/app/page";
import type { MealDTO, MealTemplateDTO } from "@/lib/types";

/**
 * Requisitos visibles en la pantalla "Hoy":
 *  - el usuario ve el total acumulado de calorías y proteína del día,
 *    calculado con todas las comidas registradas,
 *  - puede avanzar/retroceder de día para revisar comidas pasadas,
 *  - si no hay comidas, la pantalla lo deja claro e invita a añadir,
 *  - al aplicar una plantilla hay feedback inmediato y no se puede
 *    disparar dos veces sin querer.
 */

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {},
  api: {
    listMeals: vi.fn(async () => [
      meal({ title: "Desayuno", kcal: 475, protein: 32.4 }),
      meal({ title: "Comida", kcal: 850, protein: 45 }),
    ]),
    listTemplates: vi.fn(async () => [] as MealTemplateDTO[]),
    createMeal: vi.fn(async () => meal({ title: "nueva", kcal: 0, protein: 0 })),
    reorderMeals: vi.fn(async () => ({ ok: true as const })),
    listWeights: vi.fn(async () => []),
    session: vi.fn(async () => ({
      username: "demo",
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

import { api } from "@/lib/api";

describe("pantalla Hoy", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.listTemplates).mockResolvedValue([]);
  });

  it("muestra los totales diarios sumando todas las comidas del día", async () => {
    render(<HoyPage />);

    // 475 + 850 = 1325 kcal; 32.4 + 45 = 77.4 g
    // (el separador de miles depende de la configuración regional del sistema)
    expect(await screen.findByText(/1[.,]?325/)).toBeInTheDocument();
    expect(screen.getByText("77,4")).toBeInTheDocument();
    expect(screen.getByText("Desayuno")).toBeInTheDocument();
    expect(screen.getByText("Comida")).toBeInTheDocument();
  });

  it("cada comida muestra sus propias calorías y proteína", async () => {
    render(<HoyPage />);

    // el valor aparece tanto en el resumen de ingredientes como en la insignia de la comida
    expect((await screen.findAllByText("475 kcal")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("850 kcal").length).toBeGreaterThan(0);
    expect(screen.getByText("32 g · Proteína")).toBeInTheDocument(); // redondeo visible
  });

  it("al cambiar de día se cargan las comidas de ese otro día", async () => {
    const user = userEvent.setup();
    render(<HoyPage />);
    await screen.findByText("Desayuno");

    await user.click(screen.getByRole("button", { name: "Día siguiente" }));

    expect(api.listMeals).toHaveBeenLastCalledWith(expect.any(String), expect.any(String));
    expect(vi.mocked(api.listMeals).mock.calls.length).toBeGreaterThanOrEqual(2);
  });

  it("aplicar una plantilla añade la comida, avisa al usuario y bloquea el botón mientras tanto", async () => {
    const user = userEvent.setup();
    let resolveCreate!: (value: MealDTO) => void;
    vi.mocked(api.createMeal).mockImplementationOnce(
      () => new Promise<MealDTO>((resolve) => (resolveCreate = resolve)),
    );
    vi.mocked(api.listTemplates).mockResolvedValue([
      template({ name: "Desayuno salvaje" }),
    ]);
    render(<HoyPage />);

    const chip = await screen.findByRole("button", { name: "Desayuno salvaje" });
    expect(vi.mocked(api.createMeal)).not.toHaveBeenCalled();

    await user.click(chip);

    expect(vi.mocked(api.createMeal)).toHaveBeenCalledTimes(1);
    expect(vi.mocked(api.createMeal)).toHaveBeenCalledWith(
      expect.objectContaining({
        title: "Desayuno salvaje",
        entryMode: "per_ingredient",
        logDate: expect.any(String),
      }),
    );

    // Mientras la petición vuela, el chip muestra "Añadiendo…" y no se puede re-disparar.
    const pendingChip = screen.getByRole("button", { name: "Añadiendo…" });
    expect(pendingChip).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Desayuno salvaje" })).toBeNull();

    resolveCreate(meal({ title: "Desayuno salvaje", kcal: 300, protein: 20 }));
    // Termina la petición: el chip vuelve a su estado normal y la lista se refresca.
    expect(await screen.findByRole("button", { name: "Desayuno salvaje" })).toBeEnabled();
    expect(screen.queryByRole("button", { name: "Añadiendo…" })).toBeNull();
  });
});

function meal(partial: { title: string; kcal: number; protein: number }): MealDTO {
  return {
    id: `meal-${partial.title}`,
    logDate: "2026-08-23",
    title: partial.title,
    notes: null,
    entryMode: "per_ingredient",
    ingredients: [{ name: "ingrediente", calories: partial.kcal, protein: partial.protein }],
    totalCalories: null,
    totalProtein: null,
    totalCarbs: null,
    totalFat: null,
    resolvedCalories: partial.kcal,
    resolvedProtein: partial.protein,
    resolvedCarbs: 0,
    resolvedFat: 0,
  };
}

function template(partial: { name: string }): MealTemplateDTO {
  return {
    id: `tpl-${partial.name}`,
    name: partial.name,
    title: partial.name,
    notes: null,
    ingredients: [{ name: "avena", calories: 150, protein: 5 }],
  };
}
