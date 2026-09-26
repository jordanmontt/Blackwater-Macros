package com.blackwatermacros.app.data

import com.blackwatermacros.app.core.isValidDateKey
import com.blackwatermacros.app.core.toCsv
import com.blackwatermacros.app.data.local.normalizeMeasuredAt

/**
 * CSV export/import in the **same format as the web export**
 * (`src/server/services/export-service.ts`), so files move freely between the
 * web and the phone and serve as a backup of a local-only install.
 *
 * Column names are a file format, not UI text: they stay in Spanish whatever
 * the app language.
 */
object CsvBackup {

    private val MEAL_HEADER = listOf(
        "fecha", "comida", "modo", "notas",
        "ingrediente", "cantidad",
        "kcal_ingrediente", "proteina_ingrediente_g", "carbohidratos_ingrediente_g", "grasa_ingrediente_g",
        "total_kcal_comida", "total_proteina_comida_g", "total_carbohidratos_comida_g", "total_grasa_comida_g",
    )
    private val WEIGHT_HEADER = listOf("fecha_hora", "peso_kg", "grasa_corporal_pct", "nota")

    /** One row per ingredient (meal columns repeated); total-only meals are a single row. */
    fun mealsCsv(meals: List<MealDTO>): String {
        val rows = mutableListOf<List<Any?>>(MEAL_HEADER)
        for (meal in meals) {
            val context = listOf(meal.logDate, meal.title, meal.entryMode.wire(), meal.notes)
            val noIngredient = List(6) { null }
            when {
                meal.entryMode == WireEntryMode.TOTAL_ONLY -> rows += context + noIngredient + listOf(
                    num(meal.totalCalories), num(meal.totalProtein), num(meal.totalCarbs), num(meal.totalFat),
                )
                meal.ingredients.isEmpty() -> rows += context + noIngredient + List(4) { null }
                else -> meal.ingredients.forEach { ing ->
                    rows += context + listOf(
                        ing.name, ing.quantity,
                        num(ing.calories), num(ing.protein), num(ing.carbs), num(ing.fat),
                    ) + List(4) { null }
                }
            }
        }
        return toCsv(rows)
    }

    fun weightsCsv(weights: List<WeightDTO>): String =
        toCsv(listOf(WEIGHT_HEADER) + weights.map { listOf(it.measuredAt, num(it.weightKg), num(it.bodyFatPct), it.note) })

    sealed interface Parsed {
        /** [invalidRows] = rows skipped because they would be rejected by the server. */
        data class Meals(val meals: List<MealRequest>, val invalidRows: Int) : Parsed
        data class Weights(val weights: List<WeightRequest>, val invalidRows: Int) : Parsed

        /** Not a Blackwater CSV (header not recognised). */
        data object Unknown : Parsed
    }

    /** Detects the file type from its header. */
    fun parse(text: String): Parsed {
        val rows = parseRows(text).filter { row -> row.any { it.isNotBlank() } }
        val header = rows.firstOrNull()?.map { it.trim() } ?: return Parsed.Unknown
        return when {
            header.take(MEAL_HEADER.size) == MEAL_HEADER -> parseMeals(rows.drop(1))
            header.take(WEIGHT_HEADER.size) == WEIGHT_HEADER -> parseWeights(rows.drop(1))
            else -> Parsed.Unknown
        }
    }

    /**
     * Consecutive rows with the same day/title/mode/notes are one meal (that is
     * how the export writes them).
     */
    private fun parseMeals(rows: List<List<String>>): Parsed.Meals {
        val meals = mutableListOf<MealRequest>()
        var invalid = 0
        var current: MealRequest? = null
        var currentKey: List<String>? = null

        fun flush() {
            current?.let { if (isValidMeal(it)) meals += it else invalid++ }
            current = null
            currentKey = null
        }

        for (raw in rows) {
            val row = raw + List((MEAL_HEADER.size - raw.size).coerceAtLeast(0)) { "" }
            val key = row.take(4)
            val mode = when (row[2].trim()) {
                "total_only" -> WireEntryMode.TOTAL_ONLY
                "per_ingredient" -> WireEntryMode.PER_INGREDIENT
                else -> {
                    flush()
                    invalid++
                    continue
                }
            }
            val ingredient = row[4].trim().takeIf { it.isNotEmpty() }?.let { name ->
                WireIngredient(
                    name = name,
                    quantity = row[5].trim().ifEmpty { null },
                    calories = number(row[6]),
                    protein = number(row[7]),
                    carbs = number(row[8]),
                    fat = number(row[9]),
                )
            }
            if (mode == WireEntryMode.PER_INGREDIENT && key == currentKey && ingredient != null) {
                current = current!!.copy(ingredients = current!!.ingredients + ingredient)
                continue
            }
            flush()
            currentKey = key
            current = MealRequest(
                logDate = row[0].trim(),
                title = row[1].trim(),
                notes = row[3].trim().ifEmpty { null },
                entryMode = mode,
                ingredients = listOfNotNull(ingredient).takeIf { mode == WireEntryMode.PER_INGREDIENT }.orEmpty(),
                totalCalories = number(row[10]).takeIf { mode == WireEntryMode.TOTAL_ONLY },
                totalProtein = number(row[11]).takeIf { mode == WireEntryMode.TOTAL_ONLY },
                totalCarbs = number(row[12]).takeIf { mode == WireEntryMode.TOTAL_ONLY },
                totalFat = number(row[13]).takeIf { mode == WireEntryMode.TOTAL_ONLY },
            )
        }
        flush()
        return Parsed.Meals(meals, invalid)
    }

    private fun parseWeights(rows: List<List<String>>): Parsed.Weights {
        var invalid = 0
        val weights = rows.mapNotNull { row ->
            val weight = runCatching {
                WeightRequest(
                    measuredAt = normalizeMeasuredAt(row[0].trim()),
                    weightKg = number(row[1])!!,
                    bodyFatPct = number(row.getOrElse(2) { "" }),
                    note = row.getOrElse(3) { "" }.trim().ifEmpty { null },
                )
            }.getOrNull()
            weight?.takeIf(::isValidWeight) ?: run {
                invalid++
                null
            }
        }
        return Parsed.Weights(weights, invalid)
    }

    // Same limits as the server's zod schemas (src/server/validation.ts).
    private fun isValidMeal(meal: MealRequest): Boolean =
        isValidDateKey(meal.logDate) &&
            meal.title.isNotEmpty() && meal.title.length <= 120 &&
            meal.ingredients.size <= 100 &&
            meal.ingredients.all { it.name.length <= 200 }

    private fun isValidWeight(weight: WeightRequest): Boolean =
        weight.weightKg in 20.0..400.0 && (weight.bodyFatPct == null || weight.bodyFatPct in 3.0..60.0)

    /** Accepts `.` (export format) and `,` (spreadsheets in Spanish locale). */
    private fun number(cell: String): Double? = cell.trim().replace(',', '.').toDoubleOrNull()

    /** Matches JavaScript's number formatting in the web export: `400`, `8.5`. */
    private fun num(value: Double?): String? = value?.let {
        if (it % 1.0 == 0.0 && kotlin.math.abs(it) < 1e15) it.toLong().toString() else it.toString()
    }

    private fun WireEntryMode.wire(): String = when (this) {
        WireEntryMode.PER_INGREDIENT -> "per_ingredient"
        WireEntryMode.TOTAL_ONLY -> "total_only"
    }

    /** RFC-4180 reader: quoted cells, `""` escapes, CRLF or LF, optional UTF-8 BOM. */
    internal fun parseRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = if (text.startsWith('\uFEFF')) 1 else 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"' && i + 1 < text.length && text[i + 1] == '"') {
                    cell.append('"')
                    i++
                } else if (c == '"') {
                    inQuotes = false
                } else {
                    cell.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> {
                        row += cell.toString()
                        cell.clear()
                    }
                    '\r' -> Unit
                    '\n' -> {
                        row += cell.toString()
                        cell.clear()
                        rows += row
                        row = mutableListOf()
                    }
                    else -> cell.append(c)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row
        }
        return rows
    }
}
