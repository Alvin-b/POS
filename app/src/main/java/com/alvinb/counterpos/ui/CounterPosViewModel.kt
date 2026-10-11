package com.alvinb.counterpos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alvinb.counterpos.data.CounterPosRepository
import com.alvinb.counterpos.data.remote.AdminBusiness
import com.alvinb.counterpos.data.SessionSnapshot
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.local.entity.DiningTableEntity
import com.alvinb.counterpos.data.local.entity.SaleUnit
import com.alvinb.counterpos.data.local.entity.parseSaleUnits
import com.alvinb.counterpos.data.local.entity.serializeSaleUnits
import com.alvinb.counterpos.ui.scanner.ScannerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class PosTab { SCAN, STOCK, SALES, TABLES }
enum class StockMode { MY_STOCK, LIBRARY }

data class CounterPosUiState(
    val tab: PosTab = PosTab.SCAN,
    val stockMode: StockMode = StockMode.MY_STOCK,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val libraryCategory: String = "All",
    val selectedTableId: String? = null,
    val isCreatingTable: Boolean = false,
    val adminSelectedBusinessId: String? = null,

    // Sale sheet
    val selectedSaleCode: String? = null,
    val selectedUnitName: String = "Bottle",
    val selectedUnitPrice: Int = 0,
    val saleQuantity: Int = 1,

    // Add bottles to stock dialog
    val addStockProductCode: String? = null,

    // Edit product & stock dialog
    val editProductCode: String? = null,

    // Add new product dialog
    val isAddingNewProduct: Boolean = false,
    val prefilledBarcode: String? = null,

    // Add catalog item to shop dialog
    val selectedCatalogCode: String? = null,
    val catalogInitialStock: Int = 12,
    val catalogSellingPrice: String = "",

    // Camera Scanner
    val isScannerOpen: Boolean = false,
    val scannerMode: ScannerMode = ScannerMode.SELL,
    val barcodeAssignmentCode: String? = null,
    val unrecognizedBarcode: String? = null,

    val actionError: String? = null,
    val notice: String? = null,
    val isReady: Boolean = false,
    val startupError: String? = null
)

class CounterPosViewModel(private val repository: CounterPosRepository) : ViewModel() {
    val session = repository.session.stateIn(viewModelScope, SharingStarted.Eagerly, SessionSnapshot())
    private val _adminBusinesses = MutableStateFlow<List<AdminBusiness>>(emptyList())
    val adminBusinesses = _adminBusinesses.asStateFlow()
    val products = repository.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sales = repository.sales.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val diningTables = repository.diningTables.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tableOrders = repository.tableOrders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(CounterPosUiState())
    val uiState = _uiState.asStateFlow()
    private var mutationInProgress = false

    init {
        viewModelScope.launch {
            try {
                repository.restoreSession()
                _uiState.update { it.copy(isReady = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isReady = true, startupError = e.message) }
            }
        }
    }

    fun signIn(email: String, password: String) = viewModelScope.launch {
        _uiState.update { it.copy(startupError = null, notice = null) }
        runCatching { repository.signIn(email, password) }
            .onFailure { error -> _uiState.update { it.copy(startupError = error.message ?: "Sign in failed") } }
    }

    fun signUp(email: String, password: String) = viewModelScope.launch {
        _uiState.update { it.copy(startupError = null, notice = null) }
        runCatching { repository.signUp(email, password) }
            .onSuccess { snapshot ->
                snapshot.message?.let { message -> _uiState.update { it.copy(notice = message) } }
            }
            .onFailure { error -> _uiState.update { it.copy(startupError = error.message ?: "Could not create account") } }
    }

    fun createBusiness(name: String) = viewModelScope.launch {
        runCatching { repository.createBusiness(name) }
            .onFailure { error -> _uiState.update { it.copy(startupError = error.message ?: "Could not create business") } }
    }

    fun selectBusiness(shopId: String) = viewModelScope.launch {
        runCatching { repository.selectBusiness(shopId) }
            .onFailure { error -> _uiState.update { it.copy(startupError = error.message ?: "Could not open business") } }
    }

    fun signOut() = viewModelScope.launch { repository.signOut() }

    fun clearStartupError() = _uiState.update { it.copy(startupError = null) }

    fun loadAdminBusinesses() = viewModelScope.launch {
        runCatching { repository.adminBusinesses() }
            .onSuccess { _adminBusinesses.value = it }
            .onFailure { error -> _uiState.update { it.copy(startupError = error.message ?: "Could not load businesses") } }
    }

    fun setBusinessStatus(shopId: String, status: String, reason: String?) = viewModelScope.launch {
        runCatching { repository.adminSetBusinessStatus(shopId, status, reason) }
            .onSuccess { _adminBusinesses.value = it }
            .onFailure { error -> _uiState.update { it.copy(startupError = error.message ?: "Could not change business status") } }
    }

    fun createBusinessForOwner(name: String, ownerEmail: String) = viewModelScope.launch {
        runCatching { repository.adminCreateBusiness(name, ownerEmail) }
            .onSuccess { _adminBusinesses.value = it }
            .onFailure { error -> _uiState.update { it.copy(startupError = error.message ?: "Could not create business") } }
    }

    fun selectAdminBusiness(shopId: String) = _uiState.update {
        it.copy(adminSelectedBusinessId = shopId, notice = "Business selected. Scan a staff QR code to attach a user.")
    }

    fun handleAdminQrCode(payload: String) {
        val parts = payload.trim().split('|', limit = 4)
        if (parts.size < 3 || !parts[0].equals("COUNTERPOS", ignoreCase = true)) {
            _uiState.update { it.copy(isScannerOpen = false, startupError = "Unrecognized CounterPOS QR. Use a business or user enrollment QR.") }
            return
        }
        when (parts[1].uppercase()) {
            "BUSINESS" -> {
                val name = parts.getOrNull(2).orEmpty().trim()
                val ownerEmail = parts.getOrNull(3).orEmpty().trim()
                if (name.isBlank() || !ownerEmail.contains("@")) {
                    _uiState.update { it.copy(isScannerOpen = false, startupError = "Business QR must contain a name and registered owner email.") }
                } else {
                    _uiState.update { it.copy(isScannerOpen = false, startupError = null, notice = "Registering $name…") }
                    createBusinessForOwner(name, ownerEmail)
                }
            }
            "USER" -> {
                val shopId = _uiState.value.adminSelectedBusinessId
                val email = parts.getOrNull(2).orEmpty().trim()
                val role = parts.getOrNull(3).orEmpty().trim().ifBlank { "cashier" }
                if (shopId.isNullOrBlank()) {
                    _uiState.update { it.copy(isScannerOpen = false, startupError = "Open a business profile before scanning a staff QR.") }
                } else {
                    viewModelScope.launch {
                        runCatching { repository.addMemberByEmail(shopId, email, role) }
                            .onSuccess {
                                _adminBusinesses.value = it
                                _uiState.update { state -> state.copy(isScannerOpen = false, notice = "$email attached as $role") }
                            }
                            .onFailure { error -> _uiState.update { state -> state.copy(isScannerOpen = false, startupError = error.message ?: "Could not attach user") } }
                    }
                }
            }
            else -> _uiState.update { it.copy(isScannerOpen = false, startupError = "Unsupported CounterPOS QR type.") }
        }
    }

    fun selectTab(tab: PosTab) = _uiState.update {
        it.copy(tab = tab, actionError = null, isSearchActive = false, searchQuery = "")
    }

    fun openTable(table: DiningTableEntity) = _uiState.update {
        it.copy(selectedTableId = table.id, actionError = null)
    }

    fun backToTables() = _uiState.update { it.copy(selectedTableId = null, actionError = null) }

    fun createDiningTable(name: String) {
        if (_uiState.value.isCreatingTable) return
        _uiState.update { it.copy(isCreatingTable = true, actionError = null) }
        viewModelScope.launch {
            try {
                val table = repository.createDiningTable(name)
                _uiState.update {
                    it.copy(
                        selectedTableId = table.id,
                        isCreatingTable = false,
                        notice = "${table.name} is ready for orders"
                    )
                }
            } catch (error: Exception) {
                _uiState.update { it.copy(isCreatingTable = false, actionError = error.message ?: "Could not create table.") }
            }
        }
    }

    fun openTableSale(tableId: String, product: ProductEntity) {
        if (!product.inMyStock || product.stockQuantity <= 0) {
            _uiState.update { it.copy(actionError = "${product.name} is out of stock.") }
            return
        }
        val units = parseSaleUnits(product.salesUnitsJson, product.sellingPriceKsh)
        val defaultUnit = units.firstOrNull() ?: SaleUnit("Bottle", product.sellingPriceKsh)
        _uiState.update {
            it.copy(
                selectedTableId = tableId,
                selectedSaleCode = product.catalogCode,
                selectedUnitName = defaultUnit.name,
                selectedUnitPrice = defaultUnit.priceKsh,
                saleQuantity = 1,
                actionError = null
            )
        }
    }

    fun setStockMode(mode: StockMode) = _uiState.update {
        it.copy(stockMode = mode, libraryCategory = "All", actionError = null)
    }

    fun setSearchActive(active: Boolean) = _uiState.update {
        it.copy(isSearchActive = active, searchQuery = if (!active) "" else it.searchQuery)
    }

    fun setSearchQuery(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun clearSearch() = _uiState.update { it.copy(searchQuery = "", isSearchActive = false) }

    fun setLibraryCategory(category: String) = _uiState.update { it.copy(libraryCategory = category) }

    fun clearNotice() = _uiState.update { it.copy(notice = null) }

    // Scanner
    fun openScanner(mode: ScannerMode) = _uiState.update {
        it.copy(isScannerOpen = true, scannerMode = mode, actionError = null)
    }

    fun closeScanner() = _uiState.update {
        it.copy(isScannerOpen = false, unrecognizedBarcode = null)
    }

    fun startBarcodeAssignment(product: ProductEntity) = _uiState.update {
        it.copy(isScannerOpen = true, scannerMode = ScannerMode.ASSIGN_BARCODE, barcodeAssignmentCode = product.catalogCode, actionError = null)
    }

    fun handleAssignedBarcode(barcode: String) {
        val code = _uiState.value.barcodeAssignmentCode ?: return
        viewModelScope.launch {
            val success = repository.assignBarcode(code, barcode)
            _uiState.update {
                it.copy(isScannerOpen = false, barcodeAssignmentCode = null, notice = if (success) "Barcode linked to product" else "Barcode is already assigned or could not be saved")
            }
        }
    }

    fun handleScannedSale(product: ProductEntity) {
        if (!product.inMyStock) {
            _uiState.update {
                it.copy(
                    isScannerOpen = false,
                    tab = PosTab.STOCK,
                    stockMode = StockMode.LIBRARY,
                    selectedCatalogCode = product.catalogCode,
                    catalogInitialStock = 12,
                    catalogSellingPrice = product.suggestedPriceKsh.toString(),
                    notice = "Scanned ${product.name} is in catalog. Set your shop stock to sell it."
                )
            }
            return
        }
        if (product.stockQuantity < 1) {
            _uiState.update { it.copy(isScannerOpen = false, notice = "⚠️ ${product.name} is OUT OF STOCK!") }
            return
        }
        val units = parseSaleUnits(product.salesUnitsJson, product.sellingPriceKsh)
        val defaultUnit = units.firstOrNull() ?: SaleUnit("Bottle", product.sellingPriceKsh)
        // Every successful scan releases one unit immediately; there is no continuous-mode switch.
        viewModelScope.launch {
            val success = repository.sellWithUnit(
                code = product.catalogCode,
                quantity = 1,
                unitName = defaultUnit.name,
                unitPriceKsh = defaultUnit.priceKsh
            )
            val remaining = (product.stockQuantity - 1).coerceAtLeast(0)
            _uiState.update {
                it.copy(
                    isScannerOpen = false,
                    notice = if (success) {
                        "✓ Sold 1x ${product.name} (${defaultUnit.name}) · KSh ${defaultUnit.priceKsh} (Rem: $remaining)"
                    } else {
                        "⚠️ Could not sell ${product.name}: check stock."
                    }
                )
            }
        }
    }

    fun handleScannedTableProduct(product: ProductEntity) {
        val tableId = _uiState.value.selectedTableId
        if (tableId.isNullOrBlank()) {
            _uiState.update { it.copy(isScannerOpen = false, notice = "Open a table before scanning a product.") }
            return
        }
        if (!product.inMyStock || product.stockQuantity < 1) {
            _uiState.update {
                it.copy(
                    isScannerOpen = false,
                    notice = "${product.name} is out of stock and cannot be added to this bill."
                )
            }
            return
        }
        _uiState.update { it.copy(isScannerOpen = false, actionError = null) }
        openTableSale(tableId, product)
    }

    fun handleScannedStock(product: ProductEntity) {
        if (product.inMyStock) {
            _uiState.update {
                it.copy(
                    isScannerOpen = false,
                    tab = PosTab.STOCK,
                    stockMode = StockMode.MY_STOCK,
                    addStockProductCode = product.catalogCode,
                    notice = "Scanned ${product.name}. Enter bottles to add."
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isScannerOpen = false,
                    tab = PosTab.STOCK,
                    stockMode = StockMode.LIBRARY,
                    selectedCatalogCode = product.catalogCode,
                    catalogInitialStock = 12,
                    catalogSellingPrice = product.suggestedPriceKsh.toString(),
                    notice = "Found ${product.name}. Set price & bottles to add to stock."
                )
            }
        }
    }

    fun handleUnrecognizedBarcode(barcode: String) {
        _uiState.update {
            it.copy(
                isScannerOpen = false,
                isAddingNewProduct = true,
                prefilledBarcode = barcode,
                notice = "New barcode $barcode scanned. Enter product details."
            )
        }
    }

    // Selling & Unit of Sales
    fun openSale(product: ProductEntity) {
        val units = parseSaleUnits(product.salesUnitsJson, product.sellingPriceKsh)
        val defaultUnit = units.firstOrNull() ?: SaleUnit("Bottle", product.sellingPriceKsh)
        _uiState.update {
            it.copy(
                selectedSaleCode = product.catalogCode,
                selectedUnitName = defaultUnit.name,
                selectedUnitPrice = defaultUnit.priceKsh,
                saleQuantity = 1,
                actionError = null
            )
        }
    }

    fun selectSaleUnit(unit: SaleUnit) = _uiState.update {
        it.copy(
            selectedUnitName = unit.name,
            selectedUnitPrice = unit.priceKsh
        )
    }

    fun adjustSaleQuantity(delta: Int) = _uiState.update { state ->
        val product = products.value.firstOrNull { it.catalogCode == state.selectedSaleCode }
        val max = product?.stockQuantity ?: 99
        val newQty = (state.saleQuantity + delta).coerceIn(1, max.coerceAtLeast(1))
        state.copy(saleQuantity = newQty)
    }

    fun dismissSale() = _uiState.update { it.copy(selectedSaleCode = null, actionError = null) }

    fun confirmSale() {
        val state = _uiState.value
        val code = state.selectedSaleCode ?: return
        if (mutationInProgress) return
        mutationInProgress = true

        viewModelScope.launch {
            try {
                val tableId = state.selectedTableId.takeIf { state.tab == PosTab.TABLES }
                val success = if (tableId != null) {
                    repository.sellToTable(
                        tableId = tableId,
                        code = code,
                        quantity = state.saleQuantity,
                        unitName = state.selectedUnitName,
                        unitPriceKsh = state.selectedUnitPrice
                    )
                } else {
                    repository.sellWithUnit(
                        code = code,
                        quantity = state.saleQuantity,
                        unitName = state.selectedUnitName,
                        unitPriceKsh = state.selectedUnitPrice
                    )
                }
                if (success) {
                    val product = products.value.firstOrNull { it.catalogCode == code }
                    val total = state.selectedUnitPrice * state.saleQuantity
                    _uiState.update {
                        it.copy(
                            selectedSaleCode = null,
                            saleQuantity = 1,
                            actionError = null,
                            notice = if (tableId != null) {
                                "✓ Added ${state.saleQuantity} × ${product?.name ?: ""} to table order · KSh $total"
                            } else {
                                "✓ Sold ${state.saleQuantity}x ${product?.name ?: ""} (${state.selectedUnitName}) for KSh $total"
                            }
                        )
                    }
                } else {
                    _uiState.update { it.copy(actionError = "Could not record sale. Check stock.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionError = e.message ?: "Sale failed") }
            } finally {
                mutationInProgress = false
            }
        }
    }

    // Add bottles to stock
    fun openAddStock(product: ProductEntity) = _uiState.update {
        it.copy(addStockProductCode = product.catalogCode, actionError = null)
    }

    fun dismissAddStock() = _uiState.update { it.copy(addStockProductCode = null, actionError = null) }

    fun confirmAddStock(product: ProductEntity, additionalBottles: Int, unitCostKsh: Int) {
        if (additionalBottles < 1) {
            _uiState.update { it.copy(actionError = "Please enter at least 1 bottle.") }
            return
        }
        if (unitCostKsh < 0) {
            _uiState.update { it.copy(actionError = "Enter a valid unit cost.") }
            return
        }
        viewModelScope.launch {
            val success = repository.restock(product.catalogCode, additionalBottles, unitCostKsh)
            if (success) {
                _uiState.update {
                    it.copy(
                        addStockProductCode = null,
                        notice = "✓ Added $additionalBottles bottles at KSh $unitCostKsh to ${product.name}"
                    )
                }
            } else {
                _uiState.update { it.copy(actionError = "Failed to update stock.") }
            }
        }
    }

    // Edit Product & Stock
    fun openEditProduct(product: ProductEntity) = _uiState.update {
        it.copy(editProductCode = product.catalogCode, actionError = null)
    }

    fun dismissEditProduct() = _uiState.update { it.copy(editProductCode = null, actionError = null) }

    fun confirmEditProduct(
        product: ProductEntity,
        name: String,
        newStock: Int,
        newPrice: Int,
        units: List<SaleUnit>
    ) {
        if (newStock < 0 || newPrice < 1) {
            _uiState.update { it.copy(actionError = "Enter valid stock count and selling price.") }
            return
        }
        viewModelScope.launch {
            val updated = product.copy(
                name = name.ifBlank { product.name },
                stockQuantity = newStock,
                sellingPriceKsh = newPrice,
                salesUnitsJson = serializeSaleUnits(units)
            )
            val success = repository.updateProduct(updated)
            if (success) {
                _uiState.update {
                    it.copy(
                        editProductCode = null,
                        notice = "✓ Updated ${updated.name}: Stock set to $newStock bottles, price KSh $newPrice"
                    )
                }
            } else {
                _uiState.update { it.copy(actionError = "Failed to save product changes.") }
            }
        }
    }

    // Add New Product with Configurable Units of Sale
    fun openAddNewProduct(prefilledBarcode: String? = null) = _uiState.update {
        it.copy(
            isAddingNewProduct = true,
            prefilledBarcode = prefilledBarcode,
            actionError = null
        )
    }

    fun dismissAddNewProduct() = _uiState.update {
        it.copy(isAddingNewProduct = false, prefilledBarcode = null, actionError = null)
    }

    fun confirmCreateProduct(
        name: String,
        barcode: String,
        category: String,
        basePrice: Int,
        initialStock: Int,
        unitCostKsh: Int,
        units: List<SaleUnit>,
        brand: String = "",
        size: String = ""
    ) {
        if (name.isBlank() || basePrice < 1) {
            _uiState.update { it.copy(actionError = "Enter product name and valid base price.") }
            return
        }
        viewModelScope.launch {
            val code = barcode.trim().ifBlank { "PROD_" + UUID.randomUUID().toString().take(8) }
            val success = repository.registerNewProduct(
                barcode = code,
                name = name.trim(),
                category = category.trim().ifBlank { "Local Liquor" },
                priceKsh = basePrice,
                initialStock = initialStock.coerceAtLeast(0),
                unitCostKsh = unitCostKsh,
                brand = brand,
                size = size,
                salesUnits = units
            )
            if (success) {
                _uiState.update {
                    it.copy(
                        isAddingNewProduct = false,
                        prefilledBarcode = null,
                        actionError = null,
                        notice = "✓ Added new product '$name' with $initialStock bottles and ${units.size} sales units."
                    )
                }
            } else {
                _uiState.update { it.copy(actionError = "Failed to add product.") }
            }
        }
    }

    // Add Catalog Item to Shop
    fun openCatalogItem(product: ProductEntity) = _uiState.update {
        it.copy(
            selectedCatalogCode = product.catalogCode,
            catalogInitialStock = 12,
            catalogSellingPrice = product.suggestedPriceKsh.toString(),
            actionError = null
        )
    }

    fun dismissCatalogItem() = _uiState.update {
        it.copy(selectedCatalogCode = null, actionError = null)
    }

    fun confirmAddCatalogItem(code: String, quantity: Int, priceKsh: Int, unitCostKsh: Int) {
        if (quantity < 1 || priceKsh < 1 || unitCostKsh < 0) {
            _uiState.update { it.copy(actionError = "Enter valid quantity, cost, and selling price.") }
            return
        }
        viewModelScope.launch {
            val success = repository.addFromLibrary(code, quantity, priceKsh, unitCostKsh)
            if (success) {
                val item = products.value.firstOrNull { it.catalogCode == code }
                _uiState.update {
                    it.copy(
                        selectedCatalogCode = null,
                        stockMode = StockMode.MY_STOCK,
                        notice = "✓ Added ${item?.name ?: "Product"} to your shop stock!"
                    )
                }
            } else {
                _uiState.update { it.copy(actionError = "Could not add to stock.") }
            }
        }
    }

    fun undoLastSale() {
        if (mutationInProgress) return
        mutationInProgress = true
        viewModelScope.launch {
            try {
                val success = repository.undoLatestSale()
                _uiState.update {
                    it.copy(notice = if (success) "✓ Last sale undone and stock restored" else "Nothing to undo")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(notice = e.message ?: "Failed to undo") }
            } finally {
                mutationInProgress = false
            }
        }
    }

    fun updatePhoto(product: ProductEntity, uri: String) {
        viewModelScope.launch {
            repository.updatePhoto(product, uri)
            _uiState.update { it.copy(notice = "Photo updated for ${product.name}") }
        }
    }
}

class CounterPosViewModelFactory(private val repository: CounterPosRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(CounterPosViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        return CounterPosViewModel(repository) as T
    }
}
