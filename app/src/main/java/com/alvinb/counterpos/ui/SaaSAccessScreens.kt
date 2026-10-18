package com.alvinb.counterpos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
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
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onCredentialSignIn(credential.trim()) },
                    enabled = credential.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Continue") }
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

    val active = businesses.count { it.status == "active" }
    val pending = businesses.count { it.ownerEmail == "Pending owner enrollment" }
    Scaffold(containerColor = Color(0xFFF6F8F7)) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF073B32)).padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF1D6B5C)) {
                            Text("CP", modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp), color = Color.White, fontWeight = FontWeight.Black)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Counter POS", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Platform control center", color = Color(0xFFB9D8D0), fontSize = 12.sp)
                        }
                        IconButton(onClick = onSignOut) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out", tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Text("Good to see you, Admin", color = Color(0xFFB9D8D0), fontSize = 13.sp)
                    Text("Keep every business moving.", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text(email, color = Color(0xFFB9D8D0), fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdminMetricCard("Businesses", businesses.size.toString(), Icons.Default.Business, Color(0xFFE7F4EF), Modifier.weight(1f))
                    AdminMetricCard("Active", active.toString(), Icons.Default.CheckCircle, Color(0xFFEAF5EC), Modifier.weight(1f))
                    AdminMetricCard("Pending", pending.toString(), Icons.Default.People, Color(0xFFFFF4DE), Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdminQuickAction("New business", Icons.Default.AddBusiness, Modifier.weight(1f)) { /* form below */ }
                    AdminQuickAction("Scan QR", Icons.Default.QrCodeScanner, Modifier.weight(1f)) { viewModel.openScanner(ScannerMode.ADMIN_QR) }
                }
            }
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFE1F3EC)) {
                                Icon(Icons.Default.AddBusiness, contentDescription = null, tint = Color(0xFF0B6B56), modifier = Modifier.padding(9.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Register a business", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("Create a tenant, then securely enroll its owner.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        OutlinedTextField(value = businessName, onValueChange = { businessName = it; onClearError() }, label = { Text("Business name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                        OutlinedTextField(value = ownerEmail, onValueChange = { ownerEmail = it; onClearError() }, label = { Text("Owner email · optional") }, supportingText = { Text("Leave blank for later enrollment") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), shape = RoundedCornerShape(14.dp))
                        OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Lifecycle note · optional") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                        if (!error.isNullOrBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        Button(onClick = { viewModel.createBusinessForOwner(businessName, ownerEmail); businessName = ""; ownerEmail = "" }, enabled = businessName.trim().isNotBlank(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Text("Create business profile")
                        }
                    }
                }
            }
            state.adminBusinessPasskey?.let { passkey ->
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE5F7EE))) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF08704F))
                                Text("Owner invite ready", fontWeight = FontWeight.Bold, color = Color(0xFF075E46))
                            }
                            Text("Send this one-time passkey privately to the business owner.", fontSize = 12.sp, color = Color(0xFF38685A))
                            Text(passkey, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Color(0xFF075E46), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val clipboard = LocalClipboardManager.current
                                Button(onClick = { clipboard.setText(AnnotatedString(passkey)) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.ContentCopy, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Copy") }
                                TextButton(onClick = viewModel::clearBusinessOwnerPass, modifier = Modifier.weight(1f)) { Text("Dismiss") }
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("Your businesses", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Manage access, enrollment, and lifecycle status", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${businesses.size} total", fontSize = 12.sp, color = Color(0xFF0B6B56), fontWeight = FontWeight.Bold)
                }
            }
            items(businesses, key = { it.shopId }) { business ->
                AdminBusinessCard(business = business, reason = reason, selected = state.adminSelectedBusinessId == business.shopId, onSelect = { viewModel.selectAdminBusiness(business.shopId) }, onScanUser = { viewModel.openScanner(ScannerMode.ADMIN_QR) }, onGenerateOwnerPass = { viewModel.generateBusinessOwnerPass(business.shopId) }, onStatusChange = viewModel::setBusinessStatus)
            }
            if (businesses.isEmpty()) item { Text("No businesses returned yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
    }
}

@Composable
private fun AdminMetricCard(label: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = tint)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = Color(0xFF0B6B56), modifier = Modifier.size(19.dp))
            Text(value, fontSize = 23.sp, fontWeight = FontWeight.Black, color = Color(0xFF073B32))
            Text(label, fontSize = 11.sp, color = Color(0xFF477469))
        }
    }
}

@Composable
private fun AdminQuickAction(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(56.dp), shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun AdminBusinessCard(
    business: AdminBusiness,
    reason: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onScanUser: () -> Unit,
    onGenerateOwnerPass: () -> Unit,
    onStatusChange: (String, String, String?) -> Unit
) {
    val active = business.status == "active"
    val statusColor = if (active) Color(0xFF08704F) else if (business.status == "suspended") Color(0xFFB54708) else Color(0xFF667085)
    Card(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = if (active) Color(0xFFE5F7EE) else Color(0xFFF1F3F5)) {
                    Icon(if (active) Icons.Default.CheckCircle else Icons.Default.StopCircle, contentDescription = null, tint = statusColor, modifier = Modifier.padding(11.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(business.shopName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(business.ownerEmail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(99.dp), color = if (active) Color(0xFFE5F7EE) else Color(0xFFFFF1E5)) {
                    Text(business.status.replaceFirstChar { it.uppercase() }, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AdminStat("Staff", business.memberCount.toString(), Modifier.weight(1f))
                AdminStat("Products", business.productCount.toString(), Modifier.weight(1f))
                AdminStat("Units", business.stockUnits.toString(), Modifier.weight(1f))
            }
            if (!business.lifecycleReason.isNullOrBlank()) Text(business.lifecycleReason.orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onGenerateOwnerPass, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Security, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text("Generate owner passkey")
            }
            OutlinedButton(onClick = onSelect, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(if (selected) "Close business tools" else "Open business tools", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                Icon(Icons.Default.ArrowForward, contentDescription = null)
            }
            if (selected) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF5F8F7)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Business tools", fontWeight = FontWeight.Bold)
                        Text("User QR format: COUNTERPOS|USER|registered-user@email|cashier", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onGenerateOwnerPass, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) { Text("Generate owner pass") }
                        OutlinedButton(onClick = onScanUser, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) { Text("Scan user QR") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!active) Button(onClick = { onStatusChange(business.shopId, "active", reason.ifBlank { "Reopened by platform administrator" }) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp)) { Text("Reopen") }
                if (active) OutlinedButton(onClick = { onStatusChange(business.shopId, "suspended", reason.ifBlank { "Suspended by platform administrator" }) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp)) { Text("Suspend") }
                if (business.status != "closed") OutlinedButton(onClick = { onStatusChange(business.shopId, "closed", reason.ifBlank { "Closed by platform administrator" }) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp)) { Text("Close") }
            }
        }
    }
}

@Composable
private fun AdminStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF5F8F7), modifier = modifier) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, color = Color(0xFF073B32))
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
