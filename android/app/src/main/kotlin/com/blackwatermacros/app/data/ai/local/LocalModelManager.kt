package com.blackwatermacros.app.data.ai.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.edit
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.blackwatermacros.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

sealed interface LocalModelState {
    /** No model can run on this phone (not 64-bit ARM). */
    data object Unsupported : LocalModelState
    data object NotDownloaded : LocalModelState
    /** Waiting for Wi-Fi, or downloading ([fraction] 0–1). */
    data class Downloading(val fraction: Float, val waitingForWifi: Boolean) : LocalModelState
    data object Ready : LocalModelState
    data class Failed(val checksum: Boolean) : LocalModelState
}

/**
 * Download (WorkManager, foreground, Wi-Fi by default, resumable) and removal
 * of the on-device model. The state combines the file on disk with the work.
 */
class LocalModelManager(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fileChanged = MutableStateFlow(0)
    private val prefs = context.getSharedPreferences("local_model", Context.MODE_PRIVATE)

    /** The model on the phone, else the one picked for download (default: Gemma 4 E2B). */
    private val _selected = MutableStateFlow(
        LocalModels.installed(context) ?: LocalModels.byId(prefs.getString(KEY_SELECTED, null)) ?: LocalModels.DEFAULT,
    )
    val selected: StateFlow<LocalModelSpec> = _selected

    fun support(model: LocalModelSpec): DeviceSupport = deviceSupport(context, model)

    val phoneRamBytes: Long get() = totalRamBytes(context)

    private val anyModelRuns: Boolean by lazy { LocalModels.ALL.any { support(it) != DeviceSupport.UNSUPPORTED_ABI } }

    val state: StateFlow<LocalModelState> =
        combine(workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME), fileChanged, _selected) { infos, _, model ->
            resolve(infos.firstOrNull(), model)
        }.stateIn(scope, SharingStarted.Eagerly, resolve(null, _selected.value))

    /** Choose which model to download; only while none is on the phone or downloading. */
    fun select(model: LocalModelSpec) {
        if (LocalModels.installed(context) != null || state.value is LocalModelState.Downloading) return
        prefs.edit { putString(KEY_SELECTED, model.id) }
        _selected.value = model
    }

    private fun resolve(info: WorkInfo?, model: LocalModelSpec): LocalModelState {
        if (!anyModelRuns) return LocalModelState.Unsupported
        if (LocalModels.isDownloaded(context, model)) return LocalModelState.Ready
        return when (info?.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> LocalModelState.Downloading(partialFraction(model), waitingForWifi = true)
            WorkInfo.State.RUNNING -> LocalModelState.Downloading(
                info.progress.getFloat(KEY_FRACTION, partialFraction(model)),
                waitingForWifi = false,
            )
            WorkInfo.State.FAILED -> LocalModelState.Failed(info.outputData.getBoolean(KEY_CHECKSUM, false))
            else -> LocalModelState.NotDownloaded
        }
    }

    private fun partialFraction(model: LocalModelSpec): Float =
        LocalModels.partialFile(context, model).length().toFloat() / model.sizeBytes

    /** [anyNetwork]: also on mobile data (the user said so after the warning). */
    fun download(anyNetwork: Boolean) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(KEY_MODEL to _selected.value.id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (anyNetwork) NetworkType.CONNECTED else NetworkType.UNMETERED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Cancel keeps the partial file so a new download continues from there. */
    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    /** Frees the space: the model, any partial download and the engine in memory. */
    fun delete(onDeleted: suspend () -> Unit = {}) {
        workManager.cancelUniqueWork(WORK_NAME)
        scope.launch {
            onDeleted()
            LocalModels.deleteAllExcept(context, keep = null)
            workManager.pruneWork()
            fileChanged.value++
        }
    }

    companion object {
        const val WORK_NAME = "local-model-download"
        const val KEY_FRACTION = "fraction"
        const val KEY_CHECKSUM = "checksum"
        const val KEY_MODEL = "model"
        private const val KEY_SELECTED = "selected"
        const val CHANNEL_ID = "model-download"
    }
}

/** Downloads the model in a foreground job so it survives leaving the app. */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val model = LocalModels.byId(inputData.getString(LocalModelManager.KEY_MODEL)) ?: LocalModels.DEFAULT

    override suspend fun doWork(): Result {
        runCatching { setForeground(foregroundInfo(0f)) }
        // One model at a time: a new download replaces any other (and its partial file).
        LocalModels.deleteAllExcept(applicationContext, keep = model)
        val downloader = ModelDownloader(
            OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build(),
        )
        return try {
            downloader.download(
                url = model.url,
                target = LocalModels.file(applicationContext, model),
                partial = LocalModels.partialFile(applicationContext, model),
                expectedSize = model.sizeBytes,
                expectedSha256 = model.sha256,
            ) { downloaded, total ->
                val fraction = downloaded.toFloat() / total
                setProgressAsync(workDataOf(LocalModelManager.KEY_FRACTION to fraction))
                runCatching { setForegroundAsync(foregroundInfo(fraction)) }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ChecksumException) {
            Result.failure(workDataOf(LocalModelManager.KEY_CHECKSUM to true))
        } catch (e: Exception) {
            // Network hiccups: WorkManager tries again later and the download resumes.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private fun foregroundInfo(fraction: Float): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    LocalModelManager.CHANNEL_ID,
                    applicationContext.getString(R.string.local_model_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, LocalModelManager.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.local_model_downloading, model.name))
            .setProgress(100, (fraction * 100).toInt(), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 4201
        const val MAX_ATTEMPTS = 8
    }
}
