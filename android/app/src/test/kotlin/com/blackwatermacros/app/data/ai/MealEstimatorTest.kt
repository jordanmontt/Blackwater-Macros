package com.blackwatermacros.app.data.ai

import androidx.test.core.app.ApplicationProvider
import com.blackwatermacros.app.core.AiImage
import com.blackwatermacros.app.core.AiProvider
import com.blackwatermacros.app.core.EstimateConfidence
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Base64

/**
 * «Foto» (web `add-food-photo.test.tsx`): photos + description go to the chosen
 * provider as one JSON-mode request and come back as a meal estimate; photos
 * are downscaled and never kept. Never calls a real provider.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MealEstimatorTest {

    private val server = MockWebServer()
    private lateinit var estimator: MealEstimator

    @Before
    fun setUp() {
        server.start()
        val mock = server.url("/")
        val client = AiClient(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url.newBuilder().scheme(mock.scheme).host(mock.host).port(mock.port).build()
                    chain.proceed(chain.request().newBuilder().url(url).build())
                }
                .build(),
        )
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences("ai_test", android.content.Context.MODE_PRIVATE)
        val store = AiSettingsStore(prefs, PlainCipher)
        store.update { it.copy(provider = AiProvider.GEMINI, apiKeys = mapOf(AiProvider.GEMINI to "fake-key")) }
        estimator = MealEstimator(client, store)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun gemini(text: String) = MockResponse().setBody(
        """{"candidates":[{"content":{"parts":[{"text":${Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(text))}}]}}]}""",
    )

    @Test
    fun `photos and description become one JSON request and an estimate`() = runBlocking {
        server.enqueue(
            gemini("""{"title":"Pasta boloñesa","items":[{"name":"Espaguetis cocidos","grams":180,"calories":284,"protein":10.4,"carbs":55.8,"fat":1.7}],"confidence":"medium","notes":"Aceite estimado."}"""),
        )
        val estimate = estimator.estimate("con aceite", listOf(AiImage("image/jpeg", "SMALLJPEG")), aiLanguageName("es"))

        assertThat(estimate.title).isEqualTo("Pasta boloñesa")
        assertThat(estimate.confidence).isEqualTo(EstimateConfidence.MEDIUM)
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertThat(body["systemInstruction"].toString()).contains("in Spanish")
        assertThat(body["contents"]!!.jsonArray[0].jsonObject["parts"].toString()).isEqualTo(
            """[{"inline_data":{"mime_type":"image/jpeg","data":"SMALLJPEG"}},{"text":"A photo of my meal.\nWhat I can add: con aceite"}]""",
        )
        assertThat(body["generationConfig"].toString()).contains("application/json")
    }

    @Test
    fun `an answer without a meal is reported as unreadable`() = runBlocking {
        server.enqueue(gemini("No veo comida en la foto."))
        try {
            estimator.estimate("tortilla", emptyList(), "Spanish")
            fail("expected an AiException")
        } catch (e: AiException) {
            assertThat(e.failure).isEqualTo(AiFailure.UNREADABLE)
        }
    }

    @Test
    fun `the model is told the app language in English`() {
        assertThat(aiLanguageName("es")).isEqualTo("Spanish")
        assertThat(aiLanguageName("de")).isEqualTo("German")
        assertThat(aiLanguageName("pt")).isEqualTo("English")
    }

    @Test
    fun `photos are shrunk to 1024 px on the longest side, never enlarged`() {
        assertThat(fitWithin(4032, 3024)).isEqualTo(1024 to 768)
        assertThat(fitWithin(3000, 4000)).isEqualTo(768 to 1024)
        assertThat(fitWithin(800, 600)).isEqualTo(800 to 600)
    }

    @Test
    fun `the camera can only write into the temporary photo folder`() {
        val paths = File("src/main/res/xml/photo_paths.xml").readText()
        assertThat(Regex("<(cache|files|external)[a-z-]*-path").findAll(paths).count()).isEqualTo(1)
        assertThat(paths).contains("""<cache-path name="ai_photos" path="ai-photos/" />""")
    }

    private object PlainCipher : SecretCipher {
        override fun encrypt(plain: String) = Base64.getEncoder().encodeToString(plain.toByteArray())
        override fun decrypt(encoded: String) = String(Base64.getDecoder().decode(encoded))
    }
}
