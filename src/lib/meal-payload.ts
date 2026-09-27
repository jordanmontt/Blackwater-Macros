import type { MealPayload } from "./api";
import type { EntryMode, IngredientInput } from "./core/types";

/** The nutrition fields a meal and a template share. */
export interface MealContent {
  title: string;
  notes: string | null;
  entryMode: EntryMode;
  ingredients: IngredientInput[];
  totalCalories: number | null;
  totalProtein: number | null;
  totalCarbs: number | null;
  totalFat: number | null;
}

/**
 * A new meal on `logDate` with the same content as `source` (a template, or a
 * meal copied from another day). Totals are only sent for «Solo total» meals,
 * as the form does.
 */
export function copyMealPayload(source: MealContent, logDate: string): MealPayload {
  const totalOnly = source.entryMode === "total_only";
  return {
    logDate,
    title: source.title,
    notes: source.notes,
    entryMode: source.entryMode,
    ingredients: source.ingredients,
    totalCalories: totalOnly ? source.totalCalories : null,
    totalProtein: totalOnly ? source.totalProtein : null,
    totalCarbs: totalOnly ? source.totalCarbs : null,
    totalFat: totalOnly ? source.totalFat : null,
  };
}
