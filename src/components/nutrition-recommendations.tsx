"use client";

import { useEffect, useMemo, useState } from "react";
import { DumbbellIcon, FlameIcon } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { api } from "@/lib/api";
import { formatNumberEs } from "@/lib/dates";
import { calculateCalorieRecommendation } from "@/lib/calories";
import { calculateProteinRecommendation } from "@/lib/protein";
import type { CalorieProfile, Goal, WeightDTO } from "@/lib/types";
import { formatTemplate, t } from "@/i18n";

const GOAL_LABELS: Record<Goal, string> = {
  cut: t.calorias.goalCut,
  maintain: t.calorias.goalMaintain,
  surplus: t.calorias.goalSurplus,
};

function emptyProfile(): CalorieProfile {
  return {
    gender: null,
    birthYear: null,
    heightCm: null,
    gymDaysPerWeek: null,
    gymSessionMinutes: null,
    walkingMinutesPerDay: null,
    calorieGoal: null,
  };
}

function IntakeBar({
  current,
  rangeMin,
  rangeMax,
  statusLabel,
  statusColor,
}: {
  current: number;
  rangeMin: number;
  rangeMax: number;
  statusLabel: string;
  statusColor: string;
}) {
  const pct = rangeMax > 0 ? (current / rangeMax) * 100 : 0;
  const barPct = Math.min(Math.max(pct, 0), 100);
  return (
    <div className="space-y-1.5">
      <p className="text-xs text-muted-foreground">
        {t.calorias.currentIntake}:{" "}
        <span className="font-medium text-foreground">{formatNumberEs(current, 1)}</span>{" "}
        <span className={statusColor}>{statusLabel}</span>
      </p>
      <div className="relative h-2 w-full overflow-hidden rounded-full bg-muted">
        <div
          className="absolute inset-y-0 left-0 rounded-full bg-primary transition-all"
          style={{ width: `${barPct}%` }}
        />
        <div
          className="absolute inset-y-0 w-0.5 bg-muted-foreground/40"
          style={{ left: `${(rangeMin / rangeMax) * 100}%` }}
        />
      </div>
    </div>
  );
}

/**
 * Collapsed calorie + protein recommendation card. Fetches weights and the
 * user profile once and renders both ranges in a single card.
 */
export function NutritionRecommendationsCard({
  dailyCalories,
  dailyProtein,
}: {
  dailyCalories: number;
  dailyProtein: number;
}) {
  const [weights, setWeights] = useState<WeightDTO[] | null>(null);
  const [calorieProfile, setCalorieProfile] = useState<CalorieProfile>(emptyProfile);

  useEffect(() => {
    let cancelled = false;
    Promise.all([api.listWeights(), api.session()])
      .then(([w, session]) => {
        if (!cancelled) {
          setWeights(w);
          setCalorieProfile(session.calorieProfile);
        }
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  const latestWeight = weights?.at(-1) ?? null;

  const calorieRec = useMemo(
    () => (latestWeight ? calculateCalorieRecommendation(calorieProfile, latestWeight.weightKg) : null),
    [latestWeight, calorieProfile],
  );

  const proteinRec = useMemo(
    () =>
      latestWeight && calorieProfile.calorieGoal
        ? calculateProteinRecommendation(latestWeight.weightKg, calorieProfile.calorieGoal)
        : null,
    [latestWeight, calorieProfile.calorieGoal],
  );

  if (!latestWeight) {
    return (
      <Card>
        <CardContent className="px-4 py-3 text-center text-sm text-muted-foreground">
          {t.protein.noWeight}
        </CardContent>
      </Card>
    );
  }

  if (!calorieRec && !proteinRec) return null;

  return (
    <Card>
      <CardContent className="grid gap-4 px-4 pb-4 sm:grid-cols-2">
        {calorieRec ? (
          <section className="space-y-2.5">
            <div className="flex items-center gap-1.5 text-sm font-medium">
              <FlameIcon className="size-4" /> {t.calorias.recommendationTitle}
              <span className="text-xs font-normal text-muted-foreground">
                · {GOAL_LABELS[calorieRec.goal]}
              </span>
            </div>
            <p className="text-2xl font-semibold tabular-nums">
              {formatNumberEs(calorieRec.target)}{" "}
              <span className="text-sm font-normal text-muted-foreground">{t.calorias.perDay}</span>
            </p>
            <p className="text-xs text-muted-foreground">
              {formatNumberEs(calorieRec.targetMin)} – {formatNumberEs(calorieRec.targetMax)}{" "}
              {t.calorias.perDay}
            </p>
            <IntakeBar
              current={dailyCalories}
              rangeMin={calorieRec.targetMin}
              rangeMax={calorieRec.targetMax}
              statusLabel={
                dailyCalories >= calorieRec.targetMin && dailyCalories <= calorieRec.targetMax
                  ? t.calorias.inRange
                  : dailyCalories < calorieRec.targetMin
                    ? t.calorias.belowRange
                    : t.calorias.aboveRange
              }
              statusColor={
                dailyCalories >= calorieRec.targetMin && dailyCalories <= calorieRec.targetMax
                  ? "text-green-600 dark:text-green-400"
                  : dailyCalories < calorieRec.targetMin
                    ? "text-yellow-600 dark:text-yellow-400"
                    : "text-orange-600 dark:text-orange-400"
              }
            />
          </section>
        ) : null}

        {proteinRec ? (
          <section className="space-y-2.5">
            <div className="flex items-center gap-1.5 text-sm font-medium">
              <DumbbellIcon className="size-4" /> {t.protein.recommendationTitle}
              <span className="text-xs font-normal text-muted-foreground">
                · {GOAL_LABELS[proteinRec.goal]}
              </span>
            </div>
            <p className="text-xs text-muted-foreground">
              {formatTemplate(t.protein.perKg, {
                min: proteinRec.bwPerKg.min,
                max: proteinRec.bwPerKg.max,
              })}
            </p>
            <p className="text-2xl font-semibold tabular-nums">
              {proteinRec.bwRange.min} – {proteinRec.bwRange.max}{" "}
              <span className="text-sm font-normal text-muted-foreground">g/día</span>
            </p>
            <IntakeBar
              current={dailyProtein}
              rangeMin={proteinRec.bwRange.min}
              rangeMax={proteinRec.bwRange.max}
              statusLabel={
                dailyProtein >= proteinRec.bwRange.min && dailyProtein <= proteinRec.bwRange.max
                  ? t.protein.inRange
                  : dailyProtein < proteinRec.bwRange.min
                    ? t.protein.belowRange
                    : t.protein.aboveRange
              }
              statusColor={
                dailyProtein >= proteinRec.bwRange.min && dailyProtein <= proteinRec.bwRange.max
                  ? "text-green-600 dark:text-green-400"
                  : dailyProtein < proteinRec.bwRange.min
                    ? "text-yellow-600 dark:text-yellow-400"
                    : "text-orange-600 dark:text-orange-400"
              }
            />
          </section>
        ) : null}
      </CardContent>
    </Card>
  );
}