package com.alvinb.counterpos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvinb.counterpos.data.local.AppDatabase
import com.alvinb.counterpos.data.local.entity.DiningTableEntity
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.TableOrderEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TableOrdersDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun tableSaleCreatesReceiptLineAndDeductsStock() = runBlocking {
        val dao = database.counterPosDao()
        dao.insertDiningTable(DiningTableEntity("table-1", "Patio A", 100L))
        dao.insertProduct(
            ProductEntity(
                catalogCode = "KE-001",
                verifiedBarcode = null,
                name = "Local Spirit",
                brand = "Local",
                size = "750 ml",
                category = "Spirit",
                suggestedPriceKsh = 500,
                sellingPriceKsh = 500,
                stockQuantity = 6,
                inMyStock = true,
                lowStockThreshold = 2,
                imageAssetName = "product_stock_001"
            )
        )

        val added = dao.completeTableSaleWithUnit("table-1", "KE-001", 2, "Glass", 80, 200L)

        assertTrue(added)
        assertEquals(4, dao.getProductByCode("KE-001")?.stockQuantity)
        val orders = dao.observeTableOrders().first()
        assertEquals(1, orders.size)
        assertEquals("Patio A", dao.getDiningTable(orders.single().tableId)?.name)
        assertEquals("Local Spirit", orders.single().productName)
        assertEquals("Glass", orders.single().unitName)
        assertEquals(2, orders.single().quantity)
        assertEquals(160, orders.single().totalKsh)
        val sales = dao.observeSales().first()
        assertEquals(1, sales.size)
        assertEquals(orders.single().saleId, sales.single().id)

        assertTrue(dao.undoLatestSale())
        assertEquals(6, dao.getProductByCode("KE-001")?.stockQuantity)
        assertTrue(dao.observeTableOrders().first().isEmpty())
        assertTrue(dao.observeSales().first().single().undone)
    }

    @Test
    fun failedTableSaleDoesNotCreateReceiptLineOrSale() = runBlocking {
        val dao = database.counterPosDao()
        dao.insertDiningTable(DiningTableEntity("table-2", "Window", 100L))
        dao.insertProduct(
            ProductEntity(
                catalogCode = "KE-002",
                verifiedBarcode = null,
                name = "Out of stock",
                brand = "",
                size = "",
                category = "Spirit",
                suggestedPriceKsh = 0,
                sellingPriceKsh = 100,
                stockQuantity = 0,
                inMyStock = true,
                lowStockThreshold = 2,
                imageAssetName = "product_stock_001"
            )
        )

        assertFalse(dao.completeTableSaleWithUnit("table-2", "KE-002", 1, "Bottle", 100, 200L))
        assertTrue(dao.observeTableOrders().first().isEmpty())
        assertTrue(dao.observeSales().first().isEmpty())
    }

    @Test
    fun settledBillLinesRemainInHistoryButAreExcludedFromOpenBill() = runBlocking {
        val dao = database.counterPosDao()
        dao.insertDiningTable(DiningTableEntity("table-paid", "Garden", 100L))
        dao.insertTableOrder(TableOrderEntity(
            tableId = "table-paid", saleId = 10L, catalogCode = "KE-010",
            productName = "Soda", unitName = "Bottle", quantity = 2,
            unitPriceKsh = 100, totalKsh = 200, servedAt = 110L
        ))
        dao.insertTableOrder(TableOrderEntity(
            tableId = "table-paid", saleId = 11L, catalogCode = "KE-011",
            productName = "Water", unitName = "Bottle", quantity = 1,
            unitPriceKsh = 50, totalKsh = 50, servedAt = 120L
        ))

        assertEquals(2, dao.openTableOrderCount("table-paid"))
        assertEquals(250L, dao.openTableBillTotal("table-paid"))
        assertEquals(2, dao.markTableOrdersSettled("table-paid", "settlement-1"))
        assertEquals(0, dao.openTableOrderCount("table-paid"))
        assertEquals(0L, dao.openTableBillTotal("table-paid"))
        val history = dao.observeTableOrders().first()
        assertEquals(2, history.size)
        assertTrue(history.all { it.settlementId == "settlement-1" })
    }

    @Test
    fun businessSnapshotReplacementDoesNotEmitEmptyProductsTablesOrOrders() = runBlocking {
        val dao = database.counterPosDao()
        dao.insertProduct(
            ProductEntity(
                catalogCode = "OLD-001", verifiedBarcode = null, name = "Old product", brand = "", size = "",
                category = "Beverage", suggestedPriceKsh = 100, sellingPriceKsh = 100, stockQuantity = 5,
                inMyStock = true, lowStockThreshold = 2, imageAssetName = "product_stock_001"
            )
        )
        dao.insertDiningTable(DiningTableEntity("old-table", "Old table", 100L))
        dao.insertTableOrder(
            TableOrderEntity(
                tableId = "old-table", saleId = 1L, catalogCode = "OLD-001", productName = "Old product",
                unitName = "Bottle", quantity = 1, unitPriceKsh = 100, totalKsh = 100, servedAt = 100L
            )
        )

        val nextProducts = async { dao.observeProducts().drop(1).first() }
        val nextTables = async { dao.observeDiningTables().drop(1).first() }
        val nextOrders = async { dao.observeTableOrders().drop(1).first() }
        delay(100)

        dao.replaceBusinessSnapshot(
            shopId = "shop-1",
            products = listOf(
                ProductEntity(
                    catalogCode = "NEW-001", verifiedBarcode = null, name = "New product", brand = "", size = "",
                    category = "Beverage", suggestedPriceKsh = 200, sellingPriceKsh = 200, stockQuantity = 7,
                    inMyStock = true, lowStockThreshold = 2, imageAssetName = "product_stock_001"
                )
            ),
            sales = emptyList(),
            tables = listOf(DiningTableEntity("new-table", "New table", 200L)),
            orders = listOf(
                TableOrderEntity(
                    tableId = "new-table", saleId = 2L, catalogCode = "NEW-001", productName = "New product",
                    unitName = "Bottle", quantity = 1, unitPriceKsh = 200, totalKsh = 200, servedAt = 200L
                )
            ),
            serviceCounters = emptyList(),
            counterStock = emptyList(),
            preservePendingSales = false
        )

        assertEquals(listOf("New product"), nextProducts.await().map { it.name })
        assertEquals(listOf("New table"), nextTables.await().map { it.name })
        assertEquals(listOf("New product"), nextOrders.await().map { it.productName })
    }
}
