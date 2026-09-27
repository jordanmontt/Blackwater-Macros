"use client";

import { useMemo } from "react";
import { api } from "./api";
import { useCachedResource } from "./use-cached-resource";
import { addDaysToKey, toDateKey, todayKey } from "./core/dates";
import { EXPENDITURE_WINDOW_DAYS, estimateExpenditure } from "./core/expenditure";
import type { ExpenditureEstimate, MealDTO, WeightDTO } from "./core/types";

/**
 * Expenditure measured from the last 4 complete weeks of meals and weigh-ins
 * (`estimateExpenditure`). Null while loading or until the data is good enough.
 * Uses the "meals:" and "weights" cache keys, so logging a meal or a weight
 * refreshes it like every other screen.
 */
export function useMeasuredExpenditure(): ExpenditureEstimate | null {
  const today = todayKey();
  const from = addDaysToKey(today, -EXPENDITURE_WINDOW_DAYS);
  const yesterday = addDaysToKey(today, -1);
  const mealsRes = useCachedResource<MealDTO[]>(`meals:${from}:${yesterday}`, () =>
    api.listMeals(from, yesterday),
  );
  const weightsRes = useCachedResource<WeightDTO[]>("weights", () => api.listWeights());

  const meals = mealsRes.data;
  const weights = weightsRes.data;
  return useMemo(() => {
    if (!meals || !weights) return null;
    return estimateExpenditure(
      meals.map((meal) => ({ date: meal.logDate, value: meal.resolvedCalories })),
      weights.map((w) => ({ date: toDateKey(new Date(w.measuredAt)), value: w.weightKg })),
      today,
    );
  }, [meals, weights, today]);
}
