import { details, groupOf, macrosText, result, type Analysis } from "../answer";
import { daysOf, resolveRange, weekdayName } from "../log";
import { FOOD_QUESTIONS, RULES, fill } from "../spec";

const T = FOOD_QUESTIONS.result;

/** What was eaten on a day or a few days: «¿Qué comí ayer?». No minimum of days. */
export const dayLog: Analysis = {
  id: "day_log",
  answer(prepared, request) {
    const asked = resolveRange(request.from, request.to, prepared.log.today, 1);
    if (asked === null) return null;
    const all = daysOf(asked);
    const shown = all.slice(-RULES.dayLogMaxDays);
    const range = { from: shown[0], to: shown[shown.length - 1] };
    const lines: string[] = [];
    if (shown.length < all.length) lines.push(fill(T.dayLogCut, { shown: shown.length, days: all.length }));
    for (const date of shown) {
      const totals = prepared.days.get(date);
      const weekday = weekdayName(date);
      if (!totals || totals.calories <= 0) {
        lines.push(fill(T.dayEmpty, { date, weekday }));
        continue;
      }
      lines.push(fill(T.day, { date, weekday, macros: macrosText(totals) }));
      for (const meal of prepared.log.meals.filter((candidate) => candidate.logDate === date)) {
        const macros = macrosText({
          calories: meal.resolvedCalories,
          protein: meal.resolvedProtein,
          carbs: meal.resolvedCarbs,
          fat: meal.resolvedFat,
        });
        const items =
          meal.entryMode === "total_only"
            ? []
            : meal.ingredients
                .filter((item) => item.name.trim() !== "")
                .map((item) => (item.quantity?.trim() ? `${item.name.trim()} (${item.quantity.trim()})` : item.name.trim()));
        lines.push(
          items.length > 0
            ? fill(T.mealItems, { title: meal.title, macros, items: items.join(", ") })
            : fill(T.meal, { title: meal.title, macros }),
        );
      }
    }
    // A list, not an average: no minimum of days and no «few days» hint.
    return result(lines, { ...details("day_log", [groupOf(prepared, "period", range)]), few: false });
  },
};
