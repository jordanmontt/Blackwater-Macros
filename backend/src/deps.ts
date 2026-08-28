import { eq } from "drizzle-orm";
import type { CalorieProfile, Gender, Goal } from "@/lib/types";
import { db, schema } from "@/server/db/client";
import { repositories, serviceDeps } from "@/server/composition";
import type { AuthServiceDeps } from "@/server/services/auth-service";
import type { StatsServiceDeps } from "@/server/services/stats-service";
import type { MealsRepository } from "@/server/repositories/meals-repo";
import type { MealTemplatesRepository } from "@/server/repositories/templates-repo";
import type { WeightsRepository } from "@/server/repositories/weights-repo";
import type { SettingsRepository } from "@/server/repositories/settings-repo";

/**
 * Everything the HTTP layer needs. Mirrors the shape of `composition.ts` plus
 * the session resolver, so tests can inject in-memory fakes for every route.
 */
export interface AppDeps {
  repositories: {
    meals: MealsRepository;
    templates: MealTemplatesRepository;
    weights: WeightsRepository;
    settings: SettingsRepository;
  };
  auth: AuthServiceDeps;
  stats: StatsServiceDeps;
  getSession: (
    userId: string,
  ) => Promise<{ username: string; calorieProfile: CalorieProfile } | null>;
}

/** Production wiring: real repositories against the shared DB pool. */
export const prodDeps: AppDeps = {
  repositories: {
    meals: repositories.meals,
    templates: repositories.templates,
    weights: repositories.weights,
    settings: repositories.settings,
  },
  auth: serviceDeps.auth,
  stats: serviceDeps.stats,
  async getSession(userId) {
    const rows = await db
      .select({
        username: schema.users.username,
        gender: schema.users.gender,
        birthYear: schema.users.birthYear,
        heightCm: schema.users.heightCm,
        gymDaysPerWeek: schema.users.gymDaysPerWeek,
        gymSessionMinutes: schema.users.gymSessionMinutes,
        walkingMinutesPerDay: schema.users.walkingMinutesPerDay,
        calorieGoal: schema.users.calorieGoal,
      })
      .from(schema.users)
      .where(eq(schema.users.id, userId))
      .limit(1);
    const row = rows[0];
    if (!row) return null;
    return {
      username: row.username,
      calorieProfile: {
        gender: (row.gender as Gender | null) ?? null,
        birthYear: row.birthYear,
        heightCm: row.heightCm,
        gymDaysPerWeek: row.gymDaysPerWeek,
        gymSessionMinutes: row.gymSessionMinutes,
        walkingMinutesPerDay: row.walkingMinutesPerDay,
        calorieGoal: (row.calorieGoal as Goal | null) ?? null,
      },
    };
  },
};