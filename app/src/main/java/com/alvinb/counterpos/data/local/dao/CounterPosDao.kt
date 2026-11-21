package com.alvinb.counterpos.data.local.dao
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.alvinb.counterpos.data.local.entity.CounterStockEntity
import com.alvinb.counterpos.data.local.entity.DiningTableEntity
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.local.entity.ServiceCounterEntity
import com.alvinb.counterpos.data.local.entity.TableOrderEntity
import com.alvinb.counterpos.data.local.entity.parseSaleUnits
import kotlinx.coroutines.flow.Flow
import com.alvinb.counterpos.data.local.entity.ButcheryConfigEntity
import com.alvinb.counterpos.data.local.entity.ButcheryCutEntity
import com.alvinb.counterpos.data.local.entity.ButcherySaleEntity
import com.alvinb.counterpos.data.local.entity.OfflineSessionEntity
import com.alvinb.counterpos.data.local.entity.OfflineTransactionLogEntity
import com.alvinb.counterpos.data.local.entity.OfflineTransactionStatus
import com.alvinb.counterpos.data.local.entity.OutboxEntity
@Dao
interface CounterPosDao {
    @Query("SELECT * FROM dining_tables ORDER BY createdAt ASC, name COLLATE NOCASE ASC")
    fun observeDiningTables(): Flow<List<DiningTableEntity>>
    @Query("SELECT * FROM table_orders ORDER BY servedAt ASC, id ASC")
    fun observeTableOrders(): Flow<List<TableOrderEntity>>
    @Query("SELECT COUNT(*) FROM table_orders WHERE tableId = :tableId AND settlementId IS NULL")
    suspend fun openTableOrderCount(tableId: String): Int
    @Query("SELECT COALESCE(SUM(totalKsh), 0) FROM table_orders WHERE tableId = :tableId AND settlementId IS NULL")
    suspend fun openTableBillTotal(tableId: String): Long
    @Query("UPDATE table_orders SET settlementId = :settlementId WHERE tableId = :tableId AND settlementId IS NULL")
    suspend fun markTableOrdersSettled(tableId: String, settlementId: String): Int
    @Query("SELECT * FROM service_counters WHERE shopId = :shopId ORDER BY name COLLATE NOCASE ASC")
    suspend fun getServiceCounters(shopId: String): List<ServiceCounterEntity>
    @Query("DELETE FROM service_counters WHERE shopId = :shopId")
    suspend fun clearServiceCounters(shopId: String)
    @Query("DELETE FROM service_counters WHERE shopId = :shopId AND id = :counterId")
    suspend fun deleteServiceCounter(shopId: String, counterId: String): Int
    @Query("DELETE FROM service_counters")
    suspend fun clearAllServiceCounters()
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceCounters(counters: List<ServiceCounterEntity>)
    @Query("SELECT * FROM counter_stock WHERE shopId = :shopId AND counterId = :counterId")
    fun observeCounterStock(shopId: String, counterId: String): Flow<List<CounterStockEntity>>
    @Query("SELECT * FROM counter_stock WHERE counterId = :counterId")
    suspend fun getCounterStockRows(counterId: String): List<CounterStockEntity>
    @Query("SELECT quantity FROM counter_stock WHERE counterId = :counterId AND catalogCode = :catalogCode LIMIT 1")
    suspend fun getCounterStockQuantity(counterId: String, catalogCode: String): Int?
    @Query("DELETE FROM counter_stock WHERE shopId = :shopId AND counterId = :counterId")
    suspend fun clearCounterStock(shopId: String, counterId: String)
    @Query("DELETE FROM counter_stock WHERE shopId = :shopId")
    suspend fun clearCounterStockForShop(shopId: String)
    @Query("DELETE FROM counter_stock")
    suspend fun clearAllCounterStock()
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCounterStock(rows: List<CounterStockEntity>)
    @Query("UPDATE counter_stock SET quantity = quantity - :quantity WHERE counterId = :counterId AND catalogCode = :catalogCode AND quantity >= :quantity")
    suspend fun decrementCounterStock(counterId: String, catalogCode: String, quantity: Int): Int
    @Query("UPDATE counter_stock SET quantity = quantity + :quantity WHERE counterId = :counterId AND catalogCode = :catalogCode")
    suspend fun incrementCounterStock(counterId: String, catalogCode: String, quantity: Int): Int
    @Transaction
    suspend fun queueCounterTransfer(
        outbox: OutboxEntity,
        sourceCounterId: String,
        targetBalance: CounterStockEntity,
        quantity: Int,
        log: OfflineTransactionLogEntity? = null
    ): Boolean {
        if (quantity < 1) return false
        val available = getCounterStockQuantity(sourceCounterId, targetBalance.catalogCode) ?: return false
        if (available < quantity || decrementCounterStock(sourceCounterId, targetBalance.catalogCode, quantity) != 1) return false
        insertCounterStock(listOf(targetBalance))
        enqueueOutbox(outbox)
        if (log != null) insertTransactionLog(log)
        return true
    }
    @Query("SELECT * FROM sync_outbox WHERE shopId = :shopId AND blocked = 0 ORDER BY createdAt ASC, operationId ASC")
    suspend fun pendingOutbox(shopId: String): List<OutboxEntity>
    @Query("SELECT * FROM sync_outbox WHERE shopId = :shopId AND operationType = 'settle_table_bill' ORDER BY createdAt ASC, operationId ASC")
    fun observeTableSettlementOutbox(shopId: String): Flow<List<OutboxEntity>>
    @Query("SELECT * FROM sync_outbox WHERE shopId = :shopId AND blocked = 0 ORDER BY createdAt ASC, operationId ASC LIMIT 1")
    suspend fun nextPendingOutbox(shopId: String): OutboxEntity?
    @Query("SELECT * FROM sync_outbox WHERE operationId = :operationId LIMIT 1")
    suspend fun getOutboxItem(operationId: String): OutboxEntity?
    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observePendingOutboxCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM sync_outbox WHERE shopId = :shopId")
    fun observePendingOutboxCountForShop(shopId: String): Flow<Int>
    @Query("SELECT COUNT(*) FROM sync_outbox WHERE shopId = :shopId")
    suspend fun outboxCount(shopId: String): Int
    @Query("SELECT COUNT(*) FROM sync_outbox WHERE operationId = :operationId")
    suspend fun outboxOperationCount(operationId: String): Int
    @Query("SELECT * FROM sync_outbox WHERE shopId = :shopId AND blocked = 1 ORDER BY createdAt ASC")
    suspend fun blockedOutbox(shopId: String): List<OutboxEntity>
    @Query("SELECT * FROM sync_outbox WHERE shopId = :shopId AND blocked = 1 ORDER BY createdAt ASC")
    fun observeBlockedOutbox(shopId: String): Flow<List<OutboxEntity>>
    @Query("UPDATE sync_outbox SET blocked = 0, lastError = NULL WHERE shopId = :shopId")
    suspend fun unblockOutboxForShop(shopId: String): Int
    @Query("UPDATE sync_outbox SET blocked = 0, lastError = NULL WHERE operationId = :operationId")
    suspend fun unblockOutboxItem(operationId: String): Int
    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun outboxCountAll(): Int
    @Query("SELECT COUNT(*) FROM sync_outbox WHERE actorUserId <> :userId")
    suspend fun outboxCountForOtherActor(userId: String): Int
    @Query("SELECT COUNT(*) FROM sync_outbox WHERE shopId NOT IN (:shopIds)")
    suspend fun outboxCountForOtherShops(shopIds: List<String>): Int
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun enqueueOutbox(item: OutboxEntity)
    @Query("UPDATE sync_outbox SET attempts = attempts + 1, lastError = :error WHERE operationId = :operationId")
    suspend fun recordOutboxAttempt(operationId: String, error: String?)
    @Query("UPDATE sync_outbox SET lastError = :error, blocked = 1 WHERE operationId = :operationId")
    suspend fun blockOutboxItem(operationId: String, error: String)
    @Query("DELETE FROM sync_outbox WHERE operationId = :operationId")
    suspend fun acknowledgeOutboxItem(operationId: String): Int
    @Query("SELECT * FROM sync_outbox WHERE localSaleId = :saleId LIMIT 1")
    suspend fun outboxForSale(saleId: Long): OutboxEntity?
    @Query("DELETE FROM sync_outbox WHERE localSaleId = :saleId")
    suspend fun cancelOutboxForSale(saleId: Long): Int

    // ---- Offline Transaction Log Queries & Lifecycle ----
    @Query("SELECT * FROM offline_transaction_logs WHERE shopId = :shopId ORDER BY createdAt DESC LIMIT :limit")
    fun observeTransactionLogs(shopId: String, limit: Int = 120): Flow<List<OfflineTransactionLogEntity>>

    @Query(
        "SELECT COUNT(*) FROM offline_transaction_logs " +
            "WHERE shopId = :shopId AND status IN ('QUEUED', 'SYNCING', 'RETRY_WAITING', 'BLOCKED')"
    )
    fun observePendingTransactionLogCount(shopId: String): Flow<Int>

    @Query("SELECT * FROM offline_transaction_logs WHERE shopId = :shopId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getTransactionLogsForShop(shopId: String, limit: Int = 120): List<OfflineTransactionLogEntity>

    @Query("SELECT * FROM offline_transaction_logs WHERE operationId = :operationId LIMIT 1")
    suspend fun getTransactionLog(operationId: String): OfflineTransactionLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionLog(log: OfflineTransactionLogEntity)

    @Query("UPDATE offline_transaction_logs SET status = 'SYNCING', updatedAt = :updatedAt WHERE operationId = :operationId")
    suspend fun markTransactionLogSyncing(operationId: String, updatedAt: Long)

    @Query(
        "UPDATE offline_transaction_logs SET status = 'SYNCED', lastError = NULL, syncedAt = :syncedAt, updatedAt = :syncedAt " +
            "WHERE operationId = :operationId"
    )
    suspend fun markTransactionLogSynced(operationId: String, syncedAt: Long)

    @Query(
        "UPDATE offline_transaction_logs SET status = 'RETRY_WAITING', attempts = attempts + 1, lastError = :error, updatedAt = :updatedAt " +
            "WHERE operationId = :operationId"
    )
    suspend fun markTransactionLogRetryWaiting(operationId: String, error: String?, updatedAt: Long)

    @Query(
        "UPDATE offline_transaction_logs SET status = 'BLOCKED', attempts = attempts + 1, lastError = :error, updatedAt = :updatedAt " +
            "WHERE operationId = :operationId"
    )
    suspend fun markTransactionLogBlocked(operationId: String, error: String, updatedAt: Long)

    @Query(
        "UPDATE offline_transaction_logs SET status = 'CANCELLED', lastError = NULL, updatedAt = :updatedAt " +
            "WHERE localSaleId = :saleId AND status <> 'SYNCED'"
    )
    suspend fun markTransactionLogCancelledBySale(saleId: Long, updatedAt: Long)

    @Query(
        "UPDATE offline_transaction_logs SET status = 'QUEUED', lastError = NULL, updatedAt = :updatedAt " +
            "WHERE shopId = :shopId AND status IN ('BLOCKED', 'RETRY_WAITING')"
    )
    suspend fun unblockTransactionLogsForShop(shopId: String, updatedAt: Long)

    @Query(
        "UPDATE offline_transaction_logs SET status = 'QUEUED', lastError = NULL, updatedAt = :updatedAt " +
            "WHERE operationId = :operationId"
    )
    suspend fun unblockTransactionLog(operationId: String, updatedAt: Long)

    @Query(
        "UPDATE offline_transaction_logs SET status = 'QUEUED', updatedAt = :updatedAt " +
            "WHERE shopId = :shopId AND status = 'SYNCING'"
    )
    suspend fun resetStaleSyncingTransactionLogs(shopId: String, updatedAt: Long)

    @Query(
        "DELETE FROM offline_transaction_logs WHERE shopId = :shopId AND status IN ('SYNCED', 'CANCELLED') " +
            "AND operationId NOT IN (" +
            "SELECT operationId FROM offline_transaction_logs WHERE shopId = :shopId AND status IN ('SYNCED', 'CANCELLED') " +
            "ORDER BY createdAt DESC LIMIT :keepCount" +
            ")"
    )
    suspend fun pruneOldSyncedTransactionLogs(shopId: String, keepCount: Int = 150)

    @Transaction
    suspend fun enqueueOutboxAndLog(item: OutboxEntity, log: OfflineTransactionLogEntity) {
        enqueueOutbox(item)
        insertTransactionLog(log)
    }

    @Transaction
    suspend fun claimPendingOutboxForSync(
        operationId: String,
        updatedAt: Long = System.currentTimeMillis()
    ): OutboxEntity? {
        val current = getOutboxItem(operationId) ?: return null
        if (current.blocked) return null
        val log = getTransactionLog(operationId)
        if (log?.status == OfflineTransactionStatus.CANCELLED) return null
        markTransactionLogSyncing(operationId, updatedAt)
        return current
    }

    @Transaction
    suspend fun completeOutboxSync(operationId: String, syncedAt: Long = System.currentTimeMillis()) {
        acknowledgeOutboxItem(operationId)
        markTransactionLogSynced(operationId, syncedAt)
    }

    @Transaction
    suspend fun recordOutboxFailureRetry(
        operationId: String,
        error: String?,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        recordOutboxAttempt(operationId, error)
        markTransactionLogRetryWaiting(operationId, error, updatedAt)
    }

    @Transaction
    suspend fun blockOutboxAndLog(
        operationId: String,
        error: String,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        blockOutboxItem(operationId, error)
        markTransactionLogBlocked(operationId, error, updatedAt)
    }

    @Transaction
    suspend fun unblockShopOutboxAndLogs(shopId: String, updatedAt: Long = System.currentTimeMillis()): Int {
        unblockTransactionLogsForShop(shopId, updatedAt)
        return unblockOutboxForShop(shopId)
    }

    @Transaction
    suspend fun unblockSingleOutboxAndLog(operationId: String, updatedAt: Long = System.currentTimeMillis()): Int {
        unblockTransactionLog(operationId, updatedAt)
        return unblockOutboxItem(operationId)
    }
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOfflineSession(session: OfflineSessionEntity)
    @Query("SELECT * FROM offline_session WHERE `key` = 'account' LIMIT 1")
    suspend fun loadOfflineSession(): OfflineSessionEntity?
    @Query("UPDATE offline_session SET activeShopId = :shopId, savedAt = :savedAt WHERE `key` = 'account'")
    suspend fun setOfflineActiveShop(shopId: String?, savedAt: Long): Int
    @Query("DELETE FROM offline_session")
    suspend fun clearOfflineSession()
    @Query("SELECT * FROM dining_tables WHERE id = :id LIMIT 1")
    suspend fun getDiningTable(id: String): DiningTableEntity?
    @Query("SELECT * FROM dining_tables")
    suspend fun getDiningTables(): List<DiningTableEntity>
    @Query("SELECT * FROM dining_tables WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getDiningTableByName(name: String): DiningTableEntity?
    @Query("SELECT COUNT(*) FROM dining_tables")
    suspend fun getDiningTableCount(): Int
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDiningTable(table: DiningTableEntity)
    @Transaction
    suspend fun queueDiningTable(
        table: DiningTableEntity,
        outbox: OutboxEntity,
        log: OfflineTransactionLogEntity? = null
    ) {
        insertDiningTable(table)
        enqueueOutbox(outbox)
        if (log != null) insertTransactionLog(log)
    }
    @Query("UPDATE dining_tables SET remoteId = :remoteId WHERE id = :localId")
    suspend fun setDiningTableRemoteId(localId: String, remoteId: String): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiningTables(tables: List<DiningTableEntity>)
    @Query("DELETE FROM dining_tables WHERE id = :id")
    suspend fun deleteDiningTable(id: String): Int

    @Query("DELETE FROM table_orders WHERE tableId = :tableId")
    suspend fun deleteTableOrdersForTable(tableId: String): Int

    @Insert
    suspend fun insertTableOrder(order: TableOrderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTableOrders(orders: List<TableOrderEntity>)

    @Query("DELETE FROM table_orders WHERE saleId = :saleId")
    suspend fun deleteTableOrderBySaleId(saleId: Long): Int

    @Query("DELETE FROM sales WHERE remoteId IS NOT NULL")
    suspend fun clearSyncedSales(): Int

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE inMyStock = 1 AND stockQuantity <= lowStockThreshold ORDER BY stockQuantity ASC, name COLLATE NOCASE ASC")
    fun observeLowStockProducts(): Flow<List<ProductEntity>>

    @Query("SELECT COUNT(*) FROM products WHERE inMyStock = 1 AND stockQuantity <= lowStockThreshold")
    fun observeLowStockCount(): Flow<Int>

    @Query("SELECT * FROM sales ORDER BY createdAt DESC, id DESC")
    fun observeSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE undone = 0 AND remoteId IS NULL ORDER BY createdAt ASC, id ASC")
    suspend fun getPendingRemoteSales(): List<SaleEntity>

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductCount(): Int

    @Query("SELECT * FROM products")
    suspend fun getAllProducts(): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSales(sales: List<SaleEntity>)

    @Query("DELETE FROM table_orders")
    suspend fun clearTableOrders()

    @Query("DELETE FROM dining_tables")
    suspend fun clearDiningTables()

    @Query("DELETE FROM sales")
    suspend fun clearSales()

    @Query("DELETE FROM products")
    suspend fun clearProducts()

    @Transaction
    suspend fun clearTenantCache() {
        clearTableOrders()
        clearDiningTables()
        clearSales()
        clearProducts()
    }

    @Query("DELETE FROM dining_tables WHERE id NOT IN (:ids)")
    suspend fun deleteDiningTablesNotIn(ids: List<String>)

    @Query("DELETE FROM dining_tables WHERE remoteId IS NOT NULL AND id NOT IN (:ids)")
    suspend fun deleteSyncedDiningTablesNotIn(ids: List<String>)

    @Query("DELETE FROM dining_tables WHERE remoteId IS NOT NULL")
    suspend fun clearSyncedDiningTables()

    @Query("DELETE FROM table_orders WHERE saleId NOT IN (:saleIds)")
    suspend fun deleteTableOrdersNotIn(saleIds: List<Long>)

    @Query("DELETE FROM table_orders WHERE saleId NOT IN (:saleIds) AND saleId NOT IN (SELECT id FROM sales WHERE remoteId IS NULL AND undone = 0)")
    suspend fun deleteSyncedTableOrdersNotIn(saleIds: List<Long>)

    @Query("DELETE FROM table_orders WHERE saleId NOT IN (SELECT id FROM sales WHERE remoteId IS NULL AND undone = 0)")
    suspend fun clearSyncedTableOrders()

    @Query("DELETE FROM products WHERE catalogCode NOT IN (:codes)")
    suspend fun deleteProductsNotIn(codes: List<String>)

    @Query("DELETE FROM service_counters WHERE shopId = :shopId AND id NOT IN (:ids)")
    suspend fun deleteServiceCountersNotIn(shopId: String, ids: List<String>)

    @Transaction
    suspend fun replaceBusinessSnapshot(
        shopId: String,
        products: List<ProductEntity>,
        sales: List<SaleEntity>,
        tables: List<DiningTableEntity>,
        orders: List<TableOrderEntity>,
        serviceCounters: List<ServiceCounterEntity>,
        counterStock: List<CounterStockEntity>,
        preservePendingSales: Boolean
    ) {
        if (tables.isNotEmpty()) {
            insertDiningTables(tables)
            if (preservePendingSales) {
                deleteSyncedDiningTablesNotIn(tables.map { it.id })
            } else {
                deleteDiningTablesNotIn(tables.map { it.id })
            }
        } else if (preservePendingSales) {
            clearSyncedDiningTables()
        } else {
            clearDiningTables()
        }

        if (orders.isNotEmpty()) {
            insertTableOrders(orders)
            if (preservePendingSales) {
                deleteSyncedTableOrdersNotIn(orders.map { it.saleId })
            } else {
                deleteTableOrdersNotIn(orders.map { it.saleId })
            }
        } else if (preservePendingSales) {
            clearSyncedTableOrders()
        } else {
            clearTableOrders()
        }

        if (preservePendingSales) clearSyncedSales() else clearSales()
        if (sales.isNotEmpty()) insertSales(sales)

        if (products.isNotEmpty()) {
            insertProducts(products)
            deleteProductsNotIn(products.map { it.catalogCode })
        } else {
            clearProducts()
        }

        if (serviceCounters.isNotEmpty()) {
            insertServiceCounters(serviceCounters)
            deleteServiceCountersNotIn(shopId, serviceCounters.map { it.id })
        } else {
            clearServiceCounters(shopId)
        }

        clearCounterStockForShop(shopId)
        if (counterStock.isNotEmpty()) insertCounterStock(counterStock)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Transaction
    suspend fun queueProductChange(
        product: ProductEntity,
        outbox: OutboxEntity,
        counterStock: CounterStockEntity? = null,
        log: OfflineTransactionLogEntity? = null
    ) {
        insertProduct(product)
        if (counterStock != null) insertCounterStock(listOf(counterStock))
        enqueueOutbox(outbox)
        if (log != null) insertTransactionLog(log)
    }
    @Transaction
    suspend fun queueProductChanges(
        product: ProductEntity,
        outboxItems: List<OutboxEntity>,
        counterStock: CounterStockEntity? = null,
        logs: List<OfflineTransactionLogEntity> = emptyList()
    ) {
        insertProduct(product)
        if (counterStock != null) insertCounterStock(listOf(counterStock))
        outboxItems.forEach { enqueueOutbox(it) }
        logs.forEach { insertTransactionLog(it) }
    }

    @Query("UPDATE products SET remoteId = :remoteId WHERE catalogCode = :code")
    suspend fun setProductRemoteId(code: String, remoteId: String): Int
    @Query("UPDATE counter_stock SET productId = :remoteId WHERE catalogCode = :code")
    suspend fun setCounterStockProductId(code: String, remoteId: String): Int

    @Query("SELECT * FROM products WHERE catalogCode = :code LIMIT 1")
    suspend fun getProductByCode(code: String): ProductEntity?
    @Query("SELECT * FROM products WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getProductByRemoteId(remoteId: String): ProductEntity?

    @Query("DELETE FROM products WHERE catalogCode = :code")
    suspend fun deleteProductByCode(code: String): Int

    @Query("UPDATE sales SET catalogCode = :newCode WHERE catalogCode = :oldCode")
    suspend fun migrateSaleCatalogCode(oldCode: String, newCode: String): Int

    @Query("SELECT * FROM products WHERE catalogCode = :barcode OR verifiedBarcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): ProductEntity?

    @Query("UPDATE products SET verifiedBarcode = :barcode WHERE catalogCode = :code")
    suspend fun assignBarcode(code: String, barcode: String): Int

    @Update
    suspend fun updateProduct(product: ProductEntity): Int

    @Query("UPDATE products SET stockQuantity = stockQuantity + :quantity WHERE catalogCode = :code AND inMyStock = 1")
    suspend fun receiveStock(code: String, quantity: Int): Int

    @Query("UPDATE products SET stockQuantity = :newStock, inMyStock = 1 WHERE catalogCode = :code")
    suspend fun setProductStock(code: String, newStock: Int): Int

    @Query("UPDATE products SET stockQuantity = stockQuantity - :quantity WHERE catalogCode = :code AND inMyStock = 1 AND stockQuantity >= :quantity")
    suspend fun decrementStock(code: String, quantity: Int): Int

    @Insert
    suspend fun insertSale(sale: SaleEntity): Long

    @Query("UPDATE sales SET remoteId = :remoteId WHERE id = :saleId")
    suspend fun markSaleUploaded(saleId: Long, remoteId: String?)

    @Query("UPDATE sales SET remoteId = :remoteId WHERE id = :localSaleId")
    suspend fun setSaleRemoteId(localSaleId: Long, remoteId: String): Int

    @Transaction
    suspend fun queueSale(sale: SaleEntity, outbox: OutboxEntity, stockToDecrement: Int): Long? {
        if (stockToDecrement < 0) return null
        val product = getProductByCode(sale.catalogCode) ?: return null
        if (!product.inMyStock || product.stockQuantity < stockToDecrement) return null
        if (stockToDecrement > 0 && decrementStock(sale.catalogCode, stockToDecrement) != 1) return null
        val localId = insertSale(sale)
        enqueueOutbox(outbox.copy(localSaleId = localId))
        return localId
    }

    @Transaction
    suspend fun queueSaleAtCounter(
        sale: SaleEntity,
        outbox: OutboxEntity,
        counterId: String,
        stockToDecrement: Int,
        perCounter: Boolean,
        log: OfflineTransactionLogEntity? = null
    ): Long? {
        if (stockToDecrement < 0) return null
        val product = getProductByCode(sale.catalogCode) ?: return null
        if (!product.inMyStock) return null
        if (perCounter) {
            val stock = getCounterStockQuantity(counterId, sale.catalogCode) ?: return null
            if (stock < stockToDecrement) return null
            if (stockToDecrement > 0 && decrementCounterStock(counterId, sale.catalogCode, stockToDecrement) != 1) return null
        } else {
            if (product.stockQuantity < stockToDecrement) return null
            if (stockToDecrement > 0 && decrementStock(sale.catalogCode, stockToDecrement) != 1) return null
        }
        val localId = insertSale(sale)
        enqueueOutbox(outbox.copy(localSaleId = localId))
        if (log != null) insertTransactionLog(log.copy(localSaleId = localId))
        return localId
    }

    @Transaction
    suspend fun queueDispensationAtCounter(
        outbox: OutboxEntity,
        productCode: String,
        counterId: String,
        stockToDecrement: Int,
        perCounter: Boolean,
        log: OfflineTransactionLogEntity? = null
    ): Boolean {
        if (stockToDecrement < 1) return false
        val product = getProductByCode(productCode) ?: return false
        if (!product.inMyStock) return false
        if (perCounter) {
            val stock = getCounterStockQuantity(counterId, productCode) ?: return false
            if (stock < stockToDecrement || decrementCounterStock(counterId, productCode, stockToDecrement) != 1) return false
        } else {
            if (product.stockQuantity < stockToDecrement || decrementStock(productCode, stockToDecrement) != 1) return false
        }
        enqueueOutbox(outbox)
        if (log != null) insertTransactionLog(log)
        return true
    }

    @Transaction
    suspend fun queueTableSale(order: TableOrderEntity, sale: SaleEntity, outbox: OutboxEntity, stockToDecrement: Int): Long? {
        if (stockToDecrement < 0 || getDiningTable(order.tableId) == null) return null
        val product = getProductByCode(sale.catalogCode) ?: return null
        if (!product.inMyStock || product.stockQuantity < stockToDecrement) return null
        if (stockToDecrement > 0 && decrementStock(sale.catalogCode, stockToDecrement) != 1) return null
        val localSaleId = insertSale(sale)
        if (localSaleId <= 0L) return null
        insertTableOrder(order.copy(saleId = localSaleId))
        enqueueOutbox(outbox.copy(localSaleId = localSaleId))
        return localSaleId
    }

    @Transaction
    suspend fun queueTableSaleAtCounter(
        order: TableOrderEntity,
        sale: SaleEntity,
        outbox: OutboxEntity,
        counterId: String,
        stockToDecrement: Int,
        perCounter: Boolean,
        log: OfflineTransactionLogEntity? = null
    ): Long? {
        if (stockToDecrement < 0 || getDiningTable(order.tableId) == null) return null
        val product = getProductByCode(sale.catalogCode) ?: return null
        if (!product.inMyStock) return null
        if (perCounter) {
            val stock = getCounterStockQuantity(counterId, sale.catalogCode) ?: return null
            if (stock < stockToDecrement) return null
            if (stockToDecrement > 0 && decrementCounterStock(counterId, sale.catalogCode, stockToDecrement) != 1) return null
        } else {
            if (product.stockQuantity < stockToDecrement) return null
            if (stockToDecrement > 0 && decrementStock(sale.catalogCode, stockToDecrement) != 1) return null
        }
        val localSaleId = insertSale(sale)
        if (localSaleId <= 0L) return null
        insertTableOrder(order.copy(saleId = localSaleId))
        enqueueOutbox(outbox.copy(localSaleId = localSaleId))
        if (log != null) insertTransactionLog(log.copy(localSaleId = localSaleId))
        return localSaleId
    }

    @Query("SELECT * FROM sales WHERE undone = 0 ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun getLatestActiveSale(): SaleEntity?
    @Query("SELECT * FROM sales WHERE id = :saleId LIMIT 1")
    suspend fun getSaleById(saleId: Long): SaleEntity?

    @Query("UPDATE products SET stockQuantity = stockQuantity + :quantity WHERE catalogCode = :code AND inMyStock = 1")
    suspend fun restoreStock(code: String, quantity: Int): Int

    @Query("UPDATE sales SET undone = 1 WHERE id = :saleId AND undone = 0")
    suspend fun markSaleUndone(saleId: Long): Int

    @Transaction
    suspend fun cancelUnsentSale(sale: SaleEntity, perCounter: Boolean): Boolean {
        if (sale.undone) return false
        val outbox = outboxForSale(sale.id) ?: return false
        val log = getTransactionLog(outbox.operationId)
        if (log?.status == OfflineTransactionStatus.SYNCING) return false
        val product = getProductByCode(sale.catalogCode)
        val unitsPerSale = product?.let { current ->
            parseSaleUnits(current.salesUnitsJson, current.sellingPriceKsh)
                .firstOrNull { it.name.equals(sale.unitName, ignoreCase = true) }
                ?.stockUnitsPerSale
        } ?: 1
        val restore = sale.quantity * unitsPerSale
        val restored = if (perCounter) {
            val counterId = sale.sourceCounterId ?: return false
            incrementCounterStock(counterId, sale.catalogCode, restore) == 1
        } else restoreStock(sale.catalogCode, restore) == 1
        if (!restored) return false
        cancelOutboxForSale(sale.id)
        markTransactionLogCancelledBySale(sale.id, System.currentTimeMillis())
        deleteTableOrderBySaleId(sale.id)
        return markSaleUndone(sale.id) == 1
    }

    @Transaction
    suspend fun queueUndoSale(
        sale: SaleEntity,
        outbox: OutboxEntity,
        perCounter: Boolean,
        log: OfflineTransactionLogEntity? = null
    ): Boolean {
        if (sale.undone || sale.remoteId.isNullOrBlank()) return false
        val product = getProductByCode(sale.catalogCode)
        val unitsPerSale = product?.let { current ->
            parseSaleUnits(current.salesUnitsJson, current.sellingPriceKsh)
                .firstOrNull { it.name.equals(sale.unitName, ignoreCase = true) }
                ?.stockUnitsPerSale
        } ?: 1
        val restore = sale.quantity * unitsPerSale
        val restored = if (perCounter) {
            val counterId = sale.sourceCounterId ?: return false
            incrementCounterStock(counterId, sale.catalogCode, restore) == 1
        } else restoreStock(sale.catalogCode, restore) == 1
        if (!restored) return false
        deleteTableOrderBySaleId(sale.id)
        if (markSaleUndone(sale.id) != 1) return false
        enqueueOutbox(outbox.copy(localSaleId = sale.id))
        if (log != null) insertTransactionLog(log.copy(localSaleId = sale.id))
        return true
    }

    @Query("UPDATE products SET photoOverrideUri = :uri WHERE catalogCode = :code")
    suspend fun updatePhotoOverride(code: String, uri: String): Int

    @Transaction
    suspend fun completeSaleWithUnit(
        code: String,
        quantity: Int,
        unitName: String,
        unitPriceKsh: Int,
        createdAt: Long,
        counterId: String? = null,
        counterName: String? = null,
        waiterName: String? = null,
        tableId: String? = null,
        tableName: String? = null
    ): Boolean {
        if (quantity < 1 || unitPriceKsh < 1) return false
        val product = getProductByCode(code) ?: return false
        if (!product.inMyStock) return false

        val shouldDecrement = if (unitName.equals("Bottle", ignoreCase = true) || unitName.equals("Can", ignoreCase = true)) {
            quantity
        } else {
            if (product.stockQuantity >= 1) 1 else 0
        }
        if (shouldDecrement > 0 && product.stockQuantity >= shouldDecrement) {
            decrementStock(code, shouldDecrement)
        }

        insertSale(
            SaleEntity(
                catalogCode = product.catalogCode,
                productName = product.name,
                imageAssetName = product.imageAssetName,
                unitName = unitName,
                quantity = quantity,
                unitPriceKsh = unitPriceKsh,
                totalKsh = unitPriceKsh * quantity,
                createdAt = createdAt,
                photoOverrideUri = product.photoOverrideUri,
                counterId = counterId,
                counterName = counterName,
                waiterName = waiterName,
                tableId = tableId,
                tableName = tableName
            )
        )
        return true
    }

    @Transaction
    suspend fun completeTableSaleWithUnit(
        tableId: String,
        code: String,
        quantity: Int,
        unitName: String,
        unitPriceKsh: Int,
        createdAt: Long,
        counterId: String? = null,
        counterName: String? = null,
        waiterName: String? = null,
        tableName: String? = null
    ): Boolean {
        val diningTable = getDiningTable(tableId) ?: return false
        if (quantity < 1 || unitPriceKsh < 1) return false
        val product = getProductByCode(code) ?: return false
        if (!product.inMyStock || product.stockQuantity < 1) return false
        val stockToDeduct = quantity
        if (product.stockQuantity < stockToDeduct || decrementStock(code, stockToDeduct) != 1) return false
        val total = unitPriceKsh * quantity
        val actualTableName = tableName ?: diningTable.name
        val saleId = insertSale(
            SaleEntity(
                catalogCode = product.catalogCode,
                productName = product.name,
                imageAssetName = product.imageAssetName,
                unitName = unitName,
                quantity = quantity,
                unitPriceKsh = unitPriceKsh,
                totalKsh = total,
                createdAt = createdAt,
                photoOverrideUri = product.photoOverrideUri,
                counterId = counterId,
                counterName = counterName,
                waiterName = waiterName,
                tableId = tableId,
                tableName = actualTableName
            )
        )
        if (saleId <= 0L) return false
        insertTableOrder(
            TableOrderEntity(
                tableId = tableId,
                saleId = saleId,
                catalogCode = product.catalogCode,
                productName = product.name,
                unitName = unitName,
                quantity = quantity,
                unitPriceKsh = unitPriceKsh,
                totalKsh = total,
                servedAt = createdAt,
                counterId = counterId,
                counterName = counterName,
                waiterName = waiterName
            )
        )
        return true
    }

    @Transaction
    suspend fun completeSale(code: String, quantity: Int, createdAt: Long): Boolean {
        if (quantity < 1) return false
        val product = getProductByCode(code) ?: return false
        if (!product.inMyStock || product.stockQuantity < quantity) return false
        if (decrementStock(code, quantity) != 1) return false
        insertSale(
            SaleEntity(
                catalogCode = product.catalogCode,
                productName = product.name,
                imageAssetName = product.imageAssetName,
                unitName = "Bottle",
                quantity = quantity,
                unitPriceKsh = product.sellingPriceKsh,
                totalKsh = product.sellingPriceKsh * quantity,
                createdAt = createdAt,
                photoOverrideUri = product.photoOverrideUri
            )
        )
        return true
    }

    @Transaction
    suspend fun addCatalogProduct(code: String, quantity: Int, sellingPriceKsh: Int): Boolean {
        if (quantity < 1 || sellingPriceKsh < 1) return false
        val product = getProductByCode(code) ?: return false
        if (product.inMyStock) return false
        return updateProduct(
            product.copy(
                inMyStock = true,
                stockQuantity = quantity,
                sellingPriceKsh = sellingPriceKsh
            )
        ) == 1
    }

    @Transaction
    suspend fun undoLatestSale(): Boolean {
        val sale = getLatestActiveSale() ?: return false
        val stockToRestore = sale.quantity
        if (restoreStock(sale.catalogCode, stockToRestore) != 1) return false
        deleteTableOrderBySaleId(sale.id)
        return markSaleUndone(sale.id) == 1
    }

    // ---- Butchery Package Queries & Transactions ----
    @Query("SELECT * FROM butchery_config WHERE shopId = :shopId LIMIT 1")
    fun observeButcheryConfig(shopId: String): Flow<ButcheryConfigEntity?>

    @Query("SELECT * FROM butchery_config WHERE shopId = :shopId LIMIT 1")
    suspend fun getButcheryConfig(shopId: String): ButcheryConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertButcheryConfig(config: ButcheryConfigEntity)

    @Query("SELECT * FROM butchery_cuts WHERE shopId = :shopId ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeButcheryCuts(shopId: String): Flow<List<ButcheryCutEntity>>

    @Query("SELECT * FROM butchery_cuts WHERE shopId = :shopId ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    suspend fun getButcheryCuts(shopId: String): List<ButcheryCutEntity>

    @Query("SELECT * FROM butchery_cuts WHERE id = :cutId LIMIT 1")
    suspend fun getButcheryCutById(cutId: String): ButcheryCutEntity?

    @Query("SELECT COUNT(*) FROM butchery_cuts WHERE shopId = :shopId")
    suspend fun getButcheryCutCount(shopId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertButcheryCut(cut: ButcheryCutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertButcheryCuts(cuts: List<ButcheryCutEntity>)

    @Query("UPDATE butchery_cuts SET pricePerKgKsh = :pricePerKgKsh WHERE id = :cutId")
    suspend fun updateButcheryCutPrice(cutId: String, pricePerKgKsh: Int): Int

    @Query("UPDATE butchery_cuts SET pricePerKgKsh = :pricePerKgKsh WHERE shopId = :shopId AND category = 'Beef'")
    suspend fun updateAllBeefCutPrices(shopId: String, pricePerKgKsh: Int): Int

    @Query("UPDATE butchery_cuts SET pricePerKgKsh = :pricePerKgKsh WHERE shopId = :shopId AND lower(category) = lower(:category)")
    suspend fun updateCutPricesForCategory(shopId: String, category: String, pricePerKgKsh: Int): Int

    @Query("UPDATE butchery_cuts SET stockGrams = :stockGrams WHERE id = :cutId")
    suspend fun setButcheryCutStockGrams(cutId: String, stockGrams: Int): Int

    @Query("UPDATE butchery_cuts SET stockGrams = stockGrams + :grams WHERE id = :cutId")
    suspend fun addButcheryCutStockGrams(cutId: String, grams: Int): Int

    @Query("UPDATE butchery_cuts SET active = :active WHERE id = :cutId")
    suspend fun setButcheryCutActive(cutId: String, active: Boolean): Int

    @Query("SELECT * FROM butchery_sales WHERE shopId = :shopId ORDER BY createdAt DESC, id DESC")
    fun observeButcherySales(shopId: String): Flow<List<ButcherySaleEntity>>

    @Query("SELECT * FROM butchery_sales WHERE shopId = :shopId AND undone = 0 ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun getLatestActiveButcherySale(shopId: String): ButcherySaleEntity?

    @Query("UPDATE butchery_sales SET undone = 1 WHERE id = :saleId AND undone = 0")
    suspend fun markButcherySaleUndone(saleId: Long): Int

    @Insert
    suspend fun insertButcherySale(sale: ButcherySaleEntity): Long

    @Query("UPDATE butchery_cuts SET name = 'Beef', iconKey = 'beef' WHERE shopId = :shopId AND name = 'Beef Steak'")
    suspend fun normalizeStarterBeefCut(shopId: String): Int

    @Query("DELETE FROM butchery_cuts WHERE shopId = :shopId AND id LIKE '%_beef_cubes' AND name = 'Beef Cubes' AND stockGrams = 0")
    suspend fun removeUnusedStarterBeefCubes(shopId: String): Int

    @Transaction
    suspend fun enrollButchery(config: ButcheryConfigEntity, starterCuts: List<ButcheryCutEntity>) {
        upsertButcheryConfig(config)
        if (getButcheryCutCount(config.shopId) == 0 && starterCuts.isNotEmpty()) {
            insertButcheryCuts(starterCuts)
        } else {
            normalizeStarterBeefCut(config.shopId)
            removeUnusedStarterBeefCubes(config.shopId)
        }
    }

    @Transaction
    suspend fun recordButcherySale(
        shopId: String,
        cutId: String,
        weightGrams: Int,
        totalKsh: Int,
        paymentMethod: String,
        entryMode: String,
        attendantName: String?,
        createdAt: Long
    ): Long? {
        if (weightGrams <= 0 || totalKsh <= 0) return null
        val cut = getButcheryCutById(cutId) ?: return null
        if (cut.shopId != shopId) return null
        val deducted = minOf(cut.stockGrams.coerceAtLeast(0), weightGrams)
        if (deducted > 0) {
            setButcheryCutStockGrams(cut.id, cut.stockGrams - deducted)
        }
        val trimmedPayment = paymentMethod.trim()
        val normalizedPayment = when {
            trimmedPayment.isEmpty() -> "Cash Payment"
            trimmedPayment.equals("mpesa", ignoreCase = true) -> "mpesa"
            trimmedPayment.equals("cash", ignoreCase = true) -> "cash"
            else -> trimmedPayment.take(32)
        }
        return insertButcherySale(
            ButcherySaleEntity(
                shopId = shopId,
                cutId = cut.id,
                cutName = cut.name,
                category = cut.category,
                pricePerKgKsh = cut.pricePerKgKsh,
                weightGrams = weightGrams,
                totalKsh = totalKsh,
                paymentMethod = normalizedPayment,
                entryMode = entryMode,
                stockDeductedGrams = deducted,
                attendantName = attendantName,
                createdAt = createdAt,
                undone = false
            )
        )
    }

    @Transaction
    suspend fun undoLatestButcherySale(shopId: String): Boolean {
        val latest = getLatestActiveButcherySale(shopId) ?: return false
        if (markButcherySaleUndone(latest.id) != 1) return false
        if (latest.stockDeductedGrams > 0) {
            addButcheryCutStockGrams(latest.cutId, latest.stockDeductedGrams)
        }
        return true
    }
}
