package com.v2ray.ang.tonic

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.util.LogUtil
import java.util.concurrent.TimeUnit

/**
 * Periodically refreshes the *subscription list* from the TONIC backend (in case
 * the user's plan/servers changed there) and re-imports configs.
 *
 * Note: refreshing the configs *inside* each subscription is already handled by
 * the built-in [com.v2ray.ang.handler.SubscriptionUpdater]; this worker handles
 * the extra step of asking the backend which subscriptions the user should have.
 */
object TonicSync {
    private const val WORK_NAME = "tonic_backend_sync"

    fun schedule(context: Context = AngApplication.application) {
        val intervalHours = BuildConfig.SUB_UPDATE_HOURS
        val request = PeriodicWorkRequestBuilder<Worker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(WORK_NAME)
            .build()

        RemoteWorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context = AngApplication.application) {
        RemoteWorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (!TonicStore.isLoggedIn()) return Result.success()
            LogUtil.i(AppConfig.TAG, "TonicSync: periodic backend sync starting")
            val ok = TonicManager.syncFromBackend()
            return if (ok) Result.success() else Result.retry()
        }
    }
}
