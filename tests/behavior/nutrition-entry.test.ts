import { describe, expect, it } from "vitest";
import { createMeal } from "@/server/services/meals-service";
import type { MealInput } from "@/server/validation";
import { resolveMealTotals, round1, round2, sumIngredientNutrition } from "@/lib/nutrition";
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
        { name: "4 huevos", calories: 280, protein: 24, carbs: 2, fat: 20 },
        { name: "2 cucharillas de aceite de oliva", calories: 80, protein: 0, carbs: 0, fat: 9 },
        { name: "30-40 gramos queso Gouda", quantity: "35 g", calories: 115, protein: 8.4, carbs: 1, fat: 9 },
      ],
      null,
      null,
    );
    expect(totals.calories).toBeCloseTo(475);
    expect(totals.protein).toBeCloseTo(32.4);
    expect(totals.carbs).toBeCloseTo(3);
    expect(totals.fat).toBeCloseTo(38);
  });

  it("modo A: los ingredientes sin valores no rompen la suma (cuentan como 0)", () => {
    const totals = resolveMealTotals(
      "per_ingredient",
      [
        { name: "café con leche" },
        { name: "tostada", calories: 120, protein: 4, carbs: 20, fat: 3 },
      ],
      null,
      null,
    );
    expect(totals.calories).toBe(120);
    expect(totals.protein).toBe(4);
    expect(totals.carbs).toBe(20);
    expect(totals.fat).toBe(3);
  });

  it("modo B: usa el total manual aunque también haya ingredientes anotados", () => {
    const totals = resolveMealTotals(
      "total_only",
      [{ name: "menú del día (sin detallar)" }],
      850,
      45,
      80,
      35,
    );
    expect(totals).toEqual({ calories: 850, protein: 45, carbs: 80, fat: 35 });
  });

  it("los decimales se conservan redondeados a dos cifras", () => {
    const summed = sumIngredientNutrition([
      { name: "a", protein: 0.105 },
      { name: "b", protein: 0.105 },
    ] satisfies IngredientInput[]);
    expect(summed.protein).toBeCloseTo(0.21, 10);
    expect(summed.carbs).toBe(0);
    expect(summed.fat).toBe(0);
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
            return { id: "meal-1", sortOrder: 0, ...(data as object) };
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
          async reorder() {},
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
        { name: "4 huevos", calories: 280, protein: 24, carbs: 2, fat: 20 },
        { name: "pan", calories: 90, protein: 3, carbs: 16, fat: 1 },
      ],
      totalCalories: null,
      totalProtein: null,
    };

    const dto = await createMeal(deps.meals as never, "user-1", input);

    expect(dto.resolvedCalories).toBe(370);
    expect(dto.resolvedProtein).toBe(27);
    expect(saved[0]).toMatchObject({ logDate: "2026-08-23", title: "Desayuno" });
  });

  it("una comida total_only guarda los totales manuales en resolved y en total", async () => {
    const { deps, saved } = makeDeps();
    const input: MealInput = {
      logDate: "2026-08-24",
      title: "Menú del día",
      notes: null,
      entryMode: "total_only",
      ingredients: [],
      totalCalories: 850,
      totalProtein: 45,
      totalCarbs: 80,
      totalFat: 35,
    };

    const dto = await createMeal(deps.meals as never, "user-1", input);

    expect(dto.resolvedCalories).toBe(850);
    expect(dto.resolvedProtein).toBe(45);
    expect(dto.resolvedCarbs).toBe(80);
    expect(dto.resolvedFat).toBe(35);
    expect(dto.totalCalories).toBe(850);
    expect(dto.totalProtein).toBe(45);
    expect(dto.totalCarbs).toBe(80);
    expect(dto.totalFat).toBe(35);
    expect(saved[0]).toMatchObject({
      entryMode: "total_only",
      totalCalories: 850,
      totalProtein: 45,
    });
  });
});

describe("redondeo de valores nutricionales", () => {
  it("round1 redondea a 1 decimal", () => {
    expect(round1(1.25)).toBe(1.3);
    expect(round1(1.24)).toBe(1.2);
    expect(round1(1.0)).toBe(1);
    expect(round1(0.05)).toBe(0.1);
    expect(round1(-1.26)).toBe(-1.3);
  });

  it("round2 redondea a 2 decimales", () => {
    expect(round2(1.256)).toBe(1.26);
    expect(round2(1.254)).toBe(1.25);
    expect(round2(0.015)).toBe(0.02);
    expect(round2(-1.256)).toBe(-1.26);
  });

  it("round1 y round2 preservan enteros", () => {
    expect(round1(5)).toBe(5);
    expect(round2(5)).toBe(5);
  });
});
