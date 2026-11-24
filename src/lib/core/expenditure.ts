import { addDaysToKey, daysBetweenKeys, toDateKey, weekStartOf } from "./dates";
import type { DataPoint } from "./stats";
import type {
  CalorieRecommendation,
  ExpenditureCheck,
  ExpenditureData,
  ExpenditureEstimate,
  ExpenditureNoticeDismissal,
  ExpenditureVerdict,
  MissingExpenditureData,
  WeightDTO,
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

// --- Weigh-ins taken in the same conditions ---

/**
 * A weigh-in as the weight trend needs it: its local day and kg, plus the
 * conditions it was taken in. A weigh-in after eating or training reads about
 * 0.5–1 kg above one on waking, and another scale reads differently, so only
 * weigh-ins taken alike are compared (`groupWeighIns`).
 */
export interface WeighIn extends DataPoint {
  /** ISO instant: orders weigh-ins across days and time zones. Absent: the date. */
  time?: string;
  /** On waking, before eating or drinking. Absent: true, what the app has always asked for. */
  fasted?: boolean;
  /** The device's time zone when it was saved («Europe/Zurich»). Absent or null: unknown. */
  timeZone?: string | null;
}

/** Stored weigh-ins as the trend takes them, each on its local calendar day (Android `weighInsOf`). */
export function weighInsOf(weights: Pick<WeightDTO, "measuredAt" | "weightKg" | "fasted" | "timeZone">[]): WeighIn[] {
  return weights.map((w) => ({
    date: toDateKey(new Date(w.measuredAt)),
    value: w.weightKg,
    time: w.measuredAt,
    fasted: w.fasted,
    timeZone: w.timeZone,
  }));
}

/** Weigh-ins as the fit takes them: one point per group and day, sorted by date. */
export interface GroupedWeighIns {
  points: DataPoint[];
  /** The group of each point: «f» (fasted) or «n» (not), then the time-zone period («f0», «n1»…). */
  groups: string[];
}

/**
 * Puts together the weigh-ins taken in the same conditions: fasted or not, and
 * in the same time zone. The period goes up when a weigh-in's known zone differs
 * from the last known one (a move usually means another scale); an unknown zone
 * never starts one, so summer time (same zone name) and old weigh-ins never
 * split. The groups come only from the switch and the zone, never from the
 * weights, so a real change of body weight is never taken for a change of
 * conditions. One point per group and day: the mean, as `dailyMeans`.
 */
export function groupWeighIns(weighIns: WeighIn[]): GroupedWeighIns {
  const ordered = [...weighIns].sort((a, b) => (a.time ?? a.date).localeCompare(b.time ?? b.date));
  const byGroupDay = new Map<string, { date: string; group: string; values: number[] }>();
  let period = 0;
  let lastZone: string | null = null;
  for (const weighIn of ordered) {
    const zone = weighIn.timeZone ?? null;
    if (zone !== null) {
      if (lastZone !== null && zone !== lastZone) period++;
      lastZone = zone;
    }
    const group = `${weighIn.fasted === false ? "n" : "f"}${period}`;
    const key = `${group}#${weighIn.date}`;
    const entry = byGroupDay.get(key) ?? { date: weighIn.date, group, values: [] };
    entry.values.push(weighIn.value);
    byGroupDay.set(key, entry);
  }
  const entries = [...byGroupDay.values()].sort(
    (a, b) => a.date.localeCompare(b.date) || a.group.localeCompare(b.group),
  );
  return {
    points: entries.map((e) => ({ date: e.date, value: e.values.reduce((a, b) => a + b, 0) / e.values.length })),
    groups: entries.map((e) => e.group),
  };
}

/** Least-squares line through weigh-ins, with what is needed for its error. */
export interface WeightTrend {
  slopePerDay: number;
  /** Standard error of the slope, kg/day. */
  slopeError: number;
  /** Scatter of the weigh-ins around the line, kg (never below the noise floor). */
  sigma: number;
  /** Points the fit used (groups with a single day carry no slope and are left out). */
  n: number;
  /**
   * The level of the group of the latest weigh-in: its mean x and y (x = days
   * since `origin`) and its number of points. With one group, all the points.
   */
  meanX: number;
  meanY: number;
  levelN: number;
  /** Σ(x − x̄)² within each group, added up. */
  sxx: number;
  /** Date of the first weigh-in: x = 0. */
  origin: string;
  /** The groups the fit used (`groupWeighIns`), oldest first. */
  groups: string[];
}

interface GroupSums {
  group: string;
  xs: number[];
  ys: number[];
  meanX: number;
  meanY: number;
}

/** The time-zone period of a group («f1» → 1; the default group «» → 0). */
function periodOf(group: string): number {
  return Number(group.slice(1)) || 0;
}

/**
 * Whether `g` is the group of the latest weigh-in rather than `best`: the later
 * last day; on the same day the later time-zone period, then fasted.
 */
function isLaterGroup(g: GroupSums, best: GroupSums): boolean {
  const days = Math.max(...g.xs) - Math.max(...best.xs);
  if (days !== 0) return days > 0;
  if (periodOf(g.group) !== periodOf(best.group)) return periodOf(g.group) > periodOf(best.group);
  return g.group < best.group;
}

/** The groups with 2+ points, their means, and the common slope within them (null without spread). */
function withinGroups(points: DataPoint[], groups: string[]) {
  const origin = points[0].date;
  const byGroup = new Map<string, { xs: number[]; ys: number[] }>();
  points.forEach((point, i) => {
    const entry = byGroup.get(groups[i]) ?? { xs: [], ys: [] };
    entry.xs.push(daysBetweenKeys(origin, point.date));
    entry.ys.push(point.value);
    byGroup.set(groups[i], entry);
  });
  const used: GroupSums[] = [...byGroup.entries()]
    .filter(([, g]) => g.xs.length >= 2)
    .map(([group, g]) => ({
      group,
      xs: g.xs,
      ys: g.ys,
      meanX: g.xs.reduce((a, b) => a + b, 0) / g.xs.length,
      meanY: g.ys.reduce((a, b) => a + b, 0) / g.ys.length,
    }));
  let sxx = 0;
  let sxy = 0;
  for (const g of used) {
    g.xs.forEach((x, i) => {
      sxx += (x - g.meanX) ** 2;
      sxy += (x - g.meanX) * (g.ys[i] - g.meanY);
    });
  }
  return { origin, used, sxx, slopePerDay: sxx === 0 ? null : sxy / sxx };
}

/**
 * Least-squares weight trend and its standard error, compared only within
 * groups of weigh-ins taken alike (`groups`, from `groupWeighIns`; absent = one
 * group): each group has its own level and all share one slope (fixed effects;
 * Montgomery 2021). A jump between groups (fasted → after breakfast, a new
 * scale) therefore never counts as weight change. With one group it is the
 * ordinary least-squares line.
 *
 *   β = Σ_g Sxy_g / Σ_g Sxx_g,   σ = max(√(RSS / (n − G − 1)), 0.5 kg),   SE = σ / √Σ_g Sxx_g
 *
 * Groups with a single day carry no slope and are left out. Fewer or clustered
 * weigh-ins give a small Σ Sxx, noisier ones a larger σ; both widen the error.
 * Points must be sorted by date. Null without a degree of freedom or spread.
 */
export function fitWeightTrend(points: DataPoint[], groups: string[] = points.map(() => "")): WeightTrend | null {
  if (points.length === 0) return null;
  const { origin, used, sxx, slopePerDay } = withinGroups(points, groups);
  const n = used.reduce((sum, g) => sum + g.xs.length, 0);
  if (slopePerDay === null || n - used.length - 1 < 1) return null;
  let squaredResiduals = 0;
  for (const g of used) {
    g.xs.forEach((x, i) => {
      squaredResiduals += (g.ys[i] - (g.meanY + slopePerDay * (x - g.meanX))) ** 2;
    });
  }
  const sigma = Math.max(Math.sqrt(squaredResiduals / (n - used.length - 1)), WEIGHT_NOISE_FLOOR_KG);
  const latest = used.reduce((best, g) => (isLaterGroup(g, best) ? g : best));
  return {
    slopePerDay,
    slopeError: sigma / Math.sqrt(sxx),
    sigma,
    n,
    meanX: latest.meanX,
    meanY: latest.meanY,
    levelN: latest.xs.length,
    sxx,
    origin,
    groups: used.map((g) => g.group),
  };
}

/**
 * Only the slope within groups (kg/day), for a rate on screen (Progreso): needs
 * spread inside a group, not degrees of freedom. Null without it.
 */
export function slopeWithinGroups(grouped: GroupedWeighIns): number | null {
  return grouped.points.length === 0 ? null : withinGroups(grouped.points, grouped.groups).slopePerDay;
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
 * one point per weigh-in, with the conditions it was taken in (`WeighIn`): the
 * trend compares only weigh-ins taken alike (`groupWeighIns`). Both are
 * restricted to the 28 days before `today`.
 *
 * The estimate is null until the data can support it: ≥21 logged days and ≥4
 * weigh-in days spanning ≥14 days. It always carries its 95 % margin
 * (1.96 × slope SE × 7700) and there is no largest one: a wide margin makes the
 * data count less in the target (`combineExpenditure`) and makes the profile
 * check harder to trigger (`verdictOf`), both gradually. A cut-off would turn a
 * margin a few kcal over it into a target that jumps from one week to the next.
 * The counts come back either way, so the app can say what is missing.
 */
export function measureExpenditure(intake: DataPoint[], weights: WeighIn[], today: string): ExpenditureData {
  const from = addDaysToKey(today, -EXPENDITURE_WINDOW_DAYS);
  const inWindow = (point: DataPoint) => point.date >= from && point.date < today;

  const caloriesByDay = new Map<string, number>();
  for (const point of intake.filter(inWindow)) {
    caloriesByDay.set(point.date, (caloriesByDay.get(point.date) ?? 0) + point.value);
  }
  const loggedTotals = [...caloriesByDay.values()].filter((kcal) => kcal > 0);
  const loggedDays = loggedTotals.length;

  const grouped = groupWeighIns(weights.filter(inWindow));
  const days = [...new Set(grouped.points.map((point) => point.date))];
  const weighInDays = days.length;
  const weightSpanDays = weighInDays < 2 ? 0 : daysBetweenKeys(days[0], days[days.length - 1]);
  const trend = fitWeightTrend(grouped.points, grouped.groups);
  const margin = trend === null ? null : Math.round(Z_95 * trend.slopeError * KCAL_PER_KG);

  const data: ExpenditureData = { loggedDays, weighInDays, weightSpanDays, margin, estimate: null };
  if (
    loggedDays < MIN_LOGGED_DAYS ||
    weighInDays < MIN_WEIGH_INS ||
    weightSpanDays < MIN_WEIGHT_SPAN_DAYS ||
    trend === null ||
    margin === null
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
      ...conditionsOf(trend.groups, grouped),
    },
  };
}

/**
 * What the Perfil «why» says about the groups the trend used: how many, whether
 * fasted and not-fasted weigh-ins were both among them, and the first day in a
 * new time zone when they span a move.
 */
function conditionsOf(
  used: string[],
  grouped: GroupedWeighIns,
): Pick<ExpenditureEstimate, "groups" | "mixedFasting" | "newZoneSince"> {
  const latestPeriod = Math.max(...used.map(periodOf));
  const periods = new Set(used.map(periodOf));
  const firstInLatest = grouped.points.find((_, i) => used.includes(grouped.groups[i]) && periodOf(grouped.groups[i]) === latestPeriod);
  return {
    groups: used.length,
    mixedFasting: used.some((g) => g.startsWith("f")) && used.some((g) => g.startsWith("n")),
    newZoneSince: periods.size > 1 && firstInLatest ? firstInLatest.date : null,
  };
}

/** The measured expenditure, or null until the data can support it (see `measureExpenditure`). */
export function estimateExpenditure(
  intake: DataPoint[],
  weights: WeighIn[],
  today: string,
): ExpenditureEstimate | null {
  return measureExpenditure(intake, weights, today).estimate;
}

// --- The target follows the data ---

/**
 * One standard deviation of the profile's expenditure (BMR × activity factor)
 * for one person, as a fraction of it. Mifflin-St Jeor alone predicts the
 * basal rate within ±10 % for about 82 % of healthy adults (Frankenfield 2005),
 * an SD of about 7.5 %; the activity factor adds its own error.
 */
export const FORMULA_SD = 0.1;

/**
 * The expenditure a target is built on: the profile's and the measured one
 * combined by their precision (inverse-variance weighting, the usual way to
 * combine two estimates of one quantity):
 *
 *   share = σ_profile² / (σ_profile² + σ_measured²),  σ_profile = 10 % × profile,  σ_measured = margin / 1.96
 *   expenditure = profile + share × (measured − profile)
 *
 * Without a measurement it is the profile's; with daily weigh-ins the data
 * weighs about 90 %. `dataShare` is `share` rounded to 2 decimals.
 */
export function combineExpenditure(
  formulaTdee: number,
  measured: ExpenditureEstimate | null,
): { expenditure: number; dataShare: number } {
  if (measured === null) return { expenditure: formulaTdee, dataShare: 0 };
  const formulaVariance = (FORMULA_SD * formulaTdee) ** 2;
  const measuredVariance = (measured.margin / Z_95) ** 2;
  const share = formulaVariance / (formulaVariance + measuredVariance);
  return {
    expenditure: Math.round(formulaTdee + share * (measured.tdee - formulaTdee)),
    dataShare: Math.round(share * 100) / 100,
  };
}

/**
 * The measured expenditure each day's target uses: the one taken at the start
 * of its week (Monday, `weekStartOf`) from the 4 weeks before, so targets change
 * once a week, not with every weigh-in. Memoized per week: Progreso asks for
 * every day of a period.
 */
export function weeklyExpenditure(
  intake: DataPoint[],
  weights: WeighIn[],
): (day: string) => ExpenditureEstimate | null {
  const byWeek = new Map<string, ExpenditureEstimate | null>();
  return (day) => {
    const monday = weekStartOf(day);
    if (!byWeek.has(monday)) byWeek.set(monday, estimateExpenditure(intake, weights, monday));
    return byWeek.get(monday) ?? null;
  };
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

/**
 * What stops the measurement, in the order the user should fix it. Enough
 * weigh-in days with no trend (`margin` null) means no group of weigh-ins taken
 * alike has 2+ days yet: more weigh-ins are needed.
 */
export function missingExpenditureData(data: ExpenditureData): MissingExpenditureData[] {
  const missing: MissingExpenditureData[] = [];
  if (data.loggedDays < MIN_LOGGED_DAYS) missing.push("meals");
  if (data.weighInDays < MIN_WEIGH_INS) missing.push("weighIns");
  else if (data.weightSpanDays < MIN_WEIGHT_SPAN_DAYS) missing.push("weightSpan");
  else if (data.margin === null) missing.push("weighIns");
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
  weights: WeighIn[],
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
