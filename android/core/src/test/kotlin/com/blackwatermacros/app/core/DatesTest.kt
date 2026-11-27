package com.blackwatermacros.app.core

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
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
    fun weekStartOf_theMondayOfTheWeek() {
        assertThat(weekStartOf("2026-10-30")).isEqualTo("2026-10-26") // Friday
        assertThat(weekStartOf("2026-10-26")).isEqualTo("2026-10-26") // Monday
        assertThat(weekStartOf("2026-11-01")).isEqualTo("2026-10-26") // Sunday
        assertThat(weekStartOf("2026-01-01")).isEqualTo("2025-12-29") // across the year
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

    // --- A weigh-in's time in its saved time zone ---

    @Test
    fun toZonedDateTimeValue_readsTheTimeWhereTheWeighInWasTaken() {
        val wakingInFrance = Instant.parse("2026-08-25T07:03:00Z")
        assertThat(toZonedDateTimeValue(wakingInFrance, "Europe/Paris")).isEqualTo("2026-08-25T09:03")
        assertThat(toZonedDateTimeValue(wakingInFrance, "America/La_Paz")).isEqualTo("2026-08-25T03:03")
        // The day is that place's day too.
        assertThat(toZonedDateTimeValue(Instant.parse("2026-11-27T03:30:00Z"), "America/La_Paz")).isEqualTo("2026-11-26T23:30")
    }

    @Test
    fun parseZonedDateTime_givesBackTheSameMomentInThatZone() {
        assertThat(parseZonedDateTime("2026-08-25T09:03", "Europe/Paris")).isEqualTo(Instant.parse("2026-08-25T07:03:00Z"))
        assertThat(parseZonedDateTime("2026-11-26T23:30", "America/La_Paz")).isEqualTo(Instant.parse("2026-11-27T03:30:00Z"))
        val instant = Instant.parse("2026-03-01T06:45:00Z")
        assertThat(parseZonedDateTime(toZonedDateTimeValue(instant, "Asia/Kolkata"), "Asia/Kolkata")).isEqualTo(instant)
    }

    @Test
    fun parseZonedDateTime_summerTimeLikeTheWeb() {
        // 25 Oct 2026, Paris: 03:00 → 02:00, so 02:30 happens twice (CEST, then CET).
        assertThat(parseZonedDateTime("2026-10-25T02:30", "Europe/Paris")).isEqualTo(Instant.parse("2026-10-25T00:30:00Z"))
        // 29 Mar 2026, Paris: 02:00 → 03:00, so 02:30 doesn't exist: it becomes 03:30 CEST.
        assertThat(parseZonedDateTime("2026-03-29T02:30", "Europe/Paris")).isEqualTo(Instant.parse("2026-03-29T01:30:00Z"))
    }

    @Test
    fun zonedDateTime_noZoneOrUnknownZoneUsesTheDevicesZone() {
        val original = LocalDateTime.of(2026, 7, 16, 8, 45)
        val instant = original.atZone(ZoneId.systemDefault()).toInstant()
        for (zone in listOf(null, "Marte/Olimpo")) {
            assertThat(toZonedDateTimeValue(instant, zone)).isEqualTo(toDateTimeLocalValue(original))
            assertThat(parseZonedDateTime("2026-07-16T08:45", zone)).isEqualTo(instant)
        }
    }

    @Test
    fun weighInDay_isItsZonesDayElseThePhones() {
        assertThat(weighInDay(Instant.parse("2026-11-27T03:30:00Z"), "America/La_Paz")).isEqualTo("2026-11-26")
        assertThat(weighInDay(Instant.parse("2026-11-26T23:30:00Z"), "Europe/Paris")).isEqualTo("2026-11-27")
        val local = LocalDateTime.of(2026, 7, 16, 0, 30).atZone(ZoneId.systemDefault()).toInstant()
        assertThat(weighInDay(local, null)).isEqualTo("2026-07-16")
    }

    @Test
    fun parseZonedDateTime_rejectsMalformedOrImpossibleValues() {
        assertThat(parseZonedDateTime("ayer por la mañana", "Europe/Paris")).isNull()
        assertThat(parseZonedDateTime("2026-02-30T08:00", "Europe/Paris")).isNull()
    }
}
