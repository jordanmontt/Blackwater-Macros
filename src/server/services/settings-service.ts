import type { CalorieProfile, ProteinGoal, UserSettings } from "@/lib/types";
import type { SettingsRepository } from "../repositories/settings-repo";

export async function getSettings(repo: SettingsRepository, userId: string): Promise<UserSettings> {
  const [proteinGoal, calorieProfile] = await Promise.all([
    repo.getProteinGoal(userId),
    repo.getCalorieProfile(userId),
  ]);
  return { proteinGoal, calorieProfile };
}

export async function updateSettings(
  repo: SettingsRepository,
  userId: string,
  input: { proteinGoal: ProteinGoal },
): Promise<UserSettings> {
  await repo.updateProteinGoal(userId, input.proteinGoal);
  return getSettings(repo, userId);
}

export async function updateCalorieProfile(
  repo: SettingsRepository,
  userId: string,
  profile: CalorieProfile,
): Promise<UserSettings> {
  await repo.updateCalorieProfile(userId, profile);
  return getSettings(repo, userId);
}
