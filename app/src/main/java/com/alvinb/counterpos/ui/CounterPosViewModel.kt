package com.alvinb.counterpos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alvinb.counterpos.data.CounterPosRepository
import com.alvinb.counterpos.data.ShopAccess
import com.alvinb.counterpos.data.UserProfile
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.remote.SupabaseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


enum class PosTab { SCAN, SALES, STOCK }
enum class StockMode { MY_STOCK, LIBRARY }

data class CounterPosUiState(
    val tab: PosTab = PosTab.SCAN,
    val stockMode: StockMode = StockMode.MY_STOCK,
    val scanQuery: String = "",
    val stockQuery: String = "",
    val libraryQuery: String = "",
    val libraryCategory: String = "All",
    val selectedSaleCode: String? = null,
    val saleQuantity: Int = 1,
    val selectedAddCode: String? = null,
    val addQuantity: Int = 0,
    val addPriceText: String = "",
    val showReceiveStock: Boolean = false,
    val isScannerOpen: Boolean = false,
    val scannerMode: com.alvinb.counterpos.ui.scanner.ScannerMode = com.alvinb.counterpos.ui.scanner.ScannerMode.SELL,
    val unrecognizedBarcode: String? = null,
    val actionError: String? = null,
    val notice: String? = null,
    val isReady: Boolean = false,
    val startupError: String? = null,
    val isCheckingSession: Boolean = true,
    val isAuthenticated: Boolean = false,
    val authEmail: String = "",
    val authPassword: String = "",
    val isCreatingAccount: Boolean = false,
    val authBusy: Boolean = false,
    val authError: String? = null,
    val authNotice: String? = null,
    val userEmail: String = "",
    val userId: String = "",
    val shops: List<ShopAccess> = emptyList(),
    val membershipLoadFailed: Boolean = false,
    val selectedShopId: String? = null,
    val shopNameInput: String = "",
    val shopBusy: Boolean = false,
    val shopError: String? = null,
    val isLoadingShop: Boolean = false,
    val shopName: String = "",
    val shopRole: String = "cashier"
)

class CounterPosViewModel(private val repository: CounterPosRepository) : ViewModel() {
    val products = repository.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sales = repository.sales.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val todaySummary = repository.todaySummary.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0 to 0)

    private val _uiState = MutableStateFlow(CounterPosUiState())
    val uiState = _uiState.asStateFlow()
    private var mutationInProgress = false

    init {
        viewModelScope.launch {
            try {
                val user = repository.restoreUser()
                _uiState.update { it.copy(isCheckingSession = false) }
                if (user != null) onAuthenticated(user)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isCheckingSession = false, authError = readable(error))
                }
            }
        }
    }

    fun setAuthEmail(value: String) = _uiState.update { it.copy(authEmail = value, authError = null, authNotice = null) }
    fun setAuthPassword(value: String) = _uiState.update { it.copy(authPassword = value, authError = null, authNotice = null) }
    fun toggleAuthMode() = _uiState.update {
        it.copy(isCreatingAccount = !it.isCreatingAccount, authError = null, authNotice = null)
    }
    fun setShopName(value: String) = _uiState.update { it.copy(shopNameInput = value, shopError = null) }

    fun submitAuth() {
        val state = _uiState.value
        val email = state.authEmail.trim()
        val password = state.authPassword
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(authError = "Enter your email and password.") }
            return
        }
        _uiState.update { it.copy(authBusy = true, authError = null, authNotice = null) }
        viewModelScope.launch {
            try {
                if (state.isCreatingAccount) {
                    val user = repository.signUp(email, password)
                    if (user == null) {
                        _uiState.update {
                            it.copy(
                                authBusy = false,
                                authPassword = "",
                                isCreatingAccount = false,
                                authNotice = "Account created. Check your email to confirm it, then sign in."
                            )
                        }
                    } else {
                        _uiState.update { it.copy(authBusy = false, authPassword = "") }
                        onAuthenticated(user)
                    }
                } else {
                    val user = repository.signIn(email, password)
                    _uiState.update { it.copy(authBusy = false, authPassword = "") }
                    onAuthenticated(user)
                }
            } catch (error: Exception) {
                _uiState.update { it.copy(authBusy = false, authError = readable(error)) }
            }
        }
    }

    private suspend fun onAuthenticated(user: UserProfile) {
        _uiState.update {
            it.copy(
                isAuthenticated = true,
                isCheckingSession = false,
                userEmail = user.email,
                userId = user.id,
                authError = null,
                authNotice = null,
                authPassword = "",
                isLoadingShop = true,
                membershipLoadFailed = false
            )
        }
        try {
            val memberships = repository.shopMemberships(user.id)
            _uiState.update { it.copy(shops = memberships, isLoadingShop = false, shopError = null, membershipLoadFailed = false) }
            if (memberships.size == 1) selectShop(memberships.single())
            else if (memberships.isEmpty()) {
                repository.clearShopCache()
                _uiState.update { it.copy(selectedShopId = null, shopName = "") }
            } else {
                repository.clearShopCache()
                _uiState.update { it.copy(selectedShopId = null, shopName = "") }
            }
        } catch (error: Exception) {
            _uiState.update { it.copy(isLoadingShop = false, shopError = readable(error), membershipLoadFailed = true) }
        }
    }

    fun retryShopLoad() {
        val state = _uiState.value
        if (state.userId.isBlank()) return
        viewModelScope.launch { onAuthenticated(UserProfile(state.userId, state.userEmail)) }
    }

    fun createShop() {
        val name = _uiState.value.shopNameInput.trim()
        if (_uiState.value.membershipLoadFailed) return
        if (name.isBlank()) {
            _uiState.update { it.copy(shopError = "Enter a name for your shop.") }
            return
        }
        _uiState.update { it.copy(shopBusy = true, shopError = null) }
        viewModelScope.launch {
            try {
                val shop = repository.createShop(name)
                _uiState.update { it.copy(shopBusy = false, shops = listOf(shop), shopNameInput = "") }
                selectShop(shop)
            } catch (error: Exception) {
                _uiState.update { it.copy(shopBusy = false, shopError = readable(error)) }
            }
        }
    }

    fun chooseShop(shop: ShopAccess) = viewModelScope.launch { selectShop(shop) }

    private suspend fun selectShop(shop: ShopAccess) {
        _uiState.update {
            it.copy(isLoadingShop = true, selectedShopId = null, shopName = shop.name, shopRole = shop.role, shopError = null)
        }
        try {
            repository.selectShop(shop)
            _uiState.update {
                it.copy(
                    isLoadingShop = false,
                    selectedShopId = shop.id,
                    shopName = shop.name,
                    shopRole = shop.role,
                    isReady = true,
                    startupError = null,
                    tab = PosTab.SCAN
                )
            }
        } catch (error: Exception) {
            _uiState.update { it.copy(isLoadingShop = false, shopError = readable(error), startupError = readable(error)) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            _uiState.value = CounterPosUiState(isCheckingSession = false)
        }
    }

    fun selectTab(tab: PosTab) = _uiState.update { it.copy(tab = tab, actionError = null) }
    fun setStockMode(mode: StockMode) = _uiState.update {
        it.copy(stockMode = mode, libraryCategory = "All", actionError = null)
    }
    fun setScanQuery(value: String) = _uiState.update { it.copy(scanQuery = value) }
    fun setStockQuery(value: String) = _uiState.update { it.copy(stockQuery = value) }
    fun setLibraryQuery(value: String) = _uiState.update { it.copy(libraryQuery = value) }
    fun setLibraryCategory(value: String) = _uiState.update { it.copy(libraryCategory = value) }
    fun clearNotice() = _uiState.update { it.copy(notice = null) }
    fun clearStartupError() = _uiState.update { it.copy(startupError = null) }

    fun openScanner(mode: com.alvinb.counterpos.ui.scanner.ScannerMode) = _uiState.update {
        it.copy(isScannerOpen = true, scannerMode = mode, actionError = null)
    }

    fun closeScanner() = _uiState.update {
        it.copy(isScannerOpen = false, unrecognizedBarcode = null)
    }

    fun handleScannedSale(product: ProductEntity, isContinuous: Boolean) {
        if (!product.inMyStock) {
            _uiState.update {
                it.copy(
                    notice = "Scanned ${product.name} is in catalog but not added to your shop yet. Add it from Stock → Library.",
                    selectedAddCode = product.catalogCode,
                    addQuantity = 1,
                    addPriceText = product.suggestedPriceKsh.toString()
                )
            }
            return
        }

        if (product.stockQuantity < 1) {
            _uiState.update { it.copy(notice = "⚠️ ${product.name} is OUT OF STOCK!") }
            return
        }

        if (isContinuous) {
            // Supermarket conveyor-belt / cash register mode: sell 1 unit instantly
            viewModelScope.launch {
                val success = repository.sell(product.catalogCode, 1)
                if (success) {
                    val remaining = (product.stockQuantity - 1).coerceAtLeast(0)
                    _uiState.update {
                        it.copy(
                            notice = "✓ Scanned 1x ${product.name} · KSh ${product.sellingPriceKsh} (Rem: $remaining)"
                        )
                    }
                } else {
                    _uiState.update { it.copy(notice = "⚠️ Could not sell ${product.name}: check stock.") }
                }
            }
        } else {
            // Open sale sheet
            openSale(product)
        }
    }

    fun handleScannedStock(product: ProductEntity) {
        if (product.inMyStock) {
            // Already in shop stock: prompt to restock
            _uiState.update {
                it.copy(
                    isScannerOpen = false,
                    tab = PosTab.STOCK,
                    stockMode = StockMode.MY_STOCK,
                    stockQuery = product.name,
                    notice = "Scanned: ${product.name} (Current: ${product.stockQuantity} units)"
                )
            }
        } else {
            // In library, not yet in shop stock
            _uiState.update {
                it.copy(
                    isScannerOpen = false,
                    tab = PosTab.STOCK,
                    stockMode = StockMode.LIBRARY,
                    selectedAddCode = product.catalogCode,
                    addQuantity = 6,
                    addPriceText = product.suggestedPriceKsh.toString(),
                    notice = "Found ${product.name} in beverage catalog! Set your initial stock."
                )
            }
        }
    }

    fun handleUnrecognizedBarcode(barcode: String) {
        _uiState.update {
            it.copy(
                unrecognizedBarcode = barcode,
                notice = "New barcode detected: $barcode"
            )
        }
    }

    fun dismissUnrecognizedBarcode() = _uiState.update {
        it.copy(unrecognizedBarcode = null)
    }

    fun registerNewBarcodeProduct(
        barcode: String,
        name: String,
        category: String,
        priceKsh: Int,
        initialStock: Int,
        brand: String = "",
        size: String = ""
    ) {
        if (name.isBlank() || priceKsh < 1) {
            _uiState.update { it.copy(actionError = "Enter a valid product name and selling price.") }
            return
        }
        viewModelScope.launch {
            val success = repository.registerNewProduct(
                barcode = barcode,
                name = name,
                category = category,
                priceKsh = priceKsh,
                initialStock = initialStock,
                brand = brand,
                size = size
            )
            _uiState.update {
                if (success) {
                    it.copy(
                        unrecognizedBarcode = null,
                        notice = "Registered $name ($barcode) with $initialStock in stock"
                    )
                } else {
                    it.copy(actionError = "Could not register product.")
                }
            }
        }
    }

    fun openSale(product: ProductEntity) = _uiState.update {
        it.copy(selectedSaleCode = product.catalogCode, saleQuantity = 1, actionError = null)
    }

    fun adjustSaleQuantity(delta: Int) = _uiState.update { state ->
        val product = products.value.firstOrNull { it.catalogCode == state.selectedSaleCode }
        val max = product?.stockQuantity ?: 1
        state.copy(saleQuantity = (state.saleQuantity + delta).coerceIn(1, max.coerceAtLeast(1)))
    }

    fun dismissSale() = _uiState.update { it.copy(selectedSaleCode = null, actionError = null) }

    fun confirmSale() {
        val state = _uiState.value
        val code = state.selectedSaleCode ?: return
        if (mutationInProgress) return
        mutationInProgress = true
        viewModelScope.launch {
            try {
                repository.sell(code, state.saleQuantity)
                _uiState.update { it.copy(selectedSaleCode = null, saleQuantity = 1, actionError = null, notice = "Sale recorded") }
            } catch (error: Exception) {
                _uiState.update { it.copy(actionError = readable(error)) }
            } finally {
                mutationInProgress = false
            }
        }
    }

    fun openLibraryProduct(product: ProductEntity) = _uiState.update {
        it.copy(
            selectedAddCode = product.catalogCode,
            addQuantity = 0,
            addPriceText = product.suggestedPriceKsh.takeIf { price -> price > 0 }?.toString().orEmpty(),
            actionError = null
        )
    }

    fun adjustAddQuantity(delta: Int) = _uiState.update {
        it.copy(addQuantity = (it.addQuantity + delta).coerceAtLeast(1), actionError = null)
    }

    fun addQuantity(amount: Int) = _uiState.update {
        it.copy(addQuantity = (it.addQuantity + amount).coerceAtLeast(1), actionError = null)
    }

    fun setAddQuantity(value: Int) = _uiState.update {
        it.copy(addQuantity = value.coerceAtLeast(0), actionError = null)
    }

    fun setAddPrice(value: String) = _uiState.update { it.copy(addPriceText = value, actionError = null) }
    fun dismissAdd() = _uiState.update { it.copy(selectedAddCode = null, actionError = null) }

    fun confirmAddToStock() {
        val state = _uiState.value
        val code = state.selectedAddCode ?: return
        if (state.addQuantity < 1) {
            _uiState.update { it.copy(actionError = "Enter the quantity you have in stock.") }
            return
        }
        val price = state.addPriceText.toIntOrNull() ?: 0
        if (price < 1) {
            _uiState.update { it.copy(actionError = "Enter your shop selling price in KSh.") }
            return
        }
        if (mutationInProgress) return
        mutationInProgress = true
        val name = products.value.firstOrNull { it.catalogCode == code }?.name ?: "Product"
        viewModelScope.launch {
            try {
                repository.addFromLibrary(code, state.addQuantity, price)
                _uiState.update {
                    it.copy(
                        selectedAddCode = null,
                        stockMode = StockMode.MY_STOCK,
                        addQuantity = 0,
                        actionError = null,
                        notice = "$name added to My stock"
                    )
                }
            } catch (error: Exception) {
                _uiState.update { it.copy(actionError = readable(error)) }
            } finally {
                mutationInProgress = false
            }
        }
    }

    fun requestReceiveStock() = _uiState.update { it.copy(showReceiveStock = true, actionError = null) }
    fun dismissReceiveStock() = _uiState.update { it.copy(showReceiveStock = false, actionError = null) }

    fun receiveStock(code: String, quantity: Int) {
        if (quantity < 1) return
        if (mutationInProgress) return
        mutationInProgress = true
        viewModelScope.launch {
            try {
                repository.restock(code, quantity)
                val name = products.value.firstOrNull { it.catalogCode == code }?.name ?: "Product"
                _uiState.update { it.copy(showReceiveStock = false, actionError = null, notice = "Received $quantity × $name") }
            } catch (error: Exception) {
                _uiState.update { it.copy(actionError = readable(error)) }
            } finally {
                mutationInProgress = false
            }
        }
    }

    fun quickRestock(product: ProductEntity, quantity: Int) {
        if (mutationInProgress) return
        mutationInProgress = true
        viewModelScope.launch {
            try {
                repository.restock(product.catalogCode, quantity)
                _uiState.update { it.copy(notice = "Received $quantity × ${product.name}") }
            } catch (error: Exception) {
                _uiState.update { it.copy(actionError = readable(error)) }
            } finally {
                mutationInProgress = false
            }
        }
    }

    fun undoLastSale() {
        if (mutationInProgress) return
        mutationInProgress = true
        viewModelScope.launch {
            try {
                repository.undoLatestSale()
                _uiState.update { it.copy(notice = "Last sale undone and stock restored") }
            } catch (error: Exception) {
                val message = if (error.message?.contains("nothing_to_undo", ignoreCase = true) == true) {
                    "There is no sale to undo"
                } else readable(error)
                _uiState.update { it.copy(notice = message) }
            } finally {
                mutationInProgress = false
            }
        }
    }

    fun updatePhoto(product: ProductEntity, uri: String) {
        if (mutationInProgress) return
        mutationInProgress = true
        viewModelScope.launch {
            try {
                repository.updatePhoto(product.catalogCode, uri)
                _uiState.update { it.copy(notice = "Product photo updated for this shop") }
            } catch (error: Exception) {
                _uiState.update { it.copy(actionError = readable(error)) }
            } finally {
                mutationInProgress = false
            }
        }
    }

    private fun readable(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("out_of_stock", ignoreCase = true) -> "Out of stock. Check the shelf and try again."
            raw.contains("invalid_quantity", ignoreCase = true) -> "Enter a valid quantity."
            raw.contains("nothing_to_undo", ignoreCase = true) -> "There is no sale to undo."
            raw.contains("invalid_credentials", ignoreCase = true) || raw.contains("Invalid login credentials", ignoreCase = true) -> "Email or password is incorrect."
            raw.contains("Email not confirmed", ignoreCase = true) -> "Confirm your email before signing in."
            raw.isBlank() -> "Something went wrong. Please try again."
            else -> raw
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
