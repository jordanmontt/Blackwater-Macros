import { addDaysToKey, daysBetweenKeys } from "../../dates";
import { MIN_WEIGHT_SPAN_DAYS, MIN_WEIGH_INS, Z_95, fitWeightTrend, groupWeighIns } from "../../expenditure";
import { round2 } from "../../nutrition";
import {
  details,
  groupOf,
  loggedDaysOf,
  matchedItems,
  noMatch,
  notEnough,
  periodLine,
  countedLines,
  result,
  type Analysis,
  type FoodGroup,
  type PreparedLog,
  type SplitReason,
} from "../answer";
import { daysOf, inRange, rangeDays, resolveRange, type DayRange, type FoodItem } from "../log";
import { FOOD_QUESTIONS, RULES, fill, num } from "../spec";

const T = FOOD_QUESTIONS.result;

const SPLIT_TEXT: Record<SplitReason, string> = {
  asked: T.weightSplitAsked,
  stopped: T.weightSplitStopped,
  started: T.weightSplitStarted,
  halves: T.weightSplitHalves,
};

export interface WeightRate {
  ratePerWeek: number;
  /** ± kg/week, 95 % interval of the slope. */
  marginPerWeek: number;
  weighInDays: number;
}

/**
 * The weight trend over [range], with the same data rules and fit as the
 * measured expenditure (≥ 4 weigh-in days spanning ≥ 14 days, compared only
 * within weigh-ins taken alike). Null when the weigh-ins cannot support one.
 */
export function weightRateIn(prepared: PreparedLog, range: DayRange): WeightRate | null {
  const weighIns = prepared.log.weights
    .filter((weight) => inRange(weight.date, range))
    .map((weight) => ({ date: weight.date, value: weight.weightKg, time: weight.time, fasted: weight.fasted, timeZone: weight.timeZone }));
  const grouped = groupWeighIns(weighIns);
  const days = [...new Set(grouped.points.map((point) => point.date))];
  if (days.length < MIN_WEIGH_INS || daysBetweenKeys(days[0], days[days.length - 1]) < MIN_WEIGHT_SPAN_DAYS) return null;
  const trend = fitWeightTrend(grouped.points, grouped.groups);
  if (trend === null) return null;
  return {
    ratePerWeek: round2(trend.slopePerDay * 7),
    marginPerWeek: round2(Z_95 * trend.slopeError * 7),
    weighInDays: days.length,
  };
}

/**
 * Where to split the period: the day the user named; else the day after the
 * foods were last logged (stopped), the first day they were (started), or the
 * middle when they were logged all along.
 */
function splitPoint(request: { at: string | null }, range: DayRange, items: FoodItem[]): { at: string; reason: SplitReason } {
  if (request.at !== null) return { at: request.at, reason: "asked" };
  const dates = items.map((item) => item.date).sort();
  const first = dates[0];
  const last = dates[dates.length - 1];
  if (daysBetweenKeys(last, range.to) >= RULES.changeGapDays) return { at: addDaysToKey(last, 1), reason: "stopped" };
  if (daysBetweenKeys(range.from, first) >= RULES.changeGapDays) return { at: first, reason: "started" };
  return { at: addDaysToKey(range.from, Math.floor(rangeDays(range) / 2)), reason: "halves" };
}

/**
 * The weight trend before and after a change in a food: «¿Cambió mi peso desde
 * que dejé la cerveza?». An association, never a cause: the text says so.
 */
export const foodVsWeight: Analysis = {
  id: "food_vs_weight",
  answer(prepared, request) {
    const today = prepared.log.today;
    // A day named without a period: as many days on each side as the default period has in all.
    const half = RULES.weightDefaultDays / 2;
    const range =
      request.at !== null && request.from === null && request.to === null
        ? resolveRange(addDaysToKey(request.at, -half), addDaysToKey(request.at, half - 1), today, RULES.weightDefaultDays)
        : resolveRange(request.from, request.to, today, RULES.weightDefaultDays);
    if (range === null) return null;
    if (request.at !== null && (request.at <= range.from || request.at > range.to)) return null;
    const items = matchedItems(prepared, request, range);
    if (items.length === 0) return noMatch(prepared, request, range);

    const split = splitPoint(request, range, items);
    const before = { from: range.from, to: addDaysToKey(split.at, -1) };
    const after = { from: split.at, to: range.to };
    const groups: FoodGroup[] = [groupOf(prepared, "before", before), groupOf(prepared, "after", after)];
    const found = { ...details("food_vs_weight", groups, items), split };
    if (groups.some((group) => group.totalDays < RULES.minSideDays)) return { kind: "not_enough", reason: "side_days", details: found };
    const short = notEnough("food_vs_weight", groups, items);
    if (short) return { ...short, details: found };
    const rates = [weightRateIn(prepared, before), weightRateIn(prepared, after)];
    if (rates.every((rate) => rate === null)) return { kind: "not_enough", reason: "weigh_ins", details: found };

    const lines = [...groups.map(periodLine), fill(SPLIT_TEXT[split.reason], { at: split.at }), ...countedLines(found)];
    [before, after].forEach((side, index) => {
      const logged = loggedDaysOf(prepared, daysOf(side));
      const eaten = items.filter((item) => inRange(item.date, side));
      let total = 0;
      for (const date of logged) total += prepared.days.get(date)!.calories;
      lines.push(
        fill(T.weightFood, {
          label: T.labels[groups[index].label],
          eatenDays: new Set(eaten.map((item) => item.date)).size,
          logged: logged.length,
          calories: num(Math.round(eaten.reduce((sum, item) => sum + item.calories, 0) / logged.length)),
          total: num(Math.round(total / logged.length)),
        }),
      );
    });
    rates.forEach((rate, index) => {
      const label = T.labels[groups[index].label];
      lines.push(
        rate === null
          ? fill(T.weightTrendMissing, { label, minWeighIns: MIN_WEIGH_INS, minSpan: MIN_WEIGHT_SPAN_DAYS })
          : fill(T.weightTrend, { label, rate: num(rate.ratePerWeek), margin: num(rate.marginPerWeek), weighIns: rate.weighInDays }),
      );
    });
    lines.push(T.association);
    return result(lines, found);
  },
};
