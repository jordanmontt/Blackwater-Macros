package com.blackwatermacros.app.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Mirrors `src/lib/core/new-product.ts`: «Añadir este producto», a product Open
 * Food Facts doesn't have (or has without nutrition facts), copied by the user
 * from its label, kept on the phone and sent to Open Food Facts.
 */

/** What the label's numbers are for: 100 g, 100 ml or one serving. */
enum class LabelBasis(val id: String) {
    PER_100G("100g"),
    PER_100ML("100ml"),
    SERVING("serving");

    companion object {
        fun fromId(id: String?): LabelBasis? = entries.firstOrNull { it.id == id }
    }
}

/** The «Nuevo producto» form: the label as printed. Null = left empty. */
data class NewProduct(
    val code: String,
    val name: String,
    val brand: String?,
    val basis: LabelBasis,
    /** «200 ml», «30 g». Required for [LabelBasis.SERVING], optional otherwise. */
    val servingSize: String?,
    val calories: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
)

enum class NewProductProblem { NAME, CALORIES, PROTEIN, SERVING, TOO_HIGH }

/** The shared account products are sent with unless the user signs in with theirs (RELEASING.md). */
const val OFF_APP_USER_ID = "blackwater-macros-app"
/** Public on purpose, like other open-source apps' (it only says «sent by Blackwater Macros»). */
const val OFF_APP_PASSWORD = "QQz3yfGiR9I8bM"
const val OFF_APP_NAME = "Blackwater Macros"

/** Grams (or ml) in the serving the label describes, or null. */
fun servingAmount(product: NewProduct): Double? = parseServingGrams(product.servingSize)

/** The label's values per 100 g (or ml), one decimal, as the app stores foods. */
fun newProductPer100g(product: NewProduct): Per100g {
    val serving = servingAmount(product)
    val factor = if (product.basis == LabelBasis.SERVING) (if (serving != null) 100 / serving else 0.0) else 1.0
    fun scale(value: Double?) = round1(maxOf(value ?: 0.0, 0.0) * factor)
    return Per100g(scale(product.calories), scale(product.protein), scale(product.carbs), scale(product.fat))
}

/** What still stops the form from saving, in screen order (see the web). */
fun newProductProblems(product: NewProduct): List<NewProductProblem> {
    val problems = mutableListOf<NewProductProblem>()
    if (product.name.isBlank()) problems += NewProductProblem.NAME
    if (product.basis == LabelBasis.SERVING && servingAmount(product) == null) problems += NewProductProblem.SERVING
    if (product.calories == null || !product.calories.isFinite() || product.calories < 0) problems += NewProductProblem.CALORIES
    if (product.protein == null || !product.protein.isFinite() || product.protein < 0) problems += NewProductProblem.PROTEIN
    if (NewProductProblem.SERVING !in problems) {
        val per100 = newProductPer100g(product)
        if (per100.calories > 900 || per100.protein + per100.carbs + per100.fat > 100.5) problems += NewProductProblem.TOO_HIGH
    }
    return problems
}

/** The product as the rest of the app uses it (portion, search, recents). */
fun newProductToFoodProduct(product: NewProduct): FoodProduct = FoodProduct(
    code = product.code,
    name = product.name.trim(),
    brand = product.brand?.trim()?.ifEmpty { null },
    per100g = newProductPer100g(product),
    servingGrams = servingAmount(product),
    incomplete = product.carbs == null || product.fat == null,
)

/** Who sends it and from which app install (`app_uuid`: a random id, so moderators can block one install). */
data class OffWriteContext(
    val lang: FoodLang,
    val userId: String,
    val password: String,
    val appVersion: String,
    val appUuid: String,
    /** Open Food Facts already had the product (without nutrition): keep its name and brand. */
    val existing: Boolean,
)

private fun credentials(context: OffWriteContext): List<Pair<String, String>> = listOf(
    "user_id" to context.userId,
    "password" to context.password,
    "app_name" to OFF_APP_NAME,
    "app_version" to context.appVersion,
    "app_uuid" to context.appUuid,
)

/** The form fields of `POST /cgi/product_jqm2.pl`: the label as printed. */
fun offProductFields(product: NewProduct, context: OffWriteContext): List<Pair<String, String>> {
    val fields = mutableListOf("code" to product.code)
    fields += credentials(context)
    fields += "lc" to context.lang.code
    if (!context.existing) fields += "product_name_${context.lang.code}" to product.name.trim()
    val brand = product.brand?.trim()
    if (!brand.isNullOrEmpty() && !context.existing) fields += "add_brands" to brand
    fields += "nutrition_data_per" to product.basis.id
    val serving = product.servingSize?.trim()
    if (!serving.isNullOrEmpty()) fields += "serving_size" to serving
    val nutrients = listOf(
        Triple("energy-kcal", product.calories, "kcal"),
        Triple("proteins", product.protein, "g"),
        Triple("carbohydrates", product.carbs, "g"),
        Triple("fat", product.fat, "g"),
    )
    for ((id, value, unit) in nutrients) {
        if (value == null) continue
        fields += "nutriment_$id" to plainNumber(value)
        fields += "nutriment_${id}_unit" to unit
    }
    fields += "comment" to "$OFF_APP_NAME ${context.appVersion}"
    return fields
}

/** The photo's field: the nutrition label in the app language («nutrition_es»). */
fun offLabelImageField(lang: FoodLang): String = "nutrition_${lang.code}"

/** The text fields of `POST /cgi/product_image_upload.pl`; the JPEG goes as `imgupload_<field>`. */
fun offImageFields(code: String, context: OffWriteContext): List<Pair<String, String>> =
    listOf("code" to code) + credentials(context) + listOf("imagefield" to offLabelImageField(context.lang))

/** What to do after an answer: done, try later, the account was refused, or give up. */
enum class OffWriteOutcome { OK, RETRY, ACCOUNT, REJECTED }

private val ACCOUNT_WORDS = Regex("credentials|password|user", RegexOption.IGNORE_CASE)

private fun byHttpStatus(status: Int): OffWriteOutcome? = when {
    status == 401 || status == 403 -> OffWriteOutcome.ACCOUNT
    status == 408 || status == 429 || status >= 500 -> OffWriteOutcome.RETRY
    status !in 200..299 -> OffWriteOutcome.REJECTED
    else -> null
}

/** Answer of `product_jqm2.pl`: `{"status":1,"status_verbose":"fields saved"}`. */
fun offFieldsOutcome(httpStatus: Int, json: JsonElement?): OffWriteOutcome {
    byHttpStatus(httpStatus)?.let { return it }
    val response = json as? JsonObject ?: return OffWriteOutcome.RETRY
    val status = response["status"] as? JsonPrimitive
    if (status?.content == "1") return OffWriteOutcome.OK
    val verbose = jsonText(response["status_verbose"]).orEmpty()
    return if (ACCOUNT_WORDS.containsMatchIn(verbose)) OffWriteOutcome.ACCOUNT else OffWriteOutcome.REJECTED
}

/** Answer of `product_image_upload.pl`; a photo it already has counts as sent. */
fun offImageOutcome(httpStatus: Int, json: JsonElement?): OffWriteOutcome {
    byHttpStatus(httpStatus)?.let { return it }
    val response = json as? JsonObject ?: return OffWriteOutcome.RETRY
    if (jsonText(response["status"]) == "status ok") return OffWriteOutcome.OK
    val error = "${jsonText(response["error"]).orEmpty()} ${jsonText(response["debug"]).orEmpty()}"
    if (Regex("already", RegexOption.IGNORE_CASE).containsMatchIn(error)) return OffWriteOutcome.OK
    return if (ACCOUNT_WORDS.containsMatchIn(error)) OffWriteOutcome.ACCOUNT else OffWriteOutcome.REJECTED
}

/** Name and brand of a product Open Food Facts knows without nutrition, to start the form from. */
data class OffKnownProduct(val name: String?, val brand: String?)

fun offKnownProduct(json: JsonElement?, lang: FoodLang): OffKnownProduct {
    val product = (json as? JsonObject)?.get("product") as? JsonObject ?: return OffKnownProduct(null, null)
    val brandsValue = product["brands"]
    val brands = if (brandsValue is JsonArray) jsonText(brandsValue.firstOrNull()) else jsonText(brandsValue)
    return OffKnownProduct(
        name = jsonText(product["product_name_${lang.code}"]) ?: jsonText(product["product_name"]) ?: jsonText(product["generic_name"]),
        brand = brands?.split(",")?.first()?.trim(),
    )
}

private val NON_WORD = Regex("[^a-z0-9]+")

/** The user's own products matching a search: every word starts a word of the name or brand. */
fun searchOwnProducts(products: List<FoodProduct>, query: String, limit: Int = 5): List<FoodProduct> {
    val words = normalizeText(query).split(NON_WORD).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return products.filter { product ->
        val haystack = normalizeText("${product.name} ${product.brand.orEmpty()}").split(NON_WORD)
        words.all { word -> haystack.any { it.startsWith(word) } }
    }.take(limit)
}

/** What the AI read from a photo of the label (null = not printed or unreadable). */
data class LabelReading(
    val name: String?,
    val brand: String?,
    val basis: LabelBasis,
    val servingSize: String?,
    val calories: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
)

const val LABEL_READING_SHAPE =
    "{\"name\": string | null, \"brand\": string | null, \"basis\": \"100g\" | \"100ml\" | \"serving\", \"servingSize\": string | null, " +
        "\"calories\": number | null, \"protein\": number | null, \"carbs\": number | null, \"fat\": number | null}"

/** System prompt for reading a nutrition label (same text as the web). */
fun buildLabelReadingSystemPrompt(language: String): String = listOf(
    "You read the nutrition label of a packaged food for Blackwater Macros, a calorie and macro tracker.",
    "Reply with only one JSON object with this shape: $LABEL_READING_SHAPE",
    "Copy the numbers exactly as printed. Never estimate or invent a value: use null for anything not printed or not readable.",
    "Use the column per 100 g or per 100 ml when the label has one (\"100g\" or \"100ml\"); otherwise the column per serving (\"serving\") with servingSize as printed, for example \"200 ml\" or \"30 g\".",
    "calories is in kcal. If only kJ is printed, divide by 4.184.",
    "carbs is total carbohydrates (not only sugars), fat is total fat.",
    "name and brand as printed on the package when visible (do not translate them); if only a generic name shows, write it in $language.",
).joinToString("\n")

const val LABEL_READING_USER_TEXT = "A photo of the nutrition label of a packaged food."

private val POSITIVE_NUMBER = Regex("""\d+(?:[.,]\d+)?""")

private fun labelNumber(value: JsonElement?): Double? {
    val primitive = value as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    if (primitive.isString) return POSITIVE_NUMBER.find(primitive.content)?.value?.replace(",", ".")?.toDouble()
    return primitive.content.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
}

private fun toBasis(value: JsonElement?, servingSize: String?): LabelBasis {
    val text = normalizeText((value as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty())
    if (Regex("serv|porc|portion|rac").containsMatchIn(text)) return LabelBasis.SERVING
    if (text.contains("ml")) return LabelBasis.PER_100ML
    if (text.isEmpty() && servingSize != null) return LabelBasis.SERVING
    return LabelBasis.PER_100G
}

/** The first of [keys] that is present and not null (JS `a ?? b`). */
private fun JsonObject.firstOf(vararg keys: String): JsonElement? =
    keys.firstNotNullOfOrNull { key -> this[key]?.takeUnless { it is JsonNull } }

/** The model's answer → the form's values; null when it read neither kcal nor protein. */
fun parseLabelReading(text: String): LabelReading? {
    val record = extractJson(text) as? JsonObject ?: return null
    val servingSize = jsonText(record["servingSize"]) ?: jsonText(record["serving_size"])
    val reading = LabelReading(
        name = jsonText(record["name"]),
        brand = jsonText(record["brand"]),
        basis = toBasis(record["basis"], servingSize),
        servingSize = servingSize,
        calories = labelNumber(record.firstOf("calories", "kcal")),
        protein = labelNumber(record.firstOf("protein", "proteins")),
        carbs = labelNumber(record.firstOf("carbs", "carbohydrates")),
        fat = labelNumber(record["fat"]),
    )
    return if (reading.calories == null && reading.protein == null) null else reading
}
