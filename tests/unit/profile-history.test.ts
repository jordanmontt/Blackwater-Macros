import { describe, expect, it } from "vitest";
import {
  averageTargets,
  bodyFatOn,
  profileOn,
  targetsOn,
  weightOn,
  withProfileChange,
  type DatedWeight,
  type ProfilePeriod,
} from "../../src/lib/core/profile-history";
import type { CalorieProfile } from "../../src/lib/core/types";

const BEFORE: CalorieProfile = {
  gender: "female",
  birthYear: 1995,
  heightCm: 165,
  gymDaysPerWeek: 2,
  gymSessionMinutes: 60,
  walkingMinutesPerDay: 20,
  calorieGoal: "cut",
};
const AFTER: CalorieProfile = { ...BEFORE, gymDaysPerWeek: 4, walkingMinutesPerDay: 60 };
const EMPTY: CalorieProfile = {
  gender: null,
  birthYear: null,
  heightCm: null,
  gymDaysPerWeek: null,
  gymSessionMinutes: null,
  walkingMinutesPerDay: null,
  calorieGoal: null,
};
const HISTORY: ProfilePeriod[] = [{ ...BEFORE, until: "2026-10-14" }];
const WEIGHTS: DatedWeight[] = [
  { date: "2026-10-01", weightKg: 62, bodyFatPct: 28 },
  { date: "2026-10-10", weightKg: 61, bodyFatPct: null },
  { date: "2026-10-15", weightKg: 60.5, bodyFatPct: null },
];

describe("withProfileChange", () => {
  it("keeps the old profile for the days up to yesterday", () => {
    expect(withProfileChange(BEFORE, [], AFTER, "2026-10-15")).toEqual([{ ...BEFORE, until: "2026-10-14" }]);
  });

  it("adds nothing when nothing changed", () => {
    expect(withProfileChange(BEFORE, [], { ...BEFORE }, "2026-10-15")).toEqual([]);
  });

  it("adds nothing when the old profile gave no target (the new one covers the earlier days)", () => {
    expect(withProfileChange(EMPTY, [], AFTER, "2026-10-15")).toEqual([]);
    expect(withProfileChange({ ...BEFORE, calorieGoal: null }, [], AFTER, "2026-10-15")).toEqual([]);
  });

  it("keeps the first profile of the day when it is edited again the same day", () => {
    const third = { ...AFTER, calorieGoal: "maintain" as const };
    expect(withProfileChange(AFTER, HISTORY, third, "2026-10-15")).toEqual(HISTORY);
  });

  it("adds a new period on a later day", () => {
    expect(withProfileChange(AFTER, HISTORY, BEFORE, "2026-10-20")).toEqual([
      ...HISTORY,
      { ...AFTER, until: "2026-10-19" },
    ]);
  });
});

describe("profileOn", () => {
  it("is the current profile without history", () => {
    expect(profileOn(AFTER, [], "2026-01-01")).toEqual(AFTER);
  });

  it("is the earliest period still running that day, else the current profile", () => {
    const history: ProfilePeriod[] = [
      { ...AFTER, until: "2026-10-19" },
      { ...BEFORE, until: "2026-10-14" },
    ];
    const current = { ...BEFORE, calorieGoal: "maintain" as const };
    expect(profileOn(current, history, "2026-10-01")).toEqual(BEFORE);
    expect(profileOn(current, history, "2026-10-14")).toEqual(BEFORE);
    expect(profileOn(current, history, "2026-10-15")).toEqual(AFTER);
    expect(profileOn(current, history, "2026-10-19")).toEqual(AFTER);
    expect(profileOn(current, history, "2026-10-20")).toEqual(current);
  });
});

describe("weightOn and bodyFatOn", () => {
  it("is the last weigh-in on or before the day, else the first one", () => {
    expect(weightOn(WEIGHTS, "2026-09-20")?.weightKg).toBe(62);
    expect(weightOn(WEIGHTS, "2026-10-10")?.weightKg).toBe(61);
    expect(weightOn(WEIGHTS, "2026-10-12")?.weightKg).toBe(61);
    expect(weightOn(WEIGHTS, "2026-10-30")?.weightKg).toBe(60.5);
    expect(weightOn([], "2026-10-12")).toBeNull();
  });

  it("is the last body fat on or before the day, else the first one", () => {
    const weights = [...WEIGHTS, { date: "2026-10-16", weightKg: 60, bodyFatPct: 27 }];
    expect(bodyFatOn(weights, "2026-09-20")).toBe(28);
    expect(bodyFatOn(weights, "2026-10-15")).toBe(28);
    expect(bodyFatOn(weights, "2026-10-16")).toBe(27);
    expect(bodyFatOn(WEIGHTS.slice(1), "2026-10-15")).toBeNull();
  });
});

describe("targetsOn", () => {
  it("uses the profile and the weight of that day", () => {
    // Before the change: 2 gym days, 20 min walking, 61 kg.
    expect(targetsOn(AFTER, HISTORY, WEIGHTS, "2026-10-12")).toEqual({
      calorie: {
        bmr: 1325,
        activityFactor: 1.46,
        tdee: 1935,
        expenditure: 1935,
        dataShare: 0,
        target: 1535,
        targetMin: 1435,
        targetMax: 1635,
        goal: "cut",
      },
      protein: {
        goal: "cut",
        basis: "leanMass",
        basisKg: 43.9,
        range: { min: 101, max: 136 },
        perKg: { min: 2.3, max: 3.1 },
        target: 119,
      },
    });
    // From the change on: 4 gym days, 60 min walking, 60.5 kg (+111 kcal).
    expect(targetsOn(AFTER, HISTORY, WEIGHTS, "2026-10-15").calorie).toEqual({
      bmr: 1320,
      activityFactor: 1.55,
      tdee: 2046,
      expenditure: 2046,
      dataShare: 0,
      target: 1646,
      targetMin: 1546,
      targetMax: 1746,
      goal: "cut",
    });
  });

  it("uses the expenditure measured on that day's week", () => {
    // Measured 2300 ± 177 from the week of 2026-10-12; nothing before.
    const measuredOn = (day: string) =>
      day >= "2026-10-12"
        ? { tdee: 2300, margin: 177, avgIntake: 0, weightChangePerWeek: 0, loggedDays: 28, weighIns: 28, windowDays: 28 }
        : null;
    expect(targetsOn(AFTER, HISTORY, WEIGHTS, "2026-10-15", measuredOn).calorie).toMatchObject({
      tdee: 2046,
      expenditure: 2259,
      dataShare: 0.84,
      targetMin: 1759,
      targetMax: 1959,
    });
    expect(targetsOn(AFTER, HISTORY, WEIGHTS, "2026-10-05", measuredOn).calorie).toMatchObject({
      tdee: 1950,
      expenditure: 1950,
      dataShare: 0,
    });
    expect(averageTargets(AFTER, HISTORY, WEIGHTS, ["2026-10-05", "2026-10-15"], measuredOn).calorie).toEqual({
      min: 1605,
      max: 1805,
    });
  });

  it("maintenance protein follows that day's training: 1.4–2.0 g/kg without gym, 1.6–2.2 with it", () => {
    const untrained = { ...BEFORE, gymDaysPerWeek: 0, calorieGoal: "maintain" as const };
    const trained = { ...untrained, gymDaysPerWeek: 3 };
    const history: ProfilePeriod[] = [{ ...untrained, until: "2026-10-14" }];
    expect(targetsOn(trained, history, WEIGHTS, "2026-10-12").protein?.range).toEqual({ min: 85, max: 122 });
    expect(targetsOn(trained, history, WEIGHTS, "2026-10-15").protein?.range).toEqual({ min: 97, max: 133 });
  });

  it("uses the first weigh-in before there was any", () => {
    expect(targetsOn(AFTER, HISTORY, WEIGHTS, "2026-09-20").calorie?.tdee).toBe(1950);
  });

  it("has no targets without a weigh-in, and no protein without a goal", () => {
    expect(targetsOn(AFTER, HISTORY, [], "2026-10-15")).toEqual({ calorie: null, protein: null });
    expect(targetsOn({ ...AFTER, calorieGoal: null }, [], WEIGHTS, "2026-10-15")).toEqual({
      calorie: null,
      protein: null,
    });
  });
});

describe("averageTargets", () => {
  it("averages each day's own range", () => {
    expect(averageTargets(AFTER, HISTORY, WEIGHTS, ["2026-10-12", "2026-10-15"])).toEqual({
      calorie: { min: 1491, max: 1691 },
      protein: { min: 101, max: 136 },
    });
  });

  it("is null without days or targets", () => {
    expect(averageTargets(AFTER, HISTORY, WEIGHTS, [])).toEqual({ calorie: null, protein: null });
    expect(averageTargets(AFTER, HISTORY, [], ["2026-10-15"])).toEqual({ calorie: null, protein: null });
  });
});
