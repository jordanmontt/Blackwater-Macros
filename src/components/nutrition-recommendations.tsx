"use client";

import { useState, type ReactNode } from "react";
import Link from "next/link";
import { ChevronRightIcon, DumbbellIcon, FlameIcon, InfoIcon } from "lucide-react";
import { formatNumber } from "@/i18n/format";
import { Card, CardContent } from "@/components/ui/card";
import { Sheet } from "@/components/ui/sheet";
import { ExpenditureCalculationButton, ExpenditureNotices, formulaInputs } from "@/components/expenditure-check";
import { useExpenditureCheck } from "@/lib/use-measured-expenditure";
import { useRecommendations } from "@/lib/use-recommendations";
import { todayKey } from "@/lib/core/dates";
import type { CalorieRecommendation, ExpenditureEstimate, Goal, ProteinRecommendation } from "@/lib/core/types";
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
    return { statusLabel: inRangeLabel, statusColor: "text-green-800 dark:text-green-400", overIsFine };
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
  showStatus = true,
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
  /** False: the status line under the bar is left out (it stays the bar's name for screen readers). */
  showStatus?: boolean;
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
      {showStatus ? <p className={cn("text-xs", statusColor)}>{statusLabel}</p> : null}
    </div>
  );
}

/**
 * One total with no target to compare with (carbs, fat; or anything before there is a
 * weight): the label above, the value under it, as the bars' titles and big numbers.
 * Laid out in a grid of equal columns, so the values line up whatever the labels.
 */
function PlainTotal({ label, value, unit }: { label: string; value: string; unit: string }) {
  return (
    <div data-testid="plain-total">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-lg font-semibold tabular-nums">
        {value} <span className="text-xs font-normal text-muted-foreground">{unit}</span>
      </p>
    </div>
  );
}

/**
 * Comidas' summary of the day: calories and protein against their ranges (a bar
 * each, with what is missing or over in words), carbs and fat as plain totals,
 * and the expenditure check's notices. Where the ranges come from (BMR, TDEE,
 * measured expenditure, protein per kg) is one tap away in «Cómo se calculan», in
 * the card's header («Objetivos de hoy») so it reads as being about every target,
 * not the totals next to it. The day's meals start on the first screen: message
 * first, then the user's numbers, then the methodology. A past `day` keeps the
 * targets it had.
 */
export function DaySummaryCard({
  day,
  totals,
  nothingLogged = false,
}: {
  /** The day shown (today when omitted). */
  day?: string;
  totals: { calories: number; protein: number; carbs: number; fat: number };
  /** No meals on that day. On a past day the bars then say so in gray, not what is «missing». */
  nothingLogged?: boolean;
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
  // An unlogged past day is not a day without food: no «te faltan 2153–2353 kcal» in orange.
  const unlogged = !isToday && nothingLogged;
  const quiet = { statusLabel: t.hoy.nothingLogged, statusColor: "text-muted-foreground", overIsFine: true };

  return (
    <Card className="py-3">
      <CardContent className="space-y-3 px-4">
        {hasTargets ? (
          <div className="flex flex-wrap items-center justify-between gap-x-3">
            <h2 className="text-sm font-semibold">{isToday ? t.hoy.targetsToday : t.hoy.targetsThatDay}</h2>
            <button
              type="button"
              onClick={() => setWhyOpen(true)}
              className="-my-3 -mr-1.5 inline-flex min-h-11 items-center gap-1 px-1.5 text-xs font-medium text-primary hover:underline"
            >
              <InfoIcon className="size-3.5" />
              {t.hoy.howCalculated}
            </button>
          </div>
        ) : null}
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
            {...(unlogged
              ? quiet
              : intakeStatus(
                  totals.calories,
                  calorieRec.targetMin,
                  calorieRec.targetMax,
                  t.calorias.missingCalories,
                  t.calorias.exceededCalories,
                  t.calorias.inRange,
                ))}
          />
        ) : null}
        {latestWeight && proteinRec ? (
          <IntakeBar
            title={t.hoy.protein}
            current={totals.protein}
            rangeMin={proteinRec.range.min}
            rangeMax={proteinRec.range.max}
            eatenLabel={t.hoy.gEaten}
            {...(unlogged
              ? quiet
              : intakeStatus(
                  totals.protein,
                  proteinRec.range.min,
                  proteinRec.range.max,
                  t.protein.missingProtein,
                  t.protein.aboveRangeBy,
                  t.protein.inRange,
                  true,
                ))}
            // Said once, under calories.
            showStatus={!(unlogged && calorieRec)}
          />
        ) : null}
        {isToday && latestWeight && calorieRec ? (
          <ExpenditureNotices
            check={check}
            inputs={formulaInputs(profile, latestWeight.weightKg, new Date().getFullYear())}
          />
        ) : null}
        {/* Two equal columns (2 × 2 without targets): the values line up and do not move with their length. */}
        <div className={cn("grid grid-cols-2 gap-x-3 gap-y-2", hasTargets && "border-t pt-2")} data-testid="plain-totals">
          {hasTargets ? null : (
            <>
              <PlainTotal label={t.hoy.calories} value={formatNumber(totals.calories)} unit={t.hoy.kcalUnit} />
              <PlainTotal label={t.hoy.protein} value={formatNumber(totals.protein, 1)} unit={t.hoy.proteinUnit} />
            </>
          )}
          <PlainTotal label={t.hoy.carbs} value={formatNumber(totals.carbs, 1)} unit={t.hoy.gramUnit} />
          <PlainTotal label={t.hoy.fat} value={formatNumber(totals.fat, 1)} unit={t.hoy.gramUnit} />
        </div>
        {latestWeight ? null : <p className="text-xs text-muted-foreground">{t.protein.noWeight}</p>}
        {differsFromToday ? (
          <p className="text-xs text-muted-foreground" data-testid="past-day-targets">
            {t.calorias.pastDayTargets}
          </p>
        ) : null}
      </CardContent>
      {hasTargets ? (
        <Sheet open={whyOpen} onOpenChange={setWhyOpen} title={t.hoy.howCalculatedTitle}>
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
                {/* The chain, inputs to range, with the user's numbers; the formulas behind each input are in «Ver el cálculo». */}
                <div className="mt-2 space-y-1 rounded-lg border px-3 py-2 text-xs tabular-nums" data-testid="target-chain">
                  <p>{expenditureChain(calorieRec, measured)}</p>
                  <p>{goalChain(calorieRec)}</p>
                </div>
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
            <div className="flex flex-wrap gap-x-5">
              {/* Today's calculation: on a past day it would not match the numbers above. */}
              {isToday && calorieRec ? (
                <ExpenditureCalculationButton className="inline-flex min-h-11 items-center gap-0.5 font-medium text-primary hover:underline">
                  {t.expenditureCheck.seeCalculation}
                </ExpenditureCalculationButton>
              ) : null}
              <Link href="/metodologia#calories" className="inline-flex min-h-11 items-center gap-0.5 font-medium text-primary hover:underline">
                {t.hoy.fullMethodology}
                <ChevronRightIcon className="size-4" />
              </Link>
            </div>
          </div>
        </Sheet>
      ) : null}
    </Card>
  );
}

/** «Gasto usado: 89 % tu gasto medido (2625) + 11 % el de tu perfil (2615) ≈ 2624 kcal/día» (`combineExpenditure`). */
function expenditureChain(rec: CalorieRecommendation, measured: ExpenditureEstimate | null): string {
  if (rec.dataShare > 0 && measured) {
    const data = Math.round(rec.dataShare * 100);
    return formatTemplate(t.calorias.chainBlend, {
      data: formatNumber(data),
      measured: formatNumber(measured.tdee),
      profile: formatNumber(100 - data),
      tdee: formatNumber(rec.tdee),
      expenditure: formatNumber(rec.expenditure),
    });
  }
  return formatTemplate(t.calorias.chainProfile, {
    bmr: formatNumber(rec.bmr),
    pal: formatNumber(rec.activityFactor, 2),
    tdee: formatNumber(rec.tdee),
  });
}

/** «Definición: −400 kcal (−500 a −300) → 2224 de media, rango 2124–2324», from the numbers shown (the BMR floor included). */
function goalChain(rec: CalorieRecommendation): string {
  const signed = (n: number) => (n > 0 ? `+${formatNumber(n)}` : n < 0 ? `−${formatNumber(-n)}` : "0");
  return formatTemplate(t.calorias.chainGoal, {
    goal: GOAL_LABELS[rec.goal],
    offset: signed(rec.target - rec.expenditure),
    offsetMin: signed(rec.targetMin - rec.expenditure),
    offsetMax: signed(rec.targetMax - rec.expenditure),
    target: formatNumber(rec.target),
    min: formatNumber(rec.targetMin),
    max: formatNumber(rec.targetMax),
  });
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
