"use client";

import Link from "next/link";
import { useState } from "react";
import { InfoIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { FAT_SHARE_MAX, FAT_SHARE_MIN, type FatShareCheck } from "@/lib/core/progress";
import { formatNumber } from "@/i18n/format";
import { formatTemplate, t } from "@/i18n";
import { cn } from "@/lib/utils";

const METHOD_HREF = "/metodologia#fat";

/**
 * Progreso's «Promedio de macros»: one line when the period's fat is outside
 * 20–35 % of calories (`checkFatShare`), with «¿Por qué?» opening the user's
 * numbers and the reasons, then /metodologia#fat. Same on Android (`FatShare.kt`).
 */
export function FatShareNotice({ check }: { check: FatShareCheck }) {
  const [open, setOpen] = useState(false);
  const low = check.verdict === "low";
  return (
    <div className="flex gap-2 border-t pt-3 text-sm">
      <InfoIcon
        aria-hidden
        className={cn("mt-0.5 size-4 shrink-0", low ? "text-amber-700 dark:text-yellow-400" : "text-muted-foreground")}
      />
      <p>
        <span>{formatTemplate(low ? t.progreso.fatLow : t.progreso.fatHigh, { share: formatNumber(check.share) })}</span>{" "}
        <button type="button" className="text-primary underline-offset-2 hover:underline" onClick={() => setOpen(true)}>
          {t.expenditureCheck.why}
        </button>
      </p>
      <FatShareDialog open={open} onOpenChange={setOpen} check={check} />
    </div>
  );
}

function FatShareDialog({
  open,
  onOpenChange,
  check,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  check: FatShareCheck;
}) {
  const low = check.verdict === "low";
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="pr-6">{t.progreso.fatTitle}</DialogTitle>
          <DialogDescription>
            {formatTemplate(low ? t.progreso.fatLow : t.progreso.fatHigh, { share: formatNumber(check.share) })}
          </DialogDescription>
        </DialogHeader>
        <p className="rounded-md border bg-muted/40 px-2 py-1.5 font-mono text-[0.7rem] leading-relaxed">
          {formatTemplate(t.progreso.fatNumbers, {
            fat: formatNumber(check.fat),
            fatKcal: formatNumber(check.fatKcal),
            macroKcal: formatNumber(check.macroKcal),
            share: formatNumber(check.share),
          })}
        </p>
        <FatShareBar share={check.share} />
        <div className="space-y-2 text-sm text-muted-foreground">
          <p>{low ? t.progreso.fatLowWhy : t.progreso.fatHighWhy}</p>
          <p>{low ? t.progreso.fatLowWhat : t.progreso.fatHighWhat}</p>
        </div>
        <div>
          <Button size="sm" variant="outline" nativeButton={false} render={<Link href={METHOD_HREF} />}>
            {t.progreso.fatHowItWorks}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

/** 0–100 % of calories: the shaded band is 20–35 %, the dot is the period's fat. */
function FatShareBar({ share }: { share: number }) {
  const range = `${FAT_SHARE_MIN}–${FAT_SHARE_MAX} %`;
  const label = `${formatTemplate(t.common.labelValue, { label: t.progreso.fatRange, value: range })}. ${t.hoy.fat}: ${formatNumber(share)} %.`;
  return (
    <div className="space-y-1.5 text-xs text-muted-foreground">
      <div role="img" aria-label={label} className="relative h-7">
        <div className="absolute inset-x-0 top-1/2 h-px bg-border" />
        <div
          className="absolute inset-y-0 rounded-md bg-primary/25 ring-1 ring-primary/60"
          style={{ left: `${FAT_SHARE_MIN}%`, width: `${FAT_SHARE_MAX - FAT_SHARE_MIN}%` }}
        />
        <div
          className="absolute top-1/2 size-3 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-background bg-foreground"
          style={{ left: `${Math.min(Math.max(share, 2), 98)}%` }}
        />
      </div>
      <div className="flex justify-between tabular-nums">
        <span>0 %</span>
        <span>100 %</span>
      </div>
      <p className="flex items-center gap-2">
        <span aria-hidden className="h-2.5 w-4 rounded-sm bg-primary/25 ring-1 ring-primary/60" />
        {formatTemplate(t.common.labelValue, { label: t.progreso.fatRange, value: range })}
      </p>
    </div>
  );
}
