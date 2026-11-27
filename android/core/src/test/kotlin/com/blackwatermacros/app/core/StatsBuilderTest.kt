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
    fun buildStatsFromData_rateComparesOnlyWeighInsTakenAlike() {
        val rows = (0 until 28).map { i ->
            StatsWeight(Instant.parse("${addDaysToKey(TODAY, -(27 - i))}T12:00:00Z"), if (i < 14) 80.8 else 80.0, null, fasted = i >= 14)
        }
        assertThat(buildStatsFromData(emptyList(), rows, StatsRange.RANGE_30D, TODAY).weight.ratePerWeekKg).isEqualTo(0.0)
        val unmarked = rows.map { it.copy(fasted = true) }
        assertThat(buildStatsFromData(emptyList(), unmarked, StatsRange.RANGE_30D, TODAY).weight.ratePerWeekKg!!).isLessThan(-0.2)
    }

    @Test
    fun buildStatsFromData_trendAndChangeUseOnlyWeighInsTakenLikeMost() {
        // Two weeks fasted at 80 kg, and the last three evenings also after dinner at 81,2 kg.
        val rows = (0 until 14).flatMap { i ->
            val day = addDaysToKey(TODAY, -(13 - i))
            listOf(StatsWeight(Instant.parse("${day}T10:00:00Z"), 80.0, null)) +
                if (i >= 11) listOf(StatsWeight(Instant.parse("${day}T14:00:00Z"), 81.2, null, fasted = false)) else emptyList()
        }
        val summary = buildStatsFromData(emptyList(), rows, StatsRange.RANGE_30D, TODAY)
        assertThat(summary.weights.count { !it.inTrend }).isEqualTo(3)
        assertThat(summary.weights.last()).isEqualTo(WeightPoint(TODAY, 81.2, 80.0, inTrend = false))
        assertThat(summary.weight.currentTrendKg).isEqualTo(80.0)
        assertThat(summary.weight.changeSinceStartKg).isEqualTo(0.0)
        // The last weigh-in is still the big number: it is what the scale said.
        assertThat(summary.weight.currentWeightKg).isEqualTo(81.2)

        // Unmarked, the same weigh-ins pull the trend up as if 1,2 kg had been gained.
        val unmarked = buildStatsFromData(emptyList(), rows.map { it.copy(fasted = true) }, StatsRange.RANGE_30D, TODAY)
        assertThat(unmarked.weight.currentTrendKg!!).isGreaterThan(80.2)
        assertThat(unmarked.weight.changeSinceStartKg!!).isWithin(1e-6).of(1.2)
    }

    @Test
    fun buildStatsFromData_mostlyNotFastedMakesThoseTheTrend() {
        val rows = (0 until 10).map { i ->
            val fasted = i < 2
            StatsWeight(
                Instant.parse("${addDaysToKey(TODAY, -(9 - i))}T${if (fasted) "10" else "14"}:00:00Z"),
                if (fasted) 80.0 else 81.0,
                null,
                fasted = fasted,
            )
        }
        val summary = buildStatsFromData(emptyList(), rows, StatsRange.RANGE_30D, TODAY)
        assertThat(summary.weights.map { it.inTrend }).containsExactlyElementsIn(listOf(false, false) + List(8) { true }).inOrder()
        assertThat(summary.weight.currentTrendKg).isEqualTo(81.0)
        assertThat(summary.weight.changeSinceStartKg).isEqualTo(0.0)
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
    fun buildStatsFromData_bodyFatTrendAndChangeUseOnlyReadingsTakenLikeMost() {
        // Two weeks fasted at 20 %, and the last three evenings also after dinner at 22 %.
        val rows = (0 until 14).flatMap { i ->
            val day = addDaysToKey(TODAY, -(13 - i))
            listOf(StatsWeight(Instant.parse("${day}T10:00:00Z"), 80.0, 20.0)) +
                if (i >= 11) listOf(StatsWeight(Instant.parse("${day}T14:00:00Z"), 81.2, 22.0, fasted = false)) else emptyList()
        }
        val summary = buildStatsFromData(emptyList(), rows, StatsRange.RANGE_30D, TODAY)
        assertThat(summary.bodyFat.count { !it.inTrend }).isEqualTo(3)
        assertThat(summary.bodyFat.last()).isEqualTo(BodyFatPoint(TODAY, 22.0, 20.0, inTrend = false))
        assertThat(summary.weight.changeBodyFatPct).isEqualTo(0.0)
        // The last reading is still the one shown: it is what the scale said.
        assertThat(summary.weight.currentBodyFatPct).isEqualTo(22.0)

        val unmarked = buildStatsFromData(emptyList(), rows.map { it.copy(fasted = true) }, StatsRange.RANGE_30D, TODAY)
        assertThat(unmarked.weight.changeBodyFatPct).isEqualTo(2.0)
    }

    @Test
    fun buildStatsFromData_bodyFatMajorityIsAmongReadingsWithBodyFat() {
        // Ten fasted weigh-ins without body fat, and three after eating with it.
        val rows = (0 until 10).map { i ->
            StatsWeight(Instant.parse("${addDaysToKey(TODAY, -(12 - i))}T10:00:00Z"), 80.0, null)
        } + listOf(21.8, 21.6, 21.4).mapIndexed { i, fat ->
            StatsWeight(Instant.parse("${addDaysToKey(TODAY, -(2 - i))}T14:00:00Z"), 81.0, fat, fasted = false)
        }
        val summary = buildStatsFromData(emptyList(), rows, StatsRange.RANGE_30D, TODAY)
        assertThat(summary.weights.count { !it.inTrend }).isEqualTo(3)
        assertThat(summary.bodyFat.all { it.inTrend }).isTrue()
        assertThat(summary.weight.changeBodyFatPct).isEqualTo(-0.4)
    }

    @Test
    fun buildStatsFromData_eachWeighInCountsOnItsDayWhereItWasTaken() {
        // 7 days from 2026-08-17. 02:00 UTC is still the day before in La Paz; 23:30 UTC is the next day in Paris.
        val rows = listOf(
            StatsWeight(Instant.parse("2026-08-17T02:00:00Z"), 80.0, null, timeZone = "America/La_Paz"), // 16 Aug there: out
            StatsWeight(Instant.parse("2026-08-20T02:00:00Z"), 79.8, null, timeZone = "America/La_Paz"),
            StatsWeight(Instant.parse("2026-08-21T23:30:00Z"), 79.6, null, timeZone = "Europe/Paris"),
        )
        val summary = buildStatsFromData(emptyList(), rows, StatsRange.RANGE_7D, TODAY)
        assertThat(summary.weights.map { it.date }).containsExactly("2026-08-19", "2026-08-22").inOrder()
    }

    @Test
    fun buildStatsFromData_returnsNullsWhenNoMealsOrWeightsInRange() {
        val summary = buildStatsFromData(emptyList(), emptyList(), StatsRange.RANGE_7D, TODAY)
        assertThat(summary.caloriesAvg).isNull()
        assertThat(summary.weight.currentWeightKg).isNull()
        assertThat(summary.weights).isEmpty()
    }
}