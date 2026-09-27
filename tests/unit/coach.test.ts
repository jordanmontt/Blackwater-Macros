import { describe, expect, it } from "vitest";
import { addDaysToKey } from "../../src/lib/core/dates";
import { buildCoachContext, buildCoachSystemPrompt, weightProjection } from "../../src/lib/core/coach";
import { COACH_INPUT, TODAY } from "./coach.fixture";

/** Daily weigh-ins from 27 days ago to today, changing `kgPerWeek`. */
function daily(kgPerWeek: number) {
  return Array.from({ length: 28 }, (_, i) => ({ date: addDaysToKey(TODAY, -(27 - i)), value: 80 + (kgPerWeek / 7) * i }));
}

describe("weightProjection", () => {
  it("stable weight stays put; the margin is the 95 % interval of the line 30 days ahead", () => {
    // n = 28, x̄ = 13.5, x = 57, Σ(x − x̄)² = 1827 → 1.96 × 0.5 × √(1/28 + 43.5²/1827) = 1.01
    expect(weightProjection(daily(0), TODAY)).toEqual({ days: 30, currentKg: 80, projectedKg: 80, marginKg: 1, ratePerWeek: 0 });
  });

  it("continues the trend: −0.5 kg/week", () => {
    expect(weightProjection(daily(-0.5), TODAY)).toEqual({
      days: 30,
      currentKg: 78.1,
      projectedKg: 75.9,
      marginKg: 1,
      ratePerWeek: -0.5,
    });
    expect(weightProjection(daily(-0.5), TODAY, 7)!.projectedKg).toBe(77.6);
  });

  it("needs 4 weigh-in days spanning 14 days within the last 4 weeks", () => {
    expect(weightProjection(daily(0).slice(0, 3), TODAY)).toBeNull();
    expect(weightProjection(daily(0).slice(-10), TODAY)).toBeNull();
    const old = daily(0).map((p) => ({ ...p, date: addDaysToKey(p.date, -30) }));
    expect(weightProjection(old, TODAY)).toBeNull();
  });
});

describe("buildCoachContext", () => {
  it("summarises profile, targets, today, the last 14 logged days and the weight trend", () => {
    expect(buildCoachContext(COACH_INPUT)).toBe(
      [
        "Today: 2026-03-01",
        "Profile: male, 36 years, 178 cm, goal: cut (lose fat).",
        "Activity: gym 3 days/week × 60 min, walking 30 min/day.",
        "Calorie target: 2089–2289 kcal/day (BMR 1738, estimated TDEE 2589, activity factor 1.49).",
        "Protein target: 147–198 g/day (2.3–3.1 g/kg of 64 kg lean mass).",
        "Measured expenditure (energy balance, last 28 days): 2750 ± 177 kcal/day.",
        "Today's meals:",
        "- Desayuno: 450 kcal, 30 g protein, 50 g carbs, 12 g fat. Items: Avena (80 g), Leche (200 ml).",
        "Today so far: 450 kcal, 30 g protein, 50 g carbs, 12 g fat.",
        "Calories: 1639–1839 kcal left to reach the target range.",
        "Protein: 117–168 g left to reach the target range.",
        "Last 14 days (logged days only, oldest first):",
        "- 2026-02-26: 1900 kcal, 120 g protein, 180 g carbs, 60 g fat",
        "- 2026-02-28: 2100 kcal, 150 g protein, 200 g carbs, 70.3 g fat",
        "Average over 2 of 14 days logged: 2000 kcal, 135 g protein, 190 g carbs, 65.1 g fat (protein 29%, carbs 40%, fat 31% of calories).",
        "Weight: latest 78.1 kg on 2026-03-01.",
        "Body fat: 20% on 2026-02-02.",
        "Weight in the last 60 days: 28 weigh-ins, the first 80 kg on 2026-02-02.",
        "Weight trend (last 28 days): -0.5 kg/week, now 78.1 kg. Projection computed by the app if this trend continues: 75.9 ± 1 kg in 30 days.",
      ].join("\n"),
    );
  });

  it("says what is missing with an empty profile and no data", () => {
    const empty = {
      gender: null,
      birthYear: null,
      heightCm: null,
      gymDaysPerWeek: null,
      gymSessionMinutes: null,
      walkingMinutesPerDay: null,
      calorieGoal: null,
    };
    expect(
      buildCoachContext({ today: TODAY, profile: empty, calorie: null, protein: null, expenditure: null, meals: [], weights: [] }),
    ).toBe(
      [
        "Today: 2026-03-01",
        "Profile: sex unknown, age unknown, height unknown, goal unknown.",
        "Calorie target: not available (profile incomplete).",
        "Measured expenditure: not enough data yet (needs 4 weeks of logged meals and frequent weigh-ins).",
        "Today's meals: none logged yet.",
        "Today so far: 0 kcal, 0 g protein, 0 g carbs, 0 g fat.",
        "Last 14 days: no meals logged.",
        "Weight: no weigh-ins logged.",
      ].join("\n"),
    );
  });

  it("says when today is within or over the target range", () => {
    const within = { ...COACH_INPUT, meals: [{ ...COACH_INPUT.meals[0], resolvedCalories: 2150, resolvedProtein: 220 }] };
    const text = buildCoachContext(within);
    expect(text).toContain("Calories: within the target range (up to 139 kcal more).");
    expect(text).toContain("Protein: over the target range by 22 g.");
  });
});

describe("buildCoachSystemPrompt", () => {
  it("sets the language and includes the data, or says it is not shared", () => {
    const withData = buildCoachSystemPrompt("Spanish", "Today: 2026-03-01");
    expect(withData).toContain("Always answer in Spanish.");
    expect(withData.endsWith("USER DATA\nToday: 2026-03-01")).toBe(true);
    expect(buildCoachSystemPrompt("English", null).endsWith("The user chose not to share their data with the coach.")).toBe(true);
  });
});
