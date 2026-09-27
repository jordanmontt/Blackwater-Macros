package com.blackwatermacros.app.data.foods

import com.blackwatermacros.app.BuildConfig
import com.blackwatermacros.app.core.FoodLang
import com.blackwatermacros.app.core.FoodProduct
import com.blackwatermacros.app.core.parseOffProduct
import com.blackwatermacros.app.core.parseOffSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Open Food Facts (ODbL): product by barcode and text search (Search-a-licious).
 * The phone calls it directly — no CORS here, unlike the web — identifying
 * itself with a User-Agent as Open Food Facts asks. Nothing is stored remotely.
 */
class OpenFoodFactsClient(
    private val http: OkHttpClient,
    private val productBaseUrl: String = "https://world.openfoodfacts.org/api/v2/product/",
    private val searchUrl: String = "https://search.openfoodfacts.org/search",
) {
    /** Null when the product is unknown; throws [IOException] when offline or on errors. */
    suspend fun product(code: String, lang: FoodLang): FoodProduct? = withContext(Dispatchers.IO) {
        val url = "$productBaseUrl$code.json".toHttpUrl().newBuilder().addQueryParameter("fields", FIELDS).build()
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw IOException("Open Food Facts ${response.code}")
            parseOffProduct(Json.parseToJsonElement(response.body!!.string()), lang)
        }
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

        fun create(
            productBaseUrl: String = "https://world.openfoodfacts.org/api/v2/product/",
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
