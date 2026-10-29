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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
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
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ModernPlatformTopBar(
                email = email,
                onRefresh = {
                    viewModel.loadAdminBusinesses()
                    viewModel.loadAdminAuditEvents()
                },
                onSignOut = onSignOut
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Metrics strip
            SuperadminMetricsStrip(
                totalCount = businesses.size,
                liveCount = liveCount,
                pendingOwnerCount = pendingOwnerCount,
                clubCount = clubCount,
                liquorCount = liquorCount,
                onMetricClick = { filter ->
                    statusFilter = filter
                    selectedTab = SuperadminTab.PORTFOLIO
                }
            )

            // Notifications & Errors
            if (!error.isNullOrBlank()) {
                ModernMessageBanner(message = error, isError = true, onDismiss = onClearError)
            }
            if (!state.notice.isNullOrBlank()) {
                ModernMessageBanner(message = state.notice.orEmpty(), isError = false, onDismiss = onClearError)
            }

            // Tab bar
            ModernSuperadminTabs(
                selectedTab = selectedTab,
                portfolioCount = filteredBusinesses.size,
                auditCount = state.adminAuditEvents.size,
                onTabSelected = { selectedTab = it }
            )

            // Tab contents
            when (selectedTab) {
                SuperadminTab.PORTFOLIO -> {
                    PortfolioContent(
                        businesses = businesses,
                        filteredBusinesses = filteredBusinesses,
                        selectedBusinessId = state.adminSelectedBusinessId,
                        adminEnrollmentCredential = state.adminEnrollmentCredential,
                        searchQuery = searchQuery,
                        statusFilter = statusFilter,
                        showStatusReasonField = showStatusReasonField,
                        lifecycleReason = lifecycleReason,
                        onSearchChange = { searchQuery = it },
                        onStatusFilterChange = { statusFilter = it },
                        onToggleReasonField = { showStatusReasonField = !showStatusReasonField },
                        onLifecycleReasonChange = { lifecycleReason = it },
                        onSelectBusiness = { viewModel.selectAdminBusiness(it) },
                        onScanOwnerQr = { shopId -> viewModel.openAdminEnrollmentScanner(shopId, "owner") },
                        onCreateOwnerPasskey = { shopId -> viewModel.createAdminEnrollmentPass(shopId, "owner") },
                        onClearPasskey = viewModel::clearAdminEnrollmentCredential,
                        onStatusChange = { shopId, status, fallback ->
                            val reason = lifecycleReason.trim().ifBlank { fallback }
                            viewModel.setBusinessStatus(shopId, status, reason)
                        },
                        onNavigateToRegister = { selectedTab = SuperadminTab.REGISTER }
                    )
                }

                SuperadminTab.REGISTER -> {
                    RegisterTenantContent(
                        businessName = regBusinessName,
                        ownerEmail = regOwnerEmail,
                        businessType = regBusinessType,
                        businessSize = regBusinessSize,
                        inventoryMode = regInventoryMode,
                        counterCount = regCounterCount,
                        isCreating = state.isCreatingAdminBusiness,
                        onBusinessNameChange = { regBusinessName = it; onClearError() },
                        onOwnerEmailChange = { regOwnerEmail = it; onClearError() },
                        onBusinessTypeChange = { type ->
                            regBusinessType = type
                            if (type == "counter_seller") {
                                regBusinessSize = "medium"
                                regInventoryMode = "shared"
                                regCounterCount = "1"
                            }
                        },
                        onBusinessSizeChange = { regBusinessSize = it },
                        onInventoryModeChange = { regInventoryMode = it },
                        onCounterCountChange = { regCounterCount = it.filter(Char::isDigit).take(2) },
                        onCreate = {
                            viewModel.createBusinessForOwner(
                                name = regBusinessName,
                                ownerEmail = regOwnerEmail,
                                businessType = regBusinessType,
                                businessSize = regBusinessSize,
                                inventoryMode = regInventoryMode,
                                counterCount = if (regBusinessType == "big_club") regCounterCount.toIntOrNull() ?: 1 else 1,
                                onCreated = {
                                    regBusinessName = ""
                                    regOwnerEmail = ""
                                    regCounterCount = "1"
                                    selectedTab = SuperadminTab.PORTFOLIO
                                }
                            )
                        }
                    )
                }

                SuperadminTab.AUDIT -> {
                    AuditTrailContent(
                        auditEvents = state.adminAuditEvents,
                        isLoading = state.isLoadingAuditEvents,
                        onRefresh = { viewModel.loadAdminAuditEvents() }
                    )
                }
            }
        }
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
    onScanOwnerQr: (String) -> Unit,
    onCreateOwnerPasskey: (String) -> Unit,
    onClearPasskey: () -> Unit,
    onStatusChange: (String, String, String?) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                    onScanOwnerQr = { onScanOwnerQr(business.shopId) },
                    onCreateOwnerPasskey = { onCreateOwnerPasskey(business.shopId) },
                    onClearPasskey = onClearPasskey,
                    onStatusChange = onStatusChange
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
    onScanOwnerQr: () -> Unit,
    onCreateOwnerPasskey: () -> Unit,
    onClearPasskey: () -> Unit,
    onStatusChange: (String, String, String?) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
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

                StatusBadge(status = business.status)
            }

            // Owner banner / status indicator
            if (isAwaitingOwner) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Owner enrollment pending — scan owner QR or generate passkey below",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Owner:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = business.ownerEmail.ifBlank { "Not configured" },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Created ${business.createdAt.take(10)}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
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
                    // Method 1: Scan owner's QR directly
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
                                text = "Have the owner open 'Scan QR' on their phone login screen, then scan their screen here to immediately grant Owner access.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onScanOwnerQr,
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
                                Text("Scan Owner QR Now")
                            }
                        }
                    }

                    // Method 2: Create Owner Passkey
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
                                    text = "Issue Single-Use Owner Passkey",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Text(
                                text = "Generates a 24-hour cryptographic passkey. The owner can paste it into the app login screen to take ownership.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onCreateOwnerPasskey,
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
                                Text("Generate 24h Owner Passkey")
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
                                            text = "ACTIVE OWNER PASSKEY (SHARE SECURELY)",
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
    businessType: String,
    businessSize: String,
    inventoryMode: String,
    counterCount: String,
    isCreating: Boolean,
    onBusinessNameChange: (String) -> Unit,
    onOwnerEmailChange: (String) -> Unit,
    onBusinessTypeChange: (String) -> Unit,
    onBusinessSizeChange: (String) -> Unit,
    onInventoryModeChange: (String) -> Unit,
    onCounterCountChange: (String) -> Unit,
    onCreate: () -> Unit
) {
    val isClub = businessType == "big_club" || businessType == "club"
    val isLiquorStore = businessType == "counter_seller" || businessType == "liquor_store"
    val ownerEmailValid = ownerEmail.isBlank() || (ownerEmail.trim().contains("@") && ownerEmail.none(Char::isWhitespace))
    val counterCountValid = !isClub || (counterCount.toIntOrNull()?.let { it in 1..50 } == true)
    val canSubmit = businessName.trim().length in 2..120 && ownerEmailValid && counterCountValid && !isCreating

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
