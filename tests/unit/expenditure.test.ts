import { describe, expect, it } from "vitest";
import { addDaysToKey } from "../../src/lib/core/dates";
import {
  approximateGap,
  checkExpenditure,
  dailyMeans,
  estimateExpenditure,
  fitWeightTrend,
  measureExpenditure,
  onlyWeighInsMissing,
  showsGapNotice,
} from "../../src/lib/core/expenditure";
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

describe("dailyMeans", () => {
  it("one point per day, the mean of that day's weigh-ins, sorted by date", () => {
    expect(
      dailyMeans([
        { date: "2026-02-03", value: 79 },
        { date: "2026-02-01", value: 80 },
        { date: "2026-02-01", value: 81 },
      ]),
    ).toEqual([
      { date: "2026-02-01", value: 80.5 },
      { date: "2026-02-03", value: 79 },
    ]);
  });

  it("morning and evening weigh-ins do not count twice in the expenditure or its margin", () => {
    const single = estimateExpenditure(meals(28, 2500), weighIns(80, -0.5), TODAY)!;
    const twice = weighIns(80, -0.5).flatMap((p) => [p, { date: p.date, value: p.value + 1 }]);
    const doubled = estimateExpenditure(meals(28, 2500), twice, TODAY)!;
    expect(doubled.tdee).toBe(single.tdee);
    expect(doubled.margin).toBe(single.margin);
    expect(doubled.weighIns).toBe(10);
  });
});

describe("fitWeightTrend", () => {
  it("fits the line and floors the scatter at 0.5 kg", () => {
    const trend = fitWeightTrend([
      { date: "2026-02-01", value: 80 },
      { date: "2026-02-08", value: 79.5 },
      { date: "2026-02-15", value: 79 },
    ])!;
    expect(trend.slopePerDay).toBeCloseTo(-0.5 / 7, 10);
    expect(trend.sigma).toBe(0.5);
    expect(trend.n).toBe(3);
    expect(trend.meanX).toBe(7);
    expect(trend.meanY).toBeCloseTo(79.5, 10);
    expect(trend.sxx).toBe(98);
    expect(trend.slopeError).toBeCloseTo(0.5 / Math.sqrt(98), 10);
    expect(trend.origin).toBe("2026-02-01");
  });

  it("needs 3 points on more than one day", () => {
    expect(fitWeightTrend([{ date: "2026-02-01", value: 80 }, { date: "2026-02-08", value: 79 }])).toBeNull();
    const sameDay = [80, 80.5, 79.5].map((value) => ({ date: "2026-02-01", value }));
    expect(fitWeightTrend(sameDay)).toBeNull();
  });
});

// Profile: BMR 1600 × activity factor 1.5 = 2400 kcal/day → normal range 2160–2640 (± 10 %).
const FORMULA = { bmr: 1600, activityFactor: 1.5, tdee: 2400 };

describe("measureExpenditure", () => {
  it("returns the counts even when they are not enough, so the app can say what is missing", () => {
    const data = measureExpenditure(meals(15, 2400), weighIns(80, -0.5, 7), TODAY);
    expect(data.estimate).toBeNull();
    expect(data.loggedDays).toBe(15);
    expect(data.weighInDays).toBe(4);
    expect(data.weightSpanDays).toBe(21);
    expect(data.margin).toBe(482);
  });

  it("no margin without a trend (fewer than 3 weigh-in days)", () => {
    const data = measureExpenditure(meals(28, 2400), [{ date: daysAgo(3), value: 80 }], TODAY);
    expect(data.weighInDays).toBe(1);
    expect(data.weightSpanDays).toBe(0);
    expect(data.margin).toBeNull();
  });
});

describe("checkExpenditure", () => {
  it("measured inside formula ± 10 %: it matches, even with a 350 kcal gap", () => {
    // 2750 ± 177: the low end (2573) is inside 2160–2640, so the formula may be right.
    const check = checkExpenditure(meals(35, 2750), weighIns(80, 0, 1, 35), FORMULA, TODAY);
    expect(check).toMatchObject({
      status: "measured",
      verdict: "matches",
      confirmed: true,
      formulaTdee: 2400,
      normalMin: 2160,
      normalMax: 2640,
      gap: 350,
    });
  });

  it("burning clearly more: the whole 95 % range is above formula + 10 %", () => {
    // Eating 2400 and losing 0.7 kg/week: 2400 + 0.1 × 7700 = 3170 ± 177; 2993 > 2640.
    const check = checkExpenditure(meals(35, 2400), weighIns(80, -0.7, 1, 35), FORMULA, TODAY);
    expect(check).toMatchObject({ status: "measured", verdict: "higher", confirmed: true, gap: 770 });
    if (check.status !== "measured") throw new Error();
    expect(check.estimate.tdee).toBe(3170);
    expect(check.estimate.margin).toBe(177);
    // 3170 / 1600: the activity factor these data correspond to, against 1.5 in the profile.
    expect(check.impliedActivityFactor).toBe(1.98);
  });

  it("burning clearly less: the whole 95 % range is below formula − 10 %", () => {
    // Stable weight on 1800: 1800 ± 177; 1977 < 2160.
    const check = checkExpenditure(meals(35, 1800), weighIns(80, 0, 1, 35), FORMULA, TODAY);
    expect(check).toMatchObject({ status: "measured", verdict: "lower", confirmed: true, gap: -600 });
  });

  it("a gap is only confirmed when the window that ended a week earlier says the same", () => {
    // Weigh-ins only in the last 20 days: today's window shows the gap (3170 ± 293),
    // the window a week earlier has 13 weigh-in days spanning 12 days, not enough.
    const check = checkExpenditure(meals(28, 2400), weighIns(80, -0.7, 1, 20), FORMULA, TODAY);
    expect(check).toMatchObject({ status: "measured", verdict: "higher", confirmed: false });
  });

  it("not enough data: says what is missing, meals first", () => {
    const missing = (intake: DataPoint[], weights: DataPoint[]) => {
      const check = checkExpenditure(intake, weights, FORMULA, TODAY);
      return check.status === "notEnoughData" ? check.missing : null;
    };
    expect(missing(meals(15, 2400), weighIns(80, 0, 1))).toEqual(["meals"]);
    const three = [daysAgo(28), daysAgo(20), daysAgo(2)].map((date) => ({ date, value: 80 }));
    expect(missing(meals(28, 2400), three)).toEqual(["weighIns"]);
    expect(missing(meals(28, 2400), weighIns(80, 0, 1, 13))).toEqual(["weightSpan"]);
    expect(missing(meals(28, 2400), weighIns(80, 0, 7))).toEqual(["margin"]);
    expect(missing(meals(10, 2400), weighIns(80, 0, 7))).toEqual(["meals", "margin"]);
  });
});

describe("Today card notices", () => {
  const higher = checkExpenditure(meals(35, 2400), weighIns(80, -0.7, 1, 35), FORMULA, TODAY);

  it("the gap notice shows only when confirmed, and «Not now» holds for one window of the same verdict", () => {
    expect(showsGapNotice(higher, null, TODAY)).toBe(true);
    expect(showsGapNotice(higher, { verdict: "higher", date: daysAgo(10) }, TODAY)).toBe(false);
    expect(showsGapNotice(higher, { verdict: "higher", date: daysAgo(28) }, TODAY)).toBe(true);
    expect(showsGapNotice(higher, { verdict: "lower", date: daysAgo(1) }, TODAY)).toBe(true);

    const unconfirmed = checkExpenditure(meals(28, 2400), weighIns(80, -0.7, 1, 20), FORMULA, TODAY);
    expect(showsGapNotice(unconfirmed, null, TODAY)).toBe(false);
    const matches = checkExpenditure(meals(35, 2400), weighIns(80, 0, 1, 35), FORMULA, TODAY);
    expect(showsGapNotice(matches, null, TODAY)).toBe(false);
    expect(showsGapNotice(null, null, TODAY)).toBe(false);
  });

  it("the weigh-in hint shows when meals are logged well and only the weigh-ins are missing", () => {
    expect(onlyWeighInsMissing(checkExpenditure(meals(28, 2400), weighIns(80, 0, 7), FORMULA, TODAY))).toBe(true);
    expect(onlyWeighInsMissing(checkExpenditure(meals(10, 2400), weighIns(80, 0, 7), FORMULA, TODAY))).toBe(false);
    expect(onlyWeighInsMissing(higher)).toBe(false);
    expect(onlyWeighInsMissing(null)).toBe(false);
  });

  it("the gap is said rounded to 50 kcal", () => {
    expect(approximateGap(770)).toBe(750);
    expect(approximateGap(-523)).toBe(500);
    expect(approximateGap(530)).toBe(550);
  });
});
