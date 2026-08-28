"use client";

import {
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { formatDateKeyShort, formatNumberEs } from "@/lib/dates";
import { t } from "@/i18n";

export interface WeightFatRow {
  date: string;
  weight?: number | null;
  weightTrend?: number | null;
  bodyFatPct?: number | null;
}

const TOOLTIP_STYLE = {
  background: "var(--popover)",
  border: "1px solid var(--border)",
  borderRadius: 10,
  fontSize: 12,
  color: "var(--popover-foreground)",
} as const;

/**
 * Combined evolution chart: weight (solid line), 7-day weight trend (dotted)
 * and body fat percentage (dotted), each on its own axis. Left axis = kg,
 * right axis = % of body fat. Used on Estadísticas and Peso.
 */
export function WeightFatChart({ data }: { data: WeightFatRow[] }) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <ComposedChart data={data} margin={{ top: 16, right: 4, bottom: 0, left: -18 }}>
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
          yAxisId="kg"
          domain={["auto", "auto"]}
          tick={{ fontSize: 10 }}
          tickLine={false}
          axisLine={false}
          width={44}
          tickFormatter={(value: number) => String(Math.round(value * 10) / 10)}
        />
        <YAxis
          yAxisId="pct"
          orientation="right"
          domain={["auto", "auto"]}
          tick={{ fontSize: 10 }}
          tickLine={false}
          axisLine={false}
          width={40}
          tickFormatter={(value: number) => `${String(Math.round(value * 10) / 10)}%`}
        />
        <Tooltip
          contentStyle={TOOLTIP_STYLE}
          labelFormatter={(value) => formatDateKeyShort(String(value))}
          formatter={(value: unknown, name: unknown) => {
            if (value === null || value === undefined) return "—";
            const isFat = name === t.stats.legendBodyFat;
            return `${formatNumberEs(Number(value), 1)}${isFat ? "%" : " kg"}`;
          }}
        />
        <Legend wrapperStyle={{ fontSize: 12 }} />
        <Line
          yAxisId="kg"
          type="monotone"
          dataKey="weight"
          name={t.stats.legendWeight}
          stroke="var(--chart-1)"
          strokeWidth={2}
          dot={{ r: 2 }}
          connectNulls
        />
        <Line
          yAxisId="kg"
          type="monotone"
          dataKey="weightTrend"
          name={t.stats.legendTrend}
          stroke="var(--chart-3)"
          strokeWidth={2}
          strokeDasharray="6 4"
          dot={false}
          connectNulls
        />
        <Line
          yAxisId="pct"
          type="monotone"
          dataKey="bodyFatPct"
          name={t.stats.legendBodyFat}
          stroke="var(--chart-2)"
          strokeWidth={2}
          strokeDasharray="3 3"
          dot={{ r: 2 }}
          connectNulls
        />
      </ComposedChart>
    </ResponsiveContainer>
  );
}