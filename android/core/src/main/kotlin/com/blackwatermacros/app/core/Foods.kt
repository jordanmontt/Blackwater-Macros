package com.blackwatermacros.app.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.Normalizer
import kotlin.math.floor

/**
 * Mirrors `src/lib/core/foods.ts`: portion scaling, Open Food Facts parsing and
 * search in the bundled generic foods.
 */

/** Nutrition per 100 g (or 100 ml) of a food, as food databases publish it. */
data class Per100g(val calories: Double, val protein: Double, val carbs: Double, val fat: Double)

/** A product from Open Food Facts (barcode or text search). */
data class FoodProduct(
    val code: String?,
    val name: String,
    val brand: String?,
    val per100g: Per100g,
    /** Grams (or ml) in one serving, when the product declares it. */
    val servingGrams: Double?,
    /** Some macros were missing in the source and count as 0. */
    val incomplete: Boolean,
)

enum class FoodLang(val code: String) { ES("es"), EN("en"), FR("fr"), DE("de"), IT("it") }

/** A generic food from the bundled offline index (USDA, CIQUAL, Swiss FCDB). */
data class GenericFood(
    val id: String,
    val source: String,
    /** Insertion order matters for the display fallback, as in JS. */
    val names: Map<FoodLang, String>,
    val per100g: Per100g,
)

data class GenericFoodMatch(val food: GenericFood, val name: String)

/** Same text as JS `String(n)` for app numbers: «120», «12.5», never «120.0». */
fun plainNumber(value: Double): String =
    if (value == floor(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()

/** Nutrition of `grams` of a food: kcal rounded to integers, macros to 1 decimal. */
fun scalePer100g(per100g: Per100g, grams: Double): NutritionTotals {
    val factor = grams / 100
    return NutritionTotals(
        calories = mathRound(per100g.calories * factor),
        protein = round1(per100g.protein * factor),
        carbs = round1(per100g.carbs * factor),
        fat = round1(per100g.fat * factor),
    )
}

/** An ingredient row for the meal form: «Yogur griego · 150 g» with its macros. */
fun foodToIngredient(name: String, per100g: Per100g, grams: Double): IngredientInput {
    val totals = scalePer100g(per100g, grams)
    return IngredientInput(
        name = name,
        quantity = "${plainNumber(round1(grams))} g",
        calories = totals.calories,
        protein = totals.protein,
        carbs = totals.carbs,
        fat = totals.fat,
    )
}

private val SERVING_REGEX = Regex("""(\d+(?:[.,]\d+)?)\s*(?:g|gr|grams?|gramos?|ml)(?![a-z])""", RegexOption.IGNORE_CASE)

/** Grams in a serving description: «30 g» → 30, «1 cup (240 ml)» → 240. Null when there is none. */
fun parseServingGrams(text: String?): Double? {
    if (text == null) return null
    val match = SERVING_REGEX.find(text) ?: return null
    val grams = match.groupValues[1].replace(",", ".").toDouble()
    return if (grams > 0) grams else null
}

private val MARKS = Regex("\\p{Mn}+")
private val SPACES = Regex("(?U)\\s+")

/** Lowercase, without accents and with single spaces: «Yogúr  Griego» → «yogur griego». */
fun normalizeText(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(MARKS, "")
        .lowercase()
        .replace(SPACES, " ")
        .trim()

private val NUMBER_REGEX = Regex("""-?\d+(?:[.,]\d+)?""")

/** Numbers, or the first number in a string («150 g» → 150). */
internal fun jsonNumber(value: JsonElement?): Double? {
    val primitive = value as? JsonPrimitive ?: return null
    if (primitive.isString) return NUMBER_REGEX.find(primitive.content)?.value?.replace(",", ".")?.toDouble()
    return primitive.content.toDoubleOrNull()?.takeIf { it.isFinite() }
}

internal fun jsonText(value: JsonElement?): String? {
    val primitive = value as? JsonPrimitive ?: return null
    if (!primitive.isString) return null
    return primitive.content.trim().ifEmpty { null }
}

private const val KJ_PER_KCAL = 4.184

/**
 * One Open Food Facts product object (from `/api/v2/product/<code>` or a
 * Search-a-licious hit). Null when it has no energy value.
 */
fun parseOffProductFields(value: JsonElement?): FoodProduct? {
    val product = value as? JsonObject ?: return null
    val nutriments = product["nutriments"] as? JsonObject ?: JsonObject(emptyMap())

    val kcal = jsonNumber(nutriments["energy-kcal_100g"])
    val kj = jsonNumber(nutriments["energy-kj_100g"]) ?: jsonNumber(nutriments["energy_100g"])
    val calories = kcal ?: kj?.let { it / KJ_PER_KCAL }
    if (calories == null || calories < 0) return null

    val protein = jsonNumber(nutriments["proteins_100g"])
    val carbs = jsonNumber(nutriments["carbohydrates_100g"])
    val fat = jsonNumber(nutriments["fat_100g"])

    val brandsValue = product["brands"]
    val brands = if (brandsValue is JsonArray) jsonText(brandsValue.firstOrNull()) else jsonText(brandsValue)
    val brand = brands?.split(",")?.get(0)?.trim()
    val code = jsonText(product["code"])
    val name = jsonText(product["product_name"]) ?: jsonText(product["generic_name"]) ?: brand ?: code ?: ""

    val declaredServing = jsonNumber(product["serving_quantity"])
    val servingGrams = if (declaredServing != null && declaredServing > 0) {
        declaredServing
    } else {
        parseServingGrams(jsonText(product["serving_size"]))
    }

    return FoodProduct(
        code = code,
        name = name,
        brand = brand,
        per100g = Per100g(
            calories = round1(calories),
            protein = round1(maxOf(protein ?: 0.0, 0.0)),
            carbs = round1(maxOf(carbs ?: 0.0, 0.0)),
            fat = round1(maxOf(fat ?: 0.0, 0.0)),
        ),
        servingGrams = servingGrams,
        incomplete = protein == null || carbs == null || fat == null,
    )
}

/** Response of `/api/v2/product/<code>.json`; null when the product is unknown. */
fun parseOffProduct(json: JsonElement): FoodProduct? {
    val response = json as? JsonObject ?: return null
    val status = response["status"] as? JsonPrimitive
    if (status != null && !status.isString && status.content.toDoubleOrNull() == 0.0) return null
    return parseOffProductFields(response["product"])
}

/** Search-a-licious (`hits`) or legacy search (`products`) response. */
fun parseOffSearch(json: JsonElement): List<FoodProduct> {
    val response = json as? JsonObject ?: return emptyList()
    val list = (response["hits"] as? JsonArray) ?: (response["products"] as? JsonArray) ?: return emptyList()
    return list.mapNotNull { parseOffProductFields(it) }
}

private val WORD_SPLIT = Regex("[^a-z0-9]+")

/**
 * Searches the bundled generic foods in every language they have, ignoring case
 * and accents. Every word of the query must start a word of the name. Best
 * first: exact name, then names that start with the query, then the rest; names
 * in the app language win ties, then shorter names.
 */
fun searchGenericFoods(foods: List<GenericFood>, query: String, lang: FoodLang, limit: Int = 20): List<GenericFoodMatch> {
    val normalizedQuery = normalizeText(query)
    val tokens = normalizedQuery.split(WORD_SPLIT).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return emptyList()

    data class Scored(val match: GenericFoodMatch, val score: Int, val length: Int)

    val scored = mutableListOf<Scored>()
    for (food in foods) {
        var best: Int? = null
        for ((nameLang, name) in food.names) {
            if (name.isEmpty()) continue
            val normalizedName = normalizeText(name)
            val words = normalizedName.split(WORD_SPLIT).filter { it.isNotEmpty() }
            if (!tokens.all { token -> words.any { it.startsWith(token) } }) continue
            val base = when {
                normalizedName == normalizedQuery -> 0
                normalizedName.startsWith(normalizedQuery) -> 1
                else -> 2
            }
            val score = base * 2 + if (nameLang == lang) 0 else 1
            if (best == null || score < best) best = score
        }
        if (best == null) continue
        val display = food.names[lang] ?: food.names[FoodLang.EN] ?: food.names.values.firstOrNull { it.isNotEmpty() } ?: food.id
        scored += Scored(GenericFoodMatch(food, display), best, display.length)
    }

    return scored
        .sortedWith(compareBy<Scored>({ it.score }, { it.length }, { it.match.food.id }))
        .take(limit)
        .map { it.match }
}
