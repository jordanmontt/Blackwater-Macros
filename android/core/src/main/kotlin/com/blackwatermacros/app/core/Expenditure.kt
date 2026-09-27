package com.blackwatermacros.app.core

import kotlin.math.sqrt

/**
 * Mirrors `src/lib/core/expenditure.ts`. Measured TDEE from energy balance
 * (the adaptive approach of apps such as MacroFactor):
 *
 *   TDEE = average daily intake - (weight slope in kg/day x 7700 kcal/kg)
 *
 * over the 28 days before today. Unlogged days are left out, never counted as
 * 0 kcal. Null until the data can support it: >= 21 logged days, >= 4 weigh-in
 * days spanning >= 14 days, and a 95 % margin of at most +-300 kcal/day.
 */

const val EXPENDITURE_WINDOW_DAYS = 28
const val MIN_LOGGED_DAYS = 21
const val MIN_WEIGH_INS = 4
const val MIN_WEIGHT_SPAN_DAYS = 14

/** Approximate energy in 1 kg of body-weight change (Hall 2008). */
const val KCAL_PER_KG = 7700.0

/** Smallest day-to-day scatter assumed for a weigh-in around the trend. */
const val WEIGHT_NOISE_FLOOR_KG = 0.5

/** Largest 95 % margin (kcal/day) at which the measured value is shown. */
const val MAX_MARGIN_KCAL = 300.0

/** Two-sided 95 % normal quantile. */
const val Z_95 = 1.96

/** Least-squares line through weigh-ins, with what is needed for its error. */
data class WeightTrend(
    val slopePerDay: Double,
    /** Standard error of the slope, kg/day. */
    val slopeError: Double,
    /** Scatter around the line, kg (never below the noise floor). */
    val sigma: Double,
    val n: Int,
    val meanX: Double,
    val meanY: Double,
    /** Sum of (x - mean x)^2. */
    val sxx: Double,
    /** Date of the first weigh-in: x = 0. */
    val origin: String,
)

/**
 * Least-squares weight trend and its standard error: SE = sigma / sqrt(Sxx),
 * sigma from the residuals (n - 2 degrees of freedom), never below
 * [WEIGHT_NOISE_FLOOR_KG]. Points must be sorted by date.
 */
fun fitWeightTrend(points: List<DataPoint>): WeightTrend? {
    val n = points.size
    if (n < 3) return null
    val origin = points[0].date
    val xs = points.map { daysBetweenKeys(origin, it.date).toDouble() }
    val ys = points.map { it.value }
    val meanX = xs.sum() / n
    val meanY = ys.sum() / n
    var sxx = 0.0
    var sxy = 0.0
    for (i in 0 until n) {
        sxx += (xs[i] - meanX) * (xs[i] - meanX)
        sxy += (xs[i] - meanX) * (ys[i] - meanY)
    }
    if (sxx == 0.0) return null
    val slopePerDay = sxy / sxx
    var squaredResiduals = 0.0
    for (i in 0 until n) {
        val residual = ys[i] - (meanY + slopePerDay * (xs[i] - meanX))
        squaredResiduals += residual * residual
    }
    val sigma = maxOf(sqrt(squaredResiduals / (n - 2)), WEIGHT_NOISE_FLOOR_KG)
    return WeightTrend(slopePerDay, sigma / sqrt(sxx), sigma, n, meanX, meanY, sxx, origin)
}

/**
 * `intake` holds one point per meal (date = log date, value = kcal); `weights`
 * one point per weigh-in.
 */
fun estimateExpenditure(intake: List<DataPoint>, weights: List<DataPoint>, today: String): ExpenditureEstimate? {
    val from = addDaysToKey(today, -EXPENDITURE_WINDOW_DAYS)
    val inWindow = { point: DataPoint -> point.date >= from && point.date < today }

    val caloriesByDay = linkedMapOf<String, Double>()
    for (point in intake.filter(inWindow)) {
        caloriesByDay[point.date] = (caloriesByDay[point.date] ?: 0.0) + point.value
    }
    val loggedTotals = caloriesByDay.values.filter { it > 0 }
    if (loggedTotals.size < MIN_LOGGED_DAYS) return null

    val weighIns = weights.filter(inWindow).sortedBy { it.date }
    val weighInDays = weighIns.map { it.date }.toSet().size
    if (weighInDays < MIN_WEIGH_INS) return null
    if (daysBetweenKeys(weighIns.first().date, weighIns.last().date) < MIN_WEIGHT_SPAN_DAYS) return null

    val trend = fitWeightTrend(weighIns) ?: return null
    val margin = mathRound(Z_95 * trend.slopeError * KCAL_PER_KG)
    if (margin > MAX_MARGIN_KCAL) return null

    val avgIntake = loggedTotals.sum() / loggedTotals.size
    val tdee = mathRound(avgIntake - trend.slopePerDay * KCAL_PER_KG)
    if (tdee <= 0) return null

    return ExpenditureEstimate(
        tdee = tdee,
        margin = margin,
        avgIntake = mathRound(avgIntake),
        weightChangePerWeek = mathRound(trend.slopePerDay * 7 * 100) / 100,
        loggedDays = loggedTotals.size,
        weighIns = weighInDays,
        windowDays = EXPENDITURE_WINDOW_DAYS,
    )
}
