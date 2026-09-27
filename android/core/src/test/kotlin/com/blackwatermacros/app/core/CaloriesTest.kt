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

    // --- getActivityMultiplier (factorial method) ---

    @Test
    fun getActivityMultiplier_noExerciseIsTheBaseline() {
        assertThat(getActivityMultiplier(0, 0, 0)).isWithin(1e-10).of(1.4)
    }

    @Test
    fun getActivityMultiplier_walkingOnly() {
        // (1380 x 1.4 + 60 x 3.5) / 1440
        assertThat(getActivityMultiplier(0, 0, 60)).isWithin(1e-10).of(1.4875)
    }

    @Test
    fun getActivityMultiplier_gymSpreadOverTheWeekAndWalking() {
        assertThat(getActivityMultiplier(3, 60, 30)).isWithin(1e-8).of(1.4901785714)
        assertThat(getActivityMultiplier(5, 90, 0)).isWithin(1e-8).of(1.5160714286)
    }

    @Test
    fun getActivityMultiplier_moreTrainingNeverLowersIt() {
        assertThat(getActivityMultiplier(4, 60, 20)).isGreaterThan(getActivityMultiplier(2, 60, 30))
        assertThat(getActivityMultiplier(4, 60, 30)).isGreaterThan(getActivityMultiplier(3, 60, 30))
        assertThat(getActivityMultiplier(3, 90, 30)).isGreaterThan(getActivityMultiplier(3, 60, 30))
        assertThat(getActivityMultiplier(3, 60, 31)).isGreaterThan(getActivityMultiplier(3, 60, 30))
    }

    @Test
    fun getActivityMultiplier_profileMaximums() {
        assertThat(getActivityMultiplier(7, 300, 480)).isWithin(1e-8).of(2.6416666667)
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
        // Edad = 2026 - 1990 = 36 -> TMB 1737.5; factor 1.49018 -> TDEE 2589.19
        val rec = calculateCalorieRecommendation(profile, 80.0, CURRENT_YEAR)

        assertThat(rec).isNotNull()
        val r = rec!!
        assertThat(r.bmr).isEqualTo(1738.0)
        assertThat(r.activityFactor).isEqualTo(1.49)
        assertThat(r.tdee).isEqualTo(2589.0)
        assertThat(r.target).isEqualTo(2189.0)
        assertThat(r.targetMin).isEqualTo(2089.0)
        assertThat(r.targetMax).isEqualTo(2289.0)
        assertThat(r.goal).isEqualTo(Goal.CUT)
    }

    @Test
    fun calculateCalorieRecommendation_surplusAddsCaloriesToTarget() {
        val profile = CalorieProfile(Gender.FEMALE, 1995, 165.0, 5, 60, 40, Goal.SURPLUS)
        // Edad = 2026 - 1995 = 31 -> TMB 1315.25; factor 1.53571 -> TDEE 2019.85
        val rec = calculateCalorieRecommendation(profile, 60.0, CURRENT_YEAR)

        assertThat(rec).isNotNull()
        assertThat(rec!!.activityFactor).isEqualTo(1.54)
        assertThat(rec.tdee).isEqualTo(2020.0)
        assertThat(rec.target).isEqualTo(2320.0)
        assertThat(rec.targetMin).isEqualTo(2220.0)
        assertThat(rec.targetMax).isEqualTo(2420.0)
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

    @Test
    fun calculateCalorieRecommendation_cutNeverGoesBelowBmr() {
        // Woman, 70, 150 cm, 50 kg, no exercise: BMR 927, TDEE 1297.
        val profile = CalorieProfile(Gender.FEMALE, 1956, 150.0, 0, 0, 0, Goal.CUT)
        val rec = calculateCalorieRecommendation(profile, 50.0, 2026)!!
        assertThat(rec.bmr).isEqualTo(927.0)
        assertThat(rec.tdee).isEqualTo(1297.0)
        assertThat(rec.targetMin).isEqualTo(927.0)
        assertThat(rec.target).isEqualTo(927.0)
        assertThat(rec.targetMax).isEqualTo(997.0)
    }
}
