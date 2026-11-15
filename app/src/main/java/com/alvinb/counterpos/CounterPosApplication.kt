package com.alvinb.counterpos

import android.app.Application
import com.alvinb.counterpos.data.CounterPosRepository
import com.alvinb.counterpos.data.local.AppDatabase
import com.alvinb.counterpos.data.remote.SupabaseRestClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application-scoped composition root for the POS data layer.
 *
 * The Android application instance is a process singleton, so the repository and
 * its dependencies are shared by activities and WorkManager workers.
 */
class CounterPosApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppDatabase.getInstance(this)
    }

    private val supabaseRestClient: SupabaseRestClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SupabaseRestClient(this)
    }

    val repository: CounterPosRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CounterPosRepository(
            dao = database.counterPosDao(),
            assets = assets,
            context = applicationContext,
            remote = supabaseRestClient
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Rehydrate shared repository and only trigger immediate outbox worker if
        // unacknowledged local mutations actually exist from a prior process.
        repository
        appScope.launch {
            runCatching {
                OfflineOutboxWorker.enqueuePeriodic(applicationContext)
                if (repository.hasPendingOutboxAll()) {
                    OfflineOutboxWorker.enqueue(applicationContext)
                }
            }
        }
    }
}
