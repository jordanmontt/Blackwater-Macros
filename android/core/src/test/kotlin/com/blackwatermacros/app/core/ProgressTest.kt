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
}
