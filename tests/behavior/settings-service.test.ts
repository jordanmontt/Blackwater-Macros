import { describe, expect, it } from "vitest";
import { getSettings, updateSettings } from "@/server/services/settings-service";
import type { SettingsRepository } from "@/server/repositories/settings-repo";
import type { CalorieProfile, ProteinGoal } from "@/lib/types";

function memorySettings(initial: ProteinGoal = "build"): SettingsRepository {
  const store = new Map<string, ProteinGoal>();
  const calorieProfiles = new Map<string, CalorieProfile>();

  const emptyProfile: CalorieProfile = {
    gender: null,
    birthYear: null,
    heightCm: null,
    gymDaysPerWeek: null,
    gymSessionMinutes: null,
    walkingMinutesPerDay: null,
    calorieGoal: null,
  };

  return {
    async getProteinGoal(userId) {
      return store.get(userId) ?? initial;
    },
    async updateProteinGoal(userId, goal) {
      store.set(userId, goal);
    },
    async getCalorieProfile(userId) {
      return calorieProfiles.get(userId) ?? emptyProfile;
    },
    async updateCalorieProfile(userId, profile) {
      calorieProfiles.set(userId, profile);
    },
  };
}

describe("gestión de ajustes", () => {
  it("getSettings devuelve el objetivo por defecto 'build' cuando no hay valor guardado", async () => {
    const repo = memorySettings();
    const settings = await getSettings(repo, "user-1");
    expect(settings.proteinGoal).toBe("build");
  });

  it("updateSettings cambia el objetivo y getSettings lo refleja", async () => {
    const repo = memorySettings();
    await updateSettings(repo, "user-1", { proteinGoal: "cut" });

    const settings = await getSettings(repo, "user-1");
    expect(settings.proteinGoal).toBe("cut");
  });

  it("cada usuario tiene su propio objetivo independiente", async () => {
    const repo = memorySettings();
    await updateSettings(repo, "user-1", { proteinGoal: "cut" });
    await updateSettings(repo, "user-2", { proteinGoal: "maintain" });

    expect((await getSettings(repo, "user-1")).proteinGoal).toBe("cut");
    expect((await getSettings(repo, "user-2")).proteinGoal).toBe("maintain");
  });

  it("updateSettings devuelve el objetivo actualizado", async () => {
    const repo = memorySettings();
    const result = await updateSettings(repo, "user-1", { proteinGoal: "build" });
    expect(result.proteinGoal).toBe("build");
  });

  it("todos los valores válidos de ProteinGoal son aceptados", async () => {
    const goals: ProteinGoal[] = ["maintain", "build", "cut"];
    for (const goal of goals) {
      const repo = memorySettings();
      await updateSettings(repo, "user-1", { proteinGoal: goal });
      expect((await getSettings(repo, "user-1")).proteinGoal).toBe(goal);
    }
  });

  it("getSettings incluye calorieProfile por defecto vacío", async () => {
    const repo = memorySettings();
    const settings = await getSettings(repo, "user-1");
    expect(settings.calorieProfile.gender).toBeNull();
    expect(settings.calorieProfile.calorieGoal).toBeNull();
  });
});
