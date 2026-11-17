import { describe, expect, it } from "vitest";
import { checkFatShare, macroAverages } from "../../src/lib/core/progress";

describe("macroAverages", () => {
  it("averages logged days only and reports how many were logged", () => {
    expect(
      macroAverages([
        { date: "2026-03-01", calories: 2000, protein: 150, carbs: 200, fat: 60 },
        { date: "2026-03-02", calories: 0, protein: 0, carbs: 0, fat: 0 },
        { date: "2026-03-03", calories: 2200, protein: 130, carbs: 250, fat: 70 },
      ]),
    ).toEqual({
      loggedDays: 2,
      totalDays: 3,
      calories: 2100,
      protein: 140,
      carbs: 225,
      fat: 65,
      // 560 + 900 + 585 = 2045 kcal from macros
      split: { protein: 27, carbs: 44, fat: 29 },
    });
  });

  it("is null when nothing was logged", () => {
    expect(macroAverages([])).toBeNull();
    expect(macroAverages([{ date: "2026-03-01", calories: 0, protein: 0, carbs: 0, fat: 0 }])).toBeNull();
  });

  it("has no split when only calories were logged", () => {
    expect(macroAverages([{ date: "2026-03-01", calories: 2000, protein: 0, carbs: 0, fat: 0 }])!.split).toBeNull();
  });
});

describe("checkFatShare", () => {
  /** `days` logged days, all with the same macros. */
  const period = (days: number, protein: number, carbs: number, fat: number) =>
    macroAverages(
      Array.from({ length: days }, (_, i) => ({
        date: `2026-03-${String(i + 1).padStart(2, "0")}`,
        calories: protein * 4 + carbs * 4 + fat * 9,
        protein,
        carbs,
        fat,
      })),
    )!;

  it("low: below 20 % of the energy from macros", () => {
    // 540 + 1060 + 315 = 1915 kcal; 315 / 1915 = 16 %
    expect(checkFatShare(period(7, 135, 265, 35))).toEqual({ verdict: "low", share: 16, fat: 35, fatKcal: 315, macroKcal: 1915 });
  });

  it("high: above 35 %", () => {
    // 500 + 620 + 981 = 2101 kcal; 981 / 2101 = 47 %
    expect(checkFatShare(period(7, 125, 155, 109))).toMatchObject({ verdict: "high", share: 47 });
  });

  it("says nothing inside 20–35 %, judged on the rounded share the card shows", () => {
    expect(checkFatShare(period(7, 140, 225, 65))).toBeNull(); // 29 %
    // 396 / 1996 = 19.8 %, shown as 20 %: not called low.
    expect(checkFatShare(period(7, 100, 300, 44))).toBeNull();
  });

  it("needs 7 logged days and macros", () => {
    expect(checkFatShare(period(6, 135, 265, 35))).toBeNull();
    const caloriesOnly = Array.from({ length: 7 }, (_, i) => ({ date: `2026-03-0${i + 1}`, calories: 2000, protein: 0, carbs: 0, fat: 0 }));
    expect(checkFatShare(macroAverages(caloriesOnly)!)).toBeNull();
  });
});
