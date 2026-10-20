package com.alvinb.counterpos

import android.app.Application
import com.alvinb.counterpos.data.CounterPosRepository
import com.alvinb.counterpos.data.local.AppDatabase
import com.alvinb.counterpos.data.remote.SupabaseRestClient

/**
 * Application-scoped composition root for the POS data layer.
 *
 * The Android application instance is a process singleton, so the repository and
 * its dependencies are shared by activities and WorkManager workers.
 */
class CounterPosApplication : Application() {
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
        // Initialize the shared repository before a worker can be created and
        // ensure any outbox entries left by a previous process are considered.
        repository
        OfflineOutboxWorker.enqueue(this)
    }
}
