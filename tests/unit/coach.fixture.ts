import { addDaysToKey } from "../../src/lib/core/dates";
import { calculateCalorieRecommendation } from "../../src/lib/core/calories";
import { calculateProteinRecommendation } from "../../src/lib/core/protein";
import type { CoachInput } from "../../src/lib/core/coach";
import type { CalorieProfile } from "../../src/lib/core/types";

export const TODAY = "2026-03-01";

const profile: CalorieProfile = {
  gender: "male",
  birthYear: 1990,
  heightCm: 178,
  gymDaysPerWeek: 3,
  gymSessionMinutes: 60,
  walkingMinutesPerDay: 30,
  calorieGoal: "cut",
};

/** Daily weigh-ins from 27 days ago to today, losing 0.5 kg/week; body fat only on the first. */
const weights = Array.from({ length: 28 }, (_, i) => ({
  date: addDaysToKey(TODAY, -(27 - i)),
  weightKg: 80 - (0.5 / 7) * i,
  bodyFatPct: i === 0 ? 20 : null,
}));

export const COACH_INPUT: CoachInput = {
  today: TODAY,
  profile,
  calorie: calculateCalorieRecommendation(profile, 80, 2026),
  protein: calculateProteinRecommendation(80, "cut", 20),
  expenditure: { tdee: 2750, margin: 177, avgIntake: 2300, weightChangePerWeek: -0.5, loggedDays: 28, weighIns: 28, windowDays: 28 },
  meals: [
    {
      logDate: TODAY,
      title: "Desayuno",
      ingredients: [
        { name: "Avena", quantity: "80 g", calories: 300 },
        { name: "Leche", quantity: "200 ml" },
        { name: " " },
      ],
      resolvedCalories: 450,
      resolvedProtein: 30,
      resolvedCarbs: 50,
      resolvedFat: 12,
    },
    { logDate: addDaysToKey(TODAY, -1), title: "Comida", ingredients: [], resolvedCalories: 2100.4, resolvedProtein: 150, resolvedCarbs: 200, resolvedFat: 70.25 },
    { logDate: addDaysToKey(TODAY, -3), title: "Cena", ingredients: [], resolvedCalories: 1900, resolvedProtein: 120, resolvedCarbs: 180, resolvedFat: 60 },
    { logDate: addDaysToKey(TODAY, -40), title: "Antigua", ingredients: [], resolvedCalories: 3000, resolvedProtein: 100, resolvedCarbs: 300, resolvedFat: 100 },
  ],
  weights,
};
