package com.blackwatermacros.app.core

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/** Kotlin JUnit mirror of `tests/unit/ai-providers.test.ts` (same bodies, byte for byte). */
class AiProvidersTest {

    private val photo = AiInput(
        system = "Eres un nutricionista.",
        messages = listOf(AiMessage(AiRole.USER, "Mi comida", listOf(AiImage("image/jpeg", "AAAA")))),
        json = true,
        stream = false,
        maxTokens = 1024,
    )

    private val chat = AiInput(
        system = "Coach",
        messages = listOf(
            AiMessage(AiRole.USER, "Hola"),
            AiMessage(AiRole.ASSISTANT, "¿Qué tal?"),
            AiMessage(AiRole.USER, "¿Qué ceno?"),
        ),
        json = false,
        stream = true,
        maxTokens = 512,
    )

    private fun config(provider: AiProvider, apiKey: String = "test-key", model: String = "", baseUrl: String = "") =
        AiConfig(provider, apiKey, model, baseUrl)

    private fun json(text: String) = Json.parseToJsonElement(text)

    @Test
    fun geminiKeyInHeaderJsonModeInlinePhotosDefaultModel() {
        val request = buildAiRequest(config(AiProvider.GEMINI), photo)
        assertThat(request.url).isEqualTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent")
        assertThat(request.headers).isEqualTo(mapOf("Content-Type" to "application/json", "x-goog-api-key" to "test-key"))
        assertThat(request.body).isEqualTo(
            """{"systemInstruction":{"parts":[{"text":"Eres un nutricionista."}]},"contents":[{"role":"user","parts":[{"inline_data":{"mime_type":"image/jpeg","data":"AAAA"}},{"text":"Mi comida"}]}],"generationConfig":{"maxOutputTokens":1024,"responseMimeType":"application/json"}}""",
        )
    }

    @Test
    fun geminiStreamingUsesSseAndModelRole() {
        val request = buildAiRequest(config(AiProvider.GEMINI, model = "gemini-3.5-flash"), chat)
        assertThat(request.url).isEqualTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:streamGenerateContent?alt=sse")
        assertThat(request.body).isEqualTo(
            """{"systemInstruction":{"parts":[{"text":"Coach"}]},"contents":[{"role":"user","parts":[{"text":"Hola"}]},{"role":"model","parts":[{"text":"¿Qué tal?"}]},{"role":"user","parts":[{"text":"¿Qué ceno?"}]}],"generationConfig":{"maxOutputTokens":512}}""",
        )
    }

    @Test
    fun openAiBearerSystemMessageDataUrlsJsonMode() {
        val request = buildAiRequest(config(AiProvider.OPENAI), photo)
        assertThat(request.url).isEqualTo("https://api.openai.com/v1/chat/completions")
        assertThat(request.headers).isEqualTo(mapOf("Content-Type" to "application/json", "Authorization" to "Bearer test-key"))
        assertThat(request.body).isEqualTo(
            """{"model":"${DEFAULT_MODELS.getValue(AiProvider.OPENAI)}","messages":[{"role":"system","content":"Eres un nutricionista."},{"role":"user","content":[{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,AAAA"}},{"type":"text","text":"Mi comida"}]}],"stream":false,"max_completion_tokens":1024,"response_format":{"type":"json_object"}}""",
        )
    }

    @Test
    fun openRouterAndCustomServersShareTheFormatCustomMayHaveNoKey() {
        assertThat(buildAiRequest(config(AiProvider.OPENROUTER), chat).url).isEqualTo("https://openrouter.ai/api/v1/chat/completions")
        val local = buildAiRequest(
            config(AiProvider.CUSTOM, apiKey = " ", baseUrl = "http://192.168.1.10:11434/v1/", model = "gemma3:4b"),
            chat,
        )
        assertThat(local.url).isEqualTo("http://192.168.1.10:11434/v1/chat/completions")
        assertThat(local.headers).isEqualTo(mapOf("Content-Type" to "application/json"))
        assertThat(local.body).isEqualTo(
            """{"model":"gemma3:4b","messages":[{"role":"system","content":"Coach"},{"role":"user","content":"Hola"},{"role":"assistant","content":"¿Qué tal?"},{"role":"user","content":"¿Qué ceno?"}],"stream":true,"max_tokens":512}""",
        )
    }

    @Test
    fun anthropicHeadersAndBase64Images() {
        val request = buildAiRequest(config(AiProvider.ANTHROPIC), photo)
        assertThat(request.url).isEqualTo("https://api.anthropic.com/v1/messages")
        assertThat(request.headers).isEqualTo(
            mapOf(
                "Content-Type" to "application/json",
                "x-api-key" to "test-key",
                "anthropic-version" to "2023-06-01",
                "anthropic-dangerous-direct-browser-access" to "true",
            ),
        )
        assertThat(request.body).isEqualTo(
            """{"model":"claude-haiku-4-5","max_tokens":1024,"system":"Eres un nutricionista.\nReply with JSON only.","messages":[{"role":"user","content":[{"type":"image","source":{"type":"base64","media_type":"image/jpeg","data":"AAAA"}},{"type":"text","text":"Mi comida"}]}],"stream":false}""",
        )
    }

    @Test
    fun readsEachProvidersAnswerText() {
        assertThat(
            parseAiResponse(AiProvider.GEMINI, json("""{"candidates":[{"content":{"role":"model","parts":[{"text":"{\"title\":"},{"text":"\"Pasta\"}"}]}}]}""")),
        ).isEqualTo("""{"title":"Pasta"}""")
        assertThat(parseAiResponse(AiProvider.OPENAI, json("""{"choices":[{"index":0,"message":{"role":"assistant","content":"Hola"}}]}""")))
            .isEqualTo("Hola")
        assertThat(parseAiResponse(AiProvider.ANTHROPIC, json("""{"content":[{"type":"thinking","thinking":"…"},{"type":"text","text":"Hola"}]}""")))
            .isEqualTo("Hola")
    }

    @Test
    fun returnsEmptyTextForUnexpectedShapes() {
        assertThat(parseAiResponse(AiProvider.GEMINI, json("""{"candidates":[]}"""))).isEmpty()
        assertThat(parseAiResponse(AiProvider.OPENROUTER, json("""{"error":{"message":"x"}}"""))).isEmpty()
        assertThat(parseAiResponse(AiProvider.ANTHROPIC, null)).isEmpty()
    }

    @Test
    fun readsTheTextDeltasOfEachProvider() {
        assertThat(parseAiStreamLine(AiProvider.GEMINI, """data: {"candidates":[{"content":{"parts":[{"text":"Ho"}]}}]}""")).isEqualTo("Ho")
        assertThat(parseAiStreamLine(AiProvider.CUSTOM, """data: {"choices":[{"delta":{"content":"la"}}]}""")).isEqualTo("la")
        assertThat(
            parseAiStreamLine(AiProvider.ANTHROPIC, """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"!"}}"""),
        ).isEqualTo("!")
    }

    @Test
    fun ignoresEventsWithoutText() {
        assertThat(parseAiStreamLine(AiProvider.OPENAI, "data: [DONE]")).isNull()
        assertThat(parseAiStreamLine(AiProvider.OPENAI, """data: {"choices":[{"delta":{"role":"assistant"}}]}""")).isNull()
        assertThat(parseAiStreamLine(AiProvider.ANTHROPIC, "event: message_start")).isNull()
        assertThat(parseAiStreamLine(AiProvider.ANTHROPIC, """data: {"type":"ping"}""")).isNull()
        assertThat(parseAiStreamLine(AiProvider.OPENROUTER, ": OPENROUTER PROCESSING")).isNull()
        assertThat(parseAiStreamLine(AiProvider.GEMINI, "data: {not json")).isNull()
    }

    @Test
    fun needsAKeyOrAServerAndModelForCustomServers() {
        assertThat(isAiConfigured(config(AiProvider.GEMINI, apiKey = ""))).isFalse()
        assertThat(isAiConfigured(config(AiProvider.GEMINI))).isTrue()
        assertThat(isAiConfigured(config(AiProvider.CUSTOM, apiKey = "", baseUrl = "http://localhost:11434/v1"))).isFalse()
        assertThat(isAiConfigured(config(AiProvider.CUSTOM, apiKey = "", baseUrl = "http://localhost:11434/v1", model = "gemma3"))).isTrue()
    }

    @Test
    fun mapsHttpErrorsToWhatTheUserCanDo() {
        assertThat(aiErrorKind(400, """{"error":{"status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}"""))
            .isEqualTo(AiErrorKind.INVALID_KEY)
        assertThat(aiErrorKind(400, """{"error":{"message":"Invalid image"}}""")).isEqualTo(AiErrorKind.PROVIDER)
        assertThat(aiErrorKind(401, "")).isEqualTo(AiErrorKind.INVALID_KEY)
        assertThat(aiErrorKind(403, "")).isEqualTo(AiErrorKind.INVALID_KEY)
        assertThat(aiErrorKind(429, "")).isEqualTo(AiErrorKind.QUOTA)
        assertThat(aiErrorKind(402, "")).isEqualTo(AiErrorKind.QUOTA)
        assertThat(aiErrorKind(404, "")).isEqualTo(AiErrorKind.NOT_FOUND)
        assertThat(aiErrorKind(503, "")).isEqualTo(AiErrorKind.UNAVAILABLE)
        assertThat(aiErrorKind(500, "")).isEqualTo(AiErrorKind.UNAVAILABLE)
        assertThat(aiErrorKind(418, "")).isEqualTo(AiErrorKind.PROVIDER)
    }

    @Test
    fun readsTheProvidersOwnErrorMessage() {
        assertThat(aiErrorDetail("""{"error":{"code":503,"message":"The model is overloaded. Please try again later.","status":"UNAVAILABLE"}}"""))
            .isEqualTo("The model is overloaded. Please try again later.")
        assertThat(aiErrorDetail("""{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""")).isEqualTo("Overloaded")
        assertThat(aiErrorDetail("""{"error":"Bad gateway"}""")).isEqualTo("Bad gateway")
        assertThat(aiErrorDetail("<html>  502\n Bad Gateway </html>")).isEqualTo("<html> 502 Bad Gateway </html>")
        assertThat(aiErrorDetail("x".repeat(500))).hasLength(200)
    }
}
