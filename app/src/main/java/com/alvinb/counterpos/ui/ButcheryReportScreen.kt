package com.alvinb.counterpos.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvinb.counterpos.data.butchery.ButcheryDailyReportSnapshot
import com.alvinb.counterpos.data.butchery.ButcheryPricing
import com.alvinb.counterpos.data.local.entity.ButcheryConfigEntity
import com.alvinb.counterpos.data.local.entity.ButcherySaleEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ButcheryReportScreen(
    config: ButcheryConfigEntity?,
    sales: List<ButcherySaleEntity>,
    onBack: () -> Unit,
    onUndoLatestSale: () -> Unit,
    onSavePdf: (ButcheryDailyReportSnapshot) -> Unit,
    onSharePdf: (ButcheryDailyReportSnapshot) -> Unit,
    onSaveExcelCsv: (ButcheryDailyReportSnapshot) -> Unit,
    onShareExcelCsv: (ButcheryDailyReportSnapshot) -> Unit,
    onOpenTrends: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var dayOffset by remember { mutableIntStateOf(0) }
    var customReferenceMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showExportDialog by remember { mutableStateOf(false) }

    val window = remember(dayOffset, customReferenceMs) {
        ButcheryPricing.dayWindowMillis(dayOffset = dayOffset, referenceMs = customReferenceMs)
    }
    val snapshot = remember(sales, window) {
        ButcheryPricing.buildDailyReport(sales, window.first, window.second)
    }
    val dateLabel = remember(window) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(window.first))
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    fun openDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = window.first }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(year, month, day, 12, 0, 0)
                }
                customReferenceMs = picked.timeInMillis
                dayOffset = 0
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ButcheryPalette.SoftSurface)
    ) {
        Surface(color = ButcheryPalette.EmeraldHeader, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("butchery_report_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Daily Sales Report",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = config?.butcheryName?.takeIf(String::isNotBlank) ?: "Butchery Ledger",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
                if (snapshot.sales.isNotEmpty()) {
                    IconButton(onClick = onUndoLatestSale, modifier = Modifier.testTag("butchery_report_undo_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo latest sale",
                            tint = Color.White
                        )
                    }
                }
                if (onOpenTrends != null) {
                    IconButton(onClick = onOpenTrends, modifier = Modifier.testTag("butchery_report_open_trends_button")) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Open Daily Sales Summary",
                            tint = Color.White
                        )
                    }
                }
                IconButton(onClick = ::openDatePicker, modifier = Modifier.testTag("butchery_report_date_picker")) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Pick report date",
                        tint = Color.White
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("butchery_report_list"),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Date row + Quick Day chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = dateLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = dayOffset == 0,
                            onClick = {
                                customReferenceMs = System.currentTimeMillis()
                                dayOffset = 0
                            },
                            label = { Text("Today", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = dayOffset == -1,
                            onClick = {
                                customReferenceMs = System.currentTimeMillis()
                                dayOffset = -1
                            },
                            label = { Text("Yesterday", fontSize = 11.sp) }
                        )
                    }
                }
            }

            // Two KPI Summary Cards (Total sales + Total weight sold)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Total sales", fontSize = 12.sp, color = Color(0xFF475569))
                            Text(
                                text = ButcheryPricing.formatKsh(snapshot.totalSalesKsh),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A),
                                modifier = Modifier.testTag("butchery_report_total_sales")
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Total weight sold", fontSize = 12.sp, color = Color(0xFF475569))
                            Text(
                                text = ButcheryPricing.formatWeightKg(snapshot.totalWeightGrams, decimals = 1),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A),
                                modifier = Modifier.testTag("butchery_report_total_weight")
                            )
                        }
                    }
                }
            }

            // Conditional Profit / Loss Card (ONLY displayed when buying prices were entered)
            val netProfit = snapshot.totalProfitKsh
            if (netProfit != null) {
                item {
                    val isProfit = netProfit >= 0L
                    val accent = if (isProfit) ButcheryPalette.EmeraldHeader else Color(0xFFB91C1C)
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (isProfit) "Estimated Profit" else "Estimated Loss",
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )
                            Text(
                                text = ButcheryPricing.formatSignedKsh(netProfit),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = accent,
                                modifier = Modifier.testTag("butchery_report_profit_loss")
                            )
                            Text(
                                text = "Cost: ${ButcheryPricing.formatKsh(snapshot.totalCostKsh ?: 0L)} · Tracked sales: ${ButcheryPricing.formatKsh(snapshot.costTrackedRevenueKsh)} (${snapshot.costTrackedSalesCount})",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            // Payment Breakdown Cards (pure numeric summary, no bar graph)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Payment Breakdown",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    val breakdownCards = remember(snapshot.paymentBreakdown, config?.paymentMethodsJson) {
                        if (snapshot.paymentBreakdown.isNotEmpty()) {
                            snapshot.paymentBreakdown
                        } else {
                            ButcheryPricing.parsePaymentMethods(config?.paymentMethodsJson).map {
                                com.alvinb.counterpos.data.butchery.ButcheryPaymentSummary(
                                    method = ButcheryPricing.paymentMethodDisplayLabel(it),
                                    totalKsh = 0L,
                                    percent = 0,
                                    count = 0
                                )
                            }
                        }
                    }
                    breakdownCards.chunked(2).forEachIndexed { rowIdx, rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEachIndexed { colIdx, summary ->
                                val bgColor = when {
                                    ButcheryPricing.isMpesaPayment(summary.method) -> ButcheryPalette.EmeraldHeader
                                    ButcheryPricing.isCashPayment(summary.method) -> ButcheryPalette.SlateButton
                                    (rowIdx * 2 + colIdx) % 2 == 0 -> Color(0xFF0F766E)
                                    else -> Color(0xFFB45309)
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = bgColor,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(summary.method, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = "${ButcheryPricing.formatKsh(summary.totalKsh)} (${summary.percent}%)",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (rowItems.size == 1 && breakdownCards.size > 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Recent Sales List
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (snapshot.sales.isEmpty()) {
                            Text(
                                text = "No butchery sales recorded on $dateLabel yet.",
                                color = Color(0xFF64748B),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            snapshot.sales.forEachIndexed { idx, sale ->
                                val timeText = timeFormat.format(Date(sale.createdAt))
                                val weightText = ButcheryPricing.formatWeightKg(sale.weightGrams, decimals = 2)
                                val isMpesa = ButcheryPricing.isMpesaPayment(sale.paymentMethod)
                                val paymentLabel = ButcheryPricing.paymentMethodDisplayLabel(sale.paymentMethod)
                                val saleProfit = ButcheryPricing.computeSaleProfitKsh(sale)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "$timeText - ${sale.cutName} ($weightText)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF0F172A),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = paymentLabel +
                                                " · KES ${sale.pricePerKgKsh}/kg" +
                                                if (sale.entryMode == "amount") " · Scale price" else "",
                                            fontSize = 11.sp,
                                            color = if (isMpesa) ButcheryPalette.EmeraldHeader else Color(0xFF475569)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = ButcheryPricing.formatKsh(sale.totalKsh),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        if (saleProfit != null) {
                                            Text(
                                                text = if (saleProfit >= 0L) {
                                                    "Profit ${ButcheryPricing.formatKsh(saleProfit)}"
                                                } else {
                                                    "Loss ${ButcheryPricing.formatSignedKsh(saleProfit)}"
                                                },
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (saleProfit >= 0L) ButcheryPalette.EmeraldHeader else Color(0xFFB91C1C)
                                            )
                                        }
                                    }
                                }
                                if (idx < snapshot.sales.lastIndex) {
                                    HorizontalDivider(color = ButcheryPalette.KeypadBorder)
                                }
                            }
                        }
                    }
                }
            }
        }

        Surface(
            color = Color.White,
            border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { showExportDialog = true },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButcheryPalette.EmeraldHeader,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .height(48.dp)
                    .testTag("butchery_export_report_button")
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export Report (PDF / Excel)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Butchery Report", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose PDF for a branded summary or Excel (.csv) for spreadsheet reconciliation.",
                        fontSize = 13.sp,
                        color = Color(0xFF475569)
                    )
                    Button(
                        onClick = {
                            showExportDialog = false
                            onSavePdf(snapshot)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ButcheryPalette.EmeraldHeader),
                        modifier = Modifier.fillMaxWidth().testTag("butchery_save_pdf_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save Branded PDF")
                    }
                    OutlinedButton(
                        onClick = {
                            showExportDialog = false
                            onSharePdf(snapshot)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("butchery_share_pdf_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share PDF")
                    }
                    Button(
                        onClick = {
                            showExportDialog = false
                            onSaveExcelCsv(snapshot)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ButcheryPalette.SlateButton),
                        modifier = Modifier.fillMaxWidth().testTag("butchery_save_excel_button")
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save Excel (.csv)")
                    }
                    OutlinedButton(
                        onClick = {
                            showExportDialog = false
                            onShareExcelCsv(snapshot)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("butchery_share_excel_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share Excel (.csv)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
