"use client";

import { useEffect, useMemo, useState } from "react";
import { DumbbellIcon } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { api } from "@/lib/api";
import { formatNumberEs } from "@/lib/dates";
import { calculateProteinRecommendation } from "@/lib/protein";
import type { ProteinRecommendation, WeightDTO } from "@/lib/types";
import { formatTemplate, t } from "@/i18n";

export function ProteinRecommendationCard({ dailyProtein }: { dailyProtein: number }) {
  const [weights, setWeights] = useState<WeightDTO[] | null>(null);
  const [proteinGoal, setProteinGoal] = useState<string>("build");

  useEffect(() => {
    let cancelled = false;
    Promise.all([api.listWeights(), api.session()])
      .then(([w, session]) => {
        if (!cancelled) {
          setWeights(w);
          setProteinGoal(session.proteinGoal);
        }
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  const latestWeight = weights?.at(-1) ?? null;

  const rec = useMemo<ProteinRecommendation | null>(() => {
    if (!latestWeight) return null;
    return calculateProteinRecommendation(
      latestWeight.weightKg,
      latestWeight.bodyFatPct,
      proteinGoal as ProteinRecommendation["goal"],
    );
  }, [latestWeight, proteinGoal]);

  if (!latestWeight) {
    return (
      <Card>
        <CardContent className="px-4 py-3 text-center text-sm text-muted-foreground">
          {t.protein.noWeight}
        </CardContent>
      </Card>
    );
  }

  if (!rec) return null;

  const goalLabel =
    rec.goal === "maintain"
      ? t.protein.goalMaintain
      : rec.goal === "build"
        ? t.protein.goalBuild
        : t.protein.goalCut;

  const bwPct = rec.bwRange.max > 0 ? dailyProtein / rec.bwRange.max : 0;
  const barPct = Math.min(Math.max(bwPct * 100, 0), 100);
  const inRange = dailyProtein >= rec.bwRange.min && dailyProtein <= rec.bwRange.max;
  const statusLabel = inRange
    ? t.protein.inRange
    : dailyProtein < rec.bwRange.min
      ? t.protein.belowRange
      : t.protein.aboveRange;
  const statusColor = inRange
    ? "text-green-600 dark:text-green-400"
    : dailyProtein < rec.bwRange.min
      ? "text-yellow-600 dark:text-yellow-400"
      : "text-orange-600 dark:text-orange-400";

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-base">
          <DumbbellIcon className="size-4" /> {t.protein.recommendationTitle}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 px-4 pb-3">
        <p className="text-xs text-muted-foreground">
          {goalLabel} · {formatNumberEs(rec.bodyWeightKg, 1)} kg
          {rec.bodyFatPct !== null ? ` · ${formatNumberEs(rec.bodyFatPct, 1)}% grasa` : ""}
        </p>

        <div className="space-y-1">
          <p className="text-sm font-medium">{t.protein.bwRange}</p>
          <p className="text-xs text-muted-foreground">
            {rec.bwRange.min} – {rec.bwRange.max} g/día{" "}
            {formatTemplate(t.protein.perKg, {
              min: rec.bwPerKg.min,
              max: rec.bwPerKg.max,
            })}
          </p>
        </div>

        {rec.ffmRange && (
          <div className="space-y-1">
            <p className="text-sm font-medium">{t.protein.ffmRange}</p>
            <p className="text-xs text-muted-foreground">
              {rec.ffmRange.min} – {rec.ffmRange.max} g/día{" "}
              {formatTemplate(t.protein.ffmPerKg, {
                min: rec.ffmPerKg!.min,
                max: rec.ffmPerKg!.max,
              })}
            </p>
          </div>
        )}

        {rec.ffmRange === null && rec.goal === "cut" && (
          <p className="text-xs text-muted-foreground">{t.protein.noBodyFat}</p>
        )}

        <div className="space-y-1.5">
          <p className="text-xs text-muted-foreground">
            {t.protein.currentIntake}: <span className="font-medium text-foreground">{formatNumberEs(dailyProtein, 1)} g</span>{" "}
            <span className={statusColor}>{statusLabel}</span>
          </p>
          <div className="relative h-2 w-full overflow-hidden rounded-full bg-muted">
            <div
              className="absolute inset-y-0 left-0 rounded-full bg-primary transition-all"
              style={{ width: `${barPct}%` }}
            />
            <div
              className="absolute inset-y-0 w-0.5 bg-muted-foreground/40"
              style={{ left: `${(rec.bwRange.min / rec.bwRange.max) * 100}%` }}
            />
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
