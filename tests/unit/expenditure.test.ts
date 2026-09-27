import { describe, expect, it } from "vitest";
import { addDaysToKey } from "../../src/lib/core/dates";
import { estimateExpenditure } from "../../src/lib/core/expenditure";
import type { DataPoint } from "../../src/lib/core/stats";

const TODAY = "2026-03-01";
/** The date `n` days before TODAY (1 = yesterday, 28 = first day of the window). */
const daysAgo = (n: number) => addDaysToKey(TODAY, -n);

/** One meal per day for `days` days before today, all with the same kcal. */
function meals(days: number, kcal: number): DataPoint[] {
  return Array.from({ length: days }, (_, i) => ({ date: daysAgo(i + 1), value: kcal }));
}

/** A weigh-in every `every` days from `first` days ago, changing linearly. */
function weighIns(startKg: number, kgPerWeek: number, every = 3, first = 28): DataPoint[] {
  const points: DataPoint[] = [];
  for (let n = first; n >= 1; n -= every) {
    points.push({ date: daysAgo(n), value: startKg + (kgPerWeek / 7) * (first - n) });
  }
  return points;
}

describe("estimateExpenditure", () => {
  it("stable weight: expenditure equals intake", () => {
    const est = estimateExpenditure(meals(28, 2500), weighIns(80, 0), TODAY);
    expect(est).not.toBeNull();
    expect(est!.tdee).toBe(2500);
    expect(est!.avgIntake).toBe(2500);
    expect(est!.weightChangePerWeek).toBe(0);
    expect(est!.loggedDays).toBe(28);
    expect(est!.weighIns).toBe(10);
    expect(est!.windowDays).toBe(28);
    // Perfect line → the 0.5 kg noise floor: 1.96 × 0.5 / √742.5 × 7700 = 277
    expect(est!.margin).toBe(277);
  });

  it("losing 0.5 kg/week on 2500 kcal → 2500 + 0.5/7 × 7700 = 3050", () => {
    const est = estimateExpenditure(meals(28, 2500), weighIns(80, -0.5), TODAY);
    expect(est!.tdee).toBe(3050);
    expect(est!.weightChangePerWeek).toBe(-0.5);
  });

  it("gaining 0.25 kg/week on 3000 kcal → 3000 − 275 = 2725", () => {
    const est = estimateExpenditure(meals(28, 3000), weighIns(70, 0.25), TODAY);
    expect(est!.tdee).toBe(2725);
  });

  it("unlogged days are left out, not counted as 0 kcal", () => {
    const est = estimateExpenditure(meals(22, 2200), weighIns(80, 0), TODAY);
    expect(est!.avgIntake).toBe(2200);
    expect(est!.loggedDays).toBe(22);
  });

  it("meals on the same day are summed", () => {
    const intake = meals(21, 1000).flatMap((p) => [p, { ...p, value: 1400 }]);
    expect(estimateExpenditure(intake, weighIns(80, 0), TODAY)!.avgIntake).toBe(2400);
  });

  it("needs 21 logged days; days whose meals add up to 0 kcal count as unlogged", () => {
    const intake = [...meals(21, 2000), ...meals(28, 0).slice(21)];
    expect(estimateExpenditure(intake, weighIns(80, 0), TODAY)!.loggedDays).toBe(21);
    expect(estimateExpenditure(meals(20, 2000), weighIns(80, 0), TODAY)).toBeNull();
  });

  it("ignores today and anything before the 28-day window", () => {
    const intake = [
      ...meals(28, 2000),
      { date: TODAY, value: 9000 },
      { date: daysAgo(29), value: 9000 },
    ];
    const weights = [...weighIns(80, 0), { date: TODAY, value: 60 }, { date: daysAgo(40), value: 100 }];
    const est = estimateExpenditure(intake, weights, TODAY);
    expect(est!.avgIntake).toBe(2000);
    expect(est!.tdee).toBe(2000);
  });

  it("needs at least 4 weigh-in days", () => {
    const three = [daysAgo(28), daysAgo(20), daysAgo(2)].map((date) => ({ date, value: 80 }));
    expect(estimateExpenditure(meals(28, 2500), three, TODAY)).toBeNull();
    // Two weigh-ins on the same day count once.
    expect(estimateExpenditure(meals(28, 2500), [...three, { date: daysAgo(2), value: 80.2 }], TODAY)).toBeNull();
  });

  it("needs the weigh-ins to span at least 14 days", () => {
    const short = weighIns(80, 0, 1, 13);
    expect(estimateExpenditure(meals(28, 2500), short, TODAY)).toBeNull();
    const enough = weighIns(80, 0, 1, 21);
    expect(estimateExpenditure(meals(28, 2500), enough, TODAY)).not.toBeNull();
  });

  it("weekly weigh-ins are too uncertain to show (margin over ±300 kcal)", () => {
    // Days 28, 21, 14, 7: 1.96 × 0.5 / √245 × 7700 = 482
    expect(estimateExpenditure(meals(28, 2500), weighIns(80, -0.5, 7), TODAY)).toBeNull();
  });

  it("daily weigh-ins narrow the margin", () => {
    // Σ(x − x̄)² = 28 × (28² − 1) / 12 = 1827 → 1.96 × 0.5 / √1827 × 7700 = 177
    expect(estimateExpenditure(meals(28, 2500), weighIns(80, -0.5, 1), TODAY)!.margin).toBe(177);
  });

  it("noisy weigh-ins widen the margin beyond the floor", () => {
    // Every 2 days alternating ±1.2 kg around a stable 80 kg.
    const noisy = weighIns(80, 0, 2).map((p, i) => ({ ...p, value: p.value + (i % 2 === 0 ? 1.2 : -1.2) }));
    expect(estimateExpenditure(meals(28, 2500), noisy, TODAY)).toBeNull();
    const calm = weighIns(80, 0, 2).map((p, i) => ({ ...p, value: p.value + (i % 2 === 0 ? 0.3 : -0.3) }));
    expect(estimateExpenditure(meals(28, 2500), calm, TODAY)!.margin).toBe(250);
  });
});
