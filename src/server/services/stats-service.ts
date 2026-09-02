import { addDaysToKey, todayKey } from "@/lib/core/dates";
import { rangeToDays } from "@/lib/core/stats";
import { buildStatsFromData, type StatsMeal, type StatsWeight } from "@/lib/core/stats-builder";
import type { StatsRange, StatsSummary } from "@/lib/core/types";
import type { MealsRepository } from "../repositories/meals-repo";
import type { WeightsRepository } from "../repositories/weights-repo";

export interface StatsServiceDeps {
  meals: Pick<MealsRepository, "listInRange">;
  weights: Pick<WeightsRepository, "listForUser">;
}

/**
 * Builds every number and series shown on the statistics page for a range.
 *
 * This is a thin adapter: it fetches rows through the injected repositories
 * and delegates the actual computation to the pure, client-safe
 * `buildStatsFromData` in `lib/core/stats-builder.ts` — the same code the local
 * demo mode uses, so server and demo statistics never drift.
 *
 * `todayKeyParam` is the caller's local calendar day (YYYY-MM-DD). Passing it
 * explicitly keeps ranges anchored to the user's timezone instead of the
 * server's.
 */
export async function buildStatsSummary(
  deps: StatsServiceDeps,
  userId: string,
  range: StatsRange,
  todayKeyParam: string = todayKey(),
): Promise<StatsSummary> {
  const days = rangeToDays(range);
  const toKey = todayKeyParam;
  const fromKey = days === null ? null : addDaysToKey(toKey, -(days - 1));

  const mealRows = await deps.meals.listInRange(userId, fromKey, toKey);
  const weightRows = await deps.weights.listForUser(userId);

  const meals: StatsMeal[] = mealRows.map((meal) => ({
    logDate: meal.logDate,
    resolvedCalories: meal.resolvedCalories,
    resolvedProtein: meal.resolvedProtein,
    resolvedCarbs: meal.resolvedCarbs,
    resolvedFat: meal.resolvedFat,
  }));
  const weights: StatsWeight[] = weightRows.map((row) => ({
    measuredAt: row.measuredAt,
    weightKg: row.weightKg,
    bodyFatPct: row.bodyFatPct,
  }));

  return buildStatsFromData(meals, weights, range, todayKeyParam);
}
