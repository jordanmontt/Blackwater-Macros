package com.blackwatermacros.app.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.math.abs

/**
 * Mirrors `src/lib/core/ai-schema.ts`: the only place where an AI answer turns
 * into meal items.
 */

enum class EstimateConfidence { LOW, MEDIUM, HIGH }

data class EstimatedItem(
    val name: String,
    val grams: Double?,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    /** kcal differ by more than 25 % from 4·protein + 4·carbs + 9·fat. */
    val inconsistent: Boolean,
)

data class MealEstimate(
    val title: String,
    val items: List<EstimatedItem>,
    val confidence: EstimateConfidence?,
    val notes: String?,
)

enum class MealEstimateError { NO_JSON, NO_ITEMS }

sealed interface MealEstimateResult {
    data class Ok(val estimate: MealEstimate) : MealEstimateResult
    data class Error(val error: MealEstimateError) : MealEstimateResult
}

/** The JSON the AI is asked to return (same text as the web). */
const val MEAL_ESTIMATE_SHAPE =
    "{\"title\": string, \"items\": [{\"name\": string, \"grams\": number, \"calories\": number, " +
        "\"protein\": number, \"carbs\": number, \"fat\": number}], \"confidence\": \"low\" | \"medium\" | \"high\", \"notes\": string}"

/** The first JSON value in a model answer, tolerating ```json fences and text around it. */
fun extractJson(text: String): JsonElement? {
    val candidates = listOf(
        text.indexOf('{') to text.lastIndexOf('}'),
        text.indexOf('[') to text.lastIndexOf(']'),
    ).filter { (start, end) -> start >= 0 && end > start }.sortedBy { it.first }
    for ((start, end) in candidates) {
        val parsed = runCatching { Json.parseToJsonElement(text.substring(start, end + 1)) }.getOrNull()
        if (parsed != null) return parsed
    }
    return null
}

private fun firstNumber(record: JsonObject, keys: List<String>): Double? {
    for (key in keys) {
        val value = jsonNumber(record[key])
        if (value != null) return value
    }
    return null
}

private fun parseItem(value: JsonElement): EstimatedItem? {
    val record = value as? JsonObject ?: return null
    val name = jsonText(record["name"]) ?: jsonText(record["food"]) ?: return null

    fun nonNegative(n: Double?) = maxOf(n ?: 0.0, 0.0)
    val grams = firstNumber(record, listOf("grams", "weight_g", "quantity_g", "amount_g", "weight", "quantity"))
    val calories = mathRound(nonNegative(firstNumber(record, listOf("calories", "kcal", "energy_kcal", "energy"))))
    val protein = round1(nonNegative(firstNumber(record, listOf("protein", "proteins", "protein_g"))))
    val carbs = round1(nonNegative(firstNumber(record, listOf("carbs", "carbohydrates", "carbs_g", "carbohydrates_g"))))
    val fat = round1(nonNegative(firstNumber(record, listOf("fat", "fats", "fat_g"))))

    val fromMacros = 4 * protein + 4 * carbs + 9 * fat
    val larger = maxOf(calories, fromMacros)
    val inconsistent = larger >= 20 && abs(calories - fromMacros) > 0.25 * larger

    return EstimatedItem(
        name = name,
        grams = if (grams != null && grams > 0) mathRound(grams) else null,
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        inconsistent = inconsistent,
    )
}

/**
 * Turns a model answer into a meal estimate. Accepts an object with `items`
 * (or `ingredients` / `foods`) or a bare list of items; unknown or negative
 * numbers become 0; items without a name are dropped.
 */
fun parseMealEstimate(text: String): MealEstimateResult {
    val json = extractJson(text) ?: return MealEstimateResult.Error(MealEstimateError.NO_JSON)

    val record = json as? JsonObject
    val list: List<JsonElement> = when {
        json is JsonArray -> json
        record != null -> listOf("items", "ingredients", "foods").firstNotNullOfOrNull { record[it] as? JsonArray } ?: emptyList()
        else -> emptyList()
    }
    val items = list.mapNotNull { parseItem(it) }
    if (items.isEmpty()) return MealEstimateResult.Error(MealEstimateError.NO_ITEMS)

    val confidence = when (jsonText(record?.get("confidence"))?.lowercase()) {
        "low" -> EstimateConfidence.LOW
        "medium" -> EstimateConfidence.MEDIUM
        "high" -> EstimateConfidence.HIGH
        else -> null
    }

    return MealEstimateResult.Ok(
        MealEstimate(
            title = jsonText(record?.get("title")) ?: items.take(3).joinToString(", ") { it.name },
            items = items,
            confidence = confidence,
            notes = jsonText(record?.get("notes")),
        ),
    )
}

/** Ingredient rows for the meal form (per-ingredient mode). */
fun estimateToIngredients(estimate: MealEstimate): List<IngredientInput> =
    estimate.items.map { item ->
        IngredientInput(
            name = item.name,
            quantity = item.grams?.let { "${plainNumber(it)} g" },
            calories = item.calories,
            protein = item.protein,
            carbs = item.carbs,
            fat = item.fat,
        )
    }

/** System prompt for estimating a meal from photos and/or a description. `language` in English («Spanish»). */
fun buildMealEstimateSystemPrompt(language: String): String = listOf(
    "You estimate the nutrition of meals for Blackwater Macros, a calorie and macro tracker.",
    "Reply with only one JSON object with this shape: $MEAL_ESTIMATE_SHAPE",
    "Write the title, the item names and the notes in $language.",
    "One item per food. Add likely hidden ingredients (cooking oil, butter, sauces, dressings, sugar) as their own items.",
    "grams is the edible weight as served. Judge portions with the references in the photos (plate, cutlery, hand).",
    "Quantities the user gives and nutrition labels in the photos beat visual guesses.",
    "calories must match 4 x protein + 4 x carbs + 9 x fat. Whole numbers for grams and calories, one decimal for macros.",
    "confidence is high only when every portion is clear, low when the photo is unclear or much is hidden.",
    "notes is one short sentence in $language with the main assumption (for example the amount of oil).",
).joinToString("\n")

/** The user message next to the photos: how many there are and what the user wrote. */
fun buildMealEstimateUserText(description: String, photoCount: Int): String {
    val text = description.trim()
    if (photoCount == 0) return "Estimate this meal: $text"
    val photos = if (photoCount == 1) {
        "A photo of my meal."
    } else {
        "$photoCount photos of the same meal from different angles (one may be a nutrition label)."
    }
    return if (text.isEmpty()) photos else "$photos\nWhat I can add: $text"
}
