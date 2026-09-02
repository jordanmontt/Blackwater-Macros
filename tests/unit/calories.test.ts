import { describe, expect, it } from "vitest";
import {
  calculateBMR,
  calculateCalorieRecommendation,
  getActivityMultiplier,
  isCalorieProfileComplete,
} from "../../src/lib/core/calories";
import type { CalorieProfile } from "../../src/lib/core/types";

describe("calculateBMR", () => {
  it("fórmula Mifflin-St Jeor para hombre", () => {
    expect(calculateBMR("male", 80, 178, 35)).toBeCloseTo(1742.5);
  });

  it("fórmula Mifflin-St Jeor para mujer", () => {
    expect(calculateBMR("female", 60, 165, 30)).toBeCloseTo(1320.25);
  });

  it("valores bajos de peso/altura", () => {
    expect(calculateBMR("female", 45, 150, 70)).toBeCloseTo(10 * 45 + 6.25 * 150 - 5 * 70 - 161);
  });
});

describe("getActivityMultiplier", () => {
  it("sedentario", () => {
    expect(getActivityMultiplier(0, 0, 0)).toBe(1.2);
  });

  it("caminata diaria sin gym", () => {
    expect(getActivityMultiplier(0, 0, 60)).toBe(1.375);
  });

  it("moderado con gym", () => {
    expect(getActivityMultiplier(3, 60, 30)).toBe(1.55);
  });

  it("entrenamiento alto", () => {
    expect(getActivityMultiplier(5, 90, 0)).toBe(1.725);
  });
});

describe("isCalorieProfileComplete", () => {
  it("perfil completo es true", () => {
    const profile: CalorieProfile = {
      gender: "male",
      birthYear: 1990,
      heightCm: 178,
      gymDaysPerWeek: 3,
      gymSessionMinutes: 60,
      walkingMinutesPerDay: 30,
      calorieGoal: "cut",
    };
    expect(isCalorieProfileComplete(profile)).toBe(true);
  });

  it("perfil con campos nulos es false", () => {
    const profile: CalorieProfile = {
      gender: null,
      birthYear: null,
      heightCm: null,
      gymDaysPerWeek: null,
      gymSessionMinutes: null,
      walkingMinutesPerDay: null,
      calorieGoal: null,
    };
    expect(isCalorieProfileComplete(profile)).toBe(false);
  });
});

describe("calculateCalorieRecommendation", () => {
  const CURRENT_YEAR = 2026;

  it("perfil incompleto devuelve null", () => {
    const profile: CalorieProfile = {
      gender: null,
      birthYear: null,
      heightCm: null,
      gymDaysPerWeek: null,
      gymSessionMinutes: null,
      walkingMinutesPerDay: null,
      calorieGoal: null,
    };
    expect(calculateCalorieRecommendation(profile, 80, CURRENT_YEAR)).toBeNull();
  });

  it("calcula bmr, tdee y objetivo en modo cut", () => {
    const profile: CalorieProfile = {
      gender: "male",
      birthYear: 1990,
      heightCm: 178,
      gymDaysPerWeek: 3,
      gymSessionMinutes: 60,
      walkingMinutesPerDay: 30,
      calorieGoal: "cut",
    };
    // Edad = 2026 - 1990 = 36
    const bmr = calculateBMR("male", 80, 178, 36);
    const tdee = Math.round(bmr * 1.55);
    const rec = calculateCalorieRecommendation(profile, 80, CURRENT_YEAR);
    expect(rec).not.toBeNull();
    expect(rec!.bmr).toBe(Math.round(bmr));
    expect(rec!.tdee).toBe(tdee);
    expect(rec!.target).toBe(tdee - 400);
    expect(rec!.targetMin).toBe(tdee - 500);
    expect(rec!.targetMax).toBe(tdee - 300);
    expect(rec!.goal).toBe("cut");
  });

  it("modo surplus suma calorías al objetivo", () => {
    const profile: CalorieProfile = {
      gender: "female",
      birthYear: 1995,
      heightCm: 165,
      gymDaysPerWeek: 5,
      gymSessionMinutes: 60,
      walkingMinutesPerDay: 40,
      calorieGoal: "surplus",
    };
    // Edad = 2026 - 1995 = 31
    const bmr = calculateBMR("female", 60, 165, 31);
    const multiplier = getActivityMultiplier(5, 60, 40); // 300 gym min, walking>=30 -> 1.55
    const tdee = Math.round(bmr * multiplier);
    const rec = calculateCalorieRecommendation(profile, 60, CURRENT_YEAR);
    expect(rec!.target).toBe(tdee + 300);
    expect(rec!.goal).toBe("surplus");
  });

  it("el año actual afecta la edad calculada", () => {
    const profile: CalorieProfile = {
      gender: "male",
      birthYear: 2000,
      heightCm: 170,
      gymDaysPerWeek: 2,
      gymSessionMinutes: 45,
      walkingMinutesPerDay: 20,
      calorieGoal: "maintain",
    };
    const ageOlder = 2026 - 2000; // 26
    const ageYounger = 2090 - 2000;
    const bmrOlder = calculateBMR("male", 75, 170, ageOlder);
    const bmrYounger = calculateBMR("male", 75, 170, ageYounger);
    const recOlder = calculateCalorieRecommendation(profile, 75, 2026);
    const recYounger = calculateCalorieRecommendation(profile, 75, 2090);
    expect(recOlder!.bmr).toBe(Math.round(bmrOlder));
    expect(recYounger!.bmr).toBe(Math.round(bmrYounger));
    expect(recOlder!.bmr).toBeGreaterThan(recYounger!.bmr);
  });
});
