import { addDaysToKey, daysBetweenKeys } from "./dates";
import type { DataPoint } from "./stats";
import type { ExpenditureEstimate } from "./types";

/** The last 4 complete weeks (today is excluded: it is still being logged). */
export const EXPENDITURE_WINDOW_DAYS = 28;
/**
 * Days with food logged (3 of every 4). The weight change reflects every day,
 * logged or not, so the intake average must cover nearly all of them.
 */
export const MIN_LOGGED_DAYS = 21;
/** Weigh-ins (distinct days) needed for a weight trend. */
export const MIN_WEIGH_INS = 4;
/** Days between the first and last weigh-in, so water swings do not dominate. */
export const MIN_WEIGHT_SPAN_DAYS = 14;
/** Approximate energy in 1 kg of body-weight change (Hall 2008). */
export const KCAL_PER_KG = 7700;
/**
 * Smallest day-to-day scatter assumed for a weigh-in around the trend (water,
 * glycogen, gut content). A few weigh-ins can fall on a line by chance; this
 * keeps them from looking more precise than a scale can be.
 */
export const WEIGHT_NOISE_FLOOR_KG = 0.5;
/** Largest 95 % margin (kcal/day) at which the measured value is shown. */
export const MAX_MARGIN_KCAL = 300;
/** Two-sided 95 % normal quantile. */
export const Z_95 = 1.96;

/** Least-squares line through weigh-ins, with what is needed for its error. */
export interface WeightTrend {
  slopePerDay: number;
  /** Standard error of the slope, kg/day. */
  slopeError: number;
  /** Scatter of the weigh-ins around the line, kg (never below the noise floor). */
  sigma: number;
  n: number;
  /** Mean of x and of y (x = days since `origin`). */
  meanX: number;
  meanY: number;
  /** Σ(x − x̄)². */
  sxx: number;
  /** Date of the first weigh-in: x = 0. */
  origin: string;
}

/**
 * Least-squares weight trend and its standard error. The scatter around the
 * line is estimated from the residuals (n − 2 degrees of freedom), never below
 * WEIGHT_NOISE_FLOOR_KG: SE = σ / √Σ(x − x̄)². Fewer or clustered weigh-ins
 * give a small Σ(x − x̄)², noisier ones a larger σ; both widen the error.
 * Points must be sorted by date. Null with fewer than 3 points or one day.
 */
export function fitWeightTrend(points: DataPoint[]): WeightTrend | null {
  const n = points.length;
  if (n < 3) return null;
  const origin = points[0].date;
  const xs = points.map((p) => daysBetweenKeys(origin, p.date));
  const ys = points.map((p) => p.value);
  const meanX = xs.reduce((a, b) => a + b, 0) / n;
  const meanY = ys.reduce((a, b) => a + b, 0) / n;
  let sxx = 0;
  let sxy = 0;
  for (let i = 0; i < n; i++) {
    sxx += (xs[i] - meanX) ** 2;
    sxy += (xs[i] - meanX) * (ys[i] - meanY);
  }
  if (sxx === 0) return null;
  const slopePerDay = sxy / sxx;
  let squaredResiduals = 0;
  for (let i = 0; i < n; i++) {
    squaredResiduals += (ys[i] - (meanY + slopePerDay * (xs[i] - meanX))) ** 2;
  }
  const sigma = Math.max(Math.sqrt(squaredResiduals / (n - 2)), WEIGHT_NOISE_FLOOR_KG);
  return { slopePerDay, slopeError: sigma / Math.sqrt(sxx), sigma, n, meanX, meanY, sxx, origin };
}

/**
 * Measured TDEE from energy balance, the approach of adaptive apps such as
 * MacroFactor: if weight is stable, you burn what you eat; if it moves, the
 * difference is the energy stored or released.
 *
 *   TDEE = average daily intake − (weight slope in kg/day × 7700 kcal/kg)
 *
 * `intake` holds one point per meal (date = log date, value = kcal); days with
 * no meals are unlogged and left out, never counted as 0 kcal. `weights` holds
 * one point per weigh-in. Both are restricted to the 28 days before `today`.
 *
 * Returns null until the data can support it: ≥21 logged days, ≥4 weigh-in
 * days spanning ≥14 days, and a 95 % margin (1.96 × slope SE × 7700) of at
 * most ±300 kcal/day. In practice that means weighing in ~3 times a week.
 */
export function estimateExpenditure(
  intake: DataPoint[],
  weights: DataPoint[],
  today: string,
): ExpenditureEstimate | null {
  const from = addDaysToKey(today, -EXPENDITURE_WINDOW_DAYS);
  const inWindow = (point: DataPoint) => point.date >= from && point.date < today;

  const caloriesByDay = new Map<string, number>();
  for (const point of intake.filter(inWindow)) {
    caloriesByDay.set(point.date, (caloriesByDay.get(point.date) ?? 0) + point.value);
  }
  const loggedTotals = [...caloriesByDay.values()].filter((kcal) => kcal > 0);
  if (loggedTotals.length < MIN_LOGGED_DAYS) return null;

  const weighIns = weights.filter(inWindow).sort((a, b) => a.date.localeCompare(b.date));
  const weighInDays = new Set(weighIns.map((point) => point.date)).size;
  if (weighInDays < MIN_WEIGH_INS) return null;
  if (daysBetweenKeys(weighIns[0].date, weighIns[weighIns.length - 1].date) < MIN_WEIGHT_SPAN_DAYS) {
    return null;
  }

  const trend = fitWeightTrend(weighIns);
  if (trend === null) return null;
  const margin = Math.round(Z_95 * trend.slopeError * KCAL_PER_KG);
  if (margin > MAX_MARGIN_KCAL) return null;

  const avgIntake = loggedTotals.reduce((a, b) => a + b, 0) / loggedTotals.length;
  const tdee = Math.round(avgIntake - trend.slopePerDay * KCAL_PER_KG);
  if (tdee <= 0) return null;

  return {
    tdee,
    margin,
    avgIntake: Math.round(avgIntake),
    weightChangePerWeek: Math.round(trend.slopePerDay * 7 * 100) / 100,
    loggedDays: loggedTotals.length,
    weighIns: weighInDays,
    windowDays: EXPENDITURE_WINDOW_DAYS,
  };
}
