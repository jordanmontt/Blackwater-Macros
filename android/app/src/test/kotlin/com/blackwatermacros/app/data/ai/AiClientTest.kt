package com.blackwatermacros.app.data.ai

import androidx.test.core.app.ApplicationProvider
import com.blackwatermacros.app.core.AiConfig
import com.blackwatermacros.app.core.AiInput
import com.blackwatermacros.app.core.AiMessage
import com.blackwatermacros.app.core.AiProvider
import com.blackwatermacros.app.core.AiRole
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
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
            retryDelaysMs = listOf(0L, 0L),
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
    fun `an overloaded provider is retried, then explained with its own words`() = runBlocking {
        val overloaded = """{"error":{"code":503,"message":"The model is overloaded. Please try again later.","status":"UNAVAILABLE"}}"""
        server.enqueue(MockResponse().setResponseCode(503).setBody(overloaded))
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"Hola"}}]}"""))
        assertThat(client.complete(openai, input)).isEqualTo("Hola")

        repeat(3) { server.enqueue(MockResponse().setResponseCode(503).setBody(overloaded)) }
        try {
            client.complete(openai, input)
            fail("expected an AiException")
        } catch (e: AiException) {
            assertThat(e.failure).isEqualTo(AiFailure.UNAVAILABLE)
            assertThat(e.detail).isEqualTo("503: The model is overloaded. Please try again later.")
        }
        assertThat(server.requestCount).isEqualTo(5)
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

    /** Collects a stream: the pieces that arrived and the failure it ended with, if any. */
    private fun collect(config: AiConfig, body: String): Pair<List<String>, AiException?> = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body))
        val pieces = mutableListOf<String>()
        val error = try {
            client.stream(config, input).collect { pieces += it }
            null
        } catch (e: AiException) {
            e
        }
        pieces to error
    }

    @Test
    fun `an answer that stops before it is done fails after the text that arrived`() {
        // Token limit (reasoning models spend part of it thinking).
        val (cut, cutError) = collect(
            openai,
            "data: {\"choices\":[{\"delta\":{\"content\":\"Lunes\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\n\n",
        )
        assertThat(cut).containsExactly("Lunes")
        assertThat(cutError?.failure).isEqualTo(AiFailure.TRUNCATED)

        // The connection ends without [DONE] or a finish reason.
        val (dropped, droppedError) = collect(openai, "data: {\"choices\":[{\"delta\":{\"content\":\"Lunes\"}}]}\n\n")
        assertThat(dropped).containsExactly("Lunes")
        assertThat(droppedError?.failure).isEqualTo(AiFailure.INTERRUPTED)

        // The provider reports a failure inside the stream (Anthropic, overloaded).
        val (failed, failedError) = collect(
            AiConfig(AiProvider.ANTHROPIC, "k", ""),
            "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Lunes\"}}\n\n" +
                "event: error\ndata: {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}\n\n",
        )
        assertThat(failed).containsExactly("Lunes")
        assertThat(failedError?.failure).isEqualTo(AiFailure.UNAVAILABLE)
        assertThat(failedError?.detail).isEqualTo("Overloaded")

        // Gemini's last chunk: text and finish reason together.
        val (done, doneError) = collect(gemini, "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Fin.\"}]},\"finishReason\":\"STOP\"}]}\n\n")
        assertThat(done).containsExactly("Fin.")
        assertThat(doneError).isNull()
    }

    @Test
    fun `a slow screen still gets every piece before the error`() = runBlocking {
        // The phone's main thread can lag behind the network: the pieces already read must not be
        // dropped when the failure comes right after them.
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                (1..5).joinToString("") { "data: {\"choices\":[{\"delta\":{\"content\":\"$it\"}}]}\n\n" } +
                    "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\n\n",
            ),
        )
        val pieces = mutableListOf<String>()
        val error = try {
            client.stream(openai, input).collect {
                delay(50)
                pieces += it
            }
            null
        } catch (e: AiException) {
            e
        }
        assertThat(pieces).containsExactly("1", "2", "3", "4", "5").inOrder()
        assertThat(error?.failure).isEqualTo(AiFailure.TRUNCATED)
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
