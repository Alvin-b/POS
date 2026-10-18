package com.alvinb.counterpos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvinb.counterpos.data.remote.AdminBusiness
import com.alvinb.counterpos.ui.scanner.BarcodeScannerScreen
import com.alvinb.counterpos.ui.scanner.ScannerMode

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
    var businessName by remember { mutableStateOf("") }
    var ownerEmail by remember { mutableStateOf("") }
    var inventoryMode by remember { mutableStateOf("shared") }
    var counterCount by remember { mutableStateOf("1") }
    var lifecycleReason by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }
    var showBusinessForm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadAdminBusinesses() }

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

    val activeCount = businesses.count { it.status == "active" }
    val pendingOwnerCount = businesses.count { it.ownerEmail == "Pending owner enrollment" }
    val filteredBusinesses = remember(businesses, searchQuery, statusFilter) {
        businesses.filter { business ->
            val matchesStatus = statusFilter == "all" || business.status == statusFilter
            val query = searchQuery.trim()
            val matchesQuery = query.isBlank() || business.shopName.contains(query, ignoreCase = true) ||
                business.ownerEmail.contains(query, ignoreCase = true) || business.businessType.contains(query, ignoreCase = true)
            matchesStatus && matchesQuery
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
            contentPadding = PaddingValues(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                DashboardHeader(email = email, onSignOut = onSignOut)
            }
            item {
                DashboardHero()
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    MetricCard("Businesses", businesses.size.toString(), Icons.Default.Storefront, Modifier.weight(1f))
                    MetricCard("Active", activeCount.toString(), Icons.Default.CheckCircle, Modifier.weight(1f))
                    MetricCard("Owner setup", pendingOwnerCount.toString(), Icons.Default.Groups, Modifier.weight(1f))
                }
            }
            if (!error.isNullOrBlank()) {
                item { MessageBanner(error, isError = true) }
            }
            if (!state.notice.isNullOrBlank()) {
                item { MessageBanner(state.notice.orEmpty(), isError = false) }
            }
            item {
                RegisterBusinessCard(
                    expanded = showBusinessForm,
                    businessName = businessName,
                    ownerEmail = ownerEmail,
                    inventoryMode = inventoryMode,
                    counterCount = counterCount,
                    onExpandedChange = { showBusinessForm = it },
                    onBusinessNameChange = { businessName = it; onClearError() },
                    onOwnerEmailChange = { ownerEmail = it; onClearError() },
                    onInventoryModeChange = { inventoryMode = it },
                    onCounterCountChange = { counterCount = it.filter(Char::isDigit).take(2) },
                    onScanBusinessQr = { viewModel.openScanner(ScannerMode.ADMIN_QR) },
                    onCreateBusiness = {
                        viewModel.createBusinessForOwner(
                            businessName,
                            ownerEmail,
                            inventoryMode,
                            counterCount.toIntOrNull()?.coerceIn(1, 50) ?: 1
                        )
                        businessName = ""
                        ownerEmail = ""
                        showBusinessForm = false
                    }
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Business portfolio", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("${filteredBusinesses.size} of ${businesses.size} businesses", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search businesses") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (searchQuery.isNotBlank()) {
                            { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear search") } }
                        } else null
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("all", "active", "suspended", "closed").forEach { filter ->
                            val count = when (filter) {
                                "active" -> businesses.count { it.status == "active" }
                                "suspended" -> businesses.count { it.status == "suspended" }
                                "closed" -> businesses.count { it.status == "closed" }
                                else -> businesses.size
                            }
                            FilterChip(
                                selected = statusFilter == filter,
                                onClick = { statusFilter = filter },
                                label = { Text("${filter.replaceFirstChar { it.uppercase() }} · $count") }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = lifecycleReason,
                        onValueChange = { lifecycleReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Reason for suspend / close (optional)") },
                        supportingText = { Text("This note is attached to business lifecycle changes.") }
                    )
                }
            }
            if (filteredBusinesses.isEmpty()) {
                item {
                    EmptyPortfolioState(
                        hasBusinesses = businesses.isNotEmpty(),
                        onClearSearch = { searchQuery = ""; statusFilter = "all" }
                    )
                }
            } else {
                items(filteredBusinesses, key = { it.shopId }) { business ->
                    PortfolioBusinessCard(
                        business = business,
                        selected = state.adminSelectedBusinessId == business.shopId,
                        onSelect = { viewModel.selectAdminBusiness(business.shopId) },
                        onScanPhone = { role -> viewModel.openAdminEnrollmentScanner(business.shopId, role) },
                        onStatusChange = { shopId, status, fallback ->
                            val reason = lifecycleReason.trim().ifBlank { fallback }
                            viewModel.setBusinessStatus(shopId, status, reason)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(email: String, onSignOut: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                Icons.Default.AdminPanelSettings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(11.dp).size(25.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text("CounterPOS", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("Platform operations", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(email, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onSignOut) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
        }
    }
}

@Composable
private fun DashboardHero() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
                    Text(
                        "SUPERADMIN · SECURE CONTROL",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        letterSpacing = 0.5.sp
                    )
                }
                Text("Run your client network", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Business access, enrollment and lifecycle controls in one place.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f))
            }
            Icon(
                Icons.Default.Business,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                modifier = Modifier.size(42.dp)
            )
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 11.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Column {
                Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 22.sp)
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MessageBanner(message: String, isError: Boolean) {
    val background = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val foreground = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(shape = RoundedCornerShape(14.dp), color = background, modifier = Modifier.fillMaxWidth()) {
        Text(message, color = foreground, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
    }
}

@Composable
private fun RegisterBusinessCard(
    expanded: Boolean,
    businessName: String,
    ownerEmail: String,
    inventoryMode: String,
    counterCount: String,
    onExpandedChange: (Boolean) -> Unit,
    onBusinessNameChange: (String) -> Unit,
    onOwnerEmailChange: (String) -> Unit,
    onInventoryModeChange: (String) -> Unit,
    onCounterCountChange: (String) -> Unit,
    onScanBusinessQr: () -> Unit,
    onCreateBusiness: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Default.AddBusiness, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(9.dp).size(21.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Add a client business", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Set up the tenant and its stock model.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onExpandedChange(!expanded) }) {
                    Text(if (expanded) "Hide" else "Create")
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onScanBusinessQr, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Import setup from business QR")
                    }
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = onBusinessNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Business name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ownerEmail,
                        onValueChange = onOwnerEmailChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Owner email (optional)") },
                        supportingText = { Text("Leave blank if the owner will enroll from another phone.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true
                    )
                    Text("Stock setup", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = inventoryMode == "shared",
                            onClick = { onInventoryModeChange("shared") },
                            label = { Text("Shared stock") },
                            modifier = Modifier.weight(1f),
                            leadingIcon = if (inventoryMode == "shared") ({ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }) else null
                        )
                        FilterChip(
                            selected = inventoryMode == "per_counter",
                            onClick = { onInventoryModeChange("per_counter") },
                            label = { Text("Per-counter") },
                            modifier = Modifier.weight(1f),
                            leadingIcon = if (inventoryMode == "per_counter") ({ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }) else null
                        )
                    }
                    Text(
                        if (inventoryMode == "shared") "One balance is shared by all counters." else "Each counter keeps its own balance; stock can be transferred between counters.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = counterCount,
                        onValueChange = onCounterCountChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Number of service counters") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Button(
                        onClick = onCreateBusiness,
                        enabled = businessName.trim().isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create business profile")
                    }
                }
            }
        }
    }
}

@Composable
private fun PortfolioBusinessCard(
    business: AdminBusiness,
    selected: Boolean,
    onSelect: () -> Unit,
    onScanPhone: (String) -> Unit,
    onStatusChange: (String, String, String?) -> Unit
) {
    var enrollmentRole by remember(business.shopId) { mutableStateOf("owner") }
    var roleMenuExpanded by remember(business.shopId) { mutableStateOf(false) }
    val highlight = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, highlight)
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp).size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(business.shopName, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${business.businessType.replace('_', ' ').replaceFirstChar { it.uppercase() }} · ${business.businessSize}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BusinessStatusPill(business.status)
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("BUSINESS OWNER", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)
                Text(
                    if (business.ownerEmail == "Pending owner enrollment") "Owner enrollment pending" else business.ownerEmail.ifBlank { "Owner not assigned" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BusinessStat("Team", business.memberCount.toString(), Icons.Default.Groups, Modifier.weight(1f))
                BusinessStat("Products", business.productCount.toString(), Icons.Default.Inventory2, Modifier.weight(1f))
                BusinessStat("Stock units", business.stockUnits.toString(), Icons.Default.Storefront, Modifier.weight(1f))
            }

            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "${if (business.counterInventoryMode == "per_counter") "Separate stock" else "Shared stock"} · ${business.counterCount} counters",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text("Created ${business.createdAt.take(10)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (!business.lifecycleReason.isNullOrBlank()) {
                Text("Lifecycle note: ${business.lifecycleReason}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            OutlinedButton(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
                Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.Business, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (selected) "Business selected" else "Manage business")
            }

            AnimatedVisibility(visible = selected) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Enroll a phone", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                "On the other phone, tap ‘Sign in with QR code’. Scan its pending QR here to grant access to this business.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    OutlinedButton(onClick = { roleMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                        Text(enrollmentRole.replace('_', ' ').replaceFirstChar { it.uppercase() }, maxLines = 1)
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
                                Button(
                                    onClick = { onScanPhone(enrollmentRole) },
                                    enabled = business.status == "active",
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Scan QR", maxLines = 1)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (business.status != "active") {
                            Button(onClick = { onStatusChange(business.shopId, "active", "Reopened by platform administrator") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Reopen")
                            }
                        }
                        if (business.status == "active") {
                            OutlinedButton(onClick = { onStatusChange(business.shopId, "suspended", "Suspended by platform administrator") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Suspend")
                            }
                        }
                        if (business.status != "closed") {
                            OutlinedButton(onClick = { onStatusChange(business.shopId, "closed", "Closed by platform administrator") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Close")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BusinessStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BusinessStatusPill(status: String) {
    val (background, foreground) = when (status) {
        "active" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "suspended" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = CircleShape, color = background) {
        Text(status.uppercase(), color = foreground, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

@Composable
private fun EmptyPortfolioState(hasBusinesses: Boolean, onClearSearch: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Text(if (hasBusinesses) "No matching businesses" else "No client businesses yet", fontWeight = FontWeight.SemiBold)
            Text(
                if (hasBusinesses) "Try another search or status filter." else "Create the first business profile to begin managing your portfolio.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasBusinesses) TextButton(onClick = onClearSearch) { Text("Clear filters") }
        }
    }
}
