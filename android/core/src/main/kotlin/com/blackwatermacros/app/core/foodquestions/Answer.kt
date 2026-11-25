package com.blackwatermacros.app.core.foodquestions

import com.blackwatermacros.app.core.mathRound
import com.blackwatermacros.app.core.plainNumber
import com.blackwatermacros.app.core.round1

/** A part of the log an answer counted, for «Cómo se calculó» (web `FoodGroup`). */
data class FoodGroup(
    val label: GroupLabel,
    val from: String,
    val to: String,
    /** Days with meals logged in it (the averages' divisor). */
    val loggedDays: Int,
    /** Calendar days in it. */
    val totalDays: Int,
)

enum class SplitReason(val id: String) { ASKED("asked"), STOPPED("stopped"), STARTED("started"), HALVES("halves") }

data class WeightSplit(val at: String, val reason: SplitReason)

/** What the app shows under the answer («Cómo se calculó»): written by the app, not the model. */
data class FoodDetails(
    val analysis: AnalysisId,
    val groups: List<FoodGroup>,
    /** Logged names counted, the most frequent first (at most `countedNamesShown`). */
    val counted: List<String>,
    val moreCounted: Int,
    /** Entries without an amount in g or ml (left out of the amounts). */
    val withoutAmount: Int,
    /** Fewer than `fewLoggedDays` logged days in a group: a hint, not a firm average. */
    val few: Boolean,
    /** Where food_vs_weight split the period, and why. */
    val split: WeightSplit? = null,
)

enum class NotEnoughReason(val id: String) { LOGGED_DAYS("logged_days"), SIDE_DAYS("side_days"), WEIGH_INS("weigh_ins") }

/**
 * The outcome of a request (web `FoodAnswer`). Numbers to explain go to the
 * model ([Result]); when there is nothing to compute the app answers itself.
 */
sealed interface FoodAnswer {
    data class Result(val text: String, val details: FoodDetails) : FoodAnswer
    data class NoMatch(val foods: List<String>, val range: DayRange, val lastSeen: String?, val similar: List<String>) : FoodAnswer
    data class NotEnough(val reason: NotEnoughReason, val details: FoodDetails) : FoodAnswer
}

/** The log read once per answer: foods and each day's totals. */
class PreparedLog(val log: FoodLog) {
    private val meals = log.meals.filter { it.logDate <= log.today }
    val items: List<FoodItem> = foodItems(meals)
    val days: Map<String, DayTotals> = dayTotals(meals)
}

/**
 * One kind of question the app can answer from the log (web `Analysis`). [answer]
 * gets requests of its own [id] only, already checked by [parseFoodRequest]; it
 * returns null when the request cannot be answered as asked, which the coach
 * reports as not understood.
 */
interface Analysis {
    val id: AnalysisId
    fun answer(prepared: PreparedLog, request: FoodRequest): FoodAnswer?
}

internal val RULES get() = FoodQuestionsSpec.rules

internal fun t(name: String): String = FoodQuestionsSpec.template(name)

/** The days of [dates] with meals logged (any calories). */
internal fun loggedDaysOf(prepared: PreparedLog, dates: List<String>): List<String> =
    dates.filter { (prepared.days[it]?.calories ?: 0.0) > 0 }

internal fun groupOf(prepared: PreparedLog, label: GroupLabel, range: DayRange, dates: List<String> = daysOf(range)): FoodGroup =
    FoodGroup(label, range.from, range.to, loggedDaysOf(prepared, dates).size, dates.size)

internal fun details(analysis: AnalysisId, groups: List<FoodGroup>, counted: List<FoodItem> = emptyList()): FoodDetails {
    val names = namesByDays(counted)
    return FoodDetails(
        analysis = analysis,
        groups = groups,
        counted = names.take(RULES.countedNamesShown),
        moreCounted = (names.size - RULES.countedNamesShown).coerceAtLeast(0),
        withoutAmount = counted.count { it.amount == null },
        few = groups.any { it.loggedDays < RULES.fewLoggedDays },
    )
}

/** The groups with too few logged days for an average, if any. */
internal fun notEnough(analysis: AnalysisId, groups: List<FoodGroup>, counted: List<FoodItem> = emptyList()): FoodAnswer.NotEnough? {
    if (groups.all { it.loggedDays >= RULES.minLoggedDays }) return null
    return FoodAnswer.NotEnough(NotEnoughReason.LOGGED_DAYS, details(analysis, groups, counted))
}

/** The foods matching the request's search words, in [range]. */
internal fun matchedItems(prepared: PreparedLog, request: FoodRequest, range: DayRange): List<FoodItem> {
    val terms = searchTerms(request.foods)
    return prepared.items.filter { inRange(it.date, range) && matchesTerms(it.key, terms) }
}

/** Nothing logged matches: when it was last logged, and names that look alike in the period. */
internal fun noMatch(prepared: PreparedLog, request: FoodRequest, range: DayRange): FoodAnswer {
    val terms = searchTerms(request.foods)
    val lastSeen = prepared.items.filter { matchesTerms(it.key, terms) }.maxOfOrNull { it.date }
    val stems = terms.flatten().map { it.take(4) }.filter { it.length >= 3 }
    val alike = prepared.items.filter { item ->
        inRange(item.date, range) && item.key.split(" ").any { word -> stems.any { word.startsWith(it) } }
    }
    return FoodAnswer.NoMatch(request.foods, range, lastSeen, namesByDays(alike).take(RULES.similarNamesShown))
}

internal fun metricOf(calories: Double, protein: Double, carbs: Double, fat: Double, metric: Metric): Double = when (metric) {
    Metric.PROTEIN -> protein
    Metric.CARBS -> carbs
    Metric.FAT -> fat
    else -> calories
}

internal fun metricOf(item: FoodItem, metric: Metric) = metricOf(item.calories, item.protein, item.carbs, item.fat, metric)

internal fun metricOf(day: DayTotals, metric: Metric) = metricOf(day.calories, day.protein, day.carbs, day.fat, metric)

/** Same text on every platform: integers without decimals, «80.2», never «80.0» or «-0». */
internal fun num(value: Double): String = plainNumber(if (value == 0.0) 0.0 else value)

/** kcal as integers, grams with one decimal. */
internal fun metricValue(value: Double, metric: Metric): String =
    num(if (metric == Metric.CALORIES) mathRound(value) else round1(value))

internal fun macrosText(totals: DayTotals): String = fill(
    t("macros"),
    "calories" to num(mathRound(totals.calories)),
    "protein" to num(round1(totals.protein)),
    "carbs" to num(round1(totals.carbs)),
    "fat" to num(round1(totals.fat)),
)

internal fun periodLine(group: FoodGroup): String =
    if (group.label == GroupLabel.PERIOD) {
        fill(t("period"), "from" to group.from, "to" to group.to, "days" to group.totalDays, "logged" to group.loggedDays)
    } else {
        fill(
            t("group"),
            "label" to FoodQuestionsSpec.label(group.label),
            "from" to group.from,
            "to" to group.to,
            "days" to group.totalDays,
            "logged" to group.loggedDays,
        )
    }

/** «Logged names counted: …» and «Only N days…» when they apply. */
internal fun countedLines(found: FoodDetails): List<String> {
    val lines = mutableListOf<String>()
    if (found.counted.isNotEmpty()) {
        val names = found.counted.joinToString(", ")
        lines += if (found.moreCounted > 0) fill(t("countedMore"), "names" to names, "more" to found.moreCounted) else fill(t("counted"), "names" to names)
    }
    if (found.few) lines += fill(t("few"), "logged" to found.groups.minOf { it.loggedDays })
    return lines
}

/**
 * The result block for the model: the header and [lines], cut at whole lines to
 * `resultMaxChars` (≈ 400 tokens) so it fits the phone model's window.
 */
fun resultText(lines: List<String>): String {
    val header = t("header")
    val cut = t("cut")
    val kept = mutableListOf(header)
    var length = header.length
    for ((index, line) in lines.withIndex()) {
        val room = if (index == lines.lastIndex) 0 else cut.length + 1
        if (length + 1 + line.length + room > RULES.resultMaxChars) {
            kept += cut
            break
        }
        kept += line
        length += 1 + line.length
    }
    return kept.joinToString("\n")
}

internal fun result(lines: List<String>, found: FoodDetails): FoodAnswer = FoodAnswer.Result(resultText(lines), found)
