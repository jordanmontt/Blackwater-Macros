package com.blackwatermacros.app.data.ai

import com.blackwatermacros.app.core.AiConfig
import com.blackwatermacros.app.core.AiErrorKind
import com.blackwatermacros.app.core.AiInput
import com.blackwatermacros.app.core.AiMessage
import com.blackwatermacros.app.core.AiRole
import com.blackwatermacros.app.core.aiErrorDetail
import com.blackwatermacros.app.core.aiErrorKind
import com.blackwatermacros.app.core.buildAiRequest
import com.blackwatermacros.app.core.isAiConfigured
import com.blackwatermacros.app.core.parseAiResponse
import com.blackwatermacros.app.core.parseAiStreamLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Web `AiFailure`: what the user is told when a call fails. */
enum class AiFailure {
    NOT_CONFIGURED, INVALID_KEY, QUOTA, NOT_FOUND, OFFLINE, EMPTY, UNREADABLE, PROVIDER,
    /** The on-device model does not take images (D6). */
    NO_VISION,
    /** The provider is overloaded or down for a moment (after retrying). */
    UNAVAILABLE,
    /** The model on this phone failed. */
    LOCAL_MODEL,
}

/** [detail]: the provider's own words (or the engine's), shown small under the message. */
class AiException(val failure: AiFailure, val detail: String = "") : Exception(detail.ifEmpty { failure.name })

private fun AiErrorKind.toFailure() = when (this) {
    AiErrorKind.INVALID_KEY -> AiFailure.INVALID_KEY
    AiErrorKind.QUOTA -> AiFailure.QUOTA
    AiErrorKind.NOT_FOUND -> AiFailure.NOT_FOUND
    AiErrorKind.UNAVAILABLE -> AiFailure.UNAVAILABLE
    AiErrorKind.PROVIDER -> AiFailure.PROVIDER
}

/**
 * The phone calls the chosen provider directly (web `lib/ai/client.ts`): the
 * key and the photos only travel to it. Request/answer formats live in `:core`.
 */
class AiClient(
    private val http: OkHttpClient,
    /** Waits before retrying an overloaded provider (Gemini answers 503 now and then). */
    private val retryDelaysMs: List<Long> = listOf(1_500L, 4_000L),
) {

    private suspend fun send(config: AiConfig, input: AiInput): Response {
        var attempt = 0
        while (true) {
            try {
                return sendOnce(config, input)
            } catch (e: AiException) {
                if (e.failure != AiFailure.UNAVAILABLE || attempt >= retryDelaysMs.size) throw e
                delay(retryDelaysMs[attempt++])
            }
        }
    }

    private fun sendOnce(config: AiConfig, input: AiInput): Response {
        if (!isAiConfigured(config)) throw AiException(AiFailure.NOT_CONFIGURED)
        val request = buildAiRequest(config, input)
        val builder = Request.Builder()
        val built = try {
            builder.url(request.url)
            request.headers.forEach { (name, value) -> if (name != "Content-Type") builder.header(name, value) }
            builder.post(request.body.toRequestBody(JSON)).build()
        } catch (e: IllegalArgumentException) {
            // A custom server address that is not a URL.
            throw AiException(AiFailure.OFFLINE, e.message.orEmpty())
        }
        val response = try {
            http.newCall(built).execute()
        } catch (e: IOException) {
            throw AiException(AiFailure.OFFLINE, e.message.orEmpty())
        }
        if (!response.isSuccessful) {
            val body = response.use { runCatching { it.body?.string() }.getOrNull().orEmpty() }
            throw AiException(aiErrorKind(response.code, body).toFailure(), "${response.code}: ${aiErrorDetail(body)}")
        }
        return response
    }

    /** One answer, whole (meal estimates). */
    suspend fun complete(config: AiConfig, input: AiInput): String = withContext(Dispatchers.IO) {
        val text = send(config, input.copy(stream = false)).use { response ->
            val json = runCatching { Json.parseToJsonElement(response.body!!.string()) }.getOrNull()
            parseAiResponse(config.provider, json)
        }
        if (text.isBlank()) throw AiException(AiFailure.EMPTY)
        text
    }

    /** The answer piece by piece as it is written (Coach). Cancelling the collector closes the call. */
    fun stream(config: AiConfig, input: AiInput): Flow<String> = flow {
        send(config, input.copy(stream = true)).use { response ->
            val source = response.body!!.source()
            try {
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    parseAiStreamLine(config.provider, line)?.let { emit(it) }
                }
            } catch (e: IOException) {
                throw AiException(AiFailure.OFFLINE, e.message.orEmpty())
            }
        }
    }.flowOn(Dispatchers.IO)

    /** «Probar»: a tiny call; a 2xx answer means key, model and server are right. */
    suspend fun test(config: AiConfig) = withContext(Dispatchers.IO) {
        send(
            config,
            AiInput(
                system = "Reply with the word OK.",
                messages = listOf(AiMessage(AiRole.USER, "OK?")),
                json = false,
                stream = false,
                maxTokens = 64,
            ),
        ).close()
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        fun create(): AiClient = AiClient(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                // Photo estimates can take a while; streams stay open while the answer is written.
                .readTimeout(90, TimeUnit.SECONDS)
                .build(),
        )
    }
}
