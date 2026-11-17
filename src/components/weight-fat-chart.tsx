"use client";

import { CartesianGrid, ComposedChart, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatDateKeyShort, formatNumber } from "@/i18n/format";
import { t } from "@/i18n";
import { ChartLegend } from "./chart-legend";
import { TOOLTIP_STYLE } from "./chart-style";

export interface WeightFatRow {
  date: string;
  weight?: number | null;
  weightTrend?: number | null;
  bodyFatPct?: number | null;
}

/** Which measurement the chart draws: one axis at a time. */
export type WeightChartSeries = "weight" | "bodyFat";

const TREND_COLOR = "var(--chart-4)";
const WEIGH_IN_COLOR = "var(--muted-foreground)";
const BODY_FAT_COLOR = "var(--chart-2)";

/**
 * Progreso's weight chart. `weight`: the 7-day trend, the line that says where
 * the weight is going, bold; each weigh-in a light dot with no line between
 * them (day-to-day water and food make it zig-zag). `bodyFat`: the body fat
 * measurements on their own axis. One axis at a time: kg and % on two axes
 * with overlapping ranges were hard to read.
 */
export function WeightFatChart({ data, series = "weight" }: { data: WeightFatRow[]; series?: WeightChartSeries }) {
  const isWeight = series === "weight";
  return (
    <div className="flex h-full flex-col gap-1">
      <div className="min-h-0 flex-1">
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={data} margin={{ top: 16, right: 12, bottom: 0, left: 0 }}>
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
            {isWeight ? (
              <>
                <Line
                  type="monotone"
                  dataKey="weight"
                  name={t.stats.legendWeight}
                  stroke="none"
                  dot={{ r: 2.5, fill: WEIGH_IN_COLOR, stroke: "none" }}
                  activeDot={{ r: 4 }}
                  isAnimationActive={false}
                />
                <Line
                  type="monotone"
                  dataKey="weightTrend"
                  name={t.stats.legendTrend}
                  stroke={TREND_COLOR}
                  strokeWidth={3}
                  dot={false}
                  connectNulls
                />
              </>
            ) : (
              <Line
                type="monotone"
                dataKey="bodyFatPct"
                name={t.stats.legendBodyFat}
                stroke={BODY_FAT_COLOR}
                strokeWidth={2}
                dot={{ r: 2.5 }}
                connectNulls
              />
            )}
          </ComposedChart>
        </ResponsiveContainer>
      </div>
      <ChartLegend
        items={
          isWeight
            ? [
                { label: t.stats.legendTrend, color: TREND_COLOR, shape: "line" },
                { label: t.stats.legendWeight, color: WEIGH_IN_COLOR, shape: "dot" },
              ]
            : [{ label: t.stats.legendBodyFat, color: BODY_FAT_COLOR, shape: "line" }]
        }
      />
    </div>
  );
}
