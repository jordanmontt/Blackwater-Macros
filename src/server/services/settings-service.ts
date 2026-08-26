import type { ProteinGoal, UserSettings } from "@/lib/types";
import type { SettingsRepository } from "../repositories/settings-repo";

export async function getSettings(repo: SettingsRepository, userId: string): Promise<UserSettings> {
  const proteinGoal = await repo.getProteinGoal(userId);
  return { proteinGoal };
}

export async function updateSettings(
  repo: SettingsRepository,
  userId: string,
  input: { proteinGoal: ProteinGoal },
): Promise<UserSettings> {
  await repo.updateProteinGoal(userId, input.proteinGoal);
  return { proteinGoal: input.proteinGoal };
}
