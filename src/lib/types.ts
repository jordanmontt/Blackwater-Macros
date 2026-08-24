export type EntryMode = "per_ingredient" | "total_only";

export interface IngredientInput {
  name: string;
  quantity?: string;
  calories?: number;
  protein?: number;
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
  resolvedCalories: number;
  resolvedProtein: number;
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
  note: string | null;
}

export interface DailyNutritionPoint {
  date: string;
  calories: number;
  protein: number;
}

export type StatsRange = "7d" | "30d" | "90d" | "all";

export interface WeightStatsSummary {
  currentWeightKg: number | null;
  currentTrendKg: number | null;
  changeSinceStartKg: number | null;
  ratePerWeekKg: number | null;
  minKg: number | null;
  maxKg: number | null;
}

export interface StatsSummary {
  calories: DailyNutritionPoint[];
  protein: DailyNutritionPoint[];
  weights: { date: string; weight: number; trend: number | null }[];
  caloriesAvg: number | null;
  caloriesMaxDay: DailyNutritionPoint | null;
  proteinAvg: number | null;
  proteinMaxDay: DailyNutritionPoint | null;
  weight: WeightStatsSummary;
  weeklyWeightAvg: { weekStart: string; avg: number }[];
}
