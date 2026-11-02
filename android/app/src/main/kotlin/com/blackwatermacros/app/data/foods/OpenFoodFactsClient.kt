package com.blackwatermacros.app.data.foods

import com.blackwatermacros.app.BuildConfig
import com.blackwatermacros.app.core.BarcodeMiss
import com.blackwatermacros.app.core.FoodLang
import com.blackwatermacros.app.core.FoodProduct
import com.blackwatermacros.app.core.OffCorrection
import com.blackwatermacros.app.core.OffKnownProduct
import com.blackwatermacros.app.core.barcodeMiss
import com.blackwatermacros.app.core.offKnownProduct
import com.blackwatermacros.app.core.parseOffCorrection
import com.blackwatermacros.app.core.parseOffProduct
import com.blackwatermacros.app.core.parseOffSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * A barcode lookup: the product, or why there is none, with the name and brand
 * Open Food Facts already has when it knows the product without nutrition.
 */
sealed interface BarcodeResult {
    data class Found(val product: FoodProduct) : BarcodeResult
    data class Missing(val reason: BarcodeMiss, val known: OffKnownProduct = OffKnownProduct(null, null)) : BarcodeResult
}

/** «Corregir valores» starts from what Open Food Facts has, and its label photo (URL). */
data class CorrectionStart(val correction: OffCorrection, val labelImage: String?)

/**
 * Open Food Facts (ODbL): product by barcode and text search (Search-a-licious).
 * The phone calls it directly — no CORS here, unlike the web — identifying
 * itself with a User-Agent as Open Food Facts asks. Nothing is stored remotely.
 */
class OpenFoodFactsClient(
    private val http: OkHttpClient,
    private val productBaseUrl: String = "${BuildConfig.OFF_SERVER}/api/v2/product/",
    private val searchUrl: String = "https://search.openfoodfacts.org/search",
) {
    /** The product, or why there is none; throws [IOException] when offline or on errors. */
    suspend fun product(code: String, lang: FoodLang): BarcodeResult = withContext(Dispatchers.IO) {
        val url = "$productBaseUrl$code.json".toHttpUrl().newBuilder().addQueryParameter("fields", FIELDS).build()
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.code == 404) return@withContext BarcodeResult.Missing(barcodeMiss(code, null))
            if (!response.isSuccessful) throw IOException("Open Food Facts ${response.code}")
            val json = Json.parseToJsonElement(response.body!!.string())
            parseOffProduct(json, lang)?.let { BarcodeResult.Found(it) }
                ?: BarcodeResult.Missing(barcodeMiss(code, json), offKnownProduct(json, lang))
        }
    }

    private suspend fun fetchProduct(code: String, lang: FoodLang, fields: String): JsonElement? = withContext(Dispatchers.IO) {
        val url = "$productBaseUrl$code.json".toHttpUrl().newBuilder()
            .addQueryParameter("fields", fields)
            .addQueryParameter("lc", lang.code)
            .build()
        http.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw IOException("Open Food Facts ${response.code}")
            Json.parseToJsonElement(response.body!!.string())
        }
    }

    /**
     * What Open Food Facts has now for «Corregir valores» (fetched again, so the
     * form starts from its latest values and the basis it stores them in), with
     * its label photo. Null when it doesn't have the product; throws [IOException] when offline.
     */
    suspend fun correction(code: String, lang: FoodLang): CorrectionStart? {
        val json = fetchProduct(code, lang, "$FIELDS,nutrition_data_per,image_nutrition_url") ?: return null
        val correction = parseOffCorrection(json, code, lang) ?: return null
        return CorrectionStart(correction, labelImage(json))
    }

    /** The label photo Open Food Facts has for a product (to show it while editing); null offline or without one. */
    suspend fun labelImage(code: String, lang: FoodLang): String? =
        runCatching { fetchProduct(code, lang, "image_nutrition_url")?.let(::labelImage) }.getOrNull()

    /** The photo's bytes, only from Open Food Facts' own image server; null when it can't be loaded. */
    suspend fun image(url: String): ByteArray? = withContext(Dispatchers.IO) {
        if (!OFF_IMAGE.containsMatchIn(url)) return@withContext null
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) response.body!!.bytes() else null
            }
        }.getOrNull()
    }

    private fun labelImage(json: JsonElement): String? {
        val url = ((json as? JsonObject)?.get("product") as? JsonObject)?.get("image_nutrition_url") as? JsonPrimitive
        return url?.takeIf { it.isString }?.content?.takeIf { OFF_IMAGE.containsMatchIn(it) }
    }

    suspend fun search(query: String, lang: FoodLang): List<FoodProduct> = withContext(Dispatchers.IO) {
        val url = searchUrl.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("langs", if (lang == FoodLang.EN) "en" else "${lang.code},en")
            .addQueryParameter("page_size", "20")
            .addQueryParameter("fields", FIELDS)
            .build()
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Open Food Facts ${response.code}")
            parseOffSearch(Json.parseToJsonElement(response.body!!.string()), lang)
        }
    }

    companion object {
        const val FIELDS =
            "code,product_name,product_name_es,product_name_en,product_name_fr,product_name_de,product_name_it," +
                "generic_name,brands,serving_size,serving_quantity,nutriments"

        const val USER_AGENT_PREFIX = "BlackwaterMacros/"

        /** Open Food Facts' image server (its test server's too); nothing else is loaded as a photo. */
        private val OFF_IMAGE = Regex("""^https://images\.openfoodfacts\.(org|net)/""")

        fun create(
            productBaseUrl: String = "${BuildConfig.OFF_SERVER}/api/v2/product/",
            searchUrl: String = "https://search.openfoodfacts.org/search",
        ): OpenFoodFactsClient = OpenFoodFactsClient(
            OkHttpClient.Builder()
                .callTimeout(10, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "$USER_AGENT_PREFIX${BuildConfig.VERSION_NAME} (Android; https://github.com/jordanmontt/Blackwater-Macros)")
                            .build(),
                    )
                }
                .build(),
            productBaseUrl,
            searchUrl,
        )
    }
}
