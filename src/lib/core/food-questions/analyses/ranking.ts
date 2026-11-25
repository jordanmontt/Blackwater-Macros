import {
  details,
  groupOf,
  metricOf,
  metricValue,
  notEnough,
  periodLine,
  result,
  type Analysis,
} from "../answer";
import { compareText, displayNames, inRange, resolveRange } from "../log";
import { FOOD_QUESTIONS, RULES, fill, num } from "../spec";

const T = FOOD_QUESTIONS.result;

/** The top foods by calories, a macro or how often: «¿Qué me da más proteína?». */
export const ranking: Analysis = {
  id: "ranking",
  answer(prepared, request) {
    const metric = request.metric ?? "calories";
    const range = resolveRange(request.from, request.to, prepared.log.today, RULES.defaultDays);
    if (range === null) return null;
    const period = groupOf(prepared, "period", range);
    const short = notEnough("ranking", [period]);
    if (short) return short;

    const items = prepared.items.filter((item) => inRange(item.date, range));
    const names = displayNames(items);
    const byFood = new Map<string, { value: number; days: Set<string> }>();
    for (const item of items) {
      const entry = byFood.get(item.key) ?? { value: 0, days: new Set<string>() };
      entry.value += metric === "times" ? 0 : metricOf(item, metric);
      entry.days.add(item.date);
      byFood.set(item.key, entry);
    }
    const rows = [...byFood]
      .map(([key, entry]) => ({ name: names.get(key)!, value: entry.value, days: entry.days.size }))
      .filter((row) => metric === "times" || row.value > 0)
      .sort((a, b) => (metric === "times" ? b.days - a.days : b.value - a.value) || compareText(a.name, b.name))
      .slice(0, RULES.rankingSize);

    const lines = [periodLine(period)];
    if (metric === "times") {
      lines.push(T.rankingTimesTitle);
      rows.forEach((row, index) =>
        lines.push(fill(T.rankingTimesLine, { rank: index + 1, name: row.name, days: row.days, logged: period.loggedDays })),
      );
    } else {
      let total = 0;
      for (const [date, totals] of prepared.days) if (inRange(date, range)) total += metricOf(totals, metric);
      const { name, unit } = T.metrics[metric];
      lines.push(fill(T.rankingTitle, { metric: name, total: metricValue(total, metric), unit }));
      rows.forEach((row, index) =>
        lines.push(
          fill(T.rankingLine, {
            rank: index + 1,
            name: row.name,
            value: metricValue(row.value, metric),
            unit,
            share: num(total > 0 ? Math.round((row.value / total) * 100) : 0),
            days: row.days,
          }),
        ),
      );
    }
    return result(lines, details("ranking", [period]));
  },
};
