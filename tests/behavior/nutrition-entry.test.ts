import { describe, expect, it } from "vitest";
import { createMeal } from "@/server/services/meals-service";
import type { MealInput } from "@/server/validation";
import { resolveMealTotals, sumIngredientNutrition } from "@/lib/nutrition";
import type { IngredientInput } from "@/lib/types";

/**
 * Requisito: el usuario puede calcular la nutrición de dos formas:
 *   A) introduciendo calorías y proteína por ingrediente (la app las suma),
 *   B) introduciendo solo el total de la comida.
 * Estas pruebas describen ese comportamiento desde fuera, como lo vive el
 * usuario, sin depender de cómo se guarde nada en la base de datos.
 */

describe("registro flexible de nutrición", () => {
  it("modo A: suma automáticamente las calorías y proteína de cada ingrediente", () => {
    const totals = resolveMealTotals(
      "per_ingredient",
      [
        { name: "4 huevos", calories: 280, protein: 24 },
        { name: "2 cucharillas de aceite de oliva", calories: 80, protein: 0 },
        { name: "30-40 gramos queso Gouda", quantity: "35 g", calories: 115, protein: 8.4 },
      ],
      null,
      null,
    );
    expect(totals.calories).toBeCloseTo(475);
    expect(totals.protein).toBeCloseTo(32.4);
  });

  it("modo A: los ingredientes sin valores no rompen la suma (cuentan como 0)", () => {
    const totals = resolveMealTotals(
      "per_ingredient",
      [
        { name: "café con leche" },
        { name: "tostada", calories: 120, protein: 4 },
      ],
      null,
      null,
    );
    expect(totals.calories).toBe(120);
    expect(totals.protein).toBe(4);
  });

  it("modo B: usa el total manual aunque también haya ingredientes anotados", () => {
    const totals = resolveMealTotals(
      "total_only",
      [{ name: "menú del día (sin detallar)" }],
      850,
      45,
    );
    expect(totals).toEqual({ calories: 850, protein: 45 });
  });

  it("los decimales se conservan redondeados a dos cifras", () => {
    const summed = sumIngredientNutrition([
      { name: "a", protein: 0.105 },
      { name: "b", protein: 0.105 },
    ] satisfies IngredientInput[]);
    expect(summed.protein).toBeCloseTo(0.21, 10);
  });
});

describe("al guardar una comida ya queda calculado su total", () => {
  const makeDeps = () => {
    const saved: unknown[] = [];
    return {
      deps: {
        meals: {
          async create(_userId: string, data: unknown) {
            saved.push(data);
            return { id: "meal-1", ...(data as object), ingredients: (data as MealInput["ingredients"]) };
          },
          async listInRange() {
            return [];
          },
          async getById() {
            return null;
          },
          async update() {
            return null;
          },
          async delete() {
            return true;
          },
        },
      },
      saved,
    };
  };

  it("una comida por ingredientes guarda el total ya sumado junto al resto de datos", async () => {
    const { deps, saved } = makeDeps();
    const input: MealInput = {
      logDate: "2026-08-23",
      title: "Desayuno",
      notes: null,
      entryMode: "per_ingredient",
      ingredients: [
        { name: "4 huevos", calories: 280, protein: 24 },
        { name: "pan", calories: 90, protein: 3 },
      ],
      totalCalories: null,
      totalProtein: null,
    };

    const dto = await createMeal(deps as never, "user-1", input);

    expect(dto.resolvedCalories).toBe(370);
    expect(dto.resolvedProtein).toBe(27);
    expect(saved[0]).toMatchObject({ logDate: "2026-08-23", title: "Desayuno" });
  });
});
