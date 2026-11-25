package com.blackwatermacros.app.core.foodquestions.analyses

import com.blackwatermacros.app.core.foodquestions.Analysis
import com.blackwatermacros.app.core.foodquestions.AnalysisId
import com.blackwatermacros.app.core.foodquestions.FoodAnswer
import com.blackwatermacros.app.core.foodquestions.FoodQuestionsSpec
import com.blackwatermacros.app.core.foodquestions.FoodRequest
import com.blackwatermacros.app.core.foodquestions.GroupLabel
import com.blackwatermacros.app.core.foodquestions.Metric
import com.blackwatermacros.app.core.foodquestions.PreparedLog
import com.blackwatermacros.app.core.foodquestions.RULES
import com.blackwatermacros.app.core.foodquestions.details
import com.blackwatermacros.app.core.foodquestions.displayNames
import com.blackwatermacros.app.core.foodquestions.fill
import com.blackwatermacros.app.core.foodquestions.groupOf
import com.blackwatermacros.app.core.foodquestions.inRange
import com.blackwatermacros.app.core.foodquestions.metricOf
import com.blackwatermacros.app.core.foodquestions.metricValue
import com.blackwatermacros.app.core.foodquestions.notEnough
import com.blackwatermacros.app.core.foodquestions.num
import com.blackwatermacros.app.core.foodquestions.periodLine
import com.blackwatermacros.app.core.foodquestions.resolveRange
import com.blackwatermacros.app.core.foodquestions.result
import com.blackwatermacros.app.core.foodquestions.t
import com.blackwatermacros.app.core.mathRound

/** The top foods by calories, a macro or how often: «¿Qué me da más proteína?» (web `ranking.ts`). */
object Ranking : Analysis {
    override val id = AnalysisId.RANKING

    private data class Row(val name: String, val value: Double, val days: Int)

    override fun answer(prepared: PreparedLog, request: FoodRequest): FoodAnswer? {
        val metric = request.metric ?: Metric.CALORIES
        val range = resolveRange(request.from, request.to, prepared.log.today, RULES.defaultDays) ?: return null
        val period = groupOf(prepared, GroupLabel.PERIOD, range)
        notEnough(id, listOf(period))?.let { return it }

        val items = prepared.items.filter { inRange(it.date, range) }
        val names = displayNames(items)
        val values = linkedMapOf<String, Double>()
        val days = linkedMapOf<String, MutableSet<String>>()
        for (item in items) {
            values[item.key] = (values[item.key] ?: 0.0) + if (metric == Metric.TIMES) 0.0 else metricOf(item, metric)
            days.getOrPut(item.key) { mutableSetOf() } += item.date
        }
        val byValue = if (metric == Metric.TIMES) compareByDescending<Row> { it.days } else compareByDescending { it.value }
        val rows = values.map { (key, value) -> Row(names.getValue(key), value, days.getValue(key).size) }
            .filter { metric == Metric.TIMES || it.value > 0 }
            .sortedWith(byValue.thenBy { it.name })
            .take(RULES.rankingSize)

        val lines = mutableListOf(periodLine(period))
        if (metric == Metric.TIMES) {
            lines += t("rankingTimesTitle")
            rows.forEachIndexed { index, row ->
                lines += fill(t("rankingTimesLine"), "rank" to index + 1, "name" to row.name, "days" to row.days, "logged" to period.loggedDays)
            }
        } else {
            var total = 0.0
            for ((date, totals) in prepared.days) if (inRange(date, range)) total += metricOf(totals, metric)
            val (name, unit) = FoodQuestionsSpec.metric(metric)
            lines += fill(t("rankingTitle"), "metric" to name, "total" to metricValue(total, metric), "unit" to unit)
            rows.forEachIndexed { index, row ->
                lines += fill(
                    t("rankingLine"),
                    "rank" to index + 1,
                    "name" to row.name,
                    "value" to metricValue(row.value, metric),
                    "unit" to unit,
                    "share" to num(if (total > 0) mathRound(row.value / total * 100) else 0.0),
                    "days" to row.days,
                )
            }
        }
        return result(lines, details(id, listOf(period)))
    }
}
