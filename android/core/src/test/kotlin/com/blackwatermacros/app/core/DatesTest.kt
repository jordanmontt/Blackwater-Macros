package com.blackwatermacros.app.core

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Test

/**
 * Kotlin JUnit mirror of `tests/unit/dates.test.ts`. Option A contract:
 * this file must stay in lockstep with the TS spec. `java.time` replaces JS Date.
 */
class DatesTest {

    // --- Date keys (YYYY-MM-DD local) ---

    @Test
    fun toDateKey_localDatesToKey() {
        assertThat(toDateKey(LocalDate.of(2026, 3, 9))).isEqualTo("2026-03-09")
        assertThat(toDateKey(LocalDate.of(2026, 12, 31))).isEqualTo("2026-12-31")
    }

    @Test
    fun isValidDateKey_validAndImpossibleKeys() {
        assertThat(isValidDateKey("2026-02-28")).isTrue()
        assertThat(isValidDateKey("2026-13-01")).isFalse()
        assertThat(isValidDateKey("2026-02-30")).isFalse()
        assertThat(isValidDateKey("09-03-2026")).isFalse()
        assertThat(isValidDateKey("no es fecha")).isFalse()
    }

    @Test
    fun addDaysToKey_crossesMonthsAndYears() {
        assertThat(addDaysToKey("2026-03-01", -1)).isEqualTo("2026-02-28")
        assertThat(addDaysToKey("2026-12-31", 1)).isEqualTo("2027-01-01")
        assertThat(addDaysToKey("2026-05-15", 0)).isEqualTo("2026-05-15")
    }

    @Test
    fun daysBetweenKeys_distanceInDays() {
        assertThat(daysBetweenKeys("2026-03-01", "2026-03-08")).isEqualTo(7)
        assertThat(daysBetweenKeys("2026-03-08", "2026-03-01")).isEqualTo(-7)
    }

    @Test
    fun todayKey_returnsAValidKeyForTheCurrentDay() {
        assertThat(isValidDateKey(todayKey())).isTrue()
    }

    // --- datetime-local selector ---

    @Test
    fun parseLocalDateTime_parsesAsLocalTime() {
        val parsed = parseLocalDateTime("2026-07-16T08:30")
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.year).isEqualTo(2026)
        assertThat(parsed.hour).isEqualTo(8) // if read as UTC would fail outside UTC
    }

    @Test
    fun parseLocalDateTime_rejectsMalformedValues() {
        assertThat(parseLocalDateTime("ayer por la mañana")).isNull()
        assertThat(parseLocalDateTime("2026-07-16")).isNull()
    }

    @Test
    fun nowDateTimeLocalValue_autocompletesPatternAndIsRecent() {
        val value = nowDateTimeLocalValue()
        assertThat(value).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}")
        val parsed = parseLocalDateTime(value)
        assertThat(parsed).isNotNull()
        val now = LocalDateTime.now()
        assertThat(Duration.between(parsed, now).abs().toMillis()).isLessThan(60_000)
    }

    @Test
    fun toDateTimeLocalValue_roundTripKeepsSameLocalTime() {
        val original = LocalDateTime.of(2026, 7, 16, 8, 45)
        val roundTrip = parseLocalDateTime(toDateTimeLocalValue(original))
        assertThat(roundTrip).isEqualTo(original)
    }

    // --- Spanish date formatting ---

    @Test
    fun formatDateKeyLong_capitalizedSpanishLongDate() {
        val formatted = formatDateKeyLong("2026-03-09") // lunes
        assertThat(formatted).containsMatch(java.util.regex.Pattern.compile("lunes", java.util.regex.Pattern.CASE_INSENSITIVE))
        assertThat(formatted.first().isUpperCase()).isTrue()
    }
}