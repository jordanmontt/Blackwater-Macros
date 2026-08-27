import type { CalorieProfile, CalorieRecommendation, Gender, Goal } from "./types";

/**
 * Mifflin-St Jeor equation (1990) — most accurate BMR formula for
 * non-athletes per Frankenfield et al. 2005 (n=1090, 82% within ±10%).
 *
 * Men:   BMR = (10 × weight_kg) + (6.25 × height_cm) - (5 × age) + 5
 * Women: BMR = (10 × weight_kg) + (6.25 × height_cm) - (5 × age) - 161
 */
export function calculateBMR(
  gender: Gender,
  weightKg: number,
  heightCm: number,
  age: number,
): number {
  const base = 10 * weightKg + 6.25 * heightCm - 5 * age;
  return gender === "male" ? base + 5 : base - 161;
}

/**
 * Derive an activity multiplier from gym frequency (days/week × session
 * minutes) and daily walking minutes.
 *
 * Thresholds based on standard PAL categories, calibrated conservatively
 * for non-athletes (research shows people tend to overestimate activity).
 */
export function getActivityMultiplier(
  gymDays: number,
  gymMinutes: number,
  walkingMinutes: number,
): number {
  const weeklyGymMinutes = gymDays * gymMinutes;

  if (weeklyGymMinutes === 0 && walkingMinutes < 30) return 1.2;
  if (weeklyGymMinutes === 0 && walkingMinutes >= 30) return 1.375;
  if (weeklyGymMinutes > 0 && weeklyGymMinutes <= 150 && walkingMinutes < 30) return 1.375;
  if (weeklyGymMinutes > 0 && weeklyGymMinutes <= 150 && walkingMinutes >= 30) return 1.55;
  if (weeklyGymMinutes > 150 && weeklyGymMinutes <= 360 && walkingMinutes >= 30) return 1.55;
  if (weeklyGymMinutes > 150 && weeklyGymMinutes <= 360 && walkingMinutes < 30) return 1.375;
  if (weeklyGymMinutes > 360 && weeklyGymMinutes <= 540) return 1.725;
  return 1.9;
}

const CALORIE_OFFSETS: Record<Goal, { target: number; min: number; max: number }> = {
  cut: { target: -400, min: -500, max: -300 },
  maintain: { target: 0, min: -100, max: 100 },
  surplus: { target: 300, min: 200, max: 400 },
};

function getCurrentAge(birthYear: number): number {
  const now = new Date();
  return now.getFullYear() - birthYear;
}

export function isCalorieProfileComplete(profile: CalorieProfile): boolean {
  return (
    profile.gender !== null &&
    profile.birthYear !== null &&
    profile.heightCm !== null &&
    profile.gymDaysPerWeek !== null &&
    profile.gymSessionMinutes !== null &&
    profile.walkingMinutesPerDay !== null &&
    profile.calorieGoal !== null
  );
}

export function calculateCalorieRecommendation(
  profile: CalorieProfile,
  weightKg: number,
): CalorieRecommendation | null {
  if (!isCalorieProfileComplete(profile)) return null;

  const age = getCurrentAge(profile.birthYear!);
  const bmr = calculateBMR(profile.gender!, weightKg, profile.heightCm!, age);
  const multiplier = getActivityMultiplier(
    profile.gymDaysPerWeek!,
    profile.gymSessionMinutes!,
    profile.walkingMinutesPerDay!,
  );
  const tdee = Math.round(bmr * multiplier);
  const offsets = CALORIE_OFFSETS[profile.calorieGoal!];

  return {
    bmr: Math.round(bmr),
    tdee,
    target: tdee + offsets.target,
    targetMin: tdee + offsets.min,
    targetMax: tdee + offsets.max,
    goal: profile.calorieGoal!,
  };
}
