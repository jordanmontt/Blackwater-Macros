package com.blackwatermacros.app.data.ai.local

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * When the phone's model needs more memory than there is, Android's low-memory
 * killer ends the app: no exception, nothing to catch. So each run leaves a mark
 * while it works; if the next start finds the mark and Android says the app was
 * killed for memory, [outOfMemory] names the model and the app explains it.
 */
class ModelRunGuard(private val context: Context) {

    private val prefs = context.getSharedPreferences("local_engine", Context.MODE_PRIVATE)
    private val _outOfMemory = MutableStateFlow<String?>(null)

    /** The model that ran out of memory last time (its name), until the user closes the notice. */
    val outOfMemory: StateFlow<String?> = _outOfMemory.asStateFlow()

    init {
        checkLastRun()
    }

    /** Written synchronously (`commit`): the process may be killed a moment later. */
    fun started(modelName: String) {
        prefs.edit(commit = true) { putString(KEY_RUNNING, modelName).putLong(KEY_SINCE, System.currentTimeMillis()) }
    }

    fun finished() {
        prefs.edit(commit = true) { remove(KEY_RUNNING).remove(KEY_SINCE) }
    }

    fun dismiss() {
        _outOfMemory.value = null
    }

    private fun checkLastRun() {
        val model = prefs.getString(KEY_RUNNING, null) ?: return
        val since = prefs.getLong(KEY_SINCE, 0)
        finished()
        // Before Android 11 the reason is not available: better say nothing than guess.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val last = runCatching { manager.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() }.getOrNull()
        if (last != null && killedForMemory(since, last.timestamp, last.reason, last.status)) _outOfMemory.value = model
    }

    companion object {
        private const val KEY_RUNNING = "running"
        private const val KEY_SINCE = "since"
    }
}

/**
 * The exit that followed a mark was the low-memory killer: «low memory», or a
 * bare SIGKILL (how it shows on some phones). Swiping the app away, a crash or
 * an update have their own reasons and are not blamed on memory.
 */
@SuppressLint("InlinedApi") // Compile-time constants; the reasons only exist on Android 11+, where it is called.
fun killedForMemory(markedAt: Long, exitAt: Long, reason: Int, status: Int): Boolean =
    exitAt >= markedAt && (
        reason == ApplicationExitInfo.REASON_LOW_MEMORY ||
            (reason == ApplicationExitInfo.REASON_SIGNALED && status == SIGKILL)
        )

private const val SIGKILL = 9
