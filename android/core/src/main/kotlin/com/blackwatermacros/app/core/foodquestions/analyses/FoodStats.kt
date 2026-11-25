package com.blackwatermacros.app.core.foodquestions.analyses

import com.blackwatermacros.app.core.foodquestions.Analysis
import com.blackwatermacros.app.core.foodquestions.AnalysisId
import com.blackwatermacros.app.core.foodquestions.AmountUnit
import com.blackwatermacros.app.core.foodquestions.FoodAnswer
import com.blackwatermacros.app.core.foodquestions.FoodItem
import com.blackwatermacros.app.core.foodquestions.FoodRequest
import com.blackwatermacros.app.core.foodquestions.GroupLabel
import com.blackwatermacros.app.core.foodquestions.PreparedLog
import com.blackwatermacros.app.core.foodquestions.RULES
import com.blackwatermacros.app.core.foodquestions.countedLines
import com.blackwatermacros.app.core.foodquestions.details
import com.blackwatermacros.app.core.foodquestions.fill
import com.blackwatermacros.app.core.foodquestions.groupOf
import com.blackwatermacros.app.core.foodquestions.inRange
import com.blackwatermacros.app.core.foodquestions.matchedItems
import com.blackwatermacros.app.core.foodquestions.noMatch
import com.blackwatermacros.app.core.foodquestions.notEnough
import com.blackwatermacros.app.core.foodquestions.num
import com.blackwatermacros.app.core.foodquestions.periodLine
import com.blackwatermacros.app.core.foodquestions.resolveRange
import com.blackwatermacros.app.core.foodquestions.result
import com.blackwatermacros.app.core.foodquestions.t
import com.blackwatermacros.app.core.mathRound
import com.blackwatermacros.app.core.round1

/** How often and how much of a food, and what it brings: «¿Cuánta leche chocolatada tomo?» (web `food-stats.ts`). */
object FoodStats : Analysis {
    override val id = AnalysisId.FOOD_STATS

    override fun answer(prepared: PreparedLog, request: FoodRequest): FoodAnswer? {
        val range = resolveRange(request.from, request.to, prepared.log.today, RULES.defaultDays) ?: return null
        val items = matchedItems(prepared, request, range)
        if (items.isEmpty()) return noMatch(prepared, request, range)
        val period = groupOf(prepared, GroupLabel.PERIOD, range)
        notEnough(id, listOf(period), items)?.let { return it }

        val logged = period.loggedDays
        val found = details(id, listOf(period), items)
        val eatenDays = items.map { it.date }.toSet().size
        val lines = mutableListOf(periodLine(period))
        lines += countedLines(found)
        lines += fill(
            t("foodDays"),
            "times" to items.size,
            // Both: «¿cuántas veces por semana?» counts entries, not days (a model once mixed them up).
            "timesPerWeek" to num(round1(items.size.toDouble() / logged * 7)),
            "eatenDays" to eatenDays,
            "logged" to logged,
            "perWeek" to num(round1(eatenDays.toDouble() / logged * 7)),
        )
        for (unit in AmountUnit.entries) {
            val withUnit = items.filter { it.amount?.unit == unit }
            if (withUnit.isEmpty()) continue
            val total = withUnit.sumOf { it.amount!!.value }
            val days = withUnit.map { it.date }.toSet().size
            lines += fill(
                t("amount"),
                "unit" to unit.id,
                "total" to num(mathRound(total)),
                "entries" to withUnit.size,
                "perEatenDay" to num(mathRound(total / days)),
                "perLoggedDay" to num(mathRound(total / logged)),
            )
        }
        if (found.withoutAmount > 0) lines += fill(t("withoutAmount"), "n" to found.withoutAmount)

        fun sum(pick: (FoodItem) -> Double) = items.fold(0.0) { total, item -> total + pick(item) }
        var allCalories = 0.0
        for ((date, totals) in prepared.days) if (inRange(date, range)) allCalories += totals.calories
        val calories = sum { it.calories }
        lines += fill(
            t("foodNutrition"),
            "calories" to num(mathRound(calories / logged)),
            "share" to num(if (allCalories > 0) mathRound(calories / allCalories * 100) else 0.0),
            "protein" to num(round1(sum { it.protein } / logged)),
            "carbs" to num(round1(sum { it.carbs } / logged)),
            "fat" to num(round1(sum { it.fat } / logged)),
        )
        return result(lines, found)
    }
}
