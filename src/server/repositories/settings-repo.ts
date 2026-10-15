import { eq } from "drizzle-orm";
import type { AppDb } from "../db/client";
import { users } from "../db/schema";
import type { CalorieProfile, Gender, Goal, ProfilePeriod } from "@/lib/core/types";

export interface SettingsRepository {
  getCalorieProfile(userId: string): Promise<CalorieProfile>;
  getProfileHistory(userId: string): Promise<ProfilePeriod[]>;
  /** `history` undefined leaves the stored history as it is. */
  updateCalorieProfile(userId: string, profile: CalorieProfile, history?: ProfilePeriod[]): Promise<void>;
}

export function createSettingsRepository(db: AppDb): SettingsRepository {
  return {
    async getCalorieProfile(userId) {
      const rows = await db
        .select({
          gender: users.gender,
          birthYear: users.birthYear,
          heightCm: users.heightCm,
          gymDaysPerWeek: users.gymDaysPerWeek,
          gymSessionMinutes: users.gymSessionMinutes,
          walkingMinutesPerDay: users.walkingMinutesPerDay,
          calorieGoal: users.calorieGoal,
        })
        .from(users)
        .where(eq(users.id, userId))
        .limit(1);
      const row = rows[0];
      return {
        gender: (row?.gender as Gender | undefined) ?? null,
        birthYear: row?.birthYear ?? null,
        heightCm: row?.heightCm ?? null,
        gymDaysPerWeek: row?.gymDaysPerWeek ?? null,
        gymSessionMinutes: row?.gymSessionMinutes ?? null,
        walkingMinutesPerDay: row?.walkingMinutesPerDay ?? null,
        calorieGoal: (row?.calorieGoal as Goal | undefined) ?? null,
      };
    },
    async getProfileHistory(userId) {
      const rows = await db
        .select({ profileHistory: users.profileHistory })
        .from(users)
        .where(eq(users.id, userId))
        .limit(1);
      return rows[0]?.profileHistory ?? [];
    },
    async updateCalorieProfile(userId, profile, history) {
      await db
        .update(users)
        .set({
          gender: profile.gender,
          birthYear: profile.birthYear,
          heightCm: profile.heightCm,
          gymDaysPerWeek: profile.gymDaysPerWeek,
          gymSessionMinutes: profile.gymSessionMinutes,
          walkingMinutesPerDay: profile.walkingMinutesPerDay,
          calorieGoal: profile.calorieGoal,
          ...(history === undefined ? {} : { profileHistory: history }),
        })
        .where(eq(users.id, userId));
    },
  };
}
