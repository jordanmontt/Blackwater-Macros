import type { ProteinGoal, UserSettings } from "@/lib/types";
import type { SettingsRepository } from "../repositories/settings-repo";

export interface SettingsServiceDeps {
  settings: SettingsRepository;
}

export async function getSettings(deps: SettingsServiceDeps, userId: string): Promise<UserSettings> {
  const proteinGoal = await deps.settings.getProteinGoal(userId);
  return { proteinGoal };
}

export async function updateSettings(
  deps: SettingsServiceDeps,
  userId: string,
  input: { proteinGoal: ProteinGoal },
): Promise<UserSettings> {
  await deps.settings.updateProteinGoal(userId, input.proteinGoal);
  return { proteinGoal: input.proteinGoal };
}
