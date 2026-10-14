"use client";

import { useMemo } from "react";
import { api } from "./api";
import { useCachedResource } from "./use-cached-resource";
import { addDaysToKey, toDateKey, todayKey } from "./core/dates";
import { CHECK_DAYS, checkExpenditure, estimateExpenditure } from "./core/expenditure";
import type { DataPoint } from "./core/stats";
import type { CalorieRecommendation, ExpenditureCheck, ExpenditureEstimate, MealDTO, WeightDTO } from "./core/types";

/**
 * Meals of the last 5 weeks (the check also reads the window that ended a week
 * earlier) and every weigh-in, as data points. Uses the "meals:" and "weights"
 * cache keys, so logging a meal or a weight refreshes it like every other screen.
 */
function useExpenditureInputs(): { intake: DataPoint[]; weights: DataPoint[]; today: string } | null {
  const today = todayKey();
  const from = addDaysToKey(today, -CHECK_DAYS);
  const yesterday = addDaysToKey(today, -1);
  const mealsRes = useCachedResource<MealDTO[]>(`meals:${from}:${yesterday}`, () =>
    api.listMeals(from, yesterday),
  );
  const weightsRes = useCachedResource<WeightDTO[]>("weights", () => api.listWeights());

  const meals = mealsRes.data;
  const weights = weightsRes.data;
  return useMemo(() => {
    if (!meals || !weights) return null;
    return {
      intake: meals.map((meal) => ({ date: meal.logDate, value: meal.resolvedCalories })),
      weights: weights.map((w) => ({ date: toDateKey(new Date(w.measuredAt)), value: w.weightKg })),
      today,
    };
  }, [meals, weights, today]);
}

/**
 * Expenditure measured from the last 4 complete weeks of meals and weigh-ins
 * (`estimateExpenditure`). Null while loading or until the data is good enough.
 */
export function useMeasuredExpenditure(): ExpenditureEstimate | null {
  const inputs = useExpenditureInputs();
  return useMemo(
    () => (inputs ? estimateExpenditure(inputs.intake, inputs.weights, inputs.today) : null),
    [inputs],
  );
}

/**
 * The profile's expenditure checked against the measured one (`checkExpenditure`):
 * what is missing, or whether they match. Null while loading or without a
 * calorie recommendation. Pass the recommendation being shown, so editing the
 * activity in Perfil updates the verdict as you type.
 */
export function useExpenditureCheck(formula: CalorieRecommendation | null): ExpenditureCheck | null {
  const inputs = useExpenditureInputs();
  const bmr = formula?.bmr;
  const activityFactor = formula?.activityFactor;
  const tdee = formula?.tdee;
  return useMemo(() => {
    if (!inputs || bmr === undefined || activityFactor === undefined || tdee === undefined) return null;
    return checkExpenditure(inputs.intake, inputs.weights, { bmr, activityFactor, tdee }, inputs.today);
  }, [inputs, bmr, activityFactor, tdee]);
}
