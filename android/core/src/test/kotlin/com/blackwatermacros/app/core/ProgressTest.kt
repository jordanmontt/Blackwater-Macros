package com.blackwatermacros.app.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Kotlin JUnit mirror of `tests/unit/progress.test.ts`. */
class ProgressTest {

    @Test
    fun averagesLoggedDaysOnly() {
        assertThat(
            macroAverages(
                listOf(
                    DailyNutritionPoint("2026-03-01", 2000.0, 150.0, 200.0, 60.0),
                    DailyNutritionPoint("2026-03-02", 0.0, 0.0, 0.0, 0.0),
                    DailyNutritionPoint("2026-03-03", 2200.0, 130.0, 250.0, 70.0),
                ),
            ),
        ).isEqualTo(MacroAverages(2, 3, 2100.0, 140.0, 225.0, 65.0, MacroSplit(27.0, 44.0, 29.0)))
    }

    @Test
    fun isNullWhenNothingWasLogged() {
        assertThat(macroAverages(emptyList())).isNull()
        assertThat(macroAverages(listOf(DailyNutritionPoint("2026-03-01", 0.0, 0.0, 0.0, 0.0)))).isNull()
    }

    @Test
    fun hasNoSplitWhenOnlyCaloriesWereLogged() {
        assertThat(macroAverages(listOf(DailyNutritionPoint("2026-03-01", 2000.0, 0.0, 0.0, 0.0)))!!.split).isNull()
    }

    /** [days] logged days, all with the same macros. */
    private fun period(days: Int, protein: Double, carbs: Double, fat: Double): MacroAverages =
        macroAverages(
            (1..days).map { DailyNutritionPoint("2026-03-%02d".format(it), protein * 4 + carbs * 4 + fat * 9, protein, carbs, fat) },
        )!!

    @Test
    fun checkFatShare_lowBelow20Percent() {
        // 540 + 1060 + 315 = 1915 kcal; 315 / 1915 = 16 %
        assertThat(checkFatShare(period(7, 135.0, 265.0, 35.0)))
            .isEqualTo(FatShareCheck(FatVerdict.LOW, 16.0, 35.0, 315.0, 1915.0))
    }

    @Test
    fun checkFatShare_highAbove35Percent() {
        // 500 + 620 + 981 = 2101 kcal; 981 / 2101 = 47 %
        val check = checkFatShare(period(7, 125.0, 155.0, 109.0))!!
        assertThat(check.verdict).isEqualTo(FatVerdict.HIGH)
        assertThat(check.share).isEqualTo(47.0)
    }

    @Test
    fun checkFatShare_saysNothingInside20To35_onTheRoundedShare() {
        assertThat(checkFatShare(period(7, 140.0, 225.0, 65.0))).isNull() // 29 %
        // 396 / 1996 = 19.8 %, shown as 20 %: not called low.
        assertThat(checkFatShare(period(7, 100.0, 300.0, 44.0))).isNull()
    }

    @Test
    fun checkFatShare_needs7LoggedDaysAndMacros() {
        assertThat(checkFatShare(period(6, 135.0, 265.0, 35.0))).isNull()
        val caloriesOnly = (1..7).map { DailyNutritionPoint("2026-03-0$it", 2000.0, 0.0, 0.0, 0.0) }
        assertThat(checkFatShare(macroAverages(caloriesOnly)!!)).isNull()
    }
}
