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
 * 0 kcal. Null until the data can support it: >= 21 logged days and >= 4
 * weigh-in days spanning >= 14 days. There is no largest margin: a wide one
 * makes the data count less (`combineExpenditure`) and the check harder to
 * trigger, both gradually, so the target never jumps at a cut-off.
 */

const val EXPENDITURE_WINDOW_DAYS = 28
const val MIN_LOGGED_DAYS = 21
const val MIN_WEIGH_INS = 4
const val MIN_WEIGHT_SPAN_DAYS = 14

/** Approximate energy in 1 kg of body-weight change (Hall 2008). */
const val KCAL_PER_KG = 7700.0

/** Smallest day-to-day scatter assumed for a weigh-in around the trend. */
const val WEIGHT_NOISE_FLOOR_KG = 0.5

/** Two-sided 95 % normal quantile. */
const val Z_95 = 1.96

/**
 * One point per day: the mean of that day's weigh-ins, sorted by date (web
 * `dailyMeans`). Several weigh-ins on one day are not independent measurements
 * of the trend.
 */
fun dailyMeans(points: List<DataPoint>): List<DataPoint> =
    points.groupBy { it.date }
        .toSortedMap()
        .map { (date, values) -> DataPoint(date, values.sumOf { it.value } / values.size) }

// --- Weigh-ins taken in the same conditions (web `groupWeighIns`) ---

/**
 * A weigh-in as the weight trend needs it: its local day and kg, plus the
 * conditions it was taken in. A weigh-in after eating or training reads about
 * 0.5-1 kg above one on waking, and another scale reads differently, so only
 * weigh-ins taken alike are compared ([groupWeighIns]).
 */
data class WeighIn(
    val date: String,
    val value: Double,
    /** ISO instant: orders weigh-ins across days and time zones. Default: the date. */
    val time: String = date,
    /** On waking, before eating or drinking. Default true, what the app has always asked for. */
    val fasted: Boolean = true,
    /** The device's time zone when it was saved ("Europe/Zurich"); null = unknown. */
    val timeZone: String? = null,
)

/** Weigh-ins as the fit takes them: one point per group and day, sorted by date. */
data class GroupedWeighIns(
    val points: List<DataPoint>,
    /** The group of each point: "f" (fasted) or "n" (not), then the time-zone period ("f0", "n1"...). */
    val groups: List<String>,
)

/**
 * Puts together the weigh-ins taken in the same conditions: fasted or not, and
 * in the same time zone. The period goes up when a weigh-in's known zone differs
 * from the last known one; an unknown zone never starts one. The groups come
 * only from the switch and the zone, never from the weights. One point per group
 * and day: the mean (web `groupWeighIns`).
 */
fun groupWeighIns(weighIns: List<WeighIn>): GroupedWeighIns {
    val ordered = weighIns.sortedWith(compareBy { it.time })
    val byGroupDay = linkedMapOf<String, Triple<String, String, MutableList<Double>>>()
    var period = 0
    var lastZone: String? = null
    for (weighIn in ordered) {
        val zone = weighIn.timeZone
        if (zone != null) {
            if (lastZone != null && zone != lastZone) period++
            lastZone = zone
        }
        val group = "${if (weighIn.fasted) "f" else "n"}$period"
        val entry = byGroupDay.getOrPut("$group#${weighIn.date}") { Triple(weighIn.date, group, mutableListOf()) }
        entry.third += weighIn.value
    }
    val entries = byGroupDay.values.sortedWith(compareBy({ it.first }, { it.second }))
    return GroupedWeighIns(
        points = entries.map { (date, _, values) -> DataPoint(date, values.sum() / values.size) },
        groups = entries.map { it.second },
    )
}

/** Least-squares line through weigh-ins, with what is needed for its error. */
data class WeightTrend(
    val slopePerDay: Double,
    /** Standard error of the slope, kg/day. */
    val slopeError: Double,
    /** Scatter around the line, kg (never below the noise floor). */
    val sigma: Double,
    /** Points the fit used (groups with a single day are left out). */
    val n: Int,
    /** The level of the latest weigh-in's group: mean x and y and its points. With one group, all. */
    val meanX: Double,
    val meanY: Double,
    val levelN: Int,
    /** Sum of (x - mean x)^2 within each group, added up. */
    val sxx: Double,
    /** Date of the first weigh-in: x = 0. */
    val origin: String,
    /** The groups the fit used, oldest first. */
    val groups: List<String>,
)

private class GroupSums(val group: String, val xs: List<Double>, val ys: List<Double>) {
    val meanX = xs.sum() / xs.size
    val meanY = ys.sum() / ys.size
}

private class WithinGroups(val origin: String, val used: List<GroupSums>, val sxx: Double, val slopePerDay: Double?)

/** The time-zone period of a group ("f1" -> 1; the default group "" -> 0). */
private fun periodOf(group: String): Int = group.drop(1).toIntOrNull() ?: 0

/** The later last day; on the same day the later time-zone period, then fasted (web `isLaterGroup`). */
private fun isLaterGroup(g: GroupSums, best: GroupSums): Boolean {
    val days = g.xs.max() - best.xs.max()
    if (days != 0.0) return days > 0
    if (periodOf(g.group) != periodOf(best.group)) return periodOf(g.group) > periodOf(best.group)
    return g.group < best.group
}

private fun withinGroups(points: List<DataPoint>, groups: List<String>): WithinGroups {
    val origin = points[0].date
    val byGroup = linkedMapOf<String, Pair<MutableList<Double>, MutableList<Double>>>()
    points.forEachIndexed { i, point ->
        val entry = byGroup.getOrPut(groups[i]) { mutableListOf<Double>() to mutableListOf() }
        entry.first += daysBetweenKeys(origin, point.date).toDouble()
        entry.second += point.value
    }
    val used = byGroup.entries.filter { it.value.first.size >= 2 }.map { (group, xy) -> GroupSums(group, xy.first, xy.second) }
    var sxx = 0.0
    var sxy = 0.0
    for (g in used) {
        g.xs.forEachIndexed { i, x ->
            sxx += (x - g.meanX) * (x - g.meanX)
            sxy += (x - g.meanX) * (g.ys[i] - g.meanY)
        }
    }
    return WithinGroups(origin, used, sxx, if (sxx == 0.0) null else sxy / sxx)
}

/**
 * Least-squares weight trend and its standard error, compared only within
 * groups of weigh-ins taken alike ([groups], from [groupWeighIns]; default one
 * group): each group has its own level, all share one slope (fixed effects).
 * beta = sum Sxy_g / sum Sxx_g, sigma = max(sqrt(RSS / (n - G - 1)), 0.5 kg),
 * SE = sigma / sqrt(sum Sxx_g). Groups with a single day are left out. Points
 * must be sorted by date. Null without a degree of freedom or spread.
 */
fun fitWeightTrend(points: List<DataPoint>, groups: List<String> = List(points.size) { "" }): WeightTrend? {
    if (points.isEmpty()) return null
    val within = withinGroups(points, groups)
    val used = within.used
    val slopePerDay = within.slopePerDay ?: return null
    val n = used.sumOf { it.xs.size }
    if (n - used.size - 1 < 1) return null
    var squaredResiduals = 0.0
    for (g in used) {
        g.xs.forEachIndexed { i, x ->
            val residual = g.ys[i] - (g.meanY + slopePerDay * (x - g.meanX))
            squaredResiduals += residual * residual
        }
    }
    val sigma = maxOf(sqrt(squaredResiduals / (n - used.size - 1)), WEIGHT_NOISE_FLOOR_KG)
    val latest = used.reduce { best, g -> if (isLaterGroup(g, best)) g else best }
    return WeightTrend(
        slopePerDay = slopePerDay,
        slopeError = sigma / sqrt(within.sxx),
        sigma = sigma,
        n = n,
        meanX = latest.meanX,
        meanY = latest.meanY,
        levelN = latest.xs.size,
        sxx = within.sxx,
        origin = within.origin,
        groups = used.map { it.group },
    )
}

/** Only the slope within groups (kg/day), for a rate on screen (Progreso). Null without spread. */
fun slopeWithinGroups(grouped: GroupedWeighIns): Double? =
    if (grouped.points.isEmpty()) null else withinGroups(grouped.points, grouped.groups).slopePerDay

/**
 * `intake` holds one point per meal (date = log date, value = kcal); `weights`
 * one point per weigh-in with the conditions it was taken in ([WeighIn]): the
 * trend compares only weigh-ins taken alike ([groupWeighIns]). The counts come
 * back even when they are not enough, so the app can say what is missing (web
 * `measureExpenditure`).
 */
fun measureExpenditure(intake: List<DataPoint>, weights: List<WeighIn>, today: String): ExpenditureData {
    val from = addDaysToKey(today, -EXPENDITURE_WINDOW_DAYS)
    val inWindow = { date: String -> date >= from && date < today }

    val caloriesByDay = linkedMapOf<String, Double>()
    for (point in intake.filter { inWindow(it.date) }) {
        caloriesByDay[point.date] = (caloriesByDay[point.date] ?: 0.0) + point.value
    }
    val loggedTotals = caloriesByDay.values.filter { it > 0 }
    val loggedDays = loggedTotals.size

    val grouped = groupWeighIns(weights.filter { inWindow(it.date) })
    val days = grouped.points.map { it.date }.distinct()
    val weighInDays = days.size
    val weightSpanDays = if (weighInDays < 2) 0 else daysBetweenKeys(days.first(), days.last())
    val trend = fitWeightTrend(grouped.points, grouped.groups)
    val margin = trend?.let { mathRound(Z_95 * it.slopeError * KCAL_PER_KG) }

    val data = ExpenditureData(loggedDays, weighInDays, weightSpanDays, margin, estimate = null)
    if (loggedDays < MIN_LOGGED_DAYS || weighInDays < MIN_WEIGH_INS || weightSpanDays < MIN_WEIGHT_SPAN_DAYS) return data
    if (trend == null || margin == null) return data

    val avgIntake = loggedTotals.sum() / loggedDays
    val tdee = mathRound(avgIntake - trend.slopePerDay * KCAL_PER_KG)
    if (tdee <= 0) return data

    val used = trend.groups
    val latestPeriod = used.maxOf { periodOf(it) }
    val firstInLatest = grouped.points.indices
        .firstOrNull { grouped.groups[it] in used && periodOf(grouped.groups[it]) == latestPeriod }
        ?.let { grouped.points[it].date }
    return data.copy(
        estimate = ExpenditureEstimate(
            tdee = tdee,
            margin = margin,
            avgIntake = mathRound(avgIntake),
            weightChangePerWeek = mathRound(trend.slopePerDay * 7 * 100) / 100,
            loggedDays = loggedDays,
            weighIns = weighInDays,
            windowDays = EXPENDITURE_WINDOW_DAYS,
            groups = used.size,
            mixedFasting = used.any { it.startsWith("f") } && used.any { it.startsWith("n") },
            newZoneSince = if (used.map { periodOf(it) }.distinct().size > 1) firstInLatest else null,
        ),
    )
}

/** The measured expenditure, or null until the data can support it (see [measureExpenditure]). */
fun estimateExpenditure(intake: List<DataPoint>, weights: List<WeighIn>, today: String): ExpenditureEstimate? =
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
fun weeklyExpenditure(intake: List<DataPoint>, weights: List<WeighIn>): (String) -> ExpenditureEstimate? {
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

/**
 * What stops the measurement, in the order the user should fix it. Enough
 * weigh-in days with no trend (`margin` null) means no group of weigh-ins taken
 * alike has 2+ days yet: more weigh-ins are needed.
 */
fun missingExpenditureData(data: ExpenditureData): List<MissingExpenditureData> = buildList {
    if (data.loggedDays < MIN_LOGGED_DAYS) add(MissingExpenditureData.MEALS)
    when {
        data.weighInDays < MIN_WEIGH_INS -> add(MissingExpenditureData.WEIGH_INS)
        data.weightSpanDays < MIN_WEIGHT_SPAN_DAYS -> add(MissingExpenditureData.WEIGHT_SPAN)
        data.margin == null -> add(MissingExpenditureData.WEIGH_INS)
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
    weights: List<WeighIn>,
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
