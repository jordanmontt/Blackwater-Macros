export type EntryMode = "per_ingredient" | "total_only";
export type ProteinGoal = "maintain" | "build" | "cut";

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
}

export interface MealTemplateDTO {
  id: string;
  name: string;
  title: string;
  notes: string | null;
  ingredients: IngredientInput[];
}

export interface WeightDTO {
  id: string;
  measuredAt: string;
  weightKg: number;
  bodyFatPct: number | null;
  note: string | null;
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
  // Lean mass = weight × (1 - bodyFatPct/100)
  currentLeanMassKg: number | null;
  changeLeanMassKg: number | null;
}

export interface StatsSummary {
  calories: DailyNutritionPoint[];
  protein: DailyNutritionPoint[];
  carbs: DailyNutritionPoint[];
  fat: DailyNutritionPoint[];
  weights: { date: string; weight: number; trend: number | null }[];
  bodyFat: { date: string; bodyFatPct: number; trend: number | null }[];
  leanMass: { date: string; leanMassKg: number; trend: number | null }[];
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
  proteinGoal: ProteinGoal;
  calorieProfile: CalorieProfile;
}

export type Gender = "male" | "female";
export type CalorieGoal = "deficit" | "maintain" | "surplus";

export interface CalorieProfile {
  gender: Gender | null;
  birthYear: number | null;
  heightCm: number | null;
  gymDaysPerWeek: number | null;
  gymSessionMinutes: number | null;
  walkingMinutesPerDay: number | null;
  calorieGoal: CalorieGoal | null;
}

export interface CalorieRecommendation {
  bmr: number;
  tdee: number;
  target: number;
  targetMin: number;
  targetMax: number;
  goal: CalorieGoal;
}

export interface ProteinRange {
  min: number;
  max: number;
}

export interface ProteinRecommendation {
  goal: ProteinGoal;
  bodyWeightKg: number;
  bwRange: ProteinRange;
  bwPerKg: ProteinRange;
}
