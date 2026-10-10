package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.AppDatabase
import com.example.data.repository.ClubPosRepository
import com.example.ui.components.ClubBottomNavigation
import com.example.ui.components.ModifierDialog
import com.example.ui.components.NewTabDialog
import com.example.ui.components.PaymentDialog
import com.example.ui.components.ReceiptDialog
import com.example.ui.components.TopBarStationHeader
import com.example.ui.components.VipCelebrationBanner
import com.example.ui.components.ZReportDialog
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.OpenTabsScreen
import com.example.ui.screens.PosSpeedRailScreen
import com.example.ui.screens.ShiftDrawerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NightClubDark
import com.example.ui.viewmodel.ClubPosViewModel
import com.example.ui.viewmodel.PosTab

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ClubPosRepository(database.clubPosDao())

        setContent {
            MyApplicationTheme {
                val viewModel: ClubPosViewModel = viewModel(
                    factory = ClubPosViewModel.Factory(repository)
                )
                PulseClubPosApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun PulseClubPosApp(viewModel: ClubPosViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val menuItems by viewModel.allMenuItems.collectAsStateWithLifecycle()
    val openOrders by viewModel.openOrders.collectAsStateWithLifecycle()
    val recentPaidOrders by viewModel.recentPaidOrders.collectAsStateWithLifecycle()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopBarStationHeader(
                activeShift = activeShift,
                activeOrder = uiState.activeOrder,
                openTabsCount = openOrders.size,
                onNewTabClick = { viewModel.showNewTabDialog() },
                onDeselectActiveOrder = { viewModel.deselectActiveOrder() },
                onSwitchStationClick = { viewModel.selectTab(PosTab.SHIFT_DRAWER) }
            )
        },
        bottomBar = {
            ClubBottomNavigation(
                currentTab = uiState.currentTab,
                openTabsCount = openOrders.size,
                onTabSelected = { viewModel.selectTab(it) }
            )
        },
        containerColor = NightClubDark,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // VIP Bottle Service Alert Banner
            VipCelebrationBanner(
                visible = uiState.showVipCelebrationBanner,
                message = uiState.celebrationMessage,
                onDismiss = { viewModel.dismissVipCelebrationBanner() }
            )

            Box(modifier = Modifier.weight(1f)) {
                when (uiState.currentTab) {
                    PosTab.SPEED_RAIL -> {
                        PosSpeedRailScreen(
                            menuItems = menuItems,
                            selectedCategory = uiState.selectedCategory,
                            searchQuery = uiState.searchQuery,
                            activeOrder = uiState.activeOrder,
                            activeOrderItems = uiState.activeOrderItems,
                            draftCart = uiState.draftCart,
                            onSelectCategory = { viewModel.selectCategory(it) },
                            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                            onQuickAddItem = { item -> viewModel.addDraftItem(item) },
                            onOpenModifiers = { item -> viewModel.openModifierDialog(item) },
                            onIncrementDraft = { index -> viewModel.incrementDraftItem(index) },
                            onDecrementDraft = { index -> viewModel.decrementDraftItem(index) },
                            onClearDraft = { viewModel.clearDraftCart() },
                            onUpdateDbItemQty = { id, qty -> viewModel.updateOrderItemQuantity(id, qty) },
                            onRemoveDbItem = { id -> viewModel.removeOrderItem(id) },
                            onHoldToTab = { viewModel.showNewTabDialog() },
                            onChargePayClick = { viewModel.openPaymentModal() }
                        )
                    }

                    PosTab.OPEN_TABS -> {
                        OpenTabsScreen(
                            openOrders = openOrders,
                            onSelectTabForOrdering = { order -> viewModel.selectActiveOrder(order) },
                            onSettleTabDirect = { order ->
                                viewModel.selectActiveOrder(order)
                                viewModel.openPaymentModal()
                            },
                            onVoidTab = { order ->
                                viewModel.selectActiveOrder(order)
                                viewModel.voidActiveOrder()
                            },
                            onOpenNewTabModal = { viewModel.showNewTabDialog() }
                        )
                    }

                    PosTab.INVENTORY -> {
                        InventoryScreen(
                            menuItems = menuItems,
                            onRestockItem = { id, amount -> viewModel.restockItem(id, amount) }
                        )
                    }

                    PosTab.SHIFT_DRAWER -> {
                        ShiftDrawerScreen(
                            activeShift = activeShift,
                            recentPaidOrders = recentPaidOrders,
                            onSwitchBartender = { name, station -> viewModel.switchBartender(name, station) },
                            onCloseShiftAndRunZ = { viewModel.closeActiveShift() }
                        )
                    }
                }
            }
        }
    }

    // Modifier Dialog
    uiState.pendingModifierItem?.let { item ->
        ModifierDialog(
            item = item,
            onDismiss = { viewModel.dismissModifierDialog() },
            onConfirm = { modifiers, extraCharge, qty ->
                viewModel.addDraftItem(
                    menuItem = item,
                    modifiers = modifiers,
                    extraCharge = extraCharge,
                    quantity = qty
                )
            }
        )
    }

    // Payment Dialog
    if (uiState.showPaymentModal) {
        val currentSubtotal = uiState.activeOrder?.subtotal
            ?: uiState.draftCart.sumOf { it.totalPrice }
        val currentTabTitle = uiState.activeOrder?.tabName
            ?: "Quick Bar Sale"

        PaymentDialog(
            subtotal = currentSubtotal,
            tabName = currentTabTitle,
            onDismiss = { viewModel.dismissPaymentModal() },
            onCompletePayment = { method, tender, tip, cardLast4 ->
                viewModel.processPayment(
                    paymentMethod = method,
                    tenderAmount = tender,
                    cardLast4 = cardLast4
                )
            }
        )
    }

    // Digital Receipt Dialog
    uiState.paymentSuccessReceipt?.let { receipt ->
        ReceiptDialog(
            receipt = receipt,
            onDismiss = { viewModel.dismissReceiptModal() }
        )
    }

    // New Tab Dialog
    if (uiState.showNewTabModal) {
        val currentBartender = activeShift?.bartenderName ?: "Elena V. (Lead)"
        NewTabDialog(
            defaultBartender = currentBartender,
            onDismiss = { viewModel.dismissNewTabDialog() },
            onConfirm = { tabName, table, bartender, isVip, card, notes ->
                viewModel.createNewTab(
                    tabName = tabName,
                    tableNumber = table,
                    bartenderName = bartender,
                    isVipTable = isVip,
                    cardLast4 = card,
                    notes = notes
                )
            }
        )
    }

    // Shift Z-Report Dialog
    if (uiState.showZReportModal) {
        ZReportDialog(
            shift = uiState.lastClosedShift ?: activeShift,
            onDismiss = { viewModel.dismissZReportModal() }
        )
    }
}
