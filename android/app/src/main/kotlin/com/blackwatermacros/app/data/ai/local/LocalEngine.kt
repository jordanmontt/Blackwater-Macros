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
import kotlinx.coroutines.Dispatchers
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
class LocalEngine(private val context: Context) {

    private val mutex = Mutex()
    private var engine: Engine? = null
    private var visionCache: Boolean? = null
    /** Set once the GPU failed on this phone (some fail only when the first message runs). */
    private var cpuOnly = false

    /** D6: whether this model file takes images (asked to the model itself, then remembered). */
    suspend fun supportsImages(): Boolean = withContext(Dispatchers.IO) {
        val model = LocalModels.installed(context) ?: return@withContext false
        if (!model.vision) return@withContext false
        visionCache ?: runCatching {
            Capabilities(LocalModels.file(context, model).absolutePath).use { it.inputModalities().vision }
        }.onFailure { Log.w(TAG, "Could not read the model capabilities", it) }
            .getOrDefault(false)
            .also { visionCache = it }
    }

    private fun loaded(): Engine {
        engine?.let { return it }
        val installed = LocalModels.installed(context) ?: throw AiException(AiFailure.NOT_CONFIGURED, "model not downloaded")
        val model = LocalModels.file(context, installed)
        // GPU is much faster where it works; not every phone (or emulator) has a usable one.
        val backends = if (cpuOnly) listOf(Backend.CPU()) else listOf(Backend.GPU(), Backend.CPU())
        val created = backends.firstNotNullOfOrNull { backend ->
            runCatching {
                Engine(
                    EngineConfig(
                        modelPath = model.absolutePath,
                        backend = backend,
                        visionBackend = backend,
                        maxNumTokens = MAX_TOKENS,
                        maxNumImages = MAX_IMAGES,
                        cacheDir = context.cacheDir.absolutePath,
                    ),
                ).also { it.initialize() }
            }.onFailure {
                Log.w(TAG, "Backend $backend failed", it)
                if (backend is Backend.GPU) cpuOnly = true
            }.getOrNull()
        } ?: throw AiException(AiFailure.LOCAL_MODEL, "the model could not be loaded")
        engine = created
        return created
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

    /**
     * Runs [block] on the loaded engine; if it fails on the GPU before producing
     * anything, the engine is reloaded on the CPU and [block] runs once more.
     */
    private suspend fun <T> withEngine(block: suspend (Engine, () -> Boolean) -> T): T {
        var produced = false
        val markProduced = { produced = true; true }
        return try {
            block(loaded(), markProduced)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Request failed (cpuOnly=$cpuOnly)", e)
            if (cpuOnly || produced) throw AiException(AiFailure.LOCAL_MODEL, e.message.orEmpty().lineSequence().first().take(200))
            cpuOnly = true
            engine?.close()
            engine = null
            try {
                block(loaded(), markProduced)
            } catch (retry: kotlinx.coroutines.CancellationException) {
                throw retry
            } catch (retry: AiException) {
                throw retry
            } catch (retry: Exception) {
                Log.w(TAG, "Request failed on the CPU too", retry)
                throw AiException(AiFailure.LOCAL_MODEL, retry.message.orEmpty().lineSequence().first().take(200))
            }
        }
    }

    /** The coach: the answer as it is written. [messages] ends with the user's question. */
    fun stream(system: String, messages: List<AiMessage>): Flow<String> = flow {
        mutex.withLock {
            withEngine { engine, markProduced ->
                engine.createConversation(config(system, messages.dropLast(1), json = false)).use { conversation ->
                    conversation.sendMessageAsync(contents(messages.last().text, messages.last().images)).collect { piece ->
                        val text = piece.text()
                        if (text.isNotEmpty()) {
                            markProduced()
                            emit(text)
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** A whole answer, as JSON following [jsonSchema] when given (meal estimates). */
    suspend fun complete(system: String, text: String, images: List<AiImage>, jsonSchema: String?): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                withEngine { engine, _ ->
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

    /** Frees the memory (and lets the model file be deleted). */
    suspend fun release() = mutex.withLock {
        engine?.close()
        engine = null
        visionCache = null
    }

    companion object {
        private const val TAG = "LocalEngine"
        /** Prompt + answer: the coach summary is ~1–2k tokens. */
        const val MAX_TOKENS = 4096
        const val MAX_IMAGES = 5

        /** The meal estimate as a JSON schema, for constrained decoding (same fields as `MEAL_ESTIMATE_SHAPE`). */
        const val MEAL_ESTIMATE_SCHEMA = """{"type":"object","properties":{"title":{"type":"string"},"items":{"type":"array","items":{"type":"object","properties":{"name":{"type":"string"},"grams":{"type":"number"},"calories":{"type":"number"},"protein":{"type":"number"},"carbs":{"type":"number"},"fat":{"type":"number"}},"required":["name","grams","calories","protein","carbs","fat"]}},"confidence":{"type":"string","enum":["low","medium","high"]},"notes":{"type":"string"}},"required":["title","items","confidence","notes"]}"""
    }
}
