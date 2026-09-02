package com.blackwatermacros.app.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Kotlin JUnit mirror of `tests/unit/stats.test.ts`. Option A contract:
 * this file must stay in lockstep with the TS spec.
 */
class StatsTest {

    // --- Weight trend (7-day moving average) ---

    @Test
    fun movingAverageByDays_smoothsDailyNoiseWithWeeklyMean() {
        val points = days("2026-03-01", listOf(80.0, 81.0, 80.5, 80.0, 79.5, 79.0, 78.0))
        val result = movingAverageByDays(points, 7)
        assertThat(result.last()).isWithin(0.001).of(79.714)
    }

    @Test
    fun movingAverageByDays_gapsUseOnlyAvailableRecords() {
        // Lunes y viernes de la misma semana, sin registros entre medias.
        val points = listOf(
            DataPoint("2026-03-02", 80.0),
            DataPoint("2026-03-06", 79.0),
        )
        val result = movingAverageByDays(points, 7)
        assertThat(result).containsExactly(80.0, (80.0 + 79.0) / 2).inOrder()
    }

    @Test
    fun movingAverageByDays_excludesPointsOutsideWindow() {
        val points = listOf(
            DataPoint("2026-03-01", 90.0), // fuera de la ventana del día 8
            DataPoint("2026-03-08", 80.0),
        )
        val result = movingAverageByDays(points, 7)
        assertThat(result[1]).isEqualTo(80.0)
    }

    @Test
    fun movingAverageByDays_emptyReturnsEmpty() {
        assertThat(movingAverageByDays(emptyList(), 7)).isEmpty()
    }

    // --- Rate of change (kg/week) ---

    @Test
    fun linearRatePerWeek_detectsConstantLossOfHalfAKgPerWeek() {
        // -0.5 kg cada 7 días durante 5 semanas
        val points = (0 until 5).map { week ->
            DataPoint(addDaysToKey("2026-01-05", week * 7), 85.0 - week * 0.5)
        }
        assertThat(linearRatePerWeek(points)!!).isWithin(0.000001).of(-0.5)
    }

    @Test
    fun linearRatePerWeek_insensitiveToOrderRegressionNotDifference() {
        val points = listOf(
            DataPoint("2026-01-12", 84.5),
            DataPoint("2026-01-05", 85.0),
            DataPoint("2026-01-19", 84.0),
        )
        assertThat(linearRatePerWeek(points)!!).isWithin(0.000001).of(-0.5)
    }

    @Test
    fun linearSlopePerDay_nullWithFewerThanTwoDistinctDates() {
        assertThat(linearSlopePerDay(listOf(DataPoint("2026-01-05", 80.0)))).isNull()
        assertThat(linearSlopePerDay(emptyList())).isNull()
    }

    @Test
    fun linearSlopePerDay_zeroWhenWeightStable() {
        val points = listOf(
            DataPoint("2026-01-05", 80.0),
            DataPoint("2026-01-19", 80.0),
        )
        assertThat(linearSlopePerDay(points)!!).isEqualTo(0.0)
    }

    // --- Weekly averages of weight ---

    @Test
    fun weeklyAverages_groupsByWeeksStartingMondayAcrossMonthBoundary() {
        val points = listOf(
            DataPoint("2026-02-28", 82.0), // sábado
            DataPoint("2026-03-01", 81.4), // domingo
            DataPoint("2026-03-02", 81.0), // lunes
            DataPoint("2026-03-03", 80.8), // martes
        )
        val weeks = weeklyAverages(points)
        assertThat(weeks).hasSize(2)
        assertThat(weeks[0].weekStart).isEqualTo("2026-02-23")
        assertThat(weeks[0].avg).isWithin(0.000001).of((82.0 + 81.4) / 2)
        assertThat(weeks[1].weekStart).isEqualTo("2026-03-02")
        assertThat(weeks[1].avg).isWithin(0.000001).of((81.0 + 80.8) / 2)
    }

    @Test
    fun weeklyAverages_ignoresWeeksWithoutDataInsteadOfFilling() {
        val points = listOf(
            DataPoint("2026-03-02", 80.0),
            DataPoint("2026-03-16", 79.0), // dos semanas después
        )
        assertThat(weeklyAverages(points).map { it.weekStart })
            .containsExactly("2026-03-02", "2026-03-16").inOrder()
    }

    // --- Daily calorie/protein series ---

    @Test
    fun buildDailyNutritionSeries_fillsZeroesForDaysWithoutMeals() {
        val totals = mapOf(
            "2026-03-01" to DailyNutritionPoint("2026-03-01", 2000.0, 120.0, 250.0, 70.0),
            "2026-03-03" to DailyNutritionPoint("2026-03-03", 1800.0, 100.0, 200.0, 60.0),
        )
        val series = buildDailyNutritionSeries(totals, "2026-03-01", "2026-03-04")
        assertThat(series.map { it.calories }).containsExactly(2000.0, 0.0, 1800.0, 0.0).inOrder()
        assertThat(series.map { it.protein }).containsExactly(120.0, 0.0, 100.0, 0.0).inOrder()
        assertThat(series.map { it.carbs }).containsExactly(250.0, 0.0, 200.0, 0.0).inOrder()
        assertThat(series.map { it.fat }).containsExactly(70.0, 0.0, 60.0, 0.0).inOrder()
    }

    @Test
    fun buildDailyNutritionSeries_coversSingleDayRange() {
        val series = buildDailyNutritionSeries(emptyMap(), "2026-03-01", "2026-03-01")
        assertThat(series).containsExactly(DailyNutritionPoint("2026-03-01", 0.0, 0.0, 0.0, 0.0))
    }

    // --- Stats ranges ---

    @Test
    fun rangeToDays_mapsRangeNamesToDayCounts() {
        assertThat(rangeToDays(StatsRange.RANGE_7D)).isEqualTo(7)
        assertThat(rangeToDays(StatsRange.RANGE_30D)).isEqualTo(30)
        assertThat(rangeToDays(StatsRange.RANGE_90D)).isEqualTo(90)
        assertThat(rangeToDays(StatsRange.ALL)).isNull()
    }

    // --- helpers ---

    private fun days(startKey: String, values: List<Double>): List<DataPoint> =
        values.mapIndexed { index, value -> DataPoint(addDaysToKey(startKey, index), value) }
}