package com.blackwatermacros.app.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Mirrors `src/lib/core/ai-models.ts`: the models a key can use, asked to the
 * provider itself, for the model dropdown in Ajustes → IA. Pure: the request
 * and the reading of the answer; the app module does the GET.
 */

data class AiModelOption(
    /** What goes in the request (`gemini-2.5-flash`). */
    val id: String,
    /** What the user reads (`Gemini 2.5 Flash`). */
    val label: String,
)

data class AiModelListRequest(val url: String, val headers: Map<String, String>)

private const val GEMINI_MODELS = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000"
private const val ANTHROPIC_MODELS = "https://api.anthropic.com/v1/models?limit=1000"

private fun openAiModelsUrl(config: AiConfig): String = when (config.provider) {
    AiProvider.OPENAI -> "https://api.openai.com/v1/models"
    AiProvider.OPENROUTER -> "https://openrouter.ai/api/v1/models"
    else -> "${config.baseUrl.trim().trimEnd('/')}/models"
}

/** The GET that lists the models for [config]'s key. */
fun buildModelListRequest(config: AiConfig): AiModelListRequest {
    val key = config.apiKey.trim()
    return when (config.provider) {
        AiProvider.GEMINI -> AiModelListRequest(GEMINI_MODELS, mapOf("x-goog-api-key" to key))
        AiProvider.ANTHROPIC -> AiModelListRequest(
            ANTHROPIC_MODELS,
            mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01", "anthropic-dangerous-direct-browser-access" to "true"),
        )
        else -> AiModelListRequest(openAiModelsUrl(config), if (key.isNotEmpty()) mapOf("Authorization" to "Bearer $key") else emptyMap())
    }
}

/**
 * Gemini also lists models that cannot chat: embeddings, image and speech
 * generation, live audio, agents. Gemma through the API has no system
 * instruction, which the app needs.
 */
private val GEMINI_EXCLUDED = Regex("(embedding|aqa|imagen|veo|image|tts|live|native-audio|computer-use|robotics|deep-research|gemma|learnlm)")
private val OPENAI_CHAT = Regex("^(gpt-|o\\d|chatgpt-)")
private val OPENAI_EXCLUDED = Regex("(audio|realtime|transcribe|tts|image|search|embedding|instruct|moderation|codex|computer-use|dall-e|whisper)")
private val CHUNKS = Regex("\\d+|\\D+")

/**
 * Numbers compared as numbers, the rest by character code: «gemini-3.8» after
 * «gemini-3.1», «gpt-5.10» after «gpt-5.9». Same order as the TypeScript.
 */
fun naturalCompare(a: String, b: String): Int {
    val x = CHUNKS.findAll(a.lowercase()).map { it.value }.toList()
    val y = CHUNKS.findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(x.size, y.size)) {
        val p = x[i]
        val q = y[i]
        if (p[0].isDigit() && q[0].isDigit()) {
            val pn = p.trimStart('0')
            val qn = q.trimStart('0')
            if (pn.length != qn.length) return pn.length.compareTo(qn.length).coerceIn(-1, 1)
            val byDigits = pn.compareTo(qn).coerceIn(-1, 1)
            if (byDigits != 0) return byDigits
        } else if (p != q) {
            return if (p < q) -1 else 1
        }
    }
    return x.size.compareTo(y.size).coerceIn(-1, 1)
}

private fun JsonElement?.records(): List<JsonObject> = (this as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()

private fun JsonElement?.text(): String = ((this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: "").trim()

/**
 * The provider's answer → the dropdown's options: only models that chat, each
 * once, «…latest» aliases first, then newest first (by the version in the id).
 */
fun parseModelList(provider: AiProvider, body: JsonElement?): List<AiModelOption> {
    val root = body as? JsonObject ?: JsonObject(emptyMap())
    val options = when (provider) {
        AiProvider.GEMINI -> root["models"].records()
            .filter { model -> (model["supportedGenerationMethods"] as? JsonArray)?.any { it.text() == "generateContent" } == true }
            .map { model ->
                val id = model["name"].text().removePrefix("models/")
                AiModelOption(id, model["displayName"].text().ifEmpty { id })
            }
            .filter { it.id.isNotEmpty() && !GEMINI_EXCLUDED.containsMatchIn(it.id) }
        AiProvider.ANTHROPIC -> root["data"].records()
            .map { AiModelOption(it["id"].text(), it["display_name"].text().ifEmpty { it["id"].text() }) }
        AiProvider.OPENROUTER -> root["data"].records()
            .filter { model ->
                val outputs = (model["architecture"] as? JsonObject)?.get("output_modalities") as? JsonArray
                outputs == null || outputs.any { it.text() == "text" }
            }
            .map { AiModelOption(it["id"].text(), it["name"].text().ifEmpty { it["id"].text() }) }
        AiProvider.OPENAI -> root["data"].records()
            .map { it["id"].text() }
            .filter { OPENAI_CHAT.containsMatchIn(it) && !OPENAI_EXCLUDED.containsMatchIn(it) }
            .map { AiModelOption(it, it) }
        // Ollama, LM Studio…: whatever the server has loaded.
        AiProvider.CUSTOM -> root["data"].records().map { AiModelOption(it["id"].text(), it["id"].text()) }
    }
    val seen = mutableSetOf<String>()
    val unique = options.filter { it.id.isNotEmpty() && seen.add(it.id) }
    fun isAlias(option: AiModelOption) = option.id.endsWith("-latest") || option.id.endsWith("/auto")
    return unique.sortedWith { a, b ->
        if (isAlias(a) != isAlias(b)) (if (isAlias(a)) -1 else 1) else naturalCompare(b.id, a.id)
    }
}
