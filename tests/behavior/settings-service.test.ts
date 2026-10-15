import { describe, expect, it } from "vitest";
import { getSettings, updateCalorieProfile } from "@/server/services/settings-service";
import type { SettingsRepository } from "@/server/repositories/settings-repo";
import type { CalorieProfile, ProfilePeriod } from "@/lib/core/types";

function memorySettings(): SettingsRepository {
  const calorieProfiles = new Map<string, CalorieProfile>();
  const histories = new Map<string, ProfilePeriod[]>();

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
    async getCalorieProfile(userId) {
      return calorieProfiles.get(userId) ?? emptyProfile;
    },
    async getProfileHistory(userId) {
      return histories.get(userId) ?? [];
    },
    async updateCalorieProfile(userId, profile, history) {
      calorieProfiles.set(userId, profile);
      if (history !== undefined) histories.set(userId, history);
    },
  };
}

describe("gestión de ajustes", () => {
  it("getSettings devuelve calorieProfile vacío por defecto", async () => {
    const repo = memorySettings();
    const settings = await getSettings(repo, "user-1");
    expect(settings.calorieProfile.calorieGoal).toBeNull();
    expect(settings.calorieProfile.gender).toBeNull();
  });

  it("updateCalorieProfile cambia el objetivo y getSettings lo refleja", async () => {
    const repo = memorySettings();
    await updateCalorieProfile(repo, "user-1", {
      gender: "male",
      birthYear: 1990,
      heightCm: 178,
      gymDaysPerWeek: 3,
      gymSessionMinutes: 60,
      walkingMinutesPerDay: 30,
      calorieGoal: "cut",
    });

    const settings = await getSettings(repo, "user-1");
    expect(settings.calorieProfile.calorieGoal).toBe("cut");
    expect(settings.calorieProfile.gender).toBe("male");
  });

  it("cada usuario tiene su propio perfil independiente", async () => {
    const repo = memorySettings();
    await updateCalorieProfile(repo, "user-1", {
      gender: "male",
      birthYear: 1990,
      heightCm: 178,
      gymDaysPerWeek: 3,
      gymSessionMinutes: 60,
      walkingMinutesPerDay: 30,
      calorieGoal: "cut",
    });
    await updateCalorieProfile(repo, "user-2", {
      gender: "female",
      birthYear: 1985,
      heightCm: 165,
      gymDaysPerWeek: 2,
      gymSessionMinutes: 45,
      walkingMinutesPerDay: 20,
      calorieGoal: "maintain",
    });

    expect((await getSettings(repo, "user-1")).calorieProfile.calorieGoal).toBe("cut");
    expect((await getSettings(repo, "user-2")).calorieProfile.calorieGoal).toBe("maintain");
  });

  it("updateCalorieProfile devuelve el perfil actualizado", async () => {
    const repo = memorySettings();
    const result = await updateCalorieProfile(repo, "user-1", {
      gender: "female",
      birthYear: 1995,
      heightCm: 160,
      gymDaysPerWeek: 4,
      gymSessionMinutes: 50,
      walkingMinutesPerDay: 40,
      calorieGoal: "surplus",
    });
    expect(result.calorieProfile.calorieGoal).toBe("surplus");
    expect(result.calorieProfile.gender).toBe("female");
  });

  it("todos los valores válidos de Goal son aceptados", async () => {
    const goals: Array<"cut" | "maintain" | "surplus"> = ["cut", "maintain", "surplus"];
    for (const goal of goals) {
      const repo = memorySettings();
      await updateCalorieProfile(repo, "user-1", {
        gender: "male",
        birthYear: 1990,
        heightCm: 178,
        gymDaysPerWeek: 3,
        gymSessionMinutes: 60,
        walkingMinutesPerDay: 30,
        calorieGoal: goal,
      });
      expect((await getSettings(repo, "user-1")).calorieProfile.calorieGoal).toBe(goal);
    }
  });

  it("guarda el historial de perfiles y lo conserva si no se envía (apps anteriores)", async () => {
    const repo = memorySettings();
    const before: CalorieProfile = {
      gender: "female",
      birthYear: 1995,
      heightCm: 165,
      gymDaysPerWeek: 2,
      gymSessionMinutes: 60,
      walkingMinutesPerDay: 20,
      calorieGoal: "cut",
    };
    const history = [{ ...before, until: "2026-10-14" }];
    const saved = await updateCalorieProfile(repo, "user-1", { ...before, gymDaysPerWeek: 4 }, history);
    expect(saved.profileHistory).toEqual(history);

    const fromOldApp = await updateCalorieProfile(repo, "user-1", { ...before, gymDaysPerWeek: 5 });
    expect(fromOldApp.calorieProfile.gymDaysPerWeek).toBe(5);
    expect(fromOldApp.profileHistory).toEqual(history);
  });

  it("el historial está vacío por defecto", async () => {
    expect((await getSettings(memorySettings(), "user-1")).profileHistory).toEqual([]);
  });
});
