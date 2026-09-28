package com.blackwatermacros.app.data.ai.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The phone models on offer, as the Blackwater site lists them
 * (`public/models/local-models.json`): edit that file and every phone sees the
 * new models the next time Ajustes → IA is opened, without a new version of
 * the app. Asked only there (not when the app opens), so the app does not
 * contact our server just by being opened. The last good copy is kept on the
 * phone, so it works offline; with no copy yet, the built-in models are used.
 * Only the file is fetched: nothing about the user is sent.
 */
class LocalModelCatalog(
    private val context: Context,
    private val url: String,
    private val appVersionCode: Int,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val cache = File(context.filesDir, CACHE_FILE)
    private val mutex = Mutex()
    private var lastFetch = 0L

    /** The copy on the phone, at start (no network). */
    fun loadCached() {
        runCatching { cache.takeIf { it.isFile }?.readText() }.getOrNull()
            ?.let { parseLocalModelCatalog(it, appVersionCode) }
            ?.takeIf { it.isNotEmpty() }
            ?.let(LocalModels::useCatalog)
    }

    /** Called when Ajustes → IA opens; at most every [MIN_INTERVAL_MS]. Failures keep the copy. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (now() - lastFetch < MIN_INTERVAL_MS) return@withLock
            lastFetch = now()
            runCatching {
                http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string().orEmpty()
                    val models = parseLocalModelCatalog(body, appVersionCode)
                    if (models.isEmpty()) return@use
                    val partial = File(context.filesDir, "$CACHE_FILE.part")
                    partial.writeText(body)
                    partial.renameTo(cache)
                    LocalModels.useCatalog(models)
                }
            }.onFailure { Log.i(TAG, "Model catalog not refreshed: ${it.message}") }
        }
    }

    companion object {
        private const val TAG = "LocalModelCatalog"
        const val CACHE_FILE = "local-models.json"
        /** Opening the screen again a minute later does not ask again. */
        const val MIN_INTERVAL_MS = 10 * 60 * 1000L
    }
}

private val ID = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
private val FILE_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}\\.litertlm$")
private val SHA256 = Regex("^[0-9a-f]{64}$")
private const val MAX_BYTES = 20_000_000_000L

private fun JsonObject.string(key: String): String = ((this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: "").trim()

/**
 * `{ "models": [ { id, name, url, fileName, sizeBytes, sha256, recommendedPhoneGb,
 * vision, notes: { en, es, … }, hidden?, minAppVersionCode? } ] }` → the models this
 * version of the app can use. A wrong entry is skipped, not the whole file; the
 * download must come from huggingface.co and is always checked against its SHA-256.
 */
fun parseLocalModelCatalog(text: String, appVersionCode: Int): List<LocalModelSpec> {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return emptyList()
    val entries = root["models"] as? JsonArray ?: return emptyList()
    val seen = mutableSetOf<String>()
    return entries.filterIsInstance<JsonObject>().mapNotNull { entry ->
        val id = entry.string("id")
        val name = entry.string("name")
        val url = entry.string("url")
        val fileName = entry.string("fileName")
        val sizeBytes = (entry["sizeBytes"] as? JsonPrimitive)?.longOrNull ?: 0L
        val sha256 = entry.string("sha256").lowercase()
        val phoneGb = (entry["recommendedPhoneGb"] as? JsonPrimitive)?.intOrNull ?: 0
        val minApp = (entry["minAppVersionCode"] as? JsonPrimitive)?.intOrNull ?: 0
        val valid = ID.matches(id) && name.isNotEmpty() && FILE_NAME.matches(fileName) &&
            url.startsWith("https://huggingface.co/") && url.endsWith("/$fileName") &&
            sizeBytes in 1..MAX_BYTES && SHA256.matches(sha256) && phoneGb in 1..64 &&
            minApp <= appVersionCode && seen.add(id)
        if (!valid) return@mapNotNull null
        LocalModelSpec(
            id = id,
            name = name,
            url = url,
            fileName = fileName,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            recommendedPhoneGb = phoneGb,
            vision = (entry["vision"] as? JsonPrimitive)?.booleanOrNull ?: false,
            notes = (entry["notes"] as? JsonObject)?.mapNotNull { (language, note) ->
                ((note as? JsonPrimitive)?.contentOrNull)?.trim()?.takeIf { it.isNotEmpty() }?.let { language to it }
            }?.toMap().orEmpty(),
            hidden = (entry["hidden"] as? JsonPrimitive)?.booleanOrNull ?: false,
        )
    }
}
