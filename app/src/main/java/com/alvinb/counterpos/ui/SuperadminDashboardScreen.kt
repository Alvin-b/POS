package com.alvinb.counterpos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvinb.counterpos.data.SubscriptionManager
import com.alvinb.counterpos.data.remote.AdminAuditEvent
import com.alvinb.counterpos.data.remote.AdminBusiness
import com.alvinb.counterpos.data.remote.PlatformBillingSettings
import com.alvinb.counterpos.ui.scanner.BarcodeScannerScreen
import com.alvinb.counterpos.ui.scanner.ScannerMode

private enum class SuperadminTab(val title: String, val navLabel: String, val icon: ImageVector) {
    PORTFOLIO("Client Portfolio", "Clients", Icons.Default.Business),
    REGISTER("Register Tenant", "Register", Icons.Default.AddBusiness),
    BILLING("Platform Defaults", "Defaults", Icons.Default.CreditCard),
    AUDIT("Security Audit", "Audit", Icons.Default.History)
}

private val AdminCanvas = Color(0xFF070C0B)
private val AdminSurface = Color(0xFF101916)
private val AdminSurfaceElevated = Color(0xFF172420)
private val AdminBorder = Color(0xFF23342E)
private val AdminEmerald = Color(0xFF34D399)
private val AdminAmber = Color(0xFFFBBF24)
private val AdminCrimson = Color(0xFFFB7185)
private val AdminSky = Color(0xFF38BDF8)
private val AdminMuted = Color(0xFF93ACA2)

private fun superadminDarkColorScheme() = darkColorScheme(
    primary = AdminEmerald,
    onPrimary = Color(0xFF022619),
    primaryContainer = Color(0xFF0F3B2D),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = AdminSky,
    onSecondary = Color(0xFF042636),
    secondaryContainer = Color(0xFF103445),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = AdminAmber,
    onTertiary = Color(0xFF2B1C00),
    background = AdminCanvas,
    onBackground = Color(0xFFEDF5F1),
    surface = AdminSurface,
    onSurface = Color(0xFFEDF5F1),
    surfaceVariant = AdminSurfaceElevated,
    onSurfaceVariant = AdminMuted,
    outline = AdminBorder,
    outlineVariant = Color(0xFF1D2C27),
    error = AdminCrimson,
    onError = Color(0xFF3D0611),
    errorContainer = Color(0xFF4C111E),
    onErrorContainer = Color(0xFFFFD5DC)
)

internal fun adminStateColor(state: AdminBillingState): Color = when (state) {
    AdminBillingState.PAID -> AdminEmerald
    AdminBillingState.ACTIVE_UNVERIFIED -> Color(0xFF6EE7B7)
    AdminBillingState.TRIAL -> AdminAmber
    AdminBillingState.EXPIRED -> AdminCrimson
    AdminBillingState.LOCKED -> Color(0xFFFF6B4A)
    AdminBillingState.UNKNOWN -> AdminMuted
}

@Composable
fun SuperadminDashboardScreen(
    viewModel: CounterPosViewModel,
    email: String,
    error: String?,
    onSignOut: () -> Unit,
    onClearError: () -> Unit
) {
    val businesses by viewModel.adminBusinesses.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(SuperadminTab.PORTFOLIO) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }
    var businessPendingDeletion by remember { mutableStateOf<AdminBusiness?>(null) }
    var businessPendingElevation by remember { mutableStateOf<AdminBusiness?>(null) }

    // Register Tenant Form state
    var regBusinessName by remember { mutableStateOf("") }
    var regOwnerEmail by remember { mutableStateOf("") }
    var regOwnerPhone by remember { mutableStateOf("") }
    var regBusinessType by remember { mutableStateOf("big_club") }
    var regBusinessSize by remember { mutableStateOf("medium") }
    var regInventoryMode by remember { mutableStateOf("shared") }
    var regCounterCount by remember { mutableStateOf("1") }

    LaunchedEffect(Unit) {
        viewModel.loadAdminBusinesses()
        viewModel.loadAdminPlatformBillingSettings()
        viewModel.loadAdminAuditEvents()
    }

    if (state.isScannerOpen) {
        BarcodeScannerScreen(
            mode = ScannerMode.ADMIN_QR,
            products = emptyList(),
            onClose = viewModel::closeScanner,
            onProductScannedForSale = {},
            onProductScannedForStock = {},
            onProductScannedForTable = {},
            onAdminQrCodeScanned = viewModel::handleAdminQrCode,
            onBarcodeAssigned = {},
            onBarcodeNotRecognized = viewModel::handleAdminQrCode,
            scannerTitle = "ADMIN ENROLLMENT QR",
            scannerSubtitle = "Scan a business owner or staff phone's sign-in QR to authorize it."
        )
        return
    }

    val filteredBusinesses = remember(businesses, searchQuery, statusFilter) {
        businesses.filter { business ->
            val matchesFilter = when (statusFilter) {
                "all" -> true
                "active" -> business.status == "active"
                "suspended" -> business.status == "suspended"
                "closed" -> business.status == "closed"
                "paid", "active_unverified", "trial", "expired", "locked", "unknown" ->
                    business.billingState().filterKey == statusFilter
                "clubs" -> business.businessType == "big_club" || business.businessType == "club"
                "liquor" -> business.businessType == "counter_seller" || business.businessType == "liquor_store"
                "awaiting_owner" -> business.ownerEmail == "Pending owner enrollment"
                else -> true
            }
            val query = searchQuery.trim()
            val queryDigits = query.filter(Char::isDigit)
            val phoneDigits = business.effectivePhone()?.filter(Char::isDigit).orEmpty()
            val matchesPhoneDigits = queryDigits.length >= 3 && (
                phoneDigits.contains(queryDigits) ||
                    (queryDigits.startsWith("0") && phoneDigits.contains("254" + queryDigits.drop(1)))
                )
            val matchesQuery = query.isBlank() ||
                business.shopName.contains(query, ignoreCase = true) ||
                business.ownerEmail.contains(query, ignoreCase = true) ||
                business.ownerPhone?.contains(query, ignoreCase = true) == true ||
                business.lastPaymentPhone?.contains(query, ignoreCase = true) == true ||
                business.displayPhoneLabel().contains(query, ignoreCase = true) ||
                matchesPhoneDigits ||
                business.businessType.contains(query, ignoreCase = true)
            matchesFilter && matchesQuery
        }.sortedWith(
            compareBy<AdminBusiness> { it.ownerEmail == "Pending owner enrollment" }
                .thenByDescending { it.billingState() == AdminBillingState.LOCKED }
                .thenBy { it.shopName.lowercase() }
        )
    }

    MaterialTheme(colorScheme = superadminDarkColorScheme()) {
        Scaffold(
            containerColor = AdminCanvas,
            topBar = {
                SuperadminDarkTopBar(
                    email = email,
                    activeTab = selectedTab,
                    isRefreshing = state.isLoadingAdminBusinesses,
                    onRefresh = {
                        viewModel.loadAdminBusinesses()
                        viewModel.loadAdminPlatformBillingSettings()
                        viewModel.loadAdminAuditEvents()
                    },
                    onSignOut = onSignOut
                )
            },
            bottomBar = {
                SuperadminDarkBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        ) { paddingValues ->
            when (selectedTab) {
                SuperadminTab.PORTFOLIO -> SuperadminPortfolioContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    businesses = businesses,
                    filteredBusinesses = filteredBusinesses,
                    adminEnrollmentCredential = state.adminEnrollmentCredential,
                    searchQuery = searchQuery,
                    statusFilter = statusFilter,
                    isLoading = state.isLoadingAdminBusinesses,
                    isDeletingBusiness = state.isDeletingAdminBusiness,
                    hasFreshData = state.hasFreshAdminBusinesses,
                    refreshError = state.adminBusinessesError,
                    selectedBusinessId = state.adminSelectedBusinessId,
                    onSearchChange = { searchQuery = it },
                    onStatusFilterChange = { statusFilter = it },
                    onSelectBusiness = { shopId ->
                        val next = if (state.adminSelectedBusinessId == shopId) "" else shopId
                        viewModel.selectAdminBusiness(next)
                    },
                    onCreateOwnerPasskey = { shopId -> viewModel.createAdminEnrollmentPass(shopId, "owner") },
                    onOpenSubscription = { viewModel.adminOpenSubscriptionDialog(it) },
                    onStatusChange = { shopId, status, fallback ->
                        viewModel.setBusinessStatus(shopId, status, fallback)
                    },
                    onRequestElevateToClub = { businessPendingElevation = it },
                    onRequestDeleteBusiness = { businessPendingDeletion = it },
                    onNavigateToRegister = { selectedTab = SuperadminTab.REGISTER },
                    error = error,
                    notice = state.notice,
                    onClearMessage = onClearError
                )

                SuperadminTab.REGISTER -> SuperadminRegisterTenantContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    businessName = regBusinessName,
                    ownerEmail = regOwnerEmail,
                    ownerPhone = regOwnerPhone,
                    businessType = regBusinessType,
                    businessSize = regBusinessSize,
                    inventoryMode = regInventoryMode,
                    counterCount = regCounterCount,
                    isCreating = state.isCreatingAdminBusiness,
                    onBusinessNameChange = { regBusinessName = it; onClearError() },
                    onOwnerEmailChange = { regOwnerEmail = it; onClearError() },
                    onOwnerPhoneChange = { regOwnerPhone = it; onClearError() },
                    onBusinessTypeChange = { regBusinessType = it },
                    onBusinessSizeChange = { regBusinessSize = it },
                    onInventoryModeChange = { regInventoryMode = it },
                    onCounterCountChange = { regCounterCount = it.filter(Char::isDigit).take(2) },
                    onCreate = {
                        viewModel.createBusinessForOwner(
                            name = regBusinessName,
                            ownerEmail = regOwnerEmail,
                            ownerPhone = regOwnerPhone,
                            businessType = regBusinessType,
                            businessSize = regBusinessSize,
                            inventoryMode = regInventoryMode,
                            counterCount = regCounterCount.toIntOrNull() ?: 1,
                            onCreated = { selectedTab = SuperadminTab.PORTFOLIO }
                        )
                    }
                )

                SuperadminTab.BILLING -> PlatformBillingDefaultsContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    settings = state.platformBillingSettings,
                    isLoading = state.isLoadingPlatformBillingSettings,
                    isSaving = state.isSavingPlatformBillingSettings,
                    error = error,
                    notice = state.notice,
                    onRefresh = { viewModel.loadAdminPlatformBillingSettings() },
                    onSave = { days, price -> viewModel.adminUpdatePlatformBillingSettings(days, price) },
                    onClearMessage = onClearError
                )

                SuperadminTab.AUDIT -> AuditTrailDarkContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    auditEvents = state.adminAuditEvents,
                    isLoading = state.isLoadingAuditEvents,
                    onRefresh = { viewModel.loadAdminAuditEvents() }
                )
            }
        }

        val selectedSubBusiness = state.adminSelectedSubscriptionBusiness
        if (selectedSubBusiness != null) {
            AdminClientBillingSheet(
                business = selectedSubBusiness,
                isUpdating = state.isUpdatingSubscription,
                isUpdatingPhone = state.isUpdatingAdminPhone,
                isUpdatingBusinessType = state.isUpdatingAdminBusinessType,
                onDismiss = viewModel::adminCloseSubscriptionDialog,
                onSave = { price, endsAt, status, planDays, trialEndsAt, note ->
                    viewModel.adminUpdateShopSubscription(
                        shopId = selectedSubBusiness.shopId,
                        priceKsh = price,
                        endsAtIso = endsAt,
                        status = status,
                        planDays = planDays,
                        trialEndsAtIso = trialEndsAt,
                        note = note
                    )
                },
                onSetCustomTrial = { days ->
                    viewModel.adminSetCustomTrial(selectedSubBusiness.shopId, days)
                },
                onExtendTrial = { days ->
                    viewModel.adminExtendTrial(selectedSubBusiness.shopId, days)
                },
                onActivatePaid = { days ->
                    viewModel.adminActivatePaidTerm(selectedSubBusiness.shopId, days)
                },
                onSetPaymentLock = { locked, amount, reason ->
                    viewModel.adminSetPaymentLock(selectedSubBusiness.shopId, locked, amount, reason)
                },
                onUpdatePhone = { phone ->
                    viewModel.adminUpdateBusinessPhone(selectedSubBusiness.shopId, phone)
                },
                onElevateToClub = {
                    businessPendingElevation = selectedSubBusiness
                },
                onRequestDeleteBusiness = {
                    businessPendingDeletion = selectedSubBusiness
                }
            )
        }

        val targetForElevation = businessPendingElevation
        if (targetForElevation != null) {
            AlertDialog(
                onDismissRequest = { if (!state.isUpdatingAdminBusinessType) businessPendingElevation = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Upgrade,
                        contentDescription = null,
                        tint = AdminEmerald,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Elevate ${targetForElevation.shopName} to Club?",
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "This upgrades ${targetForElevation.shopName} from Liquor Store to a full Club profile.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Unlocks: Floor Table Management, Multiple Counters, Team Working Hours, and Sales Analytics.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AdminEmerald
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.adminElevateToClub(targetForElevation.shopId)
                            businessPendingElevation = null
                        },
                        enabled = !state.isUpdatingAdminBusinessType,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AdminEmerald,
                            contentColor = Color(0xFF022619)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("confirm_elevate_to_club_button")
                    ) {
                        if (state.isUpdatingAdminBusinessType) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF022619))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Elevate to Club", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { businessPendingElevation = null },
                        enabled = !state.isUpdatingAdminBusinessType
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        val targetForDeletion = businessPendingDeletion
        if (targetForDeletion != null) {
            AlertDialog(
                onDismissRequest = { if (!state.isDeletingAdminBusiness) businessPendingDeletion = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = AdminCrimson,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Delete ${targetForDeletion.shopName}?",
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "This permanently removes ${targetForDeletion.shopName}, all staff accounts, inventory, tables, sales receipts, and billing history.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Phone: ${targetForDeletion.displayPhoneLabel()}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AdminEmerald
                        )
                        Text(
                            text = "This action cannot be undone.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AdminCrimson
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.adminDeleteBusiness(targetForDeletion.shopId, targetForDeletion.shopName)
                            businessPendingDeletion = null
                        },
                        enabled = !state.isDeletingAdminBusiness,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AdminCrimson,
                            contentColor = Color(0xFF3D0611)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("confirm_delete_admin_business_button")
                    ) {
                        if (state.isDeletingAdminBusiness) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF3D0611))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Delete Permanently", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { businessPendingDeletion = null },
                        enabled = !state.isDeletingAdminBusiness
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SuperadminDarkTopBar(
    email: String,
    activeTab: SuperadminTab,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    Surface(
        color = AdminSurface,
        border = BorderStroke(1.dp, AdminBorder.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AdminEmerald.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, AdminEmerald.copy(alpha = 0.35f))
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = "Superadmin Console",
                    tint = AdminEmerald,
                    modifier = Modifier
                        .padding(9.dp)
                        .size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activeTab.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Counter POS Command • $email",
                    fontSize = 11.sp,
                    color = AdminMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AdminEmerald)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            IconButton(onClick = onSignOut, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out", tint = AdminCrimson)
            }
        }
    }
}

@Composable
private fun SuperadminDarkBottomBar(
    selectedTab: SuperadminTab,
    onTabSelected: (SuperadminTab) -> Unit
) {
    Surface(
        color = AdminSurface,
        border = BorderStroke(1.dp, AdminBorder.copy(alpha = 0.7f)),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SuperadminTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                Surface(
                    onClick = { onTabSelected(tab) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) AdminEmerald.copy(alpha = 0.14f) else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.navLabel,
                            tint = if (selected) AdminEmerald else AdminMuted,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tab.navLabel,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) AdminEmerald else AdminMuted,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuperadminPortfolioContent(
    modifier: Modifier,
    businesses: List<AdminBusiness>,
    filteredBusinesses: List<AdminBusiness>,
    adminEnrollmentCredential: AdminEnrollmentCredential?,
    searchQuery: String,
    statusFilter: String,
    isLoading: Boolean,
    isDeletingBusiness: Boolean,
    hasFreshData: Boolean,
    refreshError: String?,
    selectedBusinessId: String?,
    onSearchChange: (String) -> Unit,
    onStatusFilterChange: (String) -> Unit,
    onSelectBusiness: (String) -> Unit,
    onCreateOwnerPasskey: (String) -> Unit,
    onOpenSubscription: (AdminBusiness) -> Unit,
    onStatusChange: (String, String, String?) -> Unit,
    onRequestElevateToClub: (AdminBusiness) -> Unit,
    onRequestDeleteBusiness: (AdminBusiness) -> Unit,
    onNavigateToRegister: () -> Unit,
    error: String?,
    notice: String?,
    onClearMessage: () -> Unit
) {
    val needsAttention = businesses.filter {
        it.billingState() in setOf(AdminBillingState.LOCKED, AdminBillingState.EXPIRED) ||
            it.ownerEmail == "Pending owner enrollment" ||
            it.status.equals("suspended", ignoreCase = true)
    }
    val paidCount = businesses.count { it.billingState() == AdminBillingState.PAID || it.billingState() == AdminBillingState.ACTIVE_UNVERIFIED }
    val trialCount = businesses.count { it.billingState() == AdminBillingState.TRIAL }
    val blockedCount = businesses.count { it.billingState() in setOf(AdminBillingState.LOCKED, AdminBillingState.EXPIRED) }

    val statusMix = listOf(
        AdminBillingState.PAID to businesses.count { it.billingState() == AdminBillingState.PAID },
        AdminBillingState.ACTIVE_UNVERIFIED to businesses.count { it.billingState() == AdminBillingState.ACTIVE_UNVERIFIED },
        AdminBillingState.TRIAL to trialCount,
        AdminBillingState.EXPIRED to businesses.count { it.billingState() == AdminBillingState.EXPIRED },
        AdminBillingState.LOCKED to businesses.count { it.billingState() == AdminBillingState.LOCKED }
    )
    val filters = listOf(
        "all" to "All",
        "awaiting_owner" to "Needs owner",
        "paid" to "Paid",
        "active_unverified" to "Active",
        "trial" to "Trial",
        "expired" to "Expired",
        "locked" to "Locked",
        "suspended" to "Suspended",
        "clubs" to "Bars",
        "liquor" to "Liquor"
    )

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Dark Bento Portfolio Hero Card
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, AdminEmerald.copy(alpha = 0.28f)),
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF0E2C23), Color(0xFF101916))
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(
                                    text = "LIVE CLIENT PORTFOLIO",
                                    fontSize = 10.sp,
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AdminEmerald
                                )
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = businesses.size.toString(),
                                        fontSize = 38.sp,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = " registered tenants",
                                        fontSize = 13.sp,
                                        color = AdminMuted,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(99.dp),
                                color = if (hasFreshData) AdminEmerald.copy(alpha = 0.14f) else AdminAmber.copy(alpha = 0.14f),
                                border = BorderStroke(
                                    1.dp,
                                    if (hasFreshData) AdminEmerald.copy(alpha = 0.4f) else AdminAmber.copy(alpha = 0.4f)
                                )
                            ) {
                                Text(
                                    text = when {
                                        isLoading -> "SYNCING…"
                                        !refreshError.isNullOrBlank() -> "CACHED"
                                        hasFreshData -> "LIVE SERVER"
                                        else -> "STANDBY"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasFreshData) AdminEmerald else AdminAmber,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HeroBentoStat("Active / Paid", paidCount.toString(), AdminEmerald, Modifier.weight(1f))
                            HeroBentoStat("On Trial", trialCount.toString(), AdminAmber, Modifier.weight(1f))
                            HeroBentoStat("Locked / Exp.", blockedCount.toString(), AdminCrimson, Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Scrollable Breakdown Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                statusMix.forEach { (billingState, count) ->
                    val accent = adminStateColor(billingState)
                    val selected = statusFilter == billingState.filterKey
                    Surface(
                        onClick = {
                            onStatusFilterChange(if (selected) "all" else billingState.filterKey)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) accent.copy(alpha = 0.18f) else AdminSurface,
                        border = BorderStroke(
                            1.dp,
                            if (selected) accent.copy(alpha = 0.65f) else AdminBorder
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = count.toString(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = accent
                            )
                            Text(
                                text = billingState.label,
                                fontSize = 10.sp,
                                color = AdminMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        if (!refreshError.isNullOrBlank()) {
            item {
                DarkBanner(
                    message = "Live refresh issue: $refreshError. Showing last saved portfolio snapshot.",
                    isError = true,
                    onDismiss = onClearMessage
                )
            }
        }
        if (!error.isNullOrBlank()) {
            item { DarkBanner(message = error, isError = true, onDismiss = onClearMessage) }
        }
        if (!notice.isNullOrBlank()) {
            item { DarkBanner(message = notice, isError = false, onDismiss = onClearMessage) }
        }

        adminEnrollmentCredential?.let { credential ->
            item {
                val clipboardManager = LocalClipboardManager.current
                Surface(
                    color = AdminSky.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AdminSky.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Issued ${credential.role.replace('_', ' ')} passkey • ${credential.businessName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = AdminSky,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { clipboardManager.setText(AnnotatedString(credential.passkey)) }) {
                                Text("Copy", color = AdminSky, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            credential.passkey,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val qrPayload = remember(credential.passkey) {
                            if (credential.passkey.startsWith("CPX1.", ignoreCase = true)) {
                                credential.passkey
                            } else {
                                "CPX1.${credential.passkey}"
                            }
                        }
                        val passkeyBitmap = remember(qrPayload) { createLoginQr(qrPayload) }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            modifier = Modifier
                                .size(156.dp)
                                .align(Alignment.CenterHorizontally)
                        ) {
                            Image(
                                bitmap = passkeyBitmap.asImageBitmap(),
                                contentDescription = "Owner enrollment passkey QR code",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        }
                        Text(
                            "One-time credential • expires in ${credential.expiresInHours} hours",
                            fontSize = 11.sp,
                            color = AdminMuted
                        )
                    }
                }
            }
        }

        if (needsAttention.isNotEmpty()) {
            item {
                Text(
                    text = "Requires Action • ${needsAttention.size}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AdminAmber,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(needsAttention.take(4), key = { "need_${it.shopId}" }) { business ->
                DarkAttentionCard(business, onOpenSubscription, onCreateOwnerPasskey)
            }
        }

        item {
            Text(
                text = "All Client Stores",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_search_input"),
                singleLine = true,
                placeholder = { Text("Search business, phone, owner email, or type") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AdminEmerald) },
                trailingIcon = if (searchQuery.isNotBlank()) {
                    {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                } else null,
                shape = RoundedCornerShape(14.dp)
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filters.forEach { (key, label) ->
                    val count = darkFilterCount(businesses, key)
                    FilterChip(
                        selected = statusFilter == key,
                        onClick = { onStatusFilterChange(key) },
                        label = { Text("$label ($count)", fontSize = 11.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        if (filteredBusinesses.isEmpty()) {
            item {
                DarkEmptyPortfolioCard(
                    hasAny = businesses.isNotEmpty(),
                    onClearSearch = {
                        onSearchChange("")
                        onStatusFilterChange("all")
                    },
                    onRegisterNew = onNavigateToRegister
                )
            }
        } else {
            items(filteredBusinesses, key = { it.shopId }) { business ->
                DarkBusinessCard(
                    business = business,
                    isExpanded = selectedBusinessId == business.shopId,
                    isDeleting = isDeletingBusiness,
                    onToggleExpand = { onSelectBusiness(business.shopId) },
                    onCreateOwnerPasskey = { onCreateOwnerPasskey(business.shopId) },
                    onOpenSubscription = { onOpenSubscription(business) },
                    onStatusChange = { nextStatus ->
                        onStatusChange(business.shopId, nextStatus, "Updated from Superadmin console")
                    },
                    onElevateToClub = { onRequestElevateToClub(business) },
                    onDeleteBusiness = { onRequestDeleteBusiness(business) }
                )
            }
        }

        item {
            OutlinedButton(
                onClick = onNavigateToRegister,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AdminEmerald.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.AddBusiness, contentDescription = null, tint = AdminEmerald, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Register New Business", color = AdminEmerald, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeroBentoStat(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0A1210),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = accent)
            Text(label, fontSize = 10.sp, color = AdminMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DarkBanner(message: String, isError: Boolean, onDismiss: () -> Unit) {
    val bg = if (isError) AdminCrimson.copy(alpha = 0.14f) else AdminEmerald.copy(alpha = 0.14f)
    val fg = if (isError) AdminCrimson else AdminEmerald
    Surface(
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(18.dp)
            )
            Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = fg, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun DarkAttentionCard(
    business: AdminBusiness,
    onOpenSubscription: (AdminBusiness) -> Unit,
    onCreateOwnerPasskey: (String) -> Unit
) {
    val billingState = business.billingState()
    val isBillingIssue = billingState in setOf(AdminBillingState.LOCKED, AdminBillingState.EXPIRED)
    val accent = if (isBillingIssue) AdminCrimson else AdminAmber
    val reason = when {
        billingState == AdminBillingState.LOCKED -> "Payment locked • ${business.paymentLockReason ?: "Payment required"}"
        business.ownerEmail == "Pending owner enrollment" -> "Owner device hasn't enrolled yet"
        business.status.equals("suspended", ignoreCase = true) -> "Business access is suspended"
        billingState == AdminBillingState.EXPIRED -> business.billingExpiryLabel()
        else -> "Review store access"
    }

    Surface(
        color = AdminSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isBillingIssue) Icons.Default.Lock else Icons.Default.Key,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(business.shopName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(reason, fontSize = 11.sp, color = AdminMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = business.displayPhoneLabel(),
                    fontSize = 11.sp,
                    color = if (business.effectivePhone() != null) AdminEmerald else AdminMuted,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = {
                    if (isBillingIssue || business.status.equals("suspended", ignoreCase = true)) {
                        onOpenSubscription(business)
                    } else {
                        onCreateOwnerPasskey(business.shopId)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF0C1210)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = if (isBillingIssue) "Manage" else "Enroll",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DarkBusinessCard(
    business: AdminBusiness,
    isExpanded: Boolean,
    isDeleting: Boolean,
    onToggleExpand: () -> Unit,
    onCreateOwnerPasskey: () -> Unit,
    onOpenSubscription: () -> Unit,
    onStatusChange: (String) -> Unit,
    onElevateToClub: () -> Unit,
    onDeleteBusiness: () -> Unit
) {
    val context = LocalContext.current
    val billingState = business.billingState()
    val billingColor = adminStateColor(billingState)
    val effectivePrice = business.paymentLockAmountKsh.takeIf { it > 0 } ?: business.subscriptionPriceKsh
    val effectivePhone = business.effectivePhone()

    Surface(
        color = AdminSurface,
        border = BorderStroke(
            width = if (isExpanded) 1.5.dp else 1.dp,
            color = if (isExpanded) AdminEmerald.copy(alpha = 0.55f) else AdminBorder
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = billingColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, billingColor.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = business.shopName.take(1).uppercase(),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = billingColor
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = business.shopName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Details",
                            tint = AdminMuted
                        )
                    }

                    Text(
                        text = if (business.ownerEmail == "Pending owner enrollment") "No owner enrolled yet" else business.ownerEmail,
                        fontSize = 11.sp,
                        color = AdminMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.testTag("admin_business_phone_${business.shopId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Client phone",
                            tint = if (effectivePhone != null) AdminEmerald else AdminMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = business.displayPhoneLabel(),
                            fontSize = 11.sp,
                            fontWeight = if (effectivePhone != null) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (effectivePhone != null) AdminEmerald else AdminMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                        AdminStatusPill(billingState.label, billingColor)
                        AdminStatusPill(
                            label = business.businessTypeLabel(),
                            color = if (business.isLiquorStoreBusiness()) AdminAmber else AdminSky
                        )
                        if (business.status.equals("suspended", ignoreCase = true)) {
                            AdminStatusPill("Suspended", AdminAmber)
                        } else if (business.status.equals("closed", ignoreCase = true)) {
                            AdminStatusPill("Closed", AdminMuted)
                        }
                    }

                    Text(
                        text = "${business.billingExpiryLabel()} • KSh $effectivePrice",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = business.paymentRecordLabel(),
                        fontSize = 10.sp,
                        color = AdminMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            AnimatedVisibility(isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = AdminBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DarkMetricCell(business.memberCount.coerceAtLeast(1).minus(1).toString(), "Staff")
                        DarkMetricCell(business.productCount.toString(), "Products")
                        DarkMetricCell(business.stockUnits.toString(), "Units")
                        DarkMetricCell("${business.subscriptionPlanDays}d", "Term")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenSubscription,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("admin_open_billing_${business.shopId}"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AdminEmerald,
                                contentColor = Color(0xFF022619)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Billing & Trial", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                onStatusChange(if (business.status == "suspended") "active" else "suspended")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (business.status == "suspended") "Reopen Store" else "Suspend Store",
                                fontSize = 12.sp,
                                color = if (business.status == "suspended") AdminEmerald else AdminAmber
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onCreateOwnerPasskey,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Generate Owner Passkey & QR", fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (effectivePhone != null) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$effectivePhone"))
                                        context.startActivity(dialIntent)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, AdminEmerald.copy(alpha = 0.45f))
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null, tint = AdminEmerald, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Call Client", fontSize = 11.sp, color = AdminEmerald, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        OutlinedButton(
                            onClick = onDeleteBusiness,
                            enabled = !isDeleting,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("admin_delete_business_button_${business.shopId}"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AdminCrimson.copy(alpha = 0.55f))
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = AdminCrimson, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Delete Business", fontSize = 11.sp, color = AdminCrimson, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (business.isLiquorStoreBusiness()) {
                        OutlinedButton(
                            onClick = onElevateToClub,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("admin_elevate_to_club_${business.shopId}"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AdminEmerald.copy(alpha = 0.55f))
                        ) {
                            Icon(Icons.Default.Upgrade, contentDescription = null, tint = AdminEmerald, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Elevate Liquor Store to Club", fontSize = 12.sp, color = AdminEmerald, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AdminStatusPill(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(99.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun DarkMetricCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(label, color = AdminMuted, fontSize = 10.sp)
    }
}

@Composable
private fun PlatformBillingDefaultsContent(
    modifier: Modifier,
    settings: PlatformBillingSettings?,
    isLoading: Boolean,
    isSaving: Boolean,
    error: String?,
    notice: String?,
    onRefresh: () -> Unit,
    onSave: (trialDurationDays: Int, subscriptionPriceKsh: Int) -> Unit,
    onClearMessage: () -> Unit
) {
    var trialDurationText by remember(settings?.trialDurationDays) {
        mutableStateOf(settings?.trialDurationDays?.toString() ?: "14")
    }
    var priceText by remember(settings?.subscriptionPriceKsh) {
        mutableStateOf(settings?.subscriptionPriceKsh?.toString() ?: "2500")
    }
    val trialDuration = trialDurationText.toIntOrNull()
    val price = priceText.toIntOrNull()
    val valid = settings != null && trialDuration != null && trialDuration in 0..365 &&
        price != null && price in 1..250_000

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "New Store Billing Defaults",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Configure default onboarding trials. Individual store billing & custom trials are managed per store under Clients.",
                    fontSize = 12.sp,
                    color = AdminMuted
                )
            }
            IconButton(onClick = onRefresh, enabled = !isLoading, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh billing settings", tint = AdminEmerald)
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = AdminSurface,
            border = BorderStroke(1.dp, AdminBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = AdminEmerald)
                }
                OutlinedTextField(
                    value = trialDurationText,
                    onValueChange = { trialDurationText = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Default free trial for new stores (days)") },
                    supportingText = { Text("Applies to newly created businesses only (0–365 days). Existing store trials stay independent.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Standard monthly subscription price (KSh)") },
                    supportingText = { Text("Used for new businesses and standard renewals unless overridden per store (KSh 1–250,000).") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AdminEmerald.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, AdminEmerald.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = "Tip: To assign a custom free trial, custom renewal price, or instant payment lock to one business, open the Clients tab → expand that business → tap Billing & Trial.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Button(
                    onClick = { if (valid) onSave(trialDuration!!, price!!) },
                    enabled = valid && !isLoading && !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isSaving) "Saving…" else "Save New-Store Defaults", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!error.isNullOrBlank()) {
            DarkBanner(message = error, isError = true, onDismiss = onClearMessage)
        }
        if (!notice.isNullOrBlank()) {
            DarkBanner(message = notice, isError = false, onDismiss = onClearMessage)
        }
    }
}

@Composable
private fun SuperadminRegisterTenantContent(
    modifier: Modifier,
    businessName: String,
    ownerEmail: String,
    ownerPhone: String,
    businessType: String,
    businessSize: String,
    inventoryMode: String,
    counterCount: String,
    isCreating: Boolean,
    onBusinessNameChange: (String) -> Unit,
    onOwnerEmailChange: (String) -> Unit,
    onOwnerPhoneChange: (String) -> Unit,
    onBusinessTypeChange: (String) -> Unit,
    onBusinessSizeChange: (String) -> Unit,
    onInventoryModeChange: (String) -> Unit,
    onCounterCountChange: (String) -> Unit,
    onCreate: () -> Unit
) {
    val isClub = businessType == "big_club" || businessType == "club"
    val isLiquorStore = businessType == "counter_seller" || businessType == "liquor_store"
    val ownerEmailValid = ownerEmail.isBlank() || (ownerEmail.trim().contains("@") && ownerEmail.none(Char::isWhitespace))
    val ownerPhoneValid = SubscriptionManager.normalizeKenyanPhone(ownerPhone) != null
    val counterCountValid = !isClub || (counterCount.toIntOrNull()?.let { it in 1..50 } == true)
    val canSubmit = businessName.trim().length in 2..120 && ownerEmailValid && ownerPhoneValid && counterCountValid && !isCreating

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AdminSurface,
                border = BorderStroke(1.dp, AdminBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("1. Business Identity", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AdminEmerald)
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = onBusinessNameChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_register_name"),
                        label = { Text("Business / Bar Name") },
                        placeholder = { Text("e.g. Euphoria Lounge & Grill") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = ownerEmail,
                        onValueChange = onOwnerEmailChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_register_owner_email"),
                        label = { Text("Owner Email (Optional)") },
                        placeholder = { Text("owner@example.com") },
                        isError = !ownerEmailValid,
                        supportingText = {
                            Text(
                                if (ownerEmailValid) "Leave blank to enroll via QR/passkey right after creation."
                                else "Please enter a valid email address."
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = ownerPhone,
                        onValueChange = onOwnerPhoneChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_register_owner_phone"),
                        label = { Text("Owner Mobile Number") },
                        placeholder = { Text("0712 345 678") },
                        isError = ownerPhone.isNotBlank() && !ownerPhoneValid,
                        supportingText = {
                            if (ownerPhone.isNotBlank() && !ownerPhoneValid) Text("Enter a valid Kenyan mobile number.")
                            else Text("Used to prefill M-Pesa subscription payments.")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        enabled = !isCreating,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AdminSurface,
                border = BorderStroke(1.dp, AdminBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("2. Business Architecture", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AdminEmerald)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(
                            onClick = { onBusinessTypeChange("big_club") },
                            modifier = Modifier
                                .weight(1f)
                                .height(132.dp)
                                .testTag("btn_select_type_club"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isClub) MaterialTheme.colorScheme.primaryContainer else AdminSurfaceElevated
                            ),
                            border = BorderStroke(
                                if (isClub) 2.dp else 1.dp,
                                if (isClub) AdminEmerald else AdminBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Icon(Icons.Default.TableRestaurant, contentDescription = null, tint = if (isClub) AdminEmerald else AdminMuted)
                                Column {
                                    Text("Bar & Club", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Tables & multi-counter", fontSize = 11.sp, color = AdminMuted)
                                }
                            }
                        }
                        Card(
                            onClick = { onBusinessTypeChange("counter_seller") },
                            modifier = Modifier
                                .weight(1f)
                                .height(132.dp)
                                .testTag("btn_select_type_liquor"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isLiquorStore) MaterialTheme.colorScheme.primaryContainer else AdminSurfaceElevated
                            ),
                            border = BorderStroke(
                                if (isLiquorStore) 2.dp else 1.dp,
                                if (isLiquorStore) AdminEmerald else AdminBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = if (isLiquorStore) AdminEmerald else AdminMuted)
                                Column {
                                    Text("Liquor Store", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Direct retail checkout", fontSize = 11.sp, color = AdminMuted)
                                }
                            }
                        }
                    }

                    if (isClub) {
                        Text("Club Scale", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("small" to "Small", "medium" to "Medium", "large" to "Large").forEach { (value, label) ->
                                FilterChip(
                                    selected = businessSize == value,
                                    onClick = { onBusinessSizeChange(value) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Text("Inventory Architecture", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = inventoryMode == "shared",
                                onClick = { onInventoryModeChange("shared") },
                                label = { Text("Shared Pool", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                leadingIcon = if (inventoryMode == "shared") ({ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }) else null
                            )
                            FilterChip(
                                selected = inventoryMode == "per_counter",
                                onClick = { onInventoryModeChange("per_counter") },
                                label = { Text("Per Counter", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                leadingIcon = if (inventoryMode == "per_counter") ({ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }) else null
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Service Counters", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Independent stations (1–50)", fontSize = 11.sp, color = AdminMuted)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val currentCount = counterCount.toIntOrNull() ?: 1
                                IconButton(onClick = { if (currentCount > 1) onCounterCountChange((currentCount - 1).toString()) }, enabled = currentCount > 1) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                                }
                                Text(currentCount.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                IconButton(onClick = { if (currentCount < 50) onCounterCountChange((currentCount + 1).toString()) }, enabled = currentCount < 50) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onCreate,
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_create_business_profile"),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text("Creating Business…")
                } else {
                    Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Create Client Business", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AuditTrailDarkContent(
    modifier: Modifier,
    auditEvents: List<AdminAuditEvent>,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Platform Audit Stream", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Admin, billing & authentication events", fontSize = 12.sp, color = AdminMuted)
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AdminEmerald)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh audit logs", tint = AdminEmerald)
                    }
                }
            }
        }

        if (auditEvents.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AdminSurface,
                    border = BorderStroke(1.dp, AdminBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = AdminEmerald, modifier = Modifier.size(34.dp))
                        Text(if (isLoading) "Loading audit events…" else "No audit events recorded yet", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            items(auditEvents) { event ->
                val isAuth = event.action.contains("passkey", true) || event.action.contains("login", true) || event.action.contains("enroll", true)
                val isLock = event.action.contains("lock", true) || event.action.contains("suspend", true)
                val tint = when {
                    isLock -> AdminCrimson
                    isAuth -> AdminSky
                    else -> AdminEmerald
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AdminSurface,
                    border = BorderStroke(1.dp, AdminBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(shape = CircleShape, color = tint.copy(alpha = 0.14f)) {
                            Icon(
                                imageVector = if (isAuth) Icons.Default.Key else if (isLock) Icons.Default.Pause else Icons.Default.Business,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(16.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = event.action.replace('_', ' ').replaceFirstChar { it.uppercase() },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(event.createdAt.take(19).replace('T', ' '), fontSize = 10.sp, color = AdminMuted)
                            }
                            if (event.metadata.isNotBlank() && event.metadata != "{}") {
                                Text(
                                    text = event.metadata,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AdminMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DarkEmptyPortfolioCard(
    hasAny: Boolean,
    onClearSearch: () -> Unit,
    onRegisterNew: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AdminSurface,
        border = BorderStroke(1.dp, AdminBorder)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (hasAny) Icons.Default.Search else Icons.Default.AddBusiness,
                contentDescription = null,
                tint = AdminEmerald,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = if (hasAny) "No matching client businesses" else "No client businesses yet",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = if (hasAny) "Try clearing your search or status filter." else "Create your first client business to start managing subscriptions.",
                fontSize = 12.sp,
                color = AdminMuted,
                textAlign = TextAlign.Center
            )
            if (hasAny) {
                OutlinedButton(onClick = onClearSearch) { Text("Clear Filters") }
            } else {
                Button(onClick = onRegisterNew) { Text("Register Business") }
            }
        }
    }
}

private fun darkFilterCount(businesses: List<AdminBusiness>, key: String): Int = businesses.count {
    when (key) {
        "all" -> true
        "awaiting_owner" -> it.ownerEmail == "Pending owner enrollment"
        "paid", "active_unverified", "trial", "expired", "locked", "unknown" -> it.billingState().filterKey == key
        "clubs" -> it.businessType == "big_club" || it.businessType == "club"
        "liquor" -> it.businessType == "counter_seller" || it.businessType == "liquor_store"
        "suspended" -> it.status.equals("suspended", ignoreCase = true)
        else -> true
    }
}
