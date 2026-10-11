package com.alvinb.counterpos.ui

import android.content.Intent
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.local.entity.SaleUnit
import com.alvinb.counterpos.data.local.entity.parseSaleUnits
import com.alvinb.counterpos.ui.scanner.BarcodeScannerScreen
import com.alvinb.counterpos.ui.scanner.ScannerMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CounterPosWorkspace(viewModel: CounterPosViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val diningTables by viewModel.diningTables.collectAsStateWithLifecycle()
    val tableOrders by viewModel.tableOrders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    var photoTargetCode by remember { mutableStateOf<String?>(null) }

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

    val summary = remember(sales) { salesTodaySummary(sales) }
    val role = session.activeBusiness?.role.orEmpty()
    val canReceiveStock = role in setOf("owner", "manager", "inventory_clerk")
    val canEditProducts = role in setOf("owner", "manager")

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotice()
        }
    }

    // Camera Scanner view
    if (state.isScannerOpen) {
        BarcodeScannerScreen(
            mode = state.scannerMode,
            products = products,
            onClose = viewModel::closeScanner,
            onProductScannedForSale = viewModel::handleScannedSale,
            onProductScannedForStock = viewModel::handleScannedStock,
            onBarcodeAssigned = viewModel::handleAssignedBarcode,
            onBarcodeNotRecognized = viewModel::handleUnrecognizedBarcode
        )
        return
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
            LeanTopBar(
                state = state,
                todaySalesKsh = summary.first,
                unitsSold = summary.second,
                onSearchToggle = { active -> viewModel.setSearchActive(active) },
                onSearchChange = viewModel::setSearchQuery,
                onClearSearch = viewModel::clearSearch,
                onOpenScanner = { viewModel.openScanner(if (state.tab == PosTab.STOCK) ScannerMode.TAKE_STOCK else ScannerMode.SELL) },
                onAddNewProduct = { viewModel.openAddNewProduct() },
                canAddNewProduct = canReceiveStock,
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                NavigationBarItem(
                    selected = state.tab == PosTab.SCAN,
                    onClick = { viewModel.selectTab(PosTab.SCAN) },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "POS") },
                    label = { Text("POS / Sell", fontSize = 11.sp, fontWeight = if (state.tab == PosTab.SCAN) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.testTag("nav_pos")
                )
                NavigationBarItem(
                    selected = state.tab == PosTab.STOCK,
                    onClick = { viewModel.selectTab(PosTab.STOCK) },
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = "Stock") },
                    label = { Text("Stock (${products.count { it.inMyStock }})", fontSize = 11.sp, fontWeight = if (state.tab == PosTab.STOCK) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.testTag("nav_stock")
                )
                NavigationBarItem(
                    selected = state.tab == PosTab.SALES,
                    onClick = { viewModel.selectTab(PosTab.SALES) },
                    icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Sales") },
                    label = { Text("Sales", fontSize = 11.sp, fontWeight = if (state.tab == PosTab.SALES) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.testTag("nav_sales")
                )
                NavigationBarItem(
                    selected = state.tab == PosTab.TABLES,
                    onClick = { viewModel.selectTab(PosTab.TABLES) },
                    icon = { Icon(Icons.Default.TableRestaurant, contentDescription = "Tables") },
                    label = { Text("Tables", fontSize = 11.sp, fontWeight = if (state.tab == PosTab.TABLES) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.testTag("nav_tables")
                )
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
                    onOpenScanner = { viewModel.openScanner(ScannerMode.SELL) },
                    onProductClick = viewModel::openSale
                )
                PosTab.STOCK -> FullscreenStockScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    state = state,
                    products = products,
                    onModeChange = viewModel::setStockMode,
                    onCategoryChange = viewModel::setLibraryCategory,
                    onOpenStockScanner = { viewModel.openScanner(ScannerMode.TAKE_STOCK) },
                    onAddStockClick = viewModel::openAddStock,
                    onEditProductClick = viewModel::openEditProduct,
                    onAddNewProductClick = { viewModel.openAddNewProduct() },
                    onAddCatalogClick = viewModel::openCatalogItem,
                    canReceiveStock = canReceiveStock,
                    canEditProducts = canEditProducts,
                    onChangePhoto = { product ->
                        photoTargetCode = product.catalogCode
                        photoPicker.launch(arrayOf("image/*"))
                    }
                )
                PosTab.SALES -> FullscreenSalesScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    sales = sales,
                    todaySummary = summary,
                    onUndo = viewModel::undoLastSale
                )
                PosTab.TABLES -> TablesScreen(
                    tables = diningTables,
                    orders = tableOrders,
                    products = products,
                    selectedTableId = state.selectedTableId,
                    actionError = state.actionError,
                    isCreatingTable = state.isCreatingTable,
                    onCreateTable = viewModel::createDiningTable,
                    onOpenTable = viewModel::openTable,
                    onBackToTables = viewModel::backToTables,
                    onAddProduct = viewModel::openTableSale
                )
            }
        }
    }

    // Modal: Sell Product with Units of Sale (e.g. Bottle, Glass, Cup)
    state.selectedSaleCode?.let { code ->
        products.firstOrNull { it.catalogCode == code }?.let { product ->
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
                onConfirm = { name, stock, price, units ->
                    viewModel.confirmEditProduct(product, name, stock, price, units)
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
            onConfirm = { name, barcode, cat, price, stock, unitCost, units ->
                viewModel.confirmCreateProduct(name, barcode, cat, price, stock, unitCost, units)
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
}

/**
 * Lean top bar:
 * - Doesn't waste 40% of the screen with static bulk.
 * - Icon-based search: click magnifying glass to open search, click outside / 'X' to close.
 * - Live sales summary pill and quick scanner trigger.
 */
@Composable
private fun LeanTopBar(
    state: CounterPosUiState,
    todaySalesKsh: Int,
    unitsSold: Int,
    onSearchToggle: (Boolean) -> Unit,
    onSearchChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onOpenScanner: () -> Unit,
    onAddNewProduct: () -> Unit,
    canAddNewProduct: Boolean = true,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        if (state.isSearchActive) {
            // Expanded search mode with instant outside-dismiss and close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
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

                IconButton(onClick = onOpenScanner) {
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
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Counter POS",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (unitsSold > 0) "${ksh(todaySalesKsh)} · $unitsSold sold" else "Tap 📷 to scan barcode",
                            fontSize = 11.sp,
                            color = if (unitsSold > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (unitsSold > 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Search icon button (avoids using so much static space!)
                    IconButton(
                        onClick = { onSearchToggle(true) },
                        modifier = Modifier.testTag("search_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search products"
                        )
                    }

                    // In Stock section, show Add New Product icon
                    if (state.tab == PosTab.STOCK && canAddNewProduct) {
                        IconButton(
                            onClick = onAddNewProduct,
                            modifier = Modifier.testTag("add_product_icon_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add new product",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Barcode Scanner button
                    Surface(
                        onClick = onOpenScanner,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.testTag("top_scanner_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
    onOpenScanner: () -> Unit,
    onProductClick: (ProductEntity) -> Unit
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
        columns = GridCells.Adaptive(minSize = 145.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Category filter chips
        if (categories.size > 2) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }

        // Section header
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Shop Inventory (${visible.size})",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Tap to sell bottle / glass / cup",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (visible.isEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                EmptyStateMessage(
                    if (stocked.isEmpty()) {
                        "Your shop stock is empty.\nGo to 'Stock' tab below or tap 'Scan' to add bottles to your shelves."
                    } else {
                        "No products match '${state.searchQuery}'."
                    }
                )
            }
        } else {
            items(visible, key = { it.catalogCode }) { product ->
                ProductSellCard(product = product, onClick = { onProductClick(product) })
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
    onOpenStockScanner: () -> Unit,
    onAddStockClick: (ProductEntity) -> Unit,
    onEditProductClick: (ProductEntity) -> Unit,
    onAddNewProductClick: () -> Unit,
    onAddCatalogClick: (ProductEntity) -> Unit,
    canReceiveStock: Boolean,
    canEditProducts: Boolean,
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
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Stock Camera Restock Strip inside scrollable area
        if (canReceiveStock) {
            item {
                QuickScanActionStrip(
                    title = "Scan to Take Stock / Restock",
                    subtitle = "Point camera at cartons or bottles to increase stock",
                    actionLabel = "TAKE STOCK",
                    backgroundColor = Color(0xFFD97706),
                    onClick = onOpenStockScanner
                )
            }
        }

        // Mode switch + Add Product action bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // My Stock Tab Button
                Button(
                    onClick = { onModeChange(StockMode.MY_STOCK) },
                    modifier = Modifier.weight(1f),
                    colors = if (state.stockMode == StockMode.MY_STOCK) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                ) {
                    Text(
                        "Shop Shelf (${mine.size})",
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Beverage Library Tab Button
                Button(
                    onClick = { onModeChange(StockMode.LIBRARY) },
                    modifier = Modifier.weight(1f),
                    colors = if (state.stockMode == StockMode.LIBRARY) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                ) {
                    Text(
                        "Catalog (${library.size})",
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // "+ Add New Product" Button
                if (canReceiveStock) {
                    Surface(
                        onClick = onAddNewProductClick,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "New Product",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "New",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Shelf summary pill
        if (state.stockMode == StockMode.MY_STOCK) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Total: $totalBottles bottles on shelf across ${mine.size} products",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Add / Edit below",
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
                EmptyStateMessage(
                    if (state.stockMode == StockMode.MY_STOCK) {
                        "No products in your shop stock yet.\nTap '+ New' above to add local liquor/drinks or select from 'Catalog'."
                    } else {
                        "No catalog items match '${state.searchQuery}'."
                    }
                )
            }
        } else if (state.stockMode == StockMode.MY_STOCK) {
            items(visible, key = { it.catalogCode }) { product ->
                StockProductCard(
                    product = product,
                    canReceiveStock = canReceiveStock,
                    canEditProducts = canEditProducts,
                    onAddStock = { onAddStockClick(product) },
                    onEditProduct = { onEditProductClick(product) },
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

/**
 * Sales History Screen
 */
@Composable
private fun FullscreenSalesScreen(
    modifier: Modifier,
    sales: List<SaleEntity>,
    todaySummary: Pair<Int, Int>,
    onUndo: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Today's Revenue", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(ksh(todaySummary.first), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Units / Bottles Sold", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(todaySummary.second.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onUndo,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Undo last sale & restore shelf stock")
            }
        }

        item {
            Text(
                "Sales Receipts (${sales.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (sales.isEmpty()) {
            item {
                EmptyStateMessage("No sales made today yet. Go to POS tab to sell products.")
            }
        } else {
            items(sales, key = { it.id }) { sale ->
                SaleReceiptRow(sale = sale)
            }
        }
    }
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                ProductThumbnail(product, Modifier.fillMaxSize().padding(4.dp))
                StockIndicatorPill(
                    quantity = product.stockQuantity,
                    threshold = product.lowStockThreshold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                )
            }

            Text(
                text = product.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
                lineHeight = 17.sp
            )

            // Show units summary (e.g. "Bottle · Glass · Cup")
            if (units.size > 1) {
                Text(
                    text = units.joinToString(" · ") { "${it.name}: ${it.priceKsh}" },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else {
                Text(
                    text = listOf(product.brand, product.size).filter(String::isNotBlank).joinToString(" · ").ifBlank { product.category },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
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
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        if (units.size > 1) "Options" else "Sell",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
    onAddStock: () -> Unit,
    onEditProduct: () -> Unit,
    onChangePhoto: () -> Unit
) {
    val units = remember(product.salesUnitsJson, product.sellingPriceKsh) {
        parseSaleUnits(product.salesUnitsJson, product.sellingPriceKsh)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProductThumbnail(
                product,
                Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(2.dp)
            )

            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        product.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    StockIndicatorPill(product.stockQuantity, product.lowStockThreshold)
                }

                // Show units of sale configured (e.g. Bottle @ 300, Glass @ 70, Cup @ 35)
                if (units.size > 1) {
                    Text(
                        text = "Units: " + units.joinToString(", ") { "${it.name} (${ksh(it.priceKsh)})" },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else {
                    Text(
                        text = "${ksh(product.sellingPriceKsh)} · ${product.category}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Code: ${product.verifiedBarcode ?: product.catalogCode}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Clean Action Buttons: ONLY "+ Add" and "Edit" (No bulky +24 or +6 buttons!)
                if (canReceiveStock || canEditProducts) Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    // "+ Add" button (Opens input box for bottle count)
                    if (canReceiveStock) {
                        Button(
                            onClick = onAddStock,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Stock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // "Edit" button (Allows editing stock count, price, units)
                    if (canEditProducts) {
                        OutlinedButton(
                            onClick = onEditProduct,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit", fontSize = 12.sp)
                        }
                    }

                    // Photo button
                    if (canEditProducts) {
                        IconButton(
                            onClick = onChangePhoto,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = "Change photo",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Text("Complete Sale · ${ksh(total)}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
    onConfirm: (name: String, stock: Int, price: Int, units: List<SaleUnit>) -> Unit,
    onScanBarcode: () -> Unit
) {
    var name by remember(product.catalogCode) { mutableStateOf(product.name) }
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
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (product.verifiedBarcode.isNullOrBlank()) "Scan barcode for this product" else "Replace barcode")
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
                    onConfirm(name.trim(), s, p, unitsList.toList())
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
    onConfirm: (name: String, barcode: String, category: String, price: Int, stock: Int, unitCost: Int, units: List<SaleUnit>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var barcode by remember(prefilledBarcode) { mutableStateOf(prefilledBarcode.orEmpty()) }
    var selectedCategory by remember { mutableStateOf("Local Liquor") }
    var stockText by remember { mutableStateOf("12") }
    var basePriceText by remember { mutableStateOf("250") }
    var unitCostText by remember { mutableStateOf("") }

    val categories = listOf("Local Liquor", "Beer", "Spirit", "Cider", "RTD", "Wine", "Soft Drink", "Water")

    // Default units of sale (e.g. Bottle, and optional Cup & Glass for liquor)
    val unitsList = remember {
        mutableStateListOf(
            SaleUnit("Bottle", 250),
            SaleUnit("Glass", 70),
            SaleUnit("Cup", 35)
        )
    }

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
                        label = { Text("Initial Stock (Bottles)") },
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
                        label = { Text("Bottle Price (KSh)") },
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
                    IconButton(
                        onClick = {
                            val p = extraUnitPriceText.toIntOrNull() ?: 0
                            if (extraUnitName.isNotBlank() && p > 0) {
                                unitsList.add(SaleUnit(extraUnitName.trim(), p))
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
                    val p = basePriceText.toIntOrNull() ?: 0
                    val s = stockText.toIntOrNull() ?: 0
                    val cost = unitCostText.toIntOrNull() ?: 0
                    onConfirm(name.trim(), barcode.trim(), selectedCategory, p, s, cost, unitsList.toList())
                },
                enabled = name.isNotBlank() && (basePriceText.toIntOrNull() ?: 0) > 0,
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

/**
 * Quick scan banner component (used inside scrollable content, not statically stuck)
 */
@Composable
private fun QuickScanActionStrip(
    title: String,
    subtitle: String,
    actionLabel: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White
            ) {
                Text(
                    actionLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = backgroundColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
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
private fun StockIndicatorPill(quantity: Int, threshold: Int, modifier: Modifier = Modifier) {
    val outOfStock = quantity <= 0
    val low = quantity in 1..threshold
    val bg = when {
        outOfStock -> Color(0xFFFFE4E6)
        low -> Color(0xFFFEF3C7)
        else -> Color(0xFFD1FAE5)
    }
    val textCol = when {
        outOfStock -> Color(0xFFBE123C)
        low -> Color(0xFFB45309)
        else -> Color(0xFF047857)
    }
    val label = when {
        outOfStock -> "0 left"
        else -> "$quantity"
    }

    Surface(
        modifier = modifier,
        color = bg,
        contentColor = textCol,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SaleReceiptRow(sale: SaleEntity) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.LocalDrink,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
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
private fun ProductThumbnail(product: ProductEntity, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageId = remember(product.imageAssetName) {
        context.resources.getIdentifier(product.imageAssetName, "drawable", context.packageName)
    }
    if (!product.photoOverrideUri.isNullOrBlank()) {
        AsyncImage(
            model = product.photoOverrideUri,
            contentDescription = product.name,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else if (imageId != 0) {
        Image(
            painter = painterResource(imageId),
            contentDescription = product.name,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(26.dp)
            )
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
