"use client";

import { useEffect, useMemo, useState } from "react";
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ThemeToggle } from "@/components/theme-toggle";
import { api, ApiError } from "@/lib/api";
import { formatDateKeyShort, formatNumberEs, todayKey } from "@/lib/dates";
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
        <h1 className="text-lg font-semibold">{t.stats.title}</h1>
        <ThemeToggle />
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
          <NutritionSection summary={summary} />
          <WeightSection summary={summary} />
        </div>
      )}
    </main>
  );
}

function NutritionSection({ summary }: { summary: StatsSummary }) {
  const hasNutrition = summary.calories.some((p) => p.calories > 0 || p.protein > 0);

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
        </div>
      )}

      <ChartCard title={t.stats.caloriesChartTitle}>
        <CalorieProteinChart
          data={summary.calories.map((point) => ({
            date: point.date,
            value: point.calories,
          }))}
          color="var(--chart-1)"
        />
      </ChartCard>

      <ChartCard title={t.stats.proteinChartTitle}>
        <CalorieProteinChart
          data={summary.protein.map((point) => ({ date: point.date, value: point.protein }))}
          color="var(--chart-2)"
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
          <WeightChart data={summary.weights} />
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

/** Daily bars plus a trailing 7-day moving-average line to reveal patterns. */
function CalorieProteinChart({ data, color }: { data: SeriesPoint[]; color: string }) {
  const movingAverage = useMemo(
    () => movingAverageByDays(data, 7),
    [data],
  );
  const rows = data.map((point, i) => ({
    date: point.date,
    value: point.value,
    tendencia: movingAverage[i],
  }));

  return (
    <ResponsiveContainer width="100%" height="100%">
      <ComposedChart data={rows} margin={{ top: 12, right: 8, bottom: 0, left: -18 }}>
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
        <YAxis tick={{ fontSize: 10 }} tickLine={false} axisLine={false} width={44} />
        <Tooltip
          contentStyle={{
            background: "var(--popover)",
            border: "1px solid var(--border)",
            borderRadius: 10,
            fontSize: 12,
            color: "var(--popover-foreground)",
          }}
          labelFormatter={(value) => formatDateKeyShort(String(value))}
        />
        <Bar dataKey="value" fill={color} radius={[3, 3, 0, 0]} maxBarSize={18} />
        <Line
          type="monotone"
          dataKey="tendencia"
          stroke="currentColor"
          className="text-muted-foreground"
          strokeWidth={2}
          dot={false}
          connectNulls
        />
      </ComposedChart>
    </ResponsiveContainer>
  );
}

function WeightChart({
  data,
}: {
  data: { date: string; weight: number; trend: number | null }[];
}) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <ComposedChart data={data} margin={{ top: 12, right: 8, bottom: 0, left: -18 }}>
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
        <YAxis
          domain={["auto", "auto"]}
          tick={{ fontSize: 10 }}
          tickLine={false}
          axisLine={false}
          width={44}
          tickFormatter={(value: number) => String(Math.round(value * 10) / 10)}
        />
        <Tooltip
          contentStyle={{
            background: "var(--popover)",
            border: "1px solid var(--border)",
            borderRadius: 10,
            fontSize: 12,
            color: "var(--popover-foreground)",
          }}
          labelFormatter={(value) => formatDateKeyShort(String(value))}
        />
        <Line
          type="monotone"
          dataKey="weight"
          name={t.stats.scaleWeight}
          stroke="var(--chart-3)"
          strokeWidth={1.5}
          dot={{ r: 2 }}
        />
        <Line
          type="monotone"
          dataKey="trend"
          name={t.stats.trendLine}
          stroke="var(--chart-4)"
          strokeWidth={2.5}
          dot={false}
          connectNulls
        />
      </ComposedChart>
    </ResponsiveContainer>
  );
}
