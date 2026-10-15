import { toCsv } from "@/lib/core/csv";
import { MEAL_HEADER, WEIGHT_HEADER } from "@/lib/csv-import";
import type { MealRow, WeightRow } from "../db/schema";

/**
 * Builds the meals CSV (columns: `MEAL_HEADER`, the format `csv-import.ts` reads
 * back): one row per ingredient with the meal context repeated, plus the
 * meal-level totals. Total-only meals produce a single row where the
 * ingredient columns are empty and only the totals are filled.
 */
export function buildMealsCsv(meals: MealRow[]): string {
  const noIngredient = [null, null, null, null, null, null];
  const noTotals = [null, null, null, null];
  const rows: (string | number | null)[][] = [MEAL_HEADER];
  for (const meal of meals) {
    const context = [meal.logDate, meal.title, meal.entryMode, meal.notes];
    if (meal.entryMode === "total_only") {
      rows.push([...context, ...noIngredient, meal.totalCalories, meal.totalProtein, meal.totalCarbs, meal.totalFat]);
    } else if (meal.ingredients.length === 0) {
      rows.push([...context, ...noIngredient, ...noTotals]);
    } else {
      for (const ingredient of meal.ingredients) {
        rows.push([
          ...context,
          ingredient.name,
          ingredient.quantity ?? null,
          ingredient.calories ?? null,
          ingredient.protein ?? null,
          ingredient.carbs ?? null,
          ingredient.fat ?? null,
          ...noTotals,
        ]);
      }
    }
  }
  return toCsv(rows);
}

/** Builds the weight CSV: one row per weigh-in, newest-friendly ascending order. */
export function buildWeightsCsv(weights: WeightRow[]): string {
  const rows: (string | number | null)[][] = weights.map((row) => [
    row.measuredAt.toISOString(),
    row.weightKg,
    row.bodyFatPct,
    row.note,
  ]);
  return toCsv([WEIGHT_HEADER, ...rows]);
}
