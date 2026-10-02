package com.blackwatermacros.app.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.URLEncoder

/**
 * Mirrors `src/lib/core/ai-providers.ts`: the request and answer format of each
 * cloud AI provider. The app module only does the HTTP transport, so both
 * platforms send byte-identical bodies.
 */

enum class AiProvider(val id: String) {
    GEMINI("gemini"),
    OPENAI("openai"),
    ANTHROPIC("anthropic"),
    OPENROUTER("openrouter"),
    CUSTOM("custom");

    companion object {
        fun fromId(id: String?): AiProvider? = entries.firstOrNull { it.id == id }
    }
}

data class AiConfig(
    val provider: AiProvider,
    val apiKey: String,
    val model: String,
    /** Only for [AiProvider.CUSTOM]: the server's OpenAI-compatible base URL (…/v1). */
    val baseUrl: String = "",
)

data class AiImage(val mimeType: String, /** Base64 without the `data:` prefix. */ val data: String)

enum class AiRole(val wire: String) { USER("user"), ASSISTANT("assistant") }

data class AiMessage(val role: AiRole, val text: String, val images: List<AiImage> = emptyList())

data class AiInput(
    val system: String,
    val messages: List<AiMessage>,
    /** Ask for a JSON object (meal estimates). */
    val json: Boolean,
    val stream: Boolean,
    val maxTokens: Int,
)

data class AiHttpRequest(val url: String, val headers: Map<String, String>, val body: String)

val DEFAULT_MODELS: Map<AiProvider, String> = mapOf(
    AiProvider.GEMINI to "gemini-flash-latest",
    AiProvider.OPENAI to "gpt-5-mini",
    AiProvider.ANTHROPIC to "claude-haiku-4-5",
    AiProvider.OPENROUTER to "openrouter/auto",
    AiProvider.CUSTOM to "",
)

private const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models/"
private const val OPENAI_BASE = "https://api.openai.com/v1"
private const val OPENROUTER_BASE = "https://openrouter.ai/api/v1"
private const val ANTHROPIC_URL = "https://api.anthropic.com/v1/messages"

private fun openAiBase(config: AiConfig): String = when (config.provider) {
    AiProvider.OPENAI -> OPENAI_BASE
    AiProvider.OPENROUTER -> OPENROUTER_BASE
    else -> config.baseUrl.trim().trimEnd('/')
}

/** Same as JS `encodeURIComponent` for model ids (letters, digits, `-._~` and a few more stay). */
private fun encodeComponent(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20").replace("%21", "!").replace("%27", "'")
        .replace("%28", "(").replace("%29", ")").replace("%7E", "~")

/** The HTTP request for one call. `stream` asks for server-sent events. */
fun buildAiRequest(config: AiConfig, input: AiInput): AiHttpRequest {
    val model = config.model.trim().ifEmpty { DEFAULT_MODELS.getValue(config.provider) }

    if (config.provider == AiProvider.GEMINI) {
        val method = if (input.stream) "streamGenerateContent?alt=sse" else "generateContent"
        val body = buildJsonObject {
            putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", input.system) } } }
            putJsonArray("contents") {
                for (message in input.messages) addJsonObject {
                    put("role", if (message.role == AiRole.ASSISTANT) "model" else "user")
                    putJsonArray("parts") {
                        for (image in message.images) addJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", image.mimeType)
                                put("data", image.data)
                            }
                        }
                        addJsonObject { put("text", message.text) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("maxOutputTokens", input.maxTokens)
                if (input.json) put("responseMimeType", "application/json")
            }
        }
        return AiHttpRequest(
            url = "$GEMINI_BASE${encodeComponent(model)}:$method",
            headers = mapOf("Content-Type" to "application/json", "x-goog-api-key" to config.apiKey),
            body = body.toString(),
        )
    }

    if (config.provider == AiProvider.ANTHROPIC) {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", input.maxTokens)
            put("system", if (input.json) "${input.system}\nReply with JSON only." else input.system)
            putJsonArray("messages") {
                for (message in input.messages) addJsonObject {
                    put("role", message.role.wire)
                    putJsonArray("content") {
                        for (image in message.images) addJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", image.mimeType)
                                put("data", image.data)
                            }
                        }
                        addJsonObject {
                            put("type", "text")
                            put("text", message.text)
                        }
                    }
                }
            }
            put("stream", input.stream)
        }
        return AiHttpRequest(
            url = ANTHROPIC_URL,
            headers = mapOf(
                "Content-Type" to "application/json",
                "x-api-key" to config.apiKey,
                "anthropic-version" to "2023-06-01",
                "anthropic-dangerous-direct-browser-access" to "true",
            ),
            body = body.toString(),
        )
    }

    val headers = buildMap {
        put("Content-Type", "application/json")
        if (config.apiKey.isNotBlank()) put("Authorization", "Bearer ${config.apiKey.trim()}")
    }
    val body = buildJsonObject {
        put("model", model)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", input.system)
            }
            for (message in input.messages) addJsonObject {
                put("role", message.role.wire)
                if (message.images.isEmpty()) {
                    put("content", message.text)
                } else {
                    putJsonArray("content") {
                        for (image in message.images) addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", "data:${image.mimeType};base64,${image.data}") }
                        }
                        addJsonObject {
                            put("type", "text")
                            put("text", message.text)
                        }
                    }
                }
            }
        }
        put("stream", input.stream)
        // OpenAI's reasoning models only accept the new name; other servers know the old one.
        put(if (config.provider == AiProvider.OPENAI) "max_completion_tokens" else "max_tokens", input.maxTokens)
        if (input.json) putJsonObject("response_format") { put("type", "json_object") }
    }
    return AiHttpRequest("${openAiBase(config)}/chat/completions", headers, body.toString())
}

private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private fun geminiText(json: JsonElement?): String {
    val parts = json.obj()?.get("candidates").arr()?.firstOrNull().obj()?.get("content").obj()?.get("parts").arr()
        ?: return ""
    return parts.mapNotNull { it.obj()?.get("text").str() }.joinToString("")
}

/** The answer text of a non-streaming response. */
fun parseAiResponse(provider: AiProvider, json: JsonElement?): String = when (provider) {
    AiProvider.GEMINI -> geminiText(json)
    AiProvider.ANTHROPIC -> json.obj()?.get("content").arr()
        ?.mapNotNull { block -> block.obj()?.takeIf { it["type"].str() == "text" }?.get("text").str() }
        ?.joinToString("")
        ?: ""
    else -> json.obj()?.get("choices").arr()?.firstOrNull().obj()?.get("message").obj()?.get("content").str() ?: ""
}

/**
 * The text a server-sent-events line adds to a streamed answer («data: {…}»),
 * or null when the line carries none (comments, pings, `[DONE]`, other events).
 */
fun parseAiStreamLine(provider: AiProvider, line: String): String? {
    if (!line.startsWith("data:")) return null
    val data = line.substring(5).trim()
    if (data.isEmpty() || data == "[DONE]") return null
    val json = runCatching { Json.parseToJsonElement(data) }.getOrNull() ?: return null
    return when (provider) {
        AiProvider.GEMINI -> geminiText(json).ifEmpty { null }
        AiProvider.ANTHROPIC -> json.obj()?.takeIf { it["type"].str() == "content_block_delta" }
            ?.get("delta").obj()?.get("text").str()
        else -> json.obj()?.get("choices").arr()?.firstOrNull().obj()?.get("delta").obj()?.get("content").str()
            ?.ifEmpty { null }
    }
}

/** Enough to make a call: a key (a custom server may not need one), a server and a model. */
fun isAiConfigured(config: AiConfig): Boolean =
    if (config.provider == AiProvider.CUSTOM) config.baseUrl.isNotBlank() && config.model.isNotBlank()
    else config.apiKey.isNotBlank()

enum class AiErrorKind { INVALID_KEY, QUOTA, NOT_FOUND, UNAVAILABLE, PROVIDER }

private val API_KEY_MENTION = Regex("api[_ -]?key", RegexOption.IGNORE_CASE)

/**
 * What went wrong, for a message the user understands. Gemini answers a wrong
 * key with 400 + `API_KEY_INVALID`, hence the look at the body.
 */
fun aiErrorKind(status: Int, body: String): AiErrorKind = when {
    status == 401 || status == 403 -> AiErrorKind.INVALID_KEY
    status == 400 && API_KEY_MENTION.containsMatchIn(body) -> AiErrorKind.INVALID_KEY
    status == 402 || status == 429 -> AiErrorKind.QUOTA
    status == 404 -> AiErrorKind.NOT_FOUND
    // Overloaded or down for a moment (Gemini often answers 503): worth retrying.
    status == 500 || status == 502 || status == 503 || status == 504 -> AiErrorKind.UNAVAILABLE
    else -> AiErrorKind.PROVIDER
}

private val SPACES = Regex("\\s+")

/**
 * The provider's own error text (`{"error": {"message": …}}` for Gemini, OpenAI,
 * OpenRouter and Anthropic), trimmed, so the user can see what went wrong.
 */
fun aiErrorDetail(body: String): String {
    val json = runCatching { Json.parseToJsonElement(body) }.getOrNull().obj()
    val error = json?.get("error")
    val message = error.str() ?: error.obj()?.get("message").str() ?: json?.get("message").str()
    return (message ?: body).replace(SPACES, " ").trim().take(200)
}

/**
 * How a streamed answer ended: [Done] (the model finished), [Length] (it hit
 * the token limit, mid-sentence) or [Error] (the provider reported a failure
 * inside the stream). A stream that ends without any of these was cut off.
 */
sealed interface AiStreamEnd {
    data object Done : AiStreamEnd
    data object Length : AiStreamEnd
    data class Error(val errorKind: AiErrorKind, val detail: String) : AiStreamEnd
}

private val ANTHROPIC_ERROR_KINDS = mapOf(
    "overloaded_error" to AiErrorKind.UNAVAILABLE,
    "api_error" to AiErrorKind.UNAVAILABLE,
    "rate_limit_error" to AiErrorKind.QUOTA,
    "authentication_error" to AiErrorKind.INVALID_KEY,
    "permission_error" to AiErrorKind.INVALID_KEY,
    "not_found_error" to AiErrorKind.NOT_FOUND,
)

private fun streamError(errorKind: AiErrorKind, detail: String) =
    AiStreamEnd.Error(errorKind, detail.replace(SPACES, " ").trim().take(200))

/**
 * Whether a server-sent-events line ends the streamed answer, and how; null
 * when it does not (text, pings, other events). Read after [parseAiStreamLine]:
 * Gemini's last chunk carries text and its finish reason together.
 */
fun parseAiStreamEnd(provider: AiProvider, line: String): AiStreamEnd? {
    if (!line.startsWith("data:")) return null
    val data = line.substring(5).trim()
    if (data.isEmpty()) return null
    if (data == "[DONE]") return AiStreamEnd.Done
    val json = runCatching { Json.parseToJsonElement(data) }.getOrNull().obj() ?: return null

    if (provider == AiProvider.ANTHROPIC) {
        val type = json["type"].str()
        if (type == "message_stop") return AiStreamEnd.Done
        if (type == "error") {
            val errorType = json["error"].obj()?.get("type").str().orEmpty()
            return streamError(ANTHROPIC_ERROR_KINDS[errorType] ?: AiErrorKind.PROVIDER, aiErrorDetail(data))
        }
        val stop = if (type == "message_delta") json["delta"].obj()?.get("stop_reason").str() else null
        return when (stop) {
            "max_tokens" -> AiStreamEnd.Length
            "refusal" -> streamError(AiErrorKind.PROVIDER, "stop_reason: refusal")
            else -> null
        }
    }

    // Gemini, OpenAI and OpenRouter put a failure in the stream as {"error": {"code": …, "message": …}}.
    val error = json["error"]
    if (error != null && error !is JsonNull) {
        val code = (error.obj()?.get("code") as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        return streamError(if (code != null) aiErrorKind(code, data) else AiErrorKind.PROVIDER, aiErrorDetail(data))
    }

    if (provider == AiProvider.GEMINI) {
        json["promptFeedback"].obj()?.get("blockReason").str()?.let { return streamError(AiErrorKind.PROVIDER, "blockReason: $it") }
        val reason = json["candidates"].arr()?.firstOrNull().obj()?.get("finishReason").str()
        return when {
            reason.isNullOrEmpty() -> null
            reason == "STOP" -> AiStreamEnd.Done
            reason == "MAX_TOKENS" -> AiStreamEnd.Length
            else -> streamError(AiErrorKind.PROVIDER, "finishReason: $reason")
        }
    }

    val reason = json["choices"].arr()?.firstOrNull().obj()?.get("finish_reason").str()
    return when {
        reason.isNullOrEmpty() -> null
        reason == "length" -> AiStreamEnd.Length
        reason == "content_filter" || reason == "error" -> streamError(AiErrorKind.PROVIDER, "finish_reason: $reason")
        else -> AiStreamEnd.Done
    }
}
