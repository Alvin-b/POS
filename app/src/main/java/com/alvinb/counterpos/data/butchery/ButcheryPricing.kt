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
import kotlin.math.roundToLong

enum class ButcheryEntryMode { WEIGHT_KG, AMOUNT_KES }

data class ButcheryCalculationResult(
    val weightKg: Double,
    val weightGrams: Int,
    val totalKsh: Int,
    val isValid: Boolean
)

data class ButcheryCutSummary(
    val cutName: String,
    val totalGrams: Long,
    val totalKsh: Long,
    val salesCount: Int,
    val totalCostKsh: Long? = null,
    val totalProfitKsh: Long? = null
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
    val mpesaTotalKsh: Long,
    val cashTotalKsh: Long,
    val mpesaPercent: Int,
    val cashPercent: Int,
    val sales: List<ButcherySaleEntity>,
    val topCuts: List<ButcheryCutSummary>,
    val paymentBreakdown: List<ButcheryPaymentSummary> = emptyList(),
    val totalCostKsh: Long? = null,
    val totalProfitKsh: Long? = null,
    val costTrackedSalesCount: Int = 0,
    val costTrackedRevenueKsh: Long = 0L
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
    val costKsh: Long? = null,
    val profitKsh: Long? = null
) {
    val weightKg: Double get() = weightGrams / 1000.0
}

data class ButcheryDailySalesTrendSnapshot(
    val range: ButcheryTrendRange,
    val selectedMeatName: String,
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
    val quantityChangePercent: Double?,
    val totalCostKsh: Long? = null,
    val totalProfitKsh: Long? = null,
    val costTrackedTransactions: Int = 0
) {
    val totalWeightKg: Double get() = totalWeightGrams / 1000.0
    val avgDailyWeightKg: Double get() = avgDailyWeightGrams / 1000.0
}

object ButcheryPricing {
    const val ALL_MEATS_LABEL = "All Meats"
    val DEFAULT_PAYMENT_METHODS = listOf("M-Pesa", "Cash Payment")
    const val DEFAULT_PAYMENT_METHODS_JSON = "[\"M-Pesa\",\"Cash Payment\"]"
    val DEFAULT_MEAT_NAMES = listOf(
        "Beef",
        "Goat Meat",
        "Whole Chicken",
        "Matumbo",
        "Pork Chops"
    )
    const val ALL_MEAT_NAMES_JSON = "[\"Beef\",\"Goat Meat\",\"Whole Chicken\",\"Matumbo\",\"Pork Chops\"]"
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

    fun normalizeLegacyMeatSelection(raw: String): String {
        val trimmed = raw.trim()
        return when (trimmed.lowercase(Locale.ROOT)) {
            "goat/mbuzi", "goat" -> "Goat Meat"
            "chicken" -> "Whole Chicken"
            "liver/matumbo" -> "Matumbo"
            "pork" -> "Pork Chops"
            else -> trimmed
        }
    }

    fun inferIconKey(meatName: String): String {
        val lower = meatName.trim().lowercase(Locale.ROOT)
        return when {
            lower.contains("goat") || lower.contains("mbuzi") ||
                lower.contains("mutton") || lower.contains("lamb") -> "goat"
            lower.contains("chicken") || lower.contains("kuku") ||
                lower.contains("hen") || lower.contains("poultry") -> "chicken"
            lower.contains("matumbo") || lower.contains("liver") ||
                lower.contains("tripe") || lower.contains("offal") ||
                lower.contains("kidney") -> "matumbo"
            lower.contains("pork") || lower.contains("bacon") ||
                lower.contains("ham") -> "pork"
            else -> "beef"
        }
    }

    fun parseEnabledMeats(json: String?): List<String> {
        if (json.isNullOrBlank()) return DEFAULT_MEAT_NAMES
        val parsed = runCatching {
            val array = JSONArray(json)
            val seen = mutableSetOf<String>()
            buildList {
                for (i in 0 until array.length()) {
                    val item = normalizeLegacyMeatSelection(array.optString(i))
                    if (item.isNotEmpty() && seen.add(item.lowercase(Locale.ROOT))) {
                        add(item)
                    }
                }
            }
        }.getOrDefault(emptyList())
        return parsed.ifEmpty { DEFAULT_MEAT_NAMES }
    }

    fun serializeEnabledMeats(meats: List<String>): String {
        val seen = mutableSetOf<String>()
        val normalized = meats.mapNotNull { raw ->
            val cleaned = normalizeLegacyMeatSelection(raw)
            if (cleaned.isEmpty() || !seen.add(cleaned.lowercase(Locale.ROOT))) null else cleaned
        }.ifEmpty { DEFAULT_MEAT_NAMES }
        return JSONArray(normalized).toString()
    }

    fun resolveEnabledMeats(config: ButcheryConfigEntity?): List<String> {
        if (config == null) return DEFAULT_MEAT_NAMES
        val parsed = parseEnabledMeats(config.enabledCategoriesJson)
        return if (config.beefOnlyMode && parsed.size == DEFAULT_MEAT_NAMES.size) {
            listOf("Beef")
        } else {
            parsed
        }
    }

    fun availableMeatChoices(cuts: List<ButcheryCutEntity>, currentSelected: List<String> = emptyList()): List<String> {
        val seen = mutableSetOf<String>()
        return buildList {
            DEFAULT_MEAT_NAMES.forEach { name ->
                if (seen.add(name.lowercase(Locale.ROOT))) add(name)
            }
            cuts.forEach { cut ->
                val cleaned = cut.name.trim()
                if (cleaned.isNotEmpty() && seen.add(cleaned.lowercase(Locale.ROOT))) {
                    add(cleaned)
                }
            }
            currentSelected.forEach { raw ->
                val cleaned = normalizeLegacyMeatSelection(raw)
                if (cleaned.isNotEmpty() && seen.add(cleaned.lowercase(Locale.ROOT))) {
                    add(cleaned)
                }
            }
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

    fun computeSaleCostKsh(weightGrams: Int, buyingPricePerKgKsh: Int): Long? {
        if (buyingPricePerKgKsh <= 0 || weightGrams <= 0) return null
        return ((weightGrams / 1000.0) * buyingPricePerKgKsh.toDouble()).roundToLong()
    }

    fun computeSaleProfitKsh(sale: ButcherySaleEntity): Long? {
        val cost = computeSaleCostKsh(sale.weightGrams, sale.buyingPricePerKgKsh) ?: return null
        return sale.totalKsh.toLong() - cost
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

    fun formatSignedKsh(amount: Long): String =
        if (amount < 0L) "-${formatKsh(-amount)}" else formatKsh(amount)

    fun filterCuts(
        cuts: List<ButcheryCutEntity>,
        beefOnlyMode: Boolean,
        searchQuery: String
    ): List<ButcheryCutEntity> {
        val enabled = if (beefOnlyMode) listOf("Beef") else DEFAULT_MEAT_NAMES
        return filterCuts(cuts, enabled, searchQuery)
    }

    fun filterCuts(
        cuts: List<ButcheryCutEntity>,
        enabledMeats: List<String>,
        searchQuery: String = ""
    ): List<ButcheryCutEntity> {
        val normalizedEnabled = enabledMeats
            .map(::normalizeLegacyMeatSelection)
            .ifEmpty { DEFAULT_MEAT_NAMES }
        val query = searchQuery.trim().lowercase(Locale.ROOT)
        return cuts.filter { cut ->
            if (!cut.active) return@filter false
            val matchesSelectedMeat = normalizedEnabled.any { it.equals(cut.name, ignoreCase = true) }
            val matchesSearch = query.isBlank() || cut.name.lowercase(Locale.ROOT).contains(query)
            matchesSelectedMeat && matchesSearch
        }.sortedWith(compareBy<ButcheryCutEntity> { it.sortOrder }.thenBy { it.name })
    }

    fun defaultCuts(shopId: String, beefPricePerKgKsh: Int = 0): List<ButcheryCutEntity> {
        val safeBeef = beefPricePerKgKsh.coerceIn(0, 10_000)
        return listOf(
            ButcheryCutEntity(
                id = "${shopId}_beef",
                shopId = shopId,
                name = "Beef",
                category = "",
                pricePerKgKsh = safeBeef,
                buyingPricePerKgKsh = 0,
                stockGrams = 0,
                iconKey = "beef",
                active = true,
                sortOrder = 1
            ),
            ButcheryCutEntity(
                id = "${shopId}_goat_meat",
                shopId = shopId,
                name = "Goat Meat",
                category = "",
                pricePerKgKsh = 0,
                buyingPricePerKgKsh = 0,
                stockGrams = 0,
                iconKey = "goat",
                active = true,
                sortOrder = 2
            ),
            ButcheryCutEntity(
                id = "${shopId}_whole_chicken",
                shopId = shopId,
                name = "Whole Chicken",
                category = "",
                pricePerKgKsh = 0,
                buyingPricePerKgKsh = 0,
                stockGrams = 0,
                iconKey = "chicken",
                active = true,
                sortOrder = 3
            ),
            ButcheryCutEntity(
                id = "${shopId}_matumbo",
                shopId = shopId,
                name = "Matumbo",
                category = "",
                pricePerKgKsh = 0,
                buyingPricePerKgKsh = 0,
                stockGrams = 0,
                iconKey = "matumbo",
                active = true,
                sortOrder = 4
            ),
            ButcheryCutEntity(
                id = "${shopId}_pork_chops",
                shopId = shopId,
                name = "Pork Chops",
                category = "",
                pricePerKgKsh = 0,
                buyingPricePerKgKsh = 0,
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

        val costTrackedRows = filtered.filter { it.buyingPricePerKgKsh > 0 }
        val totalCostKsh: Long?
        val totalProfitKsh: Long?
        val costTrackedRevenueKsh: Long
        if (costTrackedRows.isEmpty()) {
            totalCostKsh = null
            totalProfitKsh = null
            costTrackedRevenueKsh = 0L
        } else {
            val costSum = costTrackedRows.sumOf { computeSaleCostKsh(it.weightGrams, it.buyingPricePerKgKsh) ?: 0L }
            val revSum = costTrackedRows.sumOf { it.totalKsh.toLong() }
            totalCostKsh = costSum
            totalProfitKsh = revSum - costSum
            costTrackedRevenueKsh = revSum
        }

        val topCuts = filtered.groupBy { it.cutName }
            .map { (cutName, rows) ->
                val tracked = rows.filter { it.buyingPricePerKgKsh > 0 }
                val cutCost = if (tracked.isNotEmpty()) {
                    tracked.sumOf { computeSaleCostKsh(it.weightGrams, it.buyingPricePerKgKsh) ?: 0L }
                } else null
                val cutProfit = cutCost?.let { tracked.sumOf { s -> s.totalKsh.toLong() } - it }
                ButcheryCutSummary(
                    cutName = cutName,
                    totalGrams = rows.sumOf { it.weightGrams.toLong() },
                    totalKsh = rows.sumOf { it.totalKsh.toLong() },
                    salesCount = rows.size,
                    totalCostKsh = cutCost,
                    totalProfitKsh = cutProfit
                )
            }
            .sortedByDescending { it.totalKsh }

        return ButcheryDailyReportSnapshot(
            startMs = startMs,
            endMs = endMs,
            totalSalesKsh = totalSales,
            totalWeightGrams = totalGrams,
            mpesaTotalKsh = mpesaTotal,
            cashTotalKsh = cashTotal,
            mpesaPercent = mpesaPct,
            cashPercent = cashPct,
            sales = filtered,
            topCuts = topCuts,
            paymentBreakdown = paymentBreakdown,
            totalCostKsh = totalCostKsh,
            totalProfitKsh = totalProfitKsh,
            costTrackedSalesCount = costTrackedRows.size,
            costTrackedRevenueKsh = costTrackedRevenueKsh
        )
    }

    fun buildExcelCsv(
        butcheryName: String,
        snapshot: ButcheryDailyReportSnapshot
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val dayFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
        fun esc(value: String): String = "\"${value.replace("\"", "\"\"")}\""
        val includeProfitColumns = snapshot.totalProfitKsh != null

        return buildString {
            appendLine("Butchery,${esc(butcheryName)}")
            appendLine("Report Date,${esc(dayFormat.format(Date(snapshot.startMs)))}")
            appendLine("Total Sales (KES),${snapshot.totalSalesKsh}")
            appendLine("Total Weight Sold (kg),${String.format(Locale.US, "%.2f", snapshot.totalWeightKg)}")
            if (includeProfitColumns) {
                appendLine("Total Cost (KES),${snapshot.totalCostKsh ?: 0L}")
                val profitLabel = if ((snapshot.totalProfitKsh ?: 0L) >= 0L) "Net Profit (KES)" else "Net Loss (KES)"
                appendLine("$profitLabel,${snapshot.totalProfitKsh ?: 0L}")
            }
            appendLine("M-Pesa Total (KES),${snapshot.mpesaTotalKsh}")
            appendLine("Cash Total (KES),${snapshot.cashTotalKsh}")
            snapshot.paymentBreakdown
                .filterNot { isMpesaPayment(it.method) || isCashPayment(it.method) }
                .forEach { extra ->
                    appendLine("${esc("${extra.method} Total (KES)")},${extra.totalKsh}")
                }
            appendLine()
            if (includeProfitColumns) {
                appendLine("Time,Meat Name,Weight (kg),Selling Price/kg (KES),Buying Price/kg (KES),Cost (KES),Total (KES),Profit/Loss (KES),Payment Method,Entry Mode")
            } else {
                appendLine("Time,Meat Name,Weight (kg),Selling Price/kg (KES),Total (KES),Payment Method,Entry Mode")
            }
            snapshot.sales.forEach { sale ->
                val weightKg = String.format(Locale.US, "%.3f", sale.weightGrams / 1000.0)
                val methodLabel = paymentMethodDisplayLabel(sale.paymentMethod)
                val entryLabel = if (sale.entryMode == "amount") "By Amount (KES)" else "By Weight (kg)"
                val baseCols = mutableListOf(
                    esc(dateFormat.format(Date(sale.createdAt))),
                    esc(sale.cutName),
                    weightKg,
                    sale.pricePerKgKsh.toString()
                )
                if (includeProfitColumns) {
                    val cost = computeSaleCostKsh(sale.weightGrams, sale.buyingPricePerKgKsh)
                    val profit = cost?.let { sale.totalKsh.toLong() - it }
                    baseCols += if (sale.buyingPricePerKgKsh > 0) sale.buyingPricePerKgKsh.toString() else ""
                    baseCols += cost?.toString() ?: ""
                    baseCols += sale.totalKsh.toString()
                    baseCols += profit?.toString() ?: ""
                } else {
                    baseCols += sale.totalKsh.toString()
                }
                baseCols += esc(methodLabel)
                baseCols += esc(entryLabel)
                appendLine(baseCols.joinToString(","))
            }
        }
    }

    fun buildDailySalesTrend(
        allSales: List<ButcherySaleEntity>,
        range: ButcheryTrendRange = ButcheryTrendRange.FOURTEEN_DAYS,
        meatNameFilter: String = ALL_MEATS_LABEL,
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
                (meatNameFilter == ALL_MEATS_LABEL ||
                    sale.cutName.equals(meatNameFilter, ignoreCase = true))
        }

        val points = dayBuckets.map { (start, end, date) ->
            val daySales = eligibleSales.filter { it.createdAt in start..end }
            val tracked = daySales.filter { it.buyingPricePerKgKsh > 0 }
            val dayCost = if (tracked.isNotEmpty()) {
                tracked.sumOf { computeSaleCostKsh(it.weightGrams, it.buyingPricePerKgKsh) ?: 0L }
            } else null
            val dayProfit = dayCost?.let { tracked.sumOf { s -> s.totalKsh.toLong() } - it }
            ButcheryDailyTrendPoint(
                dayStartMs = start,
                axisLabel = axisFmt.format(date),
                shortDateLabel = shortDateFmt.format(date),
                revenueKsh = daySales.sumOf { it.totalKsh.toLong() },
                weightGrams = daySales.sumOf { it.weightGrams.toLong() },
                salesCount = daySales.size,
                costKsh = dayCost,
                profitKsh = dayProfit
            )
        }

        val totalRev = points.sumOf { it.revenueKsh }
        val totalGrams = points.sumOf { it.weightGrams }
        val totalTx = points.sumOf { it.salesCount }
        val activeDays = points.count { it.salesCount > 0 || it.revenueKsh > 0L || it.weightGrams > 0L }
        val avgRev = if (activeDays > 0) (totalRev.toDouble() / activeDays.toDouble()).roundToInt().toLong() else 0L
        val avgGrams = if (activeDays > 0) (totalGrams.toDouble() / activeDays.toDouble()).roundToInt().toLong() else 0L

        val trackedAll = eligibleSales.filter { it.buyingPricePerKgKsh > 0 }
        val windowCostKsh: Long?
        val windowProfitKsh: Long?
        if (trackedAll.isEmpty()) {
            windowCostKsh = null
            windowProfitKsh = null
        } else {
            val cSum = trackedAll.sumOf { computeSaleCostKsh(it.weightGrams, it.buyingPricePerKgKsh) ?: 0L }
            val rSum = trackedAll.sumOf { it.totalKsh.toLong() }
            windowCostKsh = cSum
            windowProfitKsh = rSum - cSum
        }

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
            selectedMeatName = meatNameFilter,
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
            quantityChangePercent = qtyChangePct,
            totalCostKsh = windowCostKsh,
            totalProfitKsh = windowProfitKsh,
            costTrackedTransactions = trackedAll.size
        )
    }
}
