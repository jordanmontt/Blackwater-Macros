package com.blackwatermacros.app.data.ai

import androidx.test.core.app.ApplicationProvider
import com.blackwatermacros.app.core.AiConfig
import com.blackwatermacros.app.core.AiInput
import com.blackwatermacros.app.core.AiMessage
import com.blackwatermacros.app.core.AiProvider
import com.blackwatermacros.app.core.AiRole
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
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
 * The AI client (web `ai-settings.test.tsx`, «cliente de IA») and the key store.
 * Never calls a real provider: every host is redirected to a MockWebServer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AiClientTest {

    private val server = MockWebServer()
    private lateinit var client: AiClient

    @Before
    fun setUp() {
        server.start()
        val mock = server.url("/")
        client = AiClient(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val original = chain.request().url
                    val redirected = original.newBuilder().scheme(mock.scheme).host(mock.host).port(mock.port).build()
                    chain.proceed(chain.request().newBuilder().url(redirected).header("X-Original-Host", original.host).build())
                }
                .build(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val gemini = AiConfig(AiProvider.GEMINI, "fake-gemini-key", "")
    private val openai = AiConfig(AiProvider.OPENAI, "k", "")
    private val input = AiInput("s", listOf(AiMessage(AiRole.USER, "hola")), json = false, stream = false, maxTokens = 100)

    private suspend fun failureOf(block: suspend () -> Unit): AiFailure {
        try {
            block()
        } catch (e: AiException) {
            return e.failure
        }
        fail("expected an AiException")
        error("unreachable")
    }

    @Test
    fun `the test call goes straight to Google with the key in a header`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"OK"}]}}]}"""))
        client.test(gemini)

        val request = server.takeRequest()
        assertThat(request.getHeader("X-Original-Host")).isEqualTo("generativelanguage.googleapis.com")
        assertThat(request.path).isEqualTo("/v1beta/models/gemini-flash-latest:generateContent")
        assertThat(request.getHeader("x-goog-api-key")).isEqualTo("fake-gemini-key")
    }

    @Test
    fun `a whole answer is read and a blank one is an error`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"Hola"}}]}"""))
        assertThat(client.complete(openai, input)).isEqualTo("Hola")

        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":" "}}]}"""))
        assertThat(failureOf { client.complete(openai, input) }).isEqualTo(AiFailure.EMPTY)
    }

    @Test
    fun `errors say what the user can do`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"details":[{"reason":"API_KEY_INVALID"}]}}"""))
        assertThat(failureOf { client.test(gemini) }).isEqualTo(AiFailure.INVALID_KEY)

        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        assertThat(failureOf { client.complete(openai, input) }).isEqualTo(AiFailure.QUOTA)

        assertThat(failureOf { client.test(AiConfig(AiProvider.GEMINI, "", "")) }).isEqualTo(AiFailure.NOT_CONFIGURED)
        assertThat(failureOf { client.test(AiConfig(AiProvider.CUSTOM, "", "gemma3", "not a url")) }).isEqualTo(AiFailure.OFFLINE)
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `a streamed answer arrives piece by piece`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n" +
                        "data: {\"choices\":[{\"delta\":{\"content\":\"Ho\"}}]}\n\n" +
                        "data: {\"choices\":[{\"delta\":{\"content\":\"la\"}}]}\n\n" +
                        "data: [DONE]\n\n",
                ),
        )
        assertThat(client.stream(openai, input).toList()).containsExactly("Ho", "la").inOrder()
        assertThat(server.takeRequest().body.readUtf8()).contains("\"stream\":true")
    }

    @Test
    fun `keys are stored encrypted, one per provider, and survive a restart`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences(AiSettingsStore.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val store = AiSettingsStore(prefs, ReversingCipher)

        assertThat(store.current.ready).isFalse()
        store.update { it.copy(apiKeys = it.apiKeys + (AiProvider.GEMINI to "gemini-key")) }
        store.update { it.copy(provider = AiProvider.ANTHROPIC, apiKeys = it.apiKeys + (AiProvider.ANTHROPIC to "claude-key")) }
        store.update { it.copy(models = it.models + (AiProvider.ANTHROPIC to "claude-sonnet-5"), coachSeesData = false) }

        val stored = prefs.all.values.joinToString()
        assertThat(stored).doesNotContain("gemini-key")
        assertThat(stored).doesNotContain("claude-key")

        val reopened = AiSettingsStore(prefs, ReversingCipher).current
        assertThat(reopened.provider).isEqualTo(AiProvider.ANTHROPIC)
        assertThat(reopened.config).isEqualTo(AiConfig(AiProvider.ANTHROPIC, "claude-key", "claude-sonnet-5"))
        assertThat(reopened.apiKeys[AiProvider.GEMINI]).isEqualTo("gemini-key")
        assertThat(reopened.coachSeesData).isFalse()
        assertThat(reopened.copy(provider = AiProvider.GEMINI).config.model).isEqualTo("gemini-flash-latest")
    }

    @Test
    fun `the AI keys are excluded from every kind of backup`() {
        val xml = File("src/main/res/xml/backup_rules.xml").readText() + File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertThat(Regex("""path="${AiSettingsStore.PREFS_NAME}\.xml"""").findAll(xml).count()).isEqualTo(3)
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertThat(manifest).contains("android:fullBackupContent=\"@xml/backup_rules\"")
        assertThat(manifest).contains("android:dataExtractionRules=\"@xml/data_extraction_rules\"")
    }

    /** Robolectric has no Android Keystore; this stands in (reversible, not plain text). */
    private object ReversingCipher : SecretCipher {
        override fun encrypt(plain: String): String = Base64.getEncoder().encodeToString(plain.reversed().toByteArray())
        override fun decrypt(encoded: String): String? = String(Base64.getDecoder().decode(encoded)).reversed()
    }
}
