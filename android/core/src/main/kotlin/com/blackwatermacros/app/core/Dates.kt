package com.blackwatermacros.app.core

import java.time.LocalDate
import java.time.LocalDateTime
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

fun addDaysToKey(key: String, days: Int): String {
    val date = LocalDate.parse(key)
    return toDateKey(date.plusDays(days.toLong()))
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
