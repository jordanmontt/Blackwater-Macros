import type { CalorieProfile, UserSettings } from "@/lib/types";
import type { SettingsRepository } from "../repositories/settings-repo";

export async function getSettings(repo: SettingsRepository, userId: string): Promise<UserSettings> {
  const calorieProfile = await repo.getCalorieProfile(userId);
  return { calorieProfile };
}

export async function updateCalorieProfile(
  repo: SettingsRepository,
  userId: string,
  profile: CalorieProfile,
): Promise<UserSettings> {
  await repo.updateCalorieProfile(userId, profile);
  return getSettings(repo, userId);
}
