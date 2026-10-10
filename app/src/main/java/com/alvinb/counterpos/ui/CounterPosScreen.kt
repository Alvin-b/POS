package com.alvinb.counterpos.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
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
fun CounterPosApp(viewModel: CounterPosViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val context = LocalContext.current
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

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotice()
        }
    }

    // Fullscreen Camera Scanner
    if (state.isScannerOpen) {
        BarcodeScannerScreen(
            mode = state.scannerMode,
            products = products,
            onClose = viewModel::closeScanner,
            onProductScannedForSale = viewModel::handleScannedSale,
            onProductScannedForStock = viewModel::handleScannedStock,
            onBarcodeNotRecognized = viewModel::handleUnrecognizedBarcode
        )
        return
    }

    Scaffold(
        topBar = {
            PosHeader(
                todaySalesKsh = summary.first,
                unitsSold = summary.second,
                onOpenScanner = { viewModel.openScanner(ScannerMode.SELL) },
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = state.tab == PosTab.SCAN,
                    onClick = { viewModel.selectTab(PosTab.SCAN) },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                    label = { Text("POS / Sell") }
                )
                NavigationBarItem(
                    selected = state.tab == PosTab.STOCK,
                    onClick = { viewModel.selectTab(PosTab.STOCK) },
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                    label = { Text("Stock & Scan") }
                )
                NavigationBarItem(
                    selected = state.tab == PosTab.SALES,
                    onClick = { viewModel.selectTab(PosTab.SALES) },
                    icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) },
                    label = { Text("Sales") }
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
        } else if (state.startupError != null && products.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("Product catalog could not be loaded", fontWeight = FontWeight.Bold)
                    Text(state.startupError.orEmpty(), modifier = Modifier.padding(top = 8.dp))
                }
            }
        } else {
            when (state.tab) {
                PosTab.SCAN -> LeanScanScreen(
                    modifier = Modifier.padding(padding),
                    state = state,
                    products = products,
                    onQueryChange = viewModel::setScanQuery,
                    onOpenScanner = { viewModel.openScanner(ScannerMode.SELL) },
                    onProductClick = viewModel::openSale
                )
                PosTab.STOCK -> LeanStockScreen(
                    modifier = Modifier.padding(padding),
                    state = state,
                    products = products,
                    onModeChange = viewModel::setStockMode,
                    onQueryChange = { query ->
                        if (state.stockMode == StockMode.MY_STOCK) viewModel.setStockQuery(query)
                        else viewModel.setLibraryQuery(query)
                    },
                    onCategoryChange = viewModel::setLibraryCategory,
                    onOpenStockScanner = { viewModel.openScanner(ScannerMode.TAKE_STOCK) },
                    onReceiveStock = viewModel::requestReceiveStock,
                    onQuickRestock = viewModel::quickRestock,
                    onChangePhoto = { product ->
                        photoTargetCode = product.catalogCode
                        photoPicker.launch(arrayOf("image/*"))
                    },
                    onAddProduct = viewModel::openLibraryProduct
                )
                PosTab.SALES -> SalesScreen(
                    modifier = Modifier.padding(padding),
                    sales = sales,
                    todaySummary = summary,
                    onUndo = viewModel::undoLastSale
                )
            }
        }
    }

    // Modal Sheet for Selling a Product
    state.selectedSaleCode?.let { code ->
        products.firstOrNull { it.catalogCode == code }?.let { product ->
            ModalBottomSheet(onDismissRequest = viewModel::dismissSale) {
                SaleSheet(
                    product = product,
                    quantity = state.saleQuantity,
                    error = state.actionError,
                    onQuantityChange = viewModel::adjustSaleQuantity,
                    onConfirm = viewModel::confirmSale
                )
            }
        }
    }

    // Modal Sheet for Adding Product from Catalog to My Stock
    state.selectedAddCode?.let { code ->
        products.firstOrNull { it.catalogCode == code }?.let { product ->
            ModalBottomSheet(onDismissRequest = viewModel::dismissAdd) {
                AddToStockSheet(
                    product = product,
                    quantity = state.addQuantity,
                    priceText = state.addPriceText,
                    error = state.actionError,
                    onQuantityChange = viewModel::adjustAddQuantity,
                    onAddQuantity = viewModel::addQuantity,
                    onSetQuantity = viewModel::setAddQuantity,
                    onPriceChange = viewModel::setAddPrice,
                    onConfirm = viewModel::confirmAddToStock
                )
            }
        }
    }

    // Modal Sheet for Receiving Stock
    if (state.showReceiveStock) {
        ModalBottomSheet(onDismissRequest = viewModel::dismissReceiveStock) {
            ReceiveStockSheet(
                products = products.filter { it.inMyStock },
                error = state.actionError,
                onReceive = viewModel::receiveStock,
                onOpenScanner = {
                    viewModel.dismissReceiveStock()
                    viewModel.openScanner(ScannerMode.TAKE_STOCK)
                }
            )
        }
    }

    // Unrecognized Barcode Modal Sheet
    state.unrecognizedBarcode?.let { barcode ->
        ModalBottomSheet(onDismissRequest = viewModel::dismissUnrecognizedBarcode) {
            RegisterBarcodeSheet(
                barcode = barcode,
                error = state.actionError,
                onDismiss = viewModel::dismissUnrecognizedBarcode,
                onRegister = { b, name, cat, price, stock ->
                    viewModel.registerNewBarcodeProduct(
                        barcode = b,
                        name = name,
                        category = cat,
                        priceKsh = price,
                        initialStock = stock
                    )
                }
            )
        }
    }

    state.startupError?.takeIf { products.isNotEmpty() }?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::clearStartupError,
            title = { Text("Catalog warning") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = viewModel::clearStartupError) { Text("OK") } }
        )
    }
}

@Composable
private fun PosHeader(
    todaySalesKsh: Int,
    unitsSold: Int,
    onOpenScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Pulse Counter POS",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    "Club & Beverage Register",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        ksh(todaySalesKsh),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "$unitsSold units sold",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    onClick = onOpenScanner,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan barcode",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeanScanScreen(
    modifier: Modifier,
    state: CounterPosUiState,
    products: List<ProductEntity>,
    onQueryChange: (String) -> Unit,
    onOpenScanner: () -> Unit,
    onProductClick: (ProductEntity) -> Unit
) {
    val stocked = remember(products) { products.filter { it.inMyStock } }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = remember(stocked) {
        listOf("All") + stocked.map { it.category }.distinct().sorted()
    }

    val visible = remember(stocked, state.scanQuery, selectedCategory) {
        stocked.filter { product ->
            (selectedCategory == "All" || product.category.equals(selectedCategory, ignoreCase = true)) &&
                product.matches(state.scanQuery)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Quick Camera Scan Banner (Supermarket style)
        Card(
            onClick = onOpenScanner,
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
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
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            "Camera Barcode Scanner",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Text(
                            "Scan to sell · Continuous supermarket mode",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        "SCAN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Lean Search Bar without any bulky find button!
        LeanSearchBar(
            value = state.scanQuery,
            onValueChange = onQueryChange,
            placeholder = "Search product or barcode...",
            onScanClick = onOpenScanner,
            modifier = Modifier.fillMaxWidth()
        )

        // Category Filter Chips
        if (categories.size > 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp)
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

        // Section Title & Counter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Products in Stock (${visible.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Tap item to sell",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (visible.isEmpty()) {
            EmptyMessage(
                if (stocked.isEmpty()) {
                    "Your stock is empty. Tap 'Stock & Scan' below to scan bottles into your shop."
                } else {
                    "No stocked items match '${state.scanQuery}'."
                }
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 145.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visible, key = { it.catalogCode }) { product ->
                    ProductTile(product = product, onClick = { onProductClick(product) })
                }
            }
        }
    }
}

@Composable
private fun LeanStockScreen(
    modifier: Modifier,
    state: CounterPosUiState,
    products: List<ProductEntity>,
    onModeChange: (StockMode) -> Unit,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onOpenStockScanner: () -> Unit,
    onReceiveStock: () -> Unit,
    onQuickRestock: (ProductEntity, Int) -> Unit,
    onChangePhoto: (ProductEntity) -> Unit,
    onAddProduct: (ProductEntity) -> Unit
) {
    val mine = remember(products) { products.filter { it.inMyStock } }
    val library = remember(products) { products.filter { !it.inMyStock } }

    val visible = remember(mine, library, state.stockMode, state.stockQuery, state.libraryQuery, state.libraryCategory) {
        if (state.stockMode == StockMode.MY_STOCK) {
            mine.filter { it.matches(state.stockQuery) }
        } else {
            library.filter { (state.libraryCategory == "All" || it.category == state.libraryCategory) && it.matches(state.libraryQuery) }
        }
    }

    val categories = remember(library) { listOf("All") + library.map { it.category }.distinct().sorted() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Stock Header Statistics
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Registered in Shop", mine.size.toString(), Modifier.weight(1f))
            StatCard("Total Units on Shelf", mine.sumOf { it.stockQuantity }.toString(), Modifier.weight(1f))
        }

        // Camera Take Stock Button
        Card(
            onClick = onOpenStockScanner,
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFD97706)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
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
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            "Scan to Take Stock / Receive",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Text(
                            "Point camera at bottles or cartons to restock",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        "SCAN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Mode switch buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ModeButton("My Stock (${mine.size})", state.stockMode == StockMode.MY_STOCK, Modifier.weight(1f)) {
                onModeChange(StockMode.MY_STOCK)
            }
            ModeButton("Beverage Library (${library.size})", state.stockMode == StockMode.LIBRARY, Modifier.weight(1f)) {
                onModeChange(StockMode.LIBRARY)
            }
        }

        // Lean Search Bar without any find button
        LeanSearchBar(
            value = if (state.stockMode == StockMode.MY_STOCK) state.stockQuery else state.libraryQuery,
            onValueChange = onQueryChange,
            placeholder = if (state.stockMode == StockMode.MY_STOCK) "Search shop stock..." else "Search beverage catalog...",
            onScanClick = onOpenStockScanner,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )

        if (state.stockMode == StockMode.LIBRARY) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
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

        if (visible.isEmpty()) {
            EmptyMessage(
                if (state.stockMode == StockMode.LIBRARY) "No other catalog products match."
                else "No stocked products found."
            )
        } else if (state.stockMode == StockMode.MY_STOCK) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 14.dp)
            ) {
                items(visible, key = { it.catalogCode }) { product ->
                    StockProductRow(
                        product = product,
                        onRestockSix = { onQuickRestock(product, 6) },
                        onRestockTwentyFour = { onQuickRestock(product, 24) },
                        onChangePhoto = { onChangePhoto(product) }
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 14.dp)
            ) {
                items(visible, key = { it.catalogCode }) { product ->
                    LibraryProductRow(product = product, onAdd = { onAddProduct(product) })
                }
            }
        }
    }
}

@Composable
private fun LeanSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text(placeholder, fontSize = 13.sp) },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear search",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(onClick = onScanClick) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = "Open camera barcode scanner",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun ProductTile(product: ProductEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                ProductPhoto(product, Modifier.fillMaxSize().padding(4.dp))
                StockPill(
                    quantity = product.stockQuantity,
                    threshold = product.lowStockThreshold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                )
            }
            Text(
                product.name,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 14.sp,
                lineHeight = 18.sp
            )
            Text(
                listOf(product.brand, product.size).filter(String::isNotBlank).joinToString(" · "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        "Sell",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductPhoto(product: ProductEntity, modifier: Modifier = Modifier) {
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
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun StockPill(quantity: Int, threshold: Int, modifier: Modifier = Modifier) {
    val outOfStock = quantity <= 0
    val low = quantity in 1..threshold
    val color = when {
        outOfStock -> Color(0xFFFFE4E6)
        low -> Color(0xFFFEF3C7)
        else -> Color(0xFFD1FAE5)
    }
    val textColor = when {
        outOfStock -> Color(0xFFBE123C)
        low -> Color(0xFFB45309)
        else -> Color(0xFF047857)
    }
    val text = when {
        outOfStock -> "Out of stock"
        else -> "$quantity left"
    }
    Surface(
        modifier = modifier,
        color = color,
        contentColor = textColor,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SalesScreen(
    modifier: Modifier,
    sales: List<SaleEntity>,
    todaySummary: Pair<Int, Int>,
    onUndo: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Total Revenue Today", ksh(todaySummary.first), Modifier.weight(1f))
            StatCard("Total Bottles Sold", todaySummary.second.toString(), Modifier.weight(1f))
        }
        OutlinedButton(
            onClick = onUndo,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 12.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("Undo last sale")
        }
        Text("Sale Receipts", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 7.dp))
        if (sales.isEmpty()) {
            EmptyMessage("No sales recorded yet today.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 14.dp)
            ) {
                items(sales, key = { it.id }) { sale -> SaleRow(sale) }
            }
        }
    }
}

@Composable
private fun SaleRow(sale: SaleEntity) {
    val productForImage = ProductEntity(
        catalogCode = sale.catalogCode,
        verifiedBarcode = null,
        name = sale.productName,
        brand = "",
        size = "",
        category = "",
        suggestedPriceKsh = sale.unitPriceKsh,
        sellingPriceKsh = sale.unitPriceKsh,
        stockQuantity = 0,
        inMyStock = true,
        lowStockThreshold = 5,
        imageAssetName = sale.imageAssetName,
        photoOverrideUri = sale.photoOverrideUri
    )
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProductPhoto(
                productForImage,
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(2.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(sale.productName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${sale.quantity} × ${ksh(sale.unitPriceKsh)} · ${saleTime(sale.createdAt)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (sale.undone) {
                    Text("UNDONE / REFUNDED", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                ksh(sale.totalKsh),
                fontWeight = FontWeight.Bold,
                color = if (sale.undone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StockProductRow(
    product: ProductEntity,
    onRestockSix: () -> Unit,
    onRestockTwentyFour: () -> Unit,
    onChangePhoto: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProductPhoto(
                product,
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(3.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${product.size} · ${ksh(product.sellingPriceKsh)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Barcode: ${product.verifiedBarcode ?: product.catalogCode}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    OutlinedButton(
                        onClick = onRestockSix,
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) { Text("+6", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = onRestockTwentyFour,
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) { Text("+24 (Crate)", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = onChangePhoto,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Photo", fontSize = 10.sp)
                    }
                }
            }
            StockPill(product.stockQuantity, product.lowStockThreshold)
        }
    }
}

@Composable
private fun LibraryProductRow(product: ProductEntity, onAdd: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProductPhoto(
                product,
                Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(3.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${product.brand} · ${product.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(product.category, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Suggested retail: ${ksh(product.suggestedPriceKsh)}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = onAdd,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) { Text("Add") }
        }
    }
}

@Composable
private fun SaleSheet(
    product: ProductEntity,
    quantity: Int,
    error: String?,
    onQuantityChange: (Int) -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        ProductPhoto(
            product,
            Modifier
                .fillMaxWidth()
                .height(155.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(6.dp)
        )
        Text(product.name, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text(
            "${product.brand} · ${product.size} · Barcode ${product.verifiedBarcode ?: product.catalogCode}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
            StatCard("Price per unit", ksh(product.sellingPriceKsh), Modifier.weight(1f))
            StatCard("Available Stock", product.stockQuantity.toString(), Modifier.weight(1f))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Quantity to sell", fontWeight = FontWeight.Medium)
            QuantityStepper(quantity = quantity, onChange = onQuantityChange, maxQuantity = product.stockQuantity)
        }
        Text(
            "Total: ${ksh(product.sellingPriceKsh * quantity)}",
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            color = MaterialTheme.colorScheme.primary
        )
        if (!error.isNullOrBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        Button(
            onClick = onConfirm,
            enabled = product.stockQuantity > 0,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 18.dp)
        ) {
            Text(if (product.stockQuantity > 0) "Complete Sale · ${ksh(product.sellingPriceKsh * quantity)}" else "Out of stock")
        }
    }
}

@Composable
private fun AddToStockSheet(
    product: ProductEntity,
    quantity: Int,
    priceText: String,
    error: String?,
    onQuantityChange: (Int) -> Unit,
    onAddQuantity: (Int) -> Unit,
    onSetQuantity: (Int) -> Unit,
    onPriceChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    var quantityText by remember(product.catalogCode, quantity) { mutableStateOf(quantity.toString()) }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        ProductPhoto(
            product,
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(6.dp)
        )
        Text(product.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text("${product.brand} · ${product.size} · ${product.category}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(
            "Barcode: ${product.verifiedBarcode ?: product.catalogCode}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Suggested retail price", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(ksh(product.suggestedPriceKsh), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        OutlinedTextField(
            value = priceText,
            onValueChange = { onPriceChange(it.filter(Char::isDigit)) },
            label = { Text("Your selling price (KSh)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text("Initial stock quantity", fontWeight = FontWeight.Medium)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { entered ->
                        val digits = entered.filter(Char::isDigit)
                        quantityText = digits
                        digits.toIntOrNull()?.let(onSetQuantity)
                    },
                    label = { Text("Units") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                QuantityStepper(quantity = quantity, onChange = onQuantityChange, maxQuantity = Int.MAX_VALUE)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            listOf(6, 12, 24).forEach { amount ->
                OutlinedButton(onClick = { onAddQuantity(amount) }, modifier = Modifier.weight(1f)) {
                    Text("+$amount")
                }
            }
        }
        if (!error.isNullOrBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        Button(
            onClick = onConfirm,
            enabled = quantity > 0 && (priceText.toIntOrNull() ?: 0) > 0,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 18.dp)
        ) {
            Text("Add $quantity to Shop Stock")
        }
    }
}

@Composable
private fun QuantityStepper(quantity: Int, onChange: (Int) -> Unit, maxQuantity: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = { onChange(-1) },
            enabled = quantity > 1,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(42.dp)
        ) { Icon(Icons.Default.Remove, contentDescription = "Decrease quantity") }
        Text(
            quantity.toString(),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 26.dp),
            textAlign = TextAlign.Center
        )
        OutlinedButton(
            onClick = { onChange(1) },
            enabled = quantity < maxQuantity,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(42.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Increase quantity") }
    }
}

@Composable
private fun ReceiveStockSheet(
    products: List<ProductEntity>,
    error: String?,
    onReceive: (String, Int) -> Unit,
    onOpenScanner: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedCode by remember { mutableStateOf<String?>(null) }
    var quantityText by remember { mutableStateOf("6") }
    val selected = products.firstOrNull { it.catalogCode == selectedCode }
    val matches = products.filter { it.matches(query) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Receive Stock", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onOpenScanner) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = "Scan barcode to receive",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (selected == null) {
            LeanSearchBar(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search product to receive...",
                onScanClick = onOpenScanner,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
            ) {
                items(matches, key = { it.catalogCode }) { product ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selectedCode = product.catalogCode }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProductPhoto(
                            product,
                            Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(2.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(product.name, fontWeight = FontWeight.SemiBold)
                            Text("${product.stockQuantity} currently in stock", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            TextButton(onClick = { selectedCode = null }) { Text("← Choose different item") }
            Text(selected.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text("Current stock: ${selected.stockQuantity}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = quantityText,
                onValueChange = { quantityText = it.filter(Char::isDigit) },
                label = { Text("Quantity received") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                singleLine = true
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                listOf(6, 12, 24).forEach { amount ->
                    OutlinedButton(
                        onClick = { quantityText = amount.toString() },
                        modifier = Modifier.weight(1f)
                    ) { Text("+$amount") }
                }
            }
            if (!error.isNullOrBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
            Button(
                onClick = { quantityText.toIntOrNull()?.takeIf { it > 0 }?.let { onReceive(selected.catalogCode, it) } },
                enabled = (quantityText.toIntOrNull() ?: 0) > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp)
            ) { Text("Receive ${quantityText.toIntOrNull() ?: 0} units into stock") }
        }
    }
}

@Composable
private fun RegisterBarcodeSheet(
    barcode: String,
    error: String?,
    onDismiss: () -> Unit,
    onRegister: (barcode: String, name: String, category: String, price: Int, stock: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Beverage") }
    var priceText by remember { mutableStateOf("250") }
    var stockText by remember { mutableStateOf("12") }

    val categories = listOf("Beer", "Cider", "RTD", "Spirit", "Wine", "Soft Drink", "Water", "Snack")

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column {
                Text("New Product Barcode Scanned", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Barcode: $barcode", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Product Name (e.g. Guinness Draught)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            "Category",
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
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it.filter(Char::isDigit) },
                label = { Text("Selling Price (KSh)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = stockText,
                onValueChange = { stockText = it.filter(Char::isDigit) },
                label = { Text("Initial Stock") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        if (!error.isNullOrBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Button(
            onClick = {
                val price = priceText.toIntOrNull() ?: 0
                val stock = stockText.toIntOrNull() ?: 0
                onRegister(barcode, name, selectedCategory, price, stock)
            },
            enabled = name.isNotBlank() && (priceText.toIntOrNull() ?: 0) > 0,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp)
        ) {
            Text("Register & Add to Shop Stock")
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontSize = 13.sp
        )
    }
}

private fun ProductEntity.matches(query: String): Boolean {
    if (query.isBlank()) return true
    return listOf(name, brand, size, category, catalogCode, verifiedBarcode.orEmpty())
        .any { it.contains(query.trim(), ignoreCase = true) }
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

private fun saleTime(timestamp: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Africa/Nairobi")
    }.format(Date(timestamp))
