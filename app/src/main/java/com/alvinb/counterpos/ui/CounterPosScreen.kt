package com.alvinb.counterpos.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.widget.Toast
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.OutboxEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.local.entity.SaleUnit
import com.alvinb.counterpos.data.local.entity.parseSaleUnits
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
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val diningTables by viewModel.diningTables.collectAsStateWithLifecycle()
    val tableOrders by viewModel.tableOrders.collectAsStateWithLifecycle()
    val serviceCounters by viewModel.serviceCounters.collectAsStateWithLifecycle()
    val products = remember(cachedProducts, state.counterStockByProduct, state.selectedCounterId, session.activeBusiness?.counterInventoryMode) {
        if (session.activeBusiness?.counterInventoryMode == "per_counter") {
            cachedProducts.map { product ->
                if (product.inMyStock && product.remoteId != null) {
                    product.copy(stockQuantity = state.counterStockByProduct[product.remoteId] ?: 0)
                } else product
            }
        } else cachedProducts
    }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    var photoTargetCode by remember { mutableStateOf<String?>(null) }
    var newProductPhotoUri by remember { mutableStateOf<String?>(null) }
    var transferProductCode by remember { mutableStateOf<String?>(null) }
    var pendingSalesPdf by remember { mutableStateOf<ByteArray?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val code = photoTargetCode
        photoTargetCode = null
        if (uri != null && code != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            products.firstOrNull { it.catalogCode == code }?.let { product ->
                viewModel.updatePhoto(product, uri.toString())
            }
        }
    }

    val newProductPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            newProductPhotoUri = uri.toString()
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
    val canManageTables = role in setOf("owner", "manager", "cashier")
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
    LaunchedEffect(supportsTables, state.tab) {
        if (!supportsTables && state.tab == PosTab.TABLES) viewModel.selectTab(PosTab.SCAN)
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
        viewModel.refreshCounterStock()
    }
    LaunchedEffect(state.tab) {
        viewModel.refreshBusinessData()
        if (state.tab == PosTab.REPORTS) viewModel.loadCounterReport(todayStartIso(), nowIso())
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

    BackHandler {
        when {
            state.isTeamPanelOpen -> viewModel.closeTeamPanel()
            state.isStaffManagementOpen -> viewModel.closeStaffManagement()
            state.tab != PosTab.SCAN && state.previousTab != null -> viewModel.goBackToPreviousTab()
            state.tab != PosTab.SCAN -> viewModel.selectTab(PosTab.SCAN)
            else -> Unit
        }
    }

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
                    onSyncData = { viewModel.synchronizeData(showNotice = true) }
                )
                if (!state.isOnline) {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Offline mode · All sales and tables saved locally. Will sync immediately when online.",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
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
            Surface(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(26.dp)),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 5.dp,
                shadowElevation = 4.dp
            ) {
                NavigationBar(
                    modifier = Modifier.height(72.dp),
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = state.tab == PosTab.SCAN,
                        onClick = { viewModel.selectTab(PosTab.SCAN) },
                        icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "POS", modifier = Modifier.size(21.dp)) },
                        label = { Text("POS / Sell", fontSize = 10.sp, fontWeight = if (state.tab == PosTab.SCAN) FontWeight.Bold else FontWeight.Normal) },
                        colors = posNavigationItemColors(),
                        modifier = Modifier.testTag("nav_pos")
                    )
                    if (canManageStock) {
                        NavigationBarItem(
                            selected = state.tab == PosTab.STOCK,
                            onClick = { viewModel.selectTab(PosTab.STOCK) },
                            icon = { Icon(Icons.Default.Inventory2, contentDescription = "Stock", modifier = Modifier.size(21.dp)) },
                            label = { Text("Stock (${products.count { it.inMyStock }})", fontSize = 10.sp, fontWeight = if (state.tab == PosTab.STOCK) FontWeight.Bold else FontWeight.Normal) },
                            colors = posNavigationItemColors(),
                            modifier = Modifier.testTag("nav_stock")
                        )
                    }
                    NavigationBarItem(
                        selected = state.tab == PosTab.SALES,
                        onClick = { viewModel.selectTab(PosTab.SALES) },
                        icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Sales", modifier = Modifier.size(20.dp)) },
                        label = { Text("Sales", fontSize = 10.sp, fontWeight = if (state.tab == PosTab.SALES) FontWeight.Bold else FontWeight.Normal) },
                        colors = posNavigationItemColors(),
                        modifier = Modifier.testTag("nav_sales")
                    )
                    NavigationBarItem(
                        selected = state.tab == PosTab.REPORTS,
                        onClick = { viewModel.selectTab(PosTab.REPORTS) },
                        icon = { Icon(Icons.Default.Assessment, contentDescription = "Reports", modifier = Modifier.size(20.dp)) },
                        label = { Text("Reports", fontSize = 10.sp, fontWeight = if (state.tab == PosTab.REPORTS) FontWeight.Bold else FontWeight.Normal) },
                        colors = posNavigationItemColors(),
                        modifier = Modifier.testTag("nav_reports")
                    )
                    if (supportsTables) {
                        NavigationBarItem(
                            selected = state.tab == PosTab.TABLES,
                            onClick = { viewModel.selectTab(PosTab.TABLES) },
                            icon = { Icon(Icons.Default.TableRestaurant, contentDescription = "Tables", modifier = Modifier.size(20.dp)) },
                            label = { Text("Tables", fontSize = 10.sp, fontWeight = if (state.tab == PosTab.TABLES) FontWeight.Bold else FontWeight.Normal) },
                            colors = posNavigationItemColors(),
                            modifier = Modifier.testTag("nav_tables")
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
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
                            photoPicker.launch(arrayOf("image/*"))
                        }
                    )
                } else {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Text("You do not have permission to adjust stock.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                PosTab.SALES -> FullscreenSalesScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    sales = sales,
                    todaySummary = summary,
                    onUndo = viewModel::undoLastSale,
                    onExportPdf = { start, end ->
                        val filtered = sales.filter { it.createdAt in start..end }
                        pendingSalesPdf = buildSalesPdf(filtered, start, end, "Sales report")
                        salesPdfPicker.launch("sales-${formatReportDate(start)}-${formatReportDate(end)}.pdf")
                    },
                    onSharePdf = { start, end ->
                        val filtered = sales.filter { it.createdAt in start..end }
                        sharePdf(context, buildSalesPdf(filtered, start, end, "Sales report"), "sales-${formatReportDate(start)}.pdf")
                    }
                )
                PosTab.REPORTS -> CounterReportScreen(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    counters = serviceCounters,
                    registeredWaiterEmails = registeredWaiterEmails,
                    sales = sales,
                    remoteRows = state.counterReport,
                    isLoading = state.isLoadingCounterReport,
                    reportFilter = state.reportFilter,
                    reportCounterFilter = state.reportCounterFilter,
                    onSetReportFilter = viewModel::setReportFilter,
                    onSetReportCounterFilter = viewModel::setReportCounterFilter,
                    onRefresh = { viewModel.loadCounterReport(todayStartIso(), nowIso()) },
                    onOpenRecordDispensation = viewModel::openRecordDispensation,
                    onQuickDispenseForCounter = { counter ->
                        viewModel.selectServingCounter(counter.id, counter.name)
                        viewModel.openRecordDispensation()
                    },
                    onExportPdf = {
                        val start = if (state.reportFilter == "today") startOfReportDay(System.currentTimeMillis()) else sales.minOfOrNull { it.createdAt } ?: startOfReportDay(System.currentTimeMillis())
                        val end = if (state.reportFilter == "today") endOfReportDay(System.currentTimeMillis()) else System.currentTimeMillis()
                        pendingSalesPdf = buildSalesPdf(sales.filter { it.createdAt in start..end }, start, end, "Performance report")
                        salesPdfPicker.launch("report-${formatReportDate(start)}.pdf")
                    },
                    onSharePdf = {
                        val start = if (state.reportFilter == "today") startOfReportDay(System.currentTimeMillis()) else sales.minOfOrNull { it.createdAt } ?: startOfReportDay(System.currentTimeMillis())
                        val end = if (state.reportFilter == "today") endOfReportDay(System.currentTimeMillis()) else System.currentTimeMillis()
                        sharePdf(context, buildSalesPdf(sales.filter { it.createdAt in start..end }, start, end, "Performance report"), "report-${formatReportDate(start)}.pdf")
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
                        canSettleTables = canSettleTables,
                        isSettlingTable = state.isSettlingTable,
                        isDeletingTable = state.isDeletingTable,
                        onCreateTable = viewModel::createDiningTable,
                        onDeleteTable = viewModel::deleteDiningTable,
                        onSettleTable = viewModel::settleTableBill,
                        onOpenTable = viewModel::openTable,
                        onBackToTables = viewModel::backToTables,
                        onNextTable = viewModel::selectNextTable,
                        onRefresh = { viewModel.synchronizeData(showNotice = true) },
                        onAddProduct = viewModel::openTableSale,
                        onScanProduct = { viewModel.openScanner(ScannerMode.TABLE_ADD) },
                        showCounterLabels = serviceCounters.size > 1,
                        registeredWaiterEmails = registeredWaiterEmails,
                        businessName = session.activeBusiness?.shopName ?: "Counter POS"
                    )
                }
            }
        }
    }

    if (state.isSyncRecoveryOpen) {
        AlertDialog(
            onDismissRequest = viewModel::closeSyncRecovery,
            title = { Text("Saved changes need attention") },
            text = {
                Column(Modifier.height(340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Retry sends the same saved operation ID, so a previously accepted sale is not duplicated.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!state.syncRecoveryError.isNullOrBlank()) {
                        Text(state.syncRecoveryError.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    if (state.blockedOutbox.isEmpty()) {
                        Text("No blocked changes for this business.", fontSize = 13.sp)
                    }
                    state.blockedOutbox.forEach { item ->
                        val label = when (item.operationType) {
                            "sale" -> "Sale"
                            "table_sale" -> "Table sale"
                            "create_dining_table" -> "Create table"
                            "add_catalog_product" -> "Add product to stock"
                            "register_product" -> "Register product"
                            "add_stock" -> "Receive stock"
                            "transfer_stock" -> "Transfer stock"
                            else -> item.operationType.replace('_', ' ').replaceFirstChar { it.uppercase() }
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
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
            initialTableId = state.selectedTableId,
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
            val selectedTable = diningTables.firstOrNull { it.id == state.selectedTableId }
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
                    selectedTableName = selectedTable?.name,
                    onSelectUnit = viewModel::selectSaleUnit,
                    onQuantityChange = viewModel::adjustSaleQuantity,
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
                onConfirm = { name, stock, price, units, barcode ->
                    viewModel.confirmEditProduct(product, name, stock, price, units, barcode)
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
            photoUri = newProductPhotoUri,
            error = state.actionError,
            onDismiss = viewModel::dismissAddNewProduct,
            onPickPhoto = { newProductPhotoPicker.launch(arrayOf("image/*")) },
            onScanBarcode = {
                viewModel.dismissAddNewProduct()
                viewModel.openScanner(ScannerMode.TAKE_STOCK)
            },
            onConfirm = { name, barcode, cat, price, stock, unitCost, units ->
                viewModel.confirmCreateProduct(name, barcode, cat, price, stock, unitCost, units, photoOverrideUri = newProductPhotoUri)
                newProductPhotoUri = null
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
                onClose = viewModel::closeTeamPanel,
                onBusinessNameSave = { viewModel.updateBusinessName(it) },
                onLoadMembers = { viewModel.loadEmployeeMembers() },
                onCounterCreate = { name -> viewModel.createServiceCounter(name) },
                onCounterRename = { id, name -> viewModel.renameServiceCounter(id, name) },
                onOpenStaffManagement = {
                    viewModel.closeTeamPanel()
                    viewModel.openStaffManagement()
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
                }
            )
        }
    }
}

@Composable
private fun posNavigationItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = Color.Transparent,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)

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
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    var counterMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
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

                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocusRequester)
                        .testTag("search_text_input"),
                    placeholder = {
                        Text(
                            if (state.tab == PosTab.STOCK) "Search bottles & brands..." else "Search to sell...",
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

                IconButton(onClick = onOpenScanner, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan barcode",
                        tint = MaterialTheme.colorScheme.primary
                    )
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
                            fontSize = 16.sp,
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

                    // Search icon button (avoids using so much static space!)
                    IconButton(
                        onClick = { onSearchToggle(true) },
                        modifier = Modifier.size(40.dp).testTag("search_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search products",
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
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.height(40.dp).testTag("top_scanner_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Open scanner",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Scan",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
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
    val mine = remember(products) { products.filter { it.inMyStock } }
    val library = remember(products) { products.filter { !it.inMyStock } }

    val visible = remember(mine, library, state.stockMode, state.searchQuery, state.libraryCategory) {
        if (state.stockMode == StockMode.MY_STOCK) {
            mine.filter { it.matches(state.searchQuery) }
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

        // Shelf summary pill
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
                            "Total: $totalBottles bottles on shelf",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "Add / Edit",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
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
                if (state.stockMode == StockMode.MY_STOCK && state.searchQuery.isBlank()) {
                    ShelfEmptyState(
                        onBrowseCatalog = { onModeChange(StockMode.LIBRARY) },
                        onScan = onScanStockClick
                    )
                } else {
                    EmptyStateMessage("No catalog items match '${state.searchQuery}'.")
                }
            }
        } else if (state.stockMode == StockMode.MY_STOCK) {
            items(visible, key = { it.catalogCode }) { product ->
                StockProductCard(
                    product = product,
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

private data class CounterProductSummary(
    val productName: String,
    val unitName: String,
    val totalQuantity: Int,
    val totalKsh: Long
)

private data class CounterWaiterSummary(
    val waiterName: String,
    val itemsServed: Int,
    val totalKsh: Long,
    val tablesServed: List<String>
)

private data class ServiceCounterReportData(
    val counterId: String,
    val counterName: String,
    val totalItems: Int,
    val totalKsh: Long,
    val salesCount: Int,
    val products: List<CounterProductSummary>,
    val waiters: List<CounterWaiterSummary>
)

/**
 * Reports Screen
 * Focused on sales performance: revenue, drinks sold, top products, and counters.
 */
@Composable
private fun ReportMetricCard(
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = containerColor
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, fontSize = 11.sp, color = contentColor.copy(alpha = 0.8f))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CounterReportScreen(
    modifier: Modifier,
    counters: List<ServiceCounter>,
    registeredWaiterEmails: Set<String>,
    sales: List<SaleEntity>,
    remoteRows: List<com.alvinb.counterpos.data.remote.CounterReportRow>,
    isLoading: Boolean,
    reportFilter: String,
    reportCounterFilter: String,
    onSetReportFilter: (String) -> Unit,
    onSetReportCounterFilter: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenRecordDispensation: () -> Unit,
    onQuickDispenseForCounter: (ServiceCounter) -> Unit,
    onExportPdf: () -> Unit,
    onSharePdf: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val todayStart = remember { startOfReportDay(System.currentTimeMillis()) }

    val effectiveCounters = counters

    val counterReports = remember(effectiveCounters, registeredWaiterEmails, sales, remoteRows, reportFilter) {
        val effectiveSales = if (reportFilter == "today") {
            sales.filter { !it.undone && it.createdAt >= todayStart }
        } else {
            sales.filter { !it.undone }
        }

        effectiveCounters.map { counter ->
            val matchingSales = effectiveSales.filter { sale ->
                sale.counterId == counter.id ||
                sale.counterName.equals(counter.name, ignoreCase = true) ||
                (effectiveCounters.size == 1 && (sale.counterId.isNullOrBlank() || sale.counterId == "unassigned"))
            }

            val productsMap = matchingSales.groupBy { it.productName to it.unitName }
                .map { (key, group) ->
                    CounterProductSummary(
                        productName = key.first,
                        unitName = key.second,
                        totalQuantity = group.sumOf { it.quantity },
                        totalKsh = group.sumOf { it.totalKsh.toLong() }
                    )
                }.sortedByDescending { it.totalQuantity }

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
                .groupBy { sale -> sale.waiterName!!.trim() }
                .map { (waiter, group) ->
                    val tables = group.mapNotNull { it.tableName?.takeIf(String::isNotBlank) }.distinct()
                    CounterWaiterSummary(
                        waiterName = waiter,
                        itemsServed = group.sumOf { it.quantity },
                        totalKsh = group.sumOf { it.totalKsh.toLong() },
                        tablesServed = tables
                    )
                }.sortedByDescending { it.itemsServed }

            val localItems = matchingSales.sumOf { it.quantity }
            val localKsh = matchingSales.sumOf { it.totalKsh.toLong() }
            val localSalesCount = matchingSales.size

            val remoteMatch = remoteRows.firstOrNull {
                it.counterId == counter.id || it.counterName.equals(counter.name, ignoreCase = true)
            }
            val finalItems = if (localItems > 0) localItems else (remoteMatch?.units?.toInt() ?: 0)
            val finalKsh = if (localKsh > 0L) localKsh else (remoteMatch?.revenueKsh ?: 0L)
            val finalSalesCount = if (localSalesCount > 0) localSalesCount else (remoteMatch?.saleCount?.toInt() ?: 0)

            ServiceCounterReportData(
                counterId = counter.id,
                counterName = if (effectiveCounters.size == 1) "Sales" else counter.name,
                totalItems = finalItems,
                totalKsh = finalKsh,
                salesCount = finalSalesCount,
                products = productsMap,
                waiters = waitersMap
            )
        }
    }

    val visibleReports = remember(counterReports, reportCounterFilter) {
        if (reportCounterFilter == "all") counterReports
        else counterReports.filter { it.counterId == reportCounterFilter }
    }

    val totalClubUnits = counterReports.sumOf { it.totalItems }
    val totalClubRevenue = counterReports.sumOf { it.totalKsh }
    val totalSalesCount = counterReports.sumOf { it.salesCount }
    val topProducts = remember(counterReports) {
        counterReports.flatMap { it.products }
            .groupBy { it.productName to it.unitName }
            .map { (key, rows) ->
                CounterProductSummary(
                    productName = key.first,
                    unitName = key.second,
                    totalQuantity = rows.sumOf { it.totalQuantity },
                    totalKsh = rows.sumOf { it.totalKsh }
                )
            }
            .sortedByDescending { it.totalQuantity }
            .take(6)
    }

    fun copyShareableReport() {
        val dateLabel = if (reportFilter == "today") "Today (${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())})" else "All-Time"
        val sb = StringBuilder()
        sb.appendLine("🍹 CLUB MULTI-COUNTER DISPENSATION REPORT")
        sb.appendLine("Period: $dateLabel")
        sb.appendLine("Total Drinks Dispensed: $totalClubUnits")
        sb.appendLine("Total Revenue: ${kshLong(totalClubRevenue)}")
        sb.appendLine("Sales: $totalSalesCount | Counters: ${counterReports.size}")
        sb.appendLine("========================================")
        counterReports.forEach { c ->
            sb.appendLine("\n📍 ${c.counterName.uppercase()}:")
            sb.appendLine("Total: ${c.totalItems} drinks · ${kshLong(c.totalKsh)}")
            if (c.products.isNotEmpty()) {
                sb.appendLine("Products Taken:")
                c.products.forEach { p ->
                    sb.appendLine("  • ${p.productName} (${p.unitName}): ${p.totalQuantity} taken · ${kshLong(p.totalKsh)}")
                }
            } else {
                sb.appendLine("  (No products dispensed yet)")
            }
            if (c.waiters.isNotEmpty()) {
                sb.appendLine("Waiters Serving from this counter:")
                c.waiters.forEach { w ->
                    val tablesText = if (w.tablesServed.isNotEmpty()) " (Served: ${w.tablesServed.joinToString(", ")})" else ""
                    sb.appendLine("  👤 ${w.waiterName}: ${w.itemsServed} drinks · ${kshLong(w.totalKsh)}$tablesText")
                }
            }
        }
        clipboard.setText(AnnotatedString(sb.toString()))
        Toast.makeText(context, "Counter report copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Reports", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("The numbers that matter", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, contentDescription = "Refresh reports")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("today" to "Today", "all" to "All-time").forEach { (value, label) ->
                    FilterChip(
                        selected = reportFilter == value,
                        onClick = { onSetReportFilter(value) },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportMetricCard("Revenue", kshLong(totalClubRevenue), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Modifier.weight(1.2f))
                ReportMetricCard("Drinks", totalClubUnits.toString(), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(0.8f))
                ReportMetricCard("Sales", totalSalesCount.toString(), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, Modifier.weight(0.8f))
            }
        }

        item { Text("Top drinks", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        if (topProducts.isEmpty()) {
            item { EmptyStateMessage("No sales recorded for this period.") }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        topProducts.forEachIndexed { index, product ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(product.productName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(product.unitName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${product.totalQuantity} sold", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(kshLong(product.totalKsh), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (index < topProducts.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        }
                    }
                }
            }
        }

        if (counterReports.size > 1) {
            item { Text("By counter", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        counterReports.sortedByDescending { it.totalKsh }.forEachIndexed { index, counter ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(counter.counterName, fontWeight = FontWeight.SemiBold)
                                    Text("${counter.totalItems} drinks · ${counter.salesCount} sales", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(kshLong(counter.totalKsh), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            if (index < counterReports.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onSharePdf) {
                    Icon(Icons.Default.Share, contentDescription = "Share report")
                }
                IconButton(onClick = onExportPdf) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Download report PDF")
                }
            }
        }
    }
}

private fun kshLong(value: Long): String = "KSh " + NumberFormat.getIntegerInstance(Locale.US).format(value)

@Composable
private fun FullscreenSalesScreen(
    modifier: Modifier,
    sales: List<SaleEntity>,
    todaySummary: Pair<Int, Int>,
    onUndo: () -> Unit,
    onExportPdf: (Long, Long) -> Unit,
    onSharePdf: (Long, Long) -> Unit
) {
    var showDateRange by remember { mutableStateOf(false) }
    var shareAfterDateSelection by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Sales", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Today’s performance and recent receipts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Today's Revenue", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(ksh(todaySummary.first), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Units / Bottles Sold", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(todaySummary.second.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onUndo,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Undo sale", fontSize = 11.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { shareAfterDateSelection = true; showDateRange = true },
                    modifier = Modifier.weight(0.5f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share sales", modifier = Modifier.size(18.dp))
                }
                OutlinedButton(
                    onClick = { shareAfterDateSelection = false; showDateRange = true },
                    modifier = Modifier.weight(0.5f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Download sales PDF", modifier = Modifier.size(18.dp))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Recent receipts", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("${sales.size}", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (sales.isEmpty()) {
            item { EmptyStateMessage("No sales made today yet. Go to POS tab to sell products.") }
        } else {
            items(sales, key = { it.id }) { sale -> SaleReceiptRow(sale = sale) }
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
private fun SalesDateRangeDialog(
    onDismiss: () -> Unit,
    onExport: (Long, Long) -> Unit
) {
    val context = LocalContext.current
    var start by remember { mutableStateOf(startOfReportDay(System.currentTimeMillis())) }
    var end by remember { mutableStateOf(endOfReportDay(System.currentTimeMillis())) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export sales PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose one day or any date range.", fontSize = 13.sp)
                OutlinedButton(onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = start }
                    DatePickerDialog(context, { _, year, month, day ->
                        start = startOfReportDay(year, month, day)
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("From: ${formatReportDate(start)}")
                }
                OutlinedButton(onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = end }
                    DatePickerDialog(context, { _, year, month, day ->
                        end = endOfReportDay(year, month, day)
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("To: ${formatReportDate(end)}")
                }
                if (end < start) {
                    Text("The end date must be on or after the start date.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = end >= start, onClick = { onExport(start, end) }) { Text("Create PDF") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun startOfReportDay(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun endOfReportDay(time: Long): Long = Calendar.getInstance().apply {
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

private fun formatReportDate(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(time))

private fun buildSalesPdf(sales: List<SaleEntity>, start: Long, end: Long, title: String): ByteArray {
    val document = PdfDocument()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val navy = android.graphics.Color.rgb(25, 32, 48)
    val accent = android.graphics.Color.rgb(38, 125, 105)
    val muted = android.graphics.Color.rgb(100, 108, 120)
    var pageNumber = 1
    var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
    var y = 48f
    fun line(text: String, size: Float = 10f, color: Int = navy, bold: Boolean = false) {
        paint.textSize = size
        paint.color = color
        paint.typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        page.canvas.drawText(text, 32f, y, paint)
        y += size + 9f
    }
    fun divider() {
        paint.color = android.graphics.Color.rgb(225, 228, 233)
        paint.strokeWidth = 1f
        page.canvas.drawLine(32f, y, 563f, y, paint)
        y += 16f
    }
    line("COUNTER POS", 11f, accent, true)
    line(title, 22f, navy, true)
    line("${formatReportDate(start)}  —  ${formatReportDate(end)}", 10f, muted)
    y += 8f
    divider()
    line("SUMMARY", 10f, accent, true)
    line("Revenue        KSh ${NumberFormat.getIntegerInstance(Locale.US).format(sales.sumOf { it.totalKsh })}", 12f, navy, true)
    line("Units sold     ${sales.sumOf { it.quantity }}        Transactions     ${sales.size}", 10f, muted)
    y += 8f
    divider()
    line("SALES DETAIL", 10f, accent, true)
    line("Date                         Item                                      Total", 9f, muted, true)
    sales.forEach { sale ->
        if (y > 790f) {
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            y = 48f
            line("COUNTER POS  ·  $title", 10f, accent, true)
            divider()
        }
        val product = sale.productName.take(27)
        line("${formatReportDate(sale.createdAt)}   ${product.padEnd(29)}  KSh ${sale.totalKsh}", 9f)
        line("                         ${sale.quantity} × ${sale.unitName}", 8f, muted)
    }
    y += 8f
    divider()
    line("Generated by Counter POS", 8f, muted)
    document.finishPage(page)
    val output = ByteArrayOutputStream()
    document.writeTo(output)
    document.close()
    return output.toByteArray()
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
    val units = remember(product.salesUnitsJson, product.sellingPriceKsh) {
        parseSaleUnits(product.salesUnitsJson, product.sellingPriceKsh)
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(122.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                ProductThumbnail(product, Modifier.fillMaxSize().padding(5.dp))
                StockIndicatorPill(
                    quantity = product.stockQuantity,
                    threshold = product.lowStockThreshold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp),
                    showInStockText = true
                )
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
                    ksh(product.sellingPriceKsh),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
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
    canReceiveStock: Boolean,
    canEditProducts: Boolean,
    canTransferStock: Boolean,
    onAddStock: () -> Unit,
    onEditProduct: () -> Unit,
    onTransferStock: () -> Unit,
    onChangePhoto: () -> Unit
) {
    val units = remember(product.salesUnitsJson, product.sellingPriceKsh) {
        parseSaleUnits(product.salesUnitsJson, product.sellingPriceKsh)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProductThumbnail(
                product,
                Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(2.dp)
                    .clickable(enabled = canEditProducts, onClick = onChangePhoto)
            )

            Column(Modifier.weight(1f)) {
                Text(
                    product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Show units of sale configured (e.g. Bottle @ 300, Glass @ 70, Cup @ 35)
                if (units.size > 1) {
                    Text(
                        text = units.joinToString(" · ") { "${it.name} ${ksh(it.priceKsh)}" },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Text(
                        text = "${ksh(product.sellingPriceKsh)} · ${product.category}",
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(2.dp)
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
    selectedTableName: String? = null,
    onSelectUnit: (SaleUnit) -> Unit,
    onQuantityChange: (Int) -> Unit,
    onConfirm: () -> Unit
) {
    val units = remember(product.salesUnitsJson, product.sellingPriceKsh) {
        parseSaleUnits(product.salesUnitsJson, product.sellingPriceKsh)
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
                    "${product.category} · Shelf Stock: ${product.stockQuantity} bottles",
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
                            ksh(unit.priceKsh),
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
                    "$quantity × $selectedUnitName @ ${ksh(selectedUnitPrice)}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    ksh(total),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (!error.isNullOrBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        }

        Button(
            onClick = onConfirm,
            enabled = serviceCounters.isNotEmpty() && selectedCounterId != null && units.isNotEmpty() && selectedUnitPrice > 0,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            val label = if (!selectedTableName.isNullOrBlank()) "Add to $selectedTableName check · ${ksh(total)}" else "Complete Sale · ${ksh(total)}"
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
        if (selectedProduct != null) parseSaleUnits(selectedProduct.salesUnitsJson, selectedProduct.sellingPriceKsh)
        else emptyList()
    }
    var selectedUnit by remember(units) { mutableStateOf(units.firstOrNull() ?: SaleUnit("Bottle", selectedProduct?.sellingPriceKsh ?: 0)) }

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
                            label = { Text("${unit.name} · ${ksh(unit.priceKsh)}") }
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
                        "Total: ${ksh(totalAmount)}",
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
                Text("Record sale · ${ksh(totalAmount)}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
    var bottlesText by remember { mutableStateOf("") }
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
                    label = { Text("Number of bottles to add") },
                    placeholder = { Text("e.g. 6, 12, 24, 48...") },
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
    onConfirm: (name: String, stock: Int, price: Int, units: List<SaleUnit>, barcode: String?) -> Unit,
    onScanBarcode: () -> Unit
) {
    var name by remember(product.catalogCode) { mutableStateOf(product.name) }
    var barcodeText by remember(product.catalogCode) { mutableStateOf(product.verifiedBarcode ?: "") }
    var stockText by remember(product.catalogCode) { mutableStateOf(product.stockQuantity.toString()) }
    var priceText by remember(product.catalogCode) { mutableStateOf(product.sellingPriceKsh.toString()) }

    val initialUnits = remember(product.catalogCode) {
        parseSaleUnits(product.salesUnitsJson, product.sellingPriceKsh)
    }
    val unitsList = remember(product.catalogCode) {
        mutableStateListOf<SaleUnit>().apply { addAll(initialUnits) }
    }

    var newUnitName by remember { mutableStateOf("") }
    var newUnitPriceText by remember { mutableStateOf("") }

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
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter(Char::isDigit) },
                        label = { Text("Base Price (KSh)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "Configured Units of Sale:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    "How you sell this drink (e.g. Bottle, Glass, Cup)",
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
                        Text(
                            "${unit.name} · ${ksh(unit.priceKsh)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = { if (unitsList.size > 1) unitsList.removeAt(index) },
                            enabled = unitsList.size > 1,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove unit", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Add another unit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = newUnitName,
                        onValueChange = { newUnitName = it },
                        placeholder = { Text("e.g. Glass, Cup", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
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
                                unitsList.add(SaleUnit(newUnitName.trim(), p))
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
                        unitsList.add(SaleUnit("Bottle", p))
                    }
                    onConfirm(name.trim(), s, p, unitsList.toList(), barcodeText.trim().ifBlank { null })
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
    photoUri: String?,
    error: String?,
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit,
    onScanBarcode: () -> Unit,
    onConfirm: (name: String, barcode: String, category: String, price: Int, stock: Int, unitCost: Int, units: List<SaleUnit>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var barcode by remember(prefilledBarcode) { mutableStateOf(prefilledBarcode.orEmpty()) }
    var selectedCategory by remember { mutableStateOf("Local Liquor") }
    var stockText by remember { mutableStateOf("") }
    var basePriceText by remember { mutableStateOf("") }
    var unitCostText by remember { mutableStateOf("") }

    val categories = listOf("Local Liquor", "Beer", "Spirit", "Cider", "RTD", "Wine", "Soft Drink", "Water")

    // Default units of sale (e.g. Bottle, and optional Cup & Glass for liquor)
    val unitsList = remember { mutableStateListOf<SaleUnit>() }

    var extraUnitName by remember { mutableStateOf("") }
    var extraUnitPriceText by remember { mutableStateOf("") }
    var extraUnitStockText by remember { mutableStateOf("1") }

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
                OutlinedButton(
                    onClick = onPickPhoto,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (photoUri.isNullOrBlank()) "Set product picture" else "Change product picture")
                }
                if (!photoUri.isNullOrBlank()) {
                    Text("Picture selected", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
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
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = stockText,
                        onValueChange = { stockText = it.filter(Char::isDigit) },
                        label = { Text("Initial Stock (base units)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = basePriceText,
                        onValueChange = {
                            basePriceText = it.filter(Char::isDigit)
                            val p = it.toIntOrNull() ?: 0
                            if (unitsList.isNotEmpty()) {
                                unitsList[0] = unitsList[0].copy(priceKsh = p)
                            }
                        },
                        label = { Text("Base Price (KSh)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = unitCostText,
                    onValueChange = { unitCostText = it.filter(Char::isDigit) },
                    label = { Text("Unit cost (KSh)") },
                    placeholder = { Text("0 if unknown") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "Configure Units of Sales:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    "Products like local liquor are sold in cups, glass & bottles. Set their prices below:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Units List
                unitsList.forEachIndexed { index, unit ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${unit.name} · ${ksh(unit.priceKsh)} · uses ${unit.stockUnitsPerSale} stock",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = { if (unitsList.size > 1) unitsList.removeAt(index) },
                            enabled = unitsList.size > 1,
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
                        placeholder = { Text("Unit name (e.g. Tot)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = extraUnitPriceText,
                        onValueChange = { extraUnitPriceText = it.filter(Char::isDigit) },
                        placeholder = { Text("Price KSh", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.9f)
                    )
                    OutlinedTextField(
                        value = extraUnitStockText,
                        onValueChange = { extraUnitStockText = it.filter(Char::isDigit) },
                        placeholder = { Text("Stock", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.65f)
                    )
                    IconButton(
                        onClick = {
                            val p = extraUnitPriceText.toIntOrNull() ?: 0
                            val stockUnits = extraUnitStockText.toIntOrNull() ?: 0
                            if (extraUnitName.isNotBlank() && p > 0 && stockUnits > 0) {
                                unitsList.add(SaleUnit(extraUnitName.trim(), p, stockUnits))
                                extraUnitName = ""
                                extraUnitPriceText = ""
                                extraUnitStockText = "1"
                            }
                        },
                        enabled = extraUnitName.isNotBlank() && (extraUnitPriceText.toIntOrNull() ?: 0) > 0 &&
                            (extraUnitStockText.toIntOrNull() ?: 0) > 0
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
                    val p = basePriceText.toIntOrNull() ?: unitsList.firstOrNull()?.priceKsh ?: 0
                    val s = stockText.toIntOrNull() ?: 0
                    val cost = unitCostText.toIntOrNull() ?: 0
                    onConfirm(name.trim(), barcode.trim(), selectedCategory, p, s, cost, unitsList.toList())
                },
                enabled = name.isNotBlank() && (stockText.toIntOrNull() ?: 0) > 0 &&
                    ((basePriceText.toIntOrNull() ?: 0) > 0 || unitsList.any { it.priceKsh > 0 }),
                modifier = Modifier.testTag("confirm_create_product_button")
            ) {
                Text("Save & Add to Stock")
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
    var priceText by remember(product.catalogCode) { mutableStateOf(product.suggestedPriceKsh.toString()) }
    var unitCostText by remember(product.catalogCode) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${product.name} to Stock", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("${product.brand} · ${product.size} · Suggested: ${ksh(product.suggestedPriceKsh)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun SaleReceiptRow(sale: SaleEntity) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.LocalDrink,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(Modifier.weight(1f)) {
                Text(sale.productName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    "${sale.quantity} × ${sale.unitName} @ ${ksh(sale.unitPriceKsh)} · ${formatSaleTime(sale.createdAt)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (sale.undone) {
                    Text("REFUNDED / UNDONE", color = MaterialTheme.colorScheme.error, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                ksh(sale.totalKsh),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (sale.undone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
            )
        }
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
private fun EmptyStateMessage(message: String) {
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
