package com.blackwatermacros.app.core.foodquestions.analyses

import com.blackwatermacros.app.core.EntryMode
import com.blackwatermacros.app.core.foodquestions.Analysis
import com.blackwatermacros.app.core.foodquestions.AnalysisId
import com.blackwatermacros.app.core.foodquestions.DayRange
import com.blackwatermacros.app.core.foodquestions.DayTotals
import com.blackwatermacros.app.core.foodquestions.FoodAnswer
import com.blackwatermacros.app.core.foodquestions.FoodRequest
import com.blackwatermacros.app.core.foodquestions.GroupLabel
import com.blackwatermacros.app.core.foodquestions.PreparedLog
import com.blackwatermacros.app.core.foodquestions.RULES
import com.blackwatermacros.app.core.foodquestions.daysOf
import com.blackwatermacros.app.core.foodquestions.details
import com.blackwatermacros.app.core.foodquestions.fill
import com.blackwatermacros.app.core.foodquestions.groupOf
import com.blackwatermacros.app.core.foodquestions.macrosText
import com.blackwatermacros.app.core.foodquestions.resolveRange
import com.blackwatermacros.app.core.foodquestions.result
import com.blackwatermacros.app.core.foodquestions.t
import com.blackwatermacros.app.core.foodquestions.weekdayName

/** What was eaten on a day or a few days: «¿Qué comí ayer?». No minimum of days (web `day-log.ts`). */
object DayLog : Analysis {
    override val id = AnalysisId.DAY_LOG

    override fun answer(prepared: PreparedLog, request: FoodRequest): FoodAnswer? {
        val asked = resolveRange(request.from, request.to, prepared.log.today, 1) ?: return null
        val all = daysOf(asked)
        val shown = all.takeLast(RULES.dayLogMaxDays)
        val range = DayRange(shown.first(), shown.last())
        val lines = mutableListOf<String>()
        if (shown.size < all.size) lines += fill(t("dayLogCut"), "shown" to shown.size, "days" to all.size)
        for (date in shown) {
            val totals = prepared.days[date]
            val weekday = weekdayName(date)
            if (totals == null || totals.calories <= 0) {
                lines += fill(t("dayEmpty"), "date" to date, "weekday" to weekday)
                continue
            }
            lines += fill(t("day"), "date" to date, "weekday" to weekday, "macros" to macrosText(totals))
            for (meal in prepared.log.meals.filter { it.logDate == date }) {
                val macros = macrosText(DayTotals(meal.resolvedCalories, meal.resolvedProtein, meal.resolvedCarbs, meal.resolvedFat))
                val items = if (meal.entryMode == EntryMode.TOTAL_ONLY) {
                    emptyList()
                } else {
                    meal.ingredients.filter { it.name.isNotBlank() }.map {
                        val quantity = it.quantity?.trim().orEmpty()
                        if (quantity.isNotEmpty()) "${it.name.trim()} ($quantity)" else it.name.trim()
                    }
                }
                lines += if (items.isNotEmpty()) {
                    fill(t("mealItems"), "title" to meal.title, "macros" to macros, "items" to items.joinToString(", "))
                } else {
                    fill(t("meal"), "title" to meal.title, "macros" to macros)
                }
            }
        }
        // A list, not an average: no minimum of days and no «few days» hint.
        return result(lines, details(id, listOf(groupOf(prepared, GroupLabel.PERIOD, range))).copy(few = false))
    }
}
