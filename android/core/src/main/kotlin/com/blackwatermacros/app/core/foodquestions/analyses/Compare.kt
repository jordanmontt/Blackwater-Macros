package com.blackwatermacros.app.core.foodquestions.analyses

import com.blackwatermacros.app.core.foodquestions.Analysis
import com.blackwatermacros.app.core.foodquestions.AnalysisId
import com.blackwatermacros.app.core.foodquestions.DayRange
import com.blackwatermacros.app.core.foodquestions.FoodAnswer
import com.blackwatermacros.app.core.foodquestions.FoodGroup
import com.blackwatermacros.app.core.foodquestions.FoodItem
import com.blackwatermacros.app.core.foodquestions.FoodQuestionsSpec
import com.blackwatermacros.app.core.foodquestions.FoodRequest
import com.blackwatermacros.app.core.foodquestions.GroupLabel
import com.blackwatermacros.app.core.foodquestions.Metric
import com.blackwatermacros.app.core.foodquestions.PreparedLog
import com.blackwatermacros.app.core.foodquestions.RULES
import com.blackwatermacros.app.core.foodquestions.Split
import com.blackwatermacros.app.core.foodquestions.countedLines
import com.blackwatermacros.app.core.foodquestions.daysOf
import com.blackwatermacros.app.core.foodquestions.details
import com.blackwatermacros.app.core.foodquestions.fill
import com.blackwatermacros.app.core.foodquestions.groupOf
import com.blackwatermacros.app.core.foodquestions.isWeekend
import com.blackwatermacros.app.core.foodquestions.loggedDaysOf
import com.blackwatermacros.app.core.foodquestions.matchedItems
import com.blackwatermacros.app.core.foodquestions.metricOf
import com.blackwatermacros.app.core.foodquestions.metricValue
import com.blackwatermacros.app.core.foodquestions.noMatch
import com.blackwatermacros.app.core.foodquestions.notEnough
import com.blackwatermacros.app.core.foodquestions.num
import com.blackwatermacros.app.core.foodquestions.periodLine
import com.blackwatermacros.app.core.foodquestions.resolveRange
import com.blackwatermacros.app.core.foodquestions.result
import com.blackwatermacros.app.core.foodquestions.t
import com.blackwatermacros.app.core.mathRound
import com.blackwatermacros.app.core.round1

/**
 * One number between weekdays and the weekend, or between two periods: of
 * everything eaten, or of some foods. «¿Como más el fin de semana?» (web `compare.ts`)
 */
object Compare : Analysis {
    override val id = AnalysisId.COMPARE

    private class Side(val group: FoodGroup, val logged: List<String>)

    private fun side(prepared: PreparedLog, label: GroupLabel, range: DayRange, dates: List<String>) =
        Side(groupOf(prepared, label, range, dates), loggedDaysOf(prepared, dates))

    override fun answer(prepared: PreparedLog, request: FoodRequest): FoodAnswer? {
        val metric = request.metric ?: Metric.CALORIES
        val withFoods = request.foods.isNotEmpty()
        if (metric == Metric.TIMES && !withFoods) return null
        val today = prepared.log.today

        val sides: List<Side>
        val whole: DayRange
        if (request.split == Split.WEEKDAY_WEEKEND) {
            // 8 weeks by default: 4 weeks have only 8 weekend days, too few once some go unlogged.
            val range = resolveRange(request.from, request.to, today, RULES.weekendDefaultDays) ?: return null
            whole = range
            val days = daysOf(range)
            sides = listOf(
                side(prepared, GroupLabel.WEEKDAYS, range, days.filterNot(::isWeekend)),
                side(prepared, GroupLabel.WEEKEND, range, days.filter(::isWeekend)),
            )
        } else {
            if (request.from == null || request.to == null || request.from2 == null || request.to2 == null) return null
            val first = resolveRange(request.from, request.to, today, RULES.defaultDays) ?: return null
            val second = resolveRange(request.from2, request.to2, today, RULES.defaultDays) ?: return null
            whole = DayRange(minOf(first.from, second.from), maxOf(first.to, second.to))
            sides = listOf(side(prepared, GroupLabel.FIRST, first, daysOf(first)), side(prepared, GroupLabel.SECOND, second, daysOf(second)))
        }

        val counted = if (withFoods) matchedItems(prepared, request, whole).filter { item -> sides.any { item.date in it.logged } } else emptyList()
        if (withFoods && counted.isEmpty()) return noMatch(prepared, request, whole)
        val groups = sides.map { it.group }
        notEnough(id, groups, counted)?.let { return it }

        val found = details(id, groups, counted)
        val lines = (groups.map(::periodLine) + countedLines(found)).toMutableList()
        val first = FoodQuestionsSpec.label(sides[0].group.label)
        val second = FoodQuestionsSpec.label(sides[1].group.label)
        if (metric == Metric.TIMES) {
            val shares = sides.map { s ->
                val eaten = counted.filter { it.date in s.logged }.map { it.date }.toSet().size
                eaten to mathRound(eaten.toDouble() / s.logged.size * 100)
            }
            lines += t("compareTimesTitle")
            sides.forEachIndexed { index, s ->
                lines += fill(
                    t("compareTimes"),
                    "label" to FoodQuestionsSpec.label(s.group.label),
                    "eatenDays" to shares[index].first,
                    "logged" to s.logged.size,
                    "share" to num(shares[index].second),
                )
            }
            lines += fill(t("compareTimesDifference"), "first" to first, "second" to second, "difference" to num(shares[1].second - shares[0].second))
            return result(lines, found)
        }

        val (name, unit) = FoodQuestionsSpec.metric(metric)
        val perDay = sides.map { perLoggedDay(prepared, it, metric, if (withFoods) counted else null) }
        lines += fill(t(if (withFoods) "compareFoodsTitle" else "compareTitle"), "metric" to name.replaceFirstChar { it.uppercaseChar() })
        sides.forEachIndexed { index, s ->
            lines += fill(t("compareValue"), "label" to FoodQuestionsSpec.label(s.group.label), "value" to metricValue(perDay[index], metric), "unit" to unit)
        }
        val rounded = perDay.map { if (metric == Metric.CALORIES) mathRound(it) else round1(it) }
        val difference = if (metric == Metric.CALORIES) rounded[1] - rounded[0] else round1(rounded[1] - rounded[0])
        lines += if (rounded[0] > 0) {
            fill(
                t("compareDifference"),
                "first" to first,
                "second" to second,
                "difference" to num(difference),
                "unit" to unit,
                "percent" to num(mathRound(difference / rounded[0] * 100)),
            )
        } else {
            fill(t("compareDifferenceFromZero"), "first" to first, "second" to second, "difference" to num(difference), "unit" to unit)
        }
        return result(lines, found)
    }

    /** The metric per logged day of a side: of everything, or of the [foods] counted. */
    private fun perLoggedDay(prepared: PreparedLog, side: Side, metric: Metric, foods: List<FoodItem>?): Double {
        var total = 0.0
        if (foods == null) for (date in side.logged) total += metricOf(prepared.days.getValue(date), metric)
        else for (item in foods) if (item.date in side.logged) total += metricOf(item, metric)
        return total / side.logged.size
    }
}
