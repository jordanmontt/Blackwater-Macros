package com.blackwatermacros.app.core

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

/**
 * Kotlin JUnit mirror of `tests/unit/stats-builder.test.ts`. Option A contract:
 * this file must stay in lockstep with the TS spec.
 */
class StatsBuilderTest {

    private val TODAY = "2026-08-23"

    private fun meals(points: List<Triple<String, Double, Double>>): List<StatsMeal> =
        points.map { (date, kcal, protein) ->
            StatsMeal(
                logDate = date,
                resolvedCalories = kcal,
                resolvedProtein = protein,
                resolvedCarbs = 0.0,
                resolvedFat = 0.0,
            )
        }

    private fun weights(entries: List<Triple<String, Double, Double?>>): List<StatsWeight> =
        entries.map { (iso, kg, bodyFatPct) ->
            StatsWeight(measuredAt = Instant.parse(iso), weightKg = kg, bodyFatPct = bodyFatPct)
        }

    @Test
    fun buildStatsFromData_sumsAllMealsOfADayAndZerosEmptyDays() {
        val summary = buildStatsFromData(
            meals(
                listOf(
                    Triple(TODAY, 475.0, 32.0),
                    Triple(TODAY, 850.0, 45.0),
                    Triple(TODAY, 600.0, 40.0),
                ),
            ),
            emptyList(),
            StatsRange.RANGE_7D,
            TODAY,
        )

        assertThat(summary.calories.find { it.date == TODAY }!!.calories).isEqualTo(1925.0)
        assertThat(summary.calories).hasSize(7)
        val yesterday = addDaysToKey(TODAY, -1)
        assertThat(summary.calories.find { it.date == yesterday }!!.calories).isEqualTo(0.0)
    }

    @Test
    fun buildStatsFromData_averagesAndPeakOnlyAmongDaysWithRecords() {
        val twoDaysAgo = addDaysToKey(TODAY, -2)
        val summary = buildStatsFromData(
            meals(
                listOf(
                    Triple(TODAY, 1500.0, 90.0),
                    Triple(twoDaysAgo, 2500.0, 150.0),
                ),
            ),
            emptyList(),
            StatsRange.RANGE_7D,
            TODAY,
        )

        assertThat(summary.caloriesAvg).isEqualTo(2000.0)
        assertThat(summary.caloriesMaxDay!!.date).isEqualTo(twoDaysAgo)
        assertThat(summary.proteinAvg).isEqualTo(120.0)
    }

    @Test
    fun buildStatsFromData_currentWeightIsLastAndTrendPresent() {
        val summary = buildStatsFromData(
            emptyList(),
            weights(
                listOf(
                    Triple("${addDaysToKey(TODAY, -6)}T12:00:00Z", 82.0, null),
                    Triple("${addDaysToKey(TODAY, -3)}T12:00:00Z", 81.5, null),
                    Triple("${TODAY}T12:00:00Z", 81.2, null),
                ),
            ),
            StatsRange.RANGE_30D,
            TODAY,
        )

        assertThat(summary.weight.currentWeightKg).isEqualTo(81.2)
        assertThat(summary.weights.last().trend).isNotNull()
    }

    @Test
    fun buildStatsFromData_monthLosingHalfKgPerWeekProducesNegativeRateAndTotalChange() {
        val entries = (0 until 5).map { week ->
            Triple("${addDaysToKey(addDaysToKey(TODAY, -28), week * 7)}T12:00:00Z", 85.0 - week * 0.5, null)
        }
        val summary = buildStatsFromData(emptyList(), weights(entries), StatsRange.RANGE_30D, TODAY)

        assertThat(summary.weight.changeSinceStartKg!!).isWithin(0.000001).of(-2.0)
        assertThat(summary.weight.ratePerWeekKg!!).isWithin(0.1).of(-0.5)
    }

    @Test
    fun buildStatsFromData_bodyFatSeriesOnlyWithValidBodyFatData() {
        val summary = buildStatsFromData(
            emptyList(),
            weights(
                listOf(
                    Triple("${addDaysToKey(TODAY, -5)}T08:00:00Z", 86.0, 20.0),
                    Triple("${TODAY}T08:00:00Z", 84.0, 18.0),
                    Triple("${addDaysToKey(TODAY, -2)}T08:00:00Z", 85.0, null),
                ),
            ),
            StatsRange.RANGE_30D,
            TODAY,
        )

        assertThat(summary.bodyFat).hasSize(2)
        assertThat(summary.weight.currentBodyFatPct).isEqualTo(18.0)
    }

    @Test
    fun buildStatsFromData_returnsNullsWhenNoMealsOrWeightsInRange() {
        val summary = buildStatsFromData(emptyList(), emptyList(), StatsRange.RANGE_7D, TODAY)
        assertThat(summary.caloriesAvg).isNull()
        assertThat(summary.weight.currentWeightKg).isNull()
        assertThat(summary.weights).isEmpty()
    }
}