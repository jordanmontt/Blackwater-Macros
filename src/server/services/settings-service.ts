import type { CalorieProfile, ProfilePeriod, UserSettings } from "@/lib/core/types";
import type { SettingsRepository } from "../repositories/settings-repo";

export async function getSettings(repo: SettingsRepository, userId: string): Promise<UserSettings> {
  const [calorieProfile, profileHistory] = await Promise.all([
    repo.getCalorieProfile(userId),
    repo.getProfileHistory(userId),
  ]);
  return { calorieProfile, profileHistory };
}

/**
 * Saves the profile, and the history when given. Without it (apps from before
 * the history existed) the stored history is kept.
 */
export async function updateCalorieProfile(
  repo: SettingsRepository,
  userId: string,
  profile: CalorieProfile,
  history?: ProfilePeriod[],
): Promise<UserSettings> {
  await repo.updateCalorieProfile(userId, profile, history);
  return getSettings(repo, userId);
}
