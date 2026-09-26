package com.blackwatermacros.app.ui

import android.text.format.DateFormat
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Display formatting in the app's language. `:core` keeps its es-ES formatters
 * (they mirror the web); the Android UI uses these instead.
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

/** Local wall-clock time of an ISO instant in the language's usual hour cycle: "14:05" / "2:05 PM". */
fun formatTime(iso: String): String = pattern("jm").format(Instant.parse(iso).atZone(ZoneId.systemDefault()))

/** Localized decimals, no thousands separator: "1234,5" / "1234.5". */
fun formatNumber(value: Double, maxDecimals: Int = 0): String =
    NumberFormat.getNumberInstance(appLocale()).apply {
        maximumFractionDigits = maxDecimals
        isGroupingUsed = false
    }.format(value)

/** Localized with thousands separator: "2.000" / "2,000". */
fun formatNumberGrouped(value: Double, maxDecimals: Int = 0): String =
    NumberFormat.getNumberInstance(appLocale()).apply { maximumFractionDigits = maxDecimals }.format(value)

/** Parses what the user typed, accepting both `,` and `.` as decimal separator. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
