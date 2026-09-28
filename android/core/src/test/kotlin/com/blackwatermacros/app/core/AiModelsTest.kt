package com.blackwatermacros.app.core

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/** Kotlin JUnit mirror of `tests/unit/ai-models.test.ts` (same requests, same options in order). */
class AiModelsTest {

    private fun config(provider: AiProvider, apiKey: String = "test-key", baseUrl: String = "") =
        AiConfig(provider, apiKey, "", baseUrl)

    private fun parse(provider: AiProvider, json: String) = parseModelList(provider, Json.parseToJsonElement(json))

    private val gemini = """{"models":[
        {"name":"models/gemini-2.5-flash","displayName":"Gemini 2.5 Flash","supportedGenerationMethods":["generateContent","countTokens"]},
        {"name":"models/gemini-3.8-flash","displayName":"Gemini 3.8 Flash","supportedGenerationMethods":["generateContent"]},
        {"name":"models/gemini-3-flash","displayName":"Gemini 3 Flash","supportedGenerationMethods":["generateContent"]},
        {"name":"models/gemini-flash-latest","displayName":"Gemini Flash Latest","supportedGenerationMethods":["generateContent"]},
        {"name":"models/gemini-2.5-flash-lite","displayName":"Gemini 2.5 Flash-Lite","supportedGenerationMethods":["generateContent"]},
        {"name":"models/gemini-2.5-flash-preview-tts","displayName":"Gemini 2.5 Flash TTS","supportedGenerationMethods":["generateContent"]},
        {"name":"models/gemini-2.5-flash-image","displayName":"Nano Banana","supportedGenerationMethods":["generateContent"]},
        {"name":"models/gemma-3-27b-it","displayName":"Gemma 3 27B","supportedGenerationMethods":["generateContent"]},
        {"name":"models/gemini-embedding-001","displayName":"Gemini Embedding","supportedGenerationMethods":["embedContent"]},
        {"name":"models/text-embedding-004","displayName":"Text Embedding","supportedGenerationMethods":["embedContent"]}
    ]}"""

    @Test
    fun asksEachProviderWithItsOwnKeyHeader() {
        assertThat(buildModelListRequest(config(AiProvider.GEMINI))).isEqualTo(
            AiModelListRequest("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000", mapOf("x-goog-api-key" to "test-key")),
        )
        assertThat(buildModelListRequest(config(AiProvider.ANTHROPIC))).isEqualTo(
            AiModelListRequest(
                "https://api.anthropic.com/v1/models?limit=1000",
                mapOf("x-api-key" to "test-key", "anthropic-version" to "2023-06-01", "anthropic-dangerous-direct-browser-access" to "true"),
            ),
        )
        assertThat(buildModelListRequest(config(AiProvider.OPENAI))).isEqualTo(
            AiModelListRequest("https://api.openai.com/v1/models", mapOf("Authorization" to "Bearer test-key")),
        )
        assertThat(buildModelListRequest(config(AiProvider.OPENROUTER)).url).isEqualTo("https://openrouter.ai/api/v1/models")
        assertThat(buildModelListRequest(config(AiProvider.CUSTOM, apiKey = "", baseUrl = "http://192.168.1.10:11434/v1/")))
            .isEqualTo(AiModelListRequest("http://192.168.1.10:11434/v1/models", emptyMap()))
    }

    @Test
    fun geminiOnlyChatModelsAliasesFirstNewestFirst() {
        assertThat(parse(AiProvider.GEMINI, gemini)).containsExactly(
            AiModelOption("gemini-flash-latest", "Gemini Flash Latest"),
            AiModelOption("gemini-3.8-flash", "Gemini 3.8 Flash"),
            AiModelOption("gemini-3-flash", "Gemini 3 Flash"),
            AiModelOption("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite"),
            AiModelOption("gemini-2.5-flash", "Gemini 2.5 Flash"),
        ).inOrder()
    }

    @Test
    fun openAiChatModelsOnlyByVersion() {
        val body = """{"data":[{"id":"gpt-5-mini"},{"id":"gpt-5.1"},{"id":"o4-mini"},{"id":"gpt-4o-audio-preview"},{"id":"gpt-realtime"},
            {"id":"text-embedding-3-small"},{"id":"dall-e-3"},{"id":"whisper-1"},{"id":"gpt-image-1"}]}"""
        assertThat(parse(AiProvider.OPENAI, body).map { it.id }).containsExactly("o4-mini", "gpt-5.1", "gpt-5-mini").inOrder()
    }

    @Test
    fun anthropicAndOpenRouterUseDisplayNames() {
        assertThat(
            parse(AiProvider.ANTHROPIC, """{"data":[{"id":"claude-haiku-4-5","display_name":"Claude Haiku 4.5"},{"id":"claude-sonnet-4-5","display_name":"Claude Sonnet 4.5"}]}"""),
        ).containsExactly(
            AiModelOption("claude-sonnet-4-5", "Claude Sonnet 4.5"),
            AiModelOption("claude-haiku-4-5", "Claude Haiku 4.5"),
        ).inOrder()
        assertThat(
            parse(
                AiProvider.OPENROUTER,
                """{"data":[
                    {"id":"google/gemini-3-flash","name":"Google: Gemini 3 Flash","architecture":{"output_modalities":["text"]}},
                    {"id":"openrouter/auto","name":"Auto Router","architecture":{"output_modalities":["text"]}},
                    {"id":"black-forest-labs/flux","name":"FLUX","architecture":{"output_modalities":["image"]}}
                ]}""",
            ),
        ).containsExactly(
            AiModelOption("openrouter/auto", "Auto Router"),
            AiModelOption("google/gemini-3-flash", "Google: Gemini 3 Flash"),
        ).inOrder()
    }

    @Test
    fun aCompatibleServerListsWhatItHasLoadedABrokenAnswerGivesNothing() {
        assertThat(parse(AiProvider.CUSTOM, """{"data":[{"id":"llama3.2"},{"id":"qwen3:8b"},{"id":"llama3.2"}]}""")).containsExactly(
            AiModelOption("qwen3:8b", "qwen3:8b"),
            AiModelOption("llama3.2", "llama3.2"),
        ).inOrder()
        assertThat(parseModelList(AiProvider.GEMINI, null)).isEmpty()
        assertThat(parse(AiProvider.OPENAI, """{"error":"nope"}""")).isEmpty()
    }

    @Test
    fun comparesNumbersAsNumbers() {
        val sorted = listOf("gpt-5.10", "gpt-5.9", "gemini-3.1-pro", "gemini-3.8-flash", "gemini-3-flash").sortedWith(::naturalCompare)
        assertThat(sorted).containsExactly("gemini-3-flash", "gemini-3.1-pro", "gemini-3.8-flash", "gpt-5.9", "gpt-5.10").inOrder()
    }
}
