package com.blackwatermacros.app.data

import com.blackwatermacros.app.ui.chart.niceTickStep
import com.blackwatermacros.app.ui.chart.niceTicks
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NiceTicksTest {

    @Test
    fun step_isRoundOneTwoOrFiveTimesPowerOfTen() {
        assertThat(niceTickStep(12.0, 34.0, 4)).isEqualTo(5.0)
        assertThat(niceTickStep(10.0, 20.0, 4)).isEqualTo(2.0)
        assertThat(niceTickStep(0.0, 1.0, 4)).isEqualTo(0.2)
        assertThat(niceTickStep(50.0, 200.0, 4)).isEqualTo(50.0)
    }

    @Test
    fun step_withEmptyOrDegenerateRange_isOne() {
        assertThat(niceTickStep(10.0, 10.0, 4)).isEqualTo(1.0)
    }

    @Test
    fun ticks_areEvenMultiplesOfStep_coveringTheRange() {
        val ticks = niceTicks(12.0, 34.0, 5.0)
        assertThat(ticks).isEqualTo(listOf(15.0, 20.0, 25.0, 30.0, 35.0))
    }

    @Test
    fun ticks_whenMinIsAlreadyAMultiple_StartAtMin() {
        val ticks = niceTicks(15.0, 30.0, 5.0)
        assertThat(ticks).isEqualTo(listOf(15.0, 20.0, 25.0, 30.0))
    }
}
