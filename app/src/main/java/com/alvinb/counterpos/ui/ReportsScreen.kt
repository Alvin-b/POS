package com.alvinb.counterpos.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvinb.counterpos.data.local.entity.DiningTableEntity
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.local.entity.TableOrderEntity
import com.alvinb.counterpos.data.remote.CounterReportRow
import com.alvinb.counterpos.data.remote.ServiceCounter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun CounterReportScreen(
    modifier: Modifier,
    businessName: String,
    counters: List<ServiceCounter>,
    registeredWaiterEmails: Set<String>,
    sales: List<SaleEntity>,
    products: List<ProductEntity>,
    diningTables: List<DiningTableEntity>,
    tableOrders: List<TableOrderEntity>,
    supportsTables: Boolean,
    remoteRows: List<CounterReportRow>,
    isLoading: Boolean,
    reportFilter: String,
    reportCounterFilter: String,
    reportPaymentFilter: String,
    onSetReportFilter: (String) -> Unit,
    onSetReportPaymentFilter: (String) -> Unit,
    onSetReportCounterFilter: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenRecordDispensation: () -> Unit,
    onQuickDispenseForCounter: (ServiceCounter) -> Unit,
    onExportPdf: (ReportAnalyticsSnapshot) -> Unit,
    onSharePdf: (ReportAnalyticsSnapshot) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val snapshot = remember(
        sales,
        products,
        counters,
        remoteRows,
        registeredWaiterEmails,
        diningTables,
        tableOrders,
        reportFilter,
        reportPaymentFilter,
        reportCounterFilter
    ) {
        buildReportAnalyticsSnapshot(
            sales = sales,
            products = products,
            counters = counters,
            remoteRows = remoteRows,
            registeredWaiterEmails = registeredWaiterEmails,
            diningTables = diningTables,
            tableOrders = tableOrders,
            reportFilter = reportFilter,
            reportPaymentFilter = reportPaymentFilter,
            reportCounterFilter = reportCounterFilter
        )
    }

    val productsByCode = remember(products) { products.associateBy { it.catalogCode } }
    var showAllTopProducts by remember { mutableStateOf(false) }

    fun copyShareableReport() {
        val sb = StringBuilder()
        sb.appendLine("${businessName.uppercase(Locale.ROOT)} · PERFORMANCE REPORT")
        sb.appendLine("Period: ${snapshot.periodLabel}")
        sb.appendLine("Gross Revenue: ${formatKshLong(snapshot.totalRevenueKsh)}")
        snapshot.profitMarginPercent?.let { margin ->
            sb.appendLine("Est. Profit: ${formatKshLong(snapshot.estimatedProfitKsh)} (${((margin * 10).roundToInt() / 10.0)}% margin)")
        }
        sb.appendLine("Transactions: ${snapshot.transactionCount} · Units: ${snapshot.totalUnitsSold} · Avg Ticket: ${formatKshLong(snapshot.averageTicketKsh)}")
        sb.appendLine("Cash: ${formatKshLong(snapshot.cashRevenueKsh)} | M-Pesa: ${formatKshLong(snapshot.mpesaRevenueKsh)}")
        if (snapshot.topProducts.isNotEmpty()) {
            sb.appendLine("\nTop Products:")
            snapshot.topProducts.take(5).forEachIndexed { idx, p ->
                sb.appendLine("  ${idx + 1}. ${p.productName} (${p.unitName}): ${p.totalQuantity} sold · ${formatKshLong(p.totalKsh)}")
            }
        }
        if (snapshot.counterReports.size > 1) {
            sb.appendLine("\nBy Counter:")
            snapshot.counterReports.forEach { c ->
                sb.appendLine("  • ${c.counterName}: ${c.totalItems} units · ${formatKshLong(c.totalKsh)}")
            }
        }
        clipboard.setText(AnnotatedString(sb.toString()))
        Toast.makeText(context, "Report summary copied", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header + Branded PDF Action Bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Reports",
                            fontSize = 27.sp,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "$businessName · ${snapshot.periodLabel}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            IconButton(
                                onClick = { copyShareableReport() },
                                modifier = Modifier
                                    .size(42.dp)
                                    .testTag("report_copy_summary_button")
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy report summary",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            IconButton(
                                onClick = onRefresh,
                                enabled = !isLoading,
                                modifier = Modifier
                                    .size(42.dp)
                                    .testTag("report_refresh_button")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Refresh reports",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Prominent PDF Export & Share CTA Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onExportPdf(snapshot) },
                        modifier = Modifier
                            .weight(1.25f)
                            .height(48.dp)
                            .testTag("report_download_pdf_button"),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Download PDF",
                            fontSize = 13.sp,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = { onSharePdf(snapshot) },
                        modifier = Modifier
                            .weight(0.95f)
                            .height(48.dp)
                            .testTag("report_share_pdf_button"),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "Share PDF",
                            fontSize = 13.sp,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 2. Timeframe, Payment & Counter Filter Pills
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "today" to "Today",
                        "7d" to "7 Days",
                        "30d" to "30 Days",
                        "all" to "All Time"
                    ).forEach { (value, label) ->
                        SerenityReportFilterPill(
                            label = label,
                            selected = reportFilter == value,
                            onClick = { onSetReportFilter(value) },
                            modifier = Modifier.testTag("report_filter_$value")
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "all" to "All payments",
                        "cash" to "Cash",
                        "mpesa" to "M-Pesa"
                    ).forEach { (value, label) ->
                        SerenityReportFilterPill(
                            label = label,
                            selected = reportPaymentFilter == value,
                            accentColor = when (value) {
                                "mpesa" -> if (isDarkSurface) Color(0xFF38BDF8) else Color(0xFF0284C7)
                                "cash" -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.primary
                            },
                            onClick = { onSetReportPaymentFilter(value) },
                            modifier = Modifier.testTag("report_payment_$value")
                        )
                    }
                    if (counters.size > 1) {
                        SerenityReportFilterPill(
                            label = "All counters",
                            selected = reportCounterFilter == "all",
                            accentColor = MaterialTheme.colorScheme.secondary,
                            onClick = { onSetReportCounterFilter("all") },
                            modifier = Modifier.testTag("report_counter_all")
                        )
                        counters.forEach { counter ->
                            SerenityReportFilterPill(
                                label = counter.name,
                                selected = reportCounterFilter == counter.id,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                onClick = { onSetReportCounterFilter(counter.id) },
                                modifier = Modifier.testTag("report_counter_${counter.id}")
                            )
                        }
                    }
                }
            }
        }

        // 3. Hero Revenue & Velocity Card
        item {
            HeroRevenueCard(snapshot = snapshot, isDarkSurface = isDarkSurface)
        }

        // 4. Financial & Operational KPI Bento Grid (2x2)
        item {
            val topProduct = snapshot.topProducts.firstOrNull()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Card 1: Profit & Margin
                    BentoMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AccountBalanceWallet,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Est. Gross Profit",
                        value = if (snapshot.costTrackedRevenueKsh > 0L) {
                            formatKshLong(snapshot.estimatedProfitKsh)
                        } else {
                            "—"
                        },
                        subtitle = if (snapshot.profitMarginPercent != null) {
                            "${((snapshot.profitMarginPercent * 10.0).roundToInt() / 10.0)}% net margin"
                        } else {
                            "Set buying prices in Stock"
                        },
                        badgeText = snapshot.profitMarginPercent?.let {
                            "${((it * 10.0).roundToInt() / 10.0)}%"
                        },
                        badgeColor = MaterialTheme.colorScheme.primaryContainer,
                        badgeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    // Card 2: Payment Collection Split
                    val mpesaPct = if (snapshot.totalRevenueKsh > 0L) {
                        ((snapshot.mpesaRevenueKsh.toDouble() / snapshot.totalRevenueKsh.toDouble()) * 100.0).roundToInt()
                    } else 0
                    val cashPct = if (snapshot.totalRevenueKsh > 0L) {
                        ((snapshot.cashRevenueKsh.toDouble() / snapshot.totalRevenueKsh.toDouble()) * 100.0).roundToInt()
                    } else 0
                    PaymentSplitBentoCard(
                        modifier = Modifier.weight(1f),
                        cashKsh = snapshot.cashRevenueKsh,
                        mpesaKsh = snapshot.mpesaRevenueKsh,
                        cashPct = cashPct,
                        mpesaPct = mpesaPct,
                        isDarkSurface = isDarkSurface
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Card 3: Inventory Retail Valuation & Low Stock Health
                    val alertTotal = snapshot.lowStockCount + snapshot.outOfStockCount
                    BentoMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = if (alertTotal > 0) Icons.Default.WarningAmber else Icons.Default.Inventory2,
                        iconTint = if (alertTotal > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        title = "Stock Value",
                        value = formatKshLong(snapshot.inventoryRetailValueKsh),
                        subtitle = if (alertTotal > 0) {
                            "${snapshot.lowStockCount} low · ${snapshot.outOfStockCount} out of stock"
                        } else if (snapshot.inventoryCostValueKsh > 0L) {
                            "Cost basis ${formatKshLong(snapshot.inventoryCostValueKsh)}"
                        } else {
                            "All stocked items healthy"
                        },
                        badgeText = if (alertTotal > 0) "$alertTotal alerts" else "Healthy",
                        badgeColor = if (alertTotal > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                        badgeContentColor = if (alertTotal > 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    // Card 4: Open Table Tabs (if enabled) OR Top Performer Highlight
                    if (supportsTables || snapshot.activeTablesCount > 0) {
                        BentoMetricCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.TableRestaurant,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            title = "Open Table Tabs",
                            value = formatKshLong(snapshot.openTabsTotalKsh),
                            subtitle = if (snapshot.activeTablesCount > 0) {
                                "${snapshot.activeTablesCount} active table${if (snapshot.activeTablesCount == 1) "" else "s"} serving"
                            } else {
                                "All tables settled"
                            },
                            badgeText = if (snapshot.activeTablesCount > 0) "${snapshot.activeTablesCount} open" else "Clear",
                            badgeColor = if (snapshot.activeTablesCount > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            badgeContentColor = if (snapshot.activeTablesCount > 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        BentoMetricCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.LocalBar,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Top Seller",
                            value = topProduct?.productName ?: "No sales yet",
                            subtitle = topProduct?.let {
                                "${it.totalQuantity} ${it.unitName.lowercase(Locale.ROOT)} · ${formatKshLong(it.totalKsh)}"
                            } ?: "Complete a sale to see top mover",
                            badgeText = topProduct?.let { "#1" },
                            badgeColor = MaterialTheme.colorScheme.primaryContainer,
                            badgeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // 5. Last 30 Days Daily Sales Revenue Visualization (Vico) & Actionable Insights
        item {
            val counterScopeName = if (reportCounterFilter == "all") {
                "All counters"
            } else {
                counters.firstOrNull { it.id == reportCounterFilter }?.name ?: "Counter"
            }
            val paymentScopeName = when (reportPaymentFilter) {
                "cash" -> "Cash"
                "mpesa" -> "M-Pesa"
                else -> "All payments"
            }
            val scopeLabel = if (counters.size > 1 && reportCounterFilter != "all") {
                "$counterScopeName · $paymentScopeName"
            } else {
                paymentScopeName
            }
            ThirtyDayRevenueVicoCard(
                insights = snapshot.thirtyDayInsights,
                scopeLabel = scopeLabel,
                isDarkSurface = isDarkSurface
            )
        }

        // 6. Period-Specific Trend Breakdown (Hourly for Today / 7-Day View)
        if (reportFilter != "30d") {
            item {
                RevenueTrendChartCard(
                    reportFilter = reportFilter,
                    buckets = snapshot.trendBuckets,
                    isDarkSurface = isDarkSurface
                )
            }
        }

        // 6. Category Breakdown Card
        if (snapshot.categoryBreakdown.isNotEmpty()) {
            item {
                CategoryBreakdownCard(
                    categories = snapshot.categoryBreakdown,
                    isDarkSurface = isDarkSurface
                )
            }
        }

        // 7. Top Products Leaderboard
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Top Products",
                            fontSize = 17.sp,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Ranked by revenue in selected window",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (snapshot.topProducts.size > 5) {
                        TextButton(
                            onClick = { showAllTopProducts = !showAllTopProducts },
                            modifier = Modifier.testTag("report_top_products_toggle")
                        ) {
                            Text(
                                if (showAllTopProducts) "Show top 5" else "View all (${snapshot.topProducts.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (showAllTopProducts) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                if (snapshot.topProducts.isEmpty()) {
                    EmptyStateMessage("No sales recorded for this period and filter combination.")
                } else {
                    val displayedProducts = if (showAllTopProducts) snapshot.topProducts else snapshot.topProducts.take(5)
                    val maxProductRevenue = snapshot.topProducts.maxOfOrNull { it.totalKsh }?.coerceAtLeast(1L) ?: 1L
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            displayedProducts.forEachIndexed { index, item ->
                                val matchedEntity = productsByCode[item.catalogCode]
                                TopProductLeaderboardRow(
                                    rank = index + 1,
                                    item = item,
                                    productEntity = matchedEntity,
                                    maxRevenueKsh = maxProductRevenue,
                                    isDarkSurface = isDarkSurface
                                )
                                if (index < displayedProducts.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 8. Service Counters Performance
        if (snapshot.counterReports.size > 1) {
            item {
                val maxCounterRev = snapshot.counterReports.maxOfOrNull { it.totalKsh }?.coerceAtLeast(1L) ?: 1L
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Counter Performance",
                                fontSize = 17.sp,
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${snapshot.counterReports.size} active service bars / counters",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            snapshot.counterReports.sortedByDescending { it.totalKsh }.forEachIndexed { index, counterData ->
                                val counterObj = counters.firstOrNull { it.id == counterData.counterId }
                                val fraction = (counterData.totalKsh.toFloat() / maxCounterRev.toFloat()).coerceIn(0f, 1f)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Icon(
                                                Icons.Default.Storefront,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(7.dp).size(16.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                counterData.counterName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                "${counterData.totalItems} units · ${counterData.salesCount} sales",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                formatKshLong(counterData.totalKsh),
                                                fontFamily = SpaceGroteskFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (counterObj != null) {
                                                Text(
                                                    "Dispense",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .clickable { onQuickDispenseForCounter(counterObj) }
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(if (counterData.totalKsh > 0L) fraction.coerceAtLeast(0.04f) else 0f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                    if (counterData.cashKsh > 0L || counterData.mpesaKsh > 0L) {
                                        Text(
                                            "Cash ${formatKshLong(counterData.cashKsh)}  ·  M-Pesa ${formatKshLong(counterData.mpesaKsh)}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (index < snapshot.counterReports.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 9. Staff / Waiter Leaderboard
        if (snapshot.waiterLeaderboard.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Staff & Waiter Activity",
                        fontSize = 17.sp,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            snapshot.waiterLeaderboard.take(6).forEachIndexed { idx, waiter ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (idx == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                modifier = Modifier.size(17.dp),
                                                tint = if (idx == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            waiter.waiterName.substringBefore("@"),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val tablesSub = if (waiter.tablesServed.isNotEmpty()) {
                                            " · Tables: ${waiter.tablesServed.take(3).joinToString(", ")}"
                                        } else ""
                                        Text(
                                            "${waiter.itemsServed} items · ${waiter.salesCount} orders$tablesSub",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        formatKshLong(waiter.totalKsh),
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (idx < snapshot.waiterLeaderboard.take(6).lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SerenityReportFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val bgColor = if (selected) accentColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface
    val borderColor = if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant
    val textColor = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HeroRevenueCard(
    snapshot: ReportAnalyticsSnapshot,
    isDarkSurface: Boolean
) {
    val gradientColors = if (isDarkSurface) {
        listOf(Color(0xFF143C2F), Color(0xFF0C1E18))
    } else {
        listOf(Color(0xFF087F5B), Color(0xFF065B41))
    }
    val borderColor = if (isDarkSurface) SerenityTokens.StrongBorderDark else Color(0xFF0A8F67)
    val delta = snapshot.revenueDeltaPercent
    val isPositive = (delta ?: 0.0) >= 0.0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, borderColor),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NET REVENUE",
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA7E8CB)
                    )
                    if (delta != null) {
                        val badgeBg = if (isPositive) {
                            Color(0xFF5EE6B0).copy(alpha = 0.2f)
                        } else {
                            Color(0xFFE8A04A).copy(alpha = 0.22f)
                        }
                        val badgeFg = if (isPositive) Color(0xFF5EE6B0) else Color(0xFFFFCA80)
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = badgeBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    if (isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = null,
                                    tint = badgeFg,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    "${formatSignedPercent(delta)} vs prior",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeFg
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.White.copy(alpha = 0.12f)
                        ) {
                            Text(
                                snapshot.periodLabel,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                Text(
                    text = formatKshLong(snapshot.totalRevenueKsh),
                    fontSize = 32.sp,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HeroStatMicro("Sales", snapshot.transactionCount.toString(), Modifier.weight(1f))
                        HeroStatMicro("Units", snapshot.totalUnitsSold.toString(), Modifier.weight(1f))
                        HeroStatMicro("Avg Ticket", formatKshLong(snapshot.averageTicketKsh), Modifier.weight(1.25f))
                        HeroStatMicro("Peak", snapshot.peakBucketLabel ?: "—", Modifier.weight(1.1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStatMicro(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.68f), maxLines = 1)
        Text(
            value,
            fontSize = 12.sp,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BentoMetricCard(
    modifier: Modifier,
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    subtitle: String,
    badgeText: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    badgeContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = iconTint.copy(alpha = 0.14f)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.padding(7.dp).size(16.dp)
                    )
                }
                if (!badgeText.isNullOrBlank()) {
                    Surface(shape = RoundedCornerShape(999.dp), color = badgeColor) {
                        Text(
                            badgeText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeContentColor,
                            maxLines = 1
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    value,
                    fontSize = 17.sp,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PaymentSplitBentoCard(
    modifier: Modifier,
    cashKsh: Long,
    mpesaKsh: Long,
    cashPct: Int,
    mpesaPct: Int,
    isDarkSurface: Boolean
) {
    val cashColor = MaterialTheme.colorScheme.primary
    val mpesaColor = if (isDarkSurface) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val total = (cashKsh + mpesaKsh).coerceAtLeast(1L)
    val cashWeight = (cashKsh.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = mpesaColor.copy(alpha = 0.14f)
                ) {
                    Icon(
                        Icons.Default.PointOfSale,
                        contentDescription = null,
                        tint = mpesaColor,
                        modifier = Modifier.padding(7.dp).size(16.dp)
                    )
                }
                Text(
                    "Payment Split",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Proportional two-tone bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (cashKsh + mpesaKsh > 0L) {
                    if (cashWeight > 0f) {
                        Box(
                            Modifier
                                .weight(cashWeight.coerceAtLeast(0.05f))
                                .fillMaxHeight()
                                .background(cashColor)
                        )
                    }
                    if (1f - cashWeight > 0f) {
                        Box(
                            Modifier
                                .weight((1f - cashWeight).coerceAtLeast(0.05f))
                                .fillMaxHeight()
                                .background(mpesaColor)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(cashColor))
                        Text("Cash $cashPct%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        formatKshLong(cashKsh),
                        fontSize = 11.sp,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(mpesaColor))
                        Text("M-Pesa $mpesaPct%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        formatKshLong(mpesaKsh),
                        fontSize = 11.sp,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RevenueTrendChartCard(
    reportFilter: String,
    buckets: List<ReportTrendBucket>,
    isDarkSurface: Boolean
) {
    var selectedIdx by remember(buckets) {
        val peakIndex = buckets.indices.maxByOrNull { buckets[it].revenueKsh } ?: -1
        mutableIntStateOf(if (peakIndex >= 0 && buckets[peakIndex].revenueKsh > 0L) peakIndex else -1)
    }

    val title = if (reportFilter == "today") "Hourly Activity" else "Daily Revenue Trend"
    val maxVal = remember(buckets) { buckets.maxOfOrNull { it.revenueKsh }?.coerceAtLeast(1L) ?: 1L }
    val hasAnySales = remember(buckets) { buckets.any { it.revenueKsh > 0L } }
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val mutedBarColor = if (isDarkSurface) Color(0xFF214237) else Color(0xFFC2E5D6)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("report_trend_chart"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.BarChart,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        title,
                        fontSize = 15.sp,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
                val inspected = buckets.getOrNull(selectedIdx)
                if (inspected != null && inspected.revenueKsh > 0L) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "${inspected.detailLabel} · ${formatKshLong(inspected.revenueKsh)}",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (!hasAnySales || buckets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Sales activity bars will appear here once orders are completed.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                        .pointerInput(buckets) {
                            detectTapGestures { tapOffset ->
                                if (buckets.isNotEmpty()) {
                                    val slotWidth = size.width / buckets.size.toFloat()
                                    val tapped = (tapOffset.x / slotWidth).toInt().coerceIn(0, buckets.lastIndex)
                                    selectedIdx = tapped
                                }
                            }
                        }
                ) {
                    val chartH = size.height
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    listOf(0.25f, 0.55f, 0.85f).forEach { frac ->
                        val lineY = chartH * frac
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, lineY),
                            end = Offset(size.width, lineY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                    }

                    val count = buckets.size
                    val slotW = size.width / count.toFloat()
                    val barW = (slotW * 0.56f).coerceIn(8.dp.toPx(), 26.dp.toPx())

                    buckets.forEachIndexed { idx, bucket ->
                        val ratio = (bucket.revenueKsh.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                        val barH = if (bucket.revenueKsh > 0L) (chartH * 0.88f * ratio).coerceAtLeast(8.dp.toPx()) else 3.dp.toPx()
                        val left = idx * slotW + (slotW - barW) / 2f
                        val top = chartH - barH
                        val isSelected = idx == selectedIdx
                        val barColor = when {
                            bucket.revenueKsh <= 0L -> gridColor.copy(alpha = 0.4f)
                            isSelected -> primaryColor
                            else -> mutedBarColor
                        }
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(left, top),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                        if (isSelected && bucket.revenueKsh > 0L) {
                            drawCircle(
                                color = secondaryColor,
                                radius = 3.5.dp.toPx(),
                                center = Offset(left + barW / 2f, (top - 6.dp.toPx()).coerceAtLeast(4.dp.toPx()))
                            )
                        }
                    }
                }

                // Clean sparse x-axis labels so text never collides
                val step = (buckets.size / 6).coerceAtLeast(1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    buckets.forEachIndexed { idx, bucket ->
                        if (idx == 0 || idx == buckets.lastIndex || idx % step == 0) {
                            Text(
                                text = bucket.label,
                                fontSize = 10.sp,
                                fontWeight = if (idx == selectedIdx) FontWeight.Bold else FontWeight.Normal,
                                color = if (idx == selectedIdx) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(
    categories: List<ReportCategorySummary>,
    isDarkSurface: Boolean
) {
    val palette = if (isDarkSurface) {
        listOf(Color(0xFF5EE6B0), Color(0xFF38BDF8), Color(0xFFE8A04A), Color(0xFFA78BFA), Color(0xFFFB7185))
    } else {
        listOf(Color(0xFF087F5B), Color(0xFF0284C7), Color(0xFFD97706), Color(0xFF7C3AED), Color(0xFFE11D48))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Revenue by Category",
                    fontSize = 15.sp,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${categories.size} categor${if (categories.size == 1) "y" else "ies"}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            categories.take(6).forEachIndexed { idx, cat ->
                val color = palette[idx % palette.size]
                val fraction = (cat.sharePercent / 100.0).toFloat().coerceIn(0.03f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                            Text(
                                cat.category,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${cat.unitsSold} units",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "${formatKshLong(cat.revenueKsh)} (${cat.sharePercent.roundToInt()}%)",
                            fontSize = 12.sp,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(999.dp))
                                .background(color)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopProductLeaderboardRow(
    rank: Int,
    item: CounterProductSummary,
    productEntity: ProductEntity?,
    maxRevenueKsh: Long,
    isDarkSurface: Boolean
) {
    val rankAccent = when (rank) {
        1 -> MaterialTheme.colorScheme.primary
        2 -> if (isDarkSurface) Color(0xFF38BDF8) else Color(0xFF0284C7)
        3 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val barFraction = (item.totalKsh.toFloat() / maxRevenueKsh.coerceAtLeast(1L).toFloat()).coerceIn(0.05f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = rankAccent.copy(alpha = 0.16f),
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "#$rank",
                    fontSize = 11.sp,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = rankAccent
                )
            }
        }
        Spacer(Modifier.width(10.dp))

        if (productEntity != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SerenityTokens.ThumbnailFrame)
                    .padding(3.dp)
            ) {
                ProductThumbnail(productEntity, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(10.dp))
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                item.productName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "${item.totalQuantity} ${item.unitName}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                item.marginPercent?.let { margin ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "${margin.roundToInt()}% margin",
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(rankAccent)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatKshLong(item.totalKsh),
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )
            if (item.hasCostData && item.estimatedProfitKsh > 0L) {
                Text(
                    "+${formatKshLong(item.estimatedProfitKsh)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
