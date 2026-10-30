"use client";

import { useMemo } from "react";
import { api } from "./api";
import { useCachedResource } from "./use-cached-resource";
import { todayKey, toDateKey, weekStartOf } from "./core/dates";
import { checkExpenditure, estimateExpenditure, weeklyExpenditure } from "./core/expenditure";
import type { MeasuredOn } from "./core/profile-history";
import type { DataPoint } from "./core/stats";
import type { CalorieRecommendation, ExpenditureCheck, ExpenditureEstimate, StatsSummary, WeightDTO } from "./core/types";

/**
 * Every day's logged kcal (the all-time stats series, 0 when nothing was logged)
 * and every weigh-in, as data points: any week's measurement can be taken, so a
 * past day keeps the target it had. Uses the "stats:" and "weights" cache keys,
 * so logging a meal or a weight refreshes it like every other screen.
 */
function useExpenditureInputs(): { intake: DataPoint[]; weights: DataPoint[]; today: string } | null {
  const today = todayKey();
  const statsRes = useCachedResource<StatsSummary>(`stats:all:${today}`, () => api.stats("all", today));
  const weightsRes = useCachedResource<WeightDTO[]>("weights", () => api.listWeights());

  const stats = statsRes.data;
  const weights = weightsRes.data;
  return useMemo(() => {
    if (!stats || !weights) return null;
    return {
      intake: stats.calories.map((day) => ({ date: day.date, value: day.calories })),
      weights: weights.map((w) => ({ date: toDateKey(new Date(w.measuredAt)), value: w.weightKg })),
      today,
    };
  }, [stats, weights, today]);
}

/**
 * Each day's measured expenditure, the one taken on the Monday of its week
 * (`weeklyExpenditure`): what the calorie targets are built on. Null while loading.
 */
export function useMeasuredOn(): MeasuredOn | null {
  const inputs = useExpenditureInputs();
  return useMemo(() => (inputs ? weeklyExpenditure(inputs.intake, inputs.weights) : null), [inputs]);
}

/**
 * This week's measured expenditure: the 4 weeks before Monday (`estimateExpenditure`).
 * Null while loading or until the data is good enough.
 */
export function useMeasuredExpenditure(): ExpenditureEstimate | null {
  const inputs = useExpenditureInputs();
  return useMemo(
    () => (inputs ? estimateExpenditure(inputs.intake, inputs.weights, weekStartOf(inputs.today)) : null),
    [inputs],
  );
}

/**
 * The profile's expenditure checked against this week's measured one
 * (`checkExpenditure`): what is missing, or whether they match. Null while
 * loading or without a calorie recommendation. Pass the recommendation being
 * shown, so editing the activity in Perfil updates the verdict as you type.
 */
export function useExpenditureCheck(formula: CalorieRecommendation | null): ExpenditureCheck | null {
  const inputs = useExpenditureInputs();
  const bmr = formula?.bmr;
  const activityFactor = formula?.activityFactor;
  const tdee = formula?.tdee;
  return useMemo(() => {
    if (!inputs || bmr === undefined || activityFactor === undefined || tdee === undefined) return null;
    return checkExpenditure(inputs.intake, inputs.weights, { bmr, activityFactor, tdee }, weekStartOf(inputs.today));
  }, [inputs, bmr, activityFactor, tdee]);
}
