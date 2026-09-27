import { mealImportKey, weightImportKey } from "@/lib/csv-import";
import { resolveMealTotals, round2 } from "@/lib/core/nutrition";
import type { IngredientInput } from "@/lib/core/types";
import type { MealsRepository } from "../repositories/meals-repo";
import type { WeightsRepository } from "../repositories/weights-repo";
import type { MealInput, WeightInput } from "../validation";
import { createMeal } from "./meals-service";
import { createWeight } from "./weights-service";

export interface ImportResult {
  added: number;
  /** Already stored (the same file imported twice, or data from the phone). */
  skipped: number;
}

/** Adds the meals from a CSV backup, skipping any identical one already stored. */
export async function importMeals(repo: MealsRepository, userId: string, meals: MealInput[]): Promise<ImportResult> {
  const known = new Set(
    (await repo.listInRange(userId, null, null)).map((row) =>
      mealImportKey({ ...row, ingredients: row.ingredients as IngredientInput[] }),
    ),
  );
  let added = 0;
  for (const meal of meals) {
    const totals = resolveMealTotals(
      meal.entryMode,
      meal.ingredients,
      meal.totalCalories ?? null,
      meal.totalProtein ?? null,
      meal.totalCarbs ?? null,
      meal.totalFat ?? null,
    );
    const key = mealImportKey({ ...meal, resolvedCalories: totals.calories, resolvedProtein: totals.protein });
    if (known.has(key)) continue;
    known.add(key);
    await createMeal(repo, userId, meal);
    added++;
  }
  return { added, skipped: meals.length - added };
}

/** Adds the weigh-ins from a CSV backup, skipping any with the same time and kilos. */
export async function importWeights(
  repo: WeightsRepository,
  userId: string,
  weights: WeightInput[],
): Promise<ImportResult> {
  const known = new Set(
    (await repo.listForUser(userId)).map((row) =>
      weightImportKey({ measuredAt: row.measuredAt.toISOString(), weightKg: row.weightKg }),
    ),
  );
  let added = 0;
  for (const weight of weights) {
    // Stored with 2 decimals (weights-service), so compare that way.
    const key = weightImportKey({ measuredAt: weight.measuredAt, weightKg: round2(weight.weightKg) });
    if (known.has(key)) continue;
    known.add(key);
    await createWeight(repo, userId, weight);
    added++;
  }
  return { added, skipped: weights.length - added };
}
