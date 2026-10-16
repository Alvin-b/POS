package com.alvinb.counterpos.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
                onCredentialSignIn = viewModel::signInWithCredential,
                onQrSignIn = viewModel::signInWithQrRequested,
                qrPayload = state.loginQrPayload,
                onClearError = viewModel::clearStartupError
            )
            session.isPlatformAdmin -> AdminConsoleScreen(
                viewModel = viewModel,
                email = session.email,
                error = state.startupError,
                onSignOut = viewModel::signOut,
                onClearError = viewModel::clearStartupError
            )
            session.activeBusiness?.employmentStatus != "active" -> EmployeeAccessBlockedScreen(session.activeBusiness, viewModel::signOut)
            session.activeBusiness != null && state.isAccessLocked -> WorkingHoursLockedScreen(state.accessLockMessage.orEmpty(), viewModel::refreshAccessNow, viewModel::signOut)
            session.activeBusiness != null -> CounterPosWorkspace(viewModel)
            session.memberships.any { it.status == "active" && it.employmentStatus == "active" } -> BusinessPickerScreen(
                session = session,
                onSelect = viewModel::selectBusiness,
                onSignOut = viewModel::signOut
            )
            session.memberships.isNotEmpty() -> BusinessAccessBlockedScreen(
                session = session,
                onSignOut = viewModel::signOut
            )
            else -> BusinessSetupScreen(
                email = session.email,
                onSignOut = viewModel::signOut,
                error = state.startupError
            )
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
    onCredentialSignIn: (String) -> Unit,
    onQrSignIn: () -> Unit,
    qrPayload: String?,
    onClearError: () -> Unit
) {
    var credential by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    fun pasteFromClipboard() {
        val clipText = clipboardManager.getText()?.text
        val textToPaste = if (!clipText.isNullOrBlank()) {
            clipText
        } else {
            runCatching {
                val sysClip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                sysClip?.primaryClip?.getItemAt(0)?.text?.toString()
            }.getOrNull()
        }
        if (!textToPaste.isNullOrBlank()) {
            credential = textToPaste.trim()
            onClearError()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Counter POS", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Secure business point of sale", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        if (qrPayload != null) LoginQrCard(payload = qrPayload)
        else Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (error.isNullOrBlank()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (error.isNullOrBlank()) "Preparing secure QR sign-in…" else "Secure QR sign-in unavailable", fontWeight = FontWeight.SemiBold)
                if (!error.isNullOrBlank()) Text(error, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                OutlinedButton(onClick = { onClearError(); onQrSignIn() }, modifier = Modifier.fillMaxWidth()) { Text("Retry QR setup") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sign in", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Enter your business or employee passkey, or the current Superadmin token. Access opens automatically for your account.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = credential,
                    onValueChange = { credential = it; onClearError() },
                    label = { Text("Passkey") },
                    placeholder = { Text("Enter passkey or access token") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (credential.isNotEmpty()) {
                                IconButton(onClick = { credential = ""; onClearError() }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear input", modifier = Modifier.size(20.dp))
                                }
                            }
                            IconButton(
                                onClick = { pasteFromClipboard() },
                                modifier = Modifier.testTag("paste_passkey_icon")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste passkey from clipboard",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("passkey_input_field")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { pasteFromClipboard() },
                        modifier = Modifier.weight(1f).testTag("paste_passkey_button")
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Paste")
                    }
                    Button(
                        onClick = { onCredentialSignIn(credential.trim()) },
                        enabled = credential.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Continue") }
                }
                OutlinedButton(onClick = onQrSignIn, modifier = Modifier.fillMaxWidth()) { Text("Sign in with QR code") }
                if (!error.isNullOrBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                if (!notice.isNullOrBlank()) Text(notice, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EmployeeAccessBlockedScreen(
    membership: com.alvinb.counterpos.data.remote.BusinessMembership?,
    onSignOut: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Employee access locked", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("${membership?.shopName.orEmpty()} has disabled this employee account. Selling, stock, and table actions are blocked by the server.", modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
    }
}

@Composable
private fun WorkingHoursLockedScreen(message: String, onCheckAgain: () -> Unit, onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Workspace locked", fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(message, modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCheckAgain, modifier = Modifier.fillMaxWidth()) { Text("Check active hours") }
        TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
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
        session.memberships.filter { it.status == "active" && it.employmentStatus == "active" }.forEach { membership ->
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
    var reason by remember { mutableStateOf("") }

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

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Platform Admin", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        AdminMetric("Businesses", businesses.size.toString())
                        AdminMetric("Active", businesses.count { it.status == "active" }.toString())
                        AdminMetric("Pending owners", businesses.count { it.ownerEmail == "Pending owner enrollment" }.toString())
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Register a client business", fontWeight = FontWeight.SemiBold)
                        Text("Create the business profile first. You can enroll its owner later with a secure QR code or passkey.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { viewModel.openScanner(ScannerMode.ADMIN_QR) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Scan business QR")
                        }
                        OutlinedTextField(value = businessName, onValueChange = { businessName = it; onClearError() }, label = { Text("Business name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = ownerEmail, onValueChange = { ownerEmail = it; onClearError() }, label = { Text("Owner email (optional)") }, supportingText = { Text("Leave blank if the owner will enroll later.") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                        OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Lifecycle reason (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (!error.isNullOrBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        Button(
                            onClick = {
                                viewModel.createBusinessForOwner(businessName, ownerEmail)
                                businessName = ""
                                ownerEmail = ""
                            },
                            enabled = businessName.trim().isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Create business profile") }
                    }
                }
            }
            item { Text("Client businesses (${businesses.size})", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)) }
            items(businesses, key = { it.shopId }) { business ->
                AdminBusinessCard(
                    business = business,
                    reason = reason,
                    selected = state.adminSelectedBusinessId == business.shopId,
                    onSelect = { viewModel.selectAdminBusiness(business.shopId) },
                    onScanUser = { viewModel.openScanner(ScannerMode.ADMIN_QR) },
                    onStatusChange = viewModel::setBusinessStatus
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
private fun AdminBusinessCard(
    business: AdminBusiness,
    reason: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onScanUser: () -> Unit,
    onStatusChange: (String, String, String?) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(business.shopName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(business.status.uppercase(), color = when (business.status) {
                    "active" -> MaterialTheme.colorScheme.primary
                    "suspended" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text("Owner: ${business.ownerEmail}", fontSize = 12.sp)
            Text("${business.memberCount} staff · ${business.productCount} products · ${business.stockUnits} units", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!business.lifecycleReason.isNullOrBlank()) Text("Reason: ${business.lifecycleReason}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
                Text(if (selected) "Business profile open" else "Open business profile")
            }
            if (selected) {
                Text("User QR format: COUNTERPOS|USER|registered-user@email|cashier", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onScanUser, modifier = Modifier.fillMaxWidth()) { Text("Scan user QR") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (business.status != "active") {
                    Button(onClick = { onStatusChange(business.shopId, "active", reason.ifBlank { "Reopened by platform administrator" }) }) { Text("Reopen") }
                }
                if (business.status == "active") {
                    OutlinedButton(onClick = { onStatusChange(business.shopId, "suspended", reason.ifBlank { "Suspended by platform administrator" }) }) { Text("Suspend") }
                }
                if (business.status != "closed") {
                    OutlinedButton(onClick = { onStatusChange(business.shopId, "closed", reason.ifBlank { "Closed by platform administrator" }) }) { Text("Close") }
                }
            }
        }
    }
}
