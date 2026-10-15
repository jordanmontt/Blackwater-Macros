export type EntryMode = "per_ingredient" | "total_only";

export interface IngredientInput {
  name: string;
  quantity?: string;
  calories?: number;
  protein?: number;
  carbs?: number;
  fat?: number;
}

export interface MealDTO {
  id: string;
  logDate: string;
  title: string;
  notes: string | null;
  entryMode: EntryMode;
  ingredients: IngredientInput[];
  totalCalories: number | null;
  totalProtein: number | null;
  totalCarbs: number | null;
  totalFat: number | null;
  resolvedCalories: number;
  resolvedProtein: number;
  resolvedCarbs: number;
  resolvedFat: number;
  updatedAt: string;
}

export interface MealTemplateDTO {
  id: string;
  name: string;
  title: string;
  notes: string | null;
  entryMode: EntryMode;
  ingredients: IngredientInput[];
  totalCalories: number | null;
  totalProtein: number | null;
  totalCarbs: number | null;
  totalFat: number | null;
  resolvedCalories: number;
  resolvedProtein: number;
  resolvedCarbs: number;
  resolvedFat: number;
  updatedAt: string;
}

export interface WeightDTO {
  id: string;
  measuredAt: string;
  weightKg: number;
  bodyFatPct: number | null;
  note: string | null;
  updatedAt: string;
}

export interface DailyNutritionPoint {
  date: string;
  calories: number;
  protein: number;
  carbs: number;
  fat: number;
}

export type StatsRange = "7d" | "30d" | "90d" | "all";

export interface CompositionStats {
  currentWeightKg: number | null;
  currentTrendKg: number | null;
  changeSinceStartKg: number | null;
  ratePerWeekKg: number | null;
  minKg: number | null;
  maxKg: number | null;
  // Body fat (null when no entries in range have body fat data)
  currentBodyFatPct: number | null;
  changeBodyFatPct: number | null;
  minBodyFatPct: number | null;
  maxBodyFatPct: number | null;
}

export interface StatsSummary {
  calories: DailyNutritionPoint[];
  protein: DailyNutritionPoint[];
  carbs: DailyNutritionPoint[];
  fat: DailyNutritionPoint[];
  weights: { date: string; weight: number; trend: number | null }[];
  bodyFat: { date: string; bodyFatPct: number; trend: number | null }[];
  caloriesAvg: number | null;
  caloriesMaxDay: DailyNutritionPoint | null;
  proteinAvg: number | null;
  proteinMaxDay: DailyNutritionPoint | null;
  carbsAvg: number | null;
  carbsMaxDay: DailyNutritionPoint | null;
  fatAvg: number | null;
  fatMaxDay: DailyNutritionPoint | null;
  weight: CompositionStats;
  weeklyWeightAvg: { weekStart: string; avg: number }[];
}

export interface UserSettings {
  calorieProfile: CalorieProfile;
  /** Former profiles, so past days keep their targets (`core/profile-history.ts`). */
  profileHistory: ProfilePeriod[];
}

export interface AdminUserDTO {
  id: string;
  username: string;
  isAdmin: boolean;
  createdAt: string;
}

export type Gender = "male" | "female";
export type Goal = "cut" | "maintain" | "surplus";

export interface CalorieProfile {
  gender: Gender | null;
  birthYear: number | null;
  heightCm: number | null;
  gymDaysPerWeek: number | null;
  gymSessionMinutes: number | null;
  walkingMinutesPerDay: number | null;
  calorieGoal: Goal | null;
}

/**
 * A former profile and the last day it applied to (inclusive). The current
 * profile applies to every day after the last period; an empty history means
 * the current profile applies to every day.
 */
export interface ProfilePeriod extends CalorieProfile {
  until: string;
}

export interface CalorieRecommendation {
  bmr: number;
  /** PAL used for the TDEE (TDEE / BMR), rounded to 2 decimals for display. */
  activityFactor: number;
  tdee: number;
  target: number;
  targetMin: number;
  targetMax: number;
  goal: Goal;
}

export interface ProteinRange {
  min: number;
  max: number;
}

/**
 * What the g/kg factors multiply: total body weight, lean (fat-free) mass, or
 * the reference weight at BMI 25 when a high BMI is likely fat.
 */
export type ProteinBasis = "bodyWeight" | "leanMass" | "referenceWeight";

export interface ProteinRecommendation {
  goal: Goal;
  basis: ProteinBasis;
  /** The kilograms the factors multiply (body weight, lean mass or reference weight, see `basis`). */
  basisKg: number;
  /** Grams per day. */
  range: ProteinRange;
  /** g per kg of `basisKg`. */
  perKg: ProteinRange;
  target: number;
}

export interface ExpenditureEstimate {
  /** Measured total daily energy expenditure, kcal/day. */
  tdee: number;
  /** ± kcal/day, 95 % margin from the uncertainty of the weight trend. */
  margin: number;
  /** Mean intake over the logged days of the window, kcal/day. */
  avgIntake: number;
  /** Weight trend over the window (least-squares slope), kg/week. */
  weightChangePerWeek: number;
  loggedDays: number;
  weighIns: number;
  windowDays: number;
}

/**
 * What the measurement found in its 28-day window, also when it is not enough:
 * the profile tells the user exactly what is missing.
 */
export interface ExpenditureData {
  /** Days with meals logged (kcal > 0). */
  loggedDays: number;
  /** Days with at least one weigh-in. */
  weighInDays: number;
  /** Days between the first and the last weigh-in (0 with fewer than 2). */
  weightSpanDays: number;
  /** 95 % margin of the weight trend, kcal/day; null with fewer than 3 weigh-in days. */
  margin: number | null;
  /** The measured expenditure; null until every rule is met. */
  estimate: ExpenditureEstimate | null;
}

/** Why the measured expenditure is not there yet, in the order the user should fix it. */
export type MissingExpenditureData = "meals" | "weighIns" | "weightSpan" | "margin";

/** The measured expenditure is above, inside or below the formula's normal range. */
export type ExpenditureVerdict = "higher" | "matches" | "lower";

/** The formula's expenditure (profile) checked against the measured one (your data). */
export type ExpenditureCheck =
  | { status: "notEnoughData"; data: ExpenditureData; missing: MissingExpenditureData[] }
  | {
      status: "measured";
      verdict: ExpenditureVerdict;
      /** The same verdict one week earlier too (always true for "matches"). */
      confirmed: boolean;
      estimate: ExpenditureEstimate;
      /** From the profile: BMR × activity factor. */
      formulaTdee: number;
      bmr: number;
      activityFactor: number;
      /** formulaTdee ± 10 %: what the formula can be off by for anyone. */
      normalMin: number;
      normalMax: number;
      /** Measured − formula, kcal/day. */
      gap: number;
      /** The activity factor your measured expenditure corresponds to (measured / BMR). */
      impliedActivityFactor: number;
    };

/** «Not now» on the Today card's gap notice: it comes back if the verdict changes or after a new 4-week window. */
export interface ExpenditureNoticeDismissal {
  verdict: "higher" | "lower";
  /** YYYY-MM-DD. */
  date: string;
}
