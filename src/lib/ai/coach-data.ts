import { api } from "@/lib/api";
import { calculateCalorieRecommendation, doesStrengthTraining } from "@/lib/core/calories";
import { buildCoachContext, COACH_WEIGHT_DAYS, type CoachInput, type CoachWeight } from "@/lib/core/coach";
import { addDaysToKey, toDateKey, todayKey, weekStartOf } from "@/lib/core/dates";
import { EXPENDITURE_WINDOW_DAYS, estimateExpenditure, weighInsOf } from "@/lib/core/expenditure";
import type { FoodLog } from "@/lib/core/food-questions";
import { calculateProteinRecommendation } from "@/lib/core/protein";
import type { WeightDTO } from "@/lib/core/types";

/** The weigh-ins as the coach reads them, oldest first. */
function coachWeights(weightDtos: WeightDTO[]): CoachWeight[] {
  return weightDtos
    .map((w) => ({
      date: toDateKey(new Date(w.measuredAt)),
      weightKg: w.weightKg,
      bodyFatPct: w.bodyFatPct,
      fasted: w.fasted,
      time: w.measuredAt,
      timeZone: w.timeZone,
    }))
    .sort((a, b) => a.date.localeCompare(b.date));
}

/**
 * The coach's view of the user's data, read fresh at each
 * question: profile, targets, this week's measured expenditure, the meals since
 * 4 weeks before Monday and the weigh-ins. Same numbers as the Comidas card and Progreso.
 */
export async function loadCoachInput(today = todayKey()): Promise<CoachInput> {
  // The measurement is this week's (the 4 weeks before Monday), as on the Comidas card.
  const monday = weekStartOf(today);
  const from = addDaysToKey(monday, -EXPENDITURE_WINDOW_DAYS);
  const [session, meals, weightDtos] = await Promise.all([
    api.session(),
    api.listMeals(from, today),
    api.listWeights(),
  ]);
  const profile = session.calorieProfile;
  const weights = coachWeights(weightDtos).filter((w) => w.date >= addDaysToKey(today, -COACH_WEIGHT_DAYS) && w.date <= today);

  const latest = weightDtos.at(-1) ?? null;
  const bodyFatPct = weightDtos.findLast((w) => w.bodyFatPct !== null)?.bodyFatPct ?? null;
  const year = Number(today.slice(0, 4));
  const expenditure = estimateExpenditure(
    meals.filter((meal) => meal.logDate < today).map((meal) => ({ date: meal.logDate, value: meal.resolvedCalories })),
    weighInsOf(weightDtos),
    monday,
  );
  const calorie = latest ? calculateCalorieRecommendation(profile, latest.weightKg, year, expenditure) : null;
  const protein =
    latest && profile.calorieGoal
      ? calculateProteinRecommendation(latest.weightKg, profile.calorieGoal, bodyFatPct, {
          heightCm: profile.heightCm,
          gender: profile.gender,
          age: profile.birthYear === null ? null : year - profile.birthYear,
          strengthTraining: doesStrengthTraining(profile),
        })
      : null;

  return {
    today,
    profile,
    calorie,
    protein,
    expenditure,
    meals: meals.map((meal) => ({
      logDate: meal.logDate,
      title: meal.title,
      ingredients: meal.ingredients,
      resolvedCalories: meal.resolvedCalories,
      resolvedProtein: meal.resolvedProtein,
      resolvedCarbs: meal.resolvedCarbs,
      resolvedFat: meal.resolvedFat,
    })),
    weights,
  };
}

export async function loadCoachContext(): Promise<string> {
  return buildCoachContext(await loadCoachInput());
}

/** Food questions read the whole log, not only these weeks: «since when», «last time». */
const FOOD_LOG_START = "1900-01-01";

/**
 * Everything food questions may read: every meal up to today and every
 * weigh-in. Read fresh at each question, like the coach's summary.
 */
export async function loadFoodLog(today = todayKey()): Promise<FoodLog> {
  const [meals, weightDtos] = await Promise.all([api.listMeals(FOOD_LOG_START, today), api.listWeights()]);
  return {
    today,
    meals: meals.map((meal) => ({
      logDate: meal.logDate,
      title: meal.title,
      entryMode: meal.entryMode,
      ingredients: meal.ingredients,
      resolvedCalories: meal.resolvedCalories,
      resolvedProtein: meal.resolvedProtein,
      resolvedCarbs: meal.resolvedCarbs,
      resolvedFat: meal.resolvedFat,
    })),
    weights: coachWeights(weightDtos).filter((w) => w.date <= today),
  };
}
