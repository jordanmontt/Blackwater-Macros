import { addDaysToKey, toDateKey } from "./dates";
import { groupWeighIns, slopeWithinGroups } from "./expenditure";
import {
  buildDailyNutritionSeries,
  movingAverageByDays,
  rangeToDays,
  weeklyAverages,
  type DataPoint,
} from "./stats";
import { round1, round2 } from "./nutrition";
import type { DailyNutritionPoint, StatsRange, StatsSummary } from "./types";

const TREND_WINDOW_DAYS = 7;

/** Minimal shape of a meal needed to compute daily nutrition series. */
export interface StatsMeal {
  logDate: string;
  resolvedCalories: number;
  resolvedProtein: number;
  resolvedCarbs: number;
  resolvedFat: number;
}

/** Minimal shape of a weight row needed to compute weight/body-fat series. */
export interface StatsWeight {
  measuredAt: Date;
  weightKg: number;
  bodyFatPct: number | null;
  /** Conditions of the weigh-in, so the rate compares only weigh-ins taken alike. */
  fasted: boolean;
  timeZone: string | null;
}

/**
 * Pure, client-safe stats computation shared by the server service and the
 * demo store. Builds every number and series shown on the statistics page.
 *
 * `todayKeyParam` is the caller's local calendar day (YYYY-MM-DD), so ranges
 * stay anchored to the user's timezone.
 */
export function buildStatsFromData(
  meals: StatsMeal[],
  weights: StatsWeight[],
  range: StatsRange,
  todayKeyParam: string,
): StatsSummary {
  const days = rangeToDays(range);
  const toKey = todayKeyParam;
  const fromKey = days === null ? null : addDaysToKey(toKey, -(days - 1));

  // --- Daily calories / protein / carbs / fat ---
  const totalsByDate = new Map<string, DailyNutritionPoint>();
  for (const meal of meals) {
    const current = totalsByDate.get(meal.logDate) ?? {
      date: meal.logDate,
      calories: 0,
      protein: 0,
      carbs: 0,
      fat: 0,
    };
    current.calories += meal.resolvedCalories;
    current.protein += meal.resolvedProtein;
    current.carbs += meal.resolvedCarbs;
    current.fat += meal.resolvedFat;
    totalsByDate.set(meal.logDate, current);
  }

  const seriesFrom = fromKey ?? meals[0]?.logDate ?? toKey;
  const nutritionSeries =
    meals.length > 0 || fromKey !== null
      ? buildDailyNutritionSeries(totalsByDate, seriesFrom, toKey)
      : [];

  const caloriesPoints = nutritionSeries.filter((point) => point.calories > 0);
  const proteinPoints = nutritionSeries.filter((point) => point.protein > 0);
  const carbsPoints = nutritionSeries.filter((point) => point.carbs > 0);
  const fatPoints = nutritionSeries.filter((point) => point.fat > 0);

  // --- Weight ---
  const fromTime = fromKey ? new Date(`${fromKey}T00:00:00Z`).getTime() : 0;
  const inRange = weights.filter((row) => row.measuredAt.getTime() >= fromTime);
  const weightPoints: DataPoint[] = inRange.map((row) => ({
    date: toDateKey(row.measuredAt),
    value: row.weightKg,
  }));

  // The trend and the change use only the weigh-ins taken the usual way: one after
  // dinner weighs more without any weight gained, and averaging doesn't cancel that.
  const usuallyFasted = usualCondition(inRange);
  const inTrend = inRange.map((row) => isFasted(row) === usuallyFasted);
  const trendValues = movingAverageByDays(weightPoints, TREND_WINDOW_DAYS, inTrend);
  const weightSeries = weightPoints.map((point, i) => ({
    date: point.date,
    weight: round2(point.value),
    // Two decimals like the weight: at 0.1 kg the chart's bold trend line drew stairs.
    trend: trendValues[i] === null ? null : round2(trendValues[i] as number),
    inTrend: inTrend[i],
  }));
  const trendPoints = weightPoints.filter((_, i) => inTrend[i]);

  // --- Body fat % series (only entries with valid body fat data) ---
  const bodyFatRows = inRange.filter(
    (row) => row.bodyFatPct !== null && row.bodyFatPct > 0 && row.bodyFatPct < 100,
  );
  const bodyFatPoints: DataPoint[] = bodyFatRows.map((row) => ({
    date: toDateKey(row.measuredAt),
    value: row.bodyFatPct!,
  }));
  // The same rule as the weight, among the readings with body fat: a scale's reading
  // moves with water and food even more than the weight does.
  const usuallyFastedFat = usualCondition(bodyFatRows);
  const fatInTrend = bodyFatRows.map((row) => isFasted(row) === usuallyFastedFat);
  const bodyFatTrendValues = movingAverageByDays(bodyFatPoints, TREND_WINDOW_DAYS, fatInTrend);
  const bodyFatSeries = bodyFatPoints.map((point, i) => ({
    date: point.date,
    bodyFatPct: round1(point.value),
    // Two decimals, as the weight's: the bold line drew stairs at 0.1.
    trend: bodyFatTrendValues[i] === null ? null : round2(bodyFatTrendValues[i] as number),
    inTrend: fatInTrend[i],
  }));
  const fatTrendPoints = bodyFatPoints.filter((_, i) => fatInTrend[i]);

  const lastValue = weightPoints.at(-1)?.value ?? null;
  const firstUsual = trendPoints[0]?.value ?? null;
  const lastUsual = trendPoints.at(-1)?.value ?? null;
  const lastTrend = [...trendValues].reverse().find((v) => v !== null) ?? null;
  // The rate compares only weigh-ins taken alike, as the measured expenditure does.
  const slope = slopeWithinGroups(
    groupWeighIns(
      inRange.map((row) => ({
        date: toDateKey(row.measuredAt),
        value: row.weightKg,
        time: row.measuredAt.toISOString(),
        fasted: row.fasted,
        timeZone: row.timeZone,
      })),
    ),
  );
  const ratePerWeekKg = slope === null ? null : slope * 7;

  // Body fat stats
  const lastBodyFat = bodyFatPoints.at(-1)?.value ?? null;
  const firstUsualFat = fatTrendPoints[0]?.value ?? null;
  const lastUsualFat = fatTrendPoints.at(-1)?.value ?? null;

  const weightSummary: StatsSummary["weight"] = {
    currentWeightKg: lastValue === null ? null : round2(lastValue),
    currentTrendKg: lastTrend === null ? null : round1(lastTrend),
    changeSinceStartKg:
      firstUsual !== null && lastUsual !== null ? round2(lastUsual - firstUsual) : null,
    ratePerWeekKg: ratePerWeekKg === null ? null : round2(ratePerWeekKg),
    minKg: weightPoints.length
      ? round2(weightPoints.reduce((min, p) => Math.min(min, p.value), Infinity))
      : null,
    maxKg: weightPoints.length
      ? round2(weightPoints.reduce((max, p) => Math.max(max, p.value), -Infinity))
      : null,
    currentBodyFatPct: lastBodyFat === null ? null : round1(lastBodyFat),
    changeBodyFatPct:
      firstUsualFat !== null && lastUsualFat !== null ? round1(lastUsualFat - firstUsualFat) : null,
    minBodyFatPct: bodyFatPoints.length
      ? round1(bodyFatPoints.reduce((min, p) => Math.min(min, p.value), Infinity))
      : null,
    maxBodyFatPct: bodyFatPoints.length
      ? round1(bodyFatPoints.reduce((max, p) => Math.max(max, p.value), -Infinity))
      : null,
  };

  return {
    calories: nutritionSeries.map((p) => ({ ...p, calories: round1(p.calories) })),
    protein: nutritionSeries.map((p) => ({ ...p, protein: round1(p.protein) })),
    carbs: nutritionSeries.map((p) => ({ ...p, carbs: round1(p.carbs) })),
    fat: nutritionSeries.map((p) => ({ ...p, fat: round1(p.fat) })),
    weights: weightSeries,
    bodyFat: bodyFatSeries,
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

/**
 * How most weigh-ins of the range were taken: fasted (true) or not. A tie counts
 * as fasted, the recommended way. Someone who always weighs after eating has all
 * their weigh-ins alike, so they all count.
 */
function usualCondition(rows: StatsWeight[]): boolean {
  const fasted = rows.filter(isFasted).length;
  return fasted * 2 >= rows.length;
}

/** Older rows without the flag count as fasted, as in `groupWeighIns`. */
function isFasted(row: StatsWeight): boolean {
  return row.fasted !== false;
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
