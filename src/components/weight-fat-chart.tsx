"use client";

import { CartesianGrid, ComposedChart, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatDateKeyShort, formatNumber } from "@/i18n/format";
import { formatTemplate, t } from "@/i18n";
import { ChartLegend } from "./chart-legend";
import { TOOLTIP_STYLE } from "./chart-style";

export interface WeightFatRow {
  date: string;
  weight?: number | null;
  weightTrend?: number | null;
  /** False: taken otherwise than most weigh-ins (fasted or not); drawn hollow, outside the trend. */
  inTrend?: boolean;
  bodyFatPct?: number | null;
  bodyFatTrend?: number | null;
  /** As `inTrend`, among the readings with body fat. */
  bodyFatInTrend?: boolean;
}

/** Which measurement the chart draws: one axis at a time. */
export type WeightChartSeries = "weight" | "bodyFat";

const TREND_COLOR = "var(--chart-4)";
const WEIGH_IN_COLOR = "var(--muted-foreground)";
const BODY_FAT_COLOR = "var(--chart-2)";

/**
 * Progreso's weight chart. `weight`: the 7-day trend, the line that says where
 * the weight is going, bold; each weigh-in a light dot with no line between
 * them (day-to-day water and food make it zig-zag), hollow when it was taken
 * otherwise than most and stays out of the trend. `bodyFat`: the same for the
 * body fat readings, on their own axis (a scale's reading swings with water even
 * more than the weight). One axis at a time: kg and % on two axes with
 * overlapping ranges were hard to read.
 */
export function WeightFatChart({ data, series = "weight" }: { data: WeightFatRow[]; series?: WeightChartSeries }) {
  const isWeight = series === "weight";
  return (
    <div className="flex h-full flex-col gap-1">
      <div className="min-h-0 flex-1">
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={data} margin={{ top: 16, right: 12, bottom: 0, left: 0 }} title={chartSummary(data, series)}>
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
              tickFormatter={(value: number) => (isWeight ? formatNumber(value, 1) : `${formatNumber(value, 1)}%`)}
            />
            <Tooltip
              contentStyle={TOOLTIP_STYLE}
              labelFormatter={(value) => formatDateKeyShort(String(value))}
              formatter={(value: unknown) =>
                value === null || value === undefined ? "—" : `${formatNumber(Number(value), 1)}${isWeight ? " kg" : "%"}`
              }
            />
            <Line
              type="monotone"
              dataKey={isWeight ? "weight" : "bodyFatPct"}
              name={isWeight ? t.stats.legendWeight : t.stats.legendBodyFat}
              stroke="none"
              dot={isWeight ? WeighInDot : FatReadingDot}
              activeDot={{ r: 4 }}
              isAnimationActive={false}
            />
            <Line
              type="monotone"
              dataKey={isWeight ? "weightTrend" : "bodyFatTrend"}
              name={t.stats.trendLine}
              stroke={isWeight ? TREND_COLOR : BODY_FAT_COLOR}
              strokeWidth={3}
              dot={false}
              connectNulls
            />
          </ComposedChart>
        </ResponsiveContainer>
      </div>
      <ChartLegend
        items={[
          { label: t.stats.trendLine, color: isWeight ? TREND_COLOR : BODY_FAT_COLOR, shape: "line" },
          { label: isWeight ? t.stats.legendWeight : t.stats.legendBodyFat, color: WEIGH_IN_COLOR, shape: "dot" },
        ]}
      />
    </div>
  );
}

type DotProps = { cx?: number; cy?: number; payload?: WeightFatRow; index?: number };

/** A reading: a filled dot, or a ring when it stays out of the trend. */
function readingDot(value: (row: WeightFatRow) => number | null | undefined, inTrend: (row: WeightFatRow) => boolean | undefined) {
  return function ReadingDot({ cx, cy, payload, index }: DotProps) {
    if (cx === undefined || cy === undefined || !payload || value(payload) == null) return <g key={index} />;
    return inTrend(payload) === false ? (
      <circle key={index} cx={cx} cy={cy} r={2.75} fill="var(--card)" stroke={WEIGH_IN_COLOR} strokeWidth={1.25} />
    ) : (
      <circle key={index} cx={cx} cy={cy} r={2.5} fill={WEIGH_IN_COLOR} stroke="none" />
    );
  };
}

const WeighInDot = readingDot((row) => row.weight, (row) => row.inTrend);
const FatReadingDot = readingDot((row) => row.bodyFatPct, (row) => row.bodyFatInTrend);

/** The chart in one sentence with the user's numbers, its name for screen readers. */
export function chartSummary(data: WeightFatRow[], series: WeightChartSeries): string {
  const isWeight = series === "weight";
  // The trend where there is one, on the days with a reading.
  const value = (row: WeightFatRow) =>
    isWeight ? (row.weightTrend ?? row.weight) : row.bodyFatPct == null ? null : (row.bodyFatTrend ?? row.bodyFatPct);
  const points = data
    .map((row) => ({ date: row.date, value: value(row) }))
    .filter((point): point is { date: string; value: number } => point.value !== null && point.value !== undefined);
  const first = points[0];
  const last = points.at(-1);
  if (!first || !last) return isWeight ? t.stats.legendWeight : t.stats.legendBodyFat;
  return formatTemplate(isWeight ? t.stats.weightChartSummary : t.stats.bodyFatChartSummary, {
    from: formatNumber(first.value, 1),
    to: formatNumber(last.value, 1),
    start: formatDateKeyShort(first.date),
    end: formatDateKeyShort(last.date),
  });
}
