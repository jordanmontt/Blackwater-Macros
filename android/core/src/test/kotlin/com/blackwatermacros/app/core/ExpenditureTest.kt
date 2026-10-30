package com.blackwatermacros.app.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Kotlin JUnit mirror of `tests/unit/expenditure.test.ts`. Option A contract:
 * this file must stay in lockstep with the TS spec.
 */
class ExpenditureTest {

    private val TODAY = "2026-03-01"

    /** The date `n` days before TODAY (1 = yesterday, 28 = first day of the window). */
    private fun daysAgo(n: Int) = addDaysToKey(TODAY, -n)

    /** One meal per day for `days` days before today, all with the same kcal. */
    private fun meals(days: Int, kcal: Double) = (0 until days).map { DataPoint(daysAgo(it + 1), kcal) }

    /** A weigh-in every `every` days from `first` days ago, changing linearly. */
    private fun weighIns(startKg: Double, kgPerWeek: Double, every: Int = 3, first: Int = 28): List<DataPoint> {
        val points = mutableListOf<DataPoint>()
        var n = first
        while (n >= 1) {
            points += DataPoint(daysAgo(n), startKg + (kgPerWeek / 7) * (first - n))
            n -= every
        }
        return points
    }

    @Test
    fun stableWeight_expenditureEqualsIntake() {
        val est = estimateExpenditure(meals(28, 2500.0), weighIns(80.0, 0.0), TODAY)
        assertThat(est).isNotNull()
        assertThat(est!!.tdee).isEqualTo(2500.0)
        assertThat(est.avgIntake).isEqualTo(2500.0)
        assertThat(est.weightChangePerWeek).isEqualTo(0.0)
        assertThat(est.loggedDays).isEqualTo(28)
        assertThat(est.weighIns).isEqualTo(10)
        assertThat(est.windowDays).isEqualTo(28)
        // Perfect line -> the 0.5 kg noise floor: 1.96 x 0.5 / sqrt(742.5) x 7700 = 277
        assertThat(est.margin).isEqualTo(277.0)
    }

    @Test
    fun losingHalfKgPerWeekOn2500_is3050() {
        val est = estimateExpenditure(meals(28, 2500.0), weighIns(80.0, -0.5), TODAY)
        assertThat(est!!.tdee).isEqualTo(3050.0)
        assertThat(est.weightChangePerWeek).isEqualTo(-0.5)
    }

    @Test
    fun gainingQuarterKgPerWeekOn3000_is2725() {
        val est = estimateExpenditure(meals(28, 3000.0), weighIns(70.0, 0.25), TODAY)
        assertThat(est!!.tdee).isEqualTo(2725.0)
    }

    @Test
    fun unloggedDaysAreLeftOut_notCountedAsZero() {
        val est = estimateExpenditure(meals(22, 2200.0), weighIns(80.0, 0.0), TODAY)
        assertThat(est!!.avgIntake).isEqualTo(2200.0)
        assertThat(est.loggedDays).isEqualTo(22)
    }

    @Test
    fun mealsOnTheSameDayAreSummed() {
        val intake = meals(21, 1000.0).flatMap { listOf(it, it.copy(value = 1400.0)) }
        assertThat(estimateExpenditure(intake, weighIns(80.0, 0.0), TODAY)!!.avgIntake).isEqualTo(2400.0)
    }

    @Test
    fun needs21LoggedDays_zeroKcalDaysAreUnlogged() {
        val intake = meals(21, 2000.0) + meals(28, 0.0).drop(21)
        assertThat(estimateExpenditure(intake, weighIns(80.0, 0.0), TODAY)!!.loggedDays).isEqualTo(21)
        assertThat(estimateExpenditure(meals(20, 2000.0), weighIns(80.0, 0.0), TODAY)).isNull()
    }

    @Test
    fun ignoresTodayAndAnythingBeforeTheWindow() {
        val intake = meals(28, 2000.0) + DataPoint(TODAY, 9000.0) + DataPoint(daysAgo(29), 9000.0)
        val weights = weighIns(80.0, 0.0) + DataPoint(TODAY, 60.0) + DataPoint(daysAgo(40), 100.0)
        val est = estimateExpenditure(intake, weights, TODAY)
        assertThat(est!!.avgIntake).isEqualTo(2000.0)
        assertThat(est.tdee).isEqualTo(2000.0)
    }

    @Test
    fun needsAtLeast4WeighInDays() {
        val three = listOf(daysAgo(28), daysAgo(20), daysAgo(2)).map { DataPoint(it, 80.0) }
        assertThat(estimateExpenditure(meals(28, 2500.0), three, TODAY)).isNull()
        // Two weigh-ins on the same day count once.
        assertThat(estimateExpenditure(meals(28, 2500.0), three + DataPoint(daysAgo(2), 80.2), TODAY)).isNull()
    }

    @Test
    fun needsTheWeighInsToSpanAtLeast14Days() {
        assertThat(estimateExpenditure(meals(28, 2500.0), weighIns(80.0, 0.0, 1, 13), TODAY)).isNull()
        assertThat(estimateExpenditure(meals(28, 2500.0), weighIns(80.0, 0.0, 1, 21), TODAY)).isNotNull()
    }

    @Test
    fun weeklyWeighInsAreTooUncertainToShow() {
        // Days 28, 21, 14, 7: 1.96 x 0.5 / sqrt(245) x 7700 = 482
        assertThat(estimateExpenditure(meals(28, 2500.0), weighIns(80.0, -0.5, 7), TODAY)).isNull()
    }

    @Test
    fun dailyWeighInsNarrowTheMargin() {
        // Sxx = 28 x (28^2 - 1) / 12 = 1827 -> 1.96 x 0.5 / sqrt(1827) x 7700 = 177
        assertThat(estimateExpenditure(meals(28, 2500.0), weighIns(80.0, -0.5, 1), TODAY)!!.margin).isEqualTo(177.0)
    }

    @Test
    fun noisyWeighInsWidenTheMarginBeyondTheFloor() {
        // Every 2 days alternating +-1.2 kg around a stable 80 kg.
        val noisy = weighIns(80.0, 0.0, 2).mapIndexed { i, p -> p.copy(value = p.value + if (i % 2 == 0) 1.2 else -1.2) }
        assertThat(estimateExpenditure(meals(28, 2500.0), noisy, TODAY)).isNull()
        val calm = weighIns(80.0, 0.0, 2).mapIndexed { i, p -> p.copy(value = p.value + if (i % 2 == 0) 0.3 else -0.3) }
        assertThat(estimateExpenditure(meals(28, 2500.0), calm, TODAY)!!.margin).isEqualTo(250.0)
    }

    // --- fitWeightTrend ---

    @Test
    fun fitWeightTrendFitsTheLineAndFloorsTheScatter() {
        val trend = fitWeightTrend(
            listOf(DataPoint("2026-02-01", 80.0), DataPoint("2026-02-08", 79.5), DataPoint("2026-02-15", 79.0)),
        )!!
        assertThat(trend.slopePerDay).isWithin(1e-10).of(-0.5 / 7)
        assertThat(trend.sigma).isEqualTo(0.5)
        assertThat(trend.n).isEqualTo(3)
        assertThat(trend.meanX).isEqualTo(7.0)
        assertThat(trend.meanY).isWithin(1e-10).of(79.5)
        assertThat(trend.sxx).isEqualTo(98.0)
        assertThat(trend.slopeError).isWithin(1e-10).of(0.5 / kotlin.math.sqrt(98.0))
        assertThat(trend.origin).isEqualTo("2026-02-01")
    }

    @Test
    fun fitWeightTrendNeedsThreePointsOnMoreThanOneDay() {
        assertThat(fitWeightTrend(listOf(DataPoint("2026-02-01", 80.0), DataPoint("2026-02-08", 79.0)))).isNull()
        assertThat(fitWeightTrend(listOf(80.0, 80.5, 79.5).map { DataPoint("2026-02-01", it) })).isNull()
    }

    @Test
    fun dailyMeans_onePointPerDayTheMeanSortedByDate() {
        assertThat(
            dailyMeans(
                listOf(
                    DataPoint("2026-02-03", 79.0),
                    DataPoint("2026-02-01", 80.0),
                    DataPoint("2026-02-01", 81.0),
                ),
            ),
        ).containsExactly(DataPoint("2026-02-01", 80.5), DataPoint("2026-02-03", 79.0)).inOrder()
    }

    @Test
    fun morningAndEveningWeighInsDoNotCountTwice() {
        val single = estimateExpenditure(meals(28, 2500.0), weighIns(80.0, -0.5), TODAY)!!
        val twice = weighIns(80.0, -0.5).flatMap { listOf(it, DataPoint(it.date, it.value + 1)) }
        val doubled = estimateExpenditure(meals(28, 2500.0), twice, TODAY)!!
        assertThat(doubled.tdee).isEqualTo(single.tdee)
        assertThat(doubled.margin).isEqualTo(single.margin)
        assertThat(doubled.weighIns).isEqualTo(10)
    }

    // Profile: BMR 1600 × activity factor 1.5 = 2400 kcal/day → normal range 2160–2640 (± 10 %).
    private val FORMULA = CalorieRecommendation(
        bmr = 1600.0, activityFactor = 1.5, tdee = 2400.0, expenditure = 2400.0, dataShare = 0.0,
        target = 2400.0, targetMin = 2300.0, targetMax = 2500.0, goal = Goal.MAINTAIN,
    )

    @Test
    fun measure_returnsCountsEvenWhenNotEnough() {
        val data = measureExpenditure(meals(15, 2400.0), weighIns(80.0, -0.5, 7), TODAY)
        assertThat(data.estimate).isNull()
        assertThat(data.loggedDays).isEqualTo(15)
        assertThat(data.weighInDays).isEqualTo(4)
        assertThat(data.weightSpanDays).isEqualTo(21)
        assertThat(data.margin).isEqualTo(482.0)
    }

    @Test
    fun measure_noMarginWithoutATrend() {
        val data = measureExpenditure(meals(28, 2400.0), listOf(DataPoint(daysAgo(3), 80.0)), TODAY)
        assertThat(data.weighInDays).isEqualTo(1)
        assertThat(data.weightSpanDays).isEqualTo(0)
        assertThat(data.margin).isNull()
    }

    @Test
    fun check_insideFormulaPlusMinus10_matchesEvenWith350Gap() {
        val check = checkExpenditure(meals(35, 2750.0), weighIns(80.0, 0.0, 1, 35), FORMULA, TODAY) as ExpenditureCheck.Measured
        assertThat(check.verdict).isEqualTo(ExpenditureVerdict.MATCHES)
        assertThat(check.confirmed).isTrue()
        assertThat(check.formulaTdee).isEqualTo(2400.0)
        assertThat(check.normalMin).isEqualTo(2160.0)
        assertThat(check.normalMax).isEqualTo(2640.0)
        assertThat(check.gap).isEqualTo(350.0)
    }

    @Test
    fun check_burningClearlyMore() {
        val check = checkExpenditure(meals(35, 2400.0), weighIns(80.0, -0.7, 1, 35), FORMULA, TODAY) as ExpenditureCheck.Measured
        assertThat(check.verdict).isEqualTo(ExpenditureVerdict.HIGHER)
        assertThat(check.confirmed).isTrue()
        assertThat(check.gap).isEqualTo(770.0)
        assertThat(check.estimate.tdee).isEqualTo(3170.0)
        assertThat(check.estimate.margin).isEqualTo(177.0)
        assertThat(check.impliedActivityFactor).isEqualTo(1.98)
    }

    @Test
    fun check_burningClearlyLess() {
        val check = checkExpenditure(meals(35, 1800.0), weighIns(80.0, 0.0, 1, 35), FORMULA, TODAY) as ExpenditureCheck.Measured
        assertThat(check.verdict).isEqualTo(ExpenditureVerdict.LOWER)
        assertThat(check.confirmed).isTrue()
        assertThat(check.gap).isEqualTo(-600.0)
    }

    @Test
    fun check_gapConfirmedOnlyWhenTheWeekBeforeSaysTheSame() {
        val check = checkExpenditure(meals(28, 2400.0), weighIns(80.0, -0.7, 1, 20), FORMULA, TODAY) as ExpenditureCheck.Measured
        assertThat(check.verdict).isEqualTo(ExpenditureVerdict.HIGHER)
        assertThat(check.confirmed).isFalse()
    }

    @Test
    fun check_notEnoughData_saysWhatIsMissing_mealsFirst() {
        fun missing(intake: List<DataPoint>, weights: List<DataPoint>) =
            (checkExpenditure(intake, weights, FORMULA, TODAY) as? ExpenditureCheck.NotEnoughData)?.missing
        assertThat(missing(meals(15, 2400.0), weighIns(80.0, 0.0, 1))).containsExactly(MissingExpenditureData.MEALS)
        val three = listOf(daysAgo(28), daysAgo(20), daysAgo(2)).map { DataPoint(it, 80.0) }
        assertThat(missing(meals(28, 2400.0), three)).containsExactly(MissingExpenditureData.WEIGH_INS)
        assertThat(missing(meals(28, 2400.0), weighIns(80.0, 0.0, 1, 13))).containsExactly(MissingExpenditureData.WEIGHT_SPAN)
        assertThat(missing(meals(28, 2400.0), weighIns(80.0, 0.0, 7))).containsExactly(MissingExpenditureData.MARGIN)
        assertThat(missing(meals(10, 2400.0), weighIns(80.0, 0.0, 7)))
            .containsExactly(MissingExpenditureData.MEALS, MissingExpenditureData.MARGIN).inOrder()
    }

    @Test
    fun gapNotice_onlyWhenConfirmed_andNotNowHoldsOneWindowOfTheSameVerdict() {
        val higher = checkExpenditure(meals(35, 2400.0), weighIns(80.0, -0.7, 1, 35), FORMULA, TODAY)
        assertThat(showsGapNotice(higher, null, TODAY)).isTrue()
        assertThat(showsGapNotice(higher, ExpenditureNoticeDismissal(ExpenditureVerdict.HIGHER, daysAgo(10)), TODAY)).isFalse()
        assertThat(showsGapNotice(higher, ExpenditureNoticeDismissal(ExpenditureVerdict.HIGHER, daysAgo(28)), TODAY)).isTrue()
        assertThat(showsGapNotice(higher, ExpenditureNoticeDismissal(ExpenditureVerdict.LOWER, daysAgo(1)), TODAY)).isTrue()

        val unconfirmed = checkExpenditure(meals(28, 2400.0), weighIns(80.0, -0.7, 1, 20), FORMULA, TODAY)
        assertThat(showsGapNotice(unconfirmed, null, TODAY)).isFalse()
        val matches = checkExpenditure(meals(35, 2400.0), weighIns(80.0, 0.0, 1, 35), FORMULA, TODAY)
        assertThat(showsGapNotice(matches, null, TODAY)).isFalse()
        assertThat(showsGapNotice(null, null, TODAY)).isFalse()
    }

    @Test
    fun weighInHint_whenMealsAreLoggedAndOnlyWeighInsAreMissing() {
        assertThat(onlyWeighInsMissing(checkExpenditure(meals(28, 2400.0), weighIns(80.0, 0.0, 7), FORMULA, TODAY))).isTrue()
        assertThat(onlyWeighInsMissing(checkExpenditure(meals(10, 2400.0), weighIns(80.0, 0.0, 7), FORMULA, TODAY))).isFalse()
        assertThat(onlyWeighInsMissing(checkExpenditure(meals(35, 2400.0), weighIns(80.0, -0.7, 1, 35), FORMULA, TODAY))).isFalse()
        assertThat(onlyWeighInsMissing(null)).isFalse()
    }

    @Test
    fun gapIsSaidRoundedTo50() {
        assertThat(approximateGap(770.0)).isEqualTo(750.0)
        assertThat(approximateGap(-523.0)).isEqualTo(500.0)
        assertThat(approximateGap(530.0)).isEqualTo(550.0)
    }

    // --- combineExpenditure ---

    private fun measured(tdee: Double, margin: Double) = ExpenditureEstimate(tdee, margin, 0.0, 0.0, 28, 28, 28)

    @Test
    fun combineExpenditure_withoutAMeasurementItIsTheProfiles() {
        assertThat(combineExpenditure(2608.0, null)).isEqualTo(CombinedExpenditure(2608.0, 0.0))
    }

    @Test
    fun combineExpenditure_weighsEachByItsPrecision() {
        // σ 260.8 and 214 / 1.96 = 109.2: 260.8² / (260.8² + 109.2²) = 0.851 → 2608 + 0.851 × 452 = 2993
        assertThat(combineExpenditure(2608.0, measured(3060.0, 214.0))).isEqualTo(CombinedExpenditure(2993.0, 0.85))
    }

    @Test
    fun combineExpenditure_theNarrowerTheMarginTheMoreTheDataCount() {
        assertThat(combineExpenditure(2600.0, measured(2600.0, 177.0)).dataShare).isEqualTo(0.89)
        assertThat(combineExpenditure(2600.0, measured(2600.0, 272.0)).dataShare).isEqualTo(0.78)
        assertThat(combineExpenditure(2600.0, measured(2600.0, 300.0)).dataShare).isEqualTo(0.74)
        assertThat(combineExpenditure(2600.0, measured(3000.0, 0.0))).isEqualTo(CombinedExpenditure(3000.0, 1.0))
    }

    // --- weeklyExpenditure ---

    @Test
    fun weeklyExpenditure_everyDayOfAWeekUsesTheMeasurementTakenOnItsMonday() {
        // TODAY (2026-03-01) is a Sunday: its week started on Monday 2026-02-23.
        val intake = meals(40, 2500.0)
        val weights = weighIns(80.0, -0.5, 1, 40)
        val measuredOn = weeklyExpenditure(intake, weights)
        assertThat(measuredOn(TODAY)).isEqualTo(estimateExpenditure(intake, weights, "2026-02-23"))
        assertThat(measuredOn("2026-02-23")).isSameInstanceAs(measuredOn(TODAY))
        assertThat(measuredOn("2026-02-22")).isEqualTo(estimateExpenditure(intake, weights, "2026-02-16"))
        assertThat(measuredOn("2026-02-22")).isNotEqualTo(measuredOn(TODAY))
    }

    @Test
    fun weeklyExpenditure_isNullUntilTheDataCanSupportIt() {
        assertThat(weeklyExpenditure(emptyList(), weighIns(80.0, -0.5, 1, 40))(TODAY)).isNull()
    }
}
