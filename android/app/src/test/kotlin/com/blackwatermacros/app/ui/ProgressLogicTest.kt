package com.blackwatermacros.app.ui

import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.Gender
import com.blackwatermacros.app.core.Goal
import com.blackwatermacros.app.core.MacroSplit
import com.blackwatermacros.app.core.ProfilePeriod
import com.blackwatermacros.app.core.TargetRange
import com.blackwatermacros.app.core.calculateCalorieRecommendation
import com.blackwatermacros.app.core.mathRound
import com.blackwatermacros.app.core.addDaysToKey
import com.blackwatermacros.app.data.MealDTO
import com.blackwatermacros.app.data.WeightDTO
import com.blackwatermacros.app.data.WireEntryMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The Progreso screen's numbers (mirror of `tests/behavior/progreso-page.test.tsx`):
 * one period for everything, averages over logged days only, weigh-ins of the period
 * (one taken otherwise than most drawn as a ring, outside the trend), and each day
 * compared with the target it had.
 */
class ProgressLogicTest {

    private val today = "2026-03-01"
    private val profile = CalorieProfile(Gender.MALE, 1990, 178.0, 3, 60, 30, Goal.MAINTAIN)

    private fun meal(daysAgo: Int, kcal: Double, protein: Double, carbs: Double, fat: Double) = MealDTO(
        "m$daysAgo", addDaysToKey(today, -daysAgo), "Comida", null, WireEntryMode.TOTAL_ONLY, emptyList(),
        kcal, protein, carbs, fat, kcal, protein, carbs, fat, "",
    )

    private fun weight(daysAgo: Int, kg: Double, note: String? = null) =
        WeightDTO("w$daysAgo", "${addDaysToKey(today, -daysAgo)}T12:00:00Z", kg, null, note, "")

    private val meals = listOf(meal(1, 2000.0, 150.0, 200.0, 60.0), meal(3, 2200.0, 130.0, 250.0, 70.0))
    private val weights = listOf(weight(20, 82.0, "hace 20 días"), weight(2, 80.0, "reciente"))

    @Test
    fun `averages only the logged days of the period and shows the split`() {
        val progress = buildProgress(StatsRangeOption.R30D, meals, weights, profile, today)
        val averages = progress.averages!!
        assertThat(averages.loggedDays).isEqualTo(2)
        assertThat(averages.totalDays).isEqualTo(30)
        assertThat(averages.calories).isEqualTo(2100.0)
        assertThat(averages.protein).isEqualTo(140.0)
        assertThat(averages.split).isEqualTo(MacroSplit(27.0, 44.0, 29.0))
        // Complete profile → calorie and protein targets next to the averages.
        assertThat(progress.targets.calorie).isNotNull()
        assertThat(progress.targets.protein).isNotNull()
    }

    @Test
    fun `the period drives weights, weigh-ins and averages`() {
        val month = buildProgress(StatsRangeOption.R30D, meals, weights, profile, today)
        assertThat(month.groupedWeights.map { it.first }).containsExactly(addDaysToKey(today, -2), addDaysToKey(today, -20)).inOrder()
        assertThat(month.summary.weight.changeSinceStartKg).isEqualTo(-2.0)
        assertThat(month.weightRows).hasSize(2)

        val week = buildProgress(StatsRangeOption.R7D, meals, weights, profile, today)
        assertThat(week.groupedWeights.map { it.first }).containsExactly(addDaysToKey(today, -2))
        assertThat(week.averages!!.totalDays).isEqualTo(7)
        assertThat(week.hasAnyWeight).isTrue()
    }

    @Test
    fun `a weigh-in is listed under the day where it was taken, whatever the phone's zone`() {
        // 23:30 in La Paz is already the next day in UTC and in Europe; the day before
        // there; with no zone, the phone's day (web `toZonedDateTimeValue`).
        val lateInLaPaz = WeightDTO("w-lp", "2026-02-27T03:30:00Z", 80.0, null, null, "", timeZone = "America/La_Paz")
        val earlyInParis = WeightDTO("w-p", "2026-02-27T23:30:00Z", 80.2, null, null, "", timeZone = "Europe/Paris")
        val progress = buildProgress(StatsRangeOption.R30D, emptyList(), listOf(lateInLaPaz, earlyInParis), profile, today)
        assertThat(progress.groupedWeights.map { it.first to it.second.map(WeightDTO::id) })
            .containsExactly("2026-02-28" to listOf("w-p"), "2026-02-26" to listOf("w-lp"))
            .inOrder()
    }

    @Test
    fun `a weigh-in taken otherwise than most is a ring and stays out of the trend`() {
        fun at(daysAgo: Int, hour: Int, kg: Double, fasted: Boolean = true) =
            WeightDTO("w$daysAgo-$hour", "${addDaysToKey(today, -daysAgo)}T$hour:00:00Z", kg, null, null, "", fasted = fasted)
        val rows = listOf(
            at(5, 10, 80.0),
            at(3, 10, 80.0),
            at(3, 14, 81.4, fasted = false), // the same day after lunch: the fasted dot is the one shown
            at(2, 14, 81.6, fasted = false), // a day with only one after a meal: a ring
            at(1, 10, 80.0),
        )
        val progress = buildProgress(StatsRangeOption.R30D, emptyList(), rows, profile, today)
        assertThat(progress.weightRows.map { it.weight }).containsExactly(80.0, 80.0, 81.6, 80.0).inOrder()
        assertThat(progress.weightRows.map { it.inTrend }).containsExactly(true, true, false, true).inOrder()
        assertThat(progress.summary.weight.currentTrendKg).isEqualTo(80.0)
        assertThat(progress.summary.weight.changeSinceStartKg).isEqualTo(0.0)
    }

    @Test
    fun `nothing logged means no averages and no weigh-ins`() {
        val empty = buildProgress(StatsRangeOption.R30D, emptyList(), emptyList(), profile, today)
        assertThat(empty.averages).isNull()
        assertThat(empty.groupedWeights).isEmpty()
        assertThat(empty.hasAnyWeight).isFalse()
        assertThat(empty.targets.calorie).isNull()
    }

    @Test
    fun `each logged day keeps its own target, in the band and in the average`() {
        // Walked less until 2 days ago; 82 kg then, 80 kg from 2 days ago.
        val before = profile.copy(walkingMinutesPerDay = 0)
        val history = listOf(ProfilePeriod(addDaysToKey(today, -2), before))
        val progress = buildProgress(StatsRangeOption.R30D, meals, weights, profile, today, history)

        val old = calculateCalorieRecommendation(before, 82.0, 2026)!!
        val now = calculateCalorieRecommendation(profile, 80.0, 2026)!!
        assertThat(progress.calorieBands).containsExactly(old.targetMin to old.targetMax, now.targetMin to now.targetMax).inOrder()
        assertThat(progress.targets.calorie)
            .isEqualTo(TargetRange(mathRound((old.targetMin + now.targetMin) / 2), mathRound((old.targetMax + now.targetMax) / 2)))
    }
}
