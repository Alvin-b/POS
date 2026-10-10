package com.alvinb.counterpos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterPosDao {
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM sales ORDER BY createdAt DESC, id DESC LIMIT 200")
    fun observeSales(): Flow<List<SaleEntity>>

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Query("SELECT * FROM products WHERE catalogCode = :code LIMIT 1")
    suspend fun getProductByCode(code: String): ProductEntity?

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

        // For bottle or primary unit, decrement shelf stock by quantity (if stock available).
        // If selling smaller units like cup or glass, decrement 1 bottle if quantity exceeds,
        // or decrement 1 bottle per cup/glass sold if tracking per serving, or decrement minimum 1 if bottle unit.
        val shouldDecrement = if (unitName.equals("Bottle", ignoreCase = true) || unitName.equals("Can", ignoreCase = true)) {
            quantity
        } else {
            // For cup/glass, decrement 1 unit from stock if available, or allow sales if stocked
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
                photoOverrideUri = product.photoOverrideUri
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
        restoreStock(sale.catalogCode, sale.quantity)
        return markSaleUndone(sale.id) == 1
    }
}
