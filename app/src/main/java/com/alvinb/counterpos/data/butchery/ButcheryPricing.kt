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
    GOAT("Goat/Mbuzi"),
    CHICKEN("Chicken"),
    OFFAL("Liver/Matumbo"),
    PORK("Pork")
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
    const val ALL_MEAT_CATEGORIES_JSON = "[\"Beef\",\"Goat/Mbuzi\",\"Chicken\",\"Liver/Matumbo\",\"Pork\"]"
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
        val cubesPrice = (safeBeef - 60).coerceAtLeast(100)
        return listOf(
            ButcheryCutEntity(
                id = "${shopId}_beef_steak",
                shopId = shopId,
                name = "Beef Steak",
                category = ButcheryCategory.BEEF.label,
                pricePerKgKsh = safeBeef,
                stockGrams = 0,
                iconKey = "beef_steak",
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
                id = "${shopId}_beef_cubes",
                shopId = shopId,
                name = "Beef Cubes",
                category = ButcheryCategory.BEEF.label,
                pricePerKgKsh = cubesPrice,
                stockGrams = 0,
                iconKey = "beef_cubes",
                active = true,
                sortOrder = 4
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
                sortOrder = 5
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
                sortOrder = 6
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
            appendLine("M-Pesa Total (KES),${snapshot.mpesaTotalKsh}")
            appendLine("Cash Total (KES),${snapshot.cashTotalKsh}")
            snapshot.paymentBreakdown
                .filterNot { isMpesaPayment(it.method) || isCashPayment(it.method) }
                .forEach { extra ->
                    appendLine("${esc("${extra.method} Total (KES)")},${extra.totalKsh}")
                }
            appendLine()
            appendLine("Time,Cut Name,Category,Weight (kg),Price per kg (KES),Total (KES),Payment Method,Entry Mode")
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
                        sale.totalKsh.toString(),
                        esc(methodLabel),
                        esc(entryLabel)
                    ).joinToString(",")
                )
            }
        }
    }
}
