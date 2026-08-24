import type { EntryMode, IngredientInput } from "./types";

export interface NutritionTotals {
  calories: number;
  protein: number;
}

/**
 * Sums the nutrition values that were entered for each ingredient.
 * Ingredients without values simply contribute zero.
 */
export function sumIngredientNutrition(ingredients: IngredientInput[]): NutritionTotals {
  return ingredients.reduce<NutritionTotals>(
    (acc, ingredient) => ({
      calories: acc.calories + (ingredient.calories ?? 0),
      protein: acc.protein + (ingredient.protein ?? 0),
    }),
    { calories: 0, protein: 0 },
  );
}

/**
 * Resolves the nutrition totals of a meal according to its entry mode:
 * - "total_only": uses the manually entered meal-level values.
 * - "per_ingredient": sums whatever was entered per ingredient.
 */
export function resolveMealTotals(
  entryMode: EntryMode,
  ingredients: IngredientInput[],
  manualTotalCalories?: number | null,
  manualTotalProtein?: number | null,
): NutritionTotals {
  if (entryMode === "total_only") {
    return {
      calories: round2(manualTotalCalories ?? 0),
      protein: round2(manualTotalProtein ?? 0),
    };
  }
  const summed = sumIngredientNutrition(ingredients);
  return { calories: round2(summed.calories), protein: round2(summed.protein) };
}

export function round1(value: number): number {
  return Math.round(value * 10) / 10;
}

export function round2(value: number): number {
  return Math.round(value * 100) / 100;
}
