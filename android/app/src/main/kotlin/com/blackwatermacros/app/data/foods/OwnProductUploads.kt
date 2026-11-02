package com.blackwatermacros.app.data.foods

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

/**
 * Sends the products added from a label through WorkManager: the run waits
 * for a connection and survives the app being closed, so a product added in a
 * shop without signal reaches Open Food Facts later on its own.
 */
fun scheduleOwnProductUploads(context: Context) {
    val request = OneTimeWorkRequestBuilder<OwnProductUploadWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
        .build()
    // Appended: a product saved while a run is sending gets its own run after it.
    WorkManager.getInstance(context).enqueueUniqueWork("own-products", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
}

class OwnProductUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (AppGraph.ownProducts.sendPending()) Result.success() else Result.retry()
}
