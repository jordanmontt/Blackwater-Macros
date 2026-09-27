import { addDaysToKey, daysBetweenKeys } from "./dates";
import {
  EXPENDITURE_WINDOW_DAYS,
  MIN_WEIGHT_SPAN_DAYS,
  MIN_WEIGH_INS,
  Z_95,
  fitWeightTrend,
} from "./expenditure";
import { round1, round2 } from "./nutrition";
import { macroAverages } from "./progress";
import type { DataPoint } from "./stats";
import type {
  CalorieProfile,
  CalorieRecommendation,
  ExpenditureEstimate,
  IngredientInput,
  ProteinRecommendation,
} from "./types";

export interface WeightProjection {
  days: number;
  /** Trend value today, kg. */
  currentKg: number;
  projectedKg: number;
  /** ± kg, 95 % interval of the trend line at the projected date. */
  marginKg: number;
  ratePerWeek: number;
}

/**
 * Where the weight trend of the last 4 weeks (today included) leads in `days`
 * days if it continues. Same data rules as the measured expenditure (≥ 4
 * weigh-in days spanning ≥ 14 days). The margin is the 95 % interval of the
 * fitted line at that date, σ·√(1/n + (x − x̄)²/Σ(x − x̄)²), so it widens the
 * further ahead it looks. It says nothing about changes in diet.
 */
export function weightProjection(weights: DataPoint[], today: string, days = 30): WeightProjection | null {
  const from = addDaysToKey(today, -(EXPENDITURE_WINDOW_DAYS - 1));
  const recent = weights
    .filter((point) => point.date >= from && point.date <= today)
    .sort((a, b) => a.date.localeCompare(b.date));
  if (new Set(recent.map((point) => point.date)).size < MIN_WEIGH_INS) return null;
  if (daysBetweenKeys(recent[0].date, recent[recent.length - 1].date) < MIN_WEIGHT_SPAN_DAYS) return null;

  const trend = fitWeightTrend(recent);
  if (trend === null) return null;
  const xToday = daysBetweenKeys(trend.origin, today);
  const xTarget = xToday + days;
  const fitted = (x: number) => trend.meanY + trend.slopePerDay * (x - trend.meanX);
  const margin = Z_95 * trend.sigma * Math.sqrt(1 / trend.n + (xTarget - trend.meanX) ** 2 / trend.sxx);

  return {
    days,
    currentKg: round1(fitted(xToday)),
    projectedKg: round1(fitted(xTarget)),
    marginKg: round1(margin),
    ratePerWeek: round2(trend.slopePerDay * 7),
  };
}

/** Minimal meal shape the coach needs. */
export interface CoachMeal {
  logDate: string;
  title: string;
  ingredients: IngredientInput[];
  resolvedCalories: number;
  resolvedProtein: number;
  resolvedCarbs: number;
  resolvedFat: number;
}

export interface CoachWeight {
  date: string;
  weightKg: number;
  bodyFatPct: number | null;
}

export interface CoachInput {
  today: string;
  profile: CalorieProfile;
  calorie: CalorieRecommendation | null;
  protein: ProteinRecommendation | null;
  expenditure: ExpenditureEstimate | null;
  meals: CoachMeal[];
  /** Sorted by date, oldest first. */
  weights: CoachWeight[];
}

/** Days of daily totals included, before today. */
export const COACH_HISTORY_DAYS = 14;
/** How far back the weight summary looks. */
export const COACH_WEIGHT_DAYS = 60;

/** Same text on every platform: integers without decimals, «80.2», never «80.0». */
function num(value: number): string {
  return String(value === 0 ? 0 : value);
}

const GOAL_TEXT = {
  cut: "cut (lose fat)",
  maintain: "maintain weight",
  surplus: "bulk (gain muscle)",
} as const;

function macros(calories: number, protein: number, carbs: number, fat: number): string {
  return `${num(Math.round(calories))} kcal, ${num(round1(protein))} g protein, ${num(round1(carbs))} g carbs, ${num(round1(fat))} g fat`;
}

/** «Left to reach the target range: 739–939 kcal», «Within …», «Over … by 120 kcal». */
function remaining(label: string, unit: string, eaten: number, min: number, max: number): string {
  const toMin = Math.round(min - eaten);
  const toMax = Math.round(max - eaten);
  if (toMax < 0) return `${label}: over the target range by ${num(-toMax)} ${unit}`;
  if (toMin <= 0) return `${label}: within the target range (up to ${num(toMax)} ${unit} more)`;
  return `${label}: ${num(toMin)}–${num(toMax)} ${unit} left to reach the target range`;
}

function dailyTotals(meals: CoachMeal[]): Map<string, { calories: number; protein: number; carbs: number; fat: number }> {
  const totals = new Map<string, { calories: number; protein: number; carbs: number; fat: number }>();
  for (const meal of meals) {
    const day = totals.get(meal.logDate) ?? { calories: 0, protein: 0, carbs: 0, fat: 0 };
    day.calories += meal.resolvedCalories;
    day.protein += meal.resolvedProtein;
    day.carbs += meal.resolvedCarbs;
    day.fat += meal.resolvedFat;
    totals.set(meal.logDate, day);
  }
  return totals;
}

/**
 * The user's data as compact English text for the coach's system prompt. Only
 * numbers the app computed (targets, measured expenditure, projection) so the
 * model does not have to invent them. Deterministic: same input, same text,
 * on web and Android.
 */
export function buildCoachContext(input: CoachInput): string {
  const { today, profile, calorie, protein, expenditure } = input;
  const lines: string[] = [`Today: ${today}`];

  // Profile
  const year = Number(today.slice(0, 4));
  const parts = [
    profile.gender ?? "sex unknown",
    profile.birthYear !== null ? `${num(year - profile.birthYear)} years` : "age unknown",
    profile.heightCm !== null ? `${num(profile.heightCm)} cm` : "height unknown",
    profile.calorieGoal !== null ? `goal: ${GOAL_TEXT[profile.calorieGoal]}` : "goal unknown",
  ];
  lines.push(`Profile: ${parts.join(", ")}.`);
  if (profile.gymDaysPerWeek !== null && profile.gymSessionMinutes !== null && profile.walkingMinutesPerDay !== null) {
    lines.push(
      `Activity: gym ${num(profile.gymDaysPerWeek)} days/week × ${num(profile.gymSessionMinutes)} min, walking ${num(profile.walkingMinutesPerDay)} min/day.`,
    );
  }

  // Targets
  if (calorie) {
    lines.push(
      `Calorie target: ${num(calorie.targetMin)}–${num(calorie.targetMax)} kcal/day (BMR ${num(calorie.bmr)}, estimated TDEE ${num(calorie.tdee)}, activity factor ${num(calorie.activityFactor)}).`,
    );
  } else {
    lines.push("Calorie target: not available (profile incomplete).");
  }
  if (protein) {
    const basis =
      protein.basis === "leanMass"
        ? `${num(protein.perKg.min)}–${num(protein.perKg.max)} g/kg of ${num(protein.basisKg)} kg lean mass`
        : `${num(protein.perKg.min)}–${num(protein.perKg.max)} g/kg body weight`;
    lines.push(`Protein target: ${num(protein.range.min)}–${num(protein.range.max)} g/day (${basis}).`);
  }
  lines.push(
    expenditure
      ? `Measured expenditure (energy balance, last ${num(expenditure.windowDays)} days): ${num(expenditure.tdee)} ± ${num(expenditure.margin)} kcal/day.`
      : "Measured expenditure: not enough data yet (needs 4 weeks of logged meals and frequent weigh-ins).",
  );

  // Today
  const totals = dailyTotals(input.meals);
  const todaysMeals = input.meals.filter((meal) => meal.logDate === today);
  if (todaysMeals.length === 0) {
    lines.push("Today's meals: none logged yet.");
  } else {
    lines.push("Today's meals:");
    for (const meal of todaysMeals) {
      const items = meal.ingredients
        .filter((item) => item.name.trim() !== "")
        .map((item) => (item.quantity ? `${item.name} (${item.quantity})` : item.name));
      const itemText = items.length > 0 ? `. Items: ${items.join(", ")}` : "";
      lines.push(
        `- ${meal.title}: ${macros(meal.resolvedCalories, meal.resolvedProtein, meal.resolvedCarbs, meal.resolvedFat)}${itemText}.`,
      );
    }
  }
  const eaten = totals.get(today) ?? { calories: 0, protein: 0, carbs: 0, fat: 0 };
  lines.push(`Today so far: ${macros(eaten.calories, eaten.protein, eaten.carbs, eaten.fat)}.`);
  if (calorie) lines.push(`${remaining("Calories", "kcal", eaten.calories, calorie.targetMin, calorie.targetMax)}.`);
  if (protein) lines.push(`${remaining("Protein", "g", eaten.protein, protein.range.min, protein.range.max)}.`);

  // History (logged days only)
  const history = [];
  for (let n = COACH_HISTORY_DAYS; n >= 1; n--) {
    const date = addDaysToKey(today, -n);
    const day = totals.get(date) ?? { calories: 0, protein: 0, carbs: 0, fat: 0 };
    history.push({ date, ...day });
  }
  const logged = history.filter((day) => day.calories > 0);
  if (logged.length === 0) {
    lines.push(`Last ${COACH_HISTORY_DAYS} days: no meals logged.`);
  } else {
    lines.push(`Last ${COACH_HISTORY_DAYS} days (logged days only, oldest first):`);
    for (const day of logged) lines.push(`- ${day.date}: ${macros(day.calories, day.protein, day.carbs, day.fat)}`);
    const averages = macroAverages(history)!;
    const split = averages.split
      ? ` (protein ${num(averages.split.protein)}%, carbs ${num(averages.split.carbs)}%, fat ${num(averages.split.fat)}% of calories)`
      : "";
    lines.push(
      `Average over ${num(averages.loggedDays)} of ${num(averages.totalDays)} days logged: ${macros(averages.calories, averages.protein, averages.carbs, averages.fat)}${split}.`,
    );
  }

  // Weight
  const from = addDaysToKey(today, -COACH_WEIGHT_DAYS);
  const recentWeights = input.weights.filter((w) => w.date >= from && w.date <= today);
  const latest = input.weights.at(-1);
  if (!latest) {
    lines.push("Weight: no weigh-ins logged.");
  } else {
    lines.push(`Weight: latest ${num(round1(latest.weightKg))} kg on ${latest.date}.`);
    const bodyFat = [...input.weights].reverse().find((w) => w.bodyFatPct !== null);
    if (bodyFat) lines.push(`Body fat: ${num(round1(bodyFat.bodyFatPct!))}% on ${bodyFat.date}.`);
    if (recentWeights.length > 1) {
      const first = recentWeights[0];
      lines.push(
        `Weight in the last ${COACH_WEIGHT_DAYS} days: ${num(recentWeights.length)} weigh-ins, the first ${num(round1(first.weightKg))} kg on ${first.date}.`,
      );
    }
    const projection = weightProjection(
      input.weights.map((w) => ({ date: w.date, value: w.weightKg })),
      today,
    );
    lines.push(
      projection
        ? `Weight trend (last ${EXPENDITURE_WINDOW_DAYS} days): ${num(projection.ratePerWeek)} kg/week, now ${num(projection.currentKg)} kg. Projection computed by the app if this trend continues: ${num(projection.projectedKg)} ± ${num(projection.marginKg)} kg in ${num(projection.days)} days.`
        : `Weight trend: not enough weigh-ins in the last ${EXPENDITURE_WINDOW_DAYS} days to project (needs at least ${MIN_WEIGH_INS} days spanning ${MIN_WEIGHT_SPAN_DAYS}).`,
    );
  }

  return lines.join("\n");
}

/**
 * System prompt for the coach. `language` is the name of the app language in
 * English («Spanish»); `context` is `buildCoachContext()` or null when the user
 * does not share their data.
 */
export function buildCoachSystemPrompt(language: string, context: string | null): string {
  const rules = [
    "You are the coach inside Blackwater Macros, a simple app to log meals (calories, protein, carbs, fat) and body weight.",
    `Always answer in ${language}. Be brief and practical: short paragraphs or short lists.`,
    "For questions about the macros of foods, give an estimate per item (grams, kcal, protein, carbs, fat) and a total.",
    "Use the numbers the app computed (targets, measured expenditure, weight projection) instead of calculating your own, and say so when data is missing.",
    "Do not invent data the user did not log.",
    "You are not a doctor: for medical conditions, eating disorders, pregnancy or medication, recommend seeing a professional.",
  ];
  const data = context === null ? "The user chose not to share their data with the coach." : `USER DATA\n${context}`;
  return `${rules.join("\n")}\n\n${data}`;
}
