import { toCsv } from "@/lib/csv";
import type { MealRow, WeightRow } from "../db/schema";

/**
 * Builds the meals CSV: one row per ingredient with the meal context repeated,
 * plus the meal-level totals. Total-only meals produce a single row where the
 * ingredient columns are empty and only the totals are filled.
 */
export function buildMealsCsv(meals: MealRow[]): string {
  const header = [
    "fecha",
    "comida",
    "modo",
    "notas",
    "ingrediente",
    "cantidad",
    "kcal_ingrediente",
    "proteina_ingrediente_g",
    "total_kcal_comida",
    "total_proteina_comida_g",
  ];

  const rows: (string | number | null)[][] = [header];
  for (const meal of meals) {
    if (meal.entryMode === "total_only") {
      rows.push([
        meal.logDate,
        meal.title,
        meal.entryMode,
        meal.notes,
        null,
        null,
        null,
        null,
        meal.totalCalories,
        meal.totalProtein,
      ]);
      continue;
    }
    if (meal.ingredients.length === 0) {
      rows.push([meal.logDate, meal.title, meal.entryMode, meal.notes, null, null, null, null, null, null]);
      continue;
    }
    for (const ingredient of meal.ingredients) {
      rows.push([
        meal.logDate,
        meal.title,
        meal.entryMode,
        meal.notes,
        ingredient.name,
        ingredient.quantity ?? null,
        ingredient.calories ?? null,
        ingredient.protein ?? null,
        null,
        null,
      ]);
    }
  }
  return toCsv(rows);
}

/** Builds the weight CSV: one row per weigh-in, newest-friendly ascending order. */
export function buildWeightsCsv(weights: WeightRow[]): string {
  const header = ["fecha_hora", "peso_kg", "nota"];
  const rows: (string | number | null)[][] = weights.map((row) => [
    row.measuredAt.toISOString(),
    row.weightKg,
    row.note,
  ]);
  return toCsv([header, ...rows]);
}
