package com.blackwatermacros.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlin.text.Regex

/**
 * Mirrors `src/lib/core/dates.ts`. All date arithmetic is local calendar time
 * (never UTC shifts) using `java.time`.
 */

private val DATE_KEY_RE = Regex("^\\d{4}-\\d{2}-\\d{2}\$")

fun isValidDateKey(value: String): Boolean {
    if (!DATE_KEY_RE.matches(value)) return false
    return try {
        val parsed = LocalDate.parse(value)
        toDateKey(parsed) == value
    } catch (e: DateTimeParseException) {
        false
    }
}

fun toDateKey(date: LocalDate): String = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

fun todayKey(): String = toDateKey(LocalDate.now())

/**
 * The device's time zone ("Europe/Zurich"), saved with each new weigh-in: a
 * move to another zone usually means another scale, so the weight trend compares
 * the weigh-ins before and after it separately (web `deviceTimeZone`).
 */
fun deviceTimeZone(): String = ZoneId.systemDefault().id

fun addDaysToKey(key: String, days: Int): String {
    val date = LocalDate.parse(key)
    return toDateKey(date.plusDays(days.toLong()))
}

/** The Monday of [key]'s week (weeks start on Monday, as in ISO 8601). */
fun weekStartOf(key: String): String {
    val weekday = LocalDate.parse(key).dayOfWeek.value // Monday = 1 .. Sunday = 7
    return addDaysToKey(key, -(weekday - 1))
}

fun daysBetweenKeys(from: String, to: String): Int {
    val a = LocalDate.parse(from)
    val b = LocalDate.parse(to)
    return ChronoUnit.DAYS.between(a, b).toInt()
}

/** Parses a `datetime-local` input value ("YYYY-MM-DDTHH:mm") as local time. */
fun parseLocalDateTime(value: String): LocalDateTime? {
    if (!Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}").containsMatchIn(value)) return null
    return try {
        LocalDateTime.parse(value)
    } catch (e: DateTimeParseException) {
        null
    }
}

/** Value for a `datetime-local` input from a LocalDateTime, in local time. */
fun toDateTimeLocalValue(date: LocalDateTime): String =
    date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))

fun nowDateTimeLocalValue(): String = toDateTimeLocalValue(LocalDateTime.now())

/**
 * The zone a weigh-in is read in: its saved [timeZone], so a weigh-in made on waking in
 * France still reads 08:00 after a move to Bolivia. No zone (older weigh-ins), or one
 * `java.time` doesn't know → the device's.
 */
fun weighInZone(timeZone: String?): ZoneId =
    timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

/** The wall clock ("YYYY-MM-DDTHH:mm") of [instant] in its saved [timeZone] (web `toZonedDateTimeValue`). */
fun toZonedDateTimeValue(instant: Instant, timeZone: String?): String =
    toDateTimeLocalValue(instant.atZone(weighInZone(timeZone)).toLocalDateTime())

/**
 * The calendar day of a weigh-in where it was taken (its saved zone, else the phone's): the day
 * it is listed under and the one statistics, the measured expenditure and the coach count it on
 * (web `weighInDay`).
 */
fun weighInDay(instant: Instant, timeZone: String?): String =
    toDateKey(instant.atZone(weighInZone(timeZone)).toLocalDate())

/**
 * The moment a wall clock ("YYYY-MM-DDTHH:mm") in [timeZone] stands for (web `parseZonedDateTime`):
 * an hour that happens twice (summer time ends) is the first one; one that doesn't exist moves
 * later by the gap, as `atZone` does.
 */
fun parseZonedDateTime(value: String, timeZone: String?): Instant? =
    parseLocalDateTime(value)?.atZone(weighInZone(timeZone))?.toInstant()
