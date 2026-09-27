import { describe, expect, it } from "vitest";
import { macroAverages } from "../../src/lib/core/progress";

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
