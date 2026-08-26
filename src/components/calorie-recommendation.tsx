"use client";

import { useEffect, useMemo, useState } from "react";
import { FlameIcon } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { api } from "@/lib/api";
import { formatNumberEs } from "@/lib/dates";
import { calculateCalorieRecommendation } from "@/lib/calories";
import type { CalorieProfile, CalorieRecommendation, WeightDTO } from "@/lib/types";
import { t } from "@/i18n";

const GOAL_LABELS: Record<string, string> = {
  deficit: t.calorias.goalDeficit,
  maintain: t.calorias.goalMaintain,
  surplus: t.calorias.goalSurplus,
};

export function CalorieRecommendationCard({ dailyCalories }: { dailyCalories: number }) {
  const [weights, setWeights] = useState<WeightDTO[] | null>(null);
  const [calorieProfile, setCalorieProfile] = useState<CalorieProfile>({
    gender: null,
    birthYear: null,
    heightCm: null,
    gymDaysPerWeek: null,
    gymSessionMinutes: null,
    walkingMinutesPerDay: null,
    calorieGoal: null,
  });

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

  const rec = useMemo<CalorieRecommendation | null>(() => {
    if (!latestWeight) return null;
    return calculateCalorieRecommendation(calorieProfile, latestWeight.weightKg);
  }, [latestWeight, calorieProfile]);

  if (!latestWeight) {
    return (
      <Card>
        <CardContent className="px-4 py-3 text-center text-sm text-muted-foreground">
          {t.calorias.noProfile}
        </CardContent>
      </Card>
    );
  }

  if (!rec) {
    return (
      <Card>
        <CardContent className="px-4 py-3 text-center text-sm text-muted-foreground">
          {t.calorias.noProfile}
        </CardContent>
      </Card>
    );
  }

  const goalLabel = GOAL_LABELS[rec.goal] ?? rec.goal;

  const bwPct = rec.targetMax > 0 ? dailyCalories / rec.targetMax : 0;
  const barPct = Math.min(Math.max(bwPct * 100, 0), 100);
  const inRange = dailyCalories >= rec.targetMin && dailyCalories <= rec.targetMax;
  const statusLabel = inRange
    ? t.calorias.inRange
    : dailyCalories < rec.targetMin
      ? t.calorias.belowRange
      : t.calorias.aboveRange;
  const statusColor = inRange
    ? "text-green-600 dark:text-green-400"
    : dailyCalories < rec.targetMin
      ? "text-yellow-600 dark:text-yellow-400"
      : "text-orange-600 dark:text-orange-400";

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-base">
          <FlameIcon className="size-4" /> {t.calorias.recommendationTitle}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 px-4 pb-3">
        <p className="text-xs text-muted-foreground">
          {goalLabel}
        </p>

        <div className="space-y-1">
          <p className="text-sm font-medium">{t.calorias.target}</p>
          <p className="text-2xl font-semibold tabular-nums">
            {formatNumberEs(rec.target)} <span className="text-sm font-normal text-muted-foreground">{t.calorias.perDay}</span>
          </p>
          <p className="text-xs text-muted-foreground">
            {formatNumberEs(rec.targetMin)} – {formatNumberEs(rec.targetMax)} {t.calorias.perDay}
          </p>
        </div>

        <div className="space-y-1.5">
          <p className="text-xs text-muted-foreground">
            {t.calorias.currentIntake}: <span className="font-medium text-foreground">{formatNumberEs(dailyCalories)} kcal</span>{" "}
            <span className={statusColor}>{statusLabel}</span>
          </p>
          <div className="relative h-2 w-full overflow-hidden rounded-full bg-muted">
            <div
              className="absolute inset-y-0 left-0 rounded-full bg-primary transition-all"
              style={{ width: `${barPct}%` }}
            />
            <div
              className="absolute inset-y-0 w-0.5 bg-muted-foreground/40"
              style={{ left: `${(rec.targetMin / rec.targetMax) * 100}%` }}
            />
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
