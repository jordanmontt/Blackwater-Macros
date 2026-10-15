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
 * one period for everything, averages over logged days only, weigh-ins of the period,
 * and each day compared with the target it had.
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
