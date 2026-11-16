package com.alvinb.counterpos.ui

import com.alvinb.counterpos.data.local.entity.SaleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesAndSearchLogicTest {

    private fun makeSale(
        id: Long,
        productName: String = "Tusker Lager 500ml",
        unitName: String = "Bottle",
        catalogCode: String = "TUSKER_500",
        quantity: Int = 1,
        unitPriceKsh: Int = 300,
        totalKsh: Int = quantity * unitPriceKsh,
        createdAt: Long = 1_760_000_000_000L,
        undone: Boolean = false,
        paymentMethod: String = "cash",
        sourceCounterId: String? = "c1",
        counterName: String? = null,
        waiterName: String? = "Grace",
        tableId: String? = null,
        tableName: String? = null
    ) = SaleEntity(
        id = id,
        catalogCode = catalogCode,
        productName = productName,
        imageAssetName = "",
        unitName = unitName,
        quantity = quantity,
        unitPriceKsh = unitPriceKsh,
        totalKsh = totalKsh,
        createdAt = createdAt,
        undone = undone,
        sourceCounterId = sourceCounterId,
        counterName = counterName,
        waiterName = waiterName,
        tableId = tableId,
        tableName = tableName,
        paymentMethod = paymentMethod
    )

    @Test
    fun matchesReceiptSearchMatchesProductCounterWaiterTableAndPayment() {
        val counters = mapOf("c1" to "Main Bar", "c2" to "VIP Counter")
        val tableSale = makeSale(
            id = 1L,
            productName = "Jameson Irish Whiskey 750ml",
            unitName = "Tot",
            catalogCode = "JAMESON_750",
            paymentMethod = "mpesa",
            sourceCounterId = "c2",
            waiterName = "Mercy Wanjiku",
            tableId = "t-vip-1",
            tableName = "VIP Booth 2"
        )

        assertTrue(tableSale.matchesReceiptSearch("", counters))
        assertTrue(tableSale.matchesReceiptSearch("jameson", counters))
        assertTrue(tableSale.matchesReceiptSearch("tot", counters))
        assertTrue(tableSale.matchesReceiptSearch("mpesa", counters))
        assertTrue(tableSale.matchesReceiptSearch("vip counter", counters))
        assertTrue(tableSale.matchesReceiptSearch("wanjiku", counters))
        assertTrue(tableSale.matchesReceiptSearch("vip booth", counters))
        assertTrue(tableSale.matchesReceiptSearch("table", counters))
        assertFalse(tableSale.matchesReceiptSearch("heineken", counters))

        val counterFallbackSale = makeSale(
            id = 2L,
            sourceCounterId = null,
            counterName = "Patio Bar",
            tableId = null
        )
        assertTrue(counterFallbackSale.matchesReceiptSearch("patio", counters))
        assertFalse(counterFallbackSale.matchesReceiptSearch("table", counters))
    }

    @Test
    fun computeSalesLedgerSplitsRevenueExcludesUndoneAndAppliesFilters() {
        val todayStart = 2_000_000L
        val counters = mapOf("c1" to "Main Bar", "c2" to "Rooftop")

        val sales = listOf(
            // Yesterday sale (excluded on "today")
            makeSale(id = 1L, productName = "Guinness", quantity = 2, unitPriceKsh = 350, createdAt = todayStart - 1_000L, paymentMethod = "cash"),
            // Today Cash
            makeSale(id = 2L, productName = "Tusker", quantity = 2, unitPriceKsh = 300, createdAt = todayStart + 1_000L, paymentMethod = "cash", sourceCounterId = "c1"),
            // Today M-Pesa
            makeSale(id = 3L, productName = "Gilbeys Gin", quantity = 1, unitPriceKsh = 1800, createdAt = todayStart + 5_000L, paymentMethod = "mpesa", sourceCounterId = "c2"),
            // Today Undone Cash (kept in receipt list, excluded from revenue/units)
            makeSale(id = 4L, productName = "Smirnoff Ice", quantity = 3, unitPriceKsh = 300, createdAt = todayStart + 9_000L, undone = true, paymentMethod = "cash", sourceCounterId = "c1")
        )

        val todayAll = computeSalesLedger(
            sales = sales,
            countersById = counters,
            dateFilter = "today",
            paymentFilter = "all",
            newestFirst = true,
            searchQuery = "",
            todayStartMs = todayStart
        )
        assertEquals(listOf(4L, 3L, 2L), todayAll.filteredSales.map { it.id })
        assertEquals(2, todayAll.activeSalesCount)
        assertEquals(2400L, todayAll.grossRevenueKsh)
        assertEquals(3, todayAll.unitsSold)
        assertEquals(600L, todayAll.cashRevenueKsh)
        assertEquals(1800L, todayAll.mpesaRevenueKsh)
        assertEquals(1200L, todayAll.averageTicketKsh)

        val oldestMpesaAllDates = computeSalesLedger(
            sales = sales,
            countersById = counters,
            dateFilter = "all",
            paymentFilter = "mpesa",
            newestFirst = false,
            searchQuery = "rooftop",
            todayStartMs = todayStart
        )
        assertEquals(listOf(3L), oldestMpesaAllDates.filteredSales.map { it.id })
        assertEquals(1800L, oldestMpesaAllDates.mpesaRevenueKsh)
        assertEquals(0L, oldestMpesaAllDates.cashRevenueKsh)
    }
}
