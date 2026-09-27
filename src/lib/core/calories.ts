import type { CalorieProfile, CalorieRecommendation, Gender, Goal } from "./types";

/**
 * Mifflin-St Jeor equation (1990) — the most reliable predictive BMR equation
 * for healthy adults in the systematic review by Frankenfield et al. 2005.
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

/** Minutes in a day, the unit of the factorial model below. */
const MINUTES_PER_DAY = 1440;
/**
 * Average intensity (× BMR) of a day of ordinary living with no exercise:
 * sleep, sitting, desk work and light chores. The low end of the "sedentary or
 * light activity" band (PAL 1.40–1.69) of FAO/WHO/UNU 2004.
 */
const BASELINE_INTENSITY = 1.4;
/** Resistance training including rest between sets (Compendium 2024: 3.5–6 METs). */
const GYM_INTENSITY = 4.0;
/** Walking at a moderate pace, ~4–5 km/h (Compendium 2024: 3.5–3.8 METs). */
const WALKING_INTENSITY = 3.5;

/**
 * Physical activity level (PAL = TDEE / BMR) with the factorial method of
 * FAO/WHO/UNU 2004: the day is split into time blocks, each weighted by its
 * intensity as a multiple of BMR, and averaged over the 1440 minutes.
 *
 *   gym  = gymDays × gymMinutes / 7   (average minutes per day)
 *   PAL  = ((1440 − gym − walking) × 1.4 + gym × 4.0 + walking × 3.5) / 1440
 *
 * Every extra minute of training or walking raises the result, and it is
 * derived from what the user actually does rather than a self-rated
 * category, which people tend to overestimate (Prince et al. 2008).
 */
export function getActivityMultiplier(
  gymDays: number,
  gymMinutes: number,
  walkingMinutes: number,
): number {
  const gymPerDay = (gymDays * gymMinutes) / 7;
  const restMinutes = Math.max(MINUTES_PER_DAY - gymPerDay - walkingMinutes, 0);
  return (
    (restMinutes * BASELINE_INTENSITY + gymPerDay * GYM_INTENSITY + walkingMinutes * WALKING_INTENSITY) /
    MINUTES_PER_DAY
  );
}

const CALORIE_OFFSETS: Record<Goal, { target: number; min: number; max: number }> = {
  cut: { target: -400, min: -500, max: -300 },
  maintain: { target: 0, min: -100, max: 100 },
  surplus: { target: 300, min: 200, max: 400 },
};

/**
 * Computes age as `currentYear - birthYear`. `currentYear` is passed in so the
 * function stays pure/deterministic (the caller owns the clock read, e.g.
 * `new Date().getFullYear()`).
 */
function getAge(currentYear: number, birthYear: number): number {
  return currentYear - birthYear;
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
  currentYear: number,
): CalorieRecommendation | null {
  if (!isCalorieProfileComplete(profile)) return null;

  const age = getAge(currentYear, profile.birthYear!);
  const bmr = calculateBMR(profile.gender!, weightKg, profile.heightCm!, age);
  const multiplier = getActivityMultiplier(
    profile.gymDaysPerWeek!,
    profile.gymSessionMinutes!,
    profile.walkingMinutesPerDay!,
  );
  const tdee = Math.round(bmr * multiplier);
  const offsets = CALORIE_OFFSETS[profile.calorieGoal!];
  // Safety floor: the target never goes below the basal metabolic rate. Only a
  // cut for a small, sedentary person gets there (e.g. TDEE 1540 − 500 = 1040
  // against a BMR of 1100); a deficit that deep is better left to supervision.
  const floor = Math.round(bmr);
  const atLeastBmr = (kcal: number) => Math.max(kcal, floor);

  return {
    bmr: floor,
    activityFactor: Math.round(multiplier * 100) / 100,
    tdee,
    target: atLeastBmr(tdee + offsets.target),
    targetMin: atLeastBmr(tdee + offsets.min),
    targetMax: atLeastBmr(tdee + offsets.max),
    goal: profile.calorieGoal!,
  };
}
