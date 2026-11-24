package com.alvinb.counterpos.ui

import com.alvinb.counterpos.data.local.entity.DiningTableEntity
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.local.entity.TableOrderEntity
import com.alvinb.counterpos.data.remote.ServiceCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReportsAnalyticsTest {

    private fun makeProduct(
        code: String,
        name: String,
        category: String = "Beer",
        sellingPrice: Int = 350,
        avgCost: Int = 220,
        stockQty: Int = 20,
        threshold: Int = 5,
        salesUnitsJson: String = ""
    ) = ProductEntity(
        catalogCode = code,
        verifiedBarcode = null,
        name = name,
        brand = "Brand",
        size = "500ml",
        category = category,
        suggestedPriceKsh = sellingPrice,
        sellingPriceKsh = sellingPrice,
        stockQuantity = stockQty,
        inMyStock = true,
        lowStockThreshold = threshold,
        imageAssetName = "",
        salesUnitsJson = salesUnitsJson,
        averageCostKsh = avgCost
    )

    @Test
    fun calculatesProfitMarginPaymentSplitStockHealthAndOpenTables() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 15, 18, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterday = now - 86_400_000L

        val products = listOf(
            makeProduct(
                code = "tusker",
                name = "Tusker Lager",
                category = "Beer",
                sellingPrice = 300,
                avgCost = 200,
                stockQty = 10,
                threshold = 5,
                salesUnitsJson = """[{"name":"Bottle","priceKsh":300,"stockUnitsPerSale":1},{"name":"Crate (24)","priceKsh":6000,"stockUnitsPerSale":24}]"""
            ),
            makeProduct(
                code = "jameson",
                name = "Jameson 750ml",
                category = "Spirits",
                sellingPrice = 3500,
                avgCost = 2500,
                stockQty = 3,
                threshold = 5
            ),
            makeProduct(
                code = "water",
                name = "Still Water",
                category = "Soft Drinks",
                sellingPrice = 100,
                avgCost = 0,
                stockQty = 0,
                threshold = 4
            )
        )

        val counters = listOf(
            ServiceCounter(id = "c1", name = "Main Bar"),
            ServiceCounter(id = "c2", name = "VIP Lounge")
        )

        val sales = listOf(
            // Today: 2 bottles Tusker cash (rev 600, cost 400)
            SaleEntity(
                id = 1,
                catalogCode = "tusker",
                productName = "Tusker Lager",
                imageAssetName = "",
                unitName = "Bottle",
                quantity = 2,
                unitPriceKsh = 300,
                totalKsh = 600,
                createdAt = now - 3_600_000L,
                counterId = "c1",
                counterName = "Main Bar",
                waiterName = "alice@club.com",
                tableName = "Table 1",
                paymentMethod = "cash"
            ),
            // Today: 1 Crate (24) Tusker mpesa (rev 6000, cost 24*200 = 4800)
            SaleEntity(
                id = 2,
                catalogCode = "tusker",
                productName = "Tusker Lager",
                imageAssetName = "",
                unitName = "Crate (24)",
                quantity = 1,
                unitPriceKsh = 6000,
                totalKsh = 6000,
                createdAt = now - 1_800_000L,
                counterId = "c2",
                counterName = "VIP Lounge",
                waiterName = "bob@club.com",
                paymentMethod = "mpesa"
            ),
            // Today undone sale should be excluded
            SaleEntity(
                id = 3,
                catalogCode = "jameson",
                productName = "Jameson 750ml",
                imageAssetName = "",
                unitName = "Bottle",
                quantity = 1,
                unitPriceKsh = 3500,
                totalKsh = 3500,
                createdAt = now - 900_000L,
                undone = true,
                paymentMethod = "cash"
            ),
            // Yesterday sale: 3300 -> delta = (6600 - 3300)/3300 = +100%
            SaleEntity(
                id = 4,
                catalogCode = "jameson",
                productName = "Jameson 750ml",
                imageAssetName = "",
                unitName = "Bottle",
                quantity = 1,
                unitPriceKsh = 3300,
                totalKsh = 3300,
                createdAt = yesterday,
                counterId = "c1",
                counterName = "Main Bar",
                paymentMethod = "cash"
            )
        )

        val tables = listOf(DiningTableEntity(id = "t1", name = "VIP 1", createdAt = now))
        val orders = listOf(
            TableOrderEntity(
                id = 10,
                tableId = "t1",
                saleId = 101L,
                catalogCode = "jameson",
                productName = "Jameson 750ml",
                unitName = "Bottle",
                quantity = 1,
                unitPriceKsh = 3500,
                totalKsh = 3500,
                servedAt = now
            ),
            TableOrderEntity(
                id = 11,
                tableId = "t1",
                saleId = 102L,
                catalogCode = "tusker",
                productName = "Tusker Lager",
                unitName = "Bottle",
                quantity = 2,
                unitPriceKsh = 300,
                totalKsh = 600,
                servedAt = yesterday,
                settlementId = "settled-1"
            )
        )

        val snapshot = buildReportAnalyticsSnapshot(
            sales = sales,
            products = products,
            counters = counters,
            remoteRows = emptyList(),
            registeredWaiterEmails = setOf("alice@club.com", "bob@club.com"),
            diningTables = tables,
            tableOrders = orders,
            reportFilter = "today",
            reportPaymentFilter = "all",
            reportCounterFilter = "all",
            nowMs = now
        )

        assertEquals(6600L, snapshot.totalRevenueKsh)
        assertEquals(3300L, snapshot.previousRevenueKsh)
        assertNotNull(snapshot.revenueDeltaPercent)
        assertEquals(100.0, snapshot.revenueDeltaPercent!!, 0.1)
        assertEquals(2, snapshot.transactionCount)
        assertEquals(3, snapshot.totalUnitsSold)
        assertEquals(3300L, snapshot.averageTicketKsh)
        assertEquals(600L, snapshot.cashRevenueKsh)
        assertEquals(6000L, snapshot.mpesaRevenueKsh)
        assertEquals(5200L, snapshot.estimatedCostKsh)
        assertEquals(1400L, snapshot.estimatedProfitKsh)

        // Stock health: tusker healthy, jameson low (3 <= 5), water out (0)
        assertEquals(1, snapshot.lowStockCount)
        assertEquals(1, snapshot.outOfStockCount)
        assertEquals(10L * 300L + 3L * 3500L, snapshot.inventoryRetailValueKsh)

        // Open tables
        assertEquals(1, snapshot.activeTablesCount)
        assertEquals(3500L, snapshot.openTabsTotalKsh)

        // Category breakdown & waiters
        assertEquals(1, snapshot.categoryBreakdown.size)
        assertEquals("Beer", snapshot.categoryBreakdown.first().category)
        assertEquals(2, snapshot.waiterLeaderboard.size)
        assertTrue(snapshot.trendBuckets.isNotEmpty())
    }

    @Test
    fun counterFilterScopesRevenueAndTrendBuckets() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 15, 20, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val counters = listOf(
            ServiceCounter(id = "c1", name = "Main Bar"),
            ServiceCounter(id = "c2", name = "Pool Deck")
        )
        val sales = listOf(
            SaleEntity(id = 1, catalogCode = "a", productName = "Drink A", imageAssetName = "", quantity = 1, unitPriceKsh = 500, totalKsh = 500, createdAt = now, counterId = "c1", counterName = "Main Bar", paymentMethod = "cash"),
            SaleEntity(id = 2, catalogCode = "b", productName = "Drink B", imageAssetName = "", quantity = 2, unitPriceKsh = 750, totalKsh = 1500, createdAt = now, counterId = "c2", counterName = "Pool Deck", paymentMethod = "mpesa")
        )

        val poolOnly = buildReportAnalyticsSnapshot(
            sales = sales,
            products = emptyList(),
            counters = counters,
            remoteRows = emptyList(),
            registeredWaiterEmails = emptySet(),
            diningTables = emptyList(),
            tableOrders = emptyList(),
            reportFilter = "today",
            reportPaymentFilter = "all",
            reportCounterFilter = "c2",
            nowMs = now
        )

        assertEquals(1500L, poolOnly.totalRevenueKsh)
        assertEquals(1, poolOnly.transactionCount)
        assertEquals(0L, poolOnly.cashRevenueKsh)
        assertEquals(1500L, poolOnly.mpesaRevenueKsh)
        assertNull(poolOnly.profitMarginPercent)
    }

    @Test
    fun thirtyDayInsightsComputeDailyBarsRollingAvgMomentumAndActionableInsights() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 15, 20, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun saleDaysAgo(id: Long, daysAgo: Int, amountKsh: Int, method: String = "mpesa", undone: Boolean = false): SaleEntity {
            val ts = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, -daysAgo)
            }.timeInMillis
            return SaleEntity(
                id = id,
                catalogCode = "tusker",
                productName = "Tusker Lager",
                imageAssetName = "",
                unitName = "Bottle",
                quantity = 1,
                unitPriceKsh = amountKsh,
                totalKsh = amountKsh,
                createdAt = ts,
                undone = undone,
                counterId = "c1",
                counterName = "Main Bar",
                paymentMethod = method
            )
        }

        val sales = listOf(
            // Older than 30 days (ignored in 30d window)
            saleDaysAgo(1L, 35, 99_000),
            // Prior 7-day window (daysAgo 7..13): total = 10,000
            saleDaysAgo(2L, 10, 4_000, method = "cash"),
            saleDaysAgo(3L, 8, 6_000, method = "mpesa"),
            // Recent 7-day window (daysAgo 0..6): total = 20,000 (+100% momentum)
            saleDaysAgo(4L, 4, 8_000, method = "mpesa"),
            saleDaysAgo(5L, 1, 12_000, method = "mpesa"),
            // Undone sale (excluded)
            saleDaysAgo(6L, 0, 50_000, undone = true)
        )

        val snapshot30d = buildReportAnalyticsSnapshot(
            sales = sales,
            products = emptyList(),
            counters = listOf(ServiceCounter(id = "c1", name = "Main Bar")),
            remoteRows = emptyList(),
            registeredWaiterEmails = emptySet(),
            diningTables = emptyList(),
            tableOrders = emptyList(),
            reportFilter = "30d",
            reportPaymentFilter = "all",
            reportCounterFilter = "all",
            nowMs = now
        )

        assertEquals(30, snapshot30d.trendBuckets.size)

        val insights = snapshot30d.thirtyDayInsights
        assertEquals(30, insights.days.size)
        assertEquals(30_000L, insights.totalRevenueKsh)
        assertEquals(1_000L, insights.dailyAverageKsh)
        assertEquals(4, insights.activeDaysCount)
        assertEquals(10_000L, insights.prior7DaysRevenueKsh)
        assertEquals(20_000L, insights.last7DaysRevenueKsh)
        assertNotNull(insights.weekOverWeekDeltaPercent)
        assertEquals(100.0, insights.weekOverWeekDeltaPercent!!, 0.1)
        assertEquals(12_000L, insights.bestDay?.revenueKsh)
        assertTrue(insights.days.last().rolling7DayAvgKsh > 0L)
        assertTrue(insights.actionableInsights.any { it.title.contains("Momentum Up") && it.tone == InsightTone.POSITIVE })
        assertTrue(insights.actionableInsights.any { it.title.startsWith("Strongest Day:") })
        assertTrue(insights.actionableInsights.any { it.title.startsWith("Peak Day") })

        assertEquals("0", formatCompactKshAxis(0.0))
        assertEquals("850", formatCompactKshAxis(850.0))
        assertEquals("5K", formatCompactKshAxis(5_000.0))
        assertEquals("12.5K", formatCompactKshAxis(12_500.0))
        assertEquals("1.5M", formatCompactKshAxis(1_500_000.0))
    }
}
