"use client";

import { useRef, useState } from "react";
import Link from "next/link";
import { CheckCircle2Icon, InfoIcon, ScaleIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { formatNumber } from "@/i18n/format";
import { formatTemplate, t } from "@/i18n";
import { doesStrengthTraining } from "@/lib/core/calories";
import { KCAL_PER_KG, approximateGap, onlyWeighInsMissing, showsGapNotice } from "@/lib/core/expenditure";
import type { CalorieProfile, ExpenditureCheck, Gender, MissingExpenditureData } from "@/lib/core/types";
import { todayKey } from "@/lib/core/dates";
import { useExpenditureNotices } from "@/lib/expenditure-notices";
import { cn } from "@/lib/utils";

/**
 * The expenditure check (core `checkExpenditure`), shown in layers so anyone can
 * follow it as far as they want: one sentence on Today only when the gap is
 * clear, a «Why?» with the user's own numbers plugged into every formula, and
 * the full method in /metodologia#check. Same on Android (`ExpenditureCheck.kt`).
 */

/** What the profile's formula was fed, to write its equations with the user's numbers. */
export interface FormulaInputs {
  gender: Gender;
  weightKg: number;
  heightCm: number;
  age: number;
  gymDaysPerWeek: number;
  gymSessionMinutes: number;
  walkingMinutesPerDay: number;
}

export function formulaInputs(profile: CalorieProfile, weightKg: number | null, year: number): FormulaInputs | null {
  if (
    weightKg === null ||
    profile.gender === null ||
    profile.heightCm === null ||
    profile.birthYear === null ||
    profile.gymDaysPerWeek === null ||
    profile.gymSessionMinutes === null ||
    profile.walkingMinutesPerDay === null
  ) {
    return null;
  }
  return {
    gender: profile.gender,
    weightKg,
    heightCm: profile.heightCm,
    age: year - profile.birthYear,
    gymDaysPerWeek: profile.gymDaysPerWeek,
    gymSessionMinutes: profile.gymSessionMinutes,
    walkingMinutesPerDay: profile.walkingMinutesPerDay,
  };
}

const METHOD_HREF = "/metodologia#check";
const ACTIVITY_HREF = "/ajustes/perfil#gym-days";

type Measured = Extract<ExpenditureCheck, { status: "measured" }>;

/** With strength training, a «lower» gap can partly be muscle gained (/metodologia, recomposition). */
function trains(inputs: FormulaInputs | null): boolean {
  return inputs !== null && doesStrengthTraining(inputs);
}

function noticeText(check: Measured, inputs: FormulaInputs | null): string {
  const template = check.verdict === "higher" ? t.expenditureCheck.noticeHigher : t.expenditureCheck.noticeLower;
  const text = formatTemplate(template, { gap: formatNumber(approximateGap(check.gap)) });
  return check.verdict === "lower" && trains(inputs) ? `${text} ${t.expenditureCheck.noticeLowerTraining}` : text;
}

/**
 * Today, inside the calorie recommendation: the gap once it is confirmed, or
 * once, how to unlock the check when only the weigh-ins are missing. Nothing
 * otherwise: the full status lives in Perfil.
 */
export function ExpenditureNotices({
  check,
  inputs,
}: {
  check: ExpenditureCheck | null;
  inputs: FormulaInputs | null;
}) {
  const notices = useExpenditureNotices();
  const [open, setOpen] = useState(false);
  if (!notices.loaded || check === null) return null;

  const today = todayKey();
  let text: string;
  let onDismiss: () => void;
  if (check.status === "measured" && showsGapNotice(check, notices.gapDismissal, today)) {
    text = noticeText(check, inputs);
    const verdict = check.verdict === "higher" ? "higher" : "lower";
    onDismiss = () => notices.dismissGap({ verdict, date: today });
  } else if (onlyWeighInsMissing(check) && !notices.weighInHintDismissed) {
    text = t.expenditureCheck.weighInHint;
    onDismiss = notices.dismissWeighInHint;
  } else {
    return null;
  }

  return (
    <div role="status" className="flex gap-2 rounded-lg bg-muted/60 px-3 py-2 text-xs">
      {check.status === "measured" ? (
        <InfoIcon aria-hidden className="mt-0.5 size-3.5 shrink-0 text-primary" />
      ) : (
        <ScaleIcon aria-hidden className="mt-0.5 size-3.5 shrink-0 text-primary" />
      )}
      <div className="min-w-0 flex-1 space-y-1">
        <p className="text-foreground">{text}</p>
        <div className="flex gap-3">
          <button type="button" onClick={() => setOpen(true)} className="font-medium text-primary hover:underline">
            {t.expenditureCheck.why}
          </button>
          <button type="button" onClick={onDismiss} className="text-muted-foreground hover:text-foreground">
            {t.expenditureCheck.notNow}
          </button>
        </div>
      </div>
      <ExpenditureCheckDialog open={open} onOpenChange={setOpen} check={check} inputs={inputs} />
    </div>
  );
}

/**
 * Perfil, under the measured expenditure: always the full status. What is
 * missing, or the verdict, with the calculation one tap away.
 */
export function ExpenditureCheckStatus({
  check,
  inputs,
}: {
  check: ExpenditureCheck | null;
  inputs: FormulaInputs | null;
}) {
  const [open, setOpen] = useState(false);
  if (check === null) return null;

  if (check.status === "notEnoughData") {
    if (check.missing.length === 0) {
      return <p className="text-xs text-muted-foreground">{t.calorias.measuredTdeePending}</p>;
    }
    return (
      <div className="space-y-1 text-xs text-muted-foreground">
        <p>{t.expenditureCheck.notEnoughIntro}</p>
        <MissingList check={check} />
        <Link href={METHOD_HREF} className="inline-block font-medium text-primary hover:underline">
          {t.expenditureCheck.howItWorks}
        </Link>
      </div>
    );
  }

  const gap = check.verdict !== "matches";
  const text = !gap
    ? formatTemplate(t.expenditureCheck.statusMatches, {
        min: formatNumber(check.normalMin),
        max: formatNumber(check.normalMax),
      })
    : check.confirmed
      ? noticeText(check, inputs)
      : t.expenditureCheck.statusUnconfirmed;
  return (
    <div className="flex gap-2 text-xs">
      {gap ? (
        <InfoIcon aria-hidden className="mt-0.5 size-3.5 shrink-0 text-primary" />
      ) : (
        <CheckCircle2Icon aria-hidden className="mt-0.5 size-3.5 shrink-0 text-primary" />
      )}
      <p className="text-muted-foreground">
        <span className={cn(gap && check.confirmed && "text-foreground")}>{text}</span>{" "}
        <button type="button" onClick={() => setOpen(true)} className="font-medium text-primary hover:underline">
          {gap && check.confirmed ? t.expenditureCheck.why : t.expenditureCheck.seeCalculation}
        </button>
      </p>
      <ExpenditureCheckDialog open={open} onOpenChange={setOpen} check={check} inputs={inputs} />
    </div>
  );
}

function MissingList({ check }: { check: Extract<ExpenditureCheck, { status: "notEnoughData" }> }) {
  const line = (missing: MissingExpenditureData) => {
    switch (missing) {
      case "meals":
        return formatTemplate(t.expenditureCheck.missingMeals, { days: check.data.loggedDays });
      case "weighIns":
        return formatTemplate(t.expenditureCheck.missingWeighIns, { n: check.data.weighInDays });
      case "weightSpan":
        return formatTemplate(t.expenditureCheck.missingWeightSpan, { days: check.data.weightSpanDays });
      case "margin":
        return formatTemplate(t.expenditureCheck.missingMargin, { margin: formatNumber(check.data.margin ?? 0) });
    }
  };
  return (
    <ul className="list-disc space-y-1 pl-4">
      {check.missing.map((missing) => (
        <li key={missing}>{line(missing)}</li>
      ))}
    </ul>
  );
}

function ExpenditureCheckDialog({
  open,
  onOpenChange,
  check,
  inputs,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  check: ExpenditureCheck;
  inputs: FormulaInputs | null;
}) {
  // Start at the top: by default focus would jump to the first link, at the very bottom.
  const top = useRef<HTMLDivElement>(null);
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent initialFocus={top} className="max-h-[85dvh] overflow-y-auto sm:max-w-lg">
        <div ref={top} tabIndex={-1} className="sr-only" />
        {check.status === "notEnoughData" ? (
          <>
            <DialogHeader>
              <DialogTitle className="pr-6">{t.metodologia.checkTitle}</DialogTitle>
              <DialogDescription>{t.expenditureCheck.notEnoughIntro}</DialogDescription>
            </DialogHeader>
            <div className="text-xs text-muted-foreground">
              <MissingList check={check} />
            </div>
            <DialogLinks />
          </>
        ) : (
          <ExpenditureCheckDetails check={check} inputs={inputs} />
        )}
      </DialogContent>
    </Dialog>
  );
}

function DialogLinks({ showActivity = false }: { showActivity?: boolean }) {
  return (
    <div className="flex flex-wrap gap-2">
      {showActivity ? (
        <Button size="sm" nativeButton={false} render={<Link href={ACTIVITY_HREF} />}>
          {t.expenditureCheck.reviewActivity}
        </Button>
      ) : null}
      <Button size="sm" variant="outline" nativeButton={false} render={<Link href={METHOD_HREF} />}>
        {t.expenditureCheck.howItWorks}
      </Button>
    </div>
  );
}

/** «Why?»: every step with the user's numbers, so it can be checked by hand. */
function ExpenditureCheckDetails({ check, inputs }: { check: Measured; inputs: FormulaInputs | null }) {
  const { estimate } = check;
  const low = estimate.tdee - estimate.margin;
  const high = estimate.tdee + estimate.margin;
  const title = !check.confirmed
    ? t.expenditureCheck.titleUnconfirmed
    : check.verdict === "higher"
      ? t.expenditureCheck.titleHigher
      : check.verdict === "lower"
        ? t.expenditureCheck.titleLower
        : t.expenditureCheck.titleMatches;
  const range = { low: formatNumber(low), high: formatNumber(high), min: formatNumber(check.normalMin), max: formatNumber(check.normalMax) };
  const conclusion = formatTemplate(
    !check.confirmed
      ? t.expenditureCheck.conclusionUnconfirmed
      : check.verdict === "higher"
        ? t.expenditureCheck.conclusionHigher
        : check.verdict === "lower"
          ? t.expenditureCheck.conclusionLower
          : t.expenditureCheck.conclusionMatches,
    range,
  );
  const reasons =
    check.verdict === "higher"
      ? [t.expenditureCheck.higherReason1, t.expenditureCheck.higherReason2, t.expenditureCheck.higherReason3, t.expenditureCheck.higherReason4]
      : check.verdict === "lower"
        ? [
            t.expenditureCheck.lowerReason1,
            t.expenditureCheck.lowerReason2,
            t.expenditureCheck.lowerReason3,
            t.expenditureCheck.lowerReason4,
            ...(trains(inputs) ? [t.expenditureCheck.lowerReasonTraining] : []),
          ]
        : [];
  const rate = estimate.weightChangePerWeek;
  const signedRate = `${rate > 0 ? "+" : ""}${formatNumber(rate, 2)}`;
  // tdee = round(BMR × PAL) with the unrounded factors: «≈» when the rounded ones differ by a few kcal.
  const product = Math.round(check.bmr * check.activityFactor);

  return (
    <div className="space-y-4">
      <DialogHeader>
        <DialogTitle className="pr-6">{title}</DialogTitle>
        {check.verdict !== "matches" && check.confirmed ? <DialogDescription>{noticeText(check, inputs)}</DialogDescription> : null}
      </DialogHeader>

      <Step title={t.expenditureCheck.profileStep}>
        {inputs ? (
          <>
            <p>{t.expenditureCheck.bmrLabel}</p>
            <Equation>
              10 × {formatNumber(inputs.weightKg, 1)} + {formatNumber(6.25, 2)} × {formatNumber(inputs.heightCm, 1)} − 5 ×{" "}
              {inputs.age}{" "}
              {inputs.gender === "male" ? "+ 5" : "− 161"} = {formatNumber(check.bmr)} kcal
            </Equation>
            <p>
              {formatTemplate(t.expenditureCheck.activityLabel, {
                gymDays: inputs.gymDaysPerWeek,
                gymMinutes: inputs.gymSessionMinutes,
                walking: inputs.walkingMinutesPerDay,
              })}
            </p>
            <Equation>{palEquation(inputs, check.activityFactor)}</Equation>
          </>
        ) : null}
        <p>{t.expenditureCheck.formulaLabel}</p>
        <Equation>
          {formatNumber(check.bmr)} × {formatNumber(check.activityFactor, 2)} {product === check.formulaTdee ? "=" : "≈"}{" "}
          {formatNumber(check.formulaTdee)} {t.calorias.perDay}
        </Equation>
        <p>
          {formatTemplate(t.expenditureCheck.normalRange, {
            min: formatNumber(check.normalMin),
            max: formatNumber(check.normalMax),
          })}
        </p>
      </Step>

      <Step title={t.expenditureCheck.dataStep}>
        <ul className="list-disc space-y-0.5 pl-4">
          <li>
            {formatTemplate(t.expenditureCheck.eaten, {
              intake: formatNumber(estimate.avgIntake),
              days: estimate.loggedDays,
            })}
          </li>
          <li>{formatTemplate(t.expenditureCheck.trend, { rate: signedRate, n: estimate.weighIns })}</li>
        </ul>
        <p>{t.expenditureCheck.measuredLabel}</p>
        <Equation>
          {formatNumber(estimate.avgIntake)} − ({signedRate} ÷ 7 × {formatNumber(KCAL_PER_KG)}) ≈ {formatNumber(estimate.tdee)} ±{" "}
          {formatNumber(estimate.margin)} {t.calorias.perDay}
        </Equation>
        <p>{t.expenditureCheck.marginNote}</p>
      </Step>

      <Step title={t.expenditureCheck.comparisonStep}>
        <RangeComparison check={check} low={low} high={high} />
        <p className="text-foreground">{conclusion}</p>
        {check.verdict !== "matches" ? (
          <p>
            {formatTemplate(t.expenditureCheck.implied, {
              implied: formatNumber(check.impliedActivityFactor, 2),
              pal: formatNumber(check.activityFactor, 2),
            })}
          </p>
        ) : null}
      </Step>

      {reasons.length > 0 ? (
        <Step title={t.expenditureCheck.reasonsTitle}>
          <ol className="list-decimal space-y-1 pl-4">
            {reasons.map((reason) => (
              <li key={reason}>{reason}</li>
            ))}
          </ol>
        </Step>
      ) : null}

      <p className="text-xs text-muted-foreground">{t.expenditureCheck.targetNote}</p>
      <DialogLinks showActivity={check.verdict !== "matches"} />
    </div>
  );
}

/** PAL = ((1440 − gym − walking) × 1.4 + gym × 4.0 + walking × 3.5) / 1440, with the user's minutes. */
function palEquation(inputs: FormulaInputs, activityFactor: number): string {
  const gym = (inputs.gymDaysPerWeek * inputs.gymSessionMinutes) / 7;
  const walking = inputs.walkingMinutesPerDay;
  return (
    `((1440 − ${formatNumber(gym, 1)} − ${formatNumber(walking)}) × ${formatNumber(1.4, 1)} + ` +
    `${formatNumber(gym, 1)} × ${formatNumber(4, 1)} + ${formatNumber(walking)} × ${formatNumber(3.5, 1)}) / 1440 = ` +
    formatNumber(activityFactor, 2)
  );
}

function Step({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-1.5 text-xs text-muted-foreground">
      <h3 className="text-sm font-medium text-foreground">{title}</h3>
      {children}
    </section>
  );
}

function Equation({ children }: { children: React.ReactNode }) {
  return (
    <p className="rounded-md border bg-muted/40 px-2 py-1.5 font-mono text-[0.7rem] leading-relaxed text-foreground">
      {children}
    </p>
  );
}

/**
 * The comparison drawn: the shaded band is what is normal for the profile
 * (formula ± 10 %), the bar with a dot is the measured value and its 95 % margin.
 * If the bar sits entirely outside the band, the gap is clear.
 */
function RangeComparison({ check, low, high }: { check: Measured; low: number; high: number }) {
  const from = Math.min(check.normalMin, low);
  const to = Math.max(check.normalMax, high);
  const pad = (to - from) * 0.08;
  const pct = (v: number) => ((v - (from - pad)) / (to - from + 2 * pad)) * 100;
  const label = `${t.expenditureCheck.normalLegend}: ${formatNumber(check.normalMin)}–${formatNumber(check.normalMax)}. ${t.expenditureCheck.measuredLegend}: ${formatNumber(low)}–${formatNumber(high)}.`;
  return (
    <div className="space-y-2">
      <div role="img" aria-label={label} className="relative h-9">
        <div className="absolute inset-x-0 top-1/2 h-px bg-border" />
        <div
          className="absolute inset-y-0 rounded-md bg-primary/25 ring-1 ring-primary/60"
          style={{ left: `${pct(check.normalMin)}%`, width: `${pct(check.normalMax) - pct(check.normalMin)}%` }}
        />
        <div className="absolute inset-y-1 w-0.5 bg-primary" style={{ left: `${pct(check.formulaTdee)}%` }} />
        <div
          className="absolute top-1/2 h-1.5 -translate-y-1/2 rounded-full bg-foreground/70"
          style={{ left: `${pct(low)}%`, width: `${pct(high) - pct(low)}%` }}
        />
        <div
          className="absolute top-1/2 size-3 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-background bg-foreground"
          style={{ left: `${pct(check.estimate.tdee)}%` }}
        />
      </div>
      <ul className="space-y-0.5">
        <li className="flex items-center gap-2">
          <span aria-hidden className="h-2.5 w-4 rounded-sm bg-primary/25 ring-1 ring-primary/60" />
          {t.expenditureCheck.normalLegend}: {formatNumber(check.normalMin)}–{formatNumber(check.normalMax)} {t.calorias.perDay}
        </li>
        <li className="flex items-center gap-2">
          <span aria-hidden className="flex h-2.5 w-4 items-center">
            <span className="h-1.5 w-full rounded-full bg-foreground/70" />
          </span>
          {t.expenditureCheck.measuredLegend}: {formatNumber(low)}–{formatNumber(high)} {t.calorias.perDay}
        </li>
      </ul>
    </div>
  );
}
