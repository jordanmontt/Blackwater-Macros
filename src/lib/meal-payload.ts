import { resolveMealTotals } from "./core/nutrition";
import type { MealContent, ResolvedTotals } from "./core/types";

/** A meal's or template's content as sent to the server: notes and totals may be left out. */
export type MealContentInput = Pick<MealContent, "title" | "entryMode" | "ingredients"> & Partial<MealContent>;

/**
 * The content of a meal or template as it is sent and stored: notes null when
 * missing, meal-level totals only for «Solo total» meals (as the form does).
 */
export function mealContent(source: MealContentInput): MealContent {
  const totalOnly = source.entryMode === "total_only";
  const total = (value: number | null | undefined) => (totalOnly ? (value ?? null) : null);
  return {
    title: source.title,
    notes: source.notes ?? null,
    entryMode: source.entryMode,
    ingredients: source.ingredients,
    totalCalories: total(source.totalCalories),
    totalProtein: total(source.totalProtein),
    totalCarbs: total(source.totalCarbs),
    totalFat: total(source.totalFat),
  };
}

/** The content plus its resolved totals, as the server (and the demo) stores it (Android `toEntity`). */
export function storedMealContent(source: MealContentInput): MealContent & ResolvedTotals {
  const content = mealContent(source);
  const totals = resolveMealTotals(
    content.entryMode,
    content.ingredients,
    content.totalCalories,
    content.totalProtein,
    content.totalCarbs,
    content.totalFat,
  );
  return {
    ...content,
    resolvedCalories: totals.calories,
    resolvedProtein: totals.protein,
    resolvedCarbs: totals.carbs,
    resolvedFat: totals.fat,
  };
}

/** A new meal on `logDate` with the same content as `source` (a template, or a meal copied from another day). */
export function copyMealPayload(source: MealContent, logDate: string): MealContent & { logDate: string } {
  return { logDate, ...mealContent(source) };
}
