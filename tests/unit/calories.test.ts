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

describe("getActivityMultiplier (método factorial)", () => {
  it("sin ejercicio: la base de 1.4 × TMB", () => {
    expect(getActivityMultiplier(0, 0, 0)).toBeCloseTo(1.4, 10);
  });

  it("solo caminata: 60 min a 3.5 × TMB", () => {
    // (1380 × 1.4 + 60 × 3.5) / 1440
    expect(getActivityMultiplier(0, 0, 60)).toBeCloseTo(1.4875, 10);
  });

  it("gym repartido en la semana y caminata", () => {
    // gym = 3 × 60 / 7 min/día; (resto × 1.4 + gym × 4.0 + 30 × 3.5) / 1440
    expect(getActivityMultiplier(3, 60, 30)).toBeCloseTo(1.4901785714, 8);
    expect(getActivityMultiplier(5, 90, 0)).toBeCloseTo(1.5160714286, 8);
  });

  it("más entrenamiento nunca baja el factor", () => {
    expect(getActivityMultiplier(4, 60, 20)).toBeGreaterThan(getActivityMultiplier(2, 60, 30));
    expect(getActivityMultiplier(4, 60, 30)).toBeGreaterThan(getActivityMultiplier(3, 60, 30));
    expect(getActivityMultiplier(3, 90, 30)).toBeGreaterThan(getActivityMultiplier(3, 60, 30));
    expect(getActivityMultiplier(3, 60, 31)).toBeGreaterThan(getActivityMultiplier(3, 60, 30));
  });

  it("máximos permitidos del perfil (7 × 300 min + 480 min)", () => {
    expect(getActivityMultiplier(7, 300, 480)).toBeCloseTo(2.6416666667, 8);
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
    // Edad = 2026 - 1990 = 36 → TMB 1737.5; factor 1.49018 → TDEE 2589.19
    const rec = calculateCalorieRecommendation(profile, 80, CURRENT_YEAR);
    expect(rec).not.toBeNull();
    expect(rec!.bmr).toBe(1738);
    expect(rec!.activityFactor).toBe(1.49);
    expect(rec!.tdee).toBe(2589);
    expect(rec!.target).toBe(2189);
    expect(rec!.targetMin).toBe(2089);
    expect(rec!.targetMax).toBe(2289);
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
    // Edad = 2026 - 1995 = 31 → TMB 1315.25; factor 1.53571 → TDEE 2019.85
    const rec = calculateCalorieRecommendation(profile, 60, CURRENT_YEAR);
    expect(rec!.activityFactor).toBe(1.54);
    expect(rec!.tdee).toBe(2020);
    expect(rec!.target).toBe(2320);
    expect(rec!.targetMin).toBe(2220);
    expect(rec!.targetMax).toBe(2420);
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
