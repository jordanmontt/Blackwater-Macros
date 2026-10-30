package com.blackwatermacros.app.core

import kotlin.math.pow
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
 * One point per day: the mean of that day's weigh-ins, sorted by date (web
 * `dailyMeans`). Several weigh-ins on one day are not independent measurements
 * of the trend.
 */
fun dailyMeans(points: List<DataPoint>): List<DataPoint> =
    points.groupBy { it.date }
        .toSortedMap()
        .map { (date, values) -> DataPoint(date, values.sumOf { it.value } / values.size) }

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
 * one point per weigh-in. The counts come back even when they are not enough,
 * so the app can say what is missing (web `measureExpenditure`).
 */
fun measureExpenditure(intake: List<DataPoint>, weights: List<DataPoint>, today: String): ExpenditureData {
    val from = addDaysToKey(today, -EXPENDITURE_WINDOW_DAYS)
    val inWindow = { point: DataPoint -> point.date >= from && point.date < today }

    val caloriesByDay = linkedMapOf<String, Double>()
    for (point in intake.filter(inWindow)) {
        caloriesByDay[point.date] = (caloriesByDay[point.date] ?: 0.0) + point.value
    }
    val loggedTotals = caloriesByDay.values.filter { it > 0 }
    val loggedDays = loggedTotals.size

    val weighIns = dailyMeans(weights.filter(inWindow))
    val weighInDays = weighIns.size
    val weightSpanDays = if (weighInDays < 2) 0 else daysBetweenKeys(weighIns.first().date, weighIns.last().date)
    val trend = fitWeightTrend(weighIns)
    val margin = trend?.let { mathRound(Z_95 * it.slopeError * KCAL_PER_KG) }

    val data = ExpenditureData(loggedDays, weighInDays, weightSpanDays, margin, estimate = null)
    if (loggedDays < MIN_LOGGED_DAYS || weighInDays < MIN_WEIGH_INS || weightSpanDays < MIN_WEIGHT_SPAN_DAYS) return data
    if (trend == null || margin == null || margin > MAX_MARGIN_KCAL) return data

    val avgIntake = loggedTotals.sum() / loggedDays
    val tdee = mathRound(avgIntake - trend.slopePerDay * KCAL_PER_KG)
    if (tdee <= 0) return data

    return data.copy(
        estimate = ExpenditureEstimate(
            tdee = tdee,
            margin = margin,
            avgIntake = mathRound(avgIntake),
            weightChangePerWeek = mathRound(trend.slopePerDay * 7 * 100) / 100,
            loggedDays = loggedDays,
            weighIns = weighInDays,
            windowDays = EXPENDITURE_WINDOW_DAYS,
        ),
    )
}

/** The measured expenditure, or null until the data can support it (see [measureExpenditure]). */
fun estimateExpenditure(intake: List<DataPoint>, weights: List<DataPoint>, today: String): ExpenditureEstimate? =
    measureExpenditure(intake, weights, today).estimate

// --- The target follows the data (web `combineExpenditure`) ---

/**
 * One standard deviation of the profile's expenditure (BMR x activity factor)
 * for one person, as a fraction of it. Mifflin-St Jeor alone predicts the
 * basal rate within ±10 % for about 82 % of healthy adults (Frankenfield 2005),
 * an SD of about 7.5 %; the activity factor adds its own error.
 */
const val FORMULA_SD = 0.1

/** What a target is built on, and the measured expenditure's weight in it (0–1, 2 decimals). */
data class CombinedExpenditure(val expenditure: Double, val dataShare: Double)

/**
 * The expenditure a target is built on: the profile's and the measured one
 * combined by their precision (inverse-variance weighting):
 *
 *   share = σ_profile² / (σ_profile² + σ_measured²),  σ_profile = 10 % x profile,  σ_measured = margin / 1.96
 *   expenditure = profile + share x (measured − profile)
 *
 * Without a measurement it is the profile's.
 */
fun combineExpenditure(formulaTdee: Double, measured: ExpenditureEstimate?): CombinedExpenditure {
    if (measured == null) return CombinedExpenditure(formulaTdee, 0.0)
    val formulaVariance = (FORMULA_SD * formulaTdee).pow(2)
    val measuredVariance = (measured.margin / Z_95).pow(2)
    val share = formulaVariance / (formulaVariance + measuredVariance)
    return CombinedExpenditure(
        expenditure = mathRound(formulaTdee + share * (measured.tdee - formulaTdee)),
        dataShare = mathRound(share * 100) / 100,
    )
}

/**
 * The measured expenditure each day's target uses: the one taken at the start
 * of its week (Monday, [weekStartOf]) from the 4 weeks before, so targets change
 * once a week, not with every weigh-in. Memoized per week: Progreso asks for
 * every day of a period.
 */
fun weeklyExpenditure(intake: List<DataPoint>, weights: List<DataPoint>): (String) -> ExpenditureEstimate? {
    val byWeek = HashMap<String, ExpenditureEstimate?>()
    return { day ->
        val monday = weekStartOf(day)
        byWeek.getOrPut(monday) { estimateExpenditure(intake, weights, monday) }
    }
}

// --- Checking the profile against the data (web `checkExpenditure`) ---

/**
 * How far the formula can be off for anyone, as a fraction of its expenditure:
 * Mifflin-St Jeor predicts the basal rate within ±10 % in most healthy adults
 * (Frankenfield 2005). Gaps inside this band are its normal error.
 */
const val FORMULA_TOLERANCE = 0.1

/** The verdict must also hold on the window that ended a week earlier. */
const val CHECK_CONFIRM_DAYS = 7

/** Days of meals the check reads: this window plus the one a week earlier. */
const val CHECK_DAYS = EXPENDITURE_WINDOW_DAYS + CHECK_CONFIRM_DAYS

/** A dismissed notice comes back after a whole new window of data. */
const val NOTICE_SNOOZE_DAYS = EXPENDITURE_WINDOW_DAYS

/** What stops the measurement, in the order the user should fix it. */
fun missingExpenditureData(data: ExpenditureData): List<MissingExpenditureData> = buildList {
    if (data.loggedDays < MIN_LOGGED_DAYS) add(MissingExpenditureData.MEALS)
    when {
        data.weighInDays < MIN_WEIGH_INS -> add(MissingExpenditureData.WEIGH_INS)
        data.weightSpanDays < MIN_WEIGHT_SPAN_DAYS -> add(MissingExpenditureData.WEIGHT_SPAN)
        data.margin == null || data.margin > MAX_MARGIN_KCAL -> add(MissingExpenditureData.MARGIN)
    }
}

/** Strict: the whole 95 % range must lie outside formula ± 10 %. */
private fun verdictOf(estimate: ExpenditureEstimate, normalMin: Double, normalMax: Double): ExpenditureVerdict = when {
    estimate.tdee - estimate.margin > normalMax -> ExpenditureVerdict.HIGHER
    estimate.tdee + estimate.margin < normalMin -> ExpenditureVerdict.LOWER
    else -> ExpenditureVerdict.MATCHES
}

/**
 * Checks the profile's expenditure (BMR × activity factor) against the one
 * measured from meals and weigh-ins. HIGHER or LOWER is `confirmed` only if the
 * window that ended a week earlier says the same. `intake` must cover
 * [CHECK_DAYS] before today.
 */
fun checkExpenditure(
    intake: List<DataPoint>,
    weights: List<DataPoint>,
    formula: CalorieRecommendation,
    today: String,
): ExpenditureCheck {
    val data = measureExpenditure(intake, weights, today)
    val estimate = data.estimate ?: return ExpenditureCheck.NotEnoughData(data, missingExpenditureData(data))

    val normalMin = mathRound(formula.tdee * (1 - FORMULA_TOLERANCE))
    val normalMax = mathRound(formula.tdee * (1 + FORMULA_TOLERANCE))
    val verdict = verdictOf(estimate, normalMin, normalMax)
    val confirmed = verdict == ExpenditureVerdict.MATCHES ||
        estimateExpenditure(intake, weights, addDaysToKey(today, -CHECK_CONFIRM_DAYS))
            ?.let { verdictOf(it, normalMin, normalMax) == verdict } == true
    return ExpenditureCheck.Measured(
        verdict = verdict,
        confirmed = confirmed,
        estimate = estimate,
        formulaTdee = formula.tdee,
        bmr = formula.bmr,
        activityFactor = formula.activityFactor,
        normalMin = normalMin,
        normalMax = normalMax,
        gap = estimate.tdee - formula.tdee,
        impliedActivityFactor = mathRound(estimate.tdee / formula.bmr * 100) / 100,
    )
}

/** The gap as the Today card says it: «about 500», rounded to 50 kcal. */
fun approximateGap(gap: Double): Double = mathRound(kotlin.math.abs(gap) / 50) * 50

/**
 * The Today card mentions the gap only when it is confirmed, and not again
 * after «Not now» unless the verdict changes or a whole new window has passed.
 */
fun showsGapNotice(check: ExpenditureCheck?, dismissed: ExpenditureNoticeDismissal?, today: String): Boolean {
    if (check !is ExpenditureCheck.Measured || check.verdict == ExpenditureVerdict.MATCHES || !check.confirmed) return false
    if (dismissed == null || dismissed.verdict != check.verdict) return true
    return daysBetweenKeys(dismissed.date, today) >= NOTICE_SNOOZE_DAYS
}

/** Meals are logged well enough, only the weigh-ins are missing: shown once on Today until dismissed. */
fun onlyWeighInsMissing(check: ExpenditureCheck?): Boolean =
    check is ExpenditureCheck.NotEnoughData && check.missing.isNotEmpty() && MissingExpenditureData.MEALS !in check.missing
