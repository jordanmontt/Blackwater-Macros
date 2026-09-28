"use client";

import { useMemo } from "react";
import { api } from "./api";
import { useCachedResource } from "./use-cached-resource";
import { calculateCalorieRecommendation } from "./core/calories";
import { calculateProteinRecommendation } from "./core/protein";
import type { CalorieProfile, CalorieRecommendation, ProteinRecommendation, WeightDTO } from "./core/types";

const EMPTY_PROFILE: CalorieProfile = {
  gender: null,
  birthYear: null,
  heightCm: null,
  gymDaysPerWeek: null,
  gymSessionMinutes: null,
  walkingMinutesPerDay: null,
  calorieGoal: null,
};

/**
 * Calorie and protein recommendations from the latest weigh-in and the saved
 * profile (the same numbers everywhere: Comidas card, Progreso). `weights` is
 * undefined while loading.
 */
export function useRecommendations(): {
  weights: WeightDTO[] | undefined;
  latestWeight: WeightDTO | null;
  calorieRec: CalorieRecommendation | null;
  proteinRec: ProteinRecommendation | null;
} {
  const weightsRes = useCachedResource<WeightDTO[]>("weights", () => api.listWeights());
  const sessionRes = useCachedResource<Awaited<ReturnType<typeof api.session>>>("session", () => api.session());

  const weights = weightsRes.data;
  const profile = sessionRes.data?.calorieProfile ?? EMPTY_PROFILE;
  const latestWeight = weights?.at(-1) ?? null;
  // Body fat is not logged at every weigh-in: use the most recent one there is.
  const bodyFatPct = weights?.findLast((w) => w.bodyFatPct !== null)?.bodyFatPct ?? null;

  const calorieRec = useMemo(
    () => (latestWeight ? calculateCalorieRecommendation(profile, latestWeight.weightKg, new Date().getFullYear()) : null),
    [latestWeight, profile],
  );
  const proteinRec = useMemo(
    () =>
      latestWeight && profile.calorieGoal
        ? calculateProteinRecommendation(latestWeight.weightKg, profile.calorieGoal, bodyFatPct, {
            heightCm: profile.heightCm,
            gender: profile.gender,
            age: profile.birthYear === null ? null : new Date().getFullYear() - profile.birthYear,
          })
        : null,
    [latestWeight, profile.calorieGoal, profile.heightCm, profile.gender, profile.birthYear, bodyFatPct],
  );

  return { weights, latestWeight, calorieRec, proteinRec };
}
