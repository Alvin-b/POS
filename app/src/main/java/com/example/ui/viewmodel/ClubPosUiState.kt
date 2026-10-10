package com.example.ui.viewmodel

import com.example.data.local.entity.ClubOrderEntity
import com.example.data.local.entity.MenuItemEntity
import com.example.data.local.entity.OrderItemEntity
import com.example.data.local.entity.ShiftRecordEntity

enum class PosTab {
    SPEED_RAIL,
    OPEN_TABS,
    INVENTORY,
    SHIFT_DRAWER
}

data class CartItem(
    val menuItem: MenuItemEntity,
    val quantity: Int = 1,
    val modifiers: String = "",
    val extraCharge: Double = 0.0
) {
    val unitPrice: Double get() = menuItem.price + extraCharge
    val totalPrice: Double get() = unitPrice * quantity
}

data class PaymentDetails(
    val subtotal: Double,
    val tax: Double,
    val tipAmount: Double,
    val total: Double,
    val paymentMethod: String,
    val changeGiven: Double = 0.0,
    val tenderAmount: Double = 0.0,
    val cardLast4: String? = null,
    val orderId: Long,
    val tabName: String,
    val items: List<OrderItemEntity>
)

data class ClubPosUiState(
    val currentTab: PosTab = PosTab.SPEED_RAIL,
    val selectedCategory: String = "All",
    val searchQuery: String = "",
    val activeOrderId: Long? = null,
    val activeOrder: ClubOrderEntity? = null,
    val activeOrderItems: List<OrderItemEntity> = emptyList(),
    // Fast uncommitted draft cart when not attached yet to a saved tab or quick bar sale
    val draftCart: List<CartItem> = emptyList(),
    // Modifiers dialog state
    val pendingModifierItem: MenuItemEntity? = null,
    // Payment dialog state
    val showPaymentModal: Boolean = false,
    val paymentTipPercent: Int = 20, // default 20% club tip
    val customTipAmount: Double? = null,
    val paymentSuccessReceipt: PaymentDetails? = null,
    // New tab modal state
    val showNewTabModal: Boolean = false,
    // Shift & Z-report state
    val showZReportModal: Boolean = false,
    val lastClosedShift: ShiftRecordEntity? = null,
    // VIP Celebration banner state
    val showVipCelebrationBanner: Boolean = false,
    val celebrationMessage: String = ""
)
