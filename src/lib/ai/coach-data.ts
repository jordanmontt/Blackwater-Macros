import { api } from "@/lib/api";
import { calculateCalorieRecommendation } from "@/lib/core/calories";
import { buildCoachContext, COACH_WEIGHT_DAYS, type CoachInput } from "@/lib/core/coach";
import { addDaysToKey, toDateKey, todayKey } from "@/lib/core/dates";
import { EXPENDITURE_WINDOW_DAYS, estimateExpenditure } from "@/lib/core/expenditure";
import { calculateProteinRecommendation } from "@/lib/core/protein";

/**
 * The coach's view of the user's data, read fresh at each
 * question: profile, targets, measured expenditure, the last 4 weeks of meals
 * and the weigh-ins. Same numbers as the Comidas card and Progreso.
 */
export async function loadCoachInput(today = todayKey()): Promise<CoachInput> {
  const from = addDaysToKey(today, -EXPENDITURE_WINDOW_DAYS);
  const [session, meals, weightDtos] = await Promise.all([
    api.session(),
    api.listMeals(from, today),
    api.listWeights(),
  ]);
  const profile = session.calorieProfile;
  const weights = weightDtos
    .map((w) => ({ date: toDateKey(new Date(w.measuredAt)), weightKg: w.weightKg, bodyFatPct: w.bodyFatPct }))
    .filter((w) => w.date >= addDaysToKey(today, -COACH_WEIGHT_DAYS) && w.date <= today)
    .sort((a, b) => a.date.localeCompare(b.date));

  const latest = weightDtos.at(-1) ?? null;
  const bodyFatPct = weightDtos.findLast((w) => w.bodyFatPct !== null)?.bodyFatPct ?? null;
  const year = Number(today.slice(0, 4));
  const calorie = latest ? calculateCalorieRecommendation(profile, latest.weightKg, year) : null;
  const protein =
    latest && profile.calorieGoal
      ? calculateProteinRecommendation(latest.weightKg, profile.calorieGoal, bodyFatPct, {
          heightCm: profile.heightCm,
          gender: profile.gender,
          age: profile.birthYear === null ? null : year - profile.birthYear,
        })
      : null;
  const expenditure = estimateExpenditure(
    meals.filter((meal) => meal.logDate < today).map((meal) => ({ date: meal.logDate, value: meal.resolvedCalories })),
    weightDtos.map((w) => ({ date: toDateKey(new Date(w.measuredAt)), value: w.weightKg })),
    today,
  );

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
