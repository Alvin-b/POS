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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.draw.clip
import com.alvinb.counterpos.data.remote.AdminAuditEvent
import com.alvinb.counterpos.data.remote.AdminBusiness
import com.alvinb.counterpos.R
import com.alvinb.counterpos.ui.scanner.BarcodeScannerScreen
import com.alvinb.counterpos.ui.scanner.ScannerMode

@Composable
fun CounterPosApp(
    viewModel: CounterPosViewModel,
    themeMode: String = "system",
    onThemeModeChange: (String) -> Unit = {}
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !state.isReady -> LoadingScreen()
            !session.isAuthenticated && state.isScannerOpen -> BarcodeScannerScreen(
                mode = ScannerMode.ADMIN_QR,
                products = emptyList(),
                onClose = viewModel::closeScanner,
                onProductScannedForSale = {},
                onProductScannedForStock = {},
                onProductScannedForTable = {},
                onAdminQrCodeScanned = { payload ->
                    viewModel.closeScanner()
                    viewModel.signInWithCredential(payload)
                },
                onBarcodeAssigned = {},
                onBarcodeNotRecognized = { payload ->
                    viewModel.closeScanner()
                    viewModel.signInWithCredential(payload)
                }
            )
            !session.isAuthenticated -> AuthScreen(
                error = state.startupError,
                notice = state.notice,
                onCredentialSignIn = viewModel::signInWithCredential,
                onOpenScanner = { viewModel.openScanner(ScannerMode.ADMIN_QR) },
                onRefreshQr = viewModel::prepareQrChallenge,
                qrPayload = state.loginQrPayload,
                onClearError = viewModel::clearStartupError
            )
            session.activeBusiness != null && !session.activeBusiness?.role.equals("owner", ignoreCase = true) && shouldShowEmployeeAccessLocked(session.activeBusiness?.employmentStatus) -> EmployeeAccessBlockedScreen(session.activeBusiness, viewModel::signOut)
            session.activeBusiness != null && state.isAccessLocked -> WorkingHoursLockedScreen(
                message = state.accessLockMessage.orEmpty(),
                ownerCanManage = session.activeBusiness?.role.equals("owner", ignoreCase = true),
                onCheckAgain = viewModel::refreshAccessNow,
                onOpenOwnerSettings = { viewModel.openStaffManagement(openHours = true) },
                onSignOut = viewModel::signOut
            )
            session.activeBusiness != null -> CounterPosWorkspace(viewModel, themeMode, onThemeModeChange)
            session.isPlatformAdmin -> SuperadminDashboardScreen(
                viewModel = viewModel,
                email = session.email,
                error = state.startupError,
                onSignOut = viewModel::signOut,
                onClearError = viewModel::clearStartupError
            )
            session.memberships.any { it.status != "suspended" && it.status != "terminated" && it.status != "closed" && (it.role.equals("owner", ignoreCase = true) || !shouldShowEmployeeAccessLocked(it.employmentStatus)) } -> BusinessPickerScreen(
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
    onOpenScanner: () -> Unit,
    onRefreshQr: () -> Unit,
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

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 30.dp)
                .heightIn(min = maxHeight),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(74.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_counterpos_logo),
                        contentDescription = "Counter POS logo",
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Spacer(Modifier.height(13.dp))
            Text("Counter POS", fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
            Text(
                "Fast selling for your team",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(25.dp))
            val qrExpired = error?.let { message ->
                message.contains("expired", ignoreCase = true) ||
                    message.contains("session expired", ignoreCase = true) ||
                    message.contains("already used", ignoreCase = true) ||
                    message.contains("used", ignoreCase = true)
            } == true
            if (qrPayload != null && !qrExpired) LoginQrCard(payload = qrPayload, onRefresh = { onClearError(); onRefreshQr() })
            else Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (qrExpired) "QR code expired" else "QR sign-in unavailable", fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = { onClearError(); onRefreshQr() }, modifier = Modifier.fillMaxWidth()) { Text("Refresh QR") }
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Welcome back", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Enter your access passkey or scan an enrollment QR code to continue.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    OutlinedTextField(
                        value = credential,
                        onValueChange = { credential = it; onClearError() },
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
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("passkey_input_field")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenScanner,
                            modifier = Modifier.weight(1f).testTag("scan_enrollment_qr_button")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Scan QR", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { pasteFromClipboard() },
                            modifier = Modifier.weight(1f).testTag("paste_passkey_button")
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Paste", maxLines = 1)
                        }
                        Button(
                            onClick = { onCredentialSignIn(credential.trim()) },
                            enabled = credential.isNotBlank(),
                            modifier = Modifier.weight(1.1f).testTag("continue_signin_button")
                        ) { Text("Continue", maxLines = 1) }
                    }
                    if (!notice.isNullOrBlank() && !notice.contains("QR", ignoreCase = true)) {
                        Text(notice, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                }
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
private fun WorkingHoursLockedScreen(
    message: String,
    ownerCanManage: Boolean,
    onCheckAgain: () -> Unit,
    onOpenOwnerSettings: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Business hours are closed", fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(message, modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCheckAgain, modifier = Modifier.fillMaxWidth()) { Text("Check access again") }
        if (ownerCanManage) {
            OutlinedButton(onClick = onOpenOwnerSettings, modifier = Modifier.fillMaxWidth()) { Text("Manage business hours") }
        }
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
    val activeMemberships = remember(session.memberships) {
        val nonSuspended = session.memberships.filter {
            it.status != "suspended" && it.status != "terminated" && it.status != "closed"
        }
        val privileged = nonSuspended.filter {
            it.role.equals("owner", ignoreCase = true) || !shouldShowEmployeeAccessLocked(it.employmentStatus)
        }
        privileged.ifEmpty { nonSuspended }.ifEmpty { session.memberships }
    }
    LaunchedEffect(activeMemberships) {
        if (activeMemberships.isNotEmpty()) {
            val ownerMatch = activeMemberships.firstOrNull { it.role.equals("owner", ignoreCase = true) }
            val target = ownerMatch ?: activeMemberships.first()
            onSelect(target.shopId)
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Choose a business", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Select the active business for this device.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
        activeMemberships.forEach { membership ->
            Card(onClick = { onSelect(membership.shopId) }, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(membership.shopName, fontWeight = FontWeight.SemiBold)
                    Text("Role: ${membership.role.replaceFirstChar { it.uppercase() }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
