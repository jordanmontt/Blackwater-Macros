import { round1 } from "./nutrition";
import type { DailyNutritionPoint } from "./types";

export interface MacroAverages {
  /** Days with food logged (calories > 0). Averages use only these. */
  loggedDays: number;
  totalDays: number;
  calories: number;
  protein: number;
  carbs: number;
  fat: number;
  /** Share of the energy from each macro (4 / 4 / 9 kcal per g), in %; null without macros. */
  split: { protein: number; carbs: number; fat: number } | null;
}

/**
 * Daily averages over the days that were logged. Unlogged days are left out
 * (counting them as 0 would say you ate less than you did); `loggedDays` of
 * `totalDays` says how complete the picture is. Null when nothing was logged.
 */
export function macroAverages(days: DailyNutritionPoint[]): MacroAverages | null {
  const logged = days.filter((day) => day.calories > 0);
  if (logged.length === 0) return null;
  const mean = (pick: (day: DailyNutritionPoint) => number) =>
    logged.reduce((sum, day) => sum + pick(day), 0) / logged.length;

  const protein = mean((d) => d.protein);
  const carbs = mean((d) => d.carbs);
  const fat = mean((d) => d.fat);
  const energy = { protein: protein * 4, carbs: carbs * 4, fat: fat * 9 };
  const total = energy.protein + energy.carbs + energy.fat;

  return {
    loggedDays: logged.length,
    totalDays: days.length,
    calories: Math.round(mean((d) => d.calories)),
    protein: round1(protein),
    carbs: round1(carbs),
    fat: round1(fat),
    split:
      total > 0
        ? {
            protein: Math.round((energy.protein / total) * 100),
            carbs: Math.round((energy.carbs / total) * 100),
            fat: Math.round((energy.fat / total) * 100),
          }
        : null,
  };
}

// --- Fat as a share of calories ---

/**
 * The adult range for fat, as % of energy: the Acceptable Macronutrient
 * Distribution Range of the US Institute of Medicine (2005). Below it, essential
 * fatty acids and the fat-soluble vitamins (A, D, E, K) are hard to cover; above
 * it, saturated fat tends to be high. The app only records totals, so it cannot
 * see which kind of fat.
 */
export const FAT_SHARE_MIN = 20;
export const FAT_SHARE_MAX = 35;
/** Logged days before the average says anything: one heavy or light day must not. */
export const FAT_CHECK_MIN_DAYS = 7;

export interface FatShareCheck {
  verdict: "low" | "high";
  /** Fat's share of the energy from macros, in % (the same number as `split.fat`). */
  share: number;
  /** Mean grams of fat per logged day, and their energy (× 9). */
  fat: number;
  fatKcal: number;
  /** Energy from protein, carbs and fat (4 / 4 / 9 kcal per g), the base of the share. */
  macroKcal: number;
}

/**
 * Fat outside 20–35 % of calories over the period's logged days, or null when it
 * is inside, there are no macros, or fewer than 7 days were logged. It uses the
 * rounded share the card shows, so «20 %» is never called low. It is information
 * only: no calorie or protein target changes with it.
 */
export function checkFatShare(averages: MacroAverages): FatShareCheck | null {
  if (averages.loggedDays < FAT_CHECK_MIN_DAYS || averages.split === null) return null;
  const share = averages.split.fat;
  if (share >= FAT_SHARE_MIN && share <= FAT_SHARE_MAX) return null;
  return {
    verdict: share < FAT_SHARE_MIN ? "low" : "high",
    share,
    fat: averages.fat,
    fatKcal: Math.round(averages.fat * 9),
    macroKcal: Math.round(averages.protein * 4 + averages.carbs * 4 + averages.fat * 9),
  };
}
