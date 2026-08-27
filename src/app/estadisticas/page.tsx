"use client";

import { useEffect, useMemo, useState } from "react";
import { Logo } from "@/components/logo";
import Link from "next/link";
import { InfoIcon } from "lucide-react";
import {
  CartesianGrid,
  ComposedChart,
  Line,
  ReferenceDot,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ThemeToggle } from "@/components/theme-toggle";
import { api, ApiError } from "@/lib/api";
import { formatDateKeyShort, formatNumberEs, todayKey } from "@/lib/dates";
import { round1 } from "@/lib/nutrition";
import { movingAverageByDays } from "@/lib/stats";
import type { StatsRange, StatsSummary } from "@/lib/types";
import { t } from "@/i18n";

const ranges: { value: StatsRange; label: string }[] = [
  { value: "7d", label: t.stats.range7 },
  { value: "30d", label: t.stats.range30 },
  { value: "90d", label: t.stats.range90 },
  { value: "all", label: t.stats.rangeAll },
];

export default function EstadisticasPage() {
  const [range, setRange] = useState<StatsRange>("30d");
  const [loaded, setLoaded] = useState<{ range: StatsRange; summary: StatsSummary } | null>(null);
  // Mientras el rango pedido no coincide con lo cargado, mostramos el esqueleto.
  const summary = loaded && loaded.range === range ? loaded.summary : null;
  const loading = summary === null;

  useEffect(() => {
    let cancelled = false;
    api
      .stats(range, todayKey())
      .then((data) => {
        if (!cancelled) setLoaded({ range, summary: data });
      })
      .catch((error) => {
        if (!(error instanceof ApiError && error.status === 401)) console.error(error);
      });
    return () => {
      cancelled = true;
    };
  }, [range]);

  return (
    <main className="mx-auto w-full max-w-2xl px-4 pt-4 md:pt-6">
      <header className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Logo size="header" priority />
          <h1 className="text-lg font-semibold">{t.stats.title}</h1>
        </div>
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="icon"
            nativeButton={false}
            render={<Link href="/metodologia" aria-label={t.metodologia.title} />}
          >
            <InfoIcon />
          </Button>
          <ThemeToggle />
        </div>
      </header>

      <div
        role="tablist"
        aria-label={t.stats.title}
        className="mt-3 grid grid-cols-4 gap-1 rounded-lg border p-1"
      >
        {ranges.map((item) => (
          <button
            key={item.value}
            role="tab"
            aria-selected={range === item.value}
            onClick={() => setRange(item.value)}
            className={`rounded-md px-2 py-1.5 text-xs font-medium transition-colors sm:text-sm ${
              range === item.value
                ? "bg-primary text-primary-foreground"
                : "text-muted-foreground hover:bg-accent"
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      {loading && !summary ? (
        <div className="mt-5 space-y-4">
          <Skeleton className="h-40 w-full" />
          <Skeleton className="h-64 w-full" />
        </div>
      ) : summary === null ? null : (
        <div className="mt-5 space-y-6">
          <WeightSection summary={summary} />
          <BodyFatSection summary={summary} />
          <LeanMassSection summary={summary} />
          <NutritionSection summary={summary} />
        </div>
      )}
    </main>
  );
}

function NutritionSection({ summary }: { summary: StatsSummary }) {
  const hasNutrition = summary.calories.some(
    (p) => p.calories > 0 || p.protein > 0 || p.carbs > 0 || p.fat > 0,
  );

  return (
    <>
      {!hasNutrition ? (
        <p className="rounded-xl border border-dashed py-8 text-center text-sm text-muted-foreground">
          {t.stats.noData}
        </p>
      ) : (
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          <MiniStat label={t.stats.caloriesAvg} value={summary.caloriesAvg} unit="" />
          <MiniStat
            label={`${t.stats.caloriesMax}${summary.caloriesMaxDay ? ` · ${formatDateKeyShort(summary.caloriesMaxDay.date)}` : ""}`}
            value={summary.caloriesMaxDay?.calories ?? null}
            unit=""
          />
          <MiniStat label={t.stats.proteinAvg} value={summary.proteinAvg} unit="g" decimals={0} />
          <MiniStat
            label={`${t.stats.proteinMax}${summary.proteinMaxDay ? ` · ${formatDateKeyShort(summary.proteinMaxDay.date)}` : ""}`}
            value={summary.proteinMaxDay?.protein ?? null}
            unit="g"
          />
          <MiniStat label={t.stats.carbsAvg} value={summary.carbsAvg} unit="g" decimals={0} />
          <MiniStat
            label={`${t.stats.carbsMax}${summary.carbsMaxDay ? ` · ${formatDateKeyShort(summary.carbsMaxDay.date)}` : ""}`}
            value={summary.carbsMaxDay?.carbs ?? null}
            unit="g"
          />
          <MiniStat label={t.stats.fatAvg} value={summary.fatAvg} unit="g" decimals={0} />
          <MiniStat
            label={`${t.stats.fatMax}${summary.fatMaxDay ? ` · ${formatDateKeyShort(summary.fatMaxDay.date)}` : ""}`}
            value={summary.fatMaxDay?.fat ?? null}
            unit="g"
          />
        </div>
      )}

      <ChartCard title={t.stats.caloriesChartTitle}>
        <TrendChart
          data={summary.calories}
          valueKey="calories"
          color="var(--chart-1)"
          name={t.stats.dailyIntake}
          unit=" kcal"
          margin={{ top: 16, right: 12, bottom: 0, left: -18 }}
          domain={[0, "auto"]}
          peak={
            summary.caloriesMaxDay
              ? { date: summary.caloriesMaxDay.date, value: summary.caloriesMaxDay.calories }
              : null
          }
        />
      </ChartCard>

      <ChartCard title={t.stats.proteinChartTitle}>
        <TrendChart
          data={summary.protein}
          valueKey="protein"
          color="var(--chart-2)"
          name={t.stats.dailyIntake}
          unit=" g"
          margin={{ top: 16, right: 12, bottom: 0, left: -18 }}
          domain={[0, "auto"]}
          peak={
            summary.proteinMaxDay
              ? { date: summary.proteinMaxDay.date, value: summary.proteinMaxDay.protein }
              : null
          }
        />
      </ChartCard>

      <ChartCard title={t.stats.carbsChartTitle}>
        <TrendChart
          data={summary.carbs}
          valueKey="carbs"
          color="var(--chart-3)"
          name={t.stats.dailyIntake}
          unit=" g"
          margin={{ top: 16, right: 12, bottom: 0, left: -18 }}
          domain={[0, "auto"]}
          peak={
            summary.carbsMaxDay
              ? { date: summary.carbsMaxDay.date, value: summary.carbsMaxDay.carbs }
              : null
          }
        />
      </ChartCard>

      <ChartCard title={t.stats.fatChartTitle}>
        <TrendChart
          data={summary.fat}
          valueKey="fat"
          color="var(--chart-4)"
          name={t.stats.dailyIntake}
          unit=" g"
          margin={{ top: 16, right: 12, bottom: 0, left: -18 }}
          domain={[0, "auto"]}
          peak={
            summary.fatMaxDay
              ? { date: summary.fatMaxDay.date, value: summary.fatMaxDay.fat }
              : null
          }
        />
      </ChartCard>
    </>
  );
}

function WeightSection({ summary }: { summary: StatsSummary }) {
  const hasWeights = summary.weights.length > 0;
  const w = summary.weight;

  return (
    <>
      {!hasWeights ? (
        <p className="rounded-xl border border-dashed py-8 text-center text-sm text-muted-foreground">
          {t.stats.noData}
        </p>
      ) : (
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          <MiniStat
            label={t.stats.currentWeight}
            value={w.currentWeightKg}
            unit="kg"
            decimals={1}
          />
          <MiniStat label={t.stats.currentTrend} value={w.currentTrendKg} unit="kg" decimals={1} />
          <MiniStat
            label={t.stats.changeSinceStart}
            value={w.changeSinceStartKg}
            unit="kg"
            decimals={1}
            signed
          />
          <MiniStat
            label={t.stats.ratePerWeek}
            value={w.ratePerWeekKg}
            unit={` ${t.stats.perWeek}`}
            decimals={2}
            signed
          />
        </div>
      )}

      <ChartCard title={t.stats.weightChartTitle}>
        {hasWeights ? (
          <TrendChart
            data={summary.weights}
            valueKey="weight"
            color="var(--chart-3)"
            name={t.stats.scaleWeight}
            yAxisFormatter={(v) => String(Math.round(v * 10) / 10)}
          />
        ) : (
          <EmptyChartMessage />
        )}
      </ChartCard>

      {summary.weeklyWeightAvg.length > 0 ? (
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-base">{t.stats.weightWeeklyAvgTitle}</CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="grid gap-x-6 gap-y-1 text-sm sm:grid-cols-2">
              {summary.weeklyWeightAvg.map((week) => (
                <li key={week.weekStart} className="flex justify-between border-b py-1">
                  <span className="text-muted-foreground">
                    {formatDateKeyShort(week.weekStart)}
                  </span>
                  <span className="font-medium tabular-nums">
                    {formatNumberEs(week.avg, 1)} kg
                  </span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      ) : null}

      {hasWeights ? (
        <p className="text-center text-xs text-muted-foreground">
          {t.stats.minWeight}: {formatNumberEs(w.minKg ?? 0, 1)} kg · {t.stats.maxWeight}:{" "}
          {formatNumberEs(w.maxKg ?? 0, 1)} kg
        </p>
      ) : null}
    </>
  );
}

function BodyFatSection({ summary }: { summary: StatsSummary }) {
  if (summary.bodyFat.length === 0) return null;
  const bf = summary.weight;

  return (
    <>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <MiniStat
          label={t.stats.currentBodyFat}
          value={bf.currentBodyFatPct}
          unit="%"
          decimals={1}
        />
        <MiniStat
          label={t.stats.changeBodyFat}
          value={bf.changeBodyFatPct}
          unit="pp"
          decimals={1}
          signed
        />
        <MiniStat
          label={t.stats.minWeight}
          value={bf.minBodyFatPct}
          unit="%"
          decimals={1}
        />
        <MiniStat
          label={t.stats.maxWeight}
          value={bf.maxBodyFatPct}
          unit="%"
          decimals={1}
        />
      </div>

      <ChartCard title={t.stats.bodyFatChartTitle}>
        <TrendChart
          data={summary.bodyFat}
          valueKey="bodyFatPct"
          color="var(--chart-2)"
          name={t.stats.currentBodyFat}
          yAxisFormatter={(v) => `${String(Math.round(v * 10) / 10)}%`}
          tooltipSuffix="%"
        />
      </ChartCard>
    </>
  );
}

function LeanMassSection({ summary }: { summary: StatsSummary }) {
  if (summary.leanMass.length === 0) return null;
  const bf = summary.weight;

  return (
    <>
      <div className="grid grid-cols-2 gap-2">
        <MiniStat
          label={t.stats.currentLeanMass}
          value={bf.currentLeanMassKg}
          unit="kg"
          decimals={1}
        />
        <MiniStat
          label={t.stats.changeLeanMass}
          value={bf.changeLeanMassKg}
          unit="kg"
          decimals={1}
          signed
        />
      </div>

      <ChartCard title={t.stats.leanMassChartTitle}>
        <TrendChart
          data={summary.leanMass}
          valueKey="leanMassKg"
          color="var(--chart-1)"
          name={t.stats.currentLeanMass}
          yAxisFormatter={(v) => `${String(Math.round(v * 10) / 10)} kg`}
          tooltipSuffix=" kg"
        />
      </ChartCard>
    </>
  );
}

function MiniStat({
  label,
  value,
  unit,
  decimals = 0,
  signed = false,
}: {
  label: string;
  value: number | null;
  unit: string;
  decimals?: number;
  signed?: boolean;
}) {
  const display =
    value === null ? "—" : `${signed && value > 0 ? "+" : ""}${formatNumberEs(value, decimals)}`;
  return (
    <Card>
      <CardContent className="px-3 py-2.5">
        <p className="truncate text-xs font-medium uppercase tracking-wide text-muted-foreground">
          {label}
        </p>
        <p className="text-lg font-semibold tabular-nums">
          {display}
          <span className="ml-1 text-xs font-normal text-muted-foreground">{unit}</span>
        </p>
      </CardContent>
    </Card>
  );
}

function ChartCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Card>
      <CardHeader className="pb-0">
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="h-56 px-2 sm:h-64">{children}</CardContent>
    </Card>
  );
}

function EmptyChartMessage() {
  return (
    <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
      {t.stats.noData}
    </div>
  );
}

interface SeriesPoint {
  date: string;
  value: number;
}

const TOOLTIP_STYLE = {
  background: "var(--popover)",
  border: "1px solid var(--border)",
  borderRadius: 10,
  fontSize: 12,
  color: "var(--popover-foreground)",
} as const;

const DATE_AXIS_PROPS = {
  dataKey: "date" as const,
  tickFormatter: (value: string) => formatDateKeyShort(value),
  tick: { fontSize: 10 },
  interval: "preserveStartEnd" as const,
  minTickGap: 28,
  tickLine: false,
  axisLine: false,
};

function tooltipFormatter(value: unknown): string {
  if (value === null || value === undefined) return "—";
  return formatNumberEs(Number(value), 1);
}

function TrendChart<T extends { date: string }>({
  data,
  valueKey,
  color,
  name,
  unit = "",
  trendColor = "var(--chart-4)",
  yAxisFormatter,
  tooltipSuffix = "",
  peak,
  domain = ["auto", "auto"],
  margin = { top: 12, right: 8, bottom: 0, left: -18 },
}: {
  data: T[];
  valueKey: keyof T & string;
  color: string;
  name: string;
  unit?: string;
  trendColor?: string;
  yAxisFormatter?: (value: number) => string;
  tooltipSuffix?: string;
  peak?: SeriesPoint | null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  domain?: any[];
  margin?: { top: number; right: number; bottom: number; left: number };
}) {
  const movingAverage = useMemo(() => {
    const points = data.map((row) => ({
      date: String(row.date),
      value: Number((row as Record<string, unknown>)[valueKey]) || 0,
    }));
    return movingAverageByDays(points, 7);
  }, [data, valueKey]);

  const rows = data.map((point, i) => ({
    ...point,
    tendencia: movingAverage[i] === null ? null : round1(movingAverage[i] as number),
  }));

  const defaultTooltipFormatter = (value: unknown) => {
    if (value === null || value === undefined) return "—";
    return `${formatNumberEs(Number(value), 1)}${tooltipSuffix}`;
  };

  return (
    <ResponsiveContainer width="100%" height="100%">
      <ComposedChart data={rows} margin={margin}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
        <XAxis {...DATE_AXIS_PROPS} />
        <YAxis
          domain={domain}
          tick={{ fontSize: 10 }}
          tickLine={false}
          axisLine={false}
          width={44}
          {...(yAxisFormatter ? { tickFormatter: yAxisFormatter } : {})}
        />
        <Tooltip
          contentStyle={TOOLTIP_STYLE}
          formatter={tooltipSuffix ? defaultTooltipFormatter : tooltipFormatter}
          labelFormatter={(value) => formatDateKeyShort(String(value))}
        />
        <Line
          type="monotone"
          dataKey={valueKey}
          name={name}
          stroke={color}
          strokeWidth={1.5}
          dot={{ r: 2 }}
        />
        <Line
          type="monotone"
          dataKey="tendencia"
          name={t.stats.trendLine}
          stroke={trendColor}
          strokeWidth={2.5}
          className={trendColor === "currentColor" ? "text-muted-foreground" : undefined}
          dot={false}
          connectNulls
        />
        {peak ? (
          <ReferenceDot
            x={peak.date}
            y={peak.value}
            r={5}
            fill={color}
            stroke="var(--background)"
            strokeWidth={2}
            label={{
              value: `${formatNumberEs(peak.value)}${unit}`,
              position: "top",
              fontSize: 10,
              fill: "var(--foreground)",
            }}
          />
        ) : null}
      </ComposedChart>
    </ResponsiveContainer>
  );
}
