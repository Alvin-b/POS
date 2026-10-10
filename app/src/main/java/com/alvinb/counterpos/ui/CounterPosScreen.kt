package com.alvinb.counterpos.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
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
    val todaySummary by viewModel.todaySummary.collectAsStateWithLifecycle()

    if (state.isCheckingSession) {
        BackendLoadingScreen()
        return
    }
    if (!state.isAuthenticated) {
        AuthenticationScreen(
            state = state,
            onEmailChange = viewModel::setAuthEmail,
            onPasswordChange = viewModel::setAuthPassword,
            onSubmit = viewModel::submitAuth,
            onToggleMode = viewModel::toggleAuthMode
        )
        return
    }
    if (state.isLoadingShop) {
        BackendLoadingScreen("Loading your shop…")
        return
    }
    if (state.selectedShopId == null) {
        ShopSelectionScreen(
            state = state,
            onNameChange = viewModel::setShopName,
            onCreateShop = viewModel::createShop,
            onSelectShop = viewModel::chooseShop,
            onRetry = viewModel::retryShopLoad,
            onSignOut = viewModel::signOut
        )
        return
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
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

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotice()
        }
    }

    Scaffold(
            topBar = {
                PosHeader(
                    todaySalesKsh = todaySummary.first,
                    shopName = state.shopName,
                    onSignOut = viewModel::signOut,
                    modifier = Modifier.statusBarsPadding()
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = state.tab == PosTab.SCAN,
                        onClick = { viewModel.selectTab(PosTab.SCAN) },
                        icon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("Scan") }
                    )
                    NavigationBarItem(
                        selected = state.tab == PosTab.SALES,
                        onClick = { viewModel.selectTab(PosTab.SALES) },
                        icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) },
                        label = { Text("Sales") }
                    )
                    NavigationBarItem(
                        selected = state.tab == PosTab.STOCK,
                        onClick = { viewModel.selectTab(PosTab.STOCK) },
                        icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                        label = { Text("Stock") }
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
                    PosTab.SCAN -> ScanScreen(
                        modifier = Modifier.padding(padding),
                        state = state,
                        products = products,
                        onQueryChange = viewModel::setScanQuery,
                        onFind = {
                            viewModel.openExactScanMatch()
                            focusManager.clearFocus()
                        },
                        onProductClick = viewModel::openSale
                    )
                    PosTab.SALES -> SalesScreen(
                        modifier = Modifier.padding(padding),
                        sales = sales,
                        todaySummary = todaySummary,
                        onUndo = viewModel::undoLastSale
                    )
                    PosTab.STOCK -> StockScreen(
                        modifier = Modifier.padding(padding),
                        state = state,
                        products = products,
                        onModeChange = viewModel::setStockMode,
                        onQueryChange = { query ->
                            if (state.stockMode == StockMode.MY_STOCK) viewModel.setStockQuery(query)
                            else viewModel.setLibraryQuery(query)
                        },
                        onCategoryChange = viewModel::setLibraryCategory,
                        onReceiveStock = viewModel::requestReceiveStock,
                        onQuickRestock = viewModel::quickRestock,
                        onChangePhoto = { product ->
                            photoTargetCode = product.catalogCode
                            photoPicker.launch(arrayOf("image/*"))
                        },
                        onAddProduct = viewModel::openLibraryProduct
                    )
                }
            }
        }

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

    if (state.showReceiveStock) {
        ModalBottomSheet(onDismissRequest = viewModel::dismissReceiveStock) {
            ReceiveStockSheet(
                products = products.filter { it.inMyStock },
                error = state.actionError,
                onReceive = viewModel::receiveStock
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
private fun PosHeader(todaySalesKsh: Int, shopName: String, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Counter POS", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(shopName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onSignOut) { Text("Sign out", fontSize = 11.sp) }
            Column(horizontalAlignment = Alignment.End) {
                Text("Today", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(ksh(todaySalesKsh), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ScanScreen(
    modifier: Modifier,
    state: CounterPosUiState,
    products: List<ProductEntity>,
    onQueryChange: (String) -> Unit,
    onFind: () -> Unit,
    onProductClick: (ProductEntity) -> Unit
) {
    val stocked = products.filter { it.inMyStock }
    val visible = stocked.filter { it.matches(state.scanQuery) }
    Column(modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchField(
                value = state.scanQuery,
                onValueChange = onQueryChange,
                label = "Barcode or product name",
                modifier = Modifier.weight(1f),
                onSearch = onFind
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onFind, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Text("Find")
            }
        }
        Text(
            "Scan a verified barcode when available, or type a product name and tap it below.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp, top = 6.dp, bottom = 8.dp)
        )
        Text("Products in your stock · tap to sell", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 6.dp))
        if (visible.isEmpty()) {
            EmptyMessage(if (stocked.isEmpty()) "Your stock is empty. Add products from Stock → Library." else "No stocked products match that search.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 14.dp),
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
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            onSearch()
            focusManager.clearFocus()
        })
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
        Column(Modifier.padding(9.dp)) {
            Box(
                Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                ProductPhoto(product, Modifier.fillMaxSize().padding(4.dp))
                StockPill(
                    quantity = product.stockQuantity,
                    threshold = product.lowStockThreshold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                )
            }
            Text(
                product.name,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
                lineHeight = 18.sp
            )
            Text(
                listOf(product.brand, product.size).filter(String::isNotBlank).joinToString(" · "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(ksh(product.sellingPriceKsh), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Qty ${product.stockQuantity}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val remotePhoto = product.remoteImageUrl
    if (!product.photoOverrideUri.isNullOrBlank()) {
        AsyncImage(
            model = product.photoOverrideUri,
            contentDescription = product.name,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else if (!product.photoPath.isNullOrBlank() && !remotePhoto.isNullOrBlank()) {
        AsyncImage(
            model = remotePhoto,
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
    } else if (!remotePhoto.isNullOrBlank()) {
        AsyncImage(
            model = remotePhoto,
            contentDescription = product.name,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text("No photo", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StockPill(quantity: Int, threshold: Int, modifier: Modifier = Modifier) {
    val low = quantity <= threshold
    val color = if (low) Color(0xFFFFF1D6) else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    val textColor = if (low) Color(0xFF8A5200) else MaterialTheme.colorScheme.onSurface
    Surface(modifier = modifier, color = color, contentColor = textColor, shape = RoundedCornerShape(50)) {
        Text("$quantity in stock", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SalesScreen(
    modifier: Modifier,
    sales: List<SaleEntity>,
    todaySummary: Pair<Int, Int>,
    onUndo: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Sales today", ksh(todaySummary.first), Modifier.weight(1f))
            StatCard("Units sold", todaySummary.second.toString(), Modifier.weight(1f))
        }
        OutlinedButton(
            onClick = onUndo,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("Undo last sale")
        }
        Text("Recent sales", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 7.dp))
        if (sales.isEmpty()) {
            EmptyMessage("No sales recorded yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
                items(sales, key = { it.backendSaleId ?: it.id.toString() }) { sale -> SaleRow(sale) }
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
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProductPhoto(productForImage, Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(2.dp))
            Column(Modifier.weight(1f)) {
                Text(sale.productName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${sale.quantity} × ${ksh(sale.unitPriceKsh)} · ${saleTime(sale.createdAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (sale.undone) Text("UNDONE", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(ksh(sale.totalKsh), fontWeight = FontWeight.Bold, color = if (sale.undone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StockScreen(
    modifier: Modifier,
    state: CounterPosUiState,
    products: List<ProductEntity>,
    onModeChange: (StockMode) -> Unit,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onReceiveStock: () -> Unit,
    onQuickRestock: (ProductEntity, Int) -> Unit,
    onChangePhoto: (ProductEntity) -> Unit,
    onAddProduct: (ProductEntity) -> Unit
) {
    val mine = products.filter { it.inMyStock }
    val library = products.filter { !it.inMyStock }
    val visible = if (state.stockMode == StockMode.MY_STOCK) {
        mine.filter { it.matches(state.stockQuery) }
    } else {
        library.filter { (state.libraryCategory == "All" || it.category == state.libraryCategory) && it.matches(state.libraryQuery) }
    }
    val categories = remember(library) { listOf("All") + library.map { it.category }.distinct().sorted() }

    Column(modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Products in this shop", mine.size.toString(), Modifier.weight(1f))
            StatCard("Total units in stock", mine.sumOf { it.stockQuantity }.toString(), Modifier.weight(1f))
        }
        if (state.stockMode == StockMode.MY_STOCK) {
            Button(onClick = onReceiveStock, modifier = Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 10.dp)) {
                Text("Receive stock")
            }
        } else {
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ModeButton("My stock · ${mine.size}", state.stockMode == StockMode.MY_STOCK, Modifier.weight(1f)) { onModeChange(StockMode.MY_STOCK) }
            ModeButton("Library · ${library.size}", state.stockMode == StockMode.LIBRARY, Modifier.weight(1f)) { onModeChange(StockMode.LIBRARY) }
        }
        SearchField(
            value = if (state.stockMode == StockMode.MY_STOCK) state.stockQuery else state.libraryQuery,
            onValueChange = onQueryChange,
            label = if (state.stockMode == StockMode.MY_STOCK) "Search My stock" else "Search beverage library",
            modifier = Modifier.fillMaxWidth().padding(top = 9.dp)
        )
        if (state.stockMode == StockMode.LIBRARY) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = state.libraryCategory == category,
                        onClick = { onCategoryChange(category) },
                        label = { Text(category) }
                    )
                }
            }
            Text("Only products not yet added to this shop are shown.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (visible.isEmpty()) {
            EmptyMessage(if (state.stockMode == StockMode.LIBRARY) "No other products match. Try another name or category." else "No stocked products match that search.")
        } else if (state.stockMode == StockMode.MY_STOCK) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 5.dp, bottom = 14.dp)) {
                items(visible, key = { it.catalogCode }) { product ->
                    StockProductRow(
                        product = product,
                        onRestockSix = { onQuickRestock(product, 6) },
                        onRestockTwentyFour = { onQuickRestock(product, 24) },
                        onChangePhoto = { onChangePhoto(product) },
                        showPhotoButton = state.shopRole == "owner"
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 5.dp, bottom = 14.dp)) {
                items(visible, key = { it.catalogCode }) { product ->
                    LibraryProductRow(product = product, onAdd = { onAddProduct(product) })
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 10.dp)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 10.dp)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StockProductRow(
    product: ProductEntity,
    onRestockSix: () -> Unit,
    onRestockTwentyFour: () -> Unit,
    onChangePhoto: () -> Unit,
    showPhotoButton: Boolean
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProductPhoto(product, Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp))
            Column(Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${product.size} · ${ksh(product.sellingPriceKsh)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${product.stockQuantity} in stock · Catalog code ${product.catalogCode}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedButton(onClick = onRestockSix, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp), modifier = Modifier.height(34.dp)) { Text("+6") }
                    OutlinedButton(onClick = onRestockTwentyFour, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp), modifier = Modifier.height(34.dp)) { Text("+24") }
                    if (showPhotoButton) {
                        OutlinedButton(onClick = onChangePhoto, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp), modifier = Modifier.height(34.dp)) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Photo", fontSize = 11.sp)
                        }
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
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProductPhoto(product, Modifier.size(68.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp))
            Column(Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${product.brand} · ${product.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(product.category, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Suggested retail: ${ksh(product.suggestedPriceKsh)}", fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
            }
            Button(onClick = onAdd, contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp)) { Text("Add") }
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
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
        ProductPhoto(product, Modifier.fillMaxWidth().height(155.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(6.dp))
        Text(product.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text("${product.brand} · ${product.size} · Catalog code ${product.catalogCode}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
            StatCard("Price", ksh(product.sellingPriceKsh), Modifier.weight(1f))
            StatCard("In stock", product.stockQuantity.toString(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Quantity", fontWeight = FontWeight.Medium)
            QuantityStepper(quantity = quantity, onChange = onQuantityChange, maxQuantity = product.stockQuantity)
        }
        Text("Total · ${ksh(product.sellingPriceKsh * quantity)}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (!error.isNullOrBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        Button(
            onClick = onConfirm,
            enabled = product.stockQuantity > 0,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp)
        ) { Text(if (product.stockQuantity > 0) "Sell · ${ksh(product.sellingPriceKsh * quantity)}" else "Out of stock") }
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
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
        ProductPhoto(product, Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(6.dp))
        Text(product.name, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text("${product.brand} · ${product.size} · ${product.category}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(
            product.verifiedBarcode?.let { "Verified barcode: $it" } ?: "No verified barcode listed",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp)) {
                Text("Suggested retail price", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(ksh(product.suggestedPriceKsh), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        OutlinedTextField(
            value = priceText,
            onValueChange = { onPriceChange(it.filter(Char::isDigit)) },
            label = { Text("Your shop selling price (KSh)") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Text("Quantity you have", fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            listOf(6, 12, 24).forEach { amount ->
                OutlinedButton(onClick = { onAddQuantity(amount) }, modifier = Modifier.weight(1f)) { Text("+$amount") }
            }
        }
        if (!error.isNullOrBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        Button(
            onClick = onConfirm,
            enabled = quantity > 0 && (priceText.toIntOrNull() ?: 0) > 0,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp)
        ) {
            Text("Add $quantity to My stock")
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
        Text(quantity.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 26.dp))
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
    onReceive: (String, Int) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedCode by remember { mutableStateOf<String?>(null) }
    var quantityText by remember { mutableStateOf("1") }
    val selected = products.firstOrNull { it.catalogCode == selectedCode }
    val matches = products.filter { it.matches(query) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Receive stock", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("Find a product already in your shop and enter the quantity received.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp, bottom = 10.dp))
        if (selected == null) {
            SearchField(query, { query = it }, "Product name or code", Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(top = 6.dp)) {
                items(matches, key = { it.catalogCode }) { product ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedCode = product.catalogCode }.padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProductPhoto(product, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(2.dp))
                        Column(Modifier.weight(1f)) {
                            Text(product.name, fontWeight = FontWeight.SemiBold)
                            Text("${product.stockQuantity} in stock", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            TextButton(onClick = { selectedCode = null }) { Text("← Choose a different product") }
            Text(selected.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text("Current stock: ${selected.stockQuantity}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = quantityText,
                onValueChange = { quantityText = it.filter(Char::isDigit) },
                label = { Text("Quantity received") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                singleLine = true
            )
            if (!error.isNullOrBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
            Button(
                onClick = { quantityText.toIntOrNull()?.takeIf { it > 0 }?.let { onReceive(selected.catalogCode, it) } },
                enabled = (quantityText.toIntOrNull() ?: 0) > 0,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp)
            ) { Text("Receive ${quantityText.toIntOrNull() ?: 0} units") }
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 34.dp, horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
