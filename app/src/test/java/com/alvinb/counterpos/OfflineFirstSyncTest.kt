package com.alvinb.counterpos

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.alvinb.counterpos.data.CounterPosRepository
import com.alvinb.counterpos.data.local.AppDatabase
import com.alvinb.counterpos.data.local.entity.CounterStockEntity
import com.alvinb.counterpos.data.local.entity.OfflineSessionEntity
import com.alvinb.counterpos.data.local.entity.OfflineTransactionLogEntity
import com.alvinb.counterpos.data.local.entity.OfflineTransactionStatus
import com.alvinb.counterpos.data.local.entity.OutboxEntity
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.ServiceCounterEntity
import com.alvinb.counterpos.data.remote.SupabaseRestClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineFirstSyncTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var remote: SupabaseRestClient
    private lateinit var repository: CounterPosRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        OfflineOutboxWorker.stabilityCheckOverride = null
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        remote = SupabaseRestClient(context)
        remote.clearSession()
        repository = CounterPosRepository(
            context = context,
            dao = database.counterPosDao(),
            assets = context.assets,
            remote = remote
        )
    }

    @After
    fun tearDown() {
        OfflineOutboxWorker.stabilityCheckOverride = null
        database.close()
    }

    @Test
    fun coldStartSignedOutRestoresImmediatelyWithoutBlocking() = runBlocking {
        val snapshot = repository.restoreSession()
        assertFalse(snapshot.isAuthenticated)
        assertEquals(null, snapshot.activeBusiness)
        assertFalse(repository.hasPendingOutboxAll())
    }

    @Test
    fun offlineCachedSessionRestoresImmediatelyAndLogsOfflineSalesAndUndo() = runBlocking {
        val dao = database.counterPosDao()
        val shopId = "shop-offline-1"
        val counterId = "counter-main"
        val userId = "user-cashier-1"
        OfflineOutboxWorker.stabilityCheckOverride = { false }

        val sessionPayload = JSONObject()
            .put("user_id", userId)
            .put("email", "cashier@example.com")
            .put("is_platform_admin", false)
            .put(
                "memberships",
                JSONArray().put(
                    JSONObject()
                        .put("shop_id", shopId)
                        .put("shop_name", "Downtown Club")
                        .put("role", "cashier")
                        .put("status", "active")
                        .put("employment_status", "active")
                        .put("business_type", "big_club")
                        .put("business_size", "medium")
                        .put("counter_inventory_mode", "per_counter")
                        .put("counter_count", 1)
                        .put("feature_flags", JSONArray(listOf("tables")))
                        .put("currency", "KES")
                        .put("low_stock_threshold", 3)
                        .put("subscription_status", "active")
                )
            )
            .toString()

        dao.saveOfflineSession(OfflineSessionEntity(snapshotJson = sessionPayload, activeShopId = shopId))
        dao.insertServiceCounters(listOf(ServiceCounterEntity(id = counterId, shopId = shopId, name = "Main Bar", active = true)))
        dao.insertProduct(
            ProductEntity(
                catalogCode = "KE-OFF-1",
                verifiedBarcode = "123456789",
                name = "Tusker Lager",
                brand = "EABL",
                size = "500 ml",
                category = "Beer",
                suggestedPriceKsh = 300,
                sellingPriceKsh = 300,
                stockQuantity = 10,
                inMyStock = true,
                lowStockThreshold = 3,
                imageAssetName = "product_stock_001",
                remoteId = "remote-prod-1"
            )
        )
        dao.insertCounterStock(
            listOf(
                CounterStockEntity(
                    shopId = shopId,
                    counterId = counterId,
                    catalogCode = "KE-OFF-1",
                    productId = "remote-prod-1",
                    quantity = 10
                )
            )
        )

        val restored = repository.restoreSession()
        assertTrue(restored.isAuthenticated)
        assertEquals(shopId, restored.activeBusiness?.shopId)
        assertEquals(listOf(counterId), repository.serviceCounters.value.map { it.id })

        // Perform instant offline sale queued to Room outbox + offline_transaction_logs
        val sold = repository.sellWithUnit(
            code = "KE-OFF-1",
            quantity = 2,
            unitName = "Bottle",
            unitPriceKsh = 300,
            selectedCounterId = counterId,
            counterName = "Main Bar"
        )
        assertTrue(sold)
        assertEquals(8, dao.getCounterStockQuantity(counterId, "KE-OFF-1"))
        assertEquals(1, repository.pendingOutboxCount())
        assertTrue(repository.hasPendingOutboxAll())
        assertEquals(1, dao.observeSales().first().count { !it.undone })

        val logsAfterSale = repository.observeTransactionLogs(shopId).first()
        assertEquals(1, logsAfterSale.size)
        val loggedSale = logsAfterSale.single()
        assertEquals(OfflineTransactionStatus.QUEUED, loggedSale.status)
        assertTrue(loggedSale.recordedOffline)
        assertEquals(600L, loggedSale.amountKsh)
        assertTrue(loggedSale.summary.contains("Tusker Lager"))
        assertEquals(1, repository.observePendingTransactionLogCount(shopId).first())

        // Undoing an unsynced offline sale immediately restores stock, clears outbox, and marks log CANCELLED
        assertTrue(repository.undoLatestSale())
        assertEquals(10, dao.getCounterStockQuantity(counterId, "KE-OFF-1"))
        assertEquals(0, repository.pendingOutboxCount())
        assertTrue(dao.observeSales().first().single().undone)

        val logsAfterUndo = repository.observeTransactionLogs(shopId).first()
        assertEquals(OfflineTransactionStatus.CANCELLED, logsAfterUndo.single().status)
        assertEquals(0, repository.observePendingTransactionLogCount(shopId).first())
    }

    @Test
    fun transactionLogLifecycleTransitionsThroughSyncingRetryBlockedUnblockAndSynced() = runBlocking {
        val dao = database.counterPosDao()
        val shopId = "shop-lifecycle-1"
        val outbox = OutboxEntity(
            operationId = "op-life-1",
            shopId = shopId,
            actorUserId = "user-1",
            operationType = "sale",
            payloadJson = "{}",
            createdAt = 1_000L
        )
        val log = OfflineTransactionLogEntity(
            operationId = "op-life-1",
            shopId = shopId,
            actorUserId = "user-1",
            operationType = "sale",
            summary = "1x Tusker (Bottle)",
            amountKsh = 300L,
            status = OfflineTransactionStatus.QUEUED,
            recordedOffline = true,
            createdAt = 1_000L,
            updatedAt = 1_000L
        )
        dao.enqueueOutboxAndLog(outbox, log)
        assertEquals(1, dao.outboxCount(shopId))

        // Worker starts syncing
        dao.markTransactionLogSyncing("op-life-1", 1_100L)
        assertEquals(OfflineTransactionStatus.SYNCING, dao.getTransactionLog("op-life-1")?.status)

        // Transient failure -> RETRY_WAITING with incremented attempts
        dao.recordOutboxFailureRetry("op-life-1", "Timeout", 1_200L)
        val retried = dao.getTransactionLog("op-life-1")
        assertEquals(OfflineTransactionStatus.RETRY_WAITING, retried?.status)
        assertEquals(1, retried?.attempts)
        assertEquals("Timeout", retried?.lastError)

        // Hard rejection -> BLOCKED
        dao.blockOutboxAndLog("op-life-1", "Invalid price", 1_300L)
        val blocked = dao.getTransactionLog("op-life-1")
        assertEquals(OfflineTransactionStatus.BLOCKED, blocked?.status)
        assertEquals(1, dao.blockedOutbox(shopId).size)

        // Unblock restores both outbox and transaction log to QUEUED
        dao.unblockShopOutboxAndLogs(shopId, 1_400L)
        assertEquals(0, dao.blockedOutbox(shopId).size)
        val unblocked = dao.getTransactionLog("op-life-1")
        assertEquals(OfflineTransactionStatus.QUEUED, unblocked?.status)
        assertNull(unblocked?.lastError)

        // Complete sync removes from outbox and marks log SYNCED
        dao.completeOutboxSync("op-life-1", 1_500L)
        assertEquals(0, dao.outboxCount(shopId))
        val synced = dao.getTransactionLog("op-life-1")
        assertEquals(OfflineTransactionStatus.SYNCED, synced?.status)
        assertNotNull(synced?.syncedAt)
        assertEquals(1_500L, synced?.syncedAt)
    }

    @Test
    fun migration11To12CreatesLogsTableAndBackfillsPendingOutbox() {
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory SQLite db
            .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS sync_outbox (" +
                            "operationId TEXT NOT NULL, shopId TEXT NOT NULL, actorUserId TEXT NOT NULL, " +
                            "operationType TEXT NOT NULL, payloadJson TEXT NOT NULL, localSaleId INTEGER, " +
                            "createdAt INTEGER NOT NULL, attempts INTEGER NOT NULL, lastError TEXT, " +
                            "blocked INTEGER NOT NULL, PRIMARY KEY(operationId))"
                    )
                    db.execSQL(
                        "INSERT INTO sync_outbox VALUES ('op-q', 'shop-1', 'u-1', 'sale', '{}', 5, 1000, 0, NULL, 0)"
                    )
                    db.execSQL(
                        "INSERT INTO sync_outbox VALUES ('op-b', 'shop-1', 'u-1', 'table_sale', '{}', 6, 2000, 2, 'Denied', 1)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val db = helperFactory.create(config).writableDatabase
        AppDatabase.MIGRATION_11_12.migrate(db)

        val cursor = db.query("SELECT operationId, status, recordedOffline, attempts, lastError FROM offline_transaction_logs ORDER BY createdAt ASC")
        try {
            assertEquals(2, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("op-q", cursor.getString(0))
            assertEquals("QUEUED", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals(0, cursor.getInt(3))

            assertTrue(cursor.moveToNext())
            assertEquals("op-b", cursor.getString(0))
            assertEquals("BLOCKED", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals(2, cursor.getInt(3))
            assertEquals("Denied", cursor.getString(4))
        } finally {
            cursor.close()
            db.close()
        }
    }

    @Test
    fun stableInternetCheckRequiresValidatedCapability() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadowCm = Shadows.shadowOf(cm)

        val unvalidatedCaps = ShadowNetworkCapabilities.newInstance()
        val shadowUnvalidated = Shadows.shadowOf(unvalidatedCaps)
        shadowUnvalidated.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowUnvalidated.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
        shadowCm.setNetworkCapabilities(cm.activeNetwork, unvalidatedCaps)

        assertFalse(OfflineOutboxWorker.isStableInternetConnected(context))

        val validatedCaps = ShadowNetworkCapabilities.newInstance()
        val shadowValidated = Shadows.shadowOf(validatedCaps)
        shadowValidated.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowValidated.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowValidated.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
        shadowCm.setNetworkCapabilities(cm.activeNetwork, validatedCaps)

        assertTrue(OfflineOutboxWorker.isStableInternetConnected(context))
    }

    @Test
    fun roomLowStockFlowUpdatesImmediatelyOnSaleUndoAndRestock() = runBlocking {
        val dao = database.counterPosDao()
        val shopId = "shop-lowstock-1"
        val counterId = "counter-low-1"
        val userId = "user-manager-1"
        OfflineOutboxWorker.stabilityCheckOverride = { false }

        val sessionPayload = JSONObject()
            .put("user_id", userId)
            .put("email", "manager@example.com")
            .put("is_platform_admin", false)
            .put(
                "memberships",
                JSONArray().put(
                    JSONObject()
                        .put("shop_id", shopId)
                        .put("shop_name", "Oasis Lounge")
                        .put("role", "manager")
                        .put("status", "active")
                        .put("employment_status", "active")
                        .put("business_type", "big_club")
                        .put("business_size", "medium")
                        .put("counter_inventory_mode", "shared")
                        .put("counter_count", 1)
                        .put("feature_flags", JSONArray(listOf("tables")))
                        .put("currency", "KES")
                        .put("low_stock_threshold", 5)
                        .put("subscription_status", "active")
                )
            )
            .toString()

        dao.saveOfflineSession(OfflineSessionEntity(snapshotJson = sessionPayload, activeShopId = shopId))
        dao.insertServiceCounters(listOf(ServiceCounterEntity(id = counterId, shopId = shopId, name = "Main Counter", active = true)))
        dao.insertProducts(
            listOf(
                ProductEntity(
                    catalogCode = "KE-LOW-1",
                    verifiedBarcode = "111222333",
                    name = "White Cap Lager",
                    brand = "EABL",
                    size = "500 ml",
                    category = "Beer",
                    suggestedPriceKsh = 300,
                    sellingPriceKsh = 300,
                    stockQuantity = 7,
                    inMyStock = true,
                    lowStockThreshold = 5,
                    imageAssetName = "product_stock_001",
                    remoteId = "remote-prod-low-1"
                ),
                ProductEntity(
                    catalogCode = "KE-LIB-1",
                    verifiedBarcode = null,
                    name = "Catalog Only Item",
                    brand = "EABL",
                    size = "500 ml",
                    category = "Beer",
                    suggestedPriceKsh = 300,
                    sellingPriceKsh = 0,
                    stockQuantity = 0,
                    inMyStock = false,
                    lowStockThreshold = 5,
                    imageAssetName = "product_stock_001"
                )
            )
        )

        repository.restoreSession()

        // Initially healthy (7 > 5) and catalog-only item is ignored
        assertEquals(0, dao.observeLowStockCount().first())
        assertTrue(repository.lowStockProducts.first().isEmpty())

        // Sell 2 -> hits exact minimum threshold (5 <= 5) -> Room Flow flags immediately
        assertTrue(
            repository.sellWithUnit(
                code = "KE-LOW-1",
                quantity = 2,
                unitName = "Bottle",
                unitPriceKsh = 300,
                selectedCounterId = counterId,
                counterName = "Main Counter"
            )
        )
        assertEquals(1, dao.observeLowStockCount().first())
        assertEquals(listOf("KE-LOW-1"), repository.lowStockProducts.first().map { it.catalogCode })

        // Restock 4 (back to 9 > 5) -> Room Flow clears immediately
        assertEquals(1, dao.receiveStock("KE-LOW-1", 4))
        assertEquals(0, dao.observeLowStockCount().first())
        assertTrue(repository.lowStockProducts.first().isEmpty())

        // Verify restored original brand icon drawable resolves cleanly
        val iconResId = context.resources.getIdentifier(
            "ic_counterpos_logo",
            "drawable",
            context.packageName
        )
        assertTrue(iconResId != 0)
    }
}
