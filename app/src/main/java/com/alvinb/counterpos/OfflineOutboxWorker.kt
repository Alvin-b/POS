package com.alvinb.counterpos

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.concurrent.TimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * WorkManager-based background sync worker.
 * Automatically pushes local Room database changes (sales, stock, tables) to Supabase
 * as soon as an internet connection is detected, guaranteeing data integrity during offline operations.
 */
class OfflineOutboxWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            // 1. Rehydrate session and tenant context in case worker runs in a clean process
            counterPosApplication.repository.restoreSession()

            // 2. Push all locally queued Room changes (outbox) to Supabase
            counterPosApplication.repository.syncPendingOutbox(refreshSnapshot = false)

            // 3. Perform two-way reconciliation with Supabase when online
            counterPosApplication.repository.synchronizeWhenOnline()

            Result.success()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (exception.isTransientSyncFailure()) Result.retry() else Result.failure()
        }
    }

    private val counterPosApplication: CounterPosApplication
        get() = applicationContext as CounterPosApplication

    companion object {
        const val WORK_NAME = "counter_pos_offline_outbox_sync"
        const val PERIODIC_WORK_NAME = "counter_pos_periodic_sync"
        private const val INITIAL_BACKOFF_SECONDS = 30L

        /**
         * Enqueues an immediate one-time sync task constrained to run when an internet
         * connection is detected.
         */
        fun enqueue(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val request = OneTimeWorkRequestBuilder<OfflineOutboxWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        INITIAL_BACKOFF_SECONDS,
                        TimeUnit.SECONDS
                    )
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            } catch (_: Exception) {
                // WorkManager may be uninitialized in unit test runners.
            }
        }

        /**
         * Enqueues a periodic background sync every 15 minutes that only executes
         * when the device has an active internet connection.
         */
        fun enqueuePeriodic(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val periodicRequest = PeriodicWorkRequestBuilder<OfflineOutboxWorker>(
                    15, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        INITIAL_BACKOFF_SECONDS,
                        TimeUnit.SECONDS
                    )
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicRequest
                )
            } catch (_: Exception) {
                // WorkManager may be uninitialized in unit test runners.
            }
        }

        private fun Exception.isTransientSyncFailure(): Boolean {
            var current: Throwable? = this
            while (current != null) {
                if (current is IOException || current is TimeoutException) return true
                current = current.cause
            }
            return false
        }
    }
}
