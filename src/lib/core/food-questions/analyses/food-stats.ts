import { round1 } from "../../nutrition";
import {
  details,
  groupOf,
  matchedItems,
  noMatch,
  notEnough,
  periodLine,
  countedLines,
  result,
  type Analysis,
} from "../answer";
import { resolveRange, type AmountUnit } from "../log";
import { FOOD_QUESTIONS, RULES, fill, num } from "../spec";

const T = FOOD_QUESTIONS.result;
const UNITS: AmountUnit[] = ["g", "ml"];

/** How often and how much of a food, and what it brings: «¿Cuánta leche chocolatada tomo?». */
export const foodStats: Analysis = {
  id: "food_stats",
  answer(prepared, request) {
    const range = resolveRange(request.from, request.to, prepared.log.today, RULES.defaultDays);
    if (range === null) return null;
    const items = matchedItems(prepared, request, range);
    if (items.length === 0) return noMatch(prepared, request, range);
    const period = groupOf(prepared, "period", range);
    const short = notEnough("food_stats", [period], items);
    if (short) return short;

    const logged = period.loggedDays;
    const found = details("food_stats", [period], items);
    const eatenDays = new Set(items.map((item) => item.date)).size;
    const lines = [
      periodLine(period),
      ...countedLines(found),
      fill(T.foodDays, { eatenDays, logged, perWeek: num(round1((eatenDays / logged) * 7)), times: items.length }),
    ];
    for (const unit of UNITS) {
      const withUnit = items.filter((item) => item.amount?.unit === unit);
      if (withUnit.length === 0) continue;
      const total = withUnit.reduce((sum, item) => sum + item.amount!.value, 0);
      const days = new Set(withUnit.map((item) => item.date)).size;
      lines.push(
        fill(T.amount, {
          unit,
          total: num(Math.round(total)),
          entries: withUnit.length,
          perEatenDay: num(Math.round(total / days)),
          perLoggedDay: num(Math.round(total / logged)),
        }),
      );
    }
    if (found.withoutAmount > 0) lines.push(fill(T.withoutAmount, { n: found.withoutAmount }));

    const sum = (pick: (item: (typeof items)[number]) => number) => items.reduce((total, item) => total + pick(item), 0);
    let allCalories = 0;
    for (const [date, totals] of prepared.days) if (date >= range.from && date <= range.to) allCalories += totals.calories;
    const calories = sum((item) => item.calories);
    lines.push(
      fill(T.foodNutrition, {
        calories: num(Math.round(calories / logged)),
        share: num(allCalories > 0 ? Math.round((calories / allCalories) * 100) : 0),
        protein: num(round1(sum((item) => item.protein) / logged)),
        carbs: num(round1(sum((item) => item.carbs) / logged)),
        fat: num(round1(sum((item) => item.fat) / logged)),
      }),
    );
    return result(lines, found);
  },
};
