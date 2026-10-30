"use client";

import { useMemo } from "react";
import { api } from "./api";
import { useCachedResource } from "./use-cached-resource";
import { useMeasuredOn } from "./use-measured-expenditure";
import { toDateKey, todayKey } from "./core/dates";
import { targetsOn, type DatedWeight, type MeasuredOn } from "./core/profile-history";
import type {
  CalorieProfile,
  CalorieRecommendation,
  ProfilePeriod,
  ProteinRecommendation,
  WeightDTO,
} from "./core/types";

const EMPTY_PROFILE: CalorieProfile = {
  gender: null,
  birthYear: null,
  heightCm: null,
  gymDaysPerWeek: null,
  gymSessionMinutes: null,
  walkingMinutesPerDay: null,
  calorieGoal: null,
};

const NO_HISTORY: ProfilePeriod[] = [];

/** Weigh-ins on their local calendar day, in measuring order (`core/profile-history.ts`). */
export function datedWeights(weights: WeightDTO[]): DatedWeight[] {
  return weights.map((w) => ({
    date: toDateKey(new Date(w.measuredAt)),
    weightKg: w.weightKg,
    bodyFatPct: w.bodyFatPct,
  }));
}

/**
 * Calorie and protein recommendations of `day` (today by default): the profile
 * that applied that day, the last weigh-in on or before it and the expenditure
 * measured on that week's Monday, so editing the profile never rewrites past
 * days (the same numbers everywhere: Comidas card, Progreso). `weights` is
 * undefined while loading; `measuredOn` is null while the meals load.
 */
export function useRecommendations(day?: string): {
  weights: WeightDTO[] | undefined;
  profile: CalorieProfile;
  profileHistory: ProfilePeriod[];
  latestWeight: WeightDTO | null;
  measuredOn: MeasuredOn | null;
  calorieRec: CalorieRecommendation | null;
  proteinRec: ProteinRecommendation | null;
} {
  const weightsRes = useCachedResource<WeightDTO[]>("weights", () => api.listWeights());
  const sessionRes = useCachedResource<Awaited<ReturnType<typeof api.session>>>("session", () => api.session());

  const weights = weightsRes.data;
  const profile = sessionRes.data?.calorieProfile ?? EMPTY_PROFILE;
  const profileHistory = sessionRes.data?.profileHistory ?? NO_HISTORY;
  const latestWeight = weights?.at(-1) ?? null;
  const targetDay = day ?? todayKey();
  const measuredOn = useMeasuredOn();

  const { calorie, protein } = useMemo(
    () => targetsOn(profile, profileHistory, datedWeights(weights ?? []), targetDay, measuredOn ?? undefined),
    [profile, profileHistory, weights, targetDay, measuredOn],
  );

  return {
    weights,
    profile,
    profileHistory,
    latestWeight,
    measuredOn,
    calorieRec: calorie,
    proteinRec: protein,
  };
}
