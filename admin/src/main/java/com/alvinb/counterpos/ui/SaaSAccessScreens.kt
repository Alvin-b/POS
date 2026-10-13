package com.alvinb.counterpos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvinb.counterpos.data.remote.AdminBusiness
import com.alvinb.counterpos.ui.scanner.BarcodeScannerScreen
import com.alvinb.counterpos.ui.scanner.ScannerMode

@Composable
fun CounterPosApp(viewModel: CounterPosViewModel) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !state.isReady -> LoadingScreen()
            !session.isAuthenticated -> AuthScreen(
                error = state.startupError,
                notice = state.notice,
                onSignIn = viewModel::signIn,
                onClearError = viewModel::clearStartupError
            )
            session.isPlatformAdmin -> AdminConsoleScreen(
                viewModel = viewModel,
                email = session.email,
                error = state.startupError,
                onSignOut = viewModel::signOut,
                onClearError = viewModel::clearStartupError
            )
            else -> NonSuperadminBlockedScreen(onSignOut = viewModel::signOut)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Connecting securely…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AuthScreen(
    error: String?,
    notice: String?,
    onSignIn: (String, String) -> Unit,
    onClearError: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Counter POS", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Secure business point of sale", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Superadmin sign in", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("This application is restricted to the platform administrator. Business owners and employees use the separate Counter POS app.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = email, onValueChange = { email = it; onClearError() }, label = { Text("Superadmin email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it; onClearError() }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                if (!error.isNullOrBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                if (!notice.isNullOrBlank()) Text(notice, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                Button(onClick = { onSignIn(email, password) }, enabled = email.contains("@") && password.length >= 6, modifier = Modifier.fillMaxWidth()) { Text("Sign in to Control Center") }
            }
        }
    }
}

@Composable
private fun NonSuperadminBlockedScreen(onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Superadmin access required", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("This APK is only for the platform administrator. Install the separate Counter POS APK for business-owner and employee access.", modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
    }
}

@Composable
private fun BusinessSetupScreen(
    email: String,
    error: String?,
    onSignOut: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Business access pending", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Signed in as $email. Client businesses are registered by the platform superadmin. Ask the administrator to scan your business QR and add your account as the owner.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        if (!error.isNullOrBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Text("After authorization, sign in again to access the business workspace.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
    }
}

@Composable
private fun BusinessPickerScreen(
    session: com.alvinb.counterpos.data.SessionSnapshot,
    onSelect: (String) -> Unit,
    onSignOut: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Choose a business", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Select the active business for this device.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
        session.memberships.filter { it.status == "active" }.forEach { membership ->
            Card(onClick = { onSelect(membership.shopId) }, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(membership.shopName, fontWeight = FontWeight.SemiBold)
                    Text("Role: ${membership.role}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
    }
}

@Composable
private fun BusinessAccessBlockedScreen(
    session: com.alvinb.counterpos.data.SessionSnapshot,
    onSignOut: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Business access unavailable", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("The platform administrator has suspended or closed this business. Inventory and sales are retained, but all staff access is blocked until it is reopened.", modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        session.memberships.forEach { membership ->
            Text("${membership.shopName}: ${membership.status}", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
    }
}

@Composable
private fun AdminConsoleScreen(
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
    var businessType by remember { mutableStateOf("big_club") }
    var businessSize by remember { mutableStateOf("medium") }
    var reason by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadAdminBusinesses() }

    val canCreateBusiness = businessName.trim().length in 2..120 &&
        (ownerEmail.isBlank() || ownerEmail.trim().contains("@")) &&
        !state.isAdminMutating

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

    val visibleBusinesses = businesses.filter { business ->
        searchQuery.isBlank() || business.shopName.contains(searchQuery, ignoreCase = true) || business.ownerEmail.contains(searchQuery, ignoreCase = true)
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                            Text("Control Center", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Platform administration", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
                            Text(email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f), modifier = Modifier.padding(top = 8.dp))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            TextButton(onClick = { viewModel.loadAdminBusinesses(force = true) }, enabled = !state.isAdminLoading) { Text("Refresh") }
                            TextButton(onClick = onSignOut) { Text("Sign out") }
                        }
                    }
                }
            }
            if (state.isAdminLoading) {
                item { Text("Loading business directory…", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp)) }
            }
            if (!error.isNullOrBlank()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(14.dp))
                    }
                }
            }
            if (!state.notice.isNullOrBlank()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text(state.notice.orEmpty(), color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(14.dp))
                    }
                }
            }
            item {
                AdminSectionLabel("Overview")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Portfolio overview", fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            AdminMetric("Businesses", businesses.size.toString())
                            AdminMetric("Active", businesses.count { it.status == "active" }.toString())
                            AdminMetric("Pending owners", businesses.count { it.ownerEmail == "Pending owner enrollment" }.toString())
                            AdminMetric("Suspended", businesses.count { it.status == "suspended" }.toString())
                        }
                    }
                }
            }
            item {
                AdminSectionLabel("Business onboarding")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Register business", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("Create the profile first. Then select it below and issue a one-time owner pass.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(value = businessName, onValueChange = { businessName = it; onClearError() }, label = { Text("Business name") }, supportingText = { Text("2–120 characters") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = ownerEmail, onValueChange = { ownerEmail = it; onClearError() }, label = { Text("Owner email (optional)") }, supportingText = { Text("Leave blank to enroll with a passkey.") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                        Text("Business setup profile", fontWeight = FontWeight.SemiBold)
                        Text("This profile determines the dashboard and features delivered to the owner app.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { businessType = "big_club" }, modifier = Modifier.weight(1f), colors = if (businessType == "big_club") androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()) { Text("Big Club") }
                            OutlinedButton(onClick = { businessType = "counter_seller" }, modifier = Modifier.weight(1f), colors = if (businessType == "counter_seller") androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()) { Text("Counter Seller") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("small", "medium", "large").forEach { size ->
                                OutlinedButton(onClick = { businessSize = size }, modifier = Modifier.weight(1f), colors = if (businessSize == size) androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()) { Text(size.replaceFirstChar { it.uppercase() }) }
                            }
                        }
                        Text("Workflow: create profile → select business → issue owner pass → owner signs in.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = {
                                viewModel.createBusinessForOwner(businessName, ownerEmail, businessType, businessSize)
                            },
                            enabled = canCreateBusiness,
                            modifier = Modifier.fillMaxWidth()
                        ) { if (state.isAdminMutating) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp)) else Text("Register business") }
                    }
                }
            }
            item {
                AdminSectionLabel("Business directory")
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search businesses or owners") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Businesses", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("${visibleBusinesses.size} shown", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.enrollmentCredential?.let { credential ->
                item {
                    EnrollmentCredentialCard(
                        credential = credential,
                        onDismiss = viewModel::clearEnrollmentCredential
                    )
                }
            }
            items(visibleBusinesses, key = { it.shopId }) { business ->
                AdminBusinessCard(
                    business = business,
                    reason = reason,
                    selected = state.adminSelectedBusinessId == business.shopId,
                    onSelect = { viewModel.selectAdminBusiness(business.shopId) },
                    onScanUser = { viewModel.openScanner(ScannerMode.ADMIN_QR) },
                    onGeneratePass = { role -> viewModel.generateEnrollmentCredential(business, role) },
                    onStatusChange = viewModel::setBusinessStatus,
                    actionsEnabled = !state.isAdminMutating
                )
            }
            if (businesses.isEmpty()) item { Text("No businesses returned yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun AdminMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AdminSectionLabel(label: String) {
    Text(
        text = label.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun AdminBusinessCard(
    business: AdminBusiness,
    reason: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onScanUser: () -> Unit,
    onGeneratePass: (String) -> Unit,
    onStatusChange: (String, String, String?) -> Unit,
    actionsEnabled: Boolean
) {
    val statusColor = when (business.status) {
        "active" -> MaterialTheme.colorScheme.primary
        "suspended" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(business.shopName, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Created ${business.createdAt.take(10)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(business.status.uppercase(), color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Owner enrollment", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(business.ownerEmail, fontWeight = FontWeight.Medium)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AdminMetric("Staff", business.memberCount.toString())
                AdminMetric("Products", business.productCount.toString())
                AdminMetric("Stock units", business.stockUnits.toString())
            }
            Text("Setup: ${business.businessType.replace('_', ' ')} · ${business.businessSize}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            if (!business.lifecycleReason.isNullOrBlank()) Text(business.lifecycleReason, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onSelect, enabled = actionsEnabled, modifier = Modifier.fillMaxWidth()) {
                Text(if (selected) "Business profile selected" else "Open business profile")
            }
            if (selected) {
                Text("Enrollment actions", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onGeneratePass("owner") }, enabled = actionsEnabled && business.status == "active", modifier = Modifier.weight(1f)) { Text("Owner pass") }
                    OutlinedButton(onClick = { onGeneratePass("cashier") }, enabled = actionsEnabled && business.status == "active", modifier = Modifier.weight(1f)) { Text("Staff pass") }
                }
                OutlinedButton(onClick = onScanUser, enabled = actionsEnabled, modifier = Modifier.fillMaxWidth()) { Text("Scan existing user QR") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (business.status != "active") {
                    Button(onClick = { onStatusChange(business.shopId, "active", reason.ifBlank { "Reopened by platform administrator" }) }, enabled = actionsEnabled, modifier = Modifier.weight(1f)) { Text("Reopen") }
                }
                if (business.status == "active") {
                    OutlinedButton(onClick = { onStatusChange(business.shopId, "suspended", reason.ifBlank { "Suspended by platform administrator" }) }, enabled = actionsEnabled, modifier = Modifier.weight(1f)) { Text("Suspend") }
                }
                if (business.status != "closed") {
                    OutlinedButton(onClick = { onStatusChange(business.shopId, "closed", reason.ifBlank { "Closed by platform administrator" }) }, enabled = actionsEnabled, modifier = Modifier.weight(1f)) { Text("Close") }
                }
            }
        }
    }
}
