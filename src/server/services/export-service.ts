import { buildBackup } from "@/lib/core/backup";
import type { IngredientInput } from "@/lib/core/types";
import type { MealsRepository } from "../repositories/meals-repo";
import type { SettingsRepository } from "../repositories/settings-repo";
import type { MealTemplatesRepository } from "../repositories/templates-repo";
import type { WeightsRepository } from "../repositories/weights-repo";

export interface ExportRepositories {
  meals: MealsRepository;
  templates: MealTemplatesRepository;
  weights: WeightsRepository;
  settings: SettingsRepository;
}

/**
 * The user's whole export file (`core/backup.ts`): profile and past profiles,
 * weigh-ins oldest first, meals in day order, templates by name.
 */
export async function buildUserBackup(repos: ExportRepositories, userId: string, exportedAt: Date): Promise<string> {
  const [profile, profileHistory, weights, meals, templates] = await Promise.all([
    repos.settings.getCalorieProfile(userId),
    repos.settings.getProfileHistory(userId),
    repos.weights.listForUser(userId),
    repos.meals.listInRange(userId, null, null),
    repos.templates.listForUser(userId),
  ]);
  return buildBackup(
    {
      profile,
      profileHistory,
      weights: weights.map((row) => ({
        measuredAt: row.measuredAt.toISOString(),
        weightKg: row.weightKg,
        bodyFatPct: row.bodyFatPct,
        note: row.note,
      })),
      meals: meals.map((row) => ({ ...row, ingredients: row.ingredients as IngredientInput[] })),
      templates: templates.map((row) => ({ ...row, ingredients: row.ingredients as IngredientInput[] })),
    },
    exportedAt.toISOString(),
  );
}
