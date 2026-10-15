import { calculateCalorieRecommendation, isCalorieProfileComplete } from "./calories";
import { addDaysToKey } from "./dates";
import { calculateProteinRecommendation } from "./protein";
import type { CalorieProfile, CalorieRecommendation, ProfilePeriod, ProteinRecommendation } from "./types";

export type { ProfilePeriod };

/** A weigh-in on its local calendar day. */
export interface DatedWeight {
  date: string;
  weightKg: number;
  bodyFatPct: number | null;
}

/** A min–max target (kcal or g per day). */
export interface TargetRange {
  min: number;
  max: number;
}

export interface DayTargets {
  calorie: CalorieRecommendation | null;
  protein: ProteinRecommendation | null;
}

const PROFILE_FIELDS = [
  "gender",
  "birthYear",
  "heightCm",
  "gymDaysPerWeek",
  "gymSessionMinutes",
  "walkingMinutesPerDay",
  "calorieGoal",
] as const satisfies readonly (keyof CalorieProfile)[];

export function sameProfile(a: CalorieProfile, b: CalorieProfile): boolean {
  return PROFILE_FIELDS.every((field) => a[field] === b[field]);
}

/** Only the profile fields (drops `until` or anything else carried along). */
export function profileOf(profile: CalorieProfile): CalorieProfile {
  return {
    gender: profile.gender,
    birthYear: profile.birthYear,
    heightCm: profile.heightCm,
    gymDaysPerWeek: profile.gymDaysPerWeek,
    gymSessionMinutes: profile.gymSessionMinutes,
    walkingMinutesPerDay: profile.walkingMinutesPerDay,
    calorieGoal: profile.calorieGoal,
  };
}

/**
 * The history after replacing `previous` with `next` today: changes apply from
 * today on, so days up to yesterday keep `previous`. Unchanged when nothing
 * changed, when `previous` gave no calorie target (an unfinished profile: the
 * new one covers the earlier days too), or when the days up to yesterday are
 * already kept (a second edit the same day).
 */
export function withProfileChange(
  previous: CalorieProfile,
  history: ProfilePeriod[],
  next: CalorieProfile,
  today: string,
): ProfilePeriod[] {
  if (sameProfile(previous, next) || !isCalorieProfileComplete(previous)) return history;
  const yesterday = addDaysToKey(today, -1);
  if (history.some((period) => period.until >= yesterday)) return history;
  return [...history, { ...profileOf(previous), until: yesterday }];
}

/** The profile that applied on `day`: the earliest period still running that day, else the current one. */
export function profileOn(profile: CalorieProfile, history: ProfilePeriod[], day: string): CalorieProfile {
  let found: ProfilePeriod | null = null;
  for (const period of history) {
    if (period.until >= day && (found === null || period.until < found.until)) found = period;
  }
  return found === null ? profile : profileOf(found);
}

/**
 * The last weigh-in on or before `day`; before the first weigh-in, the first
 * one (the closest the app knows). `weights` in measuring order.
 */
export function weightOn(weights: DatedWeight[], day: string): DatedWeight | null {
  const known = weights.filter((w) => w.date <= day);
  return known.at(-1) ?? weights[0] ?? null;
}

/** Body fat is not logged at every weigh-in: the last one on or before `day`, else the first one. */
export function bodyFatOn(weights: DatedWeight[], day: string): number | null {
  const logged = weights.filter((w) => w.bodyFatPct !== null);
  const known = logged.filter((w) => w.date <= day);
  return (known.at(-1) ?? logged[0])?.bodyFatPct ?? null;
}

/**
 * The calorie and protein targets of `day`, with the profile, weight and body
 * fat known that day and the age in that day's year. Today's targets are the
 * usual ones (current profile, latest weigh-in).
 */
export function targetsOn(
  profile: CalorieProfile,
  history: ProfilePeriod[],
  weights: DatedWeight[],
  day: string,
): DayTargets {
  const weight = weightOn(weights, day);
  if (weight === null) return { calorie: null, protein: null };
  const dayProfile = profileOn(profile, history, day);
  const year = Number(day.slice(0, 4));
  const calorie = calculateCalorieRecommendation(dayProfile, weight.weightKg, year);
  const protein =
    dayProfile.calorieGoal === null
      ? null
      : calculateProteinRecommendation(weight.weightKg, dayProfile.calorieGoal, bodyFatOn(weights, day), {
          heightCm: dayProfile.heightCm,
          gender: dayProfile.gender,
          age: dayProfile.birthYear === null ? null : year - dayProfile.birthYear,
        });
  return { calorie, protein };
}

/**
 * The mean of the targets of `days` (e.g. the logged days of a period), each
 * with its own profile and weight. Null for a target no day has.
 */
export function averageTargets(
  profile: CalorieProfile,
  history: ProfilePeriod[],
  weights: DatedWeight[],
  days: string[],
): { calorie: TargetRange | null; protein: TargetRange | null } {
  const calories: TargetRange[] = [];
  const proteins: TargetRange[] = [];
  for (const day of days) {
    const targets = targetsOn(profile, history, weights, day);
    if (targets.calorie) calories.push({ min: targets.calorie.targetMin, max: targets.calorie.targetMax });
    if (targets.protein) proteins.push(targets.protein.range);
  }
  return { calorie: meanRange(calories), protein: meanRange(proteins) };
}

function meanRange(ranges: TargetRange[]): TargetRange | null {
  if (ranges.length === 0) return null;
  const mean = (pick: (range: TargetRange) => number) =>
    Math.round(ranges.reduce((sum, range) => sum + pick(range), 0) / ranges.length);
  return { min: mean((r) => r.min), max: mean((r) => r.max) };
}
