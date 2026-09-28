package com.blackwatermacros.app.data.ai.local

import android.content.Context
import android.util.Base64
import android.util.Log
import com.blackwatermacros.app.core.AiImage
import com.blackwatermacros.app.core.AiMessage
import com.blackwatermacros.app.core.AiRole
import com.blackwatermacros.app.data.ai.AiException
import com.blackwatermacros.app.data.ai.AiFailure
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Capabilities
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs the downloaded model with LiteRT-LM, fully on the phone. The engine is
 * loaded on first use (seconds) and kept while the app lives; one request at
 * a time. Same shapes as the cloud client: a streamed chat and a whole answer.
 */
class LocalEngine(private val context: Context, private val guard: ModelRunGuard? = null) {

    private val mutex = Mutex()
    private var engine: Engine? = null
    /** Whether [engine] has the image encoder loaded (only when a request brings photos). */
    private var engineHasVision = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Frees the model [IDLE_RELEASE_MS] after the last request (see [releaseWhenIdle]). */
    private var idleRelease: Job? = null
    private var visionCache: Boolean? = null

    /** Whether this model file takes images (asked to the model itself, then remembered). */
    suspend fun supportsImages(): Boolean = withContext(Dispatchers.IO) {
        val model = LocalModels.installed(context) ?: return@withContext false
        if (!model.vision) return@withContext false
        visionCache ?: runCatching {
            Capabilities(LocalModels.file(context, model).absolutePath).use { it.inputModalities().vision }
        }.onFailure { Log.w(TAG, "Could not read the model capabilities", it) }
            .getOrDefault(false)
            .also { visionCache = it }
    }

    /**
     * The engine, loaded on first use, on the CPU. The GPU was tried first until
     * a Pixel 10a (8 GB, Mali GPU) showed the cost: a Mali GPU keeps a second copy
     * of the weights, so Gemma 4 E4B reached ~5.3 GB and Android killed the app;
     * on the CPU the weights are read from a file (the XNNPack cache in cacheDir)
     * and it peaked at ~4.2 GB and answered. Slower, but it fits. The image
     * encoder is loaded only when a request brings photos.
     */
    private fun loaded(vision: Boolean): Engine {
        engine?.let { current ->
            if (engineHasVision || !vision) return current
            current.close()
            engine = null
        }
        val installed = LocalModels.installed(context) ?: throw AiException(AiFailure.NOT_CONFIGURED, "model not downloaded")
        // Caches from GPU runs (before it was CPU only) are dead weight: only the XNNPack copy is used.
        context.cacheDir.listFiles()?.filter { it.name.startsWith(installed.fileName) && !it.name.endsWith(".xnnpack_cache") }
            ?.forEach { it.delete() }
        val candidate = Engine(
            EngineConfig(
                modelPath = LocalModels.file(context, installed).absolutePath,
                backend = Backend.CPU(),
                visionBackend = if (vision) Backend.CPU() else null,
                maxNumTokens = MAX_TOKENS,
                maxNumImages = if (vision) MAX_IMAGES else null,
                cacheDir = context.cacheDir.absolutePath,
            ),
        )
        try {
            candidate.initialize()
        } catch (e: Exception) {
            runCatching { candidate.close() }
            Log.w(TAG, "The model could not be loaded", e)
            throw AiException(AiFailure.LOCAL_MODEL, e.message.orEmpty().lineSequence().first().take(200))
        }
        engine = candidate
        engineHasVision = vision
        return candidate
    }

    private fun config(system: String, history: List<AiMessage>, json: Boolean) = ConversationConfig(
        systemInstruction = Contents.of(system),
        initialMessages = history.map { if (it.role == AiRole.USER) Message.user(it.text) else Message.model(it.text) },
        thinkingConfig = ThinkingConfig(false),
        enableResponseFormat = json,
    )

    private fun contents(text: String, images: List<AiImage>): Contents = Contents.of(
        images.map { Content.ImageBytes(Base64.decode(it.data, Base64.NO_WRAP)) } + Content.Text(text),
    )

    private fun Message.text(): String = contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

    /** Runs [block] on the loaded engine, marked for [ModelRunGuard] while it works. */
    private suspend fun <T> withEngine(vision: Boolean, block: suspend (Engine) -> T): T {
        idleRelease?.cancel()
        guard?.started(LocalModels.installed(context)?.name.orEmpty())
        try {
            return block(loaded(vision))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Request failed", e)
            throw AiException(AiFailure.LOCAL_MODEL, e.message.orEmpty().lineSequence().first().take(200))
        } finally {
            guard?.finished()
            idleRelease = scope.launch {
                delay(IDLE_RELEASE_MS)
                release()
            }
        }
    }

    /** The coach: the answer as it is written. [messages] ends with the user's question. */
    fun stream(system: String, messages: List<AiMessage>): Flow<String> = flow {
        mutex.withLock {
            withEngine(vision = messages.last().images.isNotEmpty()) { engine ->
                engine.createConversation(config(system, messages.dropLast(1), json = false)).use { conversation ->
                    conversation.sendMessageAsync(contents(messages.last().text, messages.last().images)).collect { piece ->
                        val text = piece.text()
                        if (text.isNotEmpty()) emit(text)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** A whole answer, as JSON following [jsonSchema] when given (meal estimates). */
    suspend fun complete(system: String, text: String, images: List<AiImage>, jsonSchema: String?): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                withEngine(vision = images.isNotEmpty()) { engine ->
                    engine.createConversation(config(system, emptyList(), json = jsonSchema != null)).use { conversation ->
                        val answer = StringBuilder()
                        conversation.sendMessageAsync(
                            contents(text, images),
                            responseFormat = jsonSchema?.let { ResponseFormat.json(it) },
                        ).collect { answer.append(it.text()) }
                        answer.toString()
                    }
                }
            }
        }

    /**
     * Frees the model's memory as soon as no request is running (at once when idle):
     * «Nueva conversación» and the app going to the background (the camera opening,
     * too) call it. A loaded model holds ~4 GB; left loaded in the background, Android
     * killed the app to make room for the camera on an 8 GB phone.
     */
    fun releaseWhenIdle() {
        idleRelease?.cancel()
        idleRelease = scope.launch { release() }
    }

    /** Frees the memory (and lets the model file be deleted). */
    suspend fun release() = mutex.withLock {
        engine?.close()
        engine = null
        engineHasVision = false
        visionCache = null
    }

    companion object {
        private const val TAG = "LocalEngine"
        /** Prompt + answer: the coach summary is ~1–2k tokens. */
        const val MAX_TOKENS = 4096
        const val MAX_IMAGES = 5
        /** Long enough for a follow-up question; reloading takes ~20 s on the CPU. */
        const val IDLE_RELEASE_MS = 60_000L

        /** The meal estimate as a JSON schema, for constrained decoding (same fields as `MEAL_ESTIMATE_SHAPE`). */
        const val MEAL_ESTIMATE_SCHEMA = """{"type":"object","properties":{"title":{"type":"string"},"items":{"type":"array","items":{"type":"object","properties":{"name":{"type":"string"},"grams":{"type":"number"},"calories":{"type":"number"},"protein":{"type":"number"},"carbs":{"type":"number"},"fat":{"type":"number"}},"required":["name","grams","calories","protein","carbs","fat"]}},"confidence":{"type":"string","enum":["low","medium","high"]},"notes":{"type":"string"}},"required":["title","items","confidence","notes"]}"""
    }
}
