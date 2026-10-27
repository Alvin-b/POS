package com.alvinb.counterpos

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.concurrent.TimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Flushes locally queued changes whenever a connected network is available. */
class OfflineOutboxWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            // WorkManager may recreate the application process after the user
            // queued a stock change. Rehydrate the saved session and active
            // business first; otherwise syncPendingOutbox() has no tenant and
            // silently returns without submitting the queued mutation.
            counterPosApplication.repository.restoreSession()
            counterPosApplication.repository.syncPendingOutbox()
            Result.success()
        } catch (exception: CancellationException) {
            // WorkManager uses cancellation to stop work; it must not be converted
            // into a successful or failed sync result.
            throw exception
        } catch (exception: Exception) {
            if (exception.isTransientSyncFailure()) Result.retry() else Result.failure()
        }
    }

    private val counterPosApplication: CounterPosApplication
        get() = applicationContext as CounterPosApplication

    companion object {
        const val WORK_NAME = "counter_pos_offline_outbox_sync"
        private const val INITIAL_BACKOFF_SECONDS = 30L

        /** Enqueue at most one pending outbox sync for the application process. */
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
                    ExistingWorkPolicy.KEEP,
                    request
                )
            } catch (_: Exception) {
                // WorkManager is uninitialized in test runners without a custom configuration.
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
