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
import com.alvinb.counterpos.data.local.entity.parseSaleUnits
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
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDiningTable(table: DiningTableEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiningTables(tables: List<DiningTableEntity>)
    @Query("DELETE FROM dining_tables WHERE id = :id")
    suspend fun deleteDiningTable(id: String): Int
    @Insert
    suspend fun insertTableOrder(order: TableOrderEntity): Long

    @Query("DELETE FROM table_orders WHERE saleId = :saleId")
    suspend fun deleteTableOrderBySaleId(saleId: Long): Int

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM sales ORDER BY createdAt DESC, id DESC LIMIT 200")
    fun observeSales(): Flow<List<SaleEntity>>

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
        createdAt: Long
    ): Boolean {
        if (quantity < 1 || unitPriceKsh < 1) return false
        val product = getProductByCode(code) ?: return false
        if (!product.inMyStock) return false

        val unit = parseSaleUnits(product.salesUnitsJson, product.sellingPriceKsh)
            .firstOrNull { it.name.equals(unitName, ignoreCase = true) }
        val stockUnitsPerSale = unit?.stockUnitsPerSale?.coerceAtLeast(1) ?: 1
        val stockToDeduct = stockUnitsPerSale * quantity
        // Never record a sale without reserving all required stock. Legacy units default to one
        // stock unit per sale, preventing glass/cup sales from silently overselling inventory.
        if (product.stockQuantity < stockToDeduct || decrementStock(code, stockToDeduct) != 1) return false

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
                photoOverrideUri = product.photoOverrideUri
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
        createdAt: Long
    ): Boolean {
        if (getDiningTable(tableId) == null || quantity < 1 || unitPriceKsh < 1) return false
        val product = getProductByCode(code) ?: return false
        if (!product.inMyStock || product.stockQuantity < 1) return false
        val unit = parseSaleUnits(product.salesUnitsJson, product.sellingPriceKsh)
            .firstOrNull { it.name.equals(unitName, ignoreCase = true) }
        val stockToDeduct = (unit?.stockUnitsPerSale?.coerceAtLeast(1) ?: 1) * quantity
        if (product.stockQuantity < stockToDeduct || decrementStock(code, stockToDeduct) != 1) return false
        val total = unitPriceKsh * quantity
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
                photoOverrideUri = product.photoOverrideUri
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
                servedAt = createdAt
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
        val product = getProductByCode(sale.catalogCode) ?: return false
        val unit = parseSaleUnits(product.salesUnitsJson, product.sellingPriceKsh)
            .firstOrNull { it.name.equals(sale.unitName, ignoreCase = true) }
        val stockToRestore = (unit?.stockUnitsPerSale?.coerceAtLeast(1) ?: 1) * sale.quantity
        if (restoreStock(sale.catalogCode, stockToRestore) != 1) return false
        deleteTableOrderBySaleId(sale.id)
        return markSaleUndone(sale.id) == 1
    }
}
