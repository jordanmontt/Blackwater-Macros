"use client";

import { useMemo } from "react";
import { DumbbellIcon, FlameIcon } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { api } from "@/lib/api";
import { useCachedResource } from "@/lib/use-cached-resource";
import { useMeasuredExpenditure } from "@/lib/use-measured-expenditure";
import { formatNumberEs } from "@/lib/core/dates";
import { calculateCalorieRecommendation } from "@/lib/core/calories";
import { calculateProteinRecommendation } from "@/lib/core/protein";
import type { CalorieProfile, Goal, WeightDTO } from "@/lib/core/types";
import { formatTemplate, t } from "@/i18n";
import { cn } from "@/lib/utils";

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

/**
 * Status text for an intake vs a recommended range: "en rango", or how much
 * is missing to reach the goal as a small range ("te faltan X–Y") / exceeded
 * past the goal ("te pasaste de X–Y").
 */
function intakeStatus(
  current: number,
  rangeMin: number,
  rangeMax: number,
  missingTemplate: string,
  exceededTemplate: string,
  inRangeLabel: string,
): { statusLabel: string; statusColor: string } {
  if (current >= rangeMin && current <= rangeMax) {
    return { statusLabel: inRangeLabel, statusColor: "text-green-600 dark:text-green-400" };
  }
  if (current < rangeMin) {
    const missingMin = formatNumberEs(Math.round(rangeMin - current), 0);
    const missingMax = formatNumberEs(Math.round(rangeMax - current), 0);
    return {
      statusLabel: formatTemplate(missingTemplate, { min: missingMin, max: missingMax }),
      statusColor: "text-yellow-600 dark:text-yellow-400",
    };
  }
  const exceeded = formatNumberEs(Math.round(current - rangeMax), 0);
  return {
    statusLabel: formatTemplate(exceededTemplate, { min: exceeded }),
    statusColor: "text-orange-600 dark:text-orange-400",
  };
}

/**
 * Progress towards the daily target. The outlined track is what is left, the
 * solid fill is what was eaten (orange once past the target), and the two
 * markers crossing the bar are the target range. The numbers above say it in
 * words: eaten / target.
 */
function IntakeBar({
  current,
  rangeMin,
  rangeMax,
  unit,
  statusLabel,
  statusColor,
}: {
  current: number;
  rangeMin: number;
  rangeMax: number;
  unit: string;
  statusLabel: string;
  statusColor: string;
}) {
  // Leave room past the target so going over is visible on the bar.
  const scaleMax = rangeMax > 0 ? rangeMax * 1.1 : 1;
  const toPct = (v: number) => Math.min(Math.max((v / scaleMax) * 100, 0), 100);
  const over = current > rangeMax;
  return (
    <div className="space-y-1.5">
      <div className="flex items-baseline justify-between gap-2 text-xs">
        <span className={statusColor}>{statusLabel}</span>
        <span className="shrink-0 tabular-nums text-muted-foreground">
          <span className="font-semibold text-foreground">{formatNumberEs(Math.round(current), 0)}</span>
          {" / "}
          {formatNumberEs(rangeMin, 0)}–{formatNumberEs(rangeMax, 0)} {unit}
        </span>
      </div>
      <div
        className="relative h-4"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={Math.round(rangeMax)}
        aria-valuenow={Math.round(current)}
        aria-label={statusLabel}
      >
        <div className="absolute inset-x-0 top-1/2 h-2.5 -translate-y-1/2 overflow-hidden rounded-full border border-border bg-muted">
          <div
            data-testid="intake-fill"
            className={cn("h-full rounded-full transition-all", over ? "bg-tertiary" : "bg-primary")}
            style={{ width: `${toPct(current)}%` }}
          />
        </div>
        {[rangeMin, rangeMax].map((mark) => (
          <div
            key={mark}
            className="absolute inset-y-0 w-0.5 -translate-x-1/2 rounded-full bg-foreground/80"
            style={{ left: `${toPct(mark)}%` }}
          />
        ))}
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
  const weightsRes = useCachedResource<WeightDTO[]>("weights", () => api.listWeights());
  const sessionRes = useCachedResource<Awaited<ReturnType<typeof api.session>>>("session", () =>
    api.session(),
  );

  const weights = weightsRes.data ?? null;
  const calorieProfile = sessionRes.data?.calorieProfile ?? emptyProfile();

  const latestWeight = weights?.at(-1) ?? null;
  // Body fat is not logged at every weigh-in: use the most recent one there is.
  const bodyFatPct = weights?.findLast((w) => w.bodyFatPct !== null)?.bodyFatPct ?? null;
  const measured = useMeasuredExpenditure();

  const calorieRec = useMemo(
    () => (latestWeight ? calculateCalorieRecommendation(calorieProfile, latestWeight.weightKg, new Date().getFullYear()) : null),
    [latestWeight, calorieProfile],
  );

  const proteinRec = useMemo(
    () =>
      latestWeight && calorieProfile.calorieGoal
        ? calculateProteinRecommendation(latestWeight.weightKg, calorieProfile.calorieGoal, bodyFatPct)
        : null,
    [latestWeight, calorieProfile.calorieGoal, bodyFatPct],
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
          <section className="flex flex-col gap-2.5">
            <div className="flex items-center gap-1.5 text-sm font-medium">
              <FlameIcon className="size-4" /> {t.calorias.recommendationTitle}
              <span className="text-xs font-normal text-muted-foreground">
                · {GOAL_LABELS[calorieRec.goal]}
              </span>
            </div>
            <p className="text-2xl font-semibold tabular-nums">
              {formatNumberEs(calorieRec.targetMin)} – {formatNumberEs(calorieRec.targetMax)}{" "}
              <span className="text-sm font-normal text-muted-foreground">{t.calorias.perDay}</span>
            </p>
            <p className="text-xs text-muted-foreground">
              {formatTemplate(t.calorias.estimatedAverageValue, {
                n: formatNumberEs(calorieRec.target),
              })}{" "}
              {t.calorias.perDay}
            </p>
            {/* Where the target comes from (same breakdown as Ajustes → Perfil). */}
            <div className="space-y-0.5 text-xs text-muted-foreground">
              <p>
                {t.calorias.bmr}:{" "}
                <span className="whitespace-nowrap">{formatNumberEs(calorieRec.bmr)} {t.calorias.perDay}</span>
              </p>
              <p>
                {t.calorias.tdee}:{" "}
                <span className="whitespace-nowrap">{formatNumberEs(calorieRec.tdee)} {t.calorias.perDay}</span>
              </p>
              {measured ? (
                <p>
                  {t.calorias.measuredTdee}:{" "}
                  <span className="whitespace-nowrap">
                    {formatTemplate(t.calorias.measuredTdeeValue, {
                      n: formatNumberEs(measured.tdee),
                      margin: formatNumberEs(measured.margin),
                    })}
                  </span>
                </p>
              ) : null}
            </div>
            {/* mt-auto: both columns' bars sit at the bottom, aligned side by side on desktop. */}
            <div className="mt-auto pt-1">
              <IntakeBar
                current={dailyCalories}
                rangeMin={calorieRec.targetMin}
                rangeMax={calorieRec.targetMax}
                unit={t.hoy.kcalUnit}
                {...intakeStatus(
                  dailyCalories,
                  calorieRec.targetMin,
                  calorieRec.targetMax,
                  t.calorias.missingCalories,
                  t.calorias.exceededCalories,
                  t.calorias.inRange,
                )}
              />
            </div>
          </section>
        ) : null}

        {proteinRec ? (
          <section className="flex flex-col gap-2.5">
            <div className="flex items-center gap-1.5 text-sm font-medium">
              <DumbbellIcon className="size-4" /> {t.protein.recommendationTitle}
              <span className="text-xs font-normal text-muted-foreground">
                · {GOAL_LABELS[proteinRec.goal]}
              </span>
            </div>
            <p className="text-2xl font-semibold tabular-nums">
              {proteinRec.range.min} – {proteinRec.range.max}{" "}
              <span className="text-sm font-normal text-muted-foreground">g/día</span>
            </p>
            <p className="text-xs text-muted-foreground">
              {formatTemplate(t.calorias.estimatedAverageValue, { n: proteinRec.target })} g/día
            </p>
            <p className="text-xs text-muted-foreground">
              {proteinRec.basis === "leanMass"
                ? formatTemplate(t.protein.perKgLeanMass, {
                    min: formatNumberEs(proteinRec.perKg.min, 1),
                    max: formatNumberEs(proteinRec.perKg.max, 1),
                    kg: formatNumberEs(proteinRec.basisKg, 1),
                  })
                : formatTemplate(t.protein.perKg, {
                    min: formatNumberEs(proteinRec.perKg.min, 1),
                    max: formatNumberEs(proteinRec.perKg.max, 1),
                  })}
            </p>
            <div className="mt-auto pt-1">
              <IntakeBar
                current={dailyProtein}
                rangeMin={proteinRec.range.min}
                rangeMax={proteinRec.range.max}
                unit="g"
                {...intakeStatus(
                  dailyProtein,
                  proteinRec.range.min,
                  proteinRec.range.max,
                  t.protein.missingProtein,
                  t.protein.exceededProtein,
                  t.protein.inRange,
                )}
              />
            </div>
          </section>
        ) : null}
      </CardContent>
    </Card>
  );
}
