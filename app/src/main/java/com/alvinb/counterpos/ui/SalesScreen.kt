package com.alvinb.counterpos.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.remote.ServiceCounter
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class SalesLedgerMetrics(
    val filteredSales: List<SaleEntity>,
    val activeSalesCount: Int,
    val grossRevenueKsh: Long,
    val unitsSold: Int,
    val cashRevenueKsh: Long,
    val mpesaRevenueKsh: Long,
    val averageTicketKsh: Long
)

internal fun SaleEntity.matchesReceiptSearch(
    query: String,
    countersById: Map<String, String> = emptyMap()
): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    val resolvedCounterName = sourceCounterId?.let { countersById[it] } ?: counterName.orEmpty()
    val tableTag = if (!tableId.isNullOrBlank()) "table" else ""
    return listOf(
        productName,
        unitName,
        catalogCode,
        paymentMethod,
        waiterName.orEmpty(),
        resolvedCounterName,
        tableName.orEmpty(),
        tableTag
    ).any { it.contains(q, ignoreCase = true) }
}

internal fun computeSalesLedger(
    sales: List<SaleEntity>,
    countersById: Map<String, String>,
    dateFilter: String,
    paymentFilter: String,
    newestFirst: Boolean,
    searchQuery: String,
    todayStartMs: Long = startOfReportDay(System.currentTimeMillis())
): SalesLedgerMetrics {
    val dateAndSearchMatched = sales.asSequence()
        .filter { dateFilter != "today" || it.createdAt >= todayStartMs }
        .filter { it.matchesReceiptSearch(searchQuery, countersById) }
        .toList()

    val filtered = dateAndSearchMatched.asSequence()
        .filter { paymentFilter == "all" || it.paymentMethod.equals(paymentFilter, ignoreCase = true) }
        .sortedWith(
            if (newestFirst) compareByDescending<SaleEntity> { it.createdAt }.thenByDescending { it.id }
            else compareBy<SaleEntity> { it.createdAt }.thenBy { it.id }
        )
        .toList()

    val active = filtered.filterNot { it.undone }
    val revenue = active.sumOf { it.totalKsh.toLong() }
    val units = active.sumOf { it.quantity }
    val cashRev = active.filter { it.paymentMethod.equals("cash", ignoreCase = true) }.sumOf { it.totalKsh.toLong() }
    val mpesaRev = active.filter { it.paymentMethod.equals("mpesa", ignoreCase = true) }.sumOf { it.totalKsh.toLong() }
    val avgTicket = if (active.isNotEmpty()) revenue / active.size else 0L

    return SalesLedgerMetrics(
        filteredSales = filtered,
        activeSalesCount = active.size,
        grossRevenueKsh = revenue,
        unitsSold = units,
        cashRevenueKsh = cashRev,
        mpesaRevenueKsh = mpesaRev,
        averageTicketKsh = avgTicket
    )
}

@Composable
internal fun FullscreenSalesScreen(
    modifier: Modifier,
    sales: List<SaleEntity>,
    products: List<ProductEntity> = emptyList(),
    counters: List<ServiceCounter> = emptyList(),
    todaySummary: Pair<Int, Int>,
    searchQuery: String = "",
    onClearSearch: () -> Unit = {},
    onUndo: () -> Unit,
    onExportPdf: (Long, Long) -> Unit,
    onSharePdf: (Long, Long) -> Unit
) {
    val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var showDateRange by remember { mutableStateOf(false) }
    var shareAfterDateSelection by remember { mutableStateOf(false) }
    var dateFilter by remember { mutableStateOf("today") }
    var paymentFilter by remember { mutableStateOf("all") }
    var newestFirst by remember { mutableStateOf(true) }

    val todayStart = remember { startOfReportDay(System.currentTimeMillis()) }
    val productsByCode = remember(products) { products.associateBy { it.catalogCode } }
    val countersById = remember(counters) { counters.associate { it.id to it.name } }

    val ledger = remember(sales, countersById, dateFilter, paymentFilter, newestFirst, searchQuery, todayStart) {
        computeSalesLedger(
            sales = sales,
            countersById = countersById,
            dateFilter = dateFilter,
            paymentFilter = paymentFilter,
            newestFirst = newestFirst,
            searchQuery = searchQuery,
            todayStartMs = todayStart
        )
    }

    val cashCount = remember(sales, dateFilter, searchQuery, todayStart, countersById) {
        sales.count {
            (dateFilter != "today" || it.createdAt >= todayStart) &&
                it.matchesReceiptSearch(searchQuery, countersById) &&
                it.paymentMethod.equals("cash", ignoreCase = true)
        }
    }
    val mpesaCount = remember(sales, dateFilter, searchQuery, todayStart, countersById) {
        sales.count {
            (dateFilter != "today" || it.createdAt >= todayStart) &&
                it.matchesReceiptSearch(searchQuery, countersById) &&
                it.paymentMethod.equals("mpesa", ignoreCase = true)
        }
    }
    val allPaymentCount = remember(sales, dateFilter, searchQuery, todayStart, countersById) {
        sales.count {
            (dateFilter != "today" || it.createdAt >= todayStart) &&
                it.matchesReceiptSearch(searchQuery, countersById)
        }
    }

    val heroGradient = if (isDarkSurface) {
        Brush.linearGradient(listOf(Color(0xFF0E3A2C), Color(0xFF0A6146), Color(0xFF13483A)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF065F46), Color(0xFF087F5B), Color(0xFF0D946B)))
    }
    val heroAccent = if (isDarkSurface) SerenityTokens.MintPrimary else Color(0xFFBFFAE1)
    val cashAccent = if (isDarkSurface) Color(0xFFE8A04A) else Color(0xFFB85C0B)
    val mpesaAccent = MaterialTheme.colorScheme.primary

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Serenity Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Sales",
                        fontSize = 28.sp,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Live receipt ledger & daily cashflow",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${ledger.filteredSales.size} receipt${if (ledger.filteredSales.size == 1) "" else "s"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Active Search Indicator
        if (searchQuery.isNotBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Matching \"$searchQuery\"",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(
                            onClick = onClearSearch,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("sales_clear_search_button")
                        ) {
                            Text("Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Hero Revenue & Payment Split Card
        item {
            val showingTodayAll = dateFilter == "today" && paymentFilter == "all" && searchQuery.isBlank()
            val displayedRevenue = if (showingTodayAll) todaySummary.first.toLong() else ledger.grossRevenueKsh
            val displayedUnits = if (showingTodayAll) todaySummary.second else ledger.unitsSold
            val splitTotal = (ledger.cashRevenueKsh + ledger.mpesaRevenueKsh).coerceAtLeast(1L)
            val cashRatio = ledger.cashRevenueKsh.toFloat() / splitTotal.toFloat()
            val mpesaRatio = ledger.mpesaRevenueKsh.toFloat() / splitTotal.toFloat()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(heroGradient)
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = when {
                                    searchQuery.isNotBlank() -> "MATCHING REVENUE"
                                    dateFilter == "all" -> "ALL-TIME SALES LEDGER"
                                    else -> "TODAY'S REVENUE"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                color = heroAccent.copy(alpha = 0.85f)
                            )
                            Text(
                                text = salesKsh(displayedRevenue),
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 31.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = (-0.5).sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.14f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "AVG TICKET",
                                    fontSize = 9.sp,
                                    letterSpacing = 0.8.sp,
                                    color = heroAccent.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = salesKsh(ledger.averageTicketKsh),
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Units & Active Receipts row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HeroMiniStat(
                            label = "Units Sold",
                            value = displayedUnits.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        HeroMiniStat(
                            label = "Active Sales",
                            value = ledger.activeSalesCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        HeroMiniStat(
                            label = "M-Pesa Share",
                            value = if (ledger.cashRevenueKsh + ledger.mpesaRevenueKsh > 0L) {
                                "${(mpesaRatio * 100).toInt()}%"
                            } else "0%",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Cash vs M-Pesa split progress bar
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color.White.copy(alpha = 0.16f))
                        ) {
                            if (ledger.cashRevenueKsh + ledger.mpesaRevenueKsh > 0L) {
                                if (cashRatio > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .weight(cashRatio.coerceAtLeast(0.02f))
                                            .fillMaxHeight()
                                            .background(Color(0xFFF5B041))
                                    )
                                }
                                if (mpesaRatio > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .weight(mpesaRatio.coerceAtLeast(0.02f))
                                            .fillMaxHeight()
                                            .background(Color(0xFF5EE6B0))
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFF5B041)))
                                Text(
                                    text = "Cash · ${salesKsh(ledger.cashRevenueKsh)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.92f)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF5EE6B0)))
                                Text(
                                    text = "M-Pesa · ${salesKsh(ledger.mpesaRevenueKsh)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.92f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Actions Strip: Undo, Share PDF, Download PDF
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onUndo,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1.1f)
                        .height(48.dp)
                        .testTag("sales_undo_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = cashAccent
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("Undo Sale", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                OutlinedButton(
                    onClick = { shareAfterDateSelection = true; showDateRange = true },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(0.95f)
                        .height(48.dp)
                        .testTag("sales_share_pdf_button")
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share sales PDF",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("Share", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                Button(
                    onClick = { shareAfterDateSelection = false; showDateRange = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("sales_download_pdf_button")
                ) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = "Download sales PDF",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }

        // Sleek Pill Filter Card (Date · Sort · Payment Method)
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Segmented Date Switch (Today / All dates)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    "today" to "Today",
                                    "all" to "All dates"
                                ).forEach { (key, label) ->
                                    val selected = dateFilter == key
                                    Surface(
                                        onClick = { dateFilter = key },
                                        shape = RoundedCornerShape(11.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .testTag("sales_date_filter_$key")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Sort Order Pill Button
                        Surface(
                            onClick = { newestFirst = !newestFirst },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("sales_sort_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.SwapVert,
                                    contentDescription = "Toggle sort order",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (newestFirst) "Newest" else "Oldest",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Payment Filter Segment
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("all", "All ($allPaymentCount)", MaterialTheme.colorScheme.primary),
                            Triple("cash", "Cash ($cashCount)", cashAccent),
                            Triple("mpesa", "M-Pesa ($mpesaCount)", mpesaAccent)
                        ).forEach { (key, label, accent) ->
                            val selected = paymentFilter == key
                            Surface(
                                onClick = { paymentFilter = key },
                                shape = RoundedCornerShape(99.dp),
                                color = if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) accent.copy(alpha = 0.55f) else Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .testTag("sales_payment_filter_$key")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section Heading
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recent Receipts",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (dateFilter == "today") "Today" else "All history",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (ledger.filteredSales.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Text(
                            text = when {
                                sales.isEmpty() -> "No Sales Recorded Yet"
                                searchQuery.isNotBlank() -> "No Receipts Match \"$searchQuery\""
                                else -> "No Matching Receipts"
                            },
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = when {
                                sales.isEmpty() -> "Completed sales from the Sell screen and settled tables appear here automatically."
                                searchQuery.isNotBlank() -> "Try searching another product name, waiter, or payment method."
                                else -> "Adjust the date range or payment filter above to see more receipts."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(ledger.filteredSales, key = { it.id }) { sale ->
                SerenitySaleReceiptRow(
                    sale = sale,
                    product = productsByCode[sale.catalogCode],
                    counterName = sale.sourceCounterId?.let { countersById[it] } ?: sale.counterName
                )
            }
        }
    }

    if (showDateRange) {
        SalesDateRangeDialog(
            onDismiss = { showDateRange = false },
            onExport = { start, end ->
                showDateRange = false
                if (shareAfterDateSelection) onSharePdf(start, end) else onExportPdf(start, end)
            }
        )
    }
}

@Composable
private fun HeroMiniStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.11f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1
            )
            Text(
                text = value,
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SerenitySaleReceiptRow(
    sale: SaleEntity,
    product: ProductEntity?,
    counterName: String?
) {
    val isMpesa = sale.paymentMethod.equals("mpesa", ignoreCase = true)
    val paymentColor = if (isMpesa) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }
    val paymentLabel = when (sale.paymentMethod.lowercase(Locale.ROOT)) {
        "mpesa" -> "M-Pesa"
        "cash" -> "Cash"
        else -> sale.paymentMethod.replaceFirstChar { it.uppercase(Locale.getDefault()) }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (sale.undone) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sale_receipt_row_${sale.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // White-framed product thumbnail matching Sell / Stock / Reports
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (product != null) SerenityTokens.ThumbnailFrame else MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (product != null) {
                    ProductThumbnail(
                        product = product,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.LocalDrink,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = sale.productName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (sale.undone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (sale.undone) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${sale.quantity} × ${sale.unitName} @ ${salesKsh(sale.unitPriceKsh.toLong())} · ${formatSalesLedgerTime(sale.createdAt)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Metadata badges row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = paymentColor.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isMpesa) Icons.Default.PhoneAndroid else Icons.Default.Payments,
                                contentDescription = null,
                                tint = paymentColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = paymentLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = paymentColor
                            )
                        }
                    }

                    if (!counterName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = counterName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                maxLines = 1
                            )
                        }
                    }

                    if (!sale.tableId.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = sale.tableName?.takeIf { it.isNotBlank() } ?: "Table",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (sale.undone) {
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "UNDONE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    } else if (sale.remoteId == null) {
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Queued",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = salesKsh(sale.totalKsh.toLong()),
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textDecoration = if (sale.undone) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (sale.undone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
internal fun SalesDateRangeDialog(
    onDismiss: () -> Unit,
    onExport: (Long, Long) -> Unit
) {
    val context = LocalContext.current
    var start by remember { mutableStateOf(startOfReportDay(System.currentTimeMillis())) }
    var end by remember { mutableStateOf(endOfReportDay(System.currentTimeMillis())) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Export Branded Sales PDF",
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose one day or any date range for your statement.", fontSize = 13.sp)
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = start }
                        DatePickerDialog(
                            context,
                            { _, year, month, day -> start = startOfReportDay(year, month, day) },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("From: ${formatReportDate(start)}")
                }
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = end }
                        DatePickerDialog(
                            context,
                            { _, year, month, day -> end = endOfReportDay(year, month, day) },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("To: ${formatReportDate(end)}")
                }
                if (end < start) {
                    Text(
                        "The end date must be on or after the start date.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = end >= start,
                onClick = { onExport(start, end) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create PDF", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

internal fun startOfReportDay(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

internal fun endOfReportDay(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 999)
}.timeInMillis

private fun startOfReportDay(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
    set(year, month, day, 0, 0, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun endOfReportDay(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
    set(year, month, day, 23, 59, 59)
    set(Calendar.MILLISECOND, 999)
}.timeInMillis

internal fun formatReportDate(time: Long): String = formatReportDateIso(time)

private fun salesKsh(value: Long): String =
    "KSh " + NumberFormat.getIntegerInstance(Locale.US).format(value)

private fun formatSalesLedgerTime(timestamp: Long): String =
    SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Africa/Nairobi")
    }.format(Date(timestamp))
