import { round1 } from "../nutrition";
import {
  dayTotals,
  daysOf,
  foodItems,
  inRange,
  matchesTerms,
  namesByDays,
  searchTerms,
  type DayRange,
  type DayTotals,
  type FoodItem,
  type FoodLog,
} from "./log";
import type { FoodRequest } from "./request";
import { FOOD_QUESTIONS, RULES, fill, num, type AnalysisId, type GroupLabel, type Metric } from "./spec";

const T = FOOD_QUESTIONS.result;

/** A part of the log an answer counted, for «Cómo se calculó». */
export interface FoodGroup {
  label: GroupLabel;
  from: string;
  to: string;
  /** Days with meals logged in it (the averages' divisor). */
  loggedDays: number;
  /** Calendar days in it. */
  totalDays: number;
}

/** What the app shows under the answer («Cómo se calculó»): written by the app, not the model. */
export interface FoodDetails {
  analysis: AnalysisId;
  groups: FoodGroup[];
  /** Logged names counted, the most frequent first (at most `countedNamesShown`). */
  counted: string[];
  moreCounted: number;
  /** Entries without an amount in g or ml (left out of the amounts). */
  withoutAmount: number;
  /** Fewer than `fewLoggedDays` logged days in a group: a hint, not a firm average. */
  few: boolean;
  /** Where food_vs_weight split the period, and why. */
  split: { at: string; reason: SplitReason } | null;
}

export type SplitReason = "asked" | "stopped" | "started" | "halves";

export type NotEnoughReason = "logged_days" | "side_days" | "weigh_ins";

/**
 * The outcome of a request. Numbers to explain go to the model (`result`); when
 * there is nothing to compute the app answers itself (`no_match`, `not_enough`).
 */
export type FoodAnswer =
  | { kind: "result"; text: string; details: FoodDetails }
  | { kind: "no_match"; foods: string[]; range: DayRange; lastSeen: string | null; similar: string[] }
  | { kind: "not_enough"; reason: NotEnoughReason; details: FoodDetails };

/** The log read once per answer: foods and each day's totals. */
export interface PreparedLog {
  log: FoodLog;
  items: FoodItem[];
  days: Map<string, DayTotals>;
}

export function prepareLog(log: FoodLog): PreparedLog {
  const meals = log.meals.filter((meal) => meal.logDate <= log.today);
  return { log, items: foodItems(meals), days: dayTotals(meals) };
}

/** The days of [dates] with meals logged (any calories). */
export function loggedDaysOf(prepared: PreparedLog, dates: string[]): string[] {
  return dates.filter((date) => (prepared.days.get(date)?.calories ?? 0) > 0);
}

export function groupOf(prepared: PreparedLog, label: GroupLabel, range: DayRange, dates = daysOf(range)): FoodGroup {
  return { label, from: range.from, to: range.to, loggedDays: loggedDaysOf(prepared, dates).length, totalDays: dates.length };
}

export function details(analysis: AnalysisId, groups: FoodGroup[], counted: FoodItem[] = []): FoodDetails {
  const names = namesByDays(counted);
  return {
    analysis,
    groups,
    counted: names.slice(0, RULES.countedNamesShown),
    moreCounted: Math.max(0, names.length - RULES.countedNamesShown),
    withoutAmount: counted.filter((item) => item.amount === null).length,
    few: groups.some((group) => group.loggedDays < RULES.fewLoggedDays),
    split: null,
  };
}

/** The groups with too few logged days for an average, if any. */
export function notEnough(analysis: AnalysisId, groups: FoodGroup[], counted: FoodItem[] = []): FoodAnswer | null {
  if (groups.every((group) => group.loggedDays >= RULES.minLoggedDays)) return null;
  return { kind: "not_enough", reason: "logged_days", details: details(analysis, groups, counted) };
}

/** The foods matching the request's search words, in [range]. */
export function matchedItems(prepared: PreparedLog, request: FoodRequest, range: DayRange): FoodItem[] {
  const terms = searchTerms(request.foods);
  return prepared.items.filter((item) => inRange(item.date, range) && matchesTerms(item.key, terms));
}

/** Nothing logged matches: when it was last logged, and names that look alike in the period. */
export function noMatch(prepared: PreparedLog, request: FoodRequest, range: DayRange): FoodAnswer {
  const terms = searchTerms(request.foods);
  const lastSeen = prepared.items
    .filter((item) => matchesTerms(item.key, terms))
    .reduce<string | null>((latest, item) => (latest === null || item.date > latest ? item.date : latest), null);
  const stems = terms.flat().map((word) => word.slice(0, 4)).filter((stem) => stem.length >= 3);
  const alike = prepared.items.filter(
    (item) => inRange(item.date, range) && item.key.split(" ").some((word) => stems.some((stem) => word.startsWith(stem))),
  );
  return {
    kind: "no_match",
    foods: request.foods,
    range,
    lastSeen,
    similar: namesByDays(alike).slice(0, RULES.similarNamesShown),
  };
}

export function metricOf(totals: { calories: number; protein: number; carbs: number; fat: number }, metric: Metric): number {
  if (metric === "protein") return totals.protein;
  if (metric === "carbs") return totals.carbs;
  if (metric === "fat") return totals.fat;
  return totals.calories;
}

/** kcal as integers, grams with one decimal. */
export function metricValue(value: number, metric: Metric): string {
  return num(metric === "calories" ? Math.round(value) : round1(value));
}

export function macrosText(totals: DayTotals): string {
  return fill(T.macros, {
    calories: num(Math.round(totals.calories)),
    protein: num(round1(totals.protein)),
    carbs: num(round1(totals.carbs)),
    fat: num(round1(totals.fat)),
  });
}

export function periodLine(group: FoodGroup): string {
  return group.label === "period"
    ? fill(T.period, { from: group.from, to: group.to, days: group.totalDays, logged: group.loggedDays })
    : fill(T.group, { label: T.labels[group.label], from: group.from, to: group.to, days: group.totalDays, logged: group.loggedDays });
}

/** «Logged names counted: …» and «Only N days…» when they apply. */
export function countedLines(found: FoodDetails): string[] {
  const lines: string[] = [];
  if (found.counted.length > 0) {
    const names = found.counted.join(", ");
    lines.push(found.moreCounted > 0 ? fill(T.countedMore, { names, more: found.moreCounted }) : fill(T.counted, { names }));
  }
  const fewest = Math.min(...found.groups.map((group) => group.loggedDays));
  if (found.few) lines.push(fill(T.few, { logged: fewest }));
  return lines;
}

/**
 * The result block for the model: the header and [lines], cut at whole lines to
 * `resultMaxChars` (≈ 400 tokens) so it fits the phone model's window.
 */
export function resultText(lines: string[]): string {
  const kept = [T.header];
  let length = T.header.length;
  for (const [index, line] of lines.entries()) {
    const room = index === lines.length - 1 ? 0 : T.cut.length + 1;
    if (length + 1 + line.length + room > RULES.resultMaxChars) {
      kept.push(T.cut);
      break;
    }
    kept.push(line);
    length += 1 + line.length;
  }
  return kept.join("\n");
}

export function result(lines: string[], found: FoodDetails): FoodAnswer {
  return { kind: "result", text: resultText(lines), details: found };
}

/**
 * One kind of question the app can answer from the log. [answer] gets requests
 * of its own `id` only, already checked by `parseFoodRequest`; it returns null
 * when the request cannot be answered as asked (a period that ends before it
 * starts, a metric it does not take), which the coach reports as not understood.
 */
export interface Analysis {
  id: AnalysisId;
  answer(prepared: PreparedLog, request: FoodRequest): FoodAnswer | null;
}

