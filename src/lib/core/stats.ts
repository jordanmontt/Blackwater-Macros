import { addDaysToKey, daysBetweenKeys } from "./dates";
import type { DailyNutritionPoint, StatsRange } from "./types";

export interface DataPoint {
  date: string;
  value: number;
}

/**
 * Trailing moving average over a calendar window.
 *
 * For each data point, the average of all points whose date falls within the
 * `windowDays` ending at that point's date (inclusive). Tolerant of gaps:
 * days without entries simply contribute no value instead of breaking the
 * average. This is the standard "trend weight" smoothing used by weight
 * trackers to remove day-to-day water-weight noise.
 */
export function movingAverageByDays(points: DataPoint[], windowDays: number): (number | null)[] {
  const windowStartOffsets = points.map((point) => addDaysToKey(point.date, -(windowDays - 1)));
  return points.map((point, i) => {
    const start = windowStartOffsets[i];
    const valuesInWindow: number[] = [];
    for (let j = 0; j <= i; j++) {
      if (points[j].date >= start) {
        valuesInWindow.push(points[j].value);
      }
    }
    if (valuesInWindow.length === 0) return null;
    return valuesInWindow.reduce((a, b) => a + b, 0) / valuesInWindow.length;
  });
}

/**
 * Least-squares slope of value over time, in units per day.
 * Returns null when there are fewer than two distinct dates.
 */
export function linearSlopePerDay(points: DataPoint[]): number | null {
  if (points.length < 2) return null;
  const x0 = new Date(`${points[0].date}T00:00:00`).getTime();
  const xs = points.map((p) => (new Date(`${p.date}T00:00:00`).getTime() - x0) / 86_400_000);
  const ys = points.map((p) => p.value);
  const n = points.length;
  const meanX = xs.reduce((a, b) => a + b, 0) / n;
  const meanY = ys.reduce((a, b) => a + b, 0) / n;
  let num = 0;
  let den = 0;
  for (let i = 0; i < n; i++) {
    num += (xs[i] - meanX) * (ys[i] - meanY);
    den += (xs[i] - meanX) ** 2;
  }
  if (den === 0) return null;
  return num / den;
}

/** Least-squares rate of change expressed per week. */
export function linearRatePerWeek(points: DataPoint[]): number | null {
  const slope = linearSlopePerDay(points);
  return slope === null ? null : slope * 7;
}

function previousMonday(key: string): string {
  const date = new Date(`${key}T00:00:00`);
  const weekday = date.getDay(); // 0 = Sunday
  const diff = weekday === 0 ? 6 : weekday - 1;
  return addDaysToKey(key, -diff);
}

/**
 * Groups points into ISO weeks (starting Monday) and averages each week.
 * Only weeks that contain at least one entry are returned.
 */
export function weeklyAverages(points: DataPoint[]): { weekStart: string; avg: number }[] {
  const buckets = new Map<string, number[]>();
  for (const point of points) {
    const weekStart = previousMonday(point.date);
    const bucket = buckets.get(weekStart) ?? [];
    bucket.push(point.value);
    buckets.set(weekStart, bucket);
  }
  return [...buckets.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([weekStart, values]) => ({
      weekStart,
      avg: values.reduce((a, b) => a + b, 0) / values.length,
    }));
}

/**
 * Builds a dense daily series between two date keys (inclusive).
 * Days without logged meals get zero calories and protein so charts show
 * honest gaps in adherence rather than skipping the day entirely.
 */
export function buildDailyNutritionSeries(
  totalsByDate: Map<string, DailyNutritionPoint>,
  fromKey: string,
  toKey: string,
): DailyNutritionPoint[] {
  const series: DailyNutritionPoint[] = [];
  let cursor = fromKey;
  while (daysBetweenKeys(cursor, toKey) >= 0) {
    const entry = totalsByDate.get(cursor);
    series.push(entry ?? { date: cursor, calories: 0, protein: 0, carbs: 0, fat: 0 });
    cursor = addDaysToKey(cursor, 1);
  }
  return series;
}

/** Number of days covered by a named stats range, or null for "all time". */
export function rangeToDays(range: StatsRange): number | null {
  switch (range) {
    case "7d":
      return 7;
    case "30d":
      return 30;
    case "90d":
      return 90;
    case "all":
      return null;
  }
}
