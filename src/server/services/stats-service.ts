import { addDaysToKey, toDateKey, todayKey } from "@/lib/dates";
import {
  buildDailyNutritionSeries,
  linearRatePerWeek,
  movingAverageByDays,
  rangeToDays,
  weeklyAverages,
  type DataPoint,
} from "@/lib/stats";
import { round1, round2 } from "@/lib/nutrition";
import type { DailyNutritionPoint, StatsRange, StatsSummary } from "@/lib/types";
import type { MealsRepository } from "../repositories/meals-repo";
import type { WeightsRepository } from "../repositories/weights-repo";

export interface StatsServiceDeps {
  meals: Pick<MealsRepository, "listInRange">;
  weights: Pick<WeightsRepository, "listForUser">;
}

const TREND_WINDOW_DAYS = 7;

/**
 * Builds every number and series shown on the statistics page for a range:
 * daily calorie/protein series, weight evolution with trend line, and the
 * derived weight metrics (current trend, rate of change, weekly averages).
 *
 * `todayKeyParam` is the caller's local calendar day (YYYY-MM-DD). Passing it
 * explicitly keeps ranges anchored to the user's timezone instead of the
 * server's.
 */
export async function buildStatsSummary(
  deps: StatsServiceDeps,
  userId: string,
  range: StatsRange,
  todayKeyParam: string = todayKey(),
): Promise<StatsSummary> {
  const days = rangeToDays(range);
  const toKey = todayKeyParam;
  const fromKey = days === null ? null : addDaysToKey(toKey, -(days - 1));

  const mealRows = await deps.meals.listInRange(userId, fromKey, toKey);
  const weightRows = await deps.weights.listForUser(userId);

  // --- Daily calories / protein / carbs / fat ---
  const totalsByDate = new Map<string, DailyNutritionPoint>();
  for (const meal of mealRows) {
    const current = totalsByDate.get(meal.logDate) ?? {
      date: meal.logDate,
      calories: 0,
      protein: 0,
      carbs: 0,
      fat: 0,
    };
    current.calories += meal.resolvedCalories;
    current.protein += meal.resolvedProtein;
    current.carbs += meal.ingredients.reduce((sum, i) => sum + (i.carbs ?? 0), 0);
    current.fat += meal.ingredients.reduce((sum, i) => sum + (i.fat ?? 0), 0);
    totalsByDate.set(meal.logDate, current);
  }

  const seriesFrom =
    fromKey ?? mealRows[0]?.logDate ?? toKey;
  const nutritionSeries =
    mealRows.length > 0 || fromKey !== null
      ? buildDailyNutritionSeries(totalsByDate, seriesFrom, toKey)
      : [];

  const caloriesPoints = nutritionSeries.filter((point) => point.calories > 0);
  const proteinPoints = nutritionSeries.filter((point) => point.protein > 0);
  const carbsPoints = nutritionSeries.filter((point) => point.carbs > 0);
  const fatPoints = nutritionSeries.filter((point) => point.fat > 0);

  // --- Weight ---
  const fromTime = fromKey ? new Date(`${fromKey}T00:00:00Z`).getTime() : 0;
  const inRange = weightRows.filter((row) => row.measuredAt.getTime() >= fromTime);
  const weightPoints: DataPoint[] = inRange.map((row) => ({
    date: toDateKey(row.measuredAt),
    value: row.weightKg,
  }));

  const trendValues = movingAverageByDays(weightPoints, TREND_WINDOW_DAYS);
  const weightSeries = weightPoints.map((point, i) => ({
    date: point.date,
    weight: round2(point.value),
    trend: trendValues[i] === null ? null : round1(trendValues[i] as number),
  }));

  const lastValue = weightPoints.at(-1)?.value ?? null;
  const firstValue = weightPoints[0]?.value ?? null;
  const lastTrend = [...trendValues].reverse().find((v) => v !== null) ?? null;

  const weightSummary: StatsSummary["weight"] = {
    currentWeightKg: lastValue === null ? null : round2(lastValue),
    currentTrendKg: lastTrend === null ? null : round1(lastTrend),
    changeSinceStartKg:
      firstValue !== null && lastValue !== null ? round2(lastValue - firstValue) : null,
    ratePerWeekKg: (() => {
      const rate = linearRatePerWeek(weightPoints);
      return rate === null ? null : round2(rate);
    })(),
    minKg: weightPoints.length ? round2(Math.min(...weightPoints.map((p) => p.value))) : null,
    maxKg: weightPoints.length ? round2(Math.max(...weightPoints.map((p) => p.value))) : null,
  };

  return {
    calories: nutritionSeries.map((p) => ({ ...p, calories: round1(p.calories) })),
    protein: nutritionSeries.map((p) => ({ ...p, protein: round1(p.protein) })),
    carbs: nutritionSeries.map((p) => ({ ...p, carbs: round1(p.carbs) })),
    fat: nutritionSeries.map((p) => ({ ...p, fat: round1(p.fat) })),
    weights: weightSeries,
    caloriesAvg: averageOf(caloriesPoints.map((p) => p.calories)),
    caloriesMaxDay: maxBy(caloriesPoints, (p) => p.calories),
    proteinAvg: averageOf(proteinPoints.map((p) => p.protein)),
    proteinMaxDay: maxBy(proteinPoints, (p) => p.protein),
    carbsAvg: averageOf(carbsPoints.map((p) => p.carbs)),
    carbsMaxDay: maxBy(carbsPoints, (p) => p.carbs),
    fatAvg: averageOf(fatPoints.map((p) => p.fat)),
    fatMaxDay: maxBy(fatPoints, (p) => p.fat),
    weight: weightSummary,
    weeklyWeightAvg: weeklyAverages(weightPoints).map((week) => ({
      weekStart: week.weekStart,
      avg: round2(week.avg),
    })),
  };
}

function averageOf(values: number[]): number | null {
  if (values.length === 0) return null;
  const avg = values.reduce((a, b) => a + b, 0) / values.length;
  return Math.round(avg);
}

function maxBy<T>(items: T[], selector: (item: T) => number): T | null {
  let best: T | null = null;
  for (const item of items) {
    if (best === null || selector(item) > selector(best)) best = item;
  }
  return best;
}
