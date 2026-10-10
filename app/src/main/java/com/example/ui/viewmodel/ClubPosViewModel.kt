package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ClubOrderEntity
import com.example.data.local.entity.MenuItemEntity
import com.example.data.local.entity.OrderItemEntity
import com.example.data.local.entity.ShiftRecordEntity
import com.example.data.repository.ClubPosRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ClubPosViewModel(
    private val repository: ClubPosRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClubPosUiState())
    val uiState: StateFlow<ClubPosUiState> = _uiState.asStateFlow()

    val allMenuItems = repository.allMenuItems.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val openOrders = repository.openOrders.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val recentPaidOrders = repository.recentPaidOrders.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val activeShift = repository.activeShift.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val recentShifts = repository.recentShifts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    init {
        viewModelScope.launch {
            repository.initializeDefaultDataIfEmpty()
        }
    }

    fun selectTab(tab: PosTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openModifierDialog(menuItem: MenuItemEntity) {
        _uiState.update { it.copy(pendingModifierItem = menuItem) }
    }

    fun dismissModifierDialog() {
        _uiState.update { it.copy(pendingModifierItem = null) }
    }

    fun addDraftItem(
        menuItem: MenuItemEntity,
        modifiers: String = "",
        extraCharge: Double = 0.0,
        quantity: Int = 1
    ) {
        val activeOrderId = _uiState.value.activeOrderId
        if (activeOrderId != null) {
            // Add directly to existing active tab in database
            viewModelScope.launch {
                repository.addItemToOrder(
                    orderId = activeOrderId,
                    menuItem = menuItem,
                    modifiers = modifiers,
                    extraModifierCharge = extraCharge,
                    quantity = quantity
                )
                refreshActiveOrder(activeOrderId)
                if (menuItem.category == "Bottle Service" || menuItem.isVipExclusive) {
                    triggerVipCelebration("VIP BOTTLE ALERT: ${menuItem.name} ordered for ${_uiState.value.activeOrder?.tabName ?: "Table"}! Sparklers ready!")
                }
            }
        } else {
            // Add to draft cart
            _uiState.update { state ->
                val existingIndex = state.draftCart.indexOfFirst {
                    it.menuItem.id == menuItem.id && it.modifiers == modifiers && it.extraCharge == extraCharge
                }
                val updatedCart = if (existingIndex >= 0) {
                    state.draftCart.toMutableList().also { list ->
                        val existing = list[existingIndex]
                        list[existingIndex] = existing.copy(quantity = existing.quantity + quantity)
                    }
                } else {
                    state.draftCart + CartItem(
                        menuItem = menuItem,
                        quantity = quantity,
                        modifiers = modifiers,
                        extraCharge = extraCharge
                    )
                }
                state.copy(draftCart = updatedCart, pendingModifierItem = null)
            }
            if (menuItem.category == "Bottle Service" || menuItem.isVipExclusive) {
                triggerVipCelebration("VIP BOTTLE ALERT: ${menuItem.name} ordered! Prepare sparkler service!")
            }
        }
        _uiState.update { it.copy(pendingModifierItem = null) }
    }

    fun incrementDraftItem(index: Int) {
        _uiState.update { state ->
            val updated = state.draftCart.toMutableList()
            if (index in updated.indices) {
                val item = updated[index]
                updated[index] = item.copy(quantity = item.quantity + 1)
            }
            state.copy(draftCart = updated)
        }
    }

    fun decrementDraftItem(index: Int) {
        _uiState.update { state ->
            val updated = state.draftCart.toMutableList()
            if (index in updated.indices) {
                val item = updated[index]
                if (item.quantity > 1) {
                    updated[index] = item.copy(quantity = item.quantity - 1)
                } else {
                    updated.removeAt(index)
                }
            }
            state.copy(draftCart = updated)
        }
    }

    fun clearDraftCart() {
        _uiState.update { it.copy(draftCart = emptyList()) }
    }

    // Active Tab Management
    fun selectActiveOrder(order: ClubOrderEntity) {
        _uiState.update {
            it.copy(
                activeOrderId = order.id,
                activeOrder = order,
                currentTab = PosTab.SPEED_RAIL
            )
        }
        observeActiveOrderItems(order.id)
    }

    fun deselectActiveOrder() {
        _uiState.update {
            it.copy(
                activeOrderId = null,
                activeOrder = null,
                activeOrderItems = emptyList()
            )
        }
    }

    private fun observeActiveOrderItems(orderId: Long) {
        viewModelScope.launch {
            repository.getItemsForOrder(orderId).collect { items ->
                _uiState.update { it.copy(activeOrderItems = items) }
            }
        }
    }

    private fun refreshActiveOrder(orderId: Long) {
        viewModelScope.launch {
            repository.getOrderById(orderId).collect { order ->
                _uiState.update { it.copy(activeOrder = order) }
            }
        }
    }

    fun removeOrderItem(orderItemId: Long) {
        val activeOrderId = _uiState.value.activeOrderId ?: return
        viewModelScope.launch {
            repository.removeItemFromOrder(orderItemId, activeOrderId)
            refreshActiveOrder(activeOrderId)
        }
    }

    fun updateOrderItemQuantity(orderItemId: Long, newQuantity: Int) {
        val activeOrderId = _uiState.value.activeOrderId ?: return
        viewModelScope.launch {
            repository.updateItemQuantity(orderItemId, activeOrderId, newQuantity)
            refreshActiveOrder(activeOrderId)
        }
    }

    // New Tab creation
    fun showNewTabDialog() {
        _uiState.update { it.copy(showNewTabModal = true) }
    }

    fun dismissNewTabDialog() {
        _uiState.update { it.copy(showNewTabModal = false) }
    }

    fun createNewTab(
        tabName: String,
        tableNumber: String,
        bartenderName: String,
        isVipTable: Boolean,
        cardLast4: String?,
        notes: String
    ) {
        viewModelScope.launch {
            val orderId = repository.createNewTab(
                tabName = tabName,
                tableNumber = tableNumber,
                bartenderName = bartenderName,
                isVipTable = isVipTable,
                cardLast4 = cardLast4,
                notes = notes
            )
            // If there were draft items, transfer them to this newly opened tab
            val currentDraft = _uiState.value.draftCart
            for (draft in currentDraft) {
                repository.addItemToOrder(
                    orderId = orderId,
                    menuItem = draft.menuItem,
                    modifiers = draft.modifiers,
                    extraModifierCharge = draft.extraCharge,
                    quantity = draft.quantity
                )
            }
            _uiState.update {
                it.copy(
                    draftCart = emptyList(),
                    showNewTabModal = false,
                    activeOrderId = orderId
                )
            }
            observeActiveOrderItems(orderId)
            refreshActiveOrder(orderId)
            selectTab(PosTab.SPEED_RAIL)
        }
    }

    // Payment Flow
    fun openPaymentModal() {
        _uiState.update { it.copy(showPaymentModal = true) }
    }

    fun dismissPaymentModal() {
        _uiState.update { it.copy(showPaymentModal = false) }
    }

    fun setTipPercent(percent: Int) {
        _uiState.update { it.copy(paymentTipPercent = percent, customTipAmount = null) }
    }

    fun setCustomTip(amount: Double) {
        _uiState.update { it.copy(customTipAmount = amount) }
    }

    fun processPayment(
        paymentMethod: String, // "CARD", "CASH", "VIP_COMP"
        tenderAmount: Double = 0.0,
        cardLast4: String? = null
    ) {
        val state = _uiState.value
        val activeOrderId = state.activeOrderId

        viewModelScope.launch {
            val finalOrderId: Long
            val subtotal: Double
            val itemsForReceipt: List<OrderItemEntity>
            val tabName: String

            if (activeOrderId != null) {
                // Paying an existing open tab
                finalOrderId = activeOrderId
                val order = state.activeOrder
                tabName = order?.tabName ?: "Tab #$activeOrderId"
                val items = state.activeOrderItems
                subtotal = items.sumOf { it.unitPrice * it.quantity }
                itemsForReceipt = items
            } else {
                // Paying direct quick bar draft sale
                if (state.draftCart.isEmpty()) return@launch
                val bartender = activeShift.value?.bartenderName ?: "Elena V. (Lead)"
                finalOrderId = repository.createNewTab(
                    tabName = "Quick Bar Sale",
                    tableNumber = "BAR-SPEED",
                    bartenderName = bartender,
                    isVipTable = false,
                    cardLast4 = cardLast4,
                    notes = "Fast Speed Rail Checkout"
                )
                tabName = "Quick Bar Sale"
                for (draft in state.draftCart) {
                    repository.addItemToOrder(
                        orderId = finalOrderId,
                        menuItem = draft.menuItem,
                        modifiers = draft.modifiers,
                        extraModifierCharge = draft.extraCharge,
                        quantity = draft.quantity
                    )
                }
                subtotal = state.draftCart.sumOf { it.totalPrice }
                itemsForReceipt = state.draftCart.map {
                    OrderItemEntity(
                        orderId = finalOrderId,
                        menuItemId = it.menuItem.id,
                        itemName = it.menuItem.name,
                        itemCategory = it.menuItem.category,
                        unitPrice = it.unitPrice,
                        quantity = it.quantity,
                        modifiers = it.modifiers
                    )
                }
            }

            val tax = subtotal * 0.0825
            val tip = if (paymentMethod == "VIP_COMP") {
                0.0
            } else {
                state.customTipAmount ?: (subtotal * state.paymentTipPercent / 100.0)
            }
            val total = if (paymentMethod == "VIP_COMP") 0.0 else subtotal + tax + tip
            val change = if (paymentMethod == "CASH" && tenderAmount > total) tenderAmount - total else 0.0

            repository.processPayment(
                orderId = finalOrderId,
                paymentMethod = paymentMethod,
                tipAmount = tip,
                cardLast4 = cardLast4 ?: "4242"
            )

            val receipt = PaymentDetails(
                subtotal = subtotal,
                tax = tax,
                tipAmount = tip,
                total = total,
                paymentMethod = paymentMethod,
                changeGiven = change,
                tenderAmount = if (paymentMethod == "CASH") tenderAmount else total,
                cardLast4 = cardLast4 ?: "4242",
                orderId = finalOrderId,
                tabName = tabName,
                items = itemsForReceipt
            )

            _uiState.update {
                it.copy(
                    showPaymentModal = false,
                    paymentSuccessReceipt = receipt,
                    activeOrderId = null,
                    activeOrder = null,
                    activeOrderItems = emptyList(),
                    draftCart = emptyList()
                )
            }
        }
    }

    fun dismissReceiptModal() {
        _uiState.update { it.copy(paymentSuccessReceipt = null) }
    }

    fun voidActiveOrder() {
        val activeOrderId = _uiState.value.activeOrderId ?: return
        viewModelScope.launch {
            repository.voidOrder(activeOrderId)
            deselectActiveOrder()
        }
    }

    fun restockItem(itemId: Long, amount: Int) {
        viewModelScope.launch {
            repository.restockItem(itemId, amount)
        }
    }

    // Shift Operations
    fun switchBartender(newName: String, station: String) {
        viewModelScope.launch {
            repository.startNewShift(newName, station)
        }
    }

    fun closeActiveShift() {
        viewModelScope.launch {
            val closed = repository.closeActiveShift()
            _uiState.update {
                it.copy(
                    showZReportModal = true,
                    lastClosedShift = closed
                )
            }
        }
    }

    fun dismissZReportModal() {
        _uiState.update { it.copy(showZReportModal = false) }
    }

    fun triggerVipCelebration(message: String) {
        _uiState.update {
            it.copy(
                showVipCelebrationBanner = true,
                celebrationMessage = message
            )
        }
    }

    fun dismissVipCelebrationBanner() {
        _uiState.update { it.copy(showVipCelebrationBanner = false) }
    }

    class Factory(private val repository: ClubPosRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ClubPosViewModel::class.java)) {
                return ClubPosViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
