import { addDaysToKey, toDateKey } from "./dates";
import {
  buildDailyNutritionSeries,
  linearRatePerWeek,
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

/** Minimal shape of a weight row needed to compute weight/body-fat/lean-mass series. */
export interface StatsWeight {
  measuredAt: Date;
  weightKg: number;
  bodyFatPct: number | null;
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

  const trendValues = movingAverageByDays(weightPoints, TREND_WINDOW_DAYS);
  const weightSeries = weightPoints.map((point, i) => ({
    date: point.date,
    weight: round2(point.value),
    trend: trendValues[i] === null ? null : round1(trendValues[i] as number),
  }));

  // --- Body fat % series (only entries with valid body fat data) ---
  const bodyFatRows = inRange.filter(
    (row) => row.bodyFatPct !== null && row.bodyFatPct > 0 && row.bodyFatPct < 100,
  );
  const bodyFatPoints: DataPoint[] = bodyFatRows.map((row) => ({
    date: toDateKey(row.measuredAt),
    value: row.bodyFatPct!,
  }));
  const bodyFatTrendValues = movingAverageByDays(bodyFatPoints, TREND_WINDOW_DAYS);
  const bodyFatSeries = bodyFatPoints.map((point, i) => ({
    date: point.date,
    bodyFatPct: round1(point.value),
    trend: bodyFatTrendValues[i] === null ? null : round1(bodyFatTrendValues[i] as number),
  }));

  // --- Lean mass series (derived from weight + body fat) ---
  const leanMassPoints: DataPoint[] = bodyFatRows.map((row) => ({
    date: toDateKey(row.measuredAt),
    value: round2(row.weightKg * (1 - row.bodyFatPct! / 100)),
  }));
  const leanMassTrendValues = movingAverageByDays(leanMassPoints, TREND_WINDOW_DAYS);
  const leanMassSeries = leanMassPoints.map((point, i) => ({
    date: point.date,
    leanMassKg: round2(point.value),
    trend: leanMassTrendValues[i] === null ? null : round1(leanMassTrendValues[i] as number),
  }));

  const lastValue = weightPoints.at(-1)?.value ?? null;
  const firstValue = weightPoints[0]?.value ?? null;
  const lastTrend = [...trendValues].reverse().find((v) => v !== null) ?? null;
  const ratePerWeekKg = linearRatePerWeek(weightPoints);

  // Body fat stats
  const lastBodyFat = bodyFatPoints.at(-1)?.value ?? null;
  const firstBodyFat = bodyFatPoints[0]?.value ?? null;
  const lastLeanMass = leanMassPoints.at(-1)?.value ?? null;
  const firstLeanMass = leanMassPoints[0]?.value ?? null;

  const weightSummary: StatsSummary["weight"] = {
    currentWeightKg: lastValue === null ? null : round2(lastValue),
    currentTrendKg: lastTrend === null ? null : round1(lastTrend),
    changeSinceStartKg:
      firstValue !== null && lastValue !== null ? round2(lastValue - firstValue) : null,
    ratePerWeekKg: ratePerWeekKg === null ? null : round2(ratePerWeekKg),
    minKg: weightPoints.length
      ? round2(weightPoints.reduce((min, p) => Math.min(min, p.value), Infinity))
      : null,
    maxKg: weightPoints.length
      ? round2(weightPoints.reduce((max, p) => Math.max(max, p.value), -Infinity))
      : null,
    currentBodyFatPct: lastBodyFat === null ? null : round1(lastBodyFat),
    changeBodyFatPct:
      firstBodyFat !== null && lastBodyFat !== null ? round1(lastBodyFat - firstBodyFat) : null,
    minBodyFatPct: bodyFatPoints.length
      ? round1(bodyFatPoints.reduce((min, p) => Math.min(min, p.value), Infinity))
      : null,
    maxBodyFatPct: bodyFatPoints.length
      ? round1(bodyFatPoints.reduce((max, p) => Math.max(max, p.value), -Infinity))
      : null,
    currentLeanMassKg: lastLeanMass === null ? null : round2(lastLeanMass),
    changeLeanMassKg:
      firstLeanMass !== null && lastLeanMass !== null
        ? round2(lastLeanMass - firstLeanMass)
        : null,
  };

  return {
    calories: nutritionSeries.map((p) => ({ ...p, calories: round1(p.calories) })),
    protein: nutritionSeries.map((p) => ({ ...p, protein: round1(p.protein) })),
    carbs: nutritionSeries.map((p) => ({ ...p, carbs: round1(p.carbs) })),
    fat: nutritionSeries.map((p) => ({ ...p, fat: round1(p.fat) })),
    weights: weightSeries,
    bodyFat: bodyFatSeries,
    leanMass: leanMassSeries,
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
