package com.blackwatermacros.app.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Kotlin JUnit mirror of the CSV part of `tests/unit/auth-and-csv.test.ts`.
 * Option A contract: this file must stay in lockstep with the TS spec.
 */
class CsvTest {

    @Test
    fun toCsv_escapesCommasQuotesAndLineBreaksInNotes() {
        val csv = toCsv(
            listOf(
                listOf("fecha", "nota"),
                listOf("2026-03-01", "comida \"especial\", con salto\n de línea"),
            ),
        )
        assertThat(csv).contains("\"comida \"\"especial\"\", con salto\n")
        assertThat(csv.startsWith("\uFEFF")).isTrue() // BOM para Excel
    }

    @Test
    fun toCsv_convertsNumbersAndNullsToEmptyCells() {
        val csv = toCsv(listOf(listOf(1.5, null, null)))
        assertThat(csv).contains("1.5,,")
    }
}