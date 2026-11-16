package com.alvinb.counterpos.ui

import com.alvinb.counterpos.data.local.entity.DiningTableEntity
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.local.entity.TableOrderEntity
import com.alvinb.counterpos.data.local.entity.resolveSaleUnits
import com.alvinb.counterpos.data.remote.CounterReportRow
import com.alvinb.counterpos.data.remote.ServiceCounter
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

internal data class CounterProductSummary(
    val catalogCode: String = "",
    val productName: String,
    val unitName: String,
    val category: String = "General",
    val totalQuantity: Int,
    val totalKsh: Long,
    val estimatedCostKsh: Long = 0L,
    val hasCostData: Boolean = false
) {
    val estimatedProfitKsh: Long get() = totalKsh - estimatedCostKsh
    val marginPercent: Double?
        get() = if (hasCostData && totalKsh > 0L) {
            (estimatedProfitKsh.toDouble() / totalKsh.toDouble()) * 100.0
        } else null
}

internal data class CounterWaiterSummary(
    val waiterName: String,
    val itemsServed: Int,
    val totalKsh: Long,
    val salesCount: Int = 0,
    val tablesServed: List<String>
)

internal data class ServiceCounterReportData(
    val counterId: String,
    val counterName: String,
    val totalItems: Int,
    val totalKsh: Long,
    val cashKsh: Long = 0L,
    val mpesaKsh: Long = 0L,
    val salesCount: Int,
    val products: List<CounterProductSummary>,
    val waiters: List<CounterWaiterSummary>
)

internal data class ReportTrendBucket(
    val label: String,
    val detailLabel: String,
    val revenueKsh: Long,
    val salesCount: Int,
    val unitsSold: Int
)

internal data class ReportCategorySummary(
    val category: String,
    val revenueKsh: Long,
    val unitsSold: Int,
    val salesCount: Int,
    val sharePercent: Double
)

internal data class ReportAnalyticsSnapshot(
    val periodLabel: String,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val filteredSales: List<SaleEntity>,
    val totalRevenueKsh: Long,
    val previousRevenueKsh: Long?,
    val revenueDeltaPercent: Double?,
    val totalUnitsSold: Int,
    val transactionCount: Int,
    val averageTicketKsh: Long,
    val cashRevenueKsh: Long,
    val cashCount: Int,
    val mpesaRevenueKsh: Long,
    val mpesaCount: Int,
    val otherRevenueKsh: Long,
    val otherCount: Int,
    val estimatedCostKsh: Long,
    val estimatedProfitKsh: Long,
    val profitMarginPercent: Double?,
    val costTrackedRevenueKsh: Long,
    val peakBucketLabel: String?,
    val peakBucketRevenueKsh: Long,
    val inventoryRetailValueKsh: Long,
    val inventoryCostValueKsh: Long,
    val lowStockCount: Int,
    val outOfStockCount: Int,
    val activeTablesCount: Int,
    val openTabsTotalKsh: Long,
    val trendBuckets: List<ReportTrendBucket>,
    val categoryBreakdown: List<ReportCategorySummary>,
    val topProducts: List<CounterProductSummary>,
    val counterReports: List<ServiceCounterReportData>,
    val waiterLeaderboard: List<CounterWaiterSummary>
)

internal fun formatKshLong(value: Long): String =
    "KSh " + NumberFormat.getIntegerInstance(Locale.US).format(value)

internal fun startOfReportDayMs(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

internal fun endOfReportDayMs(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 999)
}.timeInMillis

internal fun startOfReportDayMs(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
    set(year, month, day, 0, 0, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

internal fun endOfReportDayMs(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
    set(year, month, day, 23, 59, 59)
    set(Calendar.MILLISECOND, 999)
}.timeInMillis

internal fun formatReportDateIso(time: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(time))

internal fun formatReportDateFriendly(time: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(time))

internal fun resolveReportTimeWindow(
    reportFilter: String,
    nowMs: Long,
    sales: List<SaleEntity>
): Triple<Long, Long, String> {
    val activeSales = sales.filterNot { it.undone }
    return when (reportFilter) {
        "today" -> {
            val start = startOfReportDayMs(nowMs)
            val end = endOfReportDayMs(nowMs)
            Triple(start, end, "Today · ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(nowMs))}")
        }
        "7d" -> {
            val cal = Calendar.getInstance().apply {
                timeInMillis = startOfReportDayMs(nowMs)
                add(Calendar.DAY_OF_YEAR, -6)
            }
            val start = cal.timeInMillis
            val end = endOfReportDayMs(nowMs)
            Triple(start, end, "Last 7 Days")
        }
        "30d" -> {
            val cal = Calendar.getInstance().apply {
                timeInMillis = startOfReportDayMs(nowMs)
                add(Calendar.DAY_OF_YEAR, -29)
            }
            val start = cal.timeInMillis
            val end = endOfReportDayMs(nowMs)
            Triple(start, end, "Last 30 Days")
        }
        else -> {
            val start = activeSales.minOfOrNull { it.createdAt }?.let(::startOfReportDayMs)
                ?: startOfReportDayMs(nowMs)
            val end = endOfReportDayMs(nowMs)
            Triple(start, end, "All Time")
        }
    }
}

private fun resolvePreviousTimeWindow(reportFilter: String, currentStartMs: Long, nowMs: Long): Pair<Long, Long>? {
    return when (reportFilter) {
        "today" -> {
            val prevStart = Calendar.getInstance().apply {
                timeInMillis = currentStartMs
                add(Calendar.DAY_OF_YEAR, -1)
            }.timeInMillis
            val prevEnd = endOfReportDayMs(prevStart)
            prevStart to prevEnd
        }
        "7d" -> {
            val prevEnd = currentStartMs - 1L
            val prevStart = Calendar.getInstance().apply {
                timeInMillis = currentStartMs
                add(Calendar.DAY_OF_YEAR, -7)
            }.timeInMillis
            prevStart to prevEnd
        }
        "30d" -> {
            val prevEnd = currentStartMs - 1L
            val prevStart = Calendar.getInstance().apply {
                timeInMillis = currentStartMs
                add(Calendar.DAY_OF_YEAR, -30)
            }.timeInMillis
            prevStart to prevEnd
        }
        else -> null
    }
}

internal fun SaleEntity.matchesServiceCounter(
    counter: ServiceCounter,
    totalCounters: Int
): Boolean =
    counterId == counter.id ||
        counterName.equals(counter.name, ignoreCase = true) ||
        (totalCounters <= 1 && (counterId.isNullOrBlank() || counterId == "unassigned"))

internal fun buildReportAnalyticsSnapshot(
    sales: List<SaleEntity>,
    products: List<ProductEntity>,
    counters: List<ServiceCounter>,
    remoteRows: List<CounterReportRow>,
    registeredWaiterEmails: Set<String>,
    diningTables: List<DiningTableEntity>,
    tableOrders: List<TableOrderEntity>,
    reportFilter: String,
    reportPaymentFilter: String,
    reportCounterFilter: String,
    nowMs: Long = System.currentTimeMillis()
): ReportAnalyticsSnapshot {
    val (windowStartMs, windowEndMs, periodLabel) = resolveReportTimeWindow(reportFilter, nowMs, sales)
    val productsByCode = products.associateBy { it.catalogCode }
    val productsByName = products.associateBy { it.name.lowercase(Locale.ROOT) }
    val selectedCounter = if (reportCounterFilter == "all") null else counters.firstOrNull { it.id == reportCounterFilter }

    fun SaleEntity.matchesFilters(start: Long, end: Long): Boolean {
        if (undone) return false
        if (createdAt !in start..end) return false
        if (reportPaymentFilter in setOf("cash", "mpesa") &&
            !paymentMethod.equals(reportPaymentFilter, ignoreCase = true)
        ) {
            return false
        }
        if (selectedCounter != null && !matchesServiceCounter(selectedCounter, counters.size)) {
            return false
        }
        return true
    }

    val filteredSales = sales.filter { it.matchesFilters(windowStartMs, windowEndMs) }

    val previousWindow = resolvePreviousTimeWindow(reportFilter, windowStartMs, nowMs)
    val previousRevenueKsh = previousWindow?.let { (prevStart, prevEnd) ->
        sales.filter { it.matchesFilters(prevStart, prevEnd) }.sumOf { it.totalKsh.toLong() }
    }

    // Counter reports evaluate all counters for the selected period & payment filter
    val periodPaymentSales = sales.filter { sale ->
        !sale.undone &&
            sale.createdAt in windowStartMs..windowEndMs &&
            (reportPaymentFilter !in setOf("cash", "mpesa") ||
                sale.paymentMethod.equals(reportPaymentFilter, ignoreCase = true))
    }

    fun computeSaleCost(sale: SaleEntity): Pair<Long, Boolean> {
        val product = productsByCode[sale.catalogCode]
            ?: productsByName[sale.productName.lowercase(Locale.ROOT)]
            ?: return 0L to false
        if (product.averageCostKsh <= 0) return 0L to false
        val units = resolveSaleUnits(product)
        val matchedUnit = units.firstOrNull { it.name.equals(sale.unitName, ignoreCase = true) }
        val stockMultiplier = when {
            matchedUnit != null && matchedUnit.stockUnitsPerSale == 0 -> return 0L to false
            matchedUnit != null && matchedUnit.stockUnitsPerSale > 0 -> matchedUnit.stockUnitsPerSale
            else -> 1
        }
        return (product.averageCostKsh.toLong() * stockMultiplier * sale.quantity) to true
    }

    val counterReports = counters.map { counter ->
        val matchingSales = periodPaymentSales.filter { it.matchesServiceCounter(counter, counters.size) }
        val productsMap = matchingSales
            .groupBy { Triple(it.catalogCode, it.productName, it.unitName) }
            .map { (key, group) ->
                val product = productsByCode[key.first] ?: productsByName[key.second.lowercase(Locale.ROOT)]
                var groupCost = 0L
                var anyCost = false
                group.forEach { s ->
                    val (c, tracked) = computeSaleCost(s)
                    if (tracked) {
                        groupCost += c
                        anyCost = true
                    }
                }
                CounterProductSummary(
                    catalogCode = key.first,
                    productName = key.second,
                    unitName = key.third,
                    category = product?.category?.ifBlank { "General" } ?: "General",
                    totalQuantity = group.sumOf { it.quantity },
                    totalKsh = group.sumOf { it.totalKsh.toLong() },
                    estimatedCostKsh = groupCost,
                    hasCostData = anyCost
                )
            }
            .sortedByDescending { it.totalKsh }

        val waitersMap = matchingSales
            .filter { sale ->
                val waiter = sale.waiterName?.trim()
                if (waiter.isNullOrBlank()) false
                else if (registeredWaiterEmails.isEmpty()) true
                else {
                    val low = waiter.lowercase(Locale.ROOT)
                    low in registeredWaiterEmails || registeredWaiterEmails.any { low.contains(it) || it.contains(low) }
                }
            }
            .groupBy { it.waiterName!!.trim() }
            .map { (waiter, group) ->
                CounterWaiterSummary(
                    waiterName = waiter,
                    itemsServed = group.sumOf { it.quantity },
                    totalKsh = group.sumOf { it.totalKsh.toLong() },
                    salesCount = group.size,
                    tablesServed = group.mapNotNull { it.tableName?.takeIf(String::isNotBlank) }.distinct()
                )
            }
            .sortedByDescending { it.totalKsh }

        val localItems = matchingSales.sumOf { it.quantity }
        val localKsh = matchingSales.sumOf { it.totalKsh.toLong() }
        val localSalesCount = matchingSales.size
        val localCash = matchingSales.filter { it.paymentMethod.equals("cash", true) }.sumOf { it.totalKsh.toLong() }
        val localMpesa = matchingSales.filter { it.paymentMethod.equals("mpesa", true) }.sumOf { it.totalKsh.toLong() }

        val remoteMatch = if (reportFilter == "today" && reportPaymentFilter == "all") {
            remoteRows.firstOrNull {
                it.counterId == counter.id || it.counterName.equals(counter.name, ignoreCase = true)
            }
        } else null

        ServiceCounterReportData(
            counterId = counter.id,
            counterName = if (counters.size == 1) "Main Counter" else counter.name,
            totalItems = if (localItems > 0) localItems else (remoteMatch?.units?.toInt() ?: 0),
            totalKsh = if (localKsh > 0L) localKsh else (remoteMatch?.revenueKsh ?: 0L),
            cashKsh = localCash,
            mpesaKsh = localMpesa,
            salesCount = if (localSalesCount > 0) localSalesCount else (remoteMatch?.saleCount?.toInt() ?: 0),
            products = productsMap,
            waiters = waitersMap
        )
    }

    // Top products leaderboard computed from filteredSales (or remote fallback when empty)
    val topProducts = filteredSales
        .groupBy { Triple(it.catalogCode, it.productName, it.unitName) }
        .map { (key, group) ->
            val product = productsByCode[key.first] ?: productsByName[key.second.lowercase(Locale.ROOT)]
            var cost = 0L
            var hasCost = false
            group.forEach { s ->
                val (c, tracked) = computeSaleCost(s)
                if (tracked) {
                    cost += c
                    hasCost = true
                }
            }
            CounterProductSummary(
                catalogCode = key.first,
                productName = key.second,
                unitName = key.third,
                category = product?.category?.ifBlank { "General" } ?: "General",
                totalQuantity = group.sumOf { it.quantity },
                totalKsh = group.sumOf { it.totalKsh.toLong() },
                estimatedCostKsh = cost,
                hasCostData = hasCost
            )
        }
        .sortedByDescending { it.totalKsh }

    val waiterLeaderboard = filteredSales
        .filter { !it.waiterName.isNullOrBlank() }
        .groupBy { it.waiterName!!.trim() }
        .map { (waiter, group) ->
            CounterWaiterSummary(
                waiterName = waiter,
                itemsServed = group.sumOf { it.quantity },
                totalKsh = group.sumOf { it.totalKsh.toLong() },
                salesCount = group.size,
                tablesServed = group.mapNotNull { it.tableName?.takeIf(String::isNotBlank) }.distinct()
            )
        }
        .sortedByDescending { it.totalKsh }

    val cashSales = filteredSales.filter { it.paymentMethod.equals("cash", true) }
    val mpesaSales = filteredSales.filter { it.paymentMethod.equals("mpesa", true) }
    val otherSales = filteredSales.filter {
        !it.paymentMethod.equals("cash", true) && !it.paymentMethod.equals("mpesa", true)
    }

    val cashRevenueKsh = cashSales.sumOf { it.totalKsh.toLong() }
    val mpesaRevenueKsh = mpesaSales.sumOf { it.totalKsh.toLong() }
    val otherRevenueKsh = otherSales.sumOf { it.totalKsh.toLong() }

    val localRevenue = filteredSales.sumOf { it.totalKsh.toLong() }
    val localUnits = filteredSales.sumOf { it.quantity }
    val localCount = filteredSales.size

    val visibleCounterReports = if (reportCounterFilter == "all") {
        counterReports
    } else {
        counterReports.filter { it.counterId == reportCounterFilter }
    }

    val totalRevenueKsh = if (localRevenue > 0L) localRevenue else visibleCounterReports.sumOf { it.totalKsh }
    val totalUnitsSold = if (localUnits > 0) localUnits else visibleCounterReports.sumOf { it.totalItems }
    val transactionCount = if (localCount > 0) localCount else visibleCounterReports.sumOf { it.salesCount }
    val averageTicketKsh = if (transactionCount > 0) (totalRevenueKsh / transactionCount) else 0L

    val revenueDeltaPercent = if (previousRevenueKsh != null && previousRevenueKsh > 0L) {
        ((totalRevenueKsh - previousRevenueKsh).toDouble() / previousRevenueKsh.toDouble()) * 100.0
    } else null

    var estimatedCostKsh = 0L
    var costTrackedRevenueKsh = 0L
    filteredSales.forEach { sale ->
        val (cost, tracked) = computeSaleCost(sale)
        if (tracked) {
            estimatedCostKsh += cost
            costTrackedRevenueKsh += sale.totalKsh.toLong()
        }
    }
    val estimatedProfitKsh = costTrackedRevenueKsh - estimatedCostKsh
    val profitMarginPercent = if (costTrackedRevenueKsh > 0L) {
        (estimatedProfitKsh.toDouble() / costTrackedRevenueKsh.toDouble()) * 100.0
    } else null

    // Category Breakdown
    val categoryGroups = filteredSales.groupBy { sale ->
        val product = productsByCode[sale.catalogCode] ?: productsByName[sale.productName.lowercase(Locale.ROOT)]
        product?.category?.trim()?.ifBlank { "General" } ?: "General"
    }
    val categoryBreakdown = categoryGroups.map { (category, group) ->
        val rev = group.sumOf { it.totalKsh.toLong() }
        ReportCategorySummary(
            category = category,
            revenueKsh = rev,
            unitsSold = group.sumOf { it.quantity },
            salesCount = group.size,
            sharePercent = if (totalRevenueKsh > 0L) (rev.toDouble() / totalRevenueKsh.toDouble()) * 100.0 else 0.0
        )
    }.sortedByDescending { it.revenueKsh }

    // Trend buckets (Hourly for Today, Daily for 7D / 30D / All)
    val trendBuckets = buildTrendBuckets(
        reportFilter = reportFilter,
        filteredSales = filteredSales,
        windowStartMs = windowStartMs,
        nowMs = nowMs
    )
    val peakBucket = trendBuckets.maxByOrNull { it.revenueKsh }?.takeIf { it.revenueKsh > 0L }

    // Live inventory valuation & health
    val stockedProducts = products.filter { it.inMyStock }
    var inventoryRetailValueKsh = 0L
    var inventoryCostValueKsh = 0L
    var lowStockCount = 0
    var outOfStockCount = 0
    stockedProducts.forEach { product ->
        val units = resolveSaleUnits(product)
        val isBulk = units.isNotEmpty() && units.all { it.stockUnitsPerSale == 0 }
        if (!isBulk && product.stockQuantity > 0) {
            val retailPrice = if (product.sellingPriceKsh > 0) product.sellingPriceKsh else product.suggestedPriceKsh
            inventoryRetailValueKsh += product.stockQuantity.toLong() * retailPrice.toLong()
            if (product.averageCostKsh > 0) {
                inventoryCostValueKsh += product.stockQuantity.toLong() * product.averageCostKsh.toLong()
            }
        }
        if (!isBulk) {
            val threshold = product.lowStockThreshold.coerceAtLeast(1)
            when {
                product.stockQuantity <= 0 -> outOfStockCount++
                product.stockQuantity <= threshold -> lowStockCount++
            }
        }
    }

    val openTableOrders = tableOrders.filter { it.settlementId == null && it.tableId.isNotBlank() }
    val activeTableIds = openTableOrders.map { it.tableId }.toSet()
    val activeTablesCount = diningTables.count { it.id in activeTableIds }
        .let { if (it == 0 && activeTableIds.isNotEmpty()) activeTableIds.size else it }
    val openTabsTotalKsh = openTableOrders.sumOf { it.totalKsh.toLong() }

    return ReportAnalyticsSnapshot(
        periodLabel = periodLabel,
        windowStartMs = windowStartMs,
        windowEndMs = windowEndMs,
        filteredSales = filteredSales,
        totalRevenueKsh = totalRevenueKsh,
        previousRevenueKsh = previousRevenueKsh,
        revenueDeltaPercent = revenueDeltaPercent,
        totalUnitsSold = totalUnitsSold,
        transactionCount = transactionCount,
        averageTicketKsh = averageTicketKsh,
        cashRevenueKsh = cashRevenueKsh,
        cashCount = cashSales.size,
        mpesaRevenueKsh = mpesaRevenueKsh,
        mpesaCount = mpesaSales.size,
        otherRevenueKsh = otherRevenueKsh,
        otherCount = otherSales.size,
        estimatedCostKsh = estimatedCostKsh,
        estimatedProfitKsh = estimatedProfitKsh,
        profitMarginPercent = profitMarginPercent,
        costTrackedRevenueKsh = costTrackedRevenueKsh,
        peakBucketLabel = peakBucket?.detailLabel,
        peakBucketRevenueKsh = peakBucket?.revenueKsh ?: 0L,
        inventoryRetailValueKsh = inventoryRetailValueKsh,
        inventoryCostValueKsh = inventoryCostValueKsh,
        lowStockCount = lowStockCount,
        outOfStockCount = outOfStockCount,
        activeTablesCount = activeTablesCount,
        openTabsTotalKsh = openTabsTotalKsh,
        trendBuckets = trendBuckets,
        categoryBreakdown = categoryBreakdown,
        topProducts = topProducts,
        counterReports = counterReports,
        waiterLeaderboard = waiterLeaderboard
    )
}

private fun buildTrendBuckets(
    reportFilter: String,
    filteredSales: List<SaleEntity>,
    windowStartMs: Long,
    nowMs: Long
): List<ReportTrendBucket> {
    if (reportFilter == "today") {
        val byHour = filteredSales.groupBy { sale ->
            Calendar.getInstance().apply { timeInMillis = sale.createdAt }.get(Calendar.HOUR_OF_DAY)
        }
        val activeHours = byHour.keys
        val currentHour = Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.HOUR_OF_DAY)
        val minHour = (activeHours.minOrNull() ?: 8).coerceAtMost((currentHour - 5).coerceAtLeast(0))
        val maxHour = (activeHours.maxOrNull() ?: currentHour).coerceAtLeast(currentHour)
        return (minHour..maxHour).map { hour ->
            val group = byHour[hour].orEmpty()
            val label = when {
                hour == 0 -> "12a"
                hour < 12 -> "${hour}a"
                hour == 12 -> "12p"
                else -> "${hour - 12}p"
            }
            val detail = when {
                hour == 0 -> "12:00 AM"
                hour < 12 -> "$hour:00 AM"
                hour == 12 -> "12:00 PM"
                else -> "${hour - 12}:00 PM"
            }
            ReportTrendBucket(
                label = label,
                detailLabel = detail,
                revenueKsh = group.sumOf { it.totalKsh.toLong() },
                salesCount = group.size,
                unitsSold = group.sumOf { it.quantity }
            )
        }
    }

    val dayFormatShort = SimpleDateFormat("dd", Locale.getDefault())
    val dayFormatDetail = SimpleDateFormat("dd MMM", Locale.getDefault())
    val dayFormat7d = SimpleDateFormat("EEE", Locale.getDefault())

    val daysCount = when (reportFilter) {
        "7d" -> 7
        "30d" -> 14 // group last 14 days cleanly or show last 14 days for crisp legibility on mobile
        else -> {
            val spanDays = (((endOfReportDayMs(nowMs) - windowStartMs) / 86_400_000L) + 1L).toInt()
            spanDays.coerceIn(7, 14)
        }
    }

    val endDayStart = startOfReportDayMs(nowMs)
    val startDay = Calendar.getInstance().apply {
        timeInMillis = endDayStart
        add(Calendar.DAY_OF_YEAR, -(daysCount - 1))
    }.timeInMillis

    val byDayKey = filteredSales.groupBy { sale ->
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(sale.createdAt))
    }

    return (0 until daysCount).map { offset ->
        val dayCal = Calendar.getInstance().apply {
            timeInMillis = startDay
            add(Calendar.DAY_OF_YEAR, offset)
        }
        val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(dayCal.time)
        val group = byDayKey[dayKey].orEmpty()
        ReportTrendBucket(
            label = if (daysCount <= 7) dayFormat7d.format(dayCal.time) else dayFormatShort.format(dayCal.time),
            detailLabel = dayFormatDetail.format(dayCal.time),
            revenueKsh = group.sumOf { it.totalKsh.toLong() },
            salesCount = group.size,
            unitsSold = group.sumOf { it.quantity }
        )
    }
}

internal fun formatSignedPercent(value: Double): String {
    val rounded = (value * 10.0).roundToInt() / 10.0
    val sign = if (rounded > 0) "+" else ""
    return "$sign$rounded%"
}
