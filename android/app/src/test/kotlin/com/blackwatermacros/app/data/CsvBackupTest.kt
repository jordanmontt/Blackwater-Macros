package com.blackwatermacros.app.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * CSV export/import must speak the web export's format exactly, so a file
 * exported on the web can be imported on the phone and vice versa, and a
 * local-only user can back up and restore everything.
 */
class CsvBackupTest {

    private fun meal(
        title: String,
        mode: WireEntryMode,
        ingredients: List<WireIngredient> = emptyList(),
        totals: List<Double?> = listOf(null, null, null, null),
        notes: String? = null,
        logDate: String = "2026-06-15",
    ) = MealDTO(
        id = "id-$title", logDate = logDate, title = title, notes = notes, entryMode = mode, ingredients = ingredients,
        totalCalories = totals[0], totalProtein = totals[1], totalCarbs = totals[2], totalFat = totals[3],
        resolvedCalories = 0.0, resolvedProtein = 0.0, resolvedCarbs = 0.0, resolvedFat = 0.0, updatedAt = "",
    )

    @Test
    fun `meals survive an export and re-import unchanged`() {
        val meals = listOf(
            meal(
                "Desayuno, \"grande\"",
                WireEntryMode.PER_INGREDIENT,
                ingredients = listOf(
                    WireIngredient("Avena", "80 g", 300.0, 10.5, 54.0, 5.0),
                    WireIngredient("Leche\nentera", null, 120.0, 6.0, null, null),
                ),
                notes = "antes de entrenar",
            ),
            meal("Batido", WireEntryMode.TOTAL_ONLY, totals = listOf(700.0, 35.0, 90.0, 20.0)),
        )

        val parsed = CsvBackup.parse(CsvBackup.mealsCsv(meals)) as CsvBackup.Parsed.Meals

        assertThat(parsed.invalidRows).isEqualTo(0)
        assertThat(parsed.meals).hasSize(2)
        val (first, second) = parsed.meals
        assertThat(first.title).isEqualTo("Desayuno, \"grande\"")
        assertThat(first.notes).isEqualTo("antes de entrenar")
        assertThat(first.ingredients).isEqualTo(meals[0].ingredients)
        assertThat(second.entryMode).isEqualTo(WireEntryMode.TOTAL_ONLY)
        assertThat(listOf(second.totalCalories, second.totalProtein, second.totalCarbs, second.totalFat))
            .containsExactly(700.0, 35.0, 90.0, 20.0).inOrder()
    }

    @Test
    fun `numbers are written like the web export`() {
        val csv = CsvBackup.mealsCsv(listOf(meal("Batido", WireEntryMode.TOTAL_ONLY, totals = listOf(700.0, 8.5, null, null))))
        assertThat(csv).startsWith("﻿fecha,comida,modo")
        assertThat(csv).contains("2026-06-15,Batido,total_only,,,,,,,,700,8.5,,\r\n")
    }

    @Test
    fun `reads a file exported by the web app`() {
        val web = "﻿fecha,comida,modo,notas,ingrediente,cantidad,kcal_ingrediente,proteina_ingrediente_g," +
            "carbohidratos_ingrediente_g,grasa_ingrediente_g,total_kcal_comida,total_proteina_comida_g," +
            "total_carbohidratos_comida_g,total_grasa_comida_g\r\n" +
            "2026-06-14,Cena,per_ingredient,,Pollo,200 g,330,62,,,,,,\r\n" +
            "2026-06-14,Cena,per_ingredient,,Arroz,,260,5,57,,,,,\r\n" +
            "2026-06-15,Tostadas,per_ingredient,,Pan,,150,5,,,,,,\r\n"

        val parsed = CsvBackup.parse(web) as CsvBackup.Parsed.Meals

        assertThat(parsed.meals.map { it.title }).containsExactly("Cena", "Tostadas").inOrder()
        assertThat(parsed.meals[0].ingredients.map { it.name }).containsExactly("Pollo", "Arroz").inOrder()
        assertThat(parsed.meals[0].ingredients[1].carbs).isEqualTo(57.0)
    }

    @Test
    fun `weights survive an export and re-import`() {
        val weights = listOf(
            WeightDTO("w1", "2026-06-15T06:30:00.000Z", 80.4, 18.2, "ayunas", ""),
            WeightDTO("w2", "2026-06-16T06:30:00.000Z", 80.1, null, null, ""),
        )
        val parsed = CsvBackup.parse(CsvBackup.weightsCsv(weights)) as CsvBackup.Parsed.Weights

        assertThat(parsed.weights.map { it.measuredAt }).containsExactly("2026-06-15T06:30:00.000Z", "2026-06-16T06:30:00.000Z")
        assertThat(parsed.weights.map { it.weightKg }).containsExactly(80.4, 80.1)
        assertThat(parsed.weights[0].bodyFatPct).isEqualTo(18.2)
        assertThat(parsed.weights[0].note).isEqualTo("ayunas")
    }

    @Test
    fun `rows the server would reject are skipped and counted`() {
        val csv = "fecha_hora,peso_kg,grasa_corporal_pct,nota\n" +
            "2026-06-15T06:30:00.000Z,80,,\n" +
            "2026-06-16T06:30:00.000Z,5,,\n" + // below 20 kg
            "not-a-date,80,,\n" +
            "2026-06-17T06:30:00.000Z,\"80,5\",,\n" // comma decimal from a spreadsheet
        val parsed = CsvBackup.parse(csv) as CsvBackup.Parsed.Weights
        assertThat(parsed.weights.map { it.weightKg }).containsExactly(80.0, 80.5)
        assertThat(parsed.invalidRows).isEqualTo(2)
    }

    @Test
    fun `unrelated files are recognised as unknown`() {
        assertThat(CsvBackup.parse("name,email\nana,a@b.c\n")).isEqualTo(CsvBackup.Parsed.Unknown)
        assertThat(CsvBackup.parse("")).isEqualTo(CsvBackup.Parsed.Unknown)
    }
}
