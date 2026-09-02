import { describe, expect, it } from "vitest";
import {
  resolveMealTotals,
  round1,
  round2,
  sumIngredientNutrition,
} from "../../src/lib/core/nutrition";
import type { IngredientInput } from "../../src/lib/core/types";

describe("sumIngredientNutrition", () => {
  it("suma los macro/micro por ingrediente", () => {
    const ingredients: IngredientInput[] = [
      { name: "Avena", calories: 150, protein: 5, carbs: 27, fat: 3 },
      { name: "Leche", calories: 100, protein: 3.5, carbs: 5, fat: 7 },
    ];
    expect(sumIngredientNutrition(ingredients)).toEqual({
      calories: 250,
      protein: 8.5,
      carbs: 32,
      fat: 10,
    });
  });

  it("una lista vacía devuelve todos ceros", () => {
    expect(sumIngredientNutrition([])).toEqual({
      calories: 0,
      protein: 0,
      carbs: 0,
      fat: 0,
    });
  });

  it("los ingredientes sin valores aportan cero", () => {
    const ingredients: IngredientInput[] = [
      { name: "Solo nombre" },
      { name: "Solo kcal", calories: 300 },
    ];
    expect(sumIngredientNutrition(ingredients)).toEqual({
      calories: 300,
      protein: 0,
      carbs: 0,
      fat: 0,
    });
  });

  it("un solo ingrediente con una parte nulable", () => {
    const ingredients: IngredientInput[] = [
      { name: "Huevo", calories: 70, protein: 6, carbs: 0.6, fat: undefined },
    ];
    expect(sumIngredientNutrition(ingredients).fat).toBe(0);
  });
});

describe("resolveMealTotals", () => {
  it("en modo total_only usa los totales manuales", () => {
    expect(
      resolveMealTotals("total_only", [], 700, 35, 90, 20),
    ).toEqual({ calories: 700, protein: 35, carbs: 90, fat: 20 });
  });

  it("en modo total_only redondea a 2 decimales", () => {
    expect(
      resolveMealTotals("total_only", [], 700.006, 35.006, 0, 0),
    ).toEqual({ calories: 700.01, protein: 35.01, carbs: 0, fat: 0 });
  });

  it("en modo per_ingredient suma los ingredientes", () => {
    const ingredients: IngredientInput[] = [
      { name: "A", calories: 100, protein: 4, carbs: 20, fat: 2 },
      { name: "B", calories: 200, protein: 8, carbs: 10, fat: 5 },
    ];
    expect(resolveMealTotals("per_ingredient", ingredients)).toEqual({
      calories: 300,
      protein: 12,
      carbs: 30,
      fat: 7,
    });
  });

  it("en modo per_ingredient ignora los totales manuales", () => {
    const ingredients: IngredientInput[] = [
      { name: "A", calories: 100, protein: 4, carbs: 20, fat: 2 },
    ];
    expect(resolveMealTotals("per_ingredient", ingredients, 999, 999, 999, 999)).toEqual({
      calories: 100,
      protein: 4,
      carbs: 20,
      fat: 2,
    });
  });

  it("sin ingredientes y sin totales devuelve ceros", () => {
    expect(resolveMealTotals("per_ingredient", [])).toEqual({
      calories: 0,
      protein: 0,
      carbs: 0,
      fat: 0,
    });
  });
});

describe("round1", () => {
  it("redondea a un decimal", () => {
    expect(round1(3.14159)).toBe(3.1);
    expect(round1(1.25)).toBe(1.3);
    expect(round1(0.05)).toBe(0.1);
  });
});

describe("round2", () => {
  it("redondea a dos decimales", () => {
    expect(round2(3.14159)).toBe(3.14);
    expect(round2(1.006)).toBe(1.01);
    expect(round2(0)).toBe(0);
  });
});
