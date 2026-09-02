package com.blackwatermacros.app.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Mirrors `src/lib/core/stats.ts`: moving average, linear regression (days/week),
 * weekly averages, dense daily series, and range-to-days mapping.
 */

data class DataPoint(val date: String, val value: Double)

/**
 * Trailing moving average over a calendar window. For each point, averages all
 * points whose date falls within `windowDays` ending at that point's date
 * (inclusive). Tolerant of gaps: missing days simply contribute no value.
 */
fun movingAverageByDays(points: List<DataPoint>, windowDays: Int): List<Double?> {
    val windowStartOffsets = points.map { addDaysToKey(it.date, -(windowDays - 1)) }
    return points.mapIndexed { i, point ->
        val start = windowStartOffsets[i]
        val valuesInWindow = ArrayList<Double>()
        for (j in 0..i) {
            // ISO YYYY-MM-DD keys compare chronologically as strings.
            if (points[j].date >= start) {
                valuesInWindow.add(points[j].value)
            }
        }
        if (valuesInWindow.isEmpty()) null
        else valuesInWindow.sum() / valuesInWindow.size
    }
}

/**
 * Ordinary least-squares slope of value over time, in units per day.
 * Returns null when there are fewer than two distinct dates (denominator 0).
 */
fun linearSlopePerDay(points: List<DataPoint>): Double? {
    if (points.size < 2) return null
    val x0 = LocalDate.parse(points[0].date)
    val xs = points.map { ChronoUnit.DAYS.between(x0, LocalDate.parse(it.date)).toDouble() }
    val ys = points.map { it.value }
    val n = points.size
    val meanX = xs.sum() / n
    val meanY = ys.sum() / n
    var num = 0.0
    var den = 0.0
    for (i in 0 until n) {
        num += (xs[i] - meanX) * (ys[i] - meanY)
        den += (xs[i] - meanX) * (xs[i] - meanX)
    }
    if (den == 0.0) return null
    return num / den
}

/** Least-squares rate of change expressed per week. */
fun linearRatePerWeek(points: List<DataPoint>): Double? {
    val slope = linearSlopePerDay(points)
    return slope?.times(7)
}

private fun previousMonday(key: String): String {
    val date = LocalDate.parse(key)
    val weekday = date.dayOfWeek.value // Monday = 1 .. Sunday = 7
    val diff = if (weekday == 7) 6 else weekday - 1
    return addDaysToKey(key, -diff)
}

/**
 * Groups points into ISO weeks (starting Monday) and averages each week.
 * Only weeks that contain at least one entry are returned.
 */
fun weeklyAverages(points: List<DataPoint>): List<WeeklyAvg> {
    val buckets = LinkedHashMap<String, MutableList<Double>>()
    for (point in points) {
        val weekStart = previousMonday(point.date)
        buckets.getOrPut(weekStart) { ArrayList() }.add(point.value)
    }
    return buckets.entries
        .sortedBy { it.key }
        .map { (weekStart, values) ->
            WeeklyAvg(weekStart = weekStart, avg = values.sum() / values.size)
        }
}

/**
 * Builds a dense daily series between two date keys (inclusive). Days without
 * meals get zero calories/protein so charts show honest gaps.
 */
fun buildDailyNutritionSeries(
    totalsByDate: Map<String, DailyNutritionPoint>,
    fromKey: String,
    toKey: String,
): List<DailyNutritionPoint> {
    val series = ArrayList<DailyNutritionPoint>()
    var cursor = fromKey
    while (daysBetweenKeys(cursor, toKey) >= 0) {
        val entry = totalsByDate[cursor]
        series.add(
            entry ?: DailyNutritionPoint(cursor, 0.0, 0.0, 0.0, 0.0),
        )
        cursor = addDaysToKey(cursor, 1)
    }
    return series
}

/** Number of days covered by a named stats range, or null for "all time". */
fun rangeToDays(range: StatsRange): Int? = when (range) {
    StatsRange.RANGE_7D -> 7
    StatsRange.RANGE_30D -> 30
    StatsRange.RANGE_90D -> 90
    StatsRange.ALL -> null
}