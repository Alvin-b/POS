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
import com.alvinb.counterpos.data.local.entity.DiningTableEntity
import com.alvinb.counterpos.data.local.entity.OfflineSessionEntity
import com.alvinb.counterpos.data.local.entity.OfflineTransactionLogEntity
import com.alvinb.counterpos.data.local.entity.OfflineTransactionStatus
import com.alvinb.counterpos.data.local.entity.OutboxEntity
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.local.entity.ServiceCounterEntity
import com.alvinb.counterpos.data.local.entity.TableOrderEntity
import com.alvinb.counterpos.data.remote.SupabaseApiException
import com.alvinb.counterpos.data.remote.SupabaseRestClient
import com.alvinb.counterpos.data.remote.SupabaseUnauthorizedException
import com.alvinb.counterpos.data.remote.shouldFallbackFromApplyPosOperation
import androidx.work.ExistingWorkPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
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

    @Test
    fun prepareOutboxPayloadPreservesMpesaPaymentMethodAndTimestampForSalesAndTableSales() = runBlocking {
        val dao = database.counterPosDao()
        val shopId = "shop-payload-1"
        val counterId = "counter-1"
        val tableId = "local-table-1"
        OfflineOutboxWorker.stabilityCheckOverride = { false }

        val sessionPayload = JSONObject()
            .put("user_id", "user-1")
            .put("email", "cashier@example.com")
            .put("is_platform_admin", false)
            .put(
                "memberships",
                JSONArray().put(
                    JSONObject()
                        .put("shop_id", shopId)
                        .put("shop_name", "Serenity Club")
                        .put("role", "cashier")
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
        dao.insertServiceCounters(listOf(ServiceCounterEntity(id = counterId, shopId = shopId, name = "VIP Bar", active = true)))
        dao.insertProduct(
            ProductEntity(
                catalogCode = "KE-MPESA-1",
                verifiedBarcode = null,
                name = "Guinness Stout",
                brand = "EABL",
                size = "500 ml",
                category = "Beer",
                suggestedPriceKsh = 350,
                sellingPriceKsh = 350,
                stockQuantity = 20,
                inMyStock = true,
                lowStockThreshold = 5,
                imageAssetName = "product_stock_001",
                remoteId = "remote-prod-mpesa-1"
            )
        )
        dao.insertDiningTable(
            DiningTableEntity(
                id = tableId,
                name = "VIP 1",
                createdAt = 1000L,
                remoteId = "remote-table-1"
            )
        )

        repository.restoreSession()

        assertTrue(
            repository.sellWithUnit(
                code = "KE-MPESA-1",
                quantity = 2,
                unitName = "Bottle",
                unitPriceKsh = 350,
                selectedCounterId = counterId,
                counterName = "VIP Bar",
                waiterName = "Grace",
                paymentMethod = "mpesa"
            )
        )
        assertTrue(
            repository.sellToTable(
                tableId = tableId,
                code = "KE-MPESA-1",
                quantity = 1,
                unitName = "Bottle",
                unitPriceKsh = 350,
                selectedCounterId = counterId,
                counterName = "VIP Bar",
                waiterName = "Grace",
                paymentMethod = "mpesa"
            )
        )

        val pending = dao.pendingOutbox(shopId)
        assertEquals(2, pending.size)

        val salePayload = repository.prepareOutboxPayload(pending[0])
        assertEquals("remote-prod-mpesa-1", salePayload.getString("product_id"))
        assertEquals(counterId, salePayload.getString("counter_id"))
        assertEquals("mpesa", salePayload.getString("payment_method"))
        assertEquals("VIP Bar", salePayload.getString("counter_name"))
        assertEquals("Grace", salePayload.getString("waiter_name"))
        assertTrue(salePayload.getLong("client_created_at") > 0L)

        val tableSalePayload = repository.prepareOutboxPayload(pending[1])
        assertEquals("remote-table-1", tableSalePayload.getString("table_id"))
        assertEquals("mpesa", tableSalePayload.getString("payment_method"))
        assertEquals(1, tableSalePayload.getInt("qty"))
    }

    @Test
    fun cancelUnsentSaleBlocksWhileInFlightSyncingAndAllowsAfterRetry() = runBlocking {
        val dao = database.counterPosDao()
        val shopId = "shop-race-1"
        dao.insertProduct(
            ProductEntity(
                catalogCode = "KE-RACE-1",
                verifiedBarcode = null,
                name = "Tusker Malt",
                brand = "EABL",
                size = "330 ml",
                category = "Beer",
                suggestedPriceKsh = 320,
                sellingPriceKsh = 320,
                stockQuantity = 10,
                inMyStock = true,
                lowStockThreshold = 3,
                imageAssetName = "product_stock_001",
                remoteId = "remote-race-1"
            )
        )
        val outbox = OutboxEntity(
            operationId = "op-race-1",
            shopId = shopId,
            actorUserId = "user-1",
            operationType = "sale",
            payloadJson = "{}",
            createdAt = 5000L
        )
        val log = OfflineTransactionLogEntity(
            operationId = "op-race-1",
            shopId = shopId,
            actorUserId = "user-1",
            operationType = "sale",
            summary = "2x Tusker Malt",
            amountKsh = 640L,
            status = OfflineTransactionStatus.QUEUED,
            recordedOffline = true,
            createdAt = 5000L,
            updatedAt = 5000L
        )
        val saleId = dao.queueSaleAtCounter(
            sale = SaleEntity(
                catalogCode = "KE-RACE-1",
                productName = "Tusker Malt",
                imageAssetName = "product_stock_001",
                unitName = "Bottle",
                quantity = 2,
                unitPriceKsh = 320,
                totalKsh = 640,
                createdAt = 5000L,
                paymentMethod = "cash"
            ),
            outbox = outbox,
            counterId = "c-1",
            stockToDecrement = 2,
            perCounter = false,
            log = log
        )!!
        val savedSale = dao.getSaleById(saleId)!!

        // Claim for active sync -> status becomes SYNCING
        assertNotNull(dao.claimPendingOutboxForSync("op-race-1", 5100L))
        assertFalse(dao.cancelUnsentSale(savedSale, perCounter = false))
        assertEquals(8, dao.getProductByCode("KE-RACE-1")?.stockQuantity)

        // If transient failure moves log back to RETRY_WAITING, cancellation succeeds safely
        dao.recordOutboxFailureRetry("op-race-1", "Network timeout", 5200L)
        assertTrue(dao.cancelUnsentSale(savedSale, perCounter = false))
        assertEquals(10, dao.getProductByCode("KE-RACE-1")?.stockQuantity)
        assertEquals(0, dao.outboxCount(shopId))
        assertEquals(OfflineTransactionStatus.CANCELLED, dao.getTransactionLog("op-race-1")?.status)
    }

    @Test
    fun fallbackAndWorkPolicyRespectIdempotencyAndImmediateNetworkRecovery() {
        assertTrue(shouldFallbackFromApplyPosOperation(SupabaseApiException(404, "Could not find the function public.apply_pos_operation")))
        assertTrue(shouldFallbackFromApplyPosOperation(SupabaseApiException(300, "Multiple choices")))
        assertFalse(shouldFallbackFromApplyPosOperation(SocketTimeoutException("timeout")))
        assertFalse(shouldFallbackFromApplyPosOperation(IOException("connection reset")))
        assertFalse(shouldFallbackFromApplyPosOperation(SupabaseUnauthorizedException("expired")))
        assertFalse(shouldFallbackFromApplyPosOperation(SupabaseApiException(400, "insufficient_stock")))
        assertFalse(shouldFallbackFromApplyPosOperation(SupabaseApiException(500, "internal server error")))

        assertEquals(
            ExistingWorkPolicy.REPLACE,
            OfflineOutboxWorker.selectUniqueWorkPolicy("stable_network_detected", workerCurrentlyRunning = false)
        )
        assertEquals(
            ExistingWorkPolicy.REPLACE,
            OfflineOutboxWorker.selectUniqueWorkPolicy("user_manual_flush", workerCurrentlyRunning = false)
        )
        assertEquals(
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OfflineOutboxWorker.selectUniqueWorkPolicy("stable_network_detected", workerCurrentlyRunning = true)
        )
        assertEquals(
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OfflineOutboxWorker.selectUniqueWorkPolicy("pos_mutation", workerCurrentlyRunning = false)
        )
    }

    @Test
    fun replaceBusinessSnapshotPreservesUnsyncedLocalTablesAndTableOrders() = runBlocking {
        val dao = database.counterPosDao()
        val shopId = "shop-snapshot-1"
        dao.insertProduct(
            ProductEntity(
                catalogCode = "KE-SNAP-1",
                verifiedBarcode = null,
                name = "Pilsner",
                brand = "EABL",
                size = "500 ml",
                category = "Beer",
                suggestedPriceKsh = 300,
                sellingPriceKsh = 300,
                stockQuantity = 15,
                inMyStock = true,
                lowStockThreshold = 5,
                imageAssetName = "product_stock_001",
                remoteId = "remote-snap-1"
            )
        )
        dao.insertDiningTable(DiningTableEntity(id = "local-t-1", name = "Patio 4", createdAt = 1000L, remoteId = null))
        val localSaleId = dao.insertSale(
            SaleEntity(
                catalogCode = "KE-SNAP-1",
                productName = "Pilsner",
                imageAssetName = "product_stock_001",
                unitName = "Bottle",
                quantity = 2,
                unitPriceKsh = 300,
                totalKsh = 600,
                createdAt = 1000L,
                tableId = "local-t-1",
                tableName = "Patio 4",
                remoteId = null
            )
        )
        dao.insertTableOrder(
            TableOrderEntity(
                tableId = "local-t-1",
                saleId = localSaleId,
                catalogCode = "KE-SNAP-1",
                productName = "Pilsner",
                unitName = "Bottle",
                quantity = 2,
                unitPriceKsh = 300,
                totalKsh = 600,
                servedAt = 1000L
            )
        )

        dao.replaceBusinessSnapshot(
            shopId = shopId,
            products = dao.getAllProducts(),
            sales = emptyList(),
            tables = listOf(DiningTableEntity(id = "remote-t-2", name = "Booth 2", createdAt = 900L, remoteId = "remote-t-2")),
            orders = emptyList(),
            serviceCounters = emptyList(),
            counterStock = emptyList(),
            preservePendingSales = true
        )

        assertTrue(dao.getDiningTables().any { it.id == "local-t-1" })
        assertTrue(dao.observeTableOrders().first().any { it.saleId == localSaleId })
        assertTrue(dao.observeSales().first().any { it.id == localSaleId })
    }
}
