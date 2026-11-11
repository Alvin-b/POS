package com.alvinb.counterpos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
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
import com.alvinb.counterpos.data.remote.AdminAuditEvent
import com.alvinb.counterpos.data.remote.AdminBusiness
import com.alvinb.counterpos.ui.scanner.BarcodeScannerScreen
import com.alvinb.counterpos.ui.scanner.ScannerMode

private enum class SuperadminTab(val title: String, val icon: ImageVector) {
    PORTFOLIO("Client Portfolio", Icons.Default.Business),
    REGISTER("Register Tenant", Icons.Default.AddBusiness),
    AUDIT("Audit & Activity", Icons.Default.History)
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
    val clipboardManager = LocalClipboardManager.current

    var selectedTab by remember { mutableStateOf(SuperadminTab.PORTFOLIO) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }
    var lifecycleReason by remember { mutableStateOf("") }
    var showStatusReasonField by remember { mutableStateOf(false) }

    // Register Form state
    var regBusinessName by remember { mutableStateOf("") }
    var regOwnerEmail by remember { mutableStateOf("") }
    var regOwnerPhone by remember { mutableStateOf("") }
    var regBusinessType by remember { mutableStateOf("big_club") }
    var regBusinessSize by remember { mutableStateOf("medium") }
    var regInventoryMode by remember { mutableStateOf("shared") }
    var regCounterCount by remember { mutableStateOf("1") }

    LaunchedEffect(Unit) {
        viewModel.loadAdminBusinesses()
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
            onBarcodeNotRecognized = viewModel::handleAdminQrCode
        )
        return
    }

    val liveCount = businesses.count { it.status == "active" && it.ownerEmail != "Pending owner enrollment" }
    val pendingOwnerCount = businesses.count { it.ownerEmail == "Pending owner enrollment" }
    val clubCount = businesses.count { it.businessType == "big_club" || it.businessType == "club" }
    val liquorCount = businesses.count { it.businessType == "counter_seller" || it.businessType == "liquor_store" }

    val filteredBusinesses = remember(businesses, searchQuery, statusFilter) {
        businesses.filter { business ->
            val matchesFilter = when (statusFilter) {
                "all" -> true
                "active" -> business.status == "active"
                "locked" -> business.paymentLockActive
                "trial" -> business.subscriptionStatus == "trial"
                "suspended" -> business.status == "suspended"
                "closed" -> business.status == "closed"
                "clubs" -> business.businessType == "big_club" || business.businessType == "club"
                "liquor" -> business.businessType == "counter_seller" || business.businessType == "liquor_store"
                "awaiting_owner" -> business.ownerEmail == "Pending owner enrollment"
                else -> true
            }
            val query = searchQuery.trim()
            val matchesQuery = query.isBlank() ||
                business.shopName.contains(query, ignoreCase = true) ||
                business.ownerEmail.contains(query, ignoreCase = true) ||
                business.businessType.contains(query, ignoreCase = true)
            matchesFilter && matchesQuery
        }
            .sortedWith(
                compareBy<AdminBusiness> { it.ownerEmail == "Pending owner enrollment" }
                    .thenBy { it.ownerEmail.lowercase() }
                    .thenBy { it.shopName.lowercase() }
            )
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF0E5B53),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE4EBE8),
            onPrimaryContainer = Color(0xFF13211E),
            secondaryContainer = Color(0xFFE4EBE8),
            onSecondaryContainer = Color(0xFF13211E),
            background = Color(0xFFEEF2F0),
            onBackground = Color(0xFF13211E),
            surface = Color.White,
            onSurface = Color(0xFF13211E),
            surfaceVariant = Color(0xFFE4EBE8),
            onSurfaceVariant = Color(0xFF5F6F6A),
            outline = Color(0xFFD3DDD9),
            outlineVariant = Color(0xFFE3EBE6),
            error = Color(0xFFB3261E),
            errorContainer = Color(0xFFFBE0DC),
            onErrorContainer = Color(0xFF7A271A)
        )
    ) {
    Scaffold(
        containerColor = Color(0xFFEEF2F0),
        topBar = {
            FaithfulSuperadminTopBar(
                email = email,
                onRefresh = {
                    viewModel.loadAdminBusinesses()
                    viewModel.loadAdminAuditEvents()
                },
                onSignOut = onSignOut
            )
        },
        bottomBar = {
            FaithfulSuperadminBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { paddingValues ->
        when (selectedTab) {
            SuperadminTab.PORTFOLIO -> FaithfulPortfolioContent(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                businesses = businesses,
                filteredBusinesses = filteredBusinesses,
                searchQuery = searchQuery,
                statusFilter = statusFilter,
                selectedBusinessId = state.adminSelectedBusinessId,
                onSearchChange = { searchQuery = it },
                onStatusFilterChange = { statusFilter = it },
                onSelectBusiness = { viewModel.selectAdminBusiness(it) },
                onScanOwnerQr = { shopId -> viewModel.openAdminEnrollmentScanner(shopId, "owner") },
                onCreateOwnerPasskey = { shopId -> viewModel.createAdminEnrollmentPass(shopId, "owner") },
                onOpenSubscription = { viewModel.adminOpenSubscriptionDialog(it) },
                onStatusChange = { shopId, status, fallback ->
                    viewModel.setBusinessStatus(shopId, status, lifecycleReason.trim().ifBlank { fallback })
                },
                onNavigateToRegister = { selectedTab = SuperadminTab.REGISTER },
                error = error,
                notice = state.notice,
                onClearMessage = onClearError
            )
            SuperadminTab.REGISTER -> RegisterTenantContent(
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
                onCreate = { viewModel.createBusinessForOwner(regBusinessName, regOwnerEmail, regOwnerPhone, regBusinessType, regBusinessSize, regInventoryMode, regCounterCount.toIntOrNull() ?: 1, onCreated = { selectedTab = SuperadminTab.PORTFOLIO }) }
            )
            SuperadminTab.AUDIT -> AuditTrailContent(
                auditEvents = state.adminAuditEvents,
                isLoading = state.isLoadingAuditEvents,
                onRefresh = { viewModel.loadAdminAuditEvents() }
            )
        }
    }
    }

    val selectedSubBusiness = state.adminSelectedSubscriptionBusiness
    if (selectedSubBusiness != null) {
        AdminSubscriptionDialog(
            business = selectedSubBusiness,
            isUpdating = state.isUpdatingSubscription,
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
            onExtendTrial = { viewModel.adminExtendTrial(selectedSubBusiness.shopId, 14) },
            onActivatePaid = { days -> viewModel.adminActivatePaidTerm(selectedSubBusiness.shopId, days) },
            onSetPaymentLock = { locked, amount, reason ->
                viewModel.adminSetPaymentLock(selectedSubBusiness.shopId, locked, amount, reason)
            }
        )
    }
}

@Composable
private fun ModernPlatformTopBar(
    email: String,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(9.dp)
                        .size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "CounterPOS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = "PLATFORM SUPERADMIN",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            letterSpacing = 0.4.sp
                        )
                    }
                }
                Text(
                    text = email,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.testTag("btn_refresh_superadmin")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh data",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onSignOut,
                modifier = Modifier.testTag("btn_sign_out_superadmin")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Sign out",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SuperadminMetricsStrip(
    totalCount: Int,
    liveCount: Int,
    pendingOwnerCount: Int,
    clubCount: Int,
    liquorCount: Int,
    onMetricClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PlatformMetricChip(
            label = "Total Tenants",
            value = totalCount.toString(),
            icon = Icons.Default.Business,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            onClick = { onMetricClick("all") }
        )
        PlatformMetricChip(
            label = "Live Active",
            value = liveCount.toString(),
            icon = Icons.Default.CheckCircle,
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = { onMetricClick("active") }
        )
        PlatformMetricChip(
            label = "Awaiting Owner",
            value = pendingOwnerCount.toString(),
            icon = Icons.Default.Key,
            containerColor = if (pendingOwnerCount > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = if (pendingOwnerCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = { onMetricClick("awaiting_owner") }
        )
        PlatformMetricChip(
            label = "Clubs & Bars",
            value = clubCount.toString(),
            icon = Icons.Default.TableRestaurant,
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = { onMetricClick("clubs") }
        )
        PlatformMetricChip(
            label = "Liquor Stores",
            value = liquorCount.toString(),
            icon = Icons.Default.Storefront,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = { onMetricClick("liquor") }
        )
    }
}

@Composable
private fun PlatformMetricChip(
    label: String,
    value: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Column {
                Text(
                    text = value,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = contentColor,
                    lineHeight = 16.sp
                )
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = contentColor.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ModernSuperadminTabs(
    selectedTab: SuperadminTab,
    portfolioCount: Int,
    auditCount: Int,
    onTabSelected: (SuperadminTab) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) }
    ) {
        SuperadminTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab
            Tab(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = tab.title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                        if (tab == SuperadminTab.PORTFOLIO && portfolioCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = portfolioCount.toString(),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun PortfolioContent(
    businesses: List<AdminBusiness>,
    filteredBusinesses: List<AdminBusiness>,
    selectedBusinessId: String?,
    adminEnrollmentCredential: AdminEnrollmentCredential?,
    searchQuery: String,
    statusFilter: String,
    showStatusReasonField: Boolean,
    lifecycleReason: String,
    onSearchChange: (String) -> Unit,
    onStatusFilterChange: (String) -> Unit,
    onToggleReasonField: () -> Unit,
    onLifecycleReasonChange: (String) -> Unit,
    onSelectBusiness: (String) -> Unit,
    onScanOwnerQr: (String, String) -> Unit,
    onCreateOwnerPasskey: (String, String) -> Unit,
    onClearPasskey: () -> Unit,
    onStatusChange: (String, String, String?) -> Unit,
    onOpenSubscription: (AdminBusiness) -> Unit,
    onExtendTrial: (String) -> Unit,
    onActivatePaid: (String, Int) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(10.dp).size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Owner directory", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Manage each business owner, enrollment status, billing, and access from one place.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            businesses.count { it.ownerEmail != "Pending owner enrollment" }.toString(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("owners", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        // Search & Filter header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_search_businesses"),
                    singleLine = true,
                    placeholder = { Text("Search by name, owner email, or type…") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = if (searchQuery.isNotBlank()) {
                        {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    } else null,
                    shape = RoundedCornerShape(16.dp)
                )

                // Filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        "all" to "All (${businesses.size})",
                        "active" to "Active (${businesses.count { it.status == "active" }})",
                        "awaiting_owner" to "Awaiting Owner (${businesses.count { it.ownerEmail == "Pending owner enrollment" }})",
                        "clubs" to "Clubs (${businesses.count { it.businessType == "big_club" || it.businessType == "club" }})",
                        "liquor" to "Liquor Stores (${businesses.count { it.businessType == "counter_seller" || it.businessType == "liquor_store" }})",
                        "suspended" to "Suspended (${businesses.count { it.status == "suspended" }})",
                        "closed" to "Closed (${businesses.count { it.status == "closed" }})"
                    )

                    filters.forEach { (key, label) ->
                        FilterChip(
                            selected = statusFilter == key,
                            onClick = { onStatusFilterChange(key) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                // Status note toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredBusinesses.size} of ${businesses.size} businesses shown",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onToggleReasonField) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(if (showStatusReasonField) "Hide lifecycle note" else "Add lifecycle note")
                    }
                }

                AnimatedVisibility(visible = showStatusReasonField) {
                    OutlinedTextField(
                        value = lifecycleReason,
                        onValueChange = onLifecycleReasonChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Audit reason for suspend/close/reopen") },
                        placeholder = { Text("e.g., Requested by tenant, subscription renewal…") },
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
        }

        if (filteredBusinesses.isEmpty()) {
            item {
                ModernEmptyPortfolioState(
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
                ModernBusinessCard(
                    business = business,
                    isSelected = selectedBusinessId == business.shopId,
                    adminEnrollmentCredential = adminEnrollmentCredential,
                    onSelect = { onSelectBusiness(business.shopId) },
                    onScanOwnerQr = { role -> onScanOwnerQr(business.shopId, role) },
                    onCreateOwnerPasskey = { role -> onCreateOwnerPasskey(business.shopId, role) },
                    onClearPasskey = onClearPasskey,
                    onStatusChange = onStatusChange,
                    onOpenSubscription = onOpenSubscription,
                    onExtendTrial = onExtendTrial,
                    onActivatePaid = onActivatePaid
                )
            }
        }
    }
}

@Composable
private fun ModernBusinessCard(
    business: AdminBusiness,
    isSelected: Boolean,
    adminEnrollmentCredential: AdminEnrollmentCredential?,
    onSelect: () -> Unit,
    onScanOwnerQr: (String) -> Unit,
    onCreateOwnerPasskey: (String) -> Unit,
    onClearPasskey: () -> Unit,
    onStatusChange: (String, String, String?) -> Unit,
    onOpenSubscription: (AdminBusiness) -> Unit,
    onExtendTrial: (String) -> Unit,
    onActivatePaid: (String, Int) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var enrollmentRole by remember(business.shopId) { mutableStateOf("owner") }
    var roleMenuExpanded by remember(business.shopId) { mutableStateOf(false) }
    val isClub = business.businessType == "big_club" || business.businessType == "club"
    val isAwaitingOwner = business.ownerEmail == "Pending owner enrollment"
    val visibleCredential = adminEnrollmentCredential?.takeIf { it.businessId == business.shopId }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("card_business_${business.shopId}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Icon, Name, Type, Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isClub) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Icon(
                        imageVector = if (isClub) Icons.Default.TableRestaurant else Icons.Default.Storefront,
                        contentDescription = null,
                        tint = if (isClub) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = business.shopName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isClub) "Bar / Club (${business.businessSize})" else "Liquor Store",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("•", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = if (isClub) "${business.counterCount} counters" else "Direct POS",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusBadge(status = business.status)
                    SubscriptionStatusBadge(business = business)
                }
            }

            // Owner directory block: identity, enrollment state, and account age are kept together.
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isAwaitingOwner) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    1.dp,
                    if (isAwaitingOwner) MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isAwaitingOwner) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = if (isAwaitingOwner) "!" else business.ownerEmail.trim().firstOrNull()?.toString()?.uppercase() ?: "O",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isAwaitingOwner) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Business owner", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (isAwaitingOwner) "Owner enrollment pending" else business.ownerEmail.ifBlank { "Not configured" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isAwaitingOwner) "Scan QR or issue a passkey to activate access"
                            else "Tenant since ${business.createdAt.take(10)}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isAwaitingOwner) MaterialTheme.colorScheme.error.copy(alpha = 0.14f) else Color(0xFF00A349).copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (isAwaitingOwner) "ACTION NEEDED" else "CONNECTED",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isAwaitingOwner) MaterialTheme.colorScheme.error else Color(0xFF007A34)
                        )
                    }
                }
            }

            // Metrics row: Team, Products, Stock, Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatPill(
                    icon = Icons.Default.Groups,
                    value = (business.memberCount - 1).coerceAtLeast(0).toString(),
                    label = "Staff",
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    icon = Icons.Default.Inventory2,
                    value = business.productCount.toString(),
                    label = "Products",
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    icon = Icons.Default.Storefront,
                    value = business.stockUnits.toString(),
                    label = "Units",
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    icon = Icons.Default.TableRestaurant,
                    value = if (isClub) (if (business.counterInventoryMode == "per_counter") "Counter" else "Shared") else "Single",
                    label = "Stock",
                    modifier = Modifier.weight(1.1f)
                )
            }

            if (!business.lifecycleReason.isNullOrBlank()) {
                Text(
                    text = "Lifecycle Note: ${business.lifecycleReason}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Button(
                onClick = onSelect,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_manage_${business.shopId}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.KeyboardArrowUp else Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isSelected) "Close Controls" else "Enroll Owner / Manage Business", fontWeight = FontWeight.Bold, maxLines = 1)
            }

            // Expanded Management & Owner Enrollment section
            AnimatedVisibility(visible = isSelected) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                    .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box {
                        OutlinedButton(onClick = { roleMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Enroll as: ${enrollmentRole.replace('_', ' ').replaceFirstChar { it.uppercase() }}")
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = roleMenuExpanded, onDismissRequest = { roleMenuExpanded = false }) {
                            listOf("owner", "manager", "inventory_clerk", "cashier").forEach { role ->
                                DropdownMenuItem(
                                    text = { Text(role.replace('_', ' ').replaceFirstChar { it.uppercase() }) },
                                    onClick = { enrollmentRole = role; roleMenuExpanded = false }
                                )
                            }
                        }
                    }

                    // Scan the selected role's pending sign-in QR.
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Scan Owner's Phone QR",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                                    Text(
                                        text = "FASTEST",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            Text(
                                text = "Have the ${enrollmentRole.replace('_', ' ')} open 'Sign in with QR' on their phone, then scan that screen here to grant this role.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { onScanOwnerQr(enrollmentRole) },
                                enabled = business.status == "active",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_scan_owner_qr_${business.shopId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Scan ${enrollmentRole.replace('_', ' ').replaceFirstChar { it.uppercase() }} QR Now")
                            }
                        }
                    }

                    // Issue a role-specific, single-use passkey.
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Issue Single-Use ${enrollmentRole.replace('_', ' ').replaceFirstChar { it.uppercase() }} Passkey",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Text(
                                text = "Generates a 24-hour cryptographic passkey. The selected user enters it in the app's single sign-in field.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { onCreateOwnerPasskey(enrollmentRole) },
                                enabled = business.status == "active",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_issue_passkey_${business.shopId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Generate 24h ${enrollmentRole.replace('_', ' ').replaceFirstChar { it.uppercase() }} Passkey")
                            }

                            // Passkey presentation
                            if (visibleCredential != null && visibleCredential.passkey.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "ACTIVE ${visibleCredential.role.replace('_', ' ').uppercase()} PASSKEY (SHARE SECURELY)",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 0.6.sp
                                        )
                                        Text(
                                            text = visibleCredential.passkey,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(visibleCredential.passkey))
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(Modifier.width(5.dp))
                                                Text("Copy")
                                            }
                                            TextButton(
                                                onClick = onClearPasskey,
                                                modifier = Modifier.weight(0.7f)
                                            ) {
                                                Text("Dismiss")
                                            }
                                        }
                                        Text(
                                            text = "Valid for ${visibleCredential.expiresInHours}h. One-time use.",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // SaaS Subscription & Daraja Billing Management
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CreditCard,
                                        contentDescription = null,
                                        tint = Color(0xFF00A349),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "SaaS Subscription & Daraja",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "KSh ${business.subscriptionPriceKsh}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF00A349)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Status: ${business.subscriptionStatus.uppercase()}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (!business.subscriptionEndsAt.isNullOrBlank()) "Paid until: ${business.subscriptionEndsAt.take(10)}"
                                    else if (!business.trialEndsAt.isNullOrBlank()) "Trial until: ${business.trialEndsAt.take(10)}"
                                    else "Expired",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!business.customBillingNote.isNullOrBlank()) {
                                Text(
                                    text = "Billing note: ${business.customBillingNote}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onExtendTrial(business.shopId) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("+14d Trial", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { onOpenSubscription(business) },
                                    modifier = Modifier.weight(1.3f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A349)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Manage Billing", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Status Lifecycle controls
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Tenant Lifecycle State",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (business.status != "active") {
                                    Button(
                                        onClick = {
                                            onStatusChange(
                                                business.shopId,
                                                "active",
                                                "Reopened by platform administrator"
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Reopen")
                                    }
                                }
                                if (business.status == "active") {
                                    OutlinedButton(
                                        onClick = {
                                            onStatusChange(
                                                business.shopId,
                                                "suspended",
                                                "Suspended by platform administrator"
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Pause,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Suspend")
                                    }
                                }
                                if (business.status != "closed") {
                                    OutlinedButton(
                                        onClick = {
                                            onStatusChange(
                                                business.shopId,
                                                "closed",
                                                "Closed by platform administrator"
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Close")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (bgColor, fgColor) = when (status.lowercase()) {
        "active" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "suspended" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = CircleShape,
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(fgColor, CircleShape)
            )
            Text(
                text = status.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = fgColor,
                letterSpacing = 0.4.sp
            )
        }
    }
}

@Composable
private fun RegisterTenantContent(
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
    val ownerPhoneValid = com.alvinb.counterpos.data.SubscriptionManager.normalizeKenyanPhone(ownerPhone) != null
    val counterCountValid = !isClub || (counterCount.toIntOrNull()?.let { it in 1..50 } == true)
    val canSubmit = businessName.trim().length in 2..120 && ownerEmailValid && ownerPhoneValid && counterCountValid && !isCreating

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Hero introduction
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddBusiness,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Register New Client Tenant",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Provision a secure multi-tenant environment with tailored POS & inventory architecture.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Section 1: Business Identity
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "1. Business Identity",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = onBusinessNameChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_register_name"),
                        label = { Text("Business / Bar Name") },
                        placeholder = { Text("e.g. Euphoria Lounge & Grill") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = ownerEmail,
                        onValueChange = onOwnerEmailChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_register_owner_email"),
                        label = { Text("Owner Email (Optional)") },
                        placeholder = { Text("e.g. owner@example.com") },
                        isError = !ownerEmailValid,
                        supportingText = {
                            Text(
                                if (ownerEmailValid) "If left blank, you can scan the owner's phone QR or issue a passkey right after creation."
                                else "Please enter a valid email address."
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = ownerPhone,
                        onValueChange = onOwnerPhoneChange,
                        modifier = Modifier.fillMaxWidth().testTag("input_register_owner_phone"),
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
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
        }

        // Section 2: Model Selection
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "2. Business Model & Floor Plan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Club Card
                        Card(
                            onClick = { onBusinessTypeChange("big_club") },
                            modifier = Modifier
                                .weight(1f)
                                .height(140.dp)
                                .testTag("btn_select_type_club"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isClub) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            border = BorderStroke(
                                if (isClub) 2.dp else 1.dp,
                                if (isClub) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TableRestaurant,
                                        contentDescription = null,
                                        tint = if (isClub) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    if (isClub) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "Bar & Club",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isClub) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Tables & Floors ON",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isClub) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Multi-counter waiter flow",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Liquor Store Card
                        Card(
                            onClick = { onBusinessTypeChange("counter_seller") },
                            modifier = Modifier
                                .weight(1f)
                                .height(140.dp)
                                .testTag("btn_select_type_liquor"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isLiquorStore) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            border = BorderStroke(
                                if (isLiquorStore) 2.dp else 1.dp,
                                if (isLiquorStore) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storefront,
                                        contentDescription = null,
                                        tint = if (isLiquorStore) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    if (isLiquorStore) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "Liquor Store",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isLiquorStore) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Tables OFF",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isLiquorStore) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Direct retail checkout",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Club Hardware & Counters (if club)
        if (isClub) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "3. Service Counters & Inventory Architecture",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text("Club Scale", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("small" to "Small Club", "medium" to "Medium Club", "large" to "Large Club").forEach { (value, label) ->
                                FilterChip(
                                    selected = businessSize == value,
                                    onClick = { onBusinessSizeChange(value) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Text("Stock Balance Strategy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = inventoryMode == "shared",
                                onClick = { onInventoryModeChange("shared") },
                                label = { Text("Shared Central Stock") },
                                modifier = Modifier.weight(1f),
                                leadingIcon = if (inventoryMode == "shared") ({ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }) else null
                            )
                            FilterChip(
                                selected = inventoryMode == "per_counter",
                                onClick = { onInventoryModeChange("per_counter") },
                                label = { Text("Per-Counter Isolated") },
                                modifier = Modifier.weight(1f),
                                leadingIcon = if (inventoryMode == "per_counter") ({ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }) else null
                            )
                        }

                        Text(
                            text = if (inventoryMode == "shared") "One common inventory pool is dispensed across all stations."
                            else "Each service bar keeps its own inventory balance with stock transfer controls.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Counter count stepper
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Number of Service Counters", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Independent checkout/dispense stations (1–50)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val currentCount = counterCount.toIntOrNull() ?: 1
                                IconButton(
                                    onClick = {
                                        if (currentCount > 1) onCounterCountChange((currentCount - 1).toString())
                                    },
                                    enabled = currentCount > 1
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.width(44.dp)
                                ) {
                                    Text(
                                        text = currentCount.toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (currentCount < 50) onCounterCountChange((currentCount + 1).toString())
                                    },
                                    enabled = currentCount < 50
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Submit Button
        item {
            Button(
                onClick = onCreate,
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_create_business_profile"),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Initializing Tenant Environment…")
                } else {
                    Icon(
                        imageVector = Icons.Default.AddBusiness,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Create Tenant & Open Onboarding", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AuditTrailContent(
    auditEvents: List<AdminAuditEvent>,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                    Text(
                        text = "Platform Audit & Security Stream",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Real-time ledger of administrative and authentication events",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh audit logs")
                    }
                }
            }
        }

        if (auditEvents.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (isLoading) "Loading audit trail…" else "No audit events recorded yet",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Actions such as tenant registration, passkey generation, status changes, and logins appear here automatically.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(auditEvents) { event ->
                ModernAuditItemCard(event = event)
            }
        }
    }
}

@Composable
private fun ModernAuditItemCard(event: AdminAuditEvent) {
    val isAuthAction = event.action.contains("passkey", ignoreCase = true) || event.action.contains("login", ignoreCase = true) || event.action.contains("enroll", ignoreCase = true)
    val isStatusAction = event.action.contains("status", ignoreCase = true) || event.action.contains("suspend", ignoreCase = true)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = when {
                    isAuthAction -> MaterialTheme.colorScheme.primaryContainer
                    isStatusAction -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Icon(
                    imageVector = when {
                        isAuthAction -> Icons.Default.Key
                        isStatusAction -> Icons.Default.Pause
                        else -> Icons.Default.Business
                    },
                    contentDescription = null,
                    tint = when {
                        isAuthAction -> MaterialTheme.colorScheme.onPrimaryContainer
                        isStatusAction -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
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
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = event.createdAt.take(19).replace('T', ' '),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = event.entityType.uppercase(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!event.actorUserId.isNullOrBlank()) {
                        Text(
                            text = "Actor: ${event.actorUserId.take(12)}…",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (event.metadata.isNotBlank() && event.metadata != "{}") {
                    Text(
                        text = event.metadata,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernMessageBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    val bgColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val fgColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.Close else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                color = fgColor,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = fgColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun ModernEmptyPortfolioState(
    hasAny: Boolean,
    onClearSearch: () -> Unit,
    onRegisterNew: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (hasAny) Icons.Default.Search else Icons.Default.AddBusiness,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = if (hasAny) "No matching businesses" else "No client businesses yet",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = if (hasAny) "Try adjusting your search query or status filter."
                else "Provision your first client business to start managing your POS portfolio.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (hasAny) {
                OutlinedButton(onClick = onClearSearch) {
                    Text("Clear Filters")
                }
            } else {
                Button(onClick = onRegisterNew) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Register First Business")
                }
            }
        }
    }
}

@Composable
private fun SubscriptionStatusBadge(business: AdminBusiness) {
    val subStatus = business.subscriptionStatus.lowercase()
    val subColor = when (subStatus) {
        "active" -> Color(0xFF00A349)
        "trial" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = subColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, subColor.copy(alpha = 0.35f))
    ) {
        Text(
            text = "SaaS: ${subStatus.uppercase()}",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = subColor
        )
    }
}

@Composable
private fun AdminSubscriptionDialog(
    business: AdminBusiness,
    isUpdating: Boolean,
    onDismiss: () -> Unit,
    onSave: (price: Int?, endsAt: String?, status: String?, planDays: Int?, trialEndsAt: String?, note: String?) -> Unit,
    onExtendTrial: () -> Unit,
    onActivatePaid: (days: Int) -> Unit,
    onSetPaymentLock: (locked: Boolean, amountKsh: Int?, reason: String?) -> Unit
) {
    var priceText by remember(business.subscriptionPriceKsh) {
        mutableStateOf(business.subscriptionPriceKsh.toString())
    }
    var planDaysText by remember(business.subscriptionPlanDays) {
        mutableStateOf(business.subscriptionPlanDays.toString())
    }
    var selectedStatus by remember(business.subscriptionStatus) {
        mutableStateOf(business.subscriptionStatus)
    }
    var noteText by remember(business.customBillingNote) {
        mutableStateOf(business.customBillingNote.orEmpty())
    }
    var customEndsAt by remember(business.subscriptionEndsAt) {
        mutableStateOf(business.subscriptionEndsAt.orEmpty())
    }
    var lockAmountText by remember(business.paymentLockAmountKsh, business.subscriptionPriceKsh) {
        mutableStateOf((if (business.paymentLockAmountKsh > 0) business.paymentLockAmountKsh else business.subscriptionPriceKsh).toString())
    }
    var lockReasonText by remember(business.paymentLockReason) { mutableStateOf(business.paymentLockReason.orEmpty()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Manage Subscription",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = business.shopName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider()

                // Quick Action Buttons
                Text(
                    text = "Quick Entitlement Extensions:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExtendTrial,
                        enabled = !isUpdating,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("+14d Trial", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { onActivatePaid(30) },
                        enabled = !isUpdating,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("+30d Paid", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { onActivatePaid(90) },
                        enabled = !isUpdating,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("+90d Paid", fontSize = 11.sp)
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Platform Payment Lock",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (business.paymentLockActive) "This business is currently locked on every owner and employee device."
                    else "Lock all POS functions and require the owner to pay a specific amount.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!business.paymentLockActive) {
                    OutlinedTextField(
                        value = lockAmountText,
                        onValueChange = { lockAmountText = it.filter(Char::isDigit) },
                        label = { Text("Amount due (KES)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = lockReasonText,
                        onValueChange = { lockReasonText = it.take(240) },
                        label = { Text("Reason / instruction (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Button(
                        onClick = {
                            onSetPaymentLock(true, lockAmountText.toIntOrNull(), lockReasonText.trim().ifBlank { null })
                        },
                        enabled = !isUpdating && (lockAmountText.toIntOrNull() ?: 0) > 0,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Start Payment Lock") }
                } else {
                    Button(
                        onClick = { onSetPaymentLock(false, null, null) },
                        enabled = !isUpdating,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A349))
                    ) { Text("Clear Payment Lock") }
                }

                HorizontalDivider()

                // Custom Price (KSh)
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter(Char::isDigit) },
                    label = { Text("Renewal Price (KES)") },
                    placeholder = { Text("2500") },
                    leadingIcon = {
                        Text("KSh", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Plan Duration (Days)
                OutlinedTextField(
                    value = planDaysText,
                    onValueChange = { planDaysText = it.filter(Char::isDigit) },
                    label = { Text("Plan Duration (Days)") },
                    placeholder = { Text("30") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Status Selector (trial, active, expired)
                Column {
                    Text(
                        text = "Subscription Status:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("trial", "active", "expired").forEach { st ->
                            FilterChip(
                                selected = selectedStatus == st,
                                onClick = { selectedStatus = st },
                                label = { Text(st.replaceFirstChar { it.uppercase() }) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Custom End Date (ISO string or timestamp)
                OutlinedTextField(
                    value = customEndsAt,
                    onValueChange = { customEndsAt = it },
                    label = { Text("Subscription Expiry (ISO UTC or Blank)") },
                    placeholder = { Text("2026-12-31T23:59:59Z") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Custom Billing Note
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Billing Notes / Arrangement") },
                    placeholder = { Text("e.g. Special merchant partner rate") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Last Payment History Info if present
                if (!business.lastPaymentReceipt.isNullOrBlank() || !business.lastPaymentAt.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Last Recorded Payment:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Receipt: ${business.lastPaymentReceipt ?: "N/A"}",
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Phone: ${business.lastPaymentPhone ?: "N/A"}",
                                fontSize = 11.sp
                            )
                            if (!business.lastPaymentAt.isNullOrBlank()) {
                                Text(
                                    text = "Date: ${business.lastPaymentAt.take(19).replace('T', ' ')}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val priceInt = priceText.toIntOrNull()
                            val daysInt = planDaysText.toIntOrNull()
                            val endsIso = customEndsAt.trim().takeIf(String::isNotBlank)
                            val note = noteText.trim().takeIf(String::isNotBlank)
                            onSave(priceInt, endsIso, selectedStatus, daysInt, null, note)
                        },
                        enabled = !isUpdating,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun FaithfulSuperadminTopBar(
    email: String,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    Surface(color = Color(0xFFEEF2F0)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Counter POS", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF13211E))
                Text(email, fontSize = 11.sp, color = Color(0xFF5F6F6A), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF13211E)) }
            IconButton(onClick = onSignOut, modifier = Modifier.size(36.dp)) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out", tint = Color(0xFF13211E)) }
        }
    }
}

@Composable
private fun FaithfulSuperadminBottomBar(
    selectedTab: SuperadminTab,
    onTabSelected: (SuperadminTab) -> Unit
) {
    Surface(color = Color(0xFFFFFFFF), tonalElevation = 0.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SuperadminTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                TextButton(onClick = { onTabSelected(tab) }, modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(tab.icon, contentDescription = null, tint = if (selected) Color(0xFF0E5B53) else Color(0xFF5F6F6A), modifier = Modifier.size(18.dp))
                        Text(
                            when (tab) {
                                SuperadminTab.PORTFOLIO -> "Businesses"
                                SuperadminTab.REGISTER -> "Register"
                                SuperadminTab.AUDIT -> "Activity"
                            },
                            fontSize = 11.sp,
                            color = if (selected) Color(0xFF0E5B53) else Color(0xFF5F6F6A),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FaithfulPortfolioContent(
    modifier: Modifier,
    businesses: List<AdminBusiness>,
    filteredBusinesses: List<AdminBusiness>,
    searchQuery: String,
    statusFilter: String,
    selectedBusinessId: String?,
    onSearchChange: (String) -> Unit,
    onStatusFilterChange: (String) -> Unit,
    onSelectBusiness: (String) -> Unit,
    onScanOwnerQr: (String) -> Unit,
    onCreateOwnerPasskey: (String) -> Unit,
    onOpenSubscription: (AdminBusiness) -> Unit,
    onStatusChange: (String, String, String?) -> Unit,
    onNavigateToRegister: () -> Unit,
    error: String?,
    notice: String?,
    onClearMessage: () -> Unit
) {
    val needsAttention = businesses.filter { it.paymentLockActive || it.ownerEmail == "Pending owner enrollment" || it.status == "suspended" || it.subscriptionStatus == "expired" }
    val statusMix = listOf(
        "Paid" to businesses.count { it.subscriptionStatus == "active" && !it.paymentLockActive },
        "Trial" to businesses.count { it.subscriptionStatus == "trial" && !it.paymentLockActive },
        "Locked" to businesses.count { it.paymentLockActive },
        "Suspended" to businesses.count { it.status == "suspended" }
    )
    val filters = listOf(
        "all" to "All",
        "awaiting_owner" to "Needs owner",
        "locked" to "Locked",
        "trial" to "Trial",
        "clubs" to "Bars",
        "liquor" to "Liquor stores",
        "suspended" to "Suspended"
    )
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Surface(
                color = Color(0xFF0E5B53),
                contentColor = Color.White,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(businesses.size.toString(), fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                        Text(" businesses on the platform", fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, bottom = 5.dp), color = Color.White.copy(alpha = .85f))
                    }
                    Row(Modifier.fillMaxWidth().height(12.dp).padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        statusMix.forEach { (label, count) ->
                            if (count > 0) Box(Modifier.weight(count.toFloat()).fillMaxSize().clip(RoundedCornerShape(6.dp)).background(when (label) { "Paid" -> Color(0xFF6FD59A); "Trial" -> Color(0xFF9DB4FF); "Locked" -> Color(0xFFFF8F85); else -> Color(0xFFF0B04A) }))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        statusMix.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { (label, count) ->
                                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(8.dp).clip(CircleShape).background(when (label) { "Paid" -> Color(0xFF6FD59A); "Trial" -> Color(0xFF9DB4FF); "Locked" -> Color(0xFFFF8F85); else -> Color(0xFFF0B04A) }))
                                        Text(label, fontSize = 11.sp, modifier = Modifier.padding(start = 5.dp))
                                        Spacer(Modifier.weight(1f))
                                        Text(count.toString(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!error.isNullOrBlank()) item { FaithfulNotice(error, true, onClearMessage) }
        if (!notice.isNullOrBlank()) item { FaithfulNotice(notice, false, onClearMessage) }
        if (needsAttention.isNotEmpty()) {
            item { Text("Needs you · ${needsAttention.size}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF13211E), modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)) }
            items(needsAttention.take(4), key = { "need_${it.shopId}" }) { business ->
                FaithfulNeedRow(business, onOpenSubscription, onScanOwnerQr)
            }
        }
        item { Text("All businesses", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 1.dp)) }
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search name or owner", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp)
            )
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                filters.forEach { (key, label) ->
                    FilterChip(
                        selected = statusFilter == key,
                        onClick = { onStatusFilterChange(key) },
                        label = { Text("$label ${filterCount(businesses, key)}", fontSize = 11.sp, maxLines = 1) },
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
            }
        }
        item { Text("${filteredBusinesses.size} businesses shown", color = Color(0xFF5F6F6A), fontSize = 11.sp, modifier = Modifier.padding(vertical = 3.dp)) }
        if (filteredBusinesses.isEmpty()) {
            item { Text("No businesses match. Clear the search or choose another filter.", color = Color(0xFF5F6F6A), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
        } else {
            items(filteredBusinesses, key = { it.shopId }) { business ->
                FaithfulBusinessRow(
                    business = business,
                    isOpen = selectedBusinessId == business.shopId,
                    onOpen = { onSelectBusiness(business.shopId) },
                    onScanOwnerQr = { onScanOwnerQr(business.shopId) },
                    onCreateOwnerPasskey = { onCreateOwnerPasskey(business.shopId) },
                    onOpenSubscription = { onOpenSubscription(business) },
                    onStatusChange = { status -> onStatusChange(business.shopId, status, "Status changed from superadmin dashboard") }
                )
            }
        }
        item { OutlinedButton(onClick = onNavigateToRegister, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AddBusiness, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Register new business") } }
    }
}

private fun filterCount(businesses: List<AdminBusiness>, key: String): Int = businesses.count {
    when (key) {
        "all" -> true
        "awaiting_owner" -> it.ownerEmail == "Pending owner enrollment"
        "locked" -> it.paymentLockActive
        "trial" -> it.subscriptionStatus == "trial"
        "clubs" -> it.businessType == "big_club" || it.businessType == "club"
        "liquor" -> it.businessType == "counter_seller" || it.businessType == "liquor_store"
        "suspended" -> it.status == "suspended"
        else -> true
    }
}

@Composable
private fun FaithfulNotice(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Surface(color = if (isError) Color(0xFFFBE0DC) else Color(0xFFE0E7FB), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("×") }
        }
    }
}

@Composable
private fun FaithfulNeedRow(business: AdminBusiness, onOpenSubscription: (AdminBusiness) -> Unit, onScanOwnerQr: (String) -> Unit) {
    val isLocked = business.paymentLockActive
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFFD3DDD9)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(business.shopName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(if (isLocked) "Payment locked · ${business.paymentLockReason ?: "Payment required"}" else if (business.ownerEmail == "Pending owner enrollment") "Owner hasn’t signed in yet" else "Suspended or subscription expired", fontSize = 11.sp, color = Color(0xFF5F6F6A), maxLines = 2)
            }
            Button(onClick = { if (isLocked) onOpenSubscription(business) else onScanOwnerQr(business.shopId) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp), shape = RoundedCornerShape(10.dp)) {
                Text(if (isLocked) "Review" else if (business.ownerEmail == "Pending owner enrollment") "Enroll" else "Renew", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun FaithfulBusinessRow(
    business: AdminBusiness,
    isOpen: Boolean,
    onOpen: () -> Unit,
    onScanOwnerQr: () -> Unit,
    onCreateOwnerPasskey: () -> Unit,
    onOpenSubscription: () -> Unit,
    onStatusChange: (String) -> Unit
) {
    val isClub = business.businessType == "big_club" || business.businessType == "club"
    val statusText = when {
        business.paymentLockActive -> "Locked" to Color(0xFFB3261E)
        business.status == "suspended" -> "Suspended" to Color(0xFFB86E00)
        business.subscriptionStatus == "trial" -> "Trial" to Color(0xFF3B5BB5)
        else -> "Paid" to Color(0xFF17803F)
    }
    Surface(color = Color.White, border = BorderStroke(1.dp, Color(0xFFD3DDD9)), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            TextButton(onClick = onOpen, contentPadding = PaddingValues(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = Color(0xFFE4EBE8), shape = RoundedCornerShape(10.dp)) { Text(business.shopName.take(1), modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text(business.shopName, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (business.ownerEmail == "Pending owner enrollment") "No owner yet" else business.ownerEmail, fontSize = 11.sp, color = Color(0xFF5F6F6A), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Surface(color = statusText.second.copy(alpha = .14f), shape = RoundedCornerShape(99.dp)) { Text(statusText.first, color = statusText.second, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                }
            }
            AnimatedVisibility(isOpen) {
                Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        FaithfulFact(business.memberCount.coerceAtLeast(1).minus(1).toString(), "Staff")
                        FaithfulFact(business.productCount.toString(), "Products")
                        FaithfulFact(business.stockUnits.toString(), "Units")
                        FaithfulFact("KSh ${business.paymentLockAmountKsh.takeIf { it > 0 } ?: business.subscriptionPriceKsh}", "${business.subscriptionPlanDays}d")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = onScanOwnerQr, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) { Text("Scan QR", fontSize = 10.sp) }
                        OutlinedButton(onClick = onCreateOwnerPasskey, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) { Text("Passkey", fontSize = 10.sp) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onOpenSubscription, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) { Text("Billing", fontSize = 10.sp) }
                        OutlinedButton(onClick = { onStatusChange(if (business.status == "suspended") "active" else "suspended") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) { Text(if (business.status == "suspended") "Reopen" else "Suspend", fontSize = 10.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaithfulFact(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(label, color = Color(0xFF5F6F6A), fontSize = 9.sp) }
}
