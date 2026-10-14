import { addDaysToKey, daysBetweenKeys } from "./dates";
import type { DataPoint } from "./stats";
import type {
  CalorieRecommendation,
  ExpenditureCheck,
  ExpenditureData,
  ExpenditureEstimate,
  ExpenditureNoticeDismissal,
  ExpenditureVerdict,
  MissingExpenditureData,
} from "./types";

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

/**
 * One point per day: the mean of that day's weigh-ins, sorted by date. Several
 * weigh-ins on one day (morning and evening can differ by a kilo) are not
 * independent measurements of the trend, so they must not count as several
 * points in the regression or its error.
 */
export function dailyMeans(points: DataPoint[]): DataPoint[] {
  const byDay = new Map<string, number[]>();
  for (const point of points) {
    const values = byDay.get(point.date) ?? [];
    values.push(point.value);
    byDay.set(point.date, values);
  }
  return [...byDay.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, values]) => ({ date, value: values.reduce((a, b) => a + b, 0) / values.length }));
}

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
 * The estimate is null until the data can support it: ≥21 logged days, ≥4
 * weigh-in days spanning ≥14 days, and a 95 % margin (1.96 × slope SE × 7700)
 * of at most ±300 kcal/day. In practice that means weighing in ~3 times a week.
 * The counts come back either way, so the app can say what is missing.
 */
export function measureExpenditure(intake: DataPoint[], weights: DataPoint[], today: string): ExpenditureData {
  const from = addDaysToKey(today, -EXPENDITURE_WINDOW_DAYS);
  const inWindow = (point: DataPoint) => point.date >= from && point.date < today;

  const caloriesByDay = new Map<string, number>();
  for (const point of intake.filter(inWindow)) {
    caloriesByDay.set(point.date, (caloriesByDay.get(point.date) ?? 0) + point.value);
  }
  const loggedTotals = [...caloriesByDay.values()].filter((kcal) => kcal > 0);
  const loggedDays = loggedTotals.length;

  const weighIns = dailyMeans(weights.filter(inWindow));
  const weighInDays = weighIns.length;
  const weightSpanDays =
    weighInDays < 2 ? 0 : daysBetweenKeys(weighIns[0].date, weighIns[weighIns.length - 1].date);
  const trend = fitWeightTrend(weighIns);
  const margin = trend === null ? null : Math.round(Z_95 * trend.slopeError * KCAL_PER_KG);

  const data: ExpenditureData = { loggedDays, weighInDays, weightSpanDays, margin, estimate: null };
  if (
    loggedDays < MIN_LOGGED_DAYS ||
    weighInDays < MIN_WEIGH_INS ||
    weightSpanDays < MIN_WEIGHT_SPAN_DAYS ||
    trend === null ||
    margin === null ||
    margin > MAX_MARGIN_KCAL
  ) {
    return data;
  }

  const avgIntake = loggedTotals.reduce((a, b) => a + b, 0) / loggedDays;
  const tdee = Math.round(avgIntake - trend.slopePerDay * KCAL_PER_KG);
  if (tdee <= 0) return data;

  return {
    ...data,
    estimate: {
      tdee,
      margin,
      avgIntake: Math.round(avgIntake),
      weightChangePerWeek: Math.round(trend.slopePerDay * 7 * 100) / 100,
      loggedDays,
      weighIns: weighInDays,
      windowDays: EXPENDITURE_WINDOW_DAYS,
    },
  };
}

/** The measured expenditure, or null until the data can support it (see `measureExpenditure`). */
export function estimateExpenditure(
  intake: DataPoint[],
  weights: DataPoint[],
  today: string,
): ExpenditureEstimate | null {
  return measureExpenditure(intake, weights, today).estimate;
}

// --- Checking the profile against the data ---

/**
 * How far the formula can be off for anyone, as a fraction of its expenditure.
 * Mifflin-St Jeor predicts the basal rate within ±10 % of the measured value in
 * most healthy adults, more often than any other equation (Frankenfield 2005).
 * Gaps inside this band are the formula's normal error, not something to fix.
 */
export const FORMULA_TOLERANCE = 0.1;
/** The verdict must also hold on the window that ended a week earlier. */
export const CHECK_CONFIRM_DAYS = 7;
/** Days of meals the check reads: this window plus the one a week earlier. */
export const CHECK_DAYS = EXPENDITURE_WINDOW_DAYS + CHECK_CONFIRM_DAYS;
/** A dismissed notice comes back after a whole new window of data. */
export const NOTICE_SNOOZE_DAYS = EXPENDITURE_WINDOW_DAYS;

/** What stops the measurement, in the order the user should fix it. */
export function missingExpenditureData(data: ExpenditureData): MissingExpenditureData[] {
  const missing: MissingExpenditureData[] = [];
  if (data.loggedDays < MIN_LOGGED_DAYS) missing.push("meals");
  if (data.weighInDays < MIN_WEIGH_INS) missing.push("weighIns");
  else if (data.weightSpanDays < MIN_WEIGHT_SPAN_DAYS) missing.push("weightSpan");
  else if (data.margin === null || data.margin > MAX_MARGIN_KCAL) missing.push("margin");
  return missing;
}

/**
 * Above, inside or below the formula's normal range. Strict on purpose: the
 * whole 95 % range of the measurement must lie outside formula ± 10 %, so the
 * gap is beyond both the formula's normal error and the measurement's own.
 */
function verdictOf(estimate: ExpenditureEstimate, normalMin: number, normalMax: number): ExpenditureVerdict {
  if (estimate.tdee - estimate.margin > normalMax) return "higher";
  if (estimate.tdee + estimate.margin < normalMin) return "lower";
  return "matches";
}

/**
 * Checks the profile's expenditure (BMR × activity factor) against the one
 * measured from meals and weigh-ins. A "higher" or "lower" verdict is
 * `confirmed` only if the window that ended a week earlier says the same, so a
 * single odd week (a party, a scale on a new floor) cannot trigger it. The two
 * windows share 21 days: this is about stability, not extra statistical
 * confidence. `intake` must cover `CHECK_DAYS` before today.
 */
export function checkExpenditure(
  intake: DataPoint[],
  weights: DataPoint[],
  formula: Pick<CalorieRecommendation, "bmr" | "activityFactor" | "tdee">,
  today: string,
): ExpenditureCheck {
  const data = measureExpenditure(intake, weights, today);
  const estimate = data.estimate;
  if (estimate === null) return { status: "notEnoughData", data, missing: missingExpenditureData(data) };

  const normalMin = Math.round(formula.tdee * (1 - FORMULA_TOLERANCE));
  const normalMax = Math.round(formula.tdee * (1 + FORMULA_TOLERANCE));
  const verdict = verdictOf(estimate, normalMin, normalMax);
  let confirmed = true;
  if (verdict !== "matches") {
    const weekBefore = estimateExpenditure(intake, weights, addDaysToKey(today, -CHECK_CONFIRM_DAYS));
    confirmed = weekBefore !== null && verdictOf(weekBefore, normalMin, normalMax) === verdict;
  }
  return {
    status: "measured",
    verdict,
    confirmed,
    estimate,
    formulaTdee: formula.tdee,
    bmr: formula.bmr,
    activityFactor: formula.activityFactor,
    normalMin,
    normalMax,
    gap: estimate.tdee - formula.tdee,
    impliedActivityFactor: Math.round((estimate.tdee / formula.bmr) * 100) / 100,
  };
}

/** The gap as the Today card says it: «about 500», rounded to 50 kcal. */
export function approximateGap(gap: number): number {
  return Math.round(Math.abs(gap) / 50) * 50;
}

/**
 * The Today card mentions the gap only when it is confirmed, and not again
 * after «Not now» unless the verdict changes or a whole new window has passed.
 */
export function showsGapNotice(
  check: ExpenditureCheck | null,
  dismissed: ExpenditureNoticeDismissal | null,
  today: string,
): boolean {
  if (check?.status !== "measured" || check.verdict === "matches" || !check.confirmed) return false;
  if (dismissed === null || dismissed.verdict !== check.verdict) return true;
  return daysBetweenKeys(dismissed.date, today) >= NOTICE_SNOOZE_DAYS;
}

/**
 * The one case where a single habit unlocks the check: meals are logged well
 * enough, only the weigh-ins are missing. Shown once on Today until dismissed.
 */
export function onlyWeighInsMissing(check: ExpenditureCheck | null): boolean {
  return check?.status === "notEnoughData" && check.missing.length > 0 && !check.missing.includes("meals");
}
