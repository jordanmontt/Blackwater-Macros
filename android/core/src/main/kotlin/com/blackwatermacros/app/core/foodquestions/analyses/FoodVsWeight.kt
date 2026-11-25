package com.blackwatermacros.app.core.foodquestions.analyses

import com.blackwatermacros.app.core.MIN_WEIGHT_SPAN_DAYS
import com.blackwatermacros.app.core.MIN_WEIGH_INS
import com.blackwatermacros.app.core.WeighIn
import com.blackwatermacros.app.core.Z_95
import com.blackwatermacros.app.core.addDaysToKey
import com.blackwatermacros.app.core.daysBetweenKeys
import com.blackwatermacros.app.core.fitWeightTrend
import com.blackwatermacros.app.core.foodquestions.Analysis
import com.blackwatermacros.app.core.foodquestions.AnalysisId
import com.blackwatermacros.app.core.foodquestions.DayRange
import com.blackwatermacros.app.core.foodquestions.FoodAnswer
import com.blackwatermacros.app.core.foodquestions.FoodItem
import com.blackwatermacros.app.core.foodquestions.FoodQuestionsSpec
import com.blackwatermacros.app.core.foodquestions.FoodRequest
import com.blackwatermacros.app.core.foodquestions.GroupLabel
import com.blackwatermacros.app.core.foodquestions.NotEnoughReason
import com.blackwatermacros.app.core.foodquestions.PreparedLog
import com.blackwatermacros.app.core.foodquestions.RULES
import com.blackwatermacros.app.core.foodquestions.SplitReason
import com.blackwatermacros.app.core.foodquestions.WeightSplit
import com.blackwatermacros.app.core.foodquestions.countedLines
import com.blackwatermacros.app.core.foodquestions.daysOf
import com.blackwatermacros.app.core.foodquestions.details
import com.blackwatermacros.app.core.foodquestions.fill
import com.blackwatermacros.app.core.foodquestions.groupOf
import com.blackwatermacros.app.core.foodquestions.inRange
import com.blackwatermacros.app.core.foodquestions.loggedDaysOf
import com.blackwatermacros.app.core.foodquestions.matchedItems
import com.blackwatermacros.app.core.foodquestions.noMatch
import com.blackwatermacros.app.core.foodquestions.notEnough
import com.blackwatermacros.app.core.foodquestions.num
import com.blackwatermacros.app.core.foodquestions.periodLine
import com.blackwatermacros.app.core.foodquestions.rangeDays
import com.blackwatermacros.app.core.foodquestions.resolveRange
import com.blackwatermacros.app.core.foodquestions.result
import com.blackwatermacros.app.core.foodquestions.t
import com.blackwatermacros.app.core.groupWeighIns
import com.blackwatermacros.app.core.mathRound
import com.blackwatermacros.app.core.round2

data class WeightRate(
    val ratePerWeek: Double,
    /** ± kg/week, 95 % interval of the slope. */
    val marginPerWeek: Double,
    val weighInDays: Int,
)

/**
 * The weight trend before and after a change in a food: «¿Cambió mi peso desde
 * que dejé la cerveza?». An association, never a cause: the text says so (web `food-vs-weight.ts`).
 */
object FoodVsWeight : Analysis {
    override val id = AnalysisId.FOOD_VS_WEIGHT

    private val SPLIT_TEXT = mapOf(
        SplitReason.ASKED to "weightSplitAsked",
        SplitReason.STOPPED to "weightSplitStopped",
        SplitReason.STARTED to "weightSplitStarted",
        SplitReason.HALVES to "weightSplitHalves",
    )

    /**
     * The weight trend over [range], with the same data rules and fit as the
     * measured expenditure (≥ 4 weigh-in days spanning ≥ 14 days, compared only
     * within weigh-ins taken alike). Null when the weigh-ins cannot support one.
     */
    fun weightRateIn(prepared: PreparedLog, range: DayRange): WeightRate? {
        val weighIns = prepared.log.weights.filter { inRange(it.date, range) }
            .map { WeighIn(it.date, it.weightKg, it.time, it.fasted, it.timeZone) }
        val grouped = groupWeighIns(weighIns)
        val days = grouped.points.map { it.date }.distinct()
        if (days.size < MIN_WEIGH_INS || daysBetweenKeys(days.first(), days.last()) < MIN_WEIGHT_SPAN_DAYS) return null
        val trend = fitWeightTrend(grouped.points, grouped.groups) ?: return null
        return WeightRate(round2(trend.slopePerDay * 7), round2(Z_95 * trend.slopeError * 7), days.size)
    }

    /**
     * Where to split the period: the day the user named; else the day after the
     * foods were last logged (stopped), the first day they were (started), or the
     * middle when they were logged all along.
     */
    private fun splitPoint(request: FoodRequest, range: DayRange, items: List<FoodItem>): WeightSplit {
        request.at?.let { return WeightSplit(it, SplitReason.ASKED) }
        val dates = items.map { it.date }.sorted()
        val first = dates.first()
        val last = dates.last()
        if (daysBetweenKeys(last, range.to) >= RULES.changeGapDays) return WeightSplit(addDaysToKey(last, 1), SplitReason.STOPPED)
        if (daysBetweenKeys(range.from, first) >= RULES.changeGapDays) return WeightSplit(first, SplitReason.STARTED)
        return WeightSplit(addDaysToKey(range.from, rangeDays(range) / 2), SplitReason.HALVES)
    }

    override fun answer(prepared: PreparedLog, request: FoodRequest): FoodAnswer? {
        val today = prepared.log.today
        // A day named without a period: as many days on each side as the default period has in all.
        val half = RULES.weightDefaultDays / 2
        val range = if (request.at != null && request.from == null && request.to == null) {
            resolveRange(addDaysToKey(request.at, -half), addDaysToKey(request.at, half - 1), today, RULES.weightDefaultDays)
        } else {
            resolveRange(request.from, request.to, today, RULES.weightDefaultDays)
        } ?: return null
        if (request.at != null && (request.at <= range.from || request.at > range.to)) return null
        val items = matchedItems(prepared, request, range)
        if (items.isEmpty()) return noMatch(prepared, request, range)

        val split = splitPoint(request, range, items)
        val before = DayRange(range.from, addDaysToKey(split.at, -1))
        val after = DayRange(split.at, range.to)
        val groups = listOf(groupOf(prepared, GroupLabel.BEFORE, before), groupOf(prepared, GroupLabel.AFTER, after))
        val found = details(id, groups, items).copy(split = split)
        if (groups.any { it.totalDays < RULES.minSideDays }) return FoodAnswer.NotEnough(NotEnoughReason.SIDE_DAYS, found)
        notEnough(id, groups, items)?.let { return it.copy(details = found) }
        val rates = listOf(weightRateIn(prepared, before), weightRateIn(prepared, after))
        if (rates.all { it == null }) return FoodAnswer.NotEnough(NotEnoughReason.WEIGH_INS, found)

        val lines = (groups.map(::periodLine) + fill(t(SPLIT_TEXT.getValue(split.reason)), "at" to split.at) + countedLines(found)).toMutableList()
        listOf(before, after).forEachIndexed { index, side ->
            val logged = loggedDaysOf(prepared, daysOf(side))
            val eaten = items.filter { inRange(it.date, side) }
            var total = 0.0
            for (date in logged) total += prepared.days.getValue(date).calories
            lines += fill(
                t("weightFood"),
                "label" to FoodQuestionsSpec.label(groups[index].label),
                "eatenDays" to eaten.map { it.date }.toSet().size,
                "logged" to logged.size,
                "calories" to num(mathRound(eaten.fold(0.0) { sum, item -> sum + item.calories } / logged.size)),
                "total" to num(mathRound(total / logged.size)),
            )
        }
        rates.forEachIndexed { index, rate ->
            val label = FoodQuestionsSpec.label(groups[index].label)
            lines += if (rate == null) {
                fill(t("weightTrendMissing"), "label" to label, "minWeighIns" to MIN_WEIGH_INS, "minSpan" to MIN_WEIGHT_SPAN_DAYS)
            } else {
                fill(t("weightTrend"), "label" to label, "rate" to num(rate.ratePerWeek), "margin" to num(rate.marginPerWeek), "weighIns" to rate.weighInDays)
            }
        }
        lines += t("association")
        return result(lines, found)
    }
}
