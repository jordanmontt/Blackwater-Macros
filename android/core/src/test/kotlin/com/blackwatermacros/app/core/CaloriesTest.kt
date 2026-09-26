package com.blackwatermacros.app.core

import com.google.common.truth.Truth.assertThat
import kotlin.math.round
import org.junit.Test

/**
 * Kotlin JUnit mirror of `tests/unit/calories.test.ts`. Option A contract:
 * this file must stay in lockstep with the TS spec.
 */
class CaloriesTest {

    // --- calculateBMR ---

    @Test
    fun calculateBMR_mifflinStJeorMale() {
        assertThat(calculateBMR(Gender.MALE, 80.0, 178.0, 35)).isWithin(0.001).of(1742.5)
    }

    @Test
    fun calculateBMR_mifflinStJeorFemale() {
        assertThat(calculateBMR(Gender.FEMALE, 60.0, 165.0, 30)).isWithin(0.001).of(1320.25)
    }

    @Test
    fun calculateBMR_lowWeightHeight() {
        val expected = 10 * 45.0 + 6.25 * 150.0 - 5 * 70 - 161
        assertThat(calculateBMR(Gender.FEMALE, 45.0, 150.0, 70)).isEqualTo(expected)
    }

    // --- getActivityMultiplier ---

    @Test
    fun getActivityMultiplier_sedentary() {
        assertThat(getActivityMultiplier(0, 0, 0)).isEqualTo(1.2)
    }

    @Test
    fun getActivityMultiplier_dailyWalkingNoGym() {
        assertThat(getActivityMultiplier(0, 0, 60)).isEqualTo(1.375)
    }

    @Test
    fun getActivityMultiplier_moderateWithGym() {
        assertThat(getActivityMultiplier(3, 60, 30)).isEqualTo(1.55)
    }

    @Test
    fun getActivityMultiplier_highTraining() {
        assertThat(getActivityMultiplier(5, 90, 0)).isEqualTo(1.725)
    }

    // --- isCalorieProfileComplete ---

    @Test
    fun isCalorieProfileComplete_completeIsTrue() {
        assertThat(
            isCalorieProfileComplete(
                CalorieProfile(Gender.MALE, 1990, 178.0, 3, 60, 30, Goal.CUT),
            ),
        ).isTrue()
    }

    @Test
    fun isCalorieProfileComplete_nullFieldsIsFalse() {
        assertThat(
            isCalorieProfileComplete(CalorieProfile(null, null, null, null, null, null, null)),
        ).isFalse()
    }

    // --- calculateCalorieRecommendation ---

    private val CURRENT_YEAR = 2026

    @Test
    fun calculateCalorieRecommendation_incompleteProfileReturnsNull() {
        val rec = calculateCalorieRecommendation(
            CalorieProfile(null, null, null, null, null, null, null),
            80.0,
            CURRENT_YEAR,
        )
        assertThat(rec).isNull()
    }

    @Test
    fun calculateCalorieRecommendation_cutModeComputesBmrTdeeAndTarget() {
        val profile = CalorieProfile(Gender.MALE, 1990, 178.0, 3, 60, 30, Goal.CUT)
        // Edad = 2026 - 1990 = 36
        val bmr = calculateBMR(Gender.MALE, 80.0, 178.0, 36)
        val tdee = round(bmr * 1.55)
        val rec = calculateCalorieRecommendation(profile, 80.0, CURRENT_YEAR)

        assertThat(rec).isNotNull()
        val r = rec!!
        assertThat(r.bmr).isEqualTo(round(bmr))
        assertThat(r.tdee).isEqualTo(tdee)
        assertThat(r.target).isEqualTo(tdee - 400.0)
        assertThat(r.targetMin).isEqualTo(tdee - 500.0)
        assertThat(r.targetMax).isEqualTo(tdee - 300.0)
        assertThat(r.goal).isEqualTo(Goal.CUT)
    }

    @Test
    fun calculateCalorieRecommendation_surplusAddsCaloriesToTarget() {
        val profile = CalorieProfile(Gender.FEMALE, 1995, 165.0, 5, 60, 40, Goal.SURPLUS)
        // Edad = 2026 - 1995 = 31
        val bmr = calculateBMR(Gender.FEMALE, 60.0, 165.0, 31)
        val multiplier = getActivityMultiplier(5, 60, 40) // 300 gym min, walking >= 30 -> 1.55
        val tdee = round(bmr * multiplier)
        val rec = calculateCalorieRecommendation(profile, 60.0, CURRENT_YEAR)

        assertThat(rec).isNotNull()
        assertThat(rec!!.target).isEqualTo(tdee + 300.0)
        assertThat(rec.goal).isEqualTo(Goal.SURPLUS)
    }

    @Test
    fun calculateCalorieRecommendation_currentYearAffectsAge() {
        val profile = CalorieProfile(Gender.MALE, 2000, 170.0, 2, 45, 20, Goal.MAINTAIN)
        val ageOlder = 2026 - 2000 // 26
        val ageYounger = 2090 - 2000
        val bmrOlder = calculateBMR(Gender.MALE, 75.0, 170.0, ageOlder)
        val bmrYounger = calculateBMR(Gender.MALE, 75.0, 170.0, ageYounger)
        val recOlder = calculateCalorieRecommendation(profile, 75.0, 2026)
        val recYounger = calculateCalorieRecommendation(profile, 75.0, 2090)

        assertThat(recOlder!!.bmr).isEqualTo(round(bmrOlder))
        assertThat(recYounger!!.bmr).isEqualTo(round(bmrYounger))
        assertThat(recOlder.bmr).isGreaterThan(recYounger.bmr)
    }
}