package com.blackwatermacros.app.data.update

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * «A new version is available» for the APK from the website (NewPipe's way):
 * at most once a day, when the app opens, it reads the latest GitHub release and
 * the Today screen offers the website. Ajustes can also ask right away
 * ([checkNow]), with or without the daily check. Only builds signed with the project's own
 * key ([ownBuild]) ever check: F-Droid signs its builds with its own key and
 * updates them itself, so those never contact GitHub and show no setting.
 * Only the release's version is fetched: nothing about the user is sent.
 */
class UpdateChecker(
    private val prefs: SharedPreferences,
    private val currentVersion: String,
    val ownBuild: Boolean,
    private val fetchLatestVersion: suspend () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    private val _available = MutableStateFlow(readAvailable())
    private val _newer = MutableStateFlow(readNewer())
    private val _check = MutableStateFlow(CheckState.IDLE)
    private val _lastChecked = MutableStateFlow(prefs.getLong(KEY_LAST_SUCCESS, 0L).takeIf { it > 0L })

    /** Ajustes → «Check for updates» (on by default). */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** A newer version the user has not dismissed, or null: the Today notice. */
    val available: StateFlow<String?> = _available.asStateFlow()

    /** The newest version known to be newer than this one, dismissed or not: Ajustes' row. */
    val newer: StateFlow<String?> = _newer.asStateFlow()

    /** What «Check now» is doing or found. */
    val check: StateFlow<CheckState> = _check.asStateFlow()

    /** When GitHub last answered (epoch ms), or null. */
    val lastChecked: StateFlow<Long?> = _lastChecked.asStateFlow()

    /** The installed version. */
    val installed: String get() = currentVersion

    fun setEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, value) }
        _enabled.value = value
        _available.value = readAvailable()
    }

    /** «Not now»: this version is not offered again (a later one is). */
    fun dismiss() {
        val version = _available.value ?: return
        prefs.edit { putString(KEY_DISMISSED, version) }
        _available.value = null
    }

    /** On app open; asks GitHub at most every [MIN_INTERVAL_MS]. Failures stay silent. */
    suspend fun checkIfDue() {
        if (!ownBuild || !_enabled.value) return
        mutex.withLock {
            if (now() - prefs.getLong(KEY_LAST_CHECK, 0L) < MIN_INTERVAL_MS) return
            prefs.edit { putLong(KEY_LAST_CHECK, now()) }
            val latest = fetch() ?: return
            remember(latest)
        }
    }

    /**
     * «Check now» in Ajustes: asks GitHub right away, even with the daily check
     * off. The user asked, so a newer version comes back to the Today notice
     * even after «Not now».
     */
    suspend fun checkNow() {
        if (!ownBuild) return
        _check.value = CheckState.CHECKING
        try {
            mutex.withLock {
                prefs.edit { putLong(KEY_LAST_CHECK, now()) }
                val latest = fetch()
                if (latest == null) {
                    _check.value = CheckState.FAILED
                    return
                }
                prefs.edit { remove(KEY_DISMISSED) }
                remember(latest)
                _check.value = if (isNewerVersion(latest, currentVersion)) CheckState.IDLE else CheckState.UP_TO_DATE
            }
        } finally {
            // Cancelled half-way (the screen closed): never stay «Checking…».
            if (_check.value == CheckState.CHECKING) _check.value = CheckState.IDLE
        }
    }

    private suspend fun fetch(): String? = runCatching { fetchLatestVersion() }
        .onFailure { Log.i(TAG, "Update check failed: ${it.message}") }
        .getOrNull()

    private fun remember(latest: String) {
        val at = now()
        prefs.edit {
            putString(KEY_LATEST, latest)
            putLong(KEY_LAST_SUCCESS, at)
        }
        _lastChecked.value = at
        _available.value = readAvailable()
        _newer.value = readNewer()
    }

    private fun readAvailable(): String? {
        if (!prefs.getBoolean(KEY_ENABLED, true)) return null
        return readNewer()?.takeIf { it != prefs.getString(KEY_DISMISSED, null) }
    }

    private fun readNewer(): String? {
        if (!ownBuild) return null
        return prefs.getString(KEY_LATEST, null)?.takeIf { isNewerVersion(it, currentVersion) }
    }

    companion object {
        const val PREFS_NAME = "updates"
        private const val TAG = "UpdateChecker"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_CHECK = "lastCheck"
        private const val KEY_LATEST = "latest"
        private const val KEY_DISMISSED = "dismissed"
        private const val KEY_LAST_SUCCESS = "lastSuccess"
        const val MIN_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}

/** «Check now»: idle (or a newer version, shown from [UpdateChecker.newer]), asking, up to date, or failed. */
enum class CheckState { IDLE, CHECKING, UP_TO_DATE, FAILED }

/** `v1.2.3` or `1.2.3` → [1, 2, 3]; null for anything else. */
private fun versionParts(version: String): List<Int>? =
    Regex("""^v?(\d+)\.(\d+)\.(\d+)$""").matchEntire(version.trim())?.groupValues?.drop(1)?.map { it.toInt() }

/** [latest] is a higher version than [current] (1.0.10 > 1.0.9); false if either is malformed. */
fun isNewerVersion(latest: String, current: String): Boolean {
    val a = versionParts(latest) ?: return false
    val b = versionParts(current) ?: return false
    return a.zip(b).firstOrNull { (x, y) -> x != y }?.let { (x, y) -> x > y } ?: false
}

/** The version of the latest GitHub release (`tag_name`, without the `v`), or null. */
fun githubLatestVersion(
    url: String,
    http: OkHttpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build(),
): suspend () -> String? = {
    withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            parseLatestRelease(response.body?.string().orEmpty())
        }
    }
}

/** `{"tag_name": "v1.2.3", …}` → "1.2.3"; null when it is not a version. */
fun parseLatestRelease(body: String): String? {
    val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
    val tag = (root["tag_name"] as? JsonPrimitive)?.contentOrNull ?: return null
    return versionParts(tag)?.joinToString(".")
}

/** This APK is signed with the certificate whose SHA-256 is [expectedSha256] (hex, any case and colons). */
fun signedWith(context: Context, expectedSha256: String): Boolean {
    val expected = expectedSha256.replace(":", "").lowercase()
    if (expected.length != 64) return false
    return runCatching {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty()
        }
        signatures.any { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } == expected
        }
    }.getOrDefault(false)
}
