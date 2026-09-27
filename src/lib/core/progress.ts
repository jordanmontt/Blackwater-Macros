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
