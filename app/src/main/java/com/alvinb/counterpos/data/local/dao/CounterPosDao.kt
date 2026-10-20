package com.alvinb.counterpos.data.local.dao
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.alvinb.counterpos.data.local.entity.DiningTableEntity
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.local.entity.TableOrderEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface CounterPosDao {
    @Query("SELECT * FROM dining_tables ORDER BY createdAt ASC, name COLLATE NOCASE ASC")
    fun observeDiningTables(): Flow<List<DiningTableEntity>>
    @Query("SELECT * FROM table_orders ORDER BY servedAt ASC, id ASC")
    fun observeTableOrders(): Flow<List<TableOrderEntity>>
    @Query("SELECT * FROM dining_tables WHERE id = :id LIMIT 1")
    suspend fun getDiningTable(id: String): DiningTableEntity?
    @Query("SELECT * FROM dining_tables WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getDiningTableByName(name: String): DiningTableEntity?
    @Query("SELECT COUNT(*) FROM dining_tables")
    suspend fun getDiningTableCount(): Int
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDiningTable(table: DiningTableEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiningTables(tables: List<DiningTableEntity>)
    @Query("DELETE FROM dining_tables WHERE id = :id")
    suspend fun deleteDiningTable(id: String): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTableOrder(order: TableOrderEntity): Long

    @Transaction
    suspend fun insertCommittedTableSale(sale: SaleEntity, order: TableOrderEntity) {
        insertSales(listOf(sale))
        insertTableOrder(order)
    }

    @Query("DELETE FROM table_orders WHERE saleId = :saleId")
    suspend fun deleteTableOrderBySaleId(saleId: Long): Int

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM sales ORDER BY createdAt DESC, id DESC LIMIT 200")
    fun observeSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE undone = 0 AND remoteId IS NULL ORDER BY createdAt ASC, id ASC")
    suspend fun getPendingRemoteSales(): List<SaleEntity>

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductCount(): Int

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Query("SELECT * FROM products WHERE catalogCode = :code LIMIT 1")
    suspend fun getProductByCode(code: String): ProductEntity?

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

    @Query("SELECT * FROM sales WHERE undone = 0 ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun getLatestActiveSale(): SaleEntity?

    @Query("UPDATE products SET stockQuantity = stockQuantity + :quantity WHERE catalogCode = :code AND inMyStock = 1")
    suspend fun restoreStock(code: String, quantity: Int): Int

    @Query("UPDATE sales SET undone = 1 WHERE id = :saleId AND undone = 0")
    suspend fun markSaleUndone(saleId: Long): Int

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
        val stockToDeduct = if (unitName.equals("Bottle", true) || unitName.equals("Can", true)) quantity else 1
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
        val stockToRestore = if (sale.unitName.equals("Bottle", true) || sale.unitName.equals("Can", true)) sale.quantity else 1
        if (restoreStock(sale.catalogCode, stockToRestore) != 1) return false
        deleteTableOrderBySaleId(sale.id)
        return markSaleUndone(sale.id) == 1
    }
}
