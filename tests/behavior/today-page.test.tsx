import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import HoyPage from "@/app/page";
import type { MealDTO } from "@/lib/types";

/**
 * Requisitos visibles en la pantalla "Hoy":
 *  - el usuario ve el total acumulado de calorías y proteína del día,
 *    calculado con todas las comidas registradas,
 *  - puede avanzar/retroceder de día para revisar comidas pasadas,
 *  - si no hay comidas, la pantalla lo deja claro e invita a añadir.
 */

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {},
  api: {
    listMeals: vi.fn(async () => [
      meal({ title: "Desayuno", kcal: 475, protein: 32.4 }),
      meal({ title: "Comida", kcal: 850, protein: 45 }),
    ]),
    listTemplates: vi.fn(async () => []),
    createMeal: vi.fn(),
  },
}));

vi.mock("@/components/theme-toggle", () => ({
  ThemeToggle: () => <button aria-label="Cambiar tema" />,
}));

import { api } from "@/lib/api";

describe("pantalla Hoy", () => {
  beforeEach(() => {
    vi.mocked(api.listMeals).mockClear();
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

    expect(await screen.findByText("475 kcal")).toBeInTheDocument();
    expect(screen.getByText("850 kcal")).toBeInTheDocument();
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
    resolvedCalories: partial.kcal,
    resolvedProtein: partial.protein,
  };
}
