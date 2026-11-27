package com.blackwatermacros.app.ui

import android.text.format.DateFormat
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.blackwatermacros.app.core.formatDecimal
import com.blackwatermacros.app.core.weighInZone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Display formatting: dates in the app's language, numbers in the app's single
 * format (core `formatDecimal`, the same as the web).
 */

private val SupportedLanguages = setOf("en", "es", "fr", "it", "de")

/**
 * The locale the UI is shown in: the one chosen in Ajustes, else the phone's — if
 * we have a translation for it, otherwise English (the string resources' fallback too).
 */
fun appLocale(): Locale {
    val chosen = runCatching { AppCompatDelegate.getApplicationLocales()[0] }.getOrNull()
    val locale = chosen ?: Locale.getDefault()
    return if (locale.language in SupportedLanguages) locale else Locale.ENGLISH
}

/** Languages offered in Ajustes, as BCP-47 tags; each is shown in its own language. */
val AppLanguages = listOf("en" to "English", "es" to "Español", "fr" to "Français", "it" to "Italiano", "de" to "Deutsch")

/** The language picked in the app, or null when it follows the phone. */
fun chosenAppLanguage(): String? =
    AppCompatDelegate.getApplicationLocales().takeUnless { it.isEmpty }?.get(0)?.language

/** Changes the app language (null = follow the phone); the screen is recreated in the new language. */
fun setAppLanguage(tag: String?) {
    AppCompatDelegate.setApplicationLocales(
        if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
    )
}

private fun pattern(skeleton: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(appLocale(), skeleton), appLocale())

/** "Monday, June 15" / "lunes, 15 de junio" (no year). */
fun formatDateLong(key: String): String =
    pattern("EEEEdMMMM").format(LocalDate.parse(key)).replaceFirstChar { it.titlecase(appLocale()) }

/** "Jun 15" / "15 jun". */
fun formatDateShort(key: String): String = pattern("dMMM").format(LocalDate.parse(key))

/** "Sun, Oct 4" / "dom, 4 oct": which day a new meal goes to, in a title (web `formatDateKeyWeekdayShort`). */
fun formatDateWeekdayShort(key: String): String = pattern("EEEdMMM").format(LocalDate.parse(key))

/** "Jun 15, 2026" / "15 jun 2026". */
fun formatDateWithYear(key: String): String = pattern("dMMMy").format(LocalDate.parse(key))

/** A moment as "Nov 11, 10:32" / "11 nov, 10:32", in the phone's time zone. */
fun formatDateTimeShort(epochMillis: Long): String =
    pattern("dMMMjm").format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/**
 * Wall-clock time of an ISO instant in the language's usual hour cycle: "14:05" / "2:05 PM", in
 * [timeZone] (a weigh-in's saved zone, so it reads at the hour it was taken there; web
 * `formatTime`). No zone, or an unknown one → the phone's.
 */
fun formatTime(iso: String, timeZone: String? = null): String =
    pattern("jm").format(Instant.parse(iso).atZone(weighInZone(timeZone)))

/**
 * The app's single number format, the same in every language and on the web
 * (core `formatDecimal`): «1,6», «2000», «12 345». See docs/TECHNICAL.md «Numbers on
 * screen and in inputs».
 */
fun formatNumber(value: Double, maxDecimals: Int = 0): String = formatDecimal(value, maxDecimals)

/** Parses what the user typed, accepting both `,` and `.` as decimal separator. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
