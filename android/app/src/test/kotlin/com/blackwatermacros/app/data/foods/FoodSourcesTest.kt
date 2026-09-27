package com.blackwatermacros.app.data.foods

import androidx.test.core.app.ApplicationProvider
import com.blackwatermacros.app.core.FoodLang
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Open Food Facts client (barcode + search), the bundled generic foods and
 * the recent picks — the data behind «Buscar» and «Código de barras».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class FoodSourcesTest {

    private val server = MockWebServer()
    private lateinit var client: OpenFoodFactsClient

    @Before
    fun setUp() {
        server.start()
        client = OpenFoodFactsClient.create(
            productBaseUrl = server.url("/api/v2/product/").toString(),
            searchUrl = server.url("/search").toString(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val product = """{"status":1,"product":{"code":"8480000592170","product_name":"Greek yogurt","product_name_es":"Yogur griego natural","brands":"Hacendado","serving_quantity":125,"nutriments":{"energy-kcal_100g":122,"proteins_100g":3.5,"carbohydrates_100g":4.2,"fat_100g":10}}}"""

    @Test
    fun `a barcode finds the product with its Spanish name and identifies the app`() = runBlocking {
        server.enqueue(MockResponse().setBody(product))

        val found = client.product("8480000592170", FoodLang.ES)!!
        assertThat(found.name).isEqualTo("Yogur griego natural")
        assertThat(found.brand).isEqualTo("Hacendado")
        assertThat(found.servingGrams).isEqualTo(125.0)

        val request = server.takeRequest()
        assertThat(request.path).startsWith("/api/v2/product/8480000592170.json?fields=")
        assertThat(request.getHeader("User-Agent")).startsWith(OpenFoodFactsClient.USER_AGENT_PREFIX)
    }

    @Test
    fun `an unknown barcode is null, a server error is an error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        assertThat(client.product("1", FoodLang.ES)).isNull()
        server.enqueue(MockResponse().setBody("""{"status":0}"""))
        assertThat(client.product("2", FoodLang.ES)).isNull()
        server.enqueue(MockResponse().setResponseCode(503))
        val failure = runCatching { client.product("3", FoodLang.ES) }.exceptionOrNull()
        assertThat(failure).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `search asks in Spanish first and keeps products with energy`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"hits":[${product.substringAfter("\"product\":").dropLast(1)},{"code":"2","product_name":"Sin energía","nutriments":{}}]}""",
            ),
        )

        val results = client.search("yogur griego", FoodLang.ES)
        assertThat(results.map { it.name }).containsExactly("Yogur griego natural")
        val url = server.takeRequest().requestUrl!!
        assertThat(url.queryParameter("q")).isEqualTo("yogur griego")
        assertThat(url.queryParameter("langs")).isEqualTo("es,en")
    }

    @Test
    fun `the bundled generic foods load from the assets`() = runBlocking {
        val foods = GenericFoodsStore.fromAssets(ApplicationProvider.getApplicationContext()).all()
        assertThat(foods.size).isGreaterThan(3000)
        // Spanish first: every bundled food has a Spanish name.
        assertThat(foods.all { com.blackwatermacros.app.core.FoodLang.ES in it.names }).isTrue()
    }

    @Test
    fun `recent foods keep the last picks, newest first, without repeats`() {
        val recents = RecentFoods(ApplicationProvider.getApplicationContext<android.content.Context>())
        fun choice(key: String) = FoodChoice(key, key, null, 100.0, 1.0, 2.0, 3.0, null, "generic")
        (1..25).forEach { recents.remember(choice("f$it")) }
        recents.remember(choice("f10"))

        val list = recents.list()
        assertThat(list).hasSize(20)
        assertThat(list.first().key).isEqualTo("f10")
        assertThat(list.count { it.key == "f10" }).isEqualTo(1)
    }
}
