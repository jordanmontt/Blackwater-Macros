package com.blackwatermacros.app.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Kotlin JUnit mirror of `tests/unit/profile-history.test.ts`. */
class ProfileHistoryTest {

    private val before = CalorieProfile(Gender.FEMALE, 1995, 165.0, 2, 60, 20, Goal.CUT)
    private val after = before.copy(gymDaysPerWeek = 4, walkingMinutesPerDay = 60)
    private val empty = CalorieProfile(null, null, null, null, null, null, null)
    private val history = listOf(ProfilePeriod("2026-10-14", before))
    private val weights = listOf(
        DatedWeight("2026-10-01", 62.0, 28.0),
        DatedWeight("2026-10-10", 61.0, null),
        DatedWeight("2026-10-15", 60.5, null),
    )

    // --- withProfileChange ---

    @Test
    fun keepsTheOldProfileForTheDaysUpToYesterday() {
        assertThat(withProfileChange(before, emptyList(), after, "2026-10-15"))
            .containsExactly(ProfilePeriod("2026-10-14", before))
    }

    @Test
    fun addsNothingWhenNothingChanged() {
        assertThat(withProfileChange(before, emptyList(), before.copy(), "2026-10-15")).isEmpty()
    }

    @Test
    fun addsNothingWhenTheOldProfileGaveNoTarget() {
        assertThat(withProfileChange(empty, emptyList(), after, "2026-10-15")).isEmpty()
        assertThat(withProfileChange(before.copy(calorieGoal = null), emptyList(), after, "2026-10-15")).isEmpty()
    }

    @Test
    fun keepsTheFirstProfileOfTheDayWhenEditedAgainTheSameDay() {
        val third = after.copy(calorieGoal = Goal.MAINTAIN)
        assertThat(withProfileChange(after, history, third, "2026-10-15")).isEqualTo(history)
    }

    @Test
    fun addsANewPeriodOnALaterDay() {
        assertThat(withProfileChange(after, history, before, "2026-10-20"))
            .isEqualTo(history + ProfilePeriod("2026-10-19", after))
    }

    // --- profileOn ---

    @Test
    fun isTheCurrentProfileWithoutHistory() {
        assertThat(profileOn(after, emptyList(), "2026-01-01")).isEqualTo(after)
    }

    @Test
    fun isTheEarliestPeriodStillRunningThatDayElseTheCurrentProfile() {
        val periods = listOf(ProfilePeriod("2026-10-19", after), ProfilePeriod("2026-10-14", before))
        val current = before.copy(calorieGoal = Goal.MAINTAIN)
        assertThat(profileOn(current, periods, "2026-10-01")).isEqualTo(before)
        assertThat(profileOn(current, periods, "2026-10-14")).isEqualTo(before)
        assertThat(profileOn(current, periods, "2026-10-15")).isEqualTo(after)
        assertThat(profileOn(current, periods, "2026-10-19")).isEqualTo(after)
        assertThat(profileOn(current, periods, "2026-10-20")).isEqualTo(current)
    }

    // --- weightOn and bodyFatOn ---

    @Test
    fun weightIsTheLastWeighInOnOrBeforeTheDayElseTheFirstOne() {
        assertThat(weightOn(weights, "2026-09-20")?.weightKg).isEqualTo(62.0)
        assertThat(weightOn(weights, "2026-10-10")?.weightKg).isEqualTo(61.0)
        assertThat(weightOn(weights, "2026-10-12")?.weightKg).isEqualTo(61.0)
        assertThat(weightOn(weights, "2026-10-30")?.weightKg).isEqualTo(60.5)
        assertThat(weightOn(emptyList(), "2026-10-12")).isNull()
    }

    @Test
    fun bodyFatIsTheLastOneOnOrBeforeTheDayElseTheFirstOne() {
        val withLater = weights + DatedWeight("2026-10-16", 60.0, 27.0)
        assertThat(bodyFatOn(withLater, "2026-09-20")).isEqualTo(28.0)
        assertThat(bodyFatOn(withLater, "2026-10-15")).isEqualTo(28.0)
        assertThat(bodyFatOn(withLater, "2026-10-16")).isEqualTo(27.0)
        assertThat(bodyFatOn(weights.drop(1), "2026-10-15")).isNull()
    }

    // --- targetsOn ---

    @Test
    fun usesTheProfileAndTheWeightOfThatDay() {
        // Before the change: 2 gym days, 20 min walking, 61 kg.
        assertThat(targetsOn(after, history, weights, "2026-10-12")).isEqualTo(
            DayTargets(
                CalorieRecommendation(1325.0, 1.46, 1935.0, 1935.0, 0.0, 1535.0, 1435.0, 1635.0, Goal.CUT),
                ProteinRecommendation(
                    Goal.CUT,
                    ProteinBasis.LEAN_MASS,
                    43.9,
                    ProteinRange(101.0, 136.0),
                    ProteinRange(2.3, 3.1),
                    119.0,
                ),
            ),
        )
        // From the change on: 4 gym days, 60 min walking, 60.5 kg (+111 kcal).
        assertThat(targetsOn(after, history, weights, "2026-10-15").calorie)
            .isEqualTo(CalorieRecommendation(1320.0, 1.55, 2046.0, 2046.0, 0.0, 1646.0, 1546.0, 1746.0, Goal.CUT))
    }

    @Test
    fun usesTheExpenditureMeasuredOnThatDaysWeek() {
        // Measured 2300 ± 177 from the week of 2026-10-12; nothing before.
        val measuredOn: MeasuredOn = { day ->
            if (day >= "2026-10-12") ExpenditureEstimate(2300.0, 177.0, 0.0, 0.0, 28, 28, 28) else null
        }
        val calorie = targetsOn(after, history, weights, "2026-10-15", measuredOn).calorie!!
        assertThat(calorie.tdee).isEqualTo(2046.0)
        assertThat(calorie.expenditure).isEqualTo(2259.0)
        assertThat(calorie.dataShare).isEqualTo(0.84)
        assertThat(calorie.targetMin).isEqualTo(1759.0)
        assertThat(calorie.targetMax).isEqualTo(1959.0)
        val before = targetsOn(after, history, weights, "2026-10-05", measuredOn).calorie!!
        assertThat(before.tdee).isEqualTo(1950.0)
        assertThat(before.expenditure).isEqualTo(1950.0)
        assertThat(before.dataShare).isEqualTo(0.0)
        assertThat(averageTargets(after, history, weights, listOf("2026-10-05", "2026-10-15"), measuredOn).calorie)
            .isEqualTo(TargetRange(1605.0, 1805.0))
    }

    @Test
    fun maintenanceProteinFollowsThatDaysTraining() {
        val untrained = before.copy(gymDaysPerWeek = 0, calorieGoal = Goal.MAINTAIN)
        val trained = untrained.copy(gymDaysPerWeek = 3)
        val history = listOf(ProfilePeriod("2026-10-14", untrained))
        assertThat(targetsOn(trained, history, weights, "2026-10-12").protein?.range).isEqualTo(ProteinRange(85.0, 122.0))
        assertThat(targetsOn(trained, history, weights, "2026-10-15").protein?.range).isEqualTo(ProteinRange(97.0, 133.0))
    }

    @Test
    fun usesTheFirstWeighInBeforeThereWasAny() {
        assertThat(targetsOn(after, history, weights, "2026-09-20").calorie?.tdee).isEqualTo(1950.0)
    }

    @Test
    fun hasNoTargetsWithoutAWeighInAndNoProteinWithoutAGoal() {
        assertThat(targetsOn(after, history, emptyList(), "2026-10-15")).isEqualTo(DayTargets(null, null))
        assertThat(targetsOn(after.copy(calorieGoal = null), emptyList(), weights, "2026-10-15"))
            .isEqualTo(DayTargets(null, null))
    }

    // --- averageTargets ---

    @Test
    fun averagesEachDaysOwnRange() {
        assertThat(averageTargets(after, history, weights, listOf("2026-10-12", "2026-10-15")))
            .isEqualTo(AverageTargets(TargetRange(1491.0, 1691.0), TargetRange(101.0, 136.0)))
    }

    @Test
    fun isNullWithoutDaysOrTargets() {
        assertThat(averageTargets(after, history, weights, emptyList())).isEqualTo(AverageTargets(null, null))
        assertThat(averageTargets(after, history, emptyList(), listOf("2026-10-15")))
            .isEqualTo(AverageTargets(null, null))
    }
}
