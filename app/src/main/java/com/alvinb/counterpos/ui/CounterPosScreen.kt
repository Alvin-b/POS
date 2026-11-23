package com.alvinb.counterpos.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.widget.Toast
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alvinb.counterpos.data.local.entity.DiningTableEntity
import com.alvinb.counterpos.data.local.entity.OfflineTransactionLogEntity
import com.alvinb.counterpos.data.local.entity.OfflineTransactionStatus
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.OutboxEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.local.entity.SaleUnit
import com.alvinb.counterpos.data.local.entity.parseSaleUnits
import com.alvinb.counterpos.data.local.entity.resolveSaleUnits
import com.alvinb.counterpos.data.remote.ServiceCounter
import com.alvinb.counterpos.ui.scanner.BarcodeScannerScreen
import com.alvinb.counterpos.ui.scanner.ScannerMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CounterPosWorkspace(
    viewModel: CounterPosViewModel,
    themeMode: String = "system",
    onThemeModeChange: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val cachedProducts by viewModel.products.collectAsStateWithLifecycle()
    val roomLowStockProducts by viewModel.lowStockProducts.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val diningTables by viewModel.diningTables.collectAsStateWithLifecycle()
    val tableOrders by viewModel.tableOrders.collectAsStateWithLifecycle()
    val serviceCounters by viewModel.serviceCounters.collectAsStateWithLifecycle()
    val butcheryConfig by viewModel.butcheryConfig.collectAsStateWithLifecycle()
    val butcheryCuts by viewModel.butcheryCuts.collectAsStateWithLifecycle()
    val butcherySales by viewModel.butcherySales.collectAsStateWithLifecycle()
    val products = remember(cachedProducts, state.counterStockByProduct, state.selectedCounterId, session.activeBusiness?.counterInventoryMode) {
        if (session.activeBusiness?.counterInventoryMode == "per_counter") {
            cachedProducts.map { product ->
                if (product.inMyStock) {
                    val qty = product.remoteId?.let { state.counterStockByProduct[it] }
                        ?: state.counterStockByProduct[product.catalogCode]
                        ?: 0
                    product.copy(stockQuantity = qty)
                } else product
            }
        } else cachedProducts
    }
    val lowStockCatalogCodes = remember(products, roomLowStockProducts, session.activeBusiness?.counterInventoryMode) {
        val isPerCounter = session.activeBusiness?.counterInventoryMode == "per_counter"
        val source = if (isPerCounter) products else roomLowStockProducts
        source.asSequence()
            .filter { isLowOrOutOfStock(it) }
            .map { it.catalogCode }
            .toSet()
    }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    var photoTargetCode by remember { mutableStateOf<String?>(null) }
    var transferProductCode by remember { mutableStateOf<String?>(null) }
    var pendingSalesPdf by remember { mutableStateOf<ByteArray?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val code = photoTargetCode
        photoTargetCode = null
        if (uri != null && code != null) {
            products.firstOrNull { it.catalogCode == code }?.let { product ->
                viewModel.updatePhoto(product, uri.toString())
            }
        }
    }

    val salesPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val pdf = pendingSalesPdf
        pendingSalesPdf = null
        if (uri != null && pdf != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(pdf) }
        }
    }

    val summary = remember(sales) { salesTodaySummary(sales) }
    val role = session.activeBusiness?.role.orEmpty()
    val enabledFeatures = session.activeBusiness?.featureFlags.orEmpty()
    val rawBusinessType = session.activeBusiness?.businessType.orEmpty().trim().lowercase()
    val isClub = rawBusinessType in setOf("big_club", "club", "") || session.activeBusiness == null
    val supportsTables = "tables" in enabledFeatures
    val supportsSalesAnalytics = "sales_analytics" in enabledFeatures
    val canManageTables = role in setOf("owner", "manager", "cashier")
    val canDeleteTables = role in setOf("owner", "manager")
    val canSettleTables = role in setOf("owner", "manager", "cashier")
    val registeredWaiterEmails = remember(state.employeeMembers) {
        state.employeeMembers.asSequence()
            .filter { it.optString("role").equals("cashier", ignoreCase = true) }
            .map { it.optString("email").trim().lowercase(Locale.ROOT) }
            .filter(String::isNotBlank)
            .toSet()
    }
    LaunchedEffect(session.activeBusiness?.shopId, role) {
        if (role in setOf("owner", "manager")) viewModel.loadEmployeeMembers()
    }
    LaunchedEffect(supportsTables, supportsSalesAnalytics, state.tab) {
        if ((!supportsTables && state.tab == PosTab.TABLES) || (!supportsSalesAnalytics && state.tab == PosTab.REPORTS)) {
            viewModel.selectTab(PosTab.SCAN)
        }
    }
    val canManageStock = role in setOf("owner", "manager", "inventory_clerk")
    val canReceiveStock = role in setOf("owner", "manager", "inventory_clerk")
    val canEditProducts = role in setOf("owner", "manager")
    LaunchedEffect(canManageStock, state.tab) {
        if (!canManageStock && state.tab == PosTab.STOCK) viewModel.selectTab(PosTab.SCAN)
    }

    LaunchedEffect(session.activeBusiness?.shopId, serviceCounters, state.selectedCounterId) {
        val selectedId = state.selectedServingCounterId ?: state.selectedCounterId
        if (selectedId !in serviceCounters.map { it.id }) {
            serviceCounters.firstOrNull()?.let { viewModel.selectServingCounter(it.id, it.name) }
        }
    }
    LaunchedEffect(state.selectedCounterId, session.activeBusiness?.counterInventoryMode, session.activeBusiness?.shopId) {
        if (state.counterStockByProduct.isEmpty()) {
            viewModel.refreshCounterStock()
        }
    }
    LaunchedEffect(session.activeBusiness?.shopId) {
        viewModel.refreshBusinessData(force = false)
    }
    LaunchedEffect(state.tab, supportsSalesAnalytics) {
        if (supportsSalesAnalytics && state.tab == PosTab.REPORTS) viewModel.loadCounterReport(todayStartIso(), nowIso())
    }

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotice()
        }
    }

    val topBarSubtitle = when (state.tab) {
        PosTab.SCAN -> if (summary.second > 0) "${ksh(summary.first)} · ${summary.second} sold" else "Ready for your next sale"
        PosTab.STOCK -> "Manage your shelf"
        PosTab.SALES -> "Sales history and receipts"
        PosTab.REPORTS -> "Counter and waiter activity"
        PosTab.TABLES -> serviceCounters.firstOrNull { it.id == state.selectedCounterId }
            ?.takeIf { serviceCounters.size > 1 }
            ?.let { "${it.name} · Service floor" }
            ?: "Service floor"
    }

    // Camera Scanner view
    if (state.isScannerOpen) {
        BarcodeScannerScreen(
            mode = state.scannerMode,
            products = products,
            onClose = viewModel::closeScanner,
            onProductScannedForSale = viewModel::handleScannedSale,
            onProductScannedForStock = viewModel::handleScannedStock,
            onProductScannedForTable = viewModel::handleScannedTableProduct,
            onAdminQrCodeScanned = viewModel::handleAdminQrCode,
            onBarcodeAssigned = viewModel::handleAssignedBarcode,
            onBarcodeNotRecognized = viewModel::handleUnrecognizedBarcode
        )
        return
    }

    if (state.isButcheryWorkspaceOpen) {
        ButcheryWorkspaceScreen(
            activeTab = state.butcheryTab,
            config = butcheryConfig,
            cuts = butcheryCuts,
            sales = butcherySales,
            businessName = session.activeBusiness?.shopName.orEmpty(),
            ownerCanEdit = role.equals("owner", ignoreCase = true) || role.equals("manager", ignoreCase = true),
            notice = state.notice,
            onClearNotice = viewModel::clearNotice,
            onSelectTab = viewModel::selectButcheryTab,
            onBackToMainPos = viewModel::closeButcheryWorkspace,
            onSaveConfig = { enabled, name, beefOnly, price, methods, categories ->
                viewModel.enrollOrUpdateButchery(enabled, name, beefOnly, price, methods, categories)
            },
            onApplyCategoryPrice = viewModel::applyCategoryPricePerKg,
            onUpdateCutPrice = viewModel::updateButcheryCutPrice,
            onUpdateCutStockKg = viewModel::setButcheryCutStockKg,
            onToggleCutActive = viewModel::setButcheryCutActive,
            onAddCut = viewModel::addButcheryCut,
            onRecordSale = viewModel::recordButcherySale,
            onUndoLastSale = viewModel::undoLatestButcherySale,
            onOpenButcheryEnrollment = if (role.equals("owner", ignoreCase = true)) {
                {
                    viewModel.closeButcheryWorkspace()
                    viewModel.openStaffManagement(openButchery = true)
                }
            } else null
        )
        return
    }

    BackHandler {
        when {
            state.isTeamPanelOpen -> viewModel.closeTeamPanel()
            state.isStaffManagementOpen -> viewModel.closeStaffManagement()
            state.tab != PosTab.SCAN && state.previousTab != null -> viewModel.goBackToPreviousTab()
            state.tab != PosTab.SCAN -> viewModel.selectTab(PosTab.SCAN)
            else -> Unit
        }
    }

    val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glowAccent = if (isDarkSurface) SerenityTokens.GlowDark else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(glowAccent.copy(alpha = 0.72f), Color.Transparent),
                        center = Offset(size.width * 0.85f, -size.height * 0.05f),
                        radius = size.maxDimension * 0.75f
                    )
                )
            }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        if (state.isSearchActive && state.searchQuery.isBlank()) {
                            viewModel.setSearchActive(false)
                        }
                    })
                },
            topBar = {
                Column {
                LeanTopBar(
                    modifier = Modifier.statusBarsPadding(),
                    state = state,
                    businessName = session.activeBusiness?.shopName ?: "Counter POS",
                    subtitle = topBarSubtitle,
                    isPlatformAdmin = session.isPlatformAdmin,
                    onExitToAdmin = viewModel::exitToAdminPortal,
                    onSearchToggle = { active -> viewModel.setSearchActive(active) },
                    onSearchChange = viewModel::setSearchQuery,
                    onClearSearch = viewModel::clearSearch,
                    onOpenScanner = { viewModel.openScanner(if (state.tab == PosTab.STOCK && canManageStock) ScannerMode.TAKE_STOCK else ScannerMode.SELL) },
                    onAddNewProduct = { viewModel.openAddNewProduct() },
                    canAddNewProduct = canReceiveStock,
                    canManageTeam = role in setOf("owner", "manager"),
                    onManageTeam = viewModel::openTeamPanel,
                    onManageStaff = { viewModel.openStaffManagement() },
                    counters = serviceCounters,
                    selectedCounterId = state.selectedCounterId,
                    onCounterSelect = viewModel::selectCounter,
                    onSyncData = { viewModel.synchronizeData(showNotice = true) },
                    isButcheryEnabled = butcheryConfig?.enabled == true,
                    onOpenButchery = { viewModel.openButcheryWorkspace(ButcheryTab.SELL) }
                )
                if (!state.isOnline) {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Offline mode · Transactions logged locally for auto-sync when stable connection returns.",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = viewModel::openSyncRecovery,
                                modifier = Modifier.testTag("open_transaction_log_button")
                            ) {
                                Text("Log (${state.pendingSyncCount})", color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if ((state.pendingSyncCount > 0 || state.isBackgroundOutboxSyncing) && state.blockedOutbox.isEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.isBackgroundOutboxSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                when {
                                    state.isBackgroundOutboxSyncing && state.pendingSyncCount > 0 ->
                                        "Syncing ${state.pendingSyncCount} queued transaction${if (state.pendingSyncCount == 1) "" else "s"} to Supabase…"
                                    state.isBackgroundOutboxSyncing ->
                                        "Syncing POS updates in background…"
                                    state.isConnectionStable ->
                                        "${state.pendingSyncCount} queued transaction${if (state.pendingSyncCount == 1) "" else "s"} queued for background sync"
                                    else ->
                                        "${state.pendingSyncCount} queued transaction${if (state.pendingSyncCount == 1) "" else "s"} waiting for stable connection"
                                },
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            TextButton(
                                onClick = viewModel::openSyncRecovery,
                                modifier = Modifier.testTag("open_pending_queue_button")
                            ) {
                                Text("Queue", color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                val entitlement = state.subscriptionEntitlement
                if (entitlement != null && entitlement.isGracePeriod) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Offline Grace: ${entitlement.hoursGraceRemaining}h remaining to renew subscription.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = viewModel::openSubscriptionModal) {
                                Text("Renew", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
                if (state.blockedOutbox.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${state.blockedOutbox.size} saved change${if (state.blockedOutbox.size == 1) "" else "s"} need attention",
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp
                            )
                            TextButton(onClick = viewModel::openSyncRecovery) { Text("Review", color = MaterialTheme.colorScheme.onErrorContainer) }
                        }
                    }
                }
            }
        },
        bottomBar = {
            SerenityFloatingBottomNav(
                selectedTab = state.tab,
                canManageStock = canManageStock,
                supportsSalesAnalytics = supportsSalesAnalytics,
                supportsTables = supportsTables,
                lowStockCount = lowStockCatalogCodes.size,
                isDarkSurface = isDarkSurface,
                onSelectTab = viewModel::selectTab
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        if (!state.isReady && products.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            when (state.tab) {
                PosTab.SCAN -> FullscreenSellScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    state = state,
                    products = products,
                    onProductClick = viewModel::openSale,
                    onOpenStock = if (canManageStock) { { viewModel.selectTab(PosTab.STOCK) } } else null
                )
                PosTab.STOCK -> if (canManageStock) {
                    FullscreenStockScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        state = state,
                        products = products,
                        lowStockCatalogCodes = lowStockCatalogCodes,
                        onModeChange = viewModel::setStockMode,
                        onCategoryChange = viewModel::setLibraryCategory,
                        onAddStockClick = viewModel::openAddStock,
                        onEditProductClick = viewModel::openEditProduct,
                        onScanStockClick = { viewModel.openScanner(ScannerMode.TAKE_STOCK) },
                        onAddCatalogClick = viewModel::openCatalogItem,
                        canReceiveStock = canReceiveStock,
                        canEditProducts = canEditProducts,
                        canTransferStock = session.activeBusiness?.counterInventoryMode == "per_counter" && role in setOf("owner", "manager") && serviceCounters.size > 1,
                        onTransferStock = { product -> transferProductCode = product.catalogCode },
                        onChangePhoto = { product ->
                            photoTargetCode = product.catalogCode
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                } else {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Text("You do not have permission to adjust stock.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                PosTab.SALES -> {
                    val syncStatusBySaleId = remember(state.transactionLogs) {
                        buildMap {
                            state.transactionLogs.forEach { log ->
                                val saleId = log.localSaleId
                                if (saleId != null && !containsKey(saleId)) {
                                    put(saleId, log.status)
                                }
                            }
                        }
                    }
                    FullscreenSalesScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        sales = sales,
                        products = products,
                        counters = serviceCounters,
                        todaySummary = summary,
                        syncStatusBySaleId = syncStatusBySaleId,
                        searchQuery = state.searchQuery,
                        onClearSearch = viewModel::clearSearch,
                        onUndo = viewModel::undoLastSale,
                    onExportPdf = { start, end ->
                        val filtered = sales.filter { !it.undone && it.createdAt in start..end }
                        pendingSalesPdf = buildBrandedReportPdf(
                            context = context,
                            businessName = session.activeBusiness?.shopName ?: "Counter POS",
                            sales = filtered,
                            products = products,
                            counters = serviceCounters,
                            startMs = start,
                            endMs = end,
                            title = "Sales Statement"
                        )
                        salesPdfPicker.launch("sales-${formatReportDate(start)}-${formatReportDate(end)}.pdf")
                    },
                    onSharePdf = { start, end ->
                        val filtered = sales.filter { !it.undone && it.createdAt in start..end }
                        sharePdf(
                            context,
                            buildBrandedReportPdf(
                                context = context,
                                businessName = session.activeBusiness?.shopName ?: "Counter POS",
                                sales = filtered,
                                products = products,
                                counters = serviceCounters,
                                startMs = start,
                                endMs = end,
                                title = "Sales Statement"
                            ),
                            "sales-${formatReportDate(start)}.pdf"
                        )
                    }
                )
                }
                PosTab.REPORTS -> CounterReportScreen(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    businessName = session.activeBusiness?.shopName ?: "Counter POS",
                    counters = serviceCounters,
                    registeredWaiterEmails = registeredWaiterEmails,
                    sales = sales,
                    products = products,
                    diningTables = diningTables,
                    tableOrders = tableOrders,
                    supportsTables = supportsTables,
                    remoteRows = state.counterReport,
                    isLoading = state.isLoadingCounterReport,
                    reportFilter = state.reportFilter,
                    reportCounterFilter = state.reportCounterFilter,
                    reportPaymentFilter = state.reportPaymentFilter,
                    onSetReportFilter = viewModel::setReportFilter,
                    onSetReportPaymentFilter = viewModel::setReportPaymentFilter,
                    onSetReportCounterFilter = viewModel::setReportCounterFilter,
                    onRefresh = { viewModel.loadCounterReport(todayStartIso(), nowIso()) },
                    onOpenRecordDispensation = viewModel::openRecordDispensation,
                    onQuickDispenseForCounter = { counter ->
                        viewModel.selectServingCounter(counter.id, counter.name)
                        viewModel.openRecordDispensation()
                    },
                    onExportPdf = { snapshot ->
                        val paymentTag = when (state.reportPaymentFilter) {
                            "cash" -> "Cash Only"
                            "mpesa" -> "M-Pesa Only"
                            else -> "All Payments"
                        }
                        val counterTag = if (state.reportCounterFilter == "all") {
                            if (serviceCounters.size > 1) "All Counters" else null
                        } else {
                            serviceCounters.firstOrNull { it.id == state.reportCounterFilter }?.name
                        }
                        val filterSummary = listOfNotNull(snapshot.periodLabel, paymentTag, counterTag).joinToString(" · ")
                        pendingSalesPdf = buildBrandedReportPdf(
                            context = context,
                            businessName = session.activeBusiness?.shopName ?: "Counter POS",
                            sales = snapshot.filteredSales,
                            products = products,
                            counters = serviceCounters,
                            snapshot = snapshot,
                            startMs = snapshot.windowStartMs,
                            endMs = snapshot.windowEndMs,
                            title = "Performance Report",
                            filterSummary = filterSummary
                        )
                        salesPdfPicker.launch("report-${formatReportDate(snapshot.windowStartMs)}.pdf")
                    },
                    onSharePdf = { snapshot ->
                        val paymentTag = when (state.reportPaymentFilter) {
                            "cash" -> "Cash Only"
                            "mpesa" -> "M-Pesa Only"
                            else -> "All Payments"
                        }
                        val counterTag = if (state.reportCounterFilter == "all") {
                            if (serviceCounters.size > 1) "All Counters" else null
                        } else {
                            serviceCounters.firstOrNull { it.id == state.reportCounterFilter }?.name
                        }
                        val filterSummary = listOfNotNull(snapshot.periodLabel, paymentTag, counterTag).joinToString(" · ")
                        sharePdf(
                            context,
                            buildBrandedReportPdf(
                                context = context,
                                businessName = session.activeBusiness?.shopName ?: "Counter POS",
                                sales = snapshot.filteredSales,
                                products = products,
                                counters = serviceCounters,
                                snapshot = snapshot,
                                startMs = snapshot.windowStartMs,
                                endMs = snapshot.windowEndMs,
                                title = "Performance Report",
                                filterSummary = filterSummary
                            ),
                            "report-${formatReportDate(snapshot.windowStartMs)}.pdf"
                        )
                    }
                )
                PosTab.TABLES -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    TablesScreen(
                        tables = diningTables,
                        orders = tableOrders,
                        products = products,
                        selectedTableId = state.selectedTableId,
                        actionError = state.actionError,
                        isCreatingTable = state.isCreatingTable,
                        canManageTables = canManageTables,
                        canDeleteTables = canDeleteTables,
                        canSettleTables = canSettleTables,
                        isSettlingTable = state.isSettlingTable,
                        isRefreshingTables = state.isRefreshingBusinessData,
                        pendingSettlementTableIds = state.pendingTableSettlementIds,
                        blockedSettlementTableIds = state.blockedTableSettlementIds,
                        isDeletingTable = state.isDeletingTable,
                        onCreateTable = viewModel::createDiningTable,
                        onDeleteTable = viewModel::deleteDiningTable,
                        onSettleTable = { tableId, paymentMethod, onComplete ->
                            viewModel.settleTableBill(tableId, paymentMethod, onComplete)
                        },
                        onOpenTable = viewModel::openTable,
                        onBackToTables = viewModel::backToTables,
                        onNextTable = viewModel::selectNextTable,
                        onRefresh = { viewModel.refreshBusinessData() },
                        onAddProduct = viewModel::openTableSale,
                        onScanProduct = { viewModel.openScanner(ScannerMode.TABLE_ADD) },
                        showCounterLabels = serviceCounters.size > 1,
                        registeredWaiterEmails = registeredWaiterEmails,
                        businessName = session.activeBusiness?.shopName ?: "Counter POS",
                        searchQuery = state.searchQuery,
                        onClearSearch = viewModel::clearSearch
                    )
                }
            }
        }
    }
    }

    if (state.isSyncRecoveryOpen) {
        val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
        val queuedLogsCount = remember(state.transactionLogs) {
            state.transactionLogs.count {
                it.status in setOf(
                    OfflineTransactionStatus.QUEUED,
                    OfflineTransactionStatus.SYNCING,
                    OfflineTransactionStatus.RETRY_WAITING,
                    OfflineTransactionStatus.BLOCKED
                )
            }
        }
        val syncedLogsCount = remember(state.transactionLogs) {
            state.transactionLogs.count { it.status == OfflineTransactionStatus.SYNCED }
        }
        AlertDialog(
            onDismissRequest = viewModel::closeSyncRecovery,
            modifier = Modifier.testTag("transaction_log_dialog"),
            title = { Text("Offline Transaction Log") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        when {
                            !state.isOnline -> "Offline · Transactions stay queued in Room and flush automatically via WorkManager when stable internet returns."
                            state.isConnectionStable -> "Stable connection detected · WorkManager flushes queued transactions in background without slowing POS."
                            else -> "Limited connectivity · Waiting for validated internet before flushing to Supabase."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                "Queued: $queuedLogsCount",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "Synced: $syncedLogsCount",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (state.pendingSyncCount > 0 && state.isOnline) {
                            TextButton(
                                onClick = viewModel::flushOfflineQueueNow,
                                enabled = !state.isSyncingData && !state.isBackgroundOutboxSyncing,
                                modifier = Modifier.testTag("flush_queue_button")
                            ) {
                                Text(
                                    if (state.isSyncingData || state.isBackgroundOutboxSyncing) "Syncing…" else "Flush Now",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    if (!state.syncRecoveryError.isNullOrBlank()) {
                        Text(state.syncRecoveryError.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    if (state.blockedOutbox.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Needs Attention", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                            if (state.blockedOutbox.size > 1 && state.isOnline) {
                                TextButton(
                                    onClick = viewModel::retryAllBlockedOutbox,
                                    enabled = state.retryingOperationId == null,
                                    modifier = Modifier.testTag("retry_all_blocked_button")
                                ) {
                                    Text("Retry All", fontSize = 12.sp)
                                }
                            }
                        }
                        state.blockedOutbox.forEach { item ->
                            val label = when (item.operationType) {
                                "sale" -> "Sale"
                                "table_sale" -> "Table sale"
                                "create_dining_table" -> "Create table"
                                "add_catalog_product" -> "Add product to stock"
                                "register_product" -> "Register product"
                                "receive_stock" -> "Receive stock"
                                "transfer_stock" -> "Transfer stock"
                                else -> item.operationType.replace('_', ' ').replaceFirstChar { it.uppercase() }
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f))) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(label, fontWeight = FontWeight.SemiBold)
                                    Text(item.lastError ?: "The server could not accept this saved change.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(
                                        onClick = { viewModel.retryBlockedOutbox(item.operationId) },
                                        enabled = state.retryingOperationId == null
                                    ) {
                                        if (state.retryingOperationId == item.operationId) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        else Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                    if (state.transactionLogs.isEmpty()) {
                        Text("No local offline or queued transactions recorded yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.transactionLogs.take(40).forEach { log ->
                            val (statusLabel, badgeColor, badgeTextColor) = when (log.status) {
                                OfflineTransactionStatus.SYNCED -> Triple("Synced", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                OfflineTransactionStatus.SYNCING -> Triple("Syncing", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                OfflineTransactionStatus.RETRY_WAITING -> Triple("Retrying", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                OfflineTransactionStatus.BLOCKED -> Triple("Blocked", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                                OfflineTransactionStatus.CANCELLED -> Triple("Cancelled", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                                else -> Triple("Queued", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                                Column(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            log.summary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Surface(shape = RoundedCornerShape(6.dp), color = badgeColor) {
                                            Text(
                                                statusLabel,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeTextColor
                                            )
                                        }
                                    }
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val origin = if (log.recordedOffline) "Offline · " else ""
                                        val attemptsSuffix = if (log.attempts > 0) " · ${log.attempts} retries" else ""
                                        Text(
                                            "$origin${timeFmt.format(Date(log.createdAt))}$attemptsSuffix",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (log.amountKsh > 0L) {
                                            Text(
                                                "KSh " + NumberFormat.getIntegerInstance(Locale.US).format(log.amountKsh),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    if (!log.lastError.isNullOrBlank()) {
                                        Text(
                                            log.lastError,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::closeSyncRecovery) { Text("Done") } }
        )
    }

    // Modal: Record Counter Dispensation (Simple logging of drinks taken from a counter)
    if (state.isRecordDispensationOpen) {
        RecordDispensationBottomSheet(
            serviceCounters = serviceCounters,
            initialCounterId = state.selectedServingCounterId ?: state.selectedCounterId ?: serviceCounters.firstOrNull()?.id,
            diningTables = diningTables,
            products = products.filter { it.inMyStock && it.stockQuantity > 0 },
            initialWaiter = session.email,
            initialTableId = state.activeTableIdForSale(),
            supportsTables = supportsTables,
            error = state.actionError,
            onDismiss = viewModel::dismissRecordDispensation,
            onRecord = { counterId, counterName, waiterName, tableId, tableName, productCode, quantity, unitName, unitPriceKsh ->
                viewModel.recordDirectDispensation(
                    counterId = counterId,
                    counterName = counterName,
                    waiterName = waiterName,
                    tableId = tableId,
                    tableName = tableName,
                    productCode = productCode,
                    quantity = quantity,
                    unitName = unitName,
                    unitPriceKsh = unitPriceKsh
                )
            }
        )
    }

    // Modal: Sell Product with Units of Sale (e.g. Bottle, Glass, Cup)
    state.selectedSaleCode?.let { code ->
        products.firstOrNull { it.catalogCode == code }?.let { product ->
            val selectedTable = diningTables.firstOrNull { it.id == state.activeTableIdForSale() }
            ModalBottomSheet(
                onDismissRequest = viewModel::dismissSale,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                SaleWithUnitsSheet(
                    product = product,
                    selectedUnitName = state.selectedUnitName,
                    selectedUnitPrice = state.selectedUnitPrice,
                    quantity = state.saleQuantity,
                    error = state.actionError,
                    serviceCounters = serviceCounters,
                    selectedCounterId = state.selectedServingCounterId ?: state.selectedCounterId ?: serviceCounters.firstOrNull()?.id,
                    onSelectCounter = { id, name -> viewModel.selectServingCounter(id, name) },
                    selectedWaiter = session.email,
                    selectedPaymentMethod = state.selectedPaymentMethod,
                    selectedTableName = selectedTable?.name,
                    onSelectUnit = viewModel::selectSaleUnit,
                    onQuantityChange = viewModel::adjustSaleQuantity,
                    onSelectPaymentMethod = viewModel::selectPaymentMethod,
                    onConfirm = viewModel::confirmSale
                )
            }
        }
    }

    // Modal: Add bottles to stock
    state.addStockProductCode?.let { code ->
        products.firstOrNull { it.catalogCode == code }?.let { product ->
            AddBottlesDialog(
                product = product,
                error = state.actionError,
                onDismiss = viewModel::dismissAddStock,
                onConfirm = { bottles, unitCost -> viewModel.confirmAddStock(product, bottles, unitCost) }
            )
        }
    }

    // Modal: Edit Product & Stock details
    state.editProductCode?.let { code ->
        products.firstOrNull { it.catalogCode == code }?.let { product ->
            EditProductDialog(
                product = product,
                error = state.actionError,
                onDismiss = viewModel::dismissEditProduct,
                onConfirm = { name, stock, price, units, barcode, photoUri ->
                    viewModel.confirmEditProduct(product, name, stock, price, units, barcode, photoUri)
                },
                onScanBarcode = {
                    viewModel.dismissEditProduct()
                    viewModel.startBarcodeAssignment(product)
                }
            )
        }
    }

    // Modal: Add New Product with Configurable Units of Sale (e.g. Local liquor: cup, glass, price)
    if (state.isAddingNewProduct) {
        AddNewProductDialog(
            prefilledBarcode = state.prefilledBarcode,
            error = state.actionError,
            onDismiss = viewModel::dismissAddNewProduct,
            onScanBarcode = {
                viewModel.dismissAddNewProduct()
                viewModel.openScanner(ScannerMode.TAKE_STOCK)
            },
            onConfirm = { name, barcode, cat, price, stock, unitCost, units, photoUri ->
                viewModel.confirmCreateProduct(name, barcode, cat, price, stock, unitCost, units, photoUri = photoUri)
            }
        )
    }

    // Modal: Add from Beverage Catalog
    state.selectedCatalogCode?.let { code ->
        products.firstOrNull { it.catalogCode == code }?.let { product ->
            AddCatalogItemDialog(
                product = product,
                error = state.actionError,
                onDismiss = viewModel::dismissCatalogItem,
                onConfirm = { qty, price, unitCost ->
                    viewModel.confirmAddCatalogItem(code, qty, price, unitCost)
                }
            )
        }
    }

    transferProductCode?.let { code ->
        val product = products.firstOrNull { it.catalogCode == code }
        if (product == null) transferProductCode = null else {
            val destinations = serviceCounters.filter { it.id != state.selectedCounterId }
            var destinationId by remember(code, serviceCounters, state.selectedCounterId) { mutableStateOf(destinations.firstOrNull()?.id.orEmpty()) }
            var transferQuantity by remember(code) { mutableStateOf("1") }
            val quantity = transferQuantity.toIntOrNull() ?: 0
            AlertDialog(
                onDismissRequest = { transferProductCode = null },
                title = { Text("Move ${product.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("From ${serviceCounters.firstOrNull { it.id == state.selectedCounterId }?.name ?: "selected counter"} · ${product.stockQuantity} available")
                        Text("Move to", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            destinations.forEach { counter ->
                                FilterChip(selected = destinationId == counter.id, onClick = { destinationId = counter.id }, label = { Text(counter.name) })
                            }
                        }
                        OutlinedTextField(value = transferQuantity, onValueChange = { transferQuantity = it.filter(Char::isDigit).take(6) }, label = { Text("Quantity") }, singleLine = true)
                        state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.transferCounterStock(code, destinationId, quantity)
                        transferProductCode = null
                    }, enabled = destinationId.isNotBlank() && quantity in 1..product.stockQuantity) { Text("Move stock") }
                },
                dismissButton = { TextButton(onClick = { transferProductCode = null }) { Text("Cancel") } }
            )
        }
    }

    if (state.isTeamPanelOpen && role in setOf("owner", "manager") && "employee_management" in enabledFeatures) {
        Dialog(
            onDismissRequest = viewModel::closeTeamPanel,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            OwnerSettingsScreen(
                state = state,
                businessName = session.activeBusiness?.shopName.orEmpty(),
                role = role,
                businessType = session.activeBusiness?.businessType ?: "big_club",
                registeredCounters = serviceCounters,
                butcheryConfig = butcheryConfig,
                butcheryCuts = butcheryCuts,
                onClose = viewModel::closeTeamPanel,
                onBusinessNameSave = { viewModel.updateBusinessName(it) },
                onLoadMembers = { viewModel.loadEmployeeMembers() },
                onCounterCreate = { name -> viewModel.createServiceCounter(name) },
                onCounterRename = { id, name -> viewModel.renameServiceCounter(id, name) },
                onCounterDelete = { id -> viewModel.deleteServiceCounter(id) },
                onSaveButcheryConfig = { enabled, name, beefOnly, price, methods, categories ->
                    viewModel.enrollOrUpdateButchery(enabled, name, beefOnly, price, methods, categories)
                },
                onOpenButcheryWorkspace = { tab -> viewModel.openButcheryWorkspace(tab) },
                onOpenButcheryEnrollment = {
                    viewModel.closeTeamPanel()
                    viewModel.openStaffManagement(openButchery = true)
                },
                onOpenStaffManagement = {
                    viewModel.closeTeamPanel()
                    viewModel.openStaffManagement()
                },
                onOpenSubscription = {
                    viewModel.closeTeamPanel()
                    viewModel.openSubscriptionModal()
                }
            )
        }
    }

    if (state.isStaffManagementOpen && role in setOf("owner", "manager")) {
        Dialog(
            onDismissRequest = viewModel::closeStaffManagement,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            StaffManagementScreen(
                state = state,
                businessName = session.activeBusiness?.shopName.orEmpty(),
                role = role,
                businessType = session.activeBusiness?.businessType ?: "big_club",
                startOnBusinessHours = state.staffManagementStartsOnHours,
                startOnButchery = state.staffManagementStartsOnButchery,
                onClose = viewModel::closeStaffManagement,
                onLoadMembers = { viewModel.loadEmployeeMembers() },
                onIssueWaiterPass = { r -> viewModel.issueEmployeePass(r) },
                onSetEmployeeStatus = { uid, st -> viewModel.setEmployeeStatus(uid, st) },
                onSetEmployeeRole = { uid, rl -> viewModel.setEmployeeRole(uid, rl) },
                onRenameEmployee = { uid, name -> viewModel.renameEmployee(uid, name) },
                onRemoveEmployee = { uid -> viewModel.removeEmployeeAccess(uid) },
                onIssueEmployeeRecoveryPass = { uid, name -> viewModel.issueEmployeeRecoveryPass(uid, name) },
                onClearEmployeeRecoveryPass = { viewModel.clearEmployeeRecoveryCredential() },
                onSaveEmployeeSchedule = { uid, dow, st, en, act -> viewModel.setEmployeeHours(uid, dow, st, en, act) },
                onSaveBusinessHours = { bh -> viewModel.saveBusinessHours(bh) },
                onScanWaiterCode = { selectedRole ->
                    viewModel.openStaffEnrollmentScanner(selectedRole)
                },
                onSelectEnrollStation = { station -> viewModel.selectStaffEnrollmentStation(station) },
                onIssueButcheryPass = { stationName ->
                    viewModel.issueEmployeePass("butchery", stationName)
                },
                onScanButcheryCode = { stationName ->
                    viewModel.openStaffEnrollmentScanner("butchery", stationName)
                }
            )
        }
    }
}

@Composable
private fun SerenityFloatingBottomNav(
    selectedTab: PosTab,
    canManageStock: Boolean,
    supportsSalesAnalytics: Boolean,
    supportsTables: Boolean,
    lowStockCount: Int,
    isDarkSurface: Boolean,
    onSelectTab: (PosTab) -> Unit
) {
    val navColor = if (isDarkSurface) SerenityTokens.NavSurfaceDark else MaterialTheme.colorScheme.surface
    val navItems = buildList {
        add(Triple(PosTab.SCAN, "Sell", Icons.Default.ShoppingCart) to "nav_pos")
        if (canManageStock) add(Triple(PosTab.STOCK, "Stock", Icons.Default.Inventory2) to "nav_stock")
        add(Triple(PosTab.SALES, "Sales", Icons.AutoMirrored.Filled.ReceiptLong) to "nav_sales")
        if (supportsSalesAnalytics) add(Triple(PosTab.REPORTS, "Reports", Icons.Default.Assessment) to "nav_reports")
        if (supportsTables) add(Triple(PosTab.TABLES, "Tables", Icons.Default.TableRestaurant) to "nav_tables")
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(32.dp),
            color = navColor,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                navItems.forEach { (tabInfo, testTagName) ->
                    val (tab, label, icon) = tabInfo
                    val selected = selectedTab == tab
                    val pillColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        label = "navPillColor"
                    )
                    val contentTint = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        onClick = { onSelectTab(tab) },
                        shape = RoundedCornerShape(24.dp),
                        color = pillColor,
                        modifier = Modifier
                            .height(48.dp)
                            .animateContentSize()
                            .minimumInteractiveComponentSize()
                            .testTag(testTagName)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = if (selected) 15.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (tab == PosTab.STOCK && lowStockCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.secondary,
                                            contentColor = MaterialTheme.colorScheme.onSecondary,
                                            modifier = Modifier.testTag("nav_stock_low_badge")
                                        ) {
                                            Text(
                                                if (lowStockCount > 99) "99+" else lowStockCount.toString(),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = contentTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = contentTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (selected) {
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentTint,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CounterSelector(
    counters: List<ServiceCounter>,
    selectedCounterId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = counters.firstOrNull { it.id == selectedCounterId } ?: counters.firstOrNull()
    Box(modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalDrink, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text("Taking drinks from: ${selected?.name ?: "Select counter"}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("Change", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            counters.forEach { counter ->
                DropdownMenuItem(
                    text = { Text(counter.name) },
                    onClick = { onSelect(counter.id); expanded = false }
                )
            }
        }
    }
}

private fun todayStartIso(): String {
    val calendar = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(calendar.time)
}

private fun nowIso(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())

/**
 * Lean top bar:
 * - Doesn't waste 40% of the screen with static bulk.
 * - Icon-based search: click magnifying glass to open search, click outside / 'X' to close.
 * - Live sales summary pill and quick scanner trigger.
 */
@Composable
private fun LeanTopBar(
    state: CounterPosUiState,
    businessName: String = "Counter POS",
    subtitle: String,
    isPlatformAdmin: Boolean = false,
    onExitToAdmin: () -> Unit = {},
    onSearchToggle: (Boolean) -> Unit,
    onSearchChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onOpenScanner: () -> Unit,
    onAddNewProduct: () -> Unit,
    canAddNewProduct: Boolean = true,
    canManageTeam: Boolean = false,
    onManageTeam: () -> Unit = {},
    onManageStaff: () -> Unit = {},
    counters: List<ServiceCounter> = emptyList(),
    selectedCounterId: String? = null,
    onCounterSelect: (String) -> Unit = {},
    onSyncData: () -> Unit = {},
    isButcheryEnabled: Boolean = false,
    onOpenButchery: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    var counterMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        if (state.isSearchActive) {
            // Expanded search mode with instant outside-dismiss and close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onClearSearch()
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Exit search"
                    )
                }

                val searchPlaceholder = when (state.tab) {
                    PosTab.TABLES -> "Search tables..."
                    PosTab.STOCK -> "Search bottles & brands..."
                    PosTab.SALES -> "Search receipts..."
                    PosTab.SCAN -> "Search products to sell..."
                    PosTab.REPORTS -> "Search..."
                }

                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocusRequester)
                        .testTag("search_text_input"),
                    placeholder = {
                        Text(
                            searchPlaceholder,
                            fontSize = 14.sp
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }),
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear text", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                if (state.tab == PosTab.SCAN || state.tab == PosTab.STOCK) {
                    IconButton(onClick = onOpenScanner, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan barcode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            LaunchedEffect(Unit) {
                searchFocusRequester.requestFocus()
            }
        } else {
            // Ultra-compact normal top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = businessName,
                            fontSize = 17.sp,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            modifier = Modifier.clickable(
                                enabled = state.tab == PosTab.TABLES && counters.size > 1
                            ) { counterMenuExpanded = true }.padding(top = 3.dp),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        DropdownMenu(
                            expanded = counterMenuExpanded,
                            onDismissRequest = { counterMenuExpanded = false }
                        ) {
                            counters.forEach { counter ->
                                DropdownMenuItem(
                                    text = { Text(counter.name) },
                                    onClick = {
                                        onCounterSelect(counter.id)
                                        counterMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isPlatformAdmin) {
                        IconButton(
                            onClick = onExitToAdmin,
                            modifier = Modifier.size(40.dp).testTag("exit_to_admin_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Superadmin Portal",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = onSyncData,
                        modifier = Modifier.size(40.dp).testTag("sync_refresh_button")
                    ) {
                        if (state.isSyncingData) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync latest data",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Search icon button (context-aware: tables on Tables tab, products on Sell/Stock)
                    IconButton(
                        onClick = { onSearchToggle(true) },
                        modifier = Modifier.size(40.dp).testTag("search_icon_button")
                    ) {
                        val searchDesc = when (state.tab) {
                            PosTab.TABLES -> "Search tables"
                            PosTab.SALES -> "Search receipts"
                            else -> "Search products"
                        }
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = searchDesc,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // In Stock section, show Add New Product icon
                    if (state.tab == PosTab.STOCK && canAddNewProduct) {
                        IconButton(
                            onClick = onAddNewProduct,
                            modifier = Modifier.size(40.dp).testTag("add_product_icon_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add new product",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (isButcheryEnabled) {
                        Surface(
                            onClick = onOpenButchery,
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF1F8E4E),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("open_butchery_workspace_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Scale,
                                    contentDescription = "Open Butchery POS",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Butchery",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    if (canManageTeam) {
                        IconButton(onClick = onManageStaff, modifier = Modifier.size(40.dp).testTag("staff_management_top_button")) {
                            Icon(Icons.Default.Badge, contentDescription = "Staff Management", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onManageTeam, modifier = Modifier.size(40.dp).testTag("settings_top_button")) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Barcode Scanner button
                    Surface(
                        onClick = onOpenScanner,
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(40.dp).testTag("top_scanner_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Open scanner",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "Scan",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * POS / Sell Screen - Full Screen Scrollable!
 */
@Composable
private fun FullscreenSellScreen(
    modifier: Modifier,
    state: CounterPosUiState,
    products: List<ProductEntity>,
    onProductClick: (ProductEntity) -> Unit,
    onOpenStock: (() -> Unit)? = null
) {
    val stocked = remember(products) { products.filter { it.inMyStock } }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = remember(stocked) {
        listOf("All") + stocked.map { it.category }.distinct().sorted()
    }

    val visible = remember(stocked, state.searchQuery, selectedCategory) {
        stocked.filter { product ->
            (selectedCategory == "All" || product.category.equals(selectedCategory, ignoreCase = true)) &&
                product.matches(state.searchQuery)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Category filter chips
        if (categories.size > 1) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    categories.forEach { category ->
                        val selected = selectedCategory == category
                        Surface(
                            onClick = { selectedCategory = category },
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Box(
                                Modifier.padding(horizontal = 13.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    category,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section header
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Shop Inventory (${visible.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Tap to sell",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (visible.isEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                if (stocked.isEmpty()) SellEmptyState(onOpenStock)
                else EmptyStateMessage("No products match '${state.searchQuery}'.")
            }
        } else {
            items(visible, key = { it.catalogCode }) { product ->
                ProductSellCard(product = product, onClick = { onProductClick(product) })
            }
        }
    }
}

@Composable
private fun SellEmptyState(onOpenStock: (() -> Unit)?) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(280.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(27.dp))
                }
            }
            Spacer(Modifier.height(13.dp))
            Text("Nothing to sell yet", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                if (onOpenStock != null) "Add products to your shelf before starting a sale."
                else "Waiting for products to be stocked on the shelf. Ask your manager to add stock.",
                modifier = Modifier.padding(top = 5.dp, bottom = 14.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (onOpenStock != null) {
                Button(
                    onClick = onOpenStock,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(11.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text("Open stock", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Stock & Inventory Screen - Full Screen Scrollable!
 * Features:
 * - Direct "+ Add" button per product to add specific number of bottles (NO +24 and +6 buttons)
 * - Edit Stock for each product (edit count, price, and units of sales)
 * - Add New Product button (configure name, price, stock, and units like cups/glasses for local liquor)
 */
@Composable
private fun FullscreenStockScreen(
    modifier: Modifier,
    state: CounterPosUiState,
    products: List<ProductEntity>,
    lowStockCatalogCodes: Set<String> = emptySet(),
    onModeChange: (StockMode) -> Unit,
    onCategoryChange: (String) -> Unit,
    onAddStockClick: (ProductEntity) -> Unit,
    onEditProductClick: (ProductEntity) -> Unit,
    onScanStockClick: () -> Unit,
    onAddCatalogClick: (ProductEntity) -> Unit,
    canReceiveStock: Boolean,
    canEditProducts: Boolean,
    canTransferStock: Boolean,
    onTransferStock: (ProductEntity) -> Unit,
    onChangePhoto: (ProductEntity) -> Unit
) {
    var showLowStockOnly by remember { mutableStateOf(false) }
    val mine = remember(products) { products.filter { it.inMyStock } }
    val library = remember(products) { products.filter { !it.inMyStock } }
    val lowStockMine = remember(mine, lowStockCatalogCodes) {
        mine.filter { it.catalogCode in lowStockCatalogCodes || isLowOrOutOfStock(it) }
    }
    val outOfStockCount = remember(lowStockMine) { lowStockMine.count { it.stockQuantity <= 0 } }
    val atThresholdCount = remember(lowStockMine) { lowStockMine.size - outOfStockCount }

    val visible = remember(mine, library, state.stockMode, state.searchQuery, state.libraryCategory, showLowStockOnly, lowStockCatalogCodes) {
        if (state.stockMode == StockMode.MY_STOCK) {
            mine.filter { product ->
                product.matches(state.searchQuery) &&
                    (!showLowStockOnly || product.catalogCode in lowStockCatalogCodes || isLowOrOutOfStock(product))
            }
        } else {
            library.filter { (state.libraryCategory == "All" || it.category == state.libraryCategory) && it.matches(state.searchQuery) }
        }
    }

    val categories = remember(library) { listOf("All") + library.map { it.category }.distinct().sorted() }
    val totalBottles = remember(mine) { mine.sumOf { it.stockQuantity } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        // Compact segmented shelf / catalog switch, matching the design reference.
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf(
                    StockMode.MY_STOCK to "Shop Shelf (${mine.size})",
                    StockMode.LIBRARY to "Catalog (${library.size})"
                ).forEach { (mode, label) ->
                    val selected = state.stockMode == mode
                    val action: @Composable () -> Unit = {
                        if (selected) {
                            Button(
                                onClick = { onModeChange(mode) },
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                shape = RoundedCornerShape(11.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onModeChange(mode) },
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                shape = RoundedCornerShape(11.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) { action() }
                }
            }
        }

        // Shelf summary & live Room low-stock alert controls
        if (state.stockMode == StockMode.MY_STOCK) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Total: $totalBottles units on shelf",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (lowStockMine.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (outOfStockCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    "${lowStockMine.size} low",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (outOfStockCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        } else {
                            Text(
                                "All healthy",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (lowStockMine.isNotEmpty()) {
                item {
                    val hasOut = outOfStockCount > 0
                    val bannerColor = if (hasOut) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                    val contentColor = if (hasOut) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    val borderColor = if (hasOut) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                    val breakdown = buildList {
                        if (outOfStockCount > 0) add("$outOfStockCount out of stock")
                        if (atThresholdCount > 0) add("$atThresholdCount at/below min")
                    }.joinToString(" · ")

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = bannerColor.copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("low_stock_alert_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (hasOut) Icons.Default.ErrorOutline else Icons.Default.WarningAmber,
                                contentDescription = "Low stock alert",
                                tint = contentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${lowStockMine.size} ${if (lowStockMine.size == 1) "item needs" else "items need"} restocking",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                )
                                Text(
                                    text = breakdown,
                                    fontSize = 11.sp,
                                    color = contentColor.copy(alpha = 0.85f)
                                )
                            }
                            TextButton(
                                onClick = { showLowStockOnly = !showLowStockOnly },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("low_stock_banner_toggle")
                            ) {
                                Text(
                                    if (showLowStockOnly) "Show all" else "View",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                )
                            }
                        }
                    }
                }
            }

            if (mine.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("stock_filter_row"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !showLowStockOnly,
                            onClick = { showLowStockOnly = false },
                            label = { Text("All (${mine.size})", fontSize = 11.sp) },
                            modifier = Modifier.testTag("stock_all_filter_chip")
                        )
                        FilterChip(
                            selected = showLowStockOnly,
                            onClick = { showLowStockOnly = !showLowStockOnly },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (outOfStockCount > 0) Icons.Default.ErrorOutline else Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            label = { Text("Low Stock (${lowStockMine.size})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (outOfStockCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = if (outOfStockCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedLeadingIconColor = if (outOfStockCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.testTag("stock_low_filter_chip")
                        )
                    }
                }
            }
        }

        // Category filter in Library mode
        if (state.stockMode == StockMode.LIBRARY) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = state.libraryCategory == category,
                            onClick = { onCategoryChange(category) },
                            label = { Text(category, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            item {
                if (state.stockMode == StockMode.MY_STOCK && state.searchQuery.isBlank() && !showLowStockOnly) {
                    ShelfEmptyState(
                        onBrowseCatalog = { onModeChange(StockMode.LIBRARY) },
                        onScan = onScanStockClick
                    )
                } else if (state.stockMode == StockMode.MY_STOCK && showLowStockOnly) {
                    EmptyStateMessage("No items are currently at or below their minimum stock threshold.")
                } else {
                    EmptyStateMessage("No catalog items match '${state.searchQuery}'.")
                }
            }
        } else if (state.stockMode == StockMode.MY_STOCK) {
            items(visible, key = { it.catalogCode }) { product ->
                StockProductCard(
                    product = product,
                    isLowStockAlert = product.catalogCode in lowStockCatalogCodes || isLowOrOutOfStock(product),
                    canReceiveStock = canReceiveStock,
                    canEditProducts = canEditProducts,
                    canTransferStock = canTransferStock,
                    onAddStock = { onAddStockClick(product) },
                    onEditProduct = { onEditProductClick(product) },
                    onTransferStock = { onTransferStock(product) },
                    onChangePhoto = { onChangePhoto(product) }
                )
            }
        } else {
            items(visible, key = { it.catalogCode }) { product ->
                CatalogProductCard(
                    product = product,
                    canAdd = canReceiveStock,
                    onAdd = { onAddCatalogClick(product) }
                )
            }
        }
    }
}

@Composable
private fun ShelfEmptyState(
    onBrowseCatalog: () -> Unit,
    onScan: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Your shelf is ready", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "Add products from the catalog or scan a barcode to start tracking stock and selling.",
                modifier = Modifier.padding(top = 5.dp, bottom = 15.dp),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onBrowseCatalog,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(11.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Browse catalog", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onScan,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(40.dp),
                shape = RoundedCornerShape(11.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Scan a product", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}


private fun sharePdf(context: android.content.Context, bytes: ByteArray, filename: String) {
    val directory = File(context.cacheDir, "shared_reports").apply { mkdirs() }
    val file = File(directory, filename).apply { writeBytes(bytes) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share PDF report"))
}

/**
 * Product Tile for Sell / POS Grid
 */
@Composable
private fun ProductSellCard(
    product: ProductEntity,
    onClick: () -> Unit
) {
    val units = remember(product.salesUnitsJson, product.sellingPriceKsh, product.suggestedPriceKsh) {
        resolveSaleUnits(product)
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(122.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SerenityTokens.ThumbnailFrame)
            ) {
                ProductThumbnail(product, Modifier.fillMaxSize().padding(6.dp))
                if (units.isNotEmpty() && units.all { it.stockUnitsPerSale == 0 }) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            "Bulk stock",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                } else {
                    StockIndicatorPill(
                        quantity = product.stockQuantity,
                        threshold = product.lowStockThreshold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                        showInStockText = true
                    )
                }
            }

            Text(
                text = product.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
                lineHeight = 17.sp
            )

            // Show units summary (e.g. "Bottle · Glass · Cup")
            if (units.size > 1) {
                Text(
                    text = units.joinToString(" · ") { it.name },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            } else {
                Text(
                    text = listOf(units.firstOrNull()?.name.orEmpty(), product.size.ifBlank { product.category })
                        .filter(String::isNotBlank)
                        .joinToString(" · "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when {
                        product.sellingPriceKsh > 0 -> ksh(product.sellingPriceKsh)
                        product.suggestedPriceKsh > 0 -> "Suggested ${ksh(product.suggestedPriceKsh)}"
                        else -> "Price not set"
                    },
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (product.sellingPriceKsh > 0 || product.suggestedPriceKsh > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        if (units.size > 1) "Options" else "Sell",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

/**
 * Stock Product Card - Clean, no bulky +24 or +6 buttons!
 * Has:
 * - "+ Add" button -> opens dialog to enter number of bottles
 * - "Edit" button -> opens dialog to edit stock count, price, and units
 */
@Composable
private fun StockProductCard(
    product: ProductEntity,
    isLowStockAlert: Boolean = isLowOrOutOfStock(product),
    canReceiveStock: Boolean,
    canEditProducts: Boolean,
    canTransferStock: Boolean,
    onAddStock: () -> Unit,
    onEditProduct: () -> Unit,
    onTransferStock: () -> Unit,
    onChangePhoto: () -> Unit
) {
    val units = remember(product.salesUnitsJson, product.sellingPriceKsh, product.suggestedPriceKsh) {
        resolveSaleUnits(product)
    }
    val isBulkOnly = remember(units) { units.isNotEmpty() && units.all { it.stockUnitsPerSale == 0 } }
    val outOfStock = !isBulkOnly && product.stockQuantity <= 0
    val lowStock = !isBulkOnly && !outOfStock && isLowStockAlert
    val cardContainerColor = when {
        outOfStock -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.24f)
        lowStock -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
        else -> MaterialTheme.colorScheme.surface
    }
    val cardBorder = when {
        outOfStock -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
        lowStock -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = if (outOfStock || lowStock) 2.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stock_product_card_${product.catalogCode}")
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ProductThumbnail(
                    product,
                    Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SerenityTokens.ThumbnailFrame)
                        .padding(4.dp)
                        .clickable(enabled = canEditProducts, onClick = onChangePhoto)
                )
                if (canEditProducts) {
                    TextButton(
                        onClick = onChangePhoto,
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp).testTag("change_product_photo_${product.catalogCode}")
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Change", fontSize = 10.sp, maxLines = 1)
                    }
                }
            }

            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        product.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (isBulkOnly) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                "Bulk stock",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    } else {
                        val badgeBg = when {
                            outOfStock -> MaterialTheme.colorScheme.errorContainer
                            lowStock -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                        val badgeFg = when {
                            outOfStock -> MaterialTheme.colorScheme.onErrorContainer
                            lowStock -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                        val badgeText = when {
                            outOfStock -> "OUT OF STOCK"
                            lowStock -> "LOW · ${product.stockQuantity} left"
                            else -> "${product.stockQuantity} in stock"
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = badgeBg,
                            modifier = Modifier.testTag("stock_status_badge_${product.catalogCode}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (outOfStock || lowStock) {
                                    Icon(
                                        imageVector = if (outOfStock) Icons.Default.ErrorOutline else Icons.Default.WarningAmber,
                                        contentDescription = badgeText,
                                        tint = badgeFg,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text(
                                    badgeText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeFg,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Show units of sale configured (e.g. Bottle @ 300, Glass @ 70, Cup @ 35)
                if (units.size > 1) {
                    Text(
                        text = units.joinToString(" · ") {
                            "${it.name} ${if (it.priceKsh > 0) ksh(it.priceKsh) else "Price not set"}"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Text(
                        text = "${when {
                            product.sellingPriceKsh > 0 -> ksh(product.sellingPriceKsh)
                            product.suggestedPriceKsh > 0 -> "Suggested ${ksh(product.suggestedPriceKsh)}"
                            else -> "Price not set"
                        }} · ${product.category}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Text(
                    text = "Code: ${product.verifiedBarcode ?: product.catalogCode}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Stock vs minimum threshold visual gauge for tracked items
                if (!isBulkOnly) {
                    val threshold = product.lowStockThreshold.coerceAtLeast(1)
                    val meterRatio = (product.stockQuantity.toFloat() / (threshold * 2f)).coerceIn(0f, 1f)
                    val meterColor = when {
                        outOfStock -> MaterialTheme.colorScheme.error
                        lowStock -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    }
                    val trackColor = when {
                        outOfStock -> MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
                        lowStock -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("stock_threshold_bar_${product.catalogCode}"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    outOfStock -> "Restock needed immediately"
                                    lowStock -> "At or below minimum stock"
                                    else -> "Stock healthy"
                                },
                                fontSize = 10.sp,
                                fontWeight = if (outOfStock || lowStock) FontWeight.Bold else FontWeight.Medium,
                                color = if (outOfStock || lowStock) meterColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Min: ${product.lowStockThreshold}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (outOfStock || lowStock) meterColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(trackColor)
                        ) {
                            if (meterRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(meterRatio)
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(meterColor)
                                )
                            }
                        }
                    }
                }

                // Clean Action Buttons: ONLY "+ Add" and "Edit" (No bulky +24 or +6 buttons!)
                if (canReceiveStock || canEditProducts || canTransferStock) Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    // "+ Add" button (Opens input box for bottle count)
                    if (canReceiveStock) {
                        Button(
                            onClick = onAddStock,
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Stock", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // "Edit" button (Allows editing stock count, price, units)
                    if (canEditProducts) {
                        OutlinedButton(
                            onClick = onEditProduct,
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit", fontSize = 10.sp)
                        }
                    }

                    if (canTransferStock) {
                        OutlinedButton(
                            onClick = onTransferStock,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) { Text("Move", fontSize = 12.sp) }
                    }

                }
            }
        }
    }
}

/**
 * Catalog item row for adding from library
 */
@Composable
private fun CatalogProductCard(
    product: ProductEntity,
    canAdd: Boolean,
    onAdd: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProductThumbnail(
                product,
                Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SerenityTokens.ThumbnailFrame)
                    .padding(3.dp)
            )

            Column(Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${product.brand} · ${product.size} · ${product.category}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Suggested: ${ksh(product.suggestedPriceKsh)}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            }

            if (canAdd) {
                Button(
                    onClick = onAdd,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Add", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Sale with Units Sheet:
 * Lets cashier select whether they are selling Bottle, Glass, Cup (or custom unit) and quantity!
 */
@Composable
private fun SaleWithUnitsSheet(
    product: ProductEntity,
    selectedUnitName: String,
    selectedUnitPrice: Int,
    quantity: Int,
    error: String?,
    serviceCounters: List<ServiceCounter> = emptyList(),
    selectedCounterId: String? = null,
    onSelectCounter: ((String, String) -> Unit)? = null,
    selectedWaiter: String = "",
    selectedPaymentMethod: String = "cash",
    selectedTableName: String? = null,
    onSelectUnit: (SaleUnit) -> Unit,
    onQuantityChange: (Int) -> Unit,
    onSelectPaymentMethod: (String) -> Unit,
    onConfirm: () -> Unit
) {
    val units = remember(product.salesUnitsJson, product.sellingPriceKsh, product.suggestedPriceKsh) {
        resolveSaleUnits(product)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductThumbnail(
                product,
                Modifier
                    .size(65.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Column(Modifier.weight(1f)) {
                Text(product.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (units.isNotEmpty() && units.all { it.stockUnitsPerSale == 0 }) {
                        "${product.category} · Bulk container stock"
                    } else {
                        "${product.category} · Shelf stock: ${product.stockQuantity} units"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Table banner if table sale
        if (!selectedTableName.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TableRestaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Serving Table: $selectedTableName", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        // Source Counter Selector
        if (serviceCounters.size > 1) {
            Text(
                "Source Counter (where drink is taken):",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                serviceCounters.forEach { counter ->
                    val isCounterSelected = counter.id == selectedCounterId
                    FilterChip(
                        selected = isCounterSelected,
                        onClick = { onSelectCounter?.invoke(counter.id, counter.name) },
                        label = { Text(counter.name) },
                        leadingIcon = if (isCounterSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )
                }
            }
        } else if (serviceCounters.isEmpty()) {
            Text("No service counter is registered. Ask the owner to configure one in Settings.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        if (selectedWaiter.isNotBlank()) {
            Text("Recorded by ${selectedWaiter}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }

        if (selectedTableName.isNullOrBlank()) {
            Text("Payment method", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("cash" to "Cash", "mpesa" to "M-Pesa").forEach { (key, label) ->
                    FilterChip(
                        selected = selectedPaymentMethod == key,
                        onClick = { onSelectPaymentMethod(key) },
                        label = { Text(label) },
                        leadingIcon = if (selectedPaymentMethod == key) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) } } else null
                    )
                }
            }
        }
        // Unit of Sale Selector (Bottle, Glass, Cup, etc.)
        Text(
            "Select Unit of Sale:",
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            units.forEach { unit ->
                val isSelected = unit.name.equals(selectedUnitName, ignoreCase = true)
                Surface(
                    onClick = { onSelectUnit(unit) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Column(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            unit.name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (unit.priceKsh > 0) ksh(unit.priceKsh) else "Price not set",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Quantity Stepper
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Quantity to sell", fontWeight = FontWeight.Medium)
            QuantityStepperControl(quantity = quantity, onChange = onQuantityChange)
        }

        val total = selectedUnitPrice * quantity
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (selectedUnitPrice > 0) "$quantity × $selectedUnitName @ ${ksh(selectedUnitPrice)}" else "Selling price not set",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (selectedUnitPrice > 0) ksh(total) else "—",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (selectedUnitPrice <= 0) {
            Text("Set a selling price in Stock before completing this sale.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        }

        if (!error.isNullOrBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        }

        Button(
            onClick = onConfirm,
            enabled = serviceCounters.isNotEmpty() && selectedCounterId != null && units.isNotEmpty() && selectedUnitPrice > 0 && (selectedTableName != null || selectedPaymentMethod in setOf("cash", "mpesa")),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            val label = when {
                selectedUnitPrice <= 0 -> "Set price in Stock before selling"
                !selectedTableName.isNullOrBlank() -> "Add to $selectedTableName check · ${ksh(total)}"
                else -> "Complete ${if (selectedPaymentMethod == "mpesa") "M-Pesa" else "Cash"} Sale · ${ksh(total)}"
            }
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Log drink Bottom Sheet:
 * Provides a simple, fast interface for waiters or cashiers to record
 * drinks taken from a specific counter (Counter 1, Counter 2, etc.) to serve a table or client.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordDispensationBottomSheet(
    serviceCounters: List<ServiceCounter>,
    initialCounterId: String?,
    diningTables: List<DiningTableEntity>,
    products: List<ProductEntity>,
    initialWaiter: String,
    initialTableId: String?,
    supportsTables: Boolean = true,
    error: String?,
    onDismiss: () -> Unit,
    onRecord: (counterId: String, counterName: String, waiterName: String, tableId: String?, tableName: String?, productCode: String, quantity: Int, unitName: String, unitPriceKsh: Int) -> Unit
) {
    val counters = serviceCounters
    var selectedCounterId by remember(initialCounterId, counters) {
        mutableStateOf(initialCounterId ?: counters.singleOrNull()?.id.orEmpty())
    }
    var selectedWaiter by remember(initialWaiter) { mutableStateOf(initialWaiter) }
    var destinationMode by remember(supportsTables, initialTableId) {
        mutableStateOf(if (supportsTables && initialTableId != null) "table" else "walk_in")
    }
    var selectedTableId by remember(initialTableId) { mutableStateOf(initialTableId ?: diningTables.firstOrNull()?.id) }
    var selectedProductCode by remember(products) { mutableStateOf(products.firstOrNull()?.catalogCode.orEmpty()) }
    var quantity by remember { mutableStateOf(1) }

    val selectedProduct = products.firstOrNull { it.catalogCode == selectedProductCode } ?: products.firstOrNull()
    val units = remember(selectedProduct) {
        if (selectedProduct != null) resolveSaleUnits(selectedProduct)
        else emptyList()
    }
    var selectedUnit by remember(units) {
        mutableStateOf(units.firstOrNull() ?: SaleUnit("Bottle", selectedProduct?.sellingPriceKsh?.coerceAtLeast(0) ?: 0))
    }

    val resolvedCounter = counters.firstOrNull { it.id == selectedCounterId }
    val resolvedTable = if (supportsTables && destinationMode == "table") diningTables.firstOrNull { it.id == selectedTableId } else null
    val totalAmount = selectedUnit.priceKsh * quantity

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Record Drink Dispensation", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Log drinks taken from counter by a rotating waiter", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // A lone counter is implicit; only ask for a source when there is a real choice.
            if (counters.size > 1) {
                Text("1. Source counter:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    counters.forEach { counter ->
                        FilterChip(
                            selected = counter.id == selectedCounterId,
                            onClick = { selectedCounterId = counter.id },
                            label = { Text(counter.name) },
                            leadingIcon = if (counter.id == selectedCounterId) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }
            } else if (counters.isEmpty()) {
                Text("No service counter is configured. Add one in Settings before recording sales.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Text("2. Recorded by:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(selectedWaiter.ifBlank { "Signed-in account required" }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

            // 3. Destination (Table vs Direct Walk-in)
            if (supportsTables) {
                Text("3. Served Destination:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = destinationMode == "table",
                        onClick = { destinationMode = "table" },
                        label = { Text("Table Check") }
                    )
                    FilterChip(
                        selected = destinationMode == "walk_in",
                        onClick = { destinationMode = "walk_in" },
                        label = { Text("Direct Walk-in / Bar") }
                    )
                }
                if (destinationMode == "table") {
                    if (diningTables.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            diningTables.forEach { table ->
                                FilterChip(
                                    selected = table.id == selectedTableId,
                                    onClick = { selectedTableId = table.id },
                                    label = { Text(table.name) }
                                )
                            }
                        }
                    } else {
                        Text("No dining tables yet. Create one in the Tables tab, or choose Direct Walk-in / Bar.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "3. Served Destination: Direct Register Checkout (Tables disabled for Liquor Store)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // 4. Product to Dispense
            Text("4. Select Product:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (products.isEmpty()) {
                Text("No in-stock products available. Please add products to stock first.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    products.forEach { prod ->
                        FilterChip(
                            selected = prod.catalogCode == selectedProductCode,
                            onClick = { selectedProductCode = prod.catalogCode },
                            label = { Text("${prod.name} (${prod.stockQuantity})") }
                        )
                    }
                }
            }

            // 5. Unit of Sale
            if (units.isNotEmpty()) {
                Text("5. Unit of Sale:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    units.forEach { unit ->
                        FilterChip(
                            selected = unit.name.equals(selectedUnit.name, ignoreCase = true),
                            onClick = { selectedUnit = unit },
                            label = { Text("${unit.name} · ${if (unit.priceKsh > 0) ksh(unit.priceKsh) else "Price not set"}") }
                        )
                    }
                }
            }

            // 6. Quantity Stepper
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Quantity to dispense", fontWeight = FontWeight.Medium)
                QuantityStepperControl(quantity = quantity, onChange = { if (it in 1..99) quantity = it })
            }

            // Summary Card
            val destinationName = if (destinationMode == "table") resolvedTable?.name ?: "Select a real table" else "Walk-in Client"
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "$quantity × ${selectedProduct?.name ?: "Drink"} (${selectedUnit.name})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        buildString {
                            if (counters.size > 1) append("${resolvedCounter?.name ?: "Select counter"} · ")
                            append("Recorded by $selectedWaiter → $destinationName")
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        if (selectedUnit.priceKsh > 0) "Total: ${ksh(totalAmount)}" else "Total: selling price not set",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!error.isNullOrBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            // Action Button
            Button(
                onClick = {
                    val prodCode = selectedProduct?.catalogCode ?: return@Button
                    val counter = resolvedCounter ?: return@Button
                    val tId = if (destinationMode == "table") resolvedTable?.id ?: return@Button else null
                    val tName = if (destinationMode == "table") resolvedTable?.name else "Direct Walk-in"
                    onRecord(
                        counter.id,
                        counter.name,
                        selectedWaiter,
                        tId,
                        tName,
                        prodCode,
                        quantity,
                        selectedUnit.name,
                        selectedUnit.priceKsh
                    )
                },
                enabled = selectedProduct != null && resolvedCounter != null && selectedWaiter.isNotBlank() &&
                    units.isNotEmpty() && selectedUnit.priceKsh > 0 &&
                    (destinationMode != "table" || resolvedTable != null),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            ) {
                Text(
                    if (selectedUnit.priceKsh > 0) "Record sale · ${ksh(totalAmount)}" else "Set price in Stock before selling",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Dialog to add a specific number of bottles to stock
 * Replaces old +24 and +6 buttons!
 */
@Composable
private fun AddBottlesDialog(
    product: ProductEntity,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var bottlesText by remember { mutableStateOf(if (parseSaleUnits(product.salesUnitsJson, product.sellingPriceKsh).any { it.stockUnitsPerSale == 0 }) "1" else "") }
    var unitCostText by remember(product.catalogCode) { mutableStateOf(product.averageCostKsh.toString()) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Bottles to Stock", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    product.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Current shelf count: ${product.stockQuantity} bottles",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = bottlesText,
                    onValueChange = { bottlesText = it.filter(Char::isDigit) },
                    label = { Text(if (parseSaleUnits(product.salesUnitsJson, product.sellingPriceKsh).any { it.stockUnitsPerSale == 0 }) "Containers to add" else "Number of bottles to add") },
                    placeholder = { Text(if (parseSaleUnits(product.salesUnitsJson, product.sellingPriceKsh).any { it.stockUnitsPerSale == 0 }) "e.g. 1 drum, 2 barrels" else "e.g. 6, 12, 24, 48...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("add_bottles_input")
                )

                OutlinedTextField(
                    value = unitCostText,
                    onValueChange = { unitCostText = it.filter(Char::isDigit) },
                    label = { Text("Unit cost (KSh)") },
                    placeholder = { Text("0 if unknown") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )

                // Quick helper chips for convenience
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(6, 12, 24, 48).forEach { qty ->
                        Surface(
                            onClick = { bottlesText = qty.toString() },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "+$qty",
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (!error.isNullOrBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val count = bottlesText.toIntOrNull() ?: 0
                    val cost = unitCostText.toIntOrNull() ?: 0
                    if (count > 0 && cost >= 0) onConfirm(count, cost)
                },
                enabled = (bottlesText.toIntOrNull() ?: 0) > 0,
                modifier = Modifier.testTag("confirm_add_bottles_button")
            ) {
                Text("Add to Stock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * Dialog to edit product details:
 * - Direct stock editing (shelf count)
 * - Base price
 * - Configurable units of sale (bottle, glass, cup with prices)
 */
@Composable
private fun EditProductDialog(
    product: ProductEntity,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, stock: Int, price: Int, units: List<SaleUnit>, barcode: String?, photoUri: String?) -> Unit,
    onScanBarcode: () -> Unit
) {
    var name by remember(product.catalogCode) { mutableStateOf(product.name) }
    var barcodeText by remember(product.catalogCode) { mutableStateOf(product.verifiedBarcode ?: "") }
    var stockText by remember(product.catalogCode) { mutableStateOf(product.stockQuantity.toString()) }
    var priceText by remember(product.catalogCode) {
        mutableStateOf(product.sellingPriceKsh.coerceAtLeast(0).toString())
    }
    var selectedPhotoUri by remember(product.catalogCode) {
        mutableStateOf(product.photoOverrideUri?.takeIf(String::isNotBlank))
    }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri.toString()
        }
    }

    val initialUnits = remember(product.catalogCode) {
        resolveSaleUnits(product)
    }
    val unitsList = remember(product.catalogCode) {
        mutableStateListOf<SaleUnit>().apply { addAll(initialUnits) }
    }

    var bulkStock by remember(product.catalogCode) { mutableStateOf(initialUnits.any { it.stockUnitsPerSale == 0 }) }
    var newUnitName by remember { mutableStateOf("") }
    var newUnitPriceText by remember { mutableStateOf("") }

    val quickUnits = listOf("Cup", "Glass", "Bottle", "Barrel", "Jug", "Tot", "Jerrican")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Product & Stock", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Product Photo Section
                Text(
                    "Product Photo:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!selectedPhotoUri.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(selectedPhotoUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Product photo",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (selectedPhotoUri.isNullOrBlank()) "Set Product Photo" else "Change Photo", fontSize = 12.sp)
                        }
                        if (!selectedPhotoUri.isNullOrBlank()) {
                            TextButton(
                                onClick = { selectedPhotoUri = null },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Remove Photo", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Barcode / Product Identity field with scan button
                OutlinedTextField(
                    value = barcodeText,
                    onValueChange = { barcodeText = it.trim() },
                    label = { Text("Product Barcode / Identity") },
                    placeholder = { Text("Scan or enter barcode") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = onScanBarcode) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode with camera")
                        }
                    },
                    supportingText = {
                        Text(if (barcodeText.isNotBlank()) "Linked: $barcodeText" else "Add barcode to identify product via scanner", fontSize = 11.sp)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = stockText,
                        onValueChange = { stockText = it.filter(Char::isDigit) },
                        label = { Text("Shelf Stock") },
                        supportingText = { Text("Base units", fontSize = 10.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter(Char::isDigit) },
                        label = { Text("Base Price (KSh)") },
                        supportingText = { Text("Main unit price", fontSize = 10.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (product.sellingPriceKsh <= 0 && product.suggestedPriceKsh > 0) {
                    Text(
                        "Catalog suggestion: ${ksh(product.suggestedPriceKsh)}. This is not a saved shop price; enter your actual price above.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "Configured Units of Sale:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    "Add the units customers can buy and their prices. For drums or barrels, sale units do not require a cup count.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = onScanBarcode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (barcodeText.isBlank()) "Scan Barcode with Camera" else "Rescan Barcode")
                }

                // List of units
                unitsList.forEachIndexed { index, unit ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${unit.name} · ${ksh(unit.priceKsh)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (unit.stockUnitsPerSale > 1) {
                                Text(
                                    "Consumes ${unit.stockUnitsPerSale} stock units per sale",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = { if (unitsList.size > 1) unitsList.removeAt(index) },
                            enabled = unitsList.size > 1,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove unit", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Quick Unit chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quickUnits.forEach { unitLabel ->
                        FilterChip(
                            selected = newUnitName.equals(unitLabel, ignoreCase = true),
                            onClick = {
                                newUnitName = unitLabel
                                // Sale units are priced independently. Bulk/container stock is selected below.
                            },
                            label = { Text(unitLabel, fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = bulkStock, onCheckedChange = { bulkStock = it })
                    Column {
                        Text("Bulk / container stock", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Sales do not consume a container count; adjust stock when the container is empty.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Add another unit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = newUnitName,
                        onValueChange = { newUnitName = it },
                        placeholder = { Text("Unit name", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1.1f)
                    )
                    OutlinedTextField(
                        value = newUnitPriceText,
                        onValueChange = { newUnitPriceText = it.filter(Char::isDigit) },
                        placeholder = { Text("KSh", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.9f)
                    )
                    IconButton(
                        onClick = {
                            val p = newUnitPriceText.toIntOrNull() ?: 0
                            if (newUnitName.isNotBlank() && p > 0) {
                                unitsList.add(SaleUnit(newUnitName.trim(), p, if (bulkStock) 0 else 1))
                                newUnitName = ""
                                newUnitPriceText = ""
                            }
                        },
                        enabled = newUnitName.isNotBlank() && (newUnitPriceText.toIntOrNull() ?: 0) > 0
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add unit", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                if (!error.isNullOrBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = stockText.toIntOrNull() ?: 0
                    val p = priceText.toIntOrNull() ?: 0
                    if (unitsList.isEmpty()) {
                        unitsList.add(SaleUnit("Bottle", p, if (bulkStock) 0 else 1))
                    }
                    onConfirm(name.trim(), s, p, unitsList.toList(), barcodeText.trim().ifBlank { null }, selectedPhotoUri)
                },
                enabled = name.isNotBlank() && (priceText.toIntOrNull() ?: 0) > 0
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Dialog to add a new product:
 * User configures:
 * - Name
 * - Barcode (optional)
 * - Category (Local Liquor, Spirit, Beer, etc.)
 * - Initial Stock count
 * - Units of Sale (e.g. Cup, Glass, Bottle with prices!)
 */
@Composable
private fun AddNewProductDialog(
    prefilledBarcode: String?,
    error: String?,
    onDismiss: () -> Unit,
    onScanBarcode: () -> Unit,
    onConfirm: (name: String, barcode: String, category: String, price: Int, stock: Int, unitCost: Int, units: List<SaleUnit>, photoUri: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var barcode by remember(prefilledBarcode) { mutableStateOf(prefilledBarcode.orEmpty()) }
    var selectedCategory by remember { mutableStateOf("Local Liquor") }
    var stockText by remember { mutableStateOf("") }
    var basePriceText by remember { mutableStateOf("") }
    var unitCostText by remember { mutableStateOf("") }
    var selectedPhotoUri by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri.toString()
        }
    }

    val categories = listOf("Local Liquor", "Beer", "Spirit", "Cider", "RTD", "Wine", "Soft Drink", "Water")
    val quickUnits = listOf("Cup", "Glass", "Bottle", "Barrel", "Jug", "Tot", "Jerrican")

    // Empty initial units of sale - user defines their own prices and units
    val unitsList = remember { mutableStateListOf<SaleUnit>() }

    var bulkStock by remember { mutableStateOf(true) }
    var extraUnitName by remember { mutableStateOf("") }
    var extraUnitPriceText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add New Product", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Product Photo Section
                Text(
                    "Product Photo:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!selectedPhotoUri.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(selectedPhotoUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Product photo",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (selectedPhotoUri.isNullOrBlank()) "Set Product Photo" else "Change Photo", fontSize = 12.sp)
                        }
                        if (!selectedPhotoUri.isNullOrBlank()) {
                            TextButton(
                                onClick = { selectedPhotoUri = null },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Remove Photo", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name") },
                    placeholder = { Text("e.g. Muratina, Chang'aa, Local Brew...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_product_name_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Barcode / Code") },
                        placeholder = { Text("Optional") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onScanBarcode) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Text(
                    "Category:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = cat
                                bulkStock = cat == "Local Liquor"
                                unitsList.indices.forEach { index ->
                                    unitsList[index] = unitsList[index].copy(stockUnitsPerSale = if (bulkStock) 0 else 1)
                                }
                            },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = bulkStock,
                        onCheckedChange = { enabled ->
                            bulkStock = enabled
                            unitsList.indices.forEach { index ->
                                unitsList[index] = unitsList[index].copy(stockUnitsPerSale = if (enabled) 0 else 1)
                            }
                        }
                    )
                    Column {
                        Text("Received in a drum / barrel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (bulkStock) "No opening stock count. Enter purchase cost, then set sale units and prices."
                            else "Enter the opening stock and a standard selling price.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (bulkStock) {
                    OutlinedTextField(
                        value = unitCostText,
                        onValueChange = { unitCostText = it.filter(Char::isDigit) },
                        label = { Text("Buying price per drum / barrel (KSh)") },
                        placeholder = { Text("e.g. 4,500") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = stockText,
                            onValueChange = { stockText = it.filter(Char::isDigit) },
                            label = { Text("Opening Stock") },
                            placeholder = { Text("e.g. 50 bottles") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = basePriceText,
                            onValueChange = {
                                basePriceText = it.filter(Char::isDigit)
                                val price = it.toIntOrNull() ?: 0
                                if (unitsList.isNotEmpty()) unitsList[0] = unitsList[0].copy(priceKsh = price)
                            },
                            label = { Text("Base Price (KSh)") },
                            placeholder = { Text("e.g. 50") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = unitCostText,
                        onValueChange = { unitCostText = it.filter(Char::isDigit) },
                        label = { Text("Buying cost (KSh)") },
                        placeholder = { Text("0 if unknown") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Configure Units of Sale:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    "Add each unit name and its selling price. These options will appear when you sell the product.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Quick Unit chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quickUnits.forEach { unitLabel ->
                        FilterChip(
                            selected = extraUnitName.equals(unitLabel, ignoreCase = true),
                            onClick = {
                                extraUnitName = unitLabel
                                // Sale units are priced independently. Bulk/container stock is selected below.
                            },
                            label = { Text(unitLabel, fontSize = 11.sp) }
                        )
                    }
                }

                // Units List
                unitsList.forEachIndexed { index, unit ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${unit.name} · ${ksh(unit.priceKsh)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (unit.stockUnitsPerSale > 1) {
                                Text(
                                    "Consumes ${unit.stockUnitsPerSale} stock units per sale",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = { unitsList.removeAt(index) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove unit", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Add additional unit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = extraUnitName,
                        onValueChange = { extraUnitName = it },
                        placeholder = { Text("Unit (e.g. Cup)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1.1f)
                    )
                    OutlinedTextField(
                        value = extraUnitPriceText,
                        onValueChange = { extraUnitPriceText = it.filter(Char::isDigit) },
                        placeholder = { Text("KSh", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.9f)
                    )
                    IconButton(
                        onClick = {
                            val p = extraUnitPriceText.toIntOrNull() ?: 0
                            if (extraUnitName.isNotBlank() && p > 0) {
                                unitsList.add(SaleUnit(extraUnitName.trim(), p, if (bulkStock) 0 else 1))
                                extraUnitName = ""
                                extraUnitPriceText = ""
                            }
                        },
                        enabled = extraUnitName.isNotBlank() && (extraUnitPriceText.toIntOrNull() ?: 0) > 0
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add unit", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                if (!error.isNullOrBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val typedBasePrice = basePriceText.toIntOrNull() ?: 0
                    val finalUnits = if (unitsList.isEmpty() && !bulkStock) {
                        val defaultUnitName = if (selectedCategory == "Local Liquor") "Cup" else "Bottle"
                        listOf(SaleUnit(defaultUnitName, typedBasePrice, 1))
                    } else {
                        unitsList.toList()
                    }
                    val p = if (bulkStock) finalUnits.firstOrNull()?.priceKsh ?: 0 else typedBasePrice
                    val s = if (bulkStock) 0 else stockText.toIntOrNull() ?: 0
                    val cost = unitCostText.toIntOrNull() ?: 0
                    onConfirm(name.trim(), barcode.trim(), selectedCategory, p, s, cost, finalUnits, selectedPhotoUri)
                },
                enabled = name.isNotBlank() && if (bulkStock) {
                    (unitCostText.toIntOrNull() ?: 0) > 0 && unitsList.isNotEmpty() && unitsList.all { it.priceKsh > 0 }
                } else {
                    (basePriceText.toIntOrNull() ?: 0) > 0 && (stockText.toIntOrNull() ?: 0) > 0
                },
                modifier = Modifier.testTag("confirm_create_product_button")
            ) {
                Text(if (bulkStock) "Save Product" else "Save & Add to Stock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Dialog to add item from catalog into shop stock
 */
@Composable
private fun AddCatalogItemDialog(
    product: ProductEntity,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Int, priceKsh: Int, unitCostKsh: Int) -> Unit
) {
    var quantityText by remember(product.catalogCode) { mutableStateOf("12") }
    var priceText by remember(product.catalogCode) { mutableStateOf("") }
    var unitCostText by remember(product.catalogCode) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${product.name} to Stock", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Catalog suggestion: ${ksh(product.suggestedPriceKsh)} · not your shop price", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter(Char::isDigit) },
                    label = { Text("Initial Stock (Bottles)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter(Char::isDigit) },
                    label = { Text("Your Selling Price (KSh)") },
                    placeholder = { Text("Enter your actual shop price") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = unitCostText,
                    onValueChange = { unitCostText = it.filter(Char::isDigit) },
                    label = { Text("Unit cost (KSh)") },
                    placeholder = { Text("0 if unknown") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                if (!error.isNullOrBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = quantityText.toIntOrNull() ?: 0
                    val p = priceText.toIntOrNull() ?: 0
                    val cost = unitCostText.toIntOrNull() ?: 0
                    if (q > 0 && p > 0 && cost >= 0) onConfirm(q, p, cost)
                },
                enabled = (quantityText.toIntOrNull() ?: 0) > 0 && (priceText.toIntOrNull() ?: 0) > 0
            ) {
                Text("Add to Shelf")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


@Composable
private fun QuantityStepperControl(quantity: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onChange(-1) },
            enabled = quantity > 1,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(38.dp)
        ) { Icon(Icons.Default.Remove, contentDescription = "Decrease") }

        Text(
            quantity.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center
        )

        OutlinedButton(
            onClick = { onChange(1) },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(38.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Increase") }
    }
}

@Composable
private fun StockIndicatorPill(
    quantity: Int,
    threshold: Int,
    modifier: Modifier = Modifier,
    showInStockText: Boolean = false
) {
    val outOfStock = quantity <= 0
    val low = quantity in 1..threshold
    val bg = when {
        outOfStock -> MaterialTheme.colorScheme.errorContainer
        low -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val textCol = when {
        outOfStock -> MaterialTheme.colorScheme.onErrorContainer
        low -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.primary
    }
    val label = when {
        outOfStock -> "Out of stock"
        showInStockText -> "$quantity in stock"
        else -> "$quantity"
    }

    Surface(
        modifier = modifier,
        color = bg,
        contentColor = textCol,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = if (showInStockText) 2.dp else 0.dp
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun ProductThumbnail(product: ProductEntity, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageId = remember(product.imageAssetName) {
        context.resources.getIdentifier(product.imageAssetName, "drawable", context.packageName)
    }
    val imageUrl = product.photoOverrideUri?.takeIf(String::isNotBlank)
        ?: product.imageSourceUrl?.trim()?.takeIf {
            it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
        }
    var imageLoadFailed by remember(imageUrl) { mutableStateOf(false) }
    if (imageUrl != null && !imageLoadFailed) {
        val request = remember(imageUrl, imageId) {
            ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .apply {
                    if (imageId != 0) {
                        placeholder(imageId)
                        error(imageId)
                    }
                }
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = product.name,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            onError = { imageLoadFailed = true }
        )
    } else if (imageId != 0) {
        Image(
            painter = painterResource(imageId),
            contentDescription = product.name,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        val category = product.category.lowercase(Locale.ROOT)
        val isBeverage = listOf("beer", "wine", "spirit", "liquor", "drink", "juice", "water", "soda", "cola")
            .any(category::contains)
        val brandLabel = product.brand.trim().ifBlank { product.name.trim() }
            .take(2).uppercase(Locale.ROOT)
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isBeverage) Icons.Default.LocalDrink else Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                if (brandLabel.isNotBlank()) {
                    Text(
                        text = brandLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmptyStateMessage(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

private fun isBulkContainerProduct(product: ProductEntity): Boolean {
    val units = resolveSaleUnits(product)
    return units.isNotEmpty() && units.all { it.stockUnitsPerSale == 0 }
}

private fun isLowOrOutOfStock(product: ProductEntity): Boolean =
    product.inMyStock && !isBulkContainerProduct(product) && product.stockQuantity <= product.lowStockThreshold

private fun ProductEntity.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    return listOf(name, brand, size, category, catalogCode, verifiedBarcode.orEmpty())
        .any { it.contains(q, ignoreCase = true) }
}

private fun ksh(value: Int): String = "KSh " + NumberFormat.getIntegerInstance(Locale.US).format(value)

private fun salesTodaySummary(sales: List<SaleEntity>): Pair<Int, Int> {
    val zone = TimeZone.getTimeZone("Africa/Nairobi")
    val now = Calendar.getInstance(zone)
    val activeToday = sales.filter { sale ->
        if (sale.undone) return@filter false
        val saleDay = Calendar.getInstance(zone).apply { timeInMillis = sale.createdAt }
        saleDay.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            saleDay.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    }
    return activeToday.sumOf { it.totalKsh } to activeToday.sumOf { it.quantity }
}

private fun formatSaleTime(timestamp: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Africa/Nairobi")
    }.format(Date(timestamp))
