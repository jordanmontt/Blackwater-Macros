import { round1 } from "../../nutrition";
import {
  details,
  groupOf,
  loggedDaysOf,
  matchedItems,
  metricOf,
  metricValue,
  noMatch,
  notEnough,
  periodLine,
  countedLines,
  result,
  type Analysis,
  type FoodGroup,
  type PreparedLog,
} from "../answer";
import { daysOf, isWeekend, resolveRange, type DayRange, type FoodItem } from "../log";
import { FOOD_QUESTIONS, RULES, fill, num, type Metric } from "../spec";

const T = FOOD_QUESTIONS.result;

interface Side {
  group: FoodGroup;
  /** Its logged days. */
  logged: string[];
}

/**
 * One number between weekdays and the weekend, or between two periods: of
 * everything eaten, or of some foods. «¿Como más el fin de semana?»
 */
export const compare: Analysis = {
  id: "compare",
  answer(prepared, request) {
    const metric = request.metric ?? "calories";
    const withFoods = request.foods.length > 0;
    if (metric === "times" && !withFoods) return null;
    const today = prepared.log.today;

    let sides: Side[];
    let whole: DayRange;
    if (request.split === "weekday_weekend") {
      // 8 weeks by default: 4 weeks have only 8 weekend days, too few once some go unlogged.
      const range = resolveRange(request.from, request.to, today, RULES.weekendDefaultDays);
      if (range === null) return null;
      whole = range;
      const days = daysOf(range);
      sides = [
        side(prepared, "weekdays", range, days.filter((date) => !isWeekend(date))),
        side(prepared, "weekend", range, days.filter(isWeekend)),
      ];
    } else {
      if (request.from === null || request.to === null || request.from2 === null || request.to2 === null) return null;
      const first = resolveRange(request.from, request.to, today, RULES.defaultDays);
      const second = resolveRange(request.from2, request.to2, today, RULES.defaultDays);
      if (first === null || second === null) return null;
      whole = { from: first.from < second.from ? first.from : second.from, to: first.to > second.to ? first.to : second.to };
      sides = [side(prepared, "first", first, daysOf(first)), side(prepared, "second", second, daysOf(second))];
    }

    const counted = withFoods ? matchedItems(prepared, request, whole).filter((item) => sides.some((s) => s.logged.includes(item.date))) : [];
    if (withFoods && counted.length === 0) return noMatch(prepared, request, whole);
    const groups = sides.map((s) => s.group);
    const short = notEnough("compare", groups, counted);
    if (short) return short;

    const found = details("compare", groups, counted);
    const lines = [...groups.map(periodLine), ...countedLines(found)];
    const [a, b] = sides;
    const [first, second] = [T.labels[a.group.label], T.labels[b.group.label]];
    if (metric === "times") {
      const shares = sides.map((s) => {
        const eaten = new Set(counted.filter((item) => s.logged.includes(item.date)).map((item) => item.date)).size;
        return { eaten, share: Math.round((eaten / s.logged.length) * 100) };
      });
      lines.push(T.compareTimesTitle);
      sides.forEach((s, index) =>
        lines.push(
          fill(T.compareTimes, {
            label: T.labels[s.group.label],
            eatenDays: shares[index].eaten,
            logged: s.logged.length,
            share: num(shares[index].share),
          }),
        ),
      );
      lines.push(fill(T.compareTimesDifference, { first, second, difference: num(shares[1].share - shares[0].share) }));
      return result(lines, found);
    }

    const { name, unit } = T.metrics[metric];
    const perDay = sides.map((s) => perLoggedDay(prepared, s, metric, withFoods ? counted : null));
    lines.push(fill(withFoods ? T.compareFoodsTitle : T.compareTitle, { metric: capitalized(name) }));
    sides.forEach((s, index) =>
      lines.push(fill(T.compareValue, { label: T.labels[s.group.label], value: metricValue(perDay[index], metric), unit })),
    );
    const rounded = perDay.map((value) => (metric === "calories" ? Math.round(value) : round1(value)));
    const difference = metric === "calories" ? rounded[1] - rounded[0] : round1(rounded[1] - rounded[0]);
    lines.push(
      rounded[0] > 0
        ? fill(T.compareDifference, { first, second, difference: num(difference), unit, percent: num(Math.round((difference / rounded[0]) * 100)) })
        : fill(T.compareDifferenceFromZero, { first, second, difference: num(difference), unit }),
    );
    return result(lines, found);
  },
};

function side(prepared: PreparedLog, label: FoodGroup["label"], range: DayRange, dates: string[]): Side {
  return { group: groupOf(prepared, label, range, dates), logged: loggedDaysOf(prepared, dates) };
}

/** The metric per logged day of a side: of everything, or of the [foods] counted. */
function perLoggedDay(
  prepared: PreparedLog,
  s: Side,
  metric: Exclude<Metric, "times">,
  foods: FoodItem[] | null,
): number {
  let total = 0;
  if (foods === null) for (const date of s.logged) total += metricOf(prepared.days.get(date)!, metric);
  else for (const item of foods) if (s.logged.includes(item.date)) total += metricOf(item, metric);
  return total / s.logged.length;
}

function capitalized(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}
