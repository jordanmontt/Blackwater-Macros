"use client";

import { useMemo, useState } from "react";
import { formatDateKeyLong, formatDateKeyShort, formatNumber } from "@/i18n/format";
import Link from "next/link";
import { InfoIcon, PencilIcon, WeightIcon, Trash2Icon } from "lucide-react";
import { toast } from "sonner";
import {
  CartesianGrid,
  ComposedChart,
  Line,
  ReferenceArea,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { WeightFatChart, type WeightFatRow } from "@/components/weight-fat-chart";
import { WeightFormDialog } from "@/components/weight-form-dialog";
import { api, ApiError, errorText, UNDO_TOAST_MS } from "@/lib/api";
import { useCachedResource } from "@/lib/use-cached-resource";
import { useMeasuredExpenditure } from "@/lib/use-measured-expenditure";
import { useRecommendations } from "@/lib/use-recommendations";
import { addDaysToKey, todayKey } from "@/lib/core/dates";
import { round1 } from "@/lib/core/nutrition";
import { macroAverages } from "@/lib/core/progress";
import { movingAverageByDays, rangeToDays } from "@/lib/core/stats";
import type { CalorieRecommendation, StatsRange, StatsSummary, WeightDTO } from "@/lib/core/types";
import { formatTemplate, t } from "@/i18n";

const ranges: { value: StatsRange; label: string }[] = [
  { value: "7d", label: t.stats.range7 },
  { value: "30d", label: t.stats.range30 },
  { value: "90d", label: t.stats.range90 },
  { value: "all", label: t.stats.rangeAll },
];

/**
 * Progreso: weight and eating over one selected period. Weight summary and
 * chart, calories chart with the target band, macro averages over logged days
 * and the weigh-in log (add / edit / delete).
 */
export default function ProgresoPage() {
  const [range, setRange] = useState<StatsRange>("30d");
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<WeightDTO | null>(null);
  const [formKey, setFormKey] = useState(0);
  const [deleting, setDeleting] = useState<WeightDTO | null>(null);

  const today = todayKey();
  const summaryRes = useCachedResource<StatsSummary>(`stats:${range}:${today}`, () => api.stats(range, today), {
    onError: (error) => {
      if (!(error instanceof ApiError && error.status === 401)) console.error(error);
    },
  });
  const summary = summaryRes.data ?? null;
  const { weights, calorieRec, proteinRec } = useRecommendations();
  const weightsRes = useCachedResource<WeightDTO[]>("weights", () => api.listWeights());

  function openForm(entry: WeightDTO | null) {
    setEditing(entry);
    setFormKey((k) => k + 1);
    setFormOpen(true);
  }

  async function handleDeleteConfirmed() {
    if (!deleting) return;
    const weight = deleting;
    try {
      await api.deleteWeight(weight.id);
      setDeleting(null);
      await weightsRes.trigger();
      toast.success(t.peso.deleted, {
        duration: UNDO_TOAST_MS,
        action: {
          label: t.common.undo,
          onClick: () => {
            api
              .restoreWeight(weight)
              .then(() => weightsRes.trigger())
              .catch((error) => toast.error(errorText(error)));
          },
        },
      });
    } catch (error) {
      toast.error(errorText(error));
    }
  }

  return (
    <main className="mx-auto w-full max-w-2xl px-4 pt-4 pb-24 md:pt-6">
      <header className="grid grid-cols-[1fr_auto_1fr] items-center gap-2">
        <div />
        <h1 className="text-center text-lg font-semibold">{t.progreso.title}</h1>
        <Button
          variant="ghost"
          size="icon"
          nativeButton={false}
          render={<Link href="/metodologia" aria-label={t.metodologia.title} />}
          className="justify-self-end"
        >
          <InfoIcon />
        </Button>
      </header>

      <div role="tablist" aria-label={t.progreso.title} className="mt-3 grid grid-cols-4 gap-1 rounded-lg border p-1">
        {ranges.map((item) => (
          <button
            key={item.value}
            role="tab"
            aria-selected={range === item.value}
            onClick={() => setRange(item.value)}
            className={`rounded-md px-2 py-1.5 text-xs font-medium transition-colors sm:text-sm ${
              range === item.value ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-accent"
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      {summary === null ? (
        <div className="mt-5 space-y-4">
          <Skeleton className="h-40 w-full" />
          <Skeleton className="h-64 w-full" />
        </div>
      ) : (
        <div className="mt-5 space-y-4">
          <WeightSection summary={summary} onAdd={() => openForm(null)} />
          <CaloriesSection summary={summary} calorieRec={calorieRec} />
          <MacrosCard
            summary={summary}
            calorieRange={calorieRec ? { min: calorieRec.targetMin, max: calorieRec.targetMax } : null}
            proteinRange={proteinRec ? proteinRec.range : null}
          />
          <WeightEntries
            weights={weights ?? null}
            range={range}
            today={today}
            onEdit={(entry) => openForm(entry)}
            onDelete={setDeleting}
          />
        </div>
      )}

      <WeightFormDialog
        key={formKey}
        open={formOpen}
        entry={editing}
        onOpenChange={setFormOpen}
        onSaved={() => weightsRes.trigger()}
      />

      <AlertDialog
        open={deleting !== null}
        onOpenChange={(open) => {
          if (!open) setDeleting(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t.peso.deleteConfirmTitle}</AlertDialogTitle>
            <AlertDialogDescription>{t.peso.deleteConfirmBody}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t.peso.cancel}</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={() => void handleDeleteConfirmed()}>
              {t.peso.delete}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Labelled: a bare «+» here did not say it logs a weigh-in. */}
      <Button
        onClick={() => openForm(null)}
        className="fixed right-4 bottom-[calc(5rem+env(safe-area-inset-bottom))] z-50 h-14 gap-2 rounded-full bg-tertiary px-5 text-base text-tertiary-foreground shadow-lg hover:bg-tertiary/90 md:bottom-6"
      >
        <WeightIcon className="size-5" />
        {t.peso.addTitle}
      </Button>
    </main>
  );
}

function WeightSection({ summary, onAdd }: { summary: StatsSummary; onAdd: () => void }) {
  const w = summary.weight;
  const rows = useMemo<WeightFatRow[]>(() => {
    const map = new Map<string, WeightFatRow>();
    for (const point of summary.weights) {
      map.set(point.date, { date: point.date, weight: point.weight, weightTrend: point.trend });
    }
    for (const point of summary.bodyFat) {
      map.set(point.date, { ...(map.get(point.date) ?? { date: point.date }), bodyFatPct: point.bodyFatPct });
    }
    return [...map.values()].sort((a, b) => a.date.localeCompare(b.date));
  }, [summary]);

  if (summary.weights.length === 0) {
    return (
      <button
        type="button"
        onClick={onAdd}
        className="w-full rounded-xl border border-dashed py-10 text-center text-sm text-muted-foreground transition-colors hover:bg-accent"
      >
        {t.peso.emptyList}
      </button>
    );
  }

  return (
    <Card>
      <CardHeader className="pb-0">
        <CardTitle className="text-base">{t.progreso.weightTitle}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 px-4 pb-4">
        <p className="text-4xl font-semibold tabular-nums" data-testid="current-weight">
          {w.currentWeightKg === null ? "—" : `${formatNumber(w.currentWeightKg, 1)} kg`}
        </p>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          <MiniStat label={t.progreso.trend} value={w.currentTrendKg} unit="kg" decimals={1} />
          <MiniStat label={t.progreso.change} value={w.changeSinceStartKg} unit="kg" decimals={1} signed />
          <MiniStat label={t.progreso.rate} value={w.ratePerWeekKg} unit={t.stats.perWeek} decimals={2} signed />
          <MiniStat label={t.progreso.bodyFat} value={w.currentBodyFatPct} unit="%" decimals={1} />
        </div>
        {summary.weights.length > 1 ? (
          <div className="h-56 sm:h-64">
            <WeightFatChart data={rows} />
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

function MiniStat({
  label,
  value,
  unit,
  decimals,
  signed = false,
}: {
  label: string;
  value: number | null;
  unit: string;
  decimals: number;
  signed?: boolean;
}) {
  return (
    <div className="rounded-lg border px-3 py-2">
      <p className="text-xs font-medium text-muted-foreground">{label}</p>
      <p className="text-base font-semibold tabular-nums">
        {value === null ? "—" : `${signed && value > 0 ? "+" : ""}${formatNumber(value, decimals)}`}
        {value === null ? null : <span className="ml-1 text-xs font-normal text-muted-foreground">{unit}</span>}
      </p>
    </div>
  );
}

const TOOLTIP_STYLE = {
  background: "var(--popover)",
  border: "1px solid var(--border)",
  borderRadius: 10,
  fontSize: 12,
  color: "var(--popover-foreground)",
} as const;

function CaloriesSection({ summary, calorieRec }: { summary: StatsSummary; calorieRec: CalorieRecommendation | null }) {
  const rows = useMemo(() => {
    // Logged days only: a 0 would pull the trend down for a day that was simply not logged.
    const logged = summary.calories.filter((p) => p.calories > 0);
    const trend = movingAverageByDays(
      logged.map((p) => ({ date: p.date, value: p.calories })),
      7,
    );
    return logged.map((point, i) => ({
      date: point.date,
      calories: Math.round(point.calories),
      tendencia: trend[i] === null ? null : round1(trend[i] as number),
    }));
  }, [summary]);

  if (rows.length === 0) return null;

  return (
    <Card>
      <CardHeader className="pb-0">
        <CardTitle className="text-base">{t.progreso.caloriesTitle}</CardTitle>
      </CardHeader>
      <CardContent className="h-56 px-2 sm:h-64" data-testid="calories-chart">
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={rows} margin={{ top: 16, right: 12, bottom: 0, left: -18 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
            <XAxis
              dataKey="date"
              tickFormatter={(value: string) => formatDateKeyShort(value)}
              tick={{ fontSize: 10 }}
              interval="preserveStartEnd"
              minTickGap={28}
              tickLine={false}
              axisLine={false}
            />
            <YAxis domain={[0, "auto"]} tick={{ fontSize: 10 }} tickLine={false} axisLine={false} width={44} />
            {calorieRec ? (
              <ReferenceArea
                y1={calorieRec.targetMin}
                y2={calorieRec.targetMax}
                fill="var(--primary)"
                fillOpacity={0.12}
                stroke="none"
                ifOverflow="extendDomain"
                label={{ value: t.progreso.targetBand, position: "insideTopLeft", fontSize: 10, fill: "var(--muted-foreground)" }}
              />
            ) : null}
            <Tooltip
              contentStyle={TOOLTIP_STYLE}
              formatter={(value) => (value === null || value === undefined ? "—" : formatNumber(Number(value)))}
              labelFormatter={(value) => formatDateKeyShort(String(value))}
            />
            <Line type="monotone" dataKey="calories" name={t.stats.dailyIntake} stroke="var(--chart-1)" strokeWidth={1.5} dot={{ r: 2 }} />
            <Line
              type="monotone"
              dataKey="tendencia"
              name={t.stats.trendLine}
              stroke="var(--chart-4)"
              strokeWidth={2.5}
              dot={false}
              connectNulls
            />
          </ComposedChart>
        </ResponsiveContainer>
      </CardContent>
    </Card>
  );
}

function MacrosCard({
  summary,
  calorieRange,
  proteinRange,
}: {
  summary: StatsSummary;
  calorieRange: { min: number; max: number } | null;
  proteinRange: { min: number; max: number } | null;
}) {
  const measured = useMeasuredExpenditure();
  // The stats series has every day of the period (0 when nothing was logged).
  const averages = useMemo(() => macroAverages(summary.calories), [summary]);

  if (!averages) {
    return (
      <p className="rounded-xl border border-dashed py-8 text-center text-sm text-muted-foreground">
        {t.progreso.noMeals}
      </p>
    );
  }

  const rows: { label: string; value: string; target: { min: number; max: number } | null }[] = [
    { label: t.hoy.calories, value: `${formatNumber(averages.calories)} kcal`, target: calorieRange },
    { label: t.hoy.protein, value: `${formatNumber(averages.protein)} g`, target: proteinRange },
    { label: t.hoy.carbs, value: `${formatNumber(averages.carbs)} g`, target: null },
    { label: t.hoy.fat, value: `${formatNumber(averages.fat)} g`, target: null },
  ];

  return (
    <Card>
      <CardHeader className="pb-0">
        <CardTitle className="flex items-baseline justify-between gap-2 text-base">
          {t.progreso.macrosTitle}
          <span className="text-xs font-normal text-muted-foreground">
            {formatTemplate(t.progreso.loggedDays, { n: averages.loggedDays, m: averages.totalDays })}
          </span>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 px-4 pb-4">
        <dl className="divide-y">
          {rows.map((row) => (
            <div key={row.label} className="flex items-baseline justify-between gap-3 py-1.5">
              <dt className="text-sm text-muted-foreground">{row.label}</dt>
              <dd className="text-right">
                <span className="font-semibold tabular-nums">{row.value}</span>
                {row.target ? (
                  <span className="ml-2 text-xs text-muted-foreground tabular-nums">
                    {formatTemplate(t.progreso.target, {
                      min: formatNumber(row.target.min),
                      max: formatNumber(row.target.max),
                    })}
                  </span>
                ) : null}
              </dd>
            </div>
          ))}
        </dl>
        {averages.split ? (
          <div className="space-y-1.5">
            <p className="text-xs font-medium text-muted-foreground">{t.progreso.caloriesSplit}</p>
            <div className="flex h-2.5 overflow-hidden rounded-full" aria-hidden>
              <div className="bg-[var(--chart-2)]" style={{ width: `${averages.split.protein}%` }} />
              <div className="bg-[var(--chart-3)]" style={{ width: `${averages.split.carbs}%` }} />
              <div className="bg-[var(--chart-4)]" style={{ width: `${averages.split.fat}%` }} />
            </div>
            <p className="text-xs text-muted-foreground" data-testid="macro-split">
              {t.hoy.protein} {averages.split.protein} % · {t.hoy.carbs} {averages.split.carbs} % · {t.hoy.fat}{" "}
              {averages.split.fat} %
            </p>
          </div>
        ) : null}
        <p className="text-xs text-muted-foreground">{t.progreso.perLoggedDay}</p>
        {measured ? (
          <p className="text-sm">
            {formatTemplate(t.progreso.measured, {
              n: formatNumber(measured.tdee),
              margin: formatNumber(measured.margin),
            })}
          </p>
        ) : null}
      </CardContent>
    </Card>
  );
}

function WeightEntries({
  weights,
  range,
  today,
  onEdit,
  onDelete,
}: {
  weights: WeightDTO[] | null;
  range: StatsRange;
  today: string;
  onEdit: (entry: WeightDTO) => void;
  onDelete: (entry: WeightDTO) => void;
}) {
  const groups = useMemo(() => {
    const days = rangeToDays(range);
    const from = days === null ? null : addDaysToKey(today, -(days - 1));
    const byDay = new Map<string, WeightDTO[]>();
    for (const entry of weights ?? []) {
      const day = entry.measuredAt.slice(0, 10);
      if (from !== null && day < from) continue;
      byDay.set(day, [...(byDay.get(day) ?? []), entry]);
    }
    return [...byDay.entries()].sort(([a], [b]) => b.localeCompare(a));
  }, [weights, range, today]);

  if (weights === null || weights.length === 0) return null;

  return (
    <section className="space-y-3">
      <h2 className="text-base font-semibold">{t.progreso.entriesTitle}</h2>
      {groups.length === 0 ? <p className="text-sm text-muted-foreground">{t.progreso.noEntriesInRange}</p> : null}
      {groups.map(([day, entries]) => (
        <div key={day}>
          <h3 className="text-sm font-medium text-muted-foreground">{formatDateKeyLong(day)}</h3>
          <ul className="mt-1.5 divide-y overflow-hidden rounded-xl border">
            {[...entries].reverse().map((entry) => (
              <li key={entry.id} className="flex items-center gap-3 bg-card px-3 py-2.5">
                <span className="font-medium tabular-nums">
                  {formatNumber(entry.weightKg, 1)} kg
                  {entry.bodyFatPct !== null && (
                    <span className="hidden text-muted-foreground min-[400px]:inline">
                      {" "}· {formatNumber(entry.bodyFatPct, 1)}% grasa
                    </span>
                  )}
                </span>
                {entry.note ? (
                  <span className="min-w-0 flex-1 truncate text-xs text-muted-foreground">{entry.note}</span>
                ) : (
                  <span className="flex-1" />
                )}
                <Button variant="ghost" size="icon" aria-label={t.peso.edit} onClick={() => onEdit(entry)}>
                  <PencilIcon className="text-muted-foreground" />
                </Button>
                <Button variant="ghost" size="icon" aria-label={t.peso.delete} onClick={() => onDelete(entry)}>
                  <Trash2Icon className="text-muted-foreground" />
                </Button>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </section>
  );
}
