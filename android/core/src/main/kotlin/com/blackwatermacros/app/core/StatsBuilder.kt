package com.blackwatermacros.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Mirrors `src/lib/core/stats-builder.ts`. Builds the whole stats page from meals
 * and weights. `todayKeyParam` is the caller's local calendar day so ranges stay
 * anchored to the user's timezone.
 */

/** Minimal shape of a meal needed to compute daily nutrition series. */
data class StatsMeal(
    val logDate: String,
    val resolvedCalories: Double,
    val resolvedProtein: Double,
    val resolvedCarbs: Double,
    val resolvedFat: Double,
)

/** Minimal shape of a weight row needed to compute weight/body-fat series. */
data class StatsWeight(
    val measuredAt: Instant,
    val weightKg: Double,
    val bodyFatPct: Double?,
)

private const val TREND_WINDOW_DAYS = 7

fun buildStatsFromData(
    meals: List<StatsMeal>,
    weights: List<StatsWeight>,
    range: StatsRange,
    todayKeyParam: String,
): StatsSummary {
    val days = rangeToDays(range)
    val toKey = todayKeyParam
    val fromKey = if (days == null) null else addDaysToKey(toKey, -(days - 1))

    // --- Daily calories / protein / carbs / fat ---
    val totalsByDate = LinkedHashMap<String, DailyNutritionPoint>()
    for (meal in meals) {
        val current = totalsByDate[meal.logDate] ?: DailyNutritionPoint(meal.logDate, 0.0, 0.0, 0.0, 0.0)
        totalsByDate[meal.logDate] = current.copy(
            calories = current.calories + meal.resolvedCalories,
            protein = current.protein + meal.resolvedProtein,
            carbs = current.carbs + meal.resolvedCarbs,
            fat = current.fat + meal.resolvedFat,
        )
    }

    val seriesFrom = fromKey ?: meals.firstOrNull()?.logDate ?: toKey
    val nutritionSeries =
        if (meals.isNotEmpty() || fromKey != null) {
            buildDailyNutritionSeries(totalsByDate, seriesFrom, toKey)
        } else {
            emptyList()
        }

    val caloriesPoints = nutritionSeries.filter { it.calories > 0 }
    val proteinPoints = nutritionSeries.filter { it.protein > 0 }
    val carbsPoints = nutritionSeries.filter { it.carbs > 0 }
    val fatPoints = nutritionSeries.filter { it.fat > 0 }

    // --- Weight ---
    val fromTime = if (fromKey != null) {
        // Cutoff parsed at UTC midnight to mirror `new Date(fromKey + "T00:00:00Z")`.
        Instant.parse("${fromKey}T00:00:00Z").toEpochMilli()
    } else {
        0L
    }
    val inRange = weights.filter { it.measuredAt.toEpochMilli() >= fromTime }
    val weightPoints: List<DataPoint> = inRange.map { row ->
        DataPoint(date = localDateOf(row.measuredAt), value = row.weightKg)
    }

    val trendValues = movingAverageByDays(weightPoints, TREND_WINDOW_DAYS)
    val weightSeries = weightPoints.mapIndexed { i, point ->
        WeightPoint(
            date = point.date,
            weight = round2(point.value),
            trend = trendValues[i]?.let { round1(it) },
        )
    }

    // --- Body fat % series (only entries with valid body fat data) ---
    val bodyFatRows = inRange.filter { it.bodyFatPct != null && it.bodyFatPct!! > 0 && it.bodyFatPct!! < 100 }
    val bodyFatPoints: List<DataPoint> = bodyFatRows.map { row ->
        DataPoint(date = localDateOf(row.measuredAt), value = row.bodyFatPct!!)
    }
    val bodyFatTrendValues = movingAverageByDays(bodyFatPoints, TREND_WINDOW_DAYS)
    val bodyFatSeries = bodyFatPoints.mapIndexed { i, point ->
        BodyFatPoint(
            date = point.date,
            bodyFatPct = round1(point.value),
            trend = bodyFatTrendValues[i]?.let { round1(it) },
        )
    }

    val lastValue = weightPoints.lastOrNull()?.value
    val firstValue = weightPoints.firstOrNull()?.value
    val lastTrend = trendValues.reversed().firstOrNull { it != null }
    val ratePerWeekKg = linearRatePerWeek(weightPoints)

    // Body fat stats
    val lastBodyFat = bodyFatPoints.lastOrNull()?.value
    val firstBodyFat = bodyFatPoints.firstOrNull()?.value

    val weightSummary = CompositionStats(
        currentWeightKg = lastValue?.let { round2(it) },
        currentTrendKg = lastTrend?.let { round1(it) },
        changeSinceStartKg = if (firstValue != null && lastValue != null) round2(lastValue - firstValue) else null,
        ratePerWeekKg = ratePerWeekKg?.let { round2(it) },
        minKg = if (weightPoints.isNotEmpty()) round2(weightPoints.minOf { it.value }) else null,
        maxKg = if (weightPoints.isNotEmpty()) round2(weightPoints.maxOf { it.value }) else null,
        currentBodyFatPct = lastBodyFat?.let { round1(it) },
        changeBodyFatPct = if (firstBodyFat != null && lastBodyFat != null) round1(lastBodyFat - firstBodyFat) else null,
        minBodyFatPct = if (bodyFatPoints.isNotEmpty()) round1(bodyFatPoints.minOf { it.value }) else null,
        maxBodyFatPct = if (bodyFatPoints.isNotEmpty()) round1(bodyFatPoints.maxOf { it.value }) else null,
    )

    return StatsSummary(
        calories = nutritionSeries.map { it.copy(calories = round1(it.calories)) },
        protein = nutritionSeries.map { it.copy(protein = round1(it.protein)) },
        carbs = nutritionSeries.map { it.copy(carbs = round1(it.carbs)) },
        fat = nutritionSeries.map { it.copy(fat = round1(it.fat)) },
        weights = weightSeries,
        bodyFat = bodyFatSeries,
        caloriesAvg = averageOf(caloriesPoints.map { it.calories }),
        caloriesMaxDay = maxBy(caloriesPoints) { it.calories },
        proteinAvg = averageOf(proteinPoints.map { it.protein }),
        proteinMaxDay = maxBy(proteinPoints) { it.protein },
        carbsAvg = averageOf(carbsPoints.map { it.carbs }),
        carbsMaxDay = maxBy(carbsPoints) { it.carbs },
        fatAvg = averageOf(fatPoints.map { it.fat }),
        fatMaxDay = maxBy(fatPoints) { it.fat },
        weight = weightSummary,
        weeklyWeightAvg = weeklyAverages(weightPoints).map { it.copy(avg = round2(it.avg)) },
    )
}

private fun averageOf(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    return mathRound(values.sum() / values.size)
}

private fun <T> maxBy(items: List<T>, selector: (T) -> Double): T? {
    var best: T? = null
    for (item in items) {
        if (best == null || selector(item) > selector(best)) best = item
    }
    return best
}

/** The local calendar day (in the system default zone) of an instant. */
private fun localDateOf(instant: Instant): String =
    toDateKey(instant.atZone(ZoneId.systemDefault()).toLocalDate())