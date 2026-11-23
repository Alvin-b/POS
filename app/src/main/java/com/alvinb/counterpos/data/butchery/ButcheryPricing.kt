package com.alvinb.counterpos.data.butchery

import com.alvinb.counterpos.data.local.entity.ButcheryConfigEntity
import com.alvinb.counterpos.data.local.entity.ButcheryCutEntity
import com.alvinb.counterpos.data.local.entity.ButcherySaleEntity
import org.json.JSONArray
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class ButcheryEntryMode { WEIGHT_KG, AMOUNT_KES }

enum class ButcheryCategory(val label: String) {
    ALL("All"),
    BEEF("Beef"),
    GOAT("Goat Meat"),
    CHICKEN("Whole Chicken"),
    OFFAL("Matumbo"),
    PORK("Pork Chops")
}

data class ButcheryCalculationResult(
    val weightKg: Double,
    val weightGrams: Int,
    val totalKsh: Int,
    val isValid: Boolean
)

data class ButcheryBarBucket(
    val label: String,
    val mpesaKsh: Long,
    val cashKsh: Long
)

data class ButcheryCutSummary(
    val cutName: String,
    val category: String,
    val totalGrams: Long,
    val totalKsh: Long,
    val salesCount: Int
)

data class ButcheryPaymentSummary(
    val method: String,
    val totalKsh: Long,
    val percent: Int,
    val count: Int
)

data class ButcheryDailyReportSnapshot(
    val startMs: Long,
    val endMs: Long,
    val totalSalesKsh: Long,
    val totalWeightGrams: Long,
    val totalCostKsh: Long,
    val netProfitKsh: Long,
    val mpesaTotalKsh: Long,
    val cashTotalKsh: Long,
    val mpesaPercent: Int,
    val cashPercent: Int,
    val sales: List<ButcherySaleEntity>,
    val chartBuckets: List<ButcheryBarBucket>,
    val topCuts: List<ButcheryCutSummary>,
    val paymentBreakdown: List<ButcheryPaymentSummary> = emptyList()
) {
    val totalWeightKg: Double get() = totalWeightGrams / 1000.0
}

enum class ButcheryTrendRange(val days: Int, val label: String, val subtitle: String) {
    SEVEN_DAYS(7, "7D", "Last 7 Days"),
    FOURTEEN_DAYS(14, "14D", "Last 14 Days"),
    THIRTY_DAYS(30, "30D", "Last 30 Days")
}

data class ButcheryDailyTrendPoint(
    val dayStartMs: Long,
    val axisLabel: String,
    val shortDateLabel: String,
    val revenueKsh: Long,
    val weightGrams: Long,
    val salesCount: Int,
    val rollingAvgRevenueKsh: Long,
    val rollingAvgWeightGrams: Long
) {
    val weightKg: Double get() = weightGrams / 1000.0
    val rollingAvgWeightKg: Double get() = rollingAvgWeightGrams / 1000.0
}

data class ButcheryDailySalesTrendSnapshot(
    val range: ButcheryTrendRange,
    val selectedCategory: String,
    val days: List<ButcheryDailyTrendPoint>,
    val totalRevenueKsh: Long,
    val totalWeightGrams: Long,
    val totalTransactions: Int,
    val activeDaysCount: Int,
    val avgDailyRevenueKsh: Long,
    val avgDailyWeightGrams: Long,
    val bestRevenueDay: ButcheryDailyTrendPoint?,
    val bestVolumeDay: ButcheryDailyTrendPoint?,
    val revenueChangePercent: Double?,
    val quantityChangePercent: Double?
) {
    val totalWeightKg: Double get() = totalWeightGrams / 1000.0
    val avgDailyWeightKg: Double get() = avgDailyWeightGrams / 1000.0
}

object ButcheryPricing {
    val DEFAULT_PAYMENT_METHODS = listOf("M-Pesa", "Cash Payment")
    const val DEFAULT_PAYMENT_METHODS_JSON = "[\"M-Pesa\",\"Cash Payment\"]"
    val ALL_MEAT_CATEGORIES = listOf(
        ButcheryCategory.BEEF.label,
        ButcheryCategory.GOAT.label,
        ButcheryCategory.CHICKEN.label,
        ButcheryCategory.OFFAL.label,
        ButcheryCategory.PORK.label
    )
    const val ALL_MEAT_CATEGORIES_JSON = "[\"Beef\",\"Goat Meat\",\"Whole Chicken\",\"Matumbo\",\"Pork Chops\"]"
    val SUGGESTED_PAYMENT_METHODS = listOf(
        "M-Pesa",
        "Cash Payment",
        "Airtel Money",
        "Card",
        "Bank Transfer",
        "Customer Credit"
    )

    val quickAmountPresetsKsh = listOf(100, 150, 200, 300, 500, 1000)
    val quickWeightPresetsKg = listOf("0.25", "0.5", "1", "1.5", "2")

    fun parseEnabledCategories(json: String?): List<String> {
        if (json.isNullOrBlank()) return ALL_MEAT_CATEGORIES
        val parsed = runCatching {
            val array = JSONArray(json)
            val seen = mutableSetOf<String>()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optString(i).trim()
                    if (item.isNotEmpty() && seen.add(item.lowercase(Locale.ROOT))) {
                        add(item)
                    }
                }
            }
        }.getOrDefault(emptyList())
        return parsed.ifEmpty { ALL_MEAT_CATEGORIES }
    }

    fun serializeEnabledCategories(categories: List<String>): String {
        val seen = mutableSetOf<String>()
        val normalized = categories.mapNotNull { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || !seen.add(trimmed.lowercase(Locale.ROOT))) null else trimmed
        }.ifEmpty { ALL_MEAT_CATEGORIES }
        return JSONArray(normalized).toString()
    }

    fun resolveEnabledCategories(config: ButcheryConfigEntity?): List<String> {
        if (config == null) return ALL_MEAT_CATEGORIES
        val parsed = parseEnabledCategories(config.enabledCategoriesJson)
        return if (config.beefOnlyMode && parsed.size == ALL_MEAT_CATEGORIES.size) {
            listOf(ButcheryCategory.BEEF.label)
        } else {
            parsed
        }
    }

    fun parsePaymentMethods(json: String?): List<String> {
        if (json.isNullOrBlank()) return DEFAULT_PAYMENT_METHODS
        val parsed = runCatching {
            val array = JSONArray(json)
            val seen = mutableSetOf<String>()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optString(i).trim().take(32)
                    if (item.isNotEmpty() && seen.add(item.lowercase(Locale.ROOT))) {
                        add(item)
                    }
                }
            }
        }.getOrDefault(emptyList())
        return parsed.ifEmpty { DEFAULT_PAYMENT_METHODS }
    }

    fun serializePaymentMethods(methods: List<String>): String {
        val seen = mutableSetOf<String>()
        val normalized = methods.mapNotNull { raw ->
            val trimmed = raw.trim().take(32)
            if (trimmed.isEmpty() || !seen.add(trimmed.lowercase(Locale.ROOT))) null else trimmed
        }.take(8).ifEmpty { DEFAULT_PAYMENT_METHODS }
        return JSONArray(normalized).toString()
    }

    fun isMpesaPayment(raw: String): Boolean {
        val cleaned = raw.trim()
        return cleaned.equals("mpesa", ignoreCase = true) || cleaned.equals("m-pesa", ignoreCase = true)
    }

    fun isCashPayment(raw: String): Boolean {
        val cleaned = raw.trim()
        return cleaned.equals("cash", ignoreCase = true) || cleaned.equals("cash payment", ignoreCase = true)
    }

    fun paymentMethodDisplayLabel(raw: String): String {
        val cleaned = raw.trim()
        return when {
            cleaned.isEmpty() -> "Cash Payment"
            isMpesaPayment(cleaned) -> "M-Pesa"
            cleaned.equals("cash", ignoreCase = true) -> "Cash"
            else -> cleaned
        }
    }

    fun computeFromWeightKg(weightKgInput: Double, pricePerKgKsh: Int): ButcheryCalculationResult {
        if (weightKgInput <= 0.0 || pricePerKgKsh <= 0) {
            return ButcheryCalculationResult(0.0, 0, 0, isValid = false)
        }
        val grams = (weightKgInput * 1000.0).roundToInt().coerceAtLeast(1)
        val normalizedKg = grams / 1000.0
        val total = (normalizedKg * pricePerKgKsh).roundToInt().coerceAtLeast(1)
        return ButcheryCalculationResult(
            weightKg = normalizedKg,
            weightGrams = grams,
            totalKsh = total,
            isValid = true
        )
    }

    /**
     * Digital scale price-first calculation:
     * When a customer asks for meat worth KES 100, 150, or 200, calculate exact kg from pricePerKgKsh.
     */
    fun computeFromAmountKsh(amountKshInput: Int, pricePerKgKsh: Int): ButcheryCalculationResult {
        if (amountKshInput <= 0 || pricePerKgKsh <= 0) {
            return ButcheryCalculationResult(0.0, 0, 0, isValid = false)
        }
        val rawKg = amountKshInput.toDouble() / pricePerKgKsh.toDouble()
        val grams = (rawKg * 1000.0).roundToInt().coerceAtLeast(1)
        return ButcheryCalculationResult(
            weightKg = grams / 1000.0,
            weightGrams = grams,
            totalKsh = amountKshInput,
            isValid = true
        )
    }

    fun formatWeightKg(grams: Long, decimals: Int = 2): String {
        val kg = grams / 1000.0
        return String.format(Locale.US, "%.${decimals}f kg", kg)
    }

    fun formatWeightKg(grams: Int, decimals: Int = 2): String =
        formatWeightKg(grams.toLong(), decimals)

    fun formatKsh(amount: Long): String {
        val fmt = NumberFormat.getIntegerInstance(Locale.US)
        return "KES ${fmt.format(amount)}"
    }

    fun formatKsh(amount: Int): String = formatKsh(amount.toLong())

    fun visibleCategories(beefOnlyMode: Boolean, cuts: List<ButcheryCutEntity>): List<String> {
        val enabled = if (beefOnlyMode) listOf(ButcheryCategory.BEEF.label) else ALL_MEAT_CATEGORIES
        return visibleCategories(enabled, cuts)
    }

    fun visibleCategories(enabledCategories: List<String>, cuts: List<ButcheryCutEntity>): List<String> {
        val normalizedEnabled = enabledCategories.ifEmpty { ALL_MEAT_CATEGORIES }
        if (normalizedEnabled.size == 1) return listOf(normalizedEnabled.first())
        val activeCategories = cuts
            .filter { cut -> cut.active && normalizedEnabled.any { it.equals(cut.category, ignoreCase = true) } }
            .map { it.category }
            .distinct()
        val ordered = ALL_MEAT_CATEGORIES.filter { cat ->
            normalizedEnabled.any { it.equals(cat, ignoreCase = true) } &&
                activeCategories.any { it.equals(cat, ignoreCase = true) }
        }
        val extras = activeCategories.filterNot { cat ->
            ordered.any { it.equals(cat, ignoreCase = true) }
        }.sorted()
        return listOf(ButcheryCategory.ALL.label) + ordered + extras
    }

    fun filterCuts(
        cuts: List<ButcheryCutEntity>,
        beefOnlyMode: Boolean,
        selectedCategory: String,
        searchQuery: String
    ): List<ButcheryCutEntity> {
        val enabled = if (beefOnlyMode) listOf(ButcheryCategory.BEEF.label) else ALL_MEAT_CATEGORIES
        return filterCuts(cuts, enabled, selectedCategory, searchQuery)
    }

    fun filterCuts(
        cuts: List<ButcheryCutEntity>,
        enabledCategories: List<String>,
        selectedCategory: String,
        searchQuery: String
    ): List<ButcheryCutEntity> {
        val normalizedEnabled = enabledCategories.ifEmpty { ALL_MEAT_CATEGORIES }
        val singleMeatMode = normalizedEnabled.size == 1
        val query = searchQuery.trim().lowercase(Locale.ROOT)
        return cuts.filter { cut ->
            if (!cut.active) return@filter false
            if (normalizedEnabled.none { it.equals(cut.category, ignoreCase = true) }) {
                return@filter false
            }
            val matchesCategory = singleMeatMode ||
                selectedCategory == ButcheryCategory.ALL.label ||
                cut.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = query.isBlank() ||
                cut.name.lowercase(Locale.ROOT).contains(query) ||
                cut.category.lowercase(Locale.ROOT).contains(query)
            matchesCategory && matchesSearch
        }.sortedWith(compareBy<ButcheryCutEntity> { it.sortOrder }.thenBy { it.name })
    }

    fun defaultCuts(shopId: String, beefPricePerKgKsh: Int = 680): List<ButcheryCutEntity> {
        val safeBeef = beefPricePerKgKsh.coerceIn(100, 10_000)
        return listOf(
            ButcheryCutEntity(
                id = "${shopId}_beef",
                shopId = shopId,
                name = "Beef",
                category = ButcheryCategory.BEEF.label,
                pricePerKgKsh = safeBeef,
                stockGrams = 0,
                iconKey = "beef",
                active = true,
                sortOrder = 1
            ),
            ButcheryCutEntity(
                id = "${shopId}_goat_meat",
                shopId = shopId,
                name = "Goat Meat",
                category = ButcheryCategory.GOAT.label,
                pricePerKgKsh = 750,
                stockGrams = 0,
                iconKey = "goat",
                active = true,
                sortOrder = 2
            ),
            ButcheryCutEntity(
                id = "${shopId}_whole_chicken",
                shopId = shopId,
                name = "Whole Chicken",
                category = ButcheryCategory.CHICKEN.label,
                pricePerKgKsh = 480,
                stockGrams = 0,
                iconKey = "chicken",
                active = true,
                sortOrder = 3
            ),
            ButcheryCutEntity(
                id = "${shopId}_matumbo",
                shopId = shopId,
                name = "Matumbo",
                category = ButcheryCategory.OFFAL.label,
                pricePerKgKsh = 280,
                stockGrams = 0,
                iconKey = "matumbo",
                active = true,
                sortOrder = 4
            ),
            ButcheryCutEntity(
                id = "${shopId}_pork_chops",
                shopId = shopId,
                name = "Pork Chops",
                category = ButcheryCategory.PORK.label,
                pricePerKgKsh = 700,
                stockGrams = 0,
                iconKey = "pork",
                active = true,
                sortOrder = 5
            )
        )
    }

    fun dayWindowMillis(dayOffset: Int = 0, referenceMs: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = referenceMs
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis - 1L
        return start to end
    }

    fun buildDailyReport(
        allSales: List<ButcherySaleEntity>,
        startMs: Long,
        endMs: Long
    ): ButcheryDailyReportSnapshot {
        val filtered = allSales.asSequence()
            .filter { !it.undone && it.createdAt in startMs..endMs }
            .sortedByDescending { it.createdAt }
            .toList()

        val totalSales = filtered.sumOf { it.totalKsh.toLong() }
        val totalGrams = filtered.sumOf { it.weightGrams.toLong() }
        val costedSales = filtered.filter { it.buyingPricePerKgKsh > 0 }
        val totalCost = costedSales.sumOf {
            ((it.weightGrams / 1000.0) * it.buyingPricePerKgKsh.toDouble()).roundToInt().toLong()
        }
        val mpesaTotal = filtered
            .filter { isMpesaPayment(it.paymentMethod) }
            .sumOf { it.totalKsh.toLong() }
        val cashTotal = filtered
            .filter { isCashPayment(it.paymentMethod) }
            .sumOf { it.totalKsh.toLong() }

        val mpesaPct = if (totalSales > 0L) {
            ((mpesaTotal.toDouble() / totalSales.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
        } else 0
        val cashPct = if (totalSales > 0L) {
            ((cashTotal.toDouble() / totalSales.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
        } else 0

        val paymentBreakdown = filtered
            .groupBy { paymentMethodDisplayLabel(it.paymentMethod) }
            .map { (methodLabel, rows) ->
                val sum = rows.sumOf { it.totalKsh.toLong() }
                val pct = if (totalSales > 0L) {
                    ((sum.toDouble() / totalSales.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
                } else 0
                ButcheryPaymentSummary(
                    method = methodLabel,
                    totalKsh = sum,
                    percent = pct,
                    count = rows.size
                )
            }
            .sortedByDescending { it.totalKsh }

        // 5 time-of-day blocks so chart always gives a clear M-Pesa vs Cash/Other rhythm
        val blockBounds = listOf(
            "6–10" to (6..9),
            "10–13" to (10..12),
            "13–16" to (13..15),
            "16–19" to (16..18),
            "19+" to (19..23)
        )
        val cal = Calendar.getInstance()
        val buckets = blockBounds.map { (label, hours) ->
            val blockSales = filtered.filter { sale ->
                cal.timeInMillis = sale.createdAt
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                hour in hours || (label == "6–10" && hour < 6)
            }
            ButcheryBarBucket(
                label = label,
                mpesaKsh = blockSales.filter { isMpesaPayment(it.paymentMethod) }.sumOf { it.totalKsh.toLong() },
                cashKsh = blockSales.filter { !isMpesaPayment(it.paymentMethod) }.sumOf { it.totalKsh.toLong() }
            )
        }

        val topCuts = filtered.groupBy { it.cutName }
            .map { (cutName, rows) ->
                ButcheryCutSummary(
                    cutName = cutName,
                    category = rows.first().category,
                    totalGrams = rows.sumOf { it.weightGrams.toLong() },
                    totalKsh = rows.sumOf { it.totalKsh.toLong() },
                    salesCount = rows.size
                )
            }
            .sortedByDescending { it.totalKsh }

        return ButcheryDailyReportSnapshot(
            startMs = startMs,
            endMs = endMs,
            totalSalesKsh = totalSales,
            totalWeightGrams = totalGrams,
            totalCostKsh = totalCost,
            netProfitKsh = totalSales - totalCost,
            mpesaTotalKsh = mpesaTotal,
            cashTotalKsh = cashTotal,
            mpesaPercent = mpesaPct,
            cashPercent = cashPct,
            sales = filtered,
            chartBuckets = buckets,
            topCuts = topCuts,
            paymentBreakdown = paymentBreakdown
        )
    }

    fun buildExcelCsv(
        butcheryName: String,
        snapshot: ButcheryDailyReportSnapshot
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val dayFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
        fun esc(value: String): String = "\"${value.replace("\"", "\"\"")}\""

        return buildString {
            appendLine("Butchery,${esc(butcheryName)}")
            appendLine("Report Date,${esc(dayFormat.format(Date(snapshot.startMs)))}")
            appendLine("Total Sales (KES),${snapshot.totalSalesKsh}")
            appendLine("Total Weight Sold (kg),${String.format(Locale.US, "%.2f", snapshot.totalWeightKg)}")
            appendLine("Known Cost (KES),${snapshot.totalCostKsh}")
            appendLine("Net Profit/Loss (KES),${snapshot.netProfitKsh}")
            appendLine("M-Pesa Total (KES),${snapshot.mpesaTotalKsh}")
            appendLine("Cash Total (KES),${snapshot.cashTotalKsh}")
            snapshot.paymentBreakdown
                .filterNot { isMpesaPayment(it.method) || isCashPayment(it.method) }
                .forEach { extra ->
                    appendLine("${esc("${extra.method} Total (KES)")},${extra.totalKsh}")
                }
            appendLine()
            appendLine("Time,Cut Name,Category,Weight (kg),Selling Price per kg (KES),Buying Price per kg (KES),Total (KES),Payment Method,Entry Mode")
            snapshot.sales.forEach { sale ->
                val weightKg = String.format(Locale.US, "%.3f", sale.weightGrams / 1000.0)
                val methodLabel = paymentMethodDisplayLabel(sale.paymentMethod)
                val entryLabel = if (sale.entryMode == "amount") "By Amount (KES)" else "By Weight (kg)"
                appendLine(
                    listOf(
                        esc(dateFormat.format(Date(sale.createdAt))),
                        esc(sale.cutName),
                        esc(sale.category),
                        weightKg,
                        sale.pricePerKgKsh.toString(),
                        if (sale.buyingPricePerKgKsh > 0) sale.buyingPricePerKgKsh.toString() else "",
                        sale.totalKsh.toString(),
                        esc(methodLabel),
                        esc(entryLabel)
                    ).joinToString(",")
                )
            }
        }
    }

    fun buildDailySalesTrend(
        allSales: List<ButcherySaleEntity>,
        range: ButcheryTrendRange = ButcheryTrendRange.FOURTEEN_DAYS,
        categoryFilter: String = ButcheryCategory.ALL.label,
        referenceMs: Long = System.currentTimeMillis()
    ): ButcheryDailySalesTrendSnapshot {
        val axisFmt = SimpleDateFormat("dd/MM", Locale.US)
        val shortDateFmt = SimpleDateFormat("EEE, dd MMM", Locale.US)

        val cal = Calendar.getInstance().apply {
            timeInMillis = referenceMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis

        val dayBuckets = (-(range.days - 1)..0).map { offset ->
            val dayCal = Calendar.getInstance().apply {
                timeInMillis = todayStart
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val start = dayCal.timeInMillis
            dayCal.add(Calendar.DAY_OF_YEAR, 1)
            val end = dayCal.timeInMillis - 1L
            Triple(start, end, Date(start))
        }

        val firstStartMs = dayBuckets.first().first
        val lastEndMs = dayBuckets.last().second

        val eligibleSales = allSales.filter { sale ->
            !sale.undone &&
                sale.createdAt in firstStartMs..lastEndMs &&
                (categoryFilter == ButcheryCategory.ALL.label ||
                    sale.category.equals(categoryFilter, ignoreCase = true))
        }

        data class RawDay(val startMs: Long, val date: Date, val revenueKsh: Long, val weightGrams: Long, val count: Int)
        val rawDays = dayBuckets.map { (start, end, date) ->
            val daySales = eligibleSales.filter { it.createdAt in start..end }
            RawDay(
                startMs = start,
                date = date,
                revenueKsh = daySales.sumOf { it.totalKsh.toLong() },
                weightGrams = daySales.sumOf { it.weightGrams.toLong() },
                count = daySales.size
            )
        }

        val rollingWindow = if (range == ButcheryTrendRange.THIRTY_DAYS) 7 else 3
        val points = rawDays.mapIndexed { idx, raw ->
            val fromIdx = (idx - rollingWindow + 1).coerceAtLeast(0)
            val slice = rawDays.subList(fromIdx, idx + 1)
            val avgRev = (slice.sumOf { it.revenueKsh }.toDouble() / slice.size.toDouble()).roundToInt().toLong()
            val avgGrams = (slice.sumOf { it.weightGrams }.toDouble() / slice.size.toDouble()).roundToInt().toLong()
            ButcheryDailyTrendPoint(
                dayStartMs = raw.startMs,
                axisLabel = axisFmt.format(raw.date),
                shortDateLabel = shortDateFmt.format(raw.date),
                revenueKsh = raw.revenueKsh,
                weightGrams = raw.weightGrams,
                salesCount = raw.count,
                rollingAvgRevenueKsh = avgRev,
                rollingAvgWeightGrams = avgGrams
            )
        }

        val totalRev = points.sumOf { it.revenueKsh }
        val totalGrams = points.sumOf { it.weightGrams }
        val totalTx = points.sumOf { it.salesCount }
        val activeDays = points.count { it.salesCount > 0 || it.revenueKsh > 0L || it.weightGrams > 0L }
        val avgRev = if (activeDays > 0) (totalRev.toDouble() / activeDays.toDouble()).roundToInt().toLong() else 0L
        val avgGrams = if (activeDays > 0) (totalGrams.toDouble() / activeDays.toDouble()).roundToInt().toLong() else 0L

        val split = points.size / 2
        val prior = if (split > 0) points.take(split) else emptyList()
        val recent = if (split > 0) points.drop(split) else emptyList()
        val priorRev = prior.sumOf { it.revenueKsh }
        val recentRev = recent.sumOf { it.revenueKsh }
        val priorGrams = prior.sumOf { it.weightGrams }
        val recentGrams = recent.sumOf { it.weightGrams }

        val revChangePct = if (priorRev > 0L) ((recentRev - priorRev) * 100.0) / priorRev.toDouble() else null
        val qtyChangePct = if (priorGrams > 0L) ((recentGrams - priorGrams) * 100.0) / priorGrams.toDouble() else null

        return ButcheryDailySalesTrendSnapshot(
            range = range,
            selectedCategory = categoryFilter,
            days = points,
            totalRevenueKsh = totalRev,
            totalWeightGrams = totalGrams,
            totalTransactions = totalTx,
            activeDaysCount = activeDays,
            avgDailyRevenueKsh = avgRev,
            avgDailyWeightGrams = avgGrams,
            bestRevenueDay = points.filter { it.revenueKsh > 0L }.maxByOrNull { it.revenueKsh },
            bestVolumeDay = points.filter { it.weightGrams > 0L }.maxByOrNull { it.weightGrams },
            revenueChangePercent = revChangePct,
            quantityChangePercent = qtyChangePct
        )
    }
}
