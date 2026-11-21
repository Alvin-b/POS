package com.alvinb.counterpos

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
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
import androidx.work.workDataOf
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException

/**
 * WorkManager-based background sync worker that drains local Room outbox queues
 * to Supabase when a stable internet connection is present, while keeping local
 * Room UI reads and POS writes responsive.
 */
class OfflineOutboxWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        isWorkerRunning = true
        return try {
            if (!isStableInternetConnected(applicationContext)) {
                return Result.retry()
            }

            // 1. Rehydrate local session & active tenant context directly from Room without blocking UI.
            val snapshot = counterPosApplication.repository.restoreSessionFromCacheOnly()
            if (!snapshot.isAuthenticated || snapshot.activeBusiness == null) {
                return Result.success()
            }

            // 2. Refresh expired tokens if needed, then execute the requested sync profile cleanly.
            val mode = inputData.getString(KEY_SYNC_MODE) ?: MODE_FLUSH_OUTBOX
            counterPosApplication.repository.keepSessionAliveIfStale()
            if (mode == MODE_FULL_SYNC) {
                counterPosApplication.repository.synchronizeWhenOnline(force = false)
            } else {
                counterPosApplication.repository.flushOutboxViaWorker()
            }

            Result.success()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            when {
                exception.isPermanentAuthFailure() -> Result.failure()
                exception.isTransientSyncFailure() -> Result.retry()
                else -> Result.failure()
            }
        } finally {
            isWorkerRunning = false
        }
    }

    private val counterPosApplication: CounterPosApplication
        get() = applicationContext as CounterPosApplication

    companion object {
        const val WORK_NAME = "counter_pos_offline_outbox_sync"
        const val PERIODIC_WORK_NAME = "counter_pos_periodic_sync"
        const val KEY_SYNC_MODE = "sync_mode"
        const val KEY_TRIGGER_SOURCE = "trigger_source"
        const val MODE_FLUSH_OUTBOX = "flush_outbox"
        const val MODE_FULL_SYNC = "full_sync"
        private const val INITIAL_BACKOFF_SECONDS = 15L
        private val IMMEDIATE_TRIGGERS = setOf(
            "stable_network_detected",
            "user_manual_flush",
            "settle_table_bill",
            "subscription_restored",
            "startup"
        )

        @Volatile
        internal var isWorkerRunning: Boolean = false

        @Volatile
        internal var stabilityCheckOverride: ((Context) -> Boolean)? = null

        internal fun selectUniqueWorkPolicy(
            triggerSource: String,
            workerCurrentlyRunning: Boolean
        ): ExistingWorkPolicy {
            if (workerCurrentlyRunning) return ExistingWorkPolicy.APPEND_OR_REPLACE
            return if (triggerSource in IMMEDIATE_TRIGGERS) {
                ExistingWorkPolicy.REPLACE
            } else {
                ExistingWorkPolicy.APPEND_OR_REPLACE
            }
        }

        /**
         * Verifies that the active network provides both internet transport and
         * validated upstream reachability (excluding captive or stalled links).
         */
        fun isStableInternetConnected(context: Context): Boolean {
            stabilityCheckOverride?.let { return it(context) }
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val notSuspended = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
            } else {
                true
            }
            return hasInternet && isValidated && notSuspended
        }

        /**
         * Enqueues a constrained outbox flush task. Never aborts an active RUNNING
         * upload mid-flight, while replacing stale backoff-waiting work immediately
         * when network connectivity is restored or a manual flush is requested.
         */
        fun enqueueOutboxFlush(context: Context, triggerSource: String = "mutation") {
            try {
                val workManager = WorkManager.getInstance(context.applicationContext)
                val runningInWorkManager = runCatching {
                    workManager.getWorkInfosForUniqueWork(WORK_NAME)
                        .get(100, TimeUnit.MILLISECONDS)
                        .any { it.state == androidx.work.WorkInfo.State.RUNNING }
                }.getOrDefault(false)
                val policy = selectUniqueWorkPolicy(
                    triggerSource = triggerSource,
                    workerCurrentlyRunning = isWorkerRunning || runningInWorkManager
                )
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val request = OneTimeWorkRequestBuilder<OfflineOutboxWorker>()
                    .setConstraints(constraints)
                    .setInputData(
                        workDataOf(
                            KEY_SYNC_MODE to MODE_FLUSH_OUTBOX,
                            KEY_TRIGGER_SOURCE to triggerSource
                        )
                    )
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        INITIAL_BACKOFF_SECONDS,
                        TimeUnit.SECONDS
                    )
                    .build()

                workManager.enqueueUniqueWork(
                    WORK_NAME,
                    policy,
                    request
                )
            } catch (_: Exception) {
                // WorkManager may be uninitialized in standalone JVM unit test runners.
            }
        }

        /** Backwards-compatible alias for [enqueueOutboxFlush]. */
        fun enqueue(context: Context) {
            enqueueOutboxFlush(context, triggerSource = "enqueue")
        }

        /**
         * Enqueues a periodic background reconciliation job every 15 minutes with
         * storage and battery constraints that prioritize local Room responsiveness.
         */
        fun enqueuePeriodic(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build()
                val periodicRequest = PeriodicWorkRequestBuilder<OfflineOutboxWorker>(
                    15, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .setInputData(
                        workDataOf(
                            KEY_SYNC_MODE to MODE_FULL_SYNC,
                            KEY_TRIGGER_SOURCE to "periodic"
                        )
                    )
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
                // WorkManager may be uninitialized in standalone JVM unit test runners.
            }
        }

        private fun Throwable.isPermanentAuthFailure(): Boolean {
            var current: Throwable? = this
            while (current != null) {
                if (current is com.alvinb.counterpos.data.remote.SupabaseUnauthorizedException) return true
                current = current.cause
            }
            return false
        }

        private fun Exception.isTransientSyncFailure(): Boolean {
            if (isPermanentAuthFailure()) return false
            var current: Throwable? = this
            while (current != null) {
                if (current is IOException || current is TimeoutException) return true
                current = current.cause
            }
            return false
        }
    }
}
