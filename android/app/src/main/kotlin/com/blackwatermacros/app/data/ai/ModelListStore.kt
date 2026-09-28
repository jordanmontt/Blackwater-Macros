package com.blackwatermacros.app.data.ai

import android.content.SharedPreferences
import androidx.core.content.edit
import com.blackwatermacros.app.core.AiConfig
import com.blackwatermacros.app.core.AiModelOption
import com.blackwatermacros.app.core.AiProvider
import com.blackwatermacros.app.core.isAiConfigured
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StoredModel(val id: String, val label: String)

@Serializable
data class ModelList(
    val models: List<StoredModel>,
    val fetchedAtMillis: Long,
    /** Which key/server the list is for (a hash, never the key itself). */
    val fingerprint: String,
) {
    val options: List<AiModelOption> get() = models.map { AiModelOption(it.id, it.label) }
}

/** FNV-1a of provider, key and server: enough to notice they changed (web `configFingerprint`). */
fun configFingerprint(config: AiConfig): String {
    var hash = 0x811c9dc5L
    for (char in "${config.provider.id}|${config.apiKey.trim()}|${config.baseUrl.trim()}") {
        hash = hash xor char.code.toLong()
        hash = (hash * 0x01000193L) and 0xffffffffL
    }
    return hash.toString(16)
}

/**
 * The models the user's key can use (web `lib/ai/model-list.ts`), kept on the
 * phone so the dropdown in Ajustes → IA works offline. Refreshed when the app
 * opens (at most once a day), when the key changes and with «Actualizar».
 */
class ModelListStore(
    private val prefs: SharedPreferences,
    private val fetch: suspend (AiConfig) -> List<AiModelOption>,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val _lists = MutableStateFlow(read())
    val lists: StateFlow<Map<AiProvider, ModelList>> = _lists.asStateFlow()

    /** The cached list for this key, or null (none yet, or it was for another key). */
    fun cached(config: AiConfig): ModelList? = _lists.value[config.provider]?.takeIf { it.fingerprint == configFingerprint(config) }

    /** Asks the provider again when [force]d, when there is no list for this key, or when it is a day old. */
    suspend fun refresh(config: AiConfig, force: Boolean = false): ModelList? = mutex.withLock {
        if (!isAiConfigured(config)) return null
        val cached = cached(config)
        if (!force && cached != null && now() - cached.fetchedAtMillis < MAX_AGE_MS) return cached
        val list = ModelList(fetch(config).map { StoredModel(it.id, it.label) }, now(), configFingerprint(config))
        val next = _lists.value + (config.provider to list)
        prefs.edit { putString(KEY + config.provider.id, json.encodeToString(ModelList.serializer(), list)) }
        _lists.value = next
        list
    }

    private fun read(): Map<AiProvider, ModelList> = AiProvider.entries.mapNotNull { provider ->
        prefs.getString(KEY + provider.id, null)
            ?.let { runCatching { json.decodeFromString(ModelList.serializer(), it) }.getOrNull() }
            ?.let { provider to it }
    }.toMap()

    companion object {
        const val PREFS_NAME = "ai_model_lists"
        const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
        private const val KEY = "list."
    }
}
