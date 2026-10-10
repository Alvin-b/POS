package com.alvinb.counterpos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterPosDao {
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM sales ORDER BY createdAt DESC, id DESC LIMIT 200")
    fun observeSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM products WHERE catalogCode = :code LIMIT 1")
    suspend fun getProductByCode(code: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceProducts(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceSales(sales: List<SaleEntity>)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()

    @Query("DELETE FROM sales")
    suspend fun deleteAllSales()

    @Transaction
    suspend fun replaceRemoteSnapshot(products: List<ProductEntity>, sales: List<SaleEntity>) {
        deleteAllProducts()
        deleteAllSales()
        if (products.isNotEmpty()) replaceProducts(products)
        if (sales.isNotEmpty()) replaceSales(sales)
    }
}
