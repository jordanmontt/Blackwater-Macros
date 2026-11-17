"use client";

import { useState, type ReactNode } from "react";
import Link from "next/link";
import { ChevronRightIcon, DumbbellIcon, FlameIcon } from "lucide-react";
import { formatNumber } from "@/i18n/format";
import { Card, CardContent } from "@/components/ui/card";
import { Sheet } from "@/components/ui/sheet";
import { ExpenditureNotices, formulaInputs } from "@/components/expenditure-check";
import { useExpenditureCheck } from "@/lib/use-measured-expenditure";
import { useRecommendations } from "@/lib/use-recommendations";
import { todayKey } from "@/lib/core/dates";
import type { CalorieRecommendation, Goal, ProteinRecommendation } from "@/lib/core/types";
import { formatTemplate, t } from "@/i18n";
import { cn } from "@/lib/utils";

const GOAL_LABELS: Record<Goal, string> = {
  cut: t.calorias.goalCut,
  maintain: t.calorias.goalMaintain,
  surplus: t.calorias.goalSurplus,
};

/**
 * Status text for an intake vs a recommended range: "en rango", or how much
 * is missing to reach the goal as a small range ("te faltan X–Y") / exceeded
 * past the goal ("te pasaste de X"). `overIsFine` (protein): going over is
 * said in a neutral colour, because eating more is not a problem.
 */
function intakeStatus(
  current: number,
  rangeMin: number,
  rangeMax: number,
  missingTemplate: string,
  exceededTemplate: string,
  inRangeLabel: string,
  overIsFine = false,
): { statusLabel: string; statusColor: string; overIsFine: boolean } {
  if (current >= rangeMin && current <= rangeMax) {
    return { statusLabel: inRangeLabel, statusColor: "text-green-700 dark:text-green-400", overIsFine };
  }
  if (current < rangeMin) {
    const missingMin = formatNumber(Math.round(rangeMin - current), 0);
    const missingMax = formatNumber(Math.round(rangeMax - current), 0);
    return {
      statusLabel: formatTemplate(missingTemplate, { min: missingMin, max: missingMax }),
      statusColor: "text-amber-700 dark:text-yellow-400",
      overIsFine,
    };
  }
  const exceeded = formatNumber(Math.round(current - rangeMax), 0);
  return {
    statusLabel: formatTemplate(exceededTemplate, { min: exceeded }),
    statusColor: overIsFine ? "text-muted-foreground" : "text-orange-700 dark:text-orange-400",
    overIsFine,
  };
}

/**
 * Progress towards the daily target. The outlined track is what is left, the
 * solid fill is what was eaten (orange once past the target, unless
 * `overIsFine`), and the two
 * markers crossing the bar are the target range. Above it, what was eaten is the
 * big number («2707 kcal consumidas», also when over) with the range next to it.
 */
function IntakeBar({
  title,
  eatenLabel,
  current,
  rangeMin,
  rangeMax,
  statusLabel,
  statusColor,
  overIsFine,
}: {
  /** «Calorías · Definición»: what is measured, left of the numbers. */
  title: ReactNode;
  /** «kcal consumidas», after the big number. */
  eatenLabel: string;
  current: number;
  rangeMin: number;
  rangeMax: number;
  statusLabel: string;
  statusColor: string;
  overIsFine: boolean;
}) {
  // Leave room past the target so going over is visible on the bar.
  const scaleMax = rangeMax > 0 ? rangeMax * 1.1 : 1;
  const toPct = (v: number) => Math.min(Math.max((v / scaleMax) * 100, 0), 100);
  const over = current > rangeMax && !overIsFine;
  return (
    <div className="space-y-1">
      <p className="text-xs text-muted-foreground">{title}</p>
      {/* What was eaten is the big number: the status under the bar only says how far from the range. */}
      <div className="flex items-baseline justify-between gap-2" data-testid="intake-total">
        <span className="text-2xl font-semibold tabular-nums">
          {formatNumber(Math.round(current), 0)}{" "}
          <span className="text-sm font-normal text-muted-foreground">{eatenLabel}</span>
        </span>
        <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
          {formatTemplate(t.hoy.ofRange, { min: formatNumber(rangeMin, 0), max: formatNumber(rangeMax, 0) })}
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
      <p className={cn("text-xs", statusColor)}>{statusLabel}</p>
    </div>
  );
}

/** One total with no target to compare with (carbs, fat; or anything before there is a weight). */
function PlainTotal({ label, value, unit }: { label: string; value: string; unit: string }) {
  return (
    <span className="whitespace-nowrap">
      {label} <span className="font-semibold tabular-nums text-foreground">{value}</span> {unit}
    </span>
  );
}

/** A label and its value on one line of «¿Por qué estos números?» (no colon: each language punctuates its own). */
function DetailRow({ label, children }: { label: ReactNode; children: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-3 py-1.5">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="shrink-0 text-right tabular-nums">{children}</dd>
    </div>
  );
}

/**
 * Comidas' summary of the day: calories and protein against their ranges (a bar
 * each, with what is missing or over in words), carbs and fat as plain totals,
 * and the expenditure check's notices. Where the ranges come from (BMR, TDEE,
 * measured expenditure, protein per kg) is one tap away in «¿Por qué estos
 * números?», so the day's meals start on the first screen: message first, then
 * the user's numbers, then the methodology. A past `day` keeps the targets it had.
 */
export function DaySummaryCard({
  day,
  totals,
}: {
  /** The day shown (today when omitted). */
  day?: string;
  totals: { calories: number; protein: number; carbs: number; fat: number };
}) {
  const [whyOpen, setWhyOpen] = useState(false);
  const today = useRecommendations();
  const { profile, latestWeight } = today;
  const { calorieRec, proteinRec, measuredOn } = useRecommendations(day);
  const isToday = day === undefined || day === todayKey();
  // The measurement that day's target uses (taken on that week's Monday).
  const measured = measuredOn?.(day ?? todayKey()) ?? null;
  // The check judges the current profile, whatever day is shown.
  const check = useExpenditureCheck(today.calorieRec);
  const differsFromToday =
    !isToday &&
    (calorieRec?.targetMin !== today.calorieRec?.targetMin ||
      calorieRec?.targetMax !== today.calorieRec?.targetMax ||
      proteinRec?.range.min !== today.proteinRec?.range.min ||
      proteinRec?.range.max !== today.proteinRec?.range.max);
  const hasTargets = latestWeight !== null && (calorieRec !== null || proteinRec !== null);

  return (
    <Card className="py-3">
      <CardContent className="space-y-3 px-4">
        {latestWeight && calorieRec ? (
          <IntakeBar
            title={
              <>
                {t.hoy.calories}
                <span className="font-normal text-muted-foreground"> · {GOAL_LABELS[calorieRec.goal]}</span>
              </>
            }
            current={totals.calories}
            rangeMin={calorieRec.targetMin}
            rangeMax={calorieRec.targetMax}
            eatenLabel={t.hoy.kcalEaten}
            {...intakeStatus(
              totals.calories,
              calorieRec.targetMin,
              calorieRec.targetMax,
              t.calorias.missingCalories,
              t.calorias.exceededCalories,
              t.calorias.inRange,
            )}
          />
        ) : null}
        {latestWeight && proteinRec ? (
          <IntakeBar
            title={t.hoy.protein}
            current={totals.protein}
            rangeMin={proteinRec.range.min}
            rangeMax={proteinRec.range.max}
            eatenLabel={t.hoy.gEaten}
            {...intakeStatus(
              totals.protein,
              proteinRec.range.min,
              proteinRec.range.max,
              t.protein.missingProtein,
              t.protein.aboveRangeBy,
              t.protein.inRange,
              true,
            )}
          />
        ) : null}
        {isToday && latestWeight && calorieRec ? (
          <ExpenditureNotices
            check={check}
            inputs={formulaInputs(profile, latestWeight.weightKg, new Date().getFullYear())}
          />
        ) : null}
        <div
          className={cn(
            "flex flex-wrap items-center justify-between gap-x-3 gap-y-1 text-xs text-muted-foreground",
            hasTargets && "border-t pt-2",
          )}
        >
          {/* With targets, carbs and fat are stacked so the layout does not change with the numbers. */}
          <span className={cn("flex gap-x-3 gap-y-1", hasTargets ? "flex-col" : "flex-wrap")}>
            {hasTargets ? null : (
              <>
                <PlainTotal label={t.hoy.calories} value={formatNumber(totals.calories)} unit={t.hoy.kcalUnit} />
                <PlainTotal label={t.hoy.protein} value={formatNumber(totals.protein, 1)} unit={t.hoy.proteinUnit} />
              </>
            )}
            <PlainTotal label={t.hoy.carbs} value={formatNumber(totals.carbs, 1)} unit={t.hoy.gramUnit} />
            <PlainTotal label={t.hoy.fat} value={formatNumber(totals.fat, 1)} unit={t.hoy.gramUnit} />
          </span>
          {hasTargets ? (
            <button
              type="button"
              onClick={() => setWhyOpen(true)}
              className="-my-2 inline-flex min-h-11 items-center gap-0.5 font-medium text-primary hover:underline"
            >
              {t.hoy.whyNumbers}
              <ChevronRightIcon className="size-3.5" />
            </button>
          ) : null}
        </div>
        {latestWeight ? null : <p className="text-xs text-muted-foreground">{t.protein.noWeight}</p>}
        {differsFromToday ? (
          <p className="text-xs text-muted-foreground" data-testid="past-day-targets">
            {t.calorias.pastDayTargets}
          </p>
        ) : null}
      </CardContent>
      {hasTargets ? (
        <Sheet open={whyOpen} onOpenChange={setWhyOpen} title={t.hoy.whyNumbers}>
          <div className="space-y-5 pb-2 text-sm">
            {calorieRec ? (
              <section className="space-y-1">
                <h3 className="flex items-center gap-1.5 font-medium">
                  <FlameIcon className="size-4" /> {t.calorias.recommendationTitle}
                  <span className="text-xs font-normal text-muted-foreground">· {GOAL_LABELS[calorieRec.goal]}</span>
                </h3>
                <p className="text-2xl font-semibold tabular-nums">
                  {formatNumber(calorieRec.targetMin)} – {formatNumber(calorieRec.targetMax)}{" "}
                  <span className="text-sm font-normal text-muted-foreground">{t.calorias.perDay}</span>
                </p>
                <p className="text-xs text-muted-foreground">
                  {formatTemplate(t.calorias.estimatedAverageValue, { n: formatNumber(calorieRec.target) })}{" "}
                  {t.calorias.perDay}
                </p>
                <dl className="mt-2 divide-y rounded-lg border px-3 text-xs">
                  {calorieRec.dataShare > 0 ? (
                    <DetailRow label={<TargetExpenditure rec={calorieRec} labelOnly />}>
                      {formatTemplate(t.calorias.targetExpenditureValue, {
                        n: formatNumber(calorieRec.expenditure),
                        share: formatNumber(Math.round(calorieRec.dataShare * 100)),
                      })}
                    </DetailRow>
                  ) : null}
                  {measured ? (
                    <DetailRow label={t.calorias.measuredTdee}>
                      {formatTemplate(t.calorias.measuredTdeeValue, {
                        n: formatNumber(measured.tdee),
                        margin: formatNumber(measured.margin),
                      })}
                    </DetailRow>
                  ) : null}
                  <DetailRow label={t.calorias.tdee}>
                    {formatNumber(calorieRec.tdee)} {t.calorias.perDay}
                  </DetailRow>
                  <DetailRow label={t.calorias.bmr}>
                    {formatNumber(calorieRec.bmr)} {t.calorias.perDay}
                  </DetailRow>
                </dl>
              </section>
            ) : null}
            {proteinRec ? (
              <section className="space-y-1">
                <h3 className="flex items-center gap-1.5 font-medium">
                  <DumbbellIcon className="size-4" /> {t.protein.recommendationTitle}
                </h3>
                <p className="text-2xl font-semibold tabular-nums">
                  {proteinRec.range.min} – {proteinRec.range.max}{" "}
                  <span className="text-sm font-normal text-muted-foreground">{t.calorias.gPerDay}</span>
                </p>
                <p className="text-xs text-muted-foreground">
                  {formatTemplate(t.calorias.estimatedAverageValue, { n: proteinRec.target })} {t.calorias.gPerDay}
                </p>
                <p className="text-xs text-muted-foreground">{proteinPerKgText(proteinRec)}</p>
              </section>
            ) : null}
            {differsFromToday ? <p className="text-xs text-muted-foreground">{t.calorias.pastDayTargets}</p> : null}
            <Link href="/metodologia" className="inline-flex min-h-11 items-center gap-0.5 font-medium text-primary hover:underline">
              {t.hoy.fullMethodology}
              <ChevronRightIcon className="size-4" />
            </Link>
          </div>
        </Sheet>
      ) : null}
    </Card>
  );
}

/**
 * «Gasto usado para tu objetivo: 2990 kcal/día (85 % de tus datos)»: what the
 * target is built on, once the measured expenditure counts (`combineExpenditure`).
 * It opens how (`/metodologia#adaptive`).
 */
export function TargetExpenditure({ rec, labelOnly = false }: { rec: CalorieRecommendation; labelOnly?: boolean }) {
  if (labelOnly) {
    return (
      <Link href="/metodologia#adaptive" className="underline-offset-2 hover:underline">
        {t.calorias.targetExpenditure}
      </Link>
    );
  }
  return (
    <p>
      <Link href="/metodologia#adaptive" className="hover:underline">
        {formatTemplate(t.common.labelValue, {
          label: t.calorias.targetExpenditure,
          value: formatTemplate(t.calorias.targetExpenditureValue, {
            n: formatNumber(rec.expenditure),
            share: formatNumber(Math.round(rec.dataShare * 100)),
          }),
        })}
      </Link>
    </p>
  );
}

/** «2,3–3,1 g/kg» of body weight, of lean mass («… de 43,9 kg de masa magra») or of the reference weight. */
export function proteinPerKgText(rec: ProteinRecommendation): string {
  const perKg = { min: formatNumber(rec.perKg.min, 1), max: formatNumber(rec.perKg.max, 1) };
  if (rec.basis === "bodyWeight") return formatTemplate(t.protein.perKg, perKg);
  return formatTemplate(rec.basis === "leanMass" ? t.protein.perKgLeanMass : t.protein.perKgReference, {
    ...perKg,
    kg: formatNumber(rec.basisKg, 1),
  });
}
