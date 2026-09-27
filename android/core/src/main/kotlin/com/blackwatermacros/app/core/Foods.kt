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
// Explicit Unicode spaces (like JS \s): Android's ICU regex rejects the JVM-only `(?U)` flag.
private val SPACES = Regex("[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+")

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
fun parseOffProductFields(value: JsonElement?, lang: FoodLang? = null): FoodProduct? {
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
    // With a language, `product_name_<lang>` wins (Spanish first).
    val localized = lang?.let { jsonText(product["product_name_${it.code}"]) }
    val name = localized ?: jsonText(product["product_name"]) ?: jsonText(product["generic_name"]) ?: brand ?: code ?: ""

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
fun parseOffProduct(json: JsonElement, lang: FoodLang? = null): FoodProduct? {
    val response = json as? JsonObject ?: return null
    val status = response["status"] as? JsonPrimitive
    if (status != null && !status.isString && status.content.toDoubleOrNull() == 0.0) return null
    return parseOffProductFields(response["product"], lang)
}

/** Search-a-licious (`hits`) or legacy search (`products`) response. */
fun parseOffSearch(json: JsonElement, lang: FoodLang? = null): List<FoodProduct> {
    val response = json as? JsonObject ?: return emptyList()
    val list = (response["hits"] as? JsonArray) ?: (response["products"] as? JsonArray) ?: return emptyList()
    return list.mapNotNull { parseOffProductFields(it, lang) }
}

private val WORD_SPLIT = Regex("[^a-z0-9]+")

/**
 * Words that mean the same food in Spain and Latin America (normalized). The
 * bundled names use the Spain variant; a query with any word of a group also
 * matches the others. Same list as the web.
 */
val SPANISH_SYNONYMS: List<List<String>> = listOf(
    listOf("platano", "banana", "banano", "cambur"),
    listOf("patata", "papa"),
    listOf("alubia", "judia", "frijol", "poroto", "habichuela"),
    listOf("melocoton", "durazno"),
    listOf("zumo", "jugo"),
    listOf("maiz", "choclo", "elote"),
    listOf("gamba", "camaron", "langostino"),
    listOf("cacahuete", "cacahuate", "mani"),
    listOf("aguacate", "palta"),
    listOf("fresa", "frutilla"),
    listOf("guisante", "arveja", "chicharo"),
    listOf("calabacin", "zapallito", "calabacita"),
    listOf("pimiento", "morron"),
    listOf("albaricoque", "damasco", "chabacano"),
    listOf("pina", "anana"),
    listOf("bacon", "beicon", "tocino", "tocineta"),
    listOf("boniato", "batata", "camote"),
    listOf("remolacha", "betabel", "betarraga"),
    listOf("col", "repollo"),
    listOf("cerdo", "puerco", "chancho"),
    listOf("ternera", "res", "vacuno", "vaca"),
    listOf("magdalena", "muffin"),
    listOf("pomelo", "toronja"),
    listOf("sandia", "patilla"),
    listOf("champinon", "hongo", "seta"),
    listOf("tomate", "jitomate"),
    listOf("refresco", "gaseosa", "soda"),
    listOf("yogur", "yogurt", "yoghurt"),
    listOf("galleta", "galletita"),
    listOf("yuca", "mandioca"),
    listOf("pavo", "guajolote"),
    listOf("aceituna", "oliva"),
    listOf("cereza", "guinda"),
    listOf("nata", "crema"),
)

private val SYNONYMS_BY_WORD: Map<String, List<String>> =
    SPANISH_SYNONYMS.flatMap { group -> group.map { it to group } }.toMap()

/** What a query word may match: itself, its singular and the synonyms of either. */
fun queryAlternatives(token: String): List<String> {
    val forms = mutableListOf(token)
    if (token.length >= 5 && token.endsWith("es")) forms += token.dropLast(2)
    if (token.length >= 4 && token.endsWith("s")) forms += token.dropLast(1)
    val result = linkedSetOf<String>()
    for (form in forms) {
        result += form
        SYNONYMS_BY_WORD[form]?.let { result += it }
    }
    return result.toList()
}

/**
 * Searches the bundled generic foods in every language they have, ignoring case
 * and accents. Every word of the query (or its singular or a Spanish synonym)
 * must start a word of the name. Best first: exact name, then names that start
 * with the query, then the rest; within each, names where every query word is a
 * whole word, then names in the app language, then shorter names.
 */
fun searchGenericFoods(foods: List<GenericFood>, query: String, lang: FoodLang, limit: Int = 20): List<GenericFoodMatch> {
    val normalizedQuery = normalizeText(query)
    val tokens = normalizedQuery.split(WORD_SPLIT).filter { it.isNotEmpty() }.map(::queryAlternatives)
    if (tokens.isEmpty()) return emptyList()

    data class Scored(val match: GenericFoodMatch, val score: Int, val length: Int)

    val scored = mutableListOf<Scored>()
    for (food in foods) {
        var best: Int? = null
        for ((nameLang, name) in food.names) {
            if (name.isEmpty()) continue
            val normalizedName = normalizeText(name)
            val words = normalizedName.split(WORD_SPLIT).filter { it.isNotEmpty() }
            if (!tokens.all { alternatives -> words.any { word -> alternatives.any { word.startsWith(it) } } }) continue
            val base = when {
                normalizedName == normalizedQuery -> 0
                normalizedName.startsWith(normalizedQuery) -> 1
                else -> 2
            }
            val wholeWords = tokens.all { alternatives -> words.any { it in alternatives } }
            val score = base * 4 + (if (wholeWords) 0 else 2) + if (nameLang == lang) 0 else 1
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

/**
 * The bundled index (`assets/foods/generic.json`):
 * `{ foods: [{ id, s: source, n: { es, en, fr, … }, v: [kcal, protein, carbs, fat] }] }`.
 */
fun parseGenericIndex(json: JsonElement): List<GenericFood> {
    val index = json as? JsonObject ?: return emptyList()
    val entries = index["foods"] as? JsonArray ?: return emptyList()
    return entries.mapNotNull { entry ->
        val food = entry as? JsonObject ?: return@mapNotNull null
        val names = food["n"] as? JsonObject ?: return@mapNotNull null
        val values = food["v"] as? JsonArray ?: return@mapNotNull null
        if (values.size < 4) return@mapNotNull null
        val (calories, protein, carbs, fat) = values.take(4).map { jsonNumber(it) ?: 0.0 }
        val parsedNames = linkedMapOf<FoodLang, String>()
        for ((code, name) in names) {
            val lang = FoodLang.entries.firstOrNull { it.code == code } ?: continue
            jsonText(name)?.let { parsedNames[lang] = it }
        }
        if (parsedNames.isEmpty()) return@mapNotNull null
        GenericFood(
            id = (food["id"] as? JsonPrimitive)?.content.orEmpty(),
            source = (food["s"] as? JsonPrimitive)?.content.orEmpty(),
            names = parsedNames,
            per100g = Per100g(calories, protein, carbs, fat),
        )
    }
}

