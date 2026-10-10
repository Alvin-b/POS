package com.alvinb.counterpos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alvinb.counterpos.data.CounterPosRepository
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
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
    val actionError: String? = null,
    val notice: String? = null,
    val isReady: Boolean = false,
    val startupError: String? = null
)

class CounterPosViewModel(private val repository: CounterPosRepository) : ViewModel() {
    val products = repository.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sales = repository.sales.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(CounterPosUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.seedCatalogIfEmpty() }
                .onSuccess { _uiState.update { it.copy(isReady = true) } }
                .onFailure { error ->
                    _uiState.update { it.copy(isReady = true, startupError = error.message ?: "Could not load the product catalog") }
                }
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

    fun openExactScanMatch() {
        val query = _uiState.value.scanQuery.trim()
        if (query.isBlank()) return
        val matches = products.value.filter { product ->
            product.verifiedBarcode.equals(query, ignoreCase = true) ||
                product.catalogCode.equals(query, ignoreCase = true) ||
                product.name.equals(query, ignoreCase = true)
        }
        if (matches.size == 1) openSale(matches.first())
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
        viewModelScope.launch {
            val success = repository.sell(code, state.saleQuantity)
            _uiState.update {
                if (success) it.copy(
                    selectedSaleCode = null,
                    saleQuantity = 1,
                    actionError = null,
                    notice = "Sale recorded"
                ) else it.copy(actionError = "Not enough stock to complete that sale.")
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
        viewModelScope.launch {
            val success = repository.addFromLibrary(code, state.addQuantity, price)
            val name = products.value.firstOrNull { it.catalogCode == code }?.name ?: "Product"
            _uiState.update {
                if (success) it.copy(
                    selectedAddCode = null,
                    stockMode = StockMode.MY_STOCK,
                    addQuantity = 1,
                    actionError = null,
                    notice = "$name added to My stock"
                ) else it.copy(actionError = "This product is already in My stock.")
            }
        }
    }

    fun requestReceiveStock() = _uiState.update { it.copy(showReceiveStock = true, actionError = null) }
    fun dismissReceiveStock() = _uiState.update { it.copy(showReceiveStock = false, actionError = null) }

    fun receiveStock(code: String, quantity: Int) {
        if (quantity < 1) return
        viewModelScope.launch {
            val success = repository.restock(code, quantity)
            val name = products.value.firstOrNull { it.catalogCode == code }?.name ?: "Product"
            _uiState.update {
                if (success) it.copy(
                    showReceiveStock = false,
                    actionError = null,
                    notice = "Received $quantity × $name"
                ) else it.copy(actionError = "Could not update stock.")
            }
        }
    }

    fun quickRestock(product: ProductEntity, quantity: Int) {
        viewModelScope.launch {
            if (repository.restock(product.catalogCode, quantity)) {
                _uiState.update { it.copy(notice = "Received $quantity × ${product.name}") }
            }
        }
    }

    fun undoLastSale() {
        viewModelScope.launch {
            val success = repository.undoLatestSale()
            _uiState.update {
                it.copy(notice = if (success) "Last sale undone and stock restored" else "There is no sale to undo")
            }
        }
    }

    fun updatePhoto(product: ProductEntity, uri: String) {
        viewModelScope.launch {
            if (repository.updatePhoto(product.catalogCode, uri)) {
                _uiState.update { it.copy(notice = "Product photo updated") }
            }
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
