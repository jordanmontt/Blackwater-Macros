package com.blackwatermacros.app.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Kotlin JUnit mirror of `tests/unit/numbers.test.ts`. */
class NumbersTest {

    private val nnbsp = "\u202F"

    @Test
    fun writesDecimalsWithACommaAndDropsTrailingZeros() {
        assertThat(formatDecimal(1.6, 1)).isEqualTo("1,6")
        assertThat(formatDecimal(1.25, 1)).isEqualTo("1,3")
        assertThat(formatDecimal(8.5, 6)).isEqualTo("8,5")
        assertThat(formatDecimal(2.0, 1)).isEqualTo("2")
        assertThat(formatDecimal(0.05, 1)).isEqualTo("0,1")
        assertThat(formatDecimal(0.05)).isEqualTo("0")
        assertThat(formatDecimal(1999.96, 1)).isEqualTo("2000")
    }

    @Test
    fun neverPrintsMinusZero() {
        assertThat(formatDecimal(-0.04, 1)).isEqualTo("0")
        assertThat(formatDecimal(-0.36, 1)).isEqualTo("-0,4")
        assertThat(formatDecimal(-12345.6)).isEqualTo("-12${nnbsp}346")
    }

    @Test
    fun groupsThousandsWithANarrowSpaceFromFiveDigitsUp() {
        assertThat(formatDecimal(2000.0)).isEqualTo("2000")
        assertThat(formatDecimal(12345.5, 1)).isEqualTo("12${nnbsp}345,5")
        assertThat(formatDecimal(1234567.0)).isEqualTo("1${nnbsp}234${nnbsp}567")
    }

    @Test
    fun doesNotGroupWhenAskedNotTo() {
        assertThat(formatDecimal(12345.5, 6, grouping = false)).isEqualTo("12345,5")
    }

    @Test
    fun normalizeDecimalTurnsEveryTypedDotIntoAComma() {
        assertThat(normalizeDecimal("1.6")).isEqualTo("1,6")
        assertThat(normalizeDecimal("1,6")).isEqualTo("1,6")
        assertThat(normalizeDecimal("")).isEqualTo("")
    }
}
