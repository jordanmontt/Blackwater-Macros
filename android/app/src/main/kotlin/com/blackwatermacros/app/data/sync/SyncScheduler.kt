package com.blackwatermacros.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.blackwatermacros.app.AppGraph
import java.util.concurrent.TimeUnit

/** Requests background sync runs (faked in tests). */
interface SyncScheduler {
    /** Calls within [delaySeconds] of each other collapse into one run, debouncing bursts of edits. */
    fun requestSync(delaySeconds: Long = 0)

    fun cancel()
}

/**
 * Schedules sync runs through WorkManager (AndroidX, no Google services): the
 * run waits until there is a network connection and survives the app being
 * closed, so a meal logged offline at the gym uploads on its own later.
 */
class WorkManagerSyncScheduler(private val context: Context) : SyncScheduler {

    override fun requestSync(delaySeconds: Long) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "sync"
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (AppGraph.sync.sync()) {
        SyncOutcome.Success, SyncOutcome.NotLoggedIn, SyncOutcome.SessionExpired -> Result.success()
        SyncOutcome.Offline, is SyncOutcome.Failed -> Result.retry()
    }
}
