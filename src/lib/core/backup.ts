import type { CalorieProfile, EntryMode, Gender, Goal, IngredientInput, MealContent, ProfilePeriod, ResolvedTotals } from "./types";

/**
 * The export file (`blackwater-macros-<date>.json`): everything a user logged,
 * as indented JSON with plain English names, so it reads without the app.
 * Web exports it; Android exports it and imports it back (only without an
 * account: the import replaces the phone's data). Same text on both platforms
 * (Kotlin `Backup.kt`). The format is documented in docs/TECHNICAL.md §5.11;
 * a breaking change raises `BACKUP_FORMAT`.
 */
export const BACKUP_APP = "Blackwater Macros";
export const BACKUP_FORMAT = 1;

export interface BackupWeight {
  measuredAt: string;
  weightKg: number;
  bodyFatPct: number | null;
  note: string | null;
}

export type BackupMeal = MealContent & ResolvedTotals & { logDate: string };
export type BackupTemplate = MealContent & ResolvedTotals & { name: string };

/** What goes in the file. Meals in day order (date, then their order in the day), weigh-ins oldest first. */
export interface BackupData {
  profile: CalorieProfile;
  profileHistory: ProfilePeriod[];
  weights: BackupWeight[];
  meals: BackupMeal[];
  templates: BackupTemplate[];
}

/** The file's names for the profile's values (the app's `surplus` is «bulk»). */
export const BACKUP_SEX: Record<Gender, string> = { male: "male", female: "female" };
export const BACKUP_GOAL: Record<Goal, string> = { cut: "cut", maintain: "maintain", surplus: "bulk" };
/** «foods»: the totals are the sum of the foods; «total»: the meal's total was typed in. */
export const BACKUP_MODE: Record<EntryMode, string> = { per_ingredient: "foods", total_only: "total" };

function profileJson(profile: CalorieProfile) {
  return {
    sex: profile.gender === null ? null : BACKUP_SEX[profile.gender],
    birthYear: profile.birthYear,
    heightCm: profile.heightCm,
    gymDaysPerWeek: profile.gymDaysPerWeek,
    gymMinutesPerSession: profile.gymSessionMinutes,
    walkingMinutesPerDay: profile.walkingMinutesPerDay,
    goal: profile.calorieGoal === null ? null : BACKUP_GOAL[profile.calorieGoal],
  };
}

/** A food with only the values it has (an empty one is left out, as in the app). */
function foodJson(food: IngredientInput) {
  // `?? undefined`: a stored null is left out too, as JSON.stringify does with undefined.
  return {
    name: food.name,
    amount: food.quantity ?? undefined,
    kcal: food.calories ?? undefined,
    protein: food.protein ?? undefined,
    carbs: food.carbs ?? undefined,
    fat: food.fat ?? undefined,
  };
}

/**
 * «total» is what the app counts for the meal: the sum of the foods, or the
 * typed total (carbs and fat may be null when they were not typed).
 */
function contentJson(content: MealContent & ResolvedTotals) {
  const typed = content.entryMode === "total_only";
  return {
    title: content.title,
    notes: content.notes,
    mode: BACKUP_MODE[content.entryMode],
    foods: content.ingredients.map(foodJson),
    total: {
      kcal: typed ? content.totalCalories : content.resolvedCalories,
      protein: typed ? content.totalProtein : content.resolvedProtein,
      carbs: typed ? content.totalCarbs : content.resolvedCarbs,
      fat: typed ? content.totalFat : content.resolvedFat,
    },
  };
}

/** The file's text: `JSON.stringify` with 2 spaces, which the Kotlin side reproduces byte for byte. */
export function buildBackup(data: BackupData, exportedAt: string): string {
  const file = {
    app: BACKUP_APP,
    format: BACKUP_FORMAT,
    exportedAt,
    profile: profileJson(data.profile),
    pastProfiles: data.profileHistory.map(({ until, ...profile }) => ({ until, ...profileJson(profile) })),
    weighIns: data.weights.map((weight) => ({
      time: weight.measuredAt,
      kg: weight.weightKg,
      bodyFatPct: weight.bodyFatPct,
      note: weight.note,
    })),
    meals: data.meals.map((meal) => ({ date: meal.logDate, ...contentJson(meal) })),
    templates: data.templates.map((template) => ({ name: template.name, ...contentJson(template) })),
  };
  return `${JSON.stringify(file, null, 2)}\n`;
}

/** «blackwater-macros-2026-10-26.json» */
export function backupFileName(day: string): string {
  return `blackwater-macros-${day}.json`;
}
