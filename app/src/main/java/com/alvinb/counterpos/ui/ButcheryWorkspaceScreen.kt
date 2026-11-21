package com.alvinb.counterpos.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.alvinb.counterpos.data.butchery.ButcheryPricing
import com.alvinb.counterpos.data.local.entity.ButcheryConfigEntity
import com.alvinb.counterpos.data.local.entity.ButcheryCutEntity
import com.alvinb.counterpos.data.local.entity.ButcherySaleEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ButcheryWorkspaceScreen(
    activeTab: ButcheryTab,
    config: ButcheryConfigEntity?,
    cuts: List<ButcheryCutEntity>,
    sales: List<ButcherySaleEntity>,
    businessName: String,
    ownerCanEdit: Boolean,
    notice: String?,
    onClearNotice: () -> Unit,
    onSelectTab: (ButcheryTab) -> Unit,
    onBackToMainPos: () -> Unit,
    onSaveConfig: (
        enabled: Boolean,
        name: String,
        beefOnly: Boolean,
        defaultBeefPricePerKgKsh: Int,
        paymentMethods: List<String>,
        enabledCategories: List<String>
    ) -> Unit,
    onApplyCategoryPrice: (category: String, pricePerKgKsh: Int) -> Unit,
    onUpdateCutPrice: (cutId: String, pricePerKgKsh: Int) -> Unit,
    onUpdateCutStockKg: (cutId: String, stockKg: Double) -> Unit,
    onToggleCutActive: (cutId: String, active: Boolean) -> Unit,
    onAddCut: (name: String, category: String, pricePerKgKsh: Int, stockKg: Double, iconKey: String) -> Unit,
    onRecordSale: (cut: ButcheryCutEntity, weightGrams: Int, totalKsh: Int, paymentMethod: String, entryMode: String) -> Unit,
    onUndoLastSale: () -> Unit
) {
    BackHandler {
        if (activeTab != ButcheryTab.SELL) {
            onSelectTab(ButcheryTab.SELL)
        } else {
            onBackToMainPos()
        }
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingPdfBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingCsvText by remember { mutableStateOf<String?>(null) }

    val pdfSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val bytes = pendingPdfBytes
        pendingPdfBytes = null
        if (uri != null && bytes != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        }
    }
    val csvSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val text = pendingCsvText
        pendingCsvText = null
        if (uri != null && text != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
    }

    fun shareFile(bytes: ByteArray, mimeType: String, filename: String, title: String) {
        val dir = File(context.cacheDir, "shared_reports").apply { mkdirs() }
        val file = File(dir, filename).apply { writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    LaunchedEffect(notice) {
        notice?.let {
            snackbarHostState.showSnackbar(it)
            onClearNotice()
        }
    }

    val resolvedButcheryName = config?.butcheryName?.takeIf(String::isNotBlank)
        ?: if (businessName.isNotBlank()) "$businessName Butchery" else "Butchery POS"
    val fileDateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val canUndo = remember(sales) { sales.any { !it.undone } }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ButcheryNavItem(
                        label = "Quick Sale",
                        icon = Icons.Default.Scale,
                        selected = activeTab == ButcheryTab.SELL,
                        tag = "butchery_nav_sell",
                        onClick = { onSelectTab(ButcheryTab.SELL) },
                        modifier = Modifier.weight(1f)
                    )
                    ButcheryNavItem(
                        label = "Daily Sales",
                        icon = Icons.Default.BarChart,
                        selected = activeTab == ButcheryTab.DAILY_SALES,
                        tag = "butchery_nav_daily_sales",
                        onClick = { onSelectTab(ButcheryTab.DAILY_SALES) },
                        modifier = Modifier.weight(1f)
                    )
                    ButcheryNavItem(
                        label = "Report",
                        icon = Icons.Default.Assessment,
                        selected = activeTab == ButcheryTab.REPORT,
                        tag = "butchery_nav_report",
                        onClick = { onSelectTab(ButcheryTab.REPORT) },
                        modifier = Modifier.weight(1f)
                    )
                    ButcheryNavItem(
                        label = "Settings",
                        icon = Icons.Default.Settings,
                        selected = activeTab == ButcheryTab.SETTINGS,
                        tag = "butchery_nav_settings",
                        onClick = { onSelectTab(ButcheryTab.SETTINGS) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (activeTab) {
                ButcheryTab.SELL -> ButcheryPosScreen(
                    config = config,
                    cuts = cuts,
                    canUndoLastSale = canUndo,
                    onBack = onBackToMainPos,
                    onOpenSettings = { onSelectTab(ButcheryTab.SETTINGS) },
                    onUndoLastSale = onUndoLastSale,
                    onRecordSale = onRecordSale
                )
                ButcheryTab.DAILY_SALES -> ButcheryDailySalesScreen(
                    config = config,
                    sales = sales,
                    onBack = { onSelectTab(ButcheryTab.SELL) }
                )
                ButcheryTab.REPORT -> {
                    val enabledCategories = remember(config) { ButcheryPricing.resolveEnabledCategories(config) }
                    val singleMeatLabel = if (enabledCategories.size == 1) enabledCategories.first() else null
                    ButcheryReportScreen(
                        config = config,
                        sales = sales,
                        onBack = { onSelectTab(ButcheryTab.SELL) },
                        onUndoLatestSale = onUndoLastSale,
                        onOpenTrends = { onSelectTab(ButcheryTab.DAILY_SALES) },
                        onSavePdf = { snapshot ->
                            pendingPdfBytes = buildButcheryReportPdf(
                                context = context,
                                butcheryName = resolvedButcheryName,
                                snapshot = snapshot,
                                beefOnlyMode = config?.beefOnlyMode == true,
                                singleMeatLabel = singleMeatLabel
                            )
                            pdfSaver.launch("butchery-report-${fileDateFormat.format(Date(snapshot.startMs))}.pdf")
                        },
                        onSharePdf = { snapshot ->
                            val bytes = buildButcheryReportPdf(
                                context = context,
                                butcheryName = resolvedButcheryName,
                                snapshot = snapshot,
                                beefOnlyMode = config?.beefOnlyMode == true,
                                singleMeatLabel = singleMeatLabel
                            )
                            shareFile(
                                bytes = bytes,
                                mimeType = "application/pdf",
                                filename = "butchery-report-${fileDateFormat.format(Date(snapshot.startMs))}.pdf",
                                title = "Share Butchery PDF Report"
                            )
                        },
                        onSaveExcelCsv = { snapshot ->
                            pendingCsvText = ButcheryPricing.buildExcelCsv(resolvedButcheryName, snapshot)
                            csvSaver.launch("butchery-report-${fileDateFormat.format(Date(snapshot.startMs))}.csv")
                        },
                        onShareExcelCsv = { snapshot ->
                            val csv = ButcheryPricing.buildExcelCsv(resolvedButcheryName, snapshot)
                            shareFile(
                                bytes = csv.toByteArray(Charsets.UTF_8),
                                mimeType = "text/csv",
                                filename = "butchery-report-${fileDateFormat.format(Date(snapshot.startMs))}.csv",
                                title = "Share Butchery Excel Report"
                            )
                        }
                    )
                }
                ButcheryTab.SETTINGS -> ButcherySettingsScreen(
                    config = config,
                    cuts = cuts,
                    businessName = businessName,
                    ownerCanEdit = ownerCanEdit,
                    onBack = { onSelectTab(ButcheryTab.SELL) },
                    onSaveConfig = onSaveConfig,
                    onApplyCategoryPrice = onApplyCategoryPrice,
                    onUpdateCutPrice = onUpdateCutPrice,
                    onUpdateCutStockKg = onUpdateCutStockKg,
                    onToggleCutActive = onToggleCutActive,
                    onAddCut = onAddCut
                )
            }
        }
    }
}

@Composable
private fun ButcheryNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) ButcheryPalette.EmeraldHeader else Color(0xFFF1F5F9),
        modifier = modifier
            .height(54.dp)
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else Color(0xFF475569),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else Color(0xFF334155),
                maxLines = 1
            )
        }
    }
}
