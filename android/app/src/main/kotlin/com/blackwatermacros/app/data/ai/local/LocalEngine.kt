package com.blackwatermacros.app.data.ai.local

import android.content.Context
import android.util.Base64
import android.util.Log
import com.blackwatermacros.app.core.AiImage
import com.blackwatermacros.app.core.AiMessage
import com.blackwatermacros.app.core.AiRole
import com.blackwatermacros.app.data.ai.AiException
import com.blackwatermacros.app.data.ai.flowOnKeepingItems
import com.blackwatermacros.app.data.ai.AiFailure
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Capabilities
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs the downloaded model with LiteRT-LM, fully on the phone. The engine is
 * loaded on first use, or ahead of it with [prepare] (~20 s on the CPU), and
 * freed when idle; one request at a time. Same shapes as the cloud client: a
 * streamed chat and a whole answer.
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
    /** While the Coach tab is on screen the model is not freed when idle (see [hold]). */
    @Volatile private var held = false

    /**
     * The coach's conversation, kept between questions: a follow-up then reads only
     * the new question, instead of the system prompt and every earlier turn again.
     */
    private var chat: Conversation? = null
    private var chatSystem: String? = null
    /** The turns [chat] holds after its system prompt, as text. */
    private var chatTurns: List<ChatTurn> = emptyList()
    /** Questions asked so far: a [prepare] overtaken by a question is dropped. */
    private val questions = AtomicInteger()

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
            forgetChat()
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

    private fun config(system: String, history: List<AiMessage>) = ConversationConfig(
        systemInstruction = Contents.of(system),
        initialMessages = history.map { if (it.role == AiRole.USER) Message.user(it.text) else Message.model(it.text) },
        thinkingConfig = ThinkingConfig(false),
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
            if (!held) releaseLater()
        }
    }

    private fun releaseLater() {
        idleRelease?.cancel()
        idleRelease = scope.launch {
            delay(IDLE_RELEASE_MS)
            release()
        }
    }

    /**
     * The conversation for a question with this [system] prompt and [history]: the kept
     * one when it holds exactly that and still has room for [newTokens] (the question
     * and its answer), else a new one.
     */
    private fun chatFor(engine: Engine, system: String, history: List<AiMessage>, newTokens: Int): Conversation {
        val kept = chat
        val tokens = kept?.let { runCatching { if (it.isAlive) it.getTokenCount() else null }.getOrNull() }
        if (kept != null && canContinueChat(chatSystem, chatTurns, tokens, system, history, newTokens, MAX_TOKENS)) return kept
        forgetChat()
        return engine.createConversation(config(system, history)).also {
            chat = it
            chatSystem = system
            chatTurns = history.map(::ChatTurn)
        }
    }

    private fun forgetChat() {
        runCatching { chat?.close() }
        chat = null
        chatSystem = null
        chatTurns = emptyList()
    }

    /**
     * Gets the model ready for the coach's next question while the user types (the Coach
     * tab opened, a photo added): loads it and opens the conversation that question will
     * use. Failures stay silent here: the question itself reports them.
     */
    fun prepare(system: String, history: List<AiMessage>, vision: Boolean) {
        val asked = questions.get()
        scope.launch {
            mutex.withLock {
                if (questions.get() != asked) return@withLock
                runCatching { withEngine(vision) { engine -> chatFor(engine, system, history, 0) } }
            }
        }
    }

    /** The Coach tab is on screen ([on]) or not: while it is, the model stays loaded. */
    fun hold(on: Boolean) {
        held = on
        if (on) idleRelease?.cancel() else releaseLater()
    }

    /**
     * The coach: the answer as it is written. [messages] ends with the user's question;
     * [newTokens] is what the question and its answer may take of the window.
     */
    fun stream(system: String, messages: List<AiMessage>, newTokens: Int): Flow<String> {
        questions.incrementAndGet()
        return streamAnswer(system, messages, newTokens)
    }

    private fun streamAnswer(system: String, messages: List<AiMessage>, newTokens: Int): Flow<String> = flow {
        mutex.withLock {
            val question = messages.last()
            withEngine(vision = question.images.isNotEmpty()) { engine ->
                val conversation = chatFor(engine, system, messages.dropLast(1), newTokens)
                val answer = StringBuilder()
                try {
                    conversation.sendMessageAsync(contents(question.text, question.images)).collect { piece ->
                        val text = piece.text()
                        if (text.isNotEmpty()) {
                            answer.append(text)
                            emit(text)
                        }
                    }
                } catch (e: Throwable) {
                    // Stopped or failed half-way: the next question starts a new conversation.
                    forgetChat()
                    throw e
                }
                chatTurns = chatTurns + ChatTurn(AiRole.USER, question.text) + ChatTurn(AiRole.ASSISTANT, answer.toString())
            }
        }
    }.flowOnKeepingItems(Dispatchers.IO)

    /** A whole answer (meal estimates; the prompt asks for JSON, see [MealEstimator]). */
    suspend fun complete(system: String, text: String, images: List<AiImage>): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                withEngine(vision = images.isNotEmpty()) { engine ->
                    // One conversation at a time: the coach's is opened again on its next question.
                    forgetChat()
                    engine.createConversation(config(system, emptyList())).use { conversation ->
                        val answer = StringBuilder()
                        conversation.sendMessageAsync(contents(text, images)).collect { answer.append(it.text()) }
                        answer.toString()
                    }
                }
            }
        }

    /**
     * Frees the model's memory as soon as no request is running (at once when idle):
     * the app going to the background (the camera opening, too) calls it. A loaded
     * model holds ~4 GB; left loaded in the background, Android killed the app to make
     * room for the camera on an 8 GB phone.
     */
    fun releaseWhenIdle() {
        idleRelease?.cancel()
        idleRelease = scope.launch { release() }
    }

    /** Frees the memory (and lets the model file be deleted). */
    suspend fun release() = mutex.withLock {
        forgetChat()
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
        /** After the Coach tab is left or the last request: reloading takes ~20 s on the CPU. */
        const val IDLE_RELEASE_MS = 60_000L
    }
}

/** A turn as the phone's model reads it: earlier turns are text only. */
internal data class ChatTurn(val role: AiRole, val text: String) {
    constructor(message: AiMessage) : this(message.role, message.text)
}

/**
 * Whether the kept conversation ([chatSystem], [chatTurns], [tokenCount] tokens used, null
 * when unknown) can take the next question: same system prompt (the data summary changes
 * when a meal is logged), the same earlier turns, and room left for [newTokens].
 */
internal fun canContinueChat(
    chatSystem: String?,
    chatTurns: List<ChatTurn>,
    tokenCount: Int?,
    system: String,
    history: List<AiMessage>,
    newTokens: Int,
    maxTokens: Int,
): Boolean =
    chatSystem == system && tokenCount != null && tokenCount + newTokens <= maxTokens &&
        chatTurns == history.map(::ChatTurn)
