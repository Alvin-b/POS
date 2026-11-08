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
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
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
    var showRegisterScreen by rememberSaveable { mutableStateOf(false) }

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
            !session.isAuthenticated && showRegisterScreen -> RegisterBusinessScreen(
                viewModel = viewModel,
                onNavigateToSignIn = { showRegisterScreen = false }
            )
            !session.isAuthenticated -> AuthScreen(
                error = state.startupError,
                notice = state.notice,
                isAuthenticating = state.isAuthenticating || state.isCreatingBusiness,
                onEmailSignIn = viewModel::signIn,
                onCreateAccount = viewModel::signUp,
                onRegisterBusiness = { email, password, name, type, size, mode, count ->
                    viewModel.registerOwnerAndBusiness(email, password, name, type, size, mode, count)
                },
                onNavigateToRegister = { showRegisterScreen = true },
                onCredentialSignIn = viewModel::signInWithCredential,
                onOpenScanner = { viewModel.openScanner(ScannerMode.ADMIN_QR) },
                onRefreshQr = viewModel::prepareQrChallenge,
                qrPayload = state.loginQrPayload,
                onClearError = viewModel::clearStartupError
            )
            session.isPlatformAdmin -> SuperadminDashboardScreen(
                viewModel = viewModel,
                email = session.email,
                error = state.startupError,
                onSignOut = viewModel::signOut,
                onClearError = viewModel::clearStartupError
            )
            session.activeBusiness != null && !session.activeBusiness?.role.equals("owner", ignoreCase = true) && shouldShowEmployeeAccessLocked(session.activeBusiness?.employmentStatus) -> EmployeeAccessBlockedScreen(session.activeBusiness, viewModel::signOut)
            session.activeBusiness != null && !session.activeBusiness?.role.equals("owner", ignoreCase = true) && state.isAccessLocked -> WorkingHoursLockedScreen(
                message = state.accessLockMessage.orEmpty(),
                ownerCanManage = false,
                onCheckAgain = viewModel::refreshAccessNow,
                onOpenOwnerSettings = { viewModel.openStaffManagement(openHours = true) },
                onSignOut = viewModel::signOut
            )
            session.activeBusiness != null -> CounterPosWorkspace(viewModel, themeMode, onThemeModeChange)
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
                error = state.startupError,
                notice = state.notice,
                isCreating = state.isCreatingBusiness,
                onClearError = viewModel::clearStartupError,
                onCreateBusiness = { name, type, size, mode, count ->
                    viewModel.createBusiness(name, type, size, mode, count)
                },
                onSignOut = viewModel::signOut
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
    isAuthenticating: Boolean,
    onEmailSignIn: (String, String) -> Unit,
    onCreateAccount: (String, String) -> Unit,
    onRegisterBusiness: (String, String, String, String, String, String, Int) -> Unit,
    onNavigateToRegister: () -> Unit = {},
    onCredentialSignIn: (String) -> Unit,
    onOpenScanner: () -> Unit,
    onRefreshQr: () -> Unit,
    qrPayload: String?,
    onClearError: () -> Unit
) {
    var authTab by remember { mutableStateOf(0) } // 0 = Sign In, 1 = Register Business & Account
    var credential by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirmation by remember { mutableStateOf("") }

    // Business Profile Fields
    var businessName by remember { mutableStateOf("") }
    var businessType by remember { mutableStateOf("big_club") } // "big_club" vs "counter_seller"
    var businessSize by remember { mutableStateOf("medium") } // "small", "medium", "large"
    var inventoryMode by remember { mutableStateOf("shared") } // "shared" vs "per_counter"
    var counterCount by remember { mutableStateOf("1") }

    val isClub = businessType == "big_club"
    val parsedCounterCount = counterCount.toIntOrNull() ?: 1
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val emailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val passwordsMatch = password.isNotBlank() && password == passwordConfirmation

    val canSubmitSignIn = emailValid && password.isNotBlank() && !isAuthenticating
    val canSubmitRegister = emailValid && password.length >= 6 && passwordsMatch &&
        businessName.trim().isNotBlank() && businessName.length <= 120 &&
        (!isClub || parsedCounterCount in 1..50) && !isAuthenticating

    val visibleAuthError = error?.takeUnless { it.contains("QR", ignoreCase = true) }

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
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .heightIn(min = maxHeight),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_counterpos_logo),
                        contentDescription = "Counter POS logo",
                        modifier = Modifier.size(46.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Counter POS", fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
            Text(
                "Point of sale & counter inventory management",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))

            // Segmented Tab Selector between Sign In and Register Business
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = { authTab = 0; onClearError() },
                    shape = RoundedCornerShape(10.dp),
                    color = if (authTab == 0) MaterialTheme.colorScheme.surface else Color.Transparent,
                    modifier = Modifier.weight(1f).testTag("auth_tab_signin")
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Sign In",
                            fontSize = 14.sp,
                            fontWeight = if (authTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (authTab == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    onClick = {
                        onClearError()
                        onNavigateToRegister()
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (authTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier.weight(1.3f).testTag("auth_tab_register")
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Create Business",
                            fontSize = 14.sp,
                            fontWeight = if (authTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (authTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (!visibleAuthError.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        visibleAuthError,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            if (!notice.isNullOrBlank() && !notice.contains("QR", ignoreCase = true)) {
                Text(
                    notice,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (authTab == 0) {
                // ==========================================
                // TAB 0: SIGN IN
                // ==========================================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Sign in to your business",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Enter your owner or staff email and password to access your counters.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; onClearError() },
                            modifier = Modifier.fillMaxWidth().testTag("email_signin_field"),
                            label = { Text("Email address") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            enabled = !isAuthenticating,
                            isError = email.isNotBlank() && !emailValid
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; onClearError() },
                            modifier = Modifier.fillMaxWidth().testTag("password_signin_field"),
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            enabled = !isAuthenticating
                        )
                        Button(
                            onClick = { onEmailSignIn(email.trim(), password) },
                            enabled = canSubmitSignIn,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("email_auth_submit_button")
                        ) {
                            if (isAuthenticating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(19.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(if (isAuthenticating) "Signing in…" else "Sign in")
                        }
                        TextButton(
                            onClick = {
                                onClearError()
                                onNavigateToRegister()
                            },
                            enabled = !isAuthenticating,
                            modifier = Modifier.align(Alignment.CenterHorizontally).testTag("switch_to_register_button")
                        ) {
                            Text("New here? Create account & business")
                        }
                    }
                }
            } else {
                // ==========================================
                // TAB 1: CREATE ACCOUNT & BUSINESS
                // ==========================================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Create Account & Business",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Register your owner account and launch your business profile immediately.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        // 1. Account Credentials
                        Text("1. Account Credentials", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; onClearError() },
                            modifier = Modifier.fillMaxWidth().testTag("register_email_field"),
                            label = { Text("Email address") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            enabled = !isAuthenticating,
                            isError = email.isNotBlank() && !emailValid
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; onClearError() },
                            modifier = Modifier.fillMaxWidth().testTag("register_password_field"),
                            label = { Text("Password (min 6 characters)") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            enabled = !isAuthenticating
                        )
                        OutlinedTextField(
                            value = passwordConfirmation,
                            onValueChange = { passwordConfirmation = it; onClearError() },
                            modifier = Modifier.fillMaxWidth().testTag("register_password_confirm_field"),
                            label = { Text("Confirm password") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            enabled = !isAuthenticating,
                            isError = passwordConfirmation.isNotEmpty() && passwordConfirmation != password
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // 2. Business Profile
                        Text("2. Business Profile", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = businessName,
                            onValueChange = { businessName = it; onClearError() },
                            modifier = Modifier.fillMaxWidth().testTag("register_business_name_field"),
                            label = { Text("Business name (e.g. Mama Jane Pub)") },
                            singleLine = true,
                            enabled = !isAuthenticating
                        )

                        // Business Type
                        Text("Business Type", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = businessType == "big_club",
                                onClick = {
                                    businessType = "big_club"
                                    if (counterCount.toIntOrNull() == null || counterCount.toIntOrNull()!! < 1) counterCount = "1"
                                },
                                label = { Text("Bar & Club") },
                                modifier = Modifier.weight(1f).testTag("register_type_club")
                            )
                            FilterChip(
                                selected = businessType == "counter_seller",
                                onClick = {
                                    businessType = "counter_seller"
                                    counterCount = "1"
                                    inventoryMode = "shared"
                                    businessSize = "medium"
                                },
                                label = { Text("Liquor Store") },
                                modifier = Modifier.weight(1f).testTag("register_type_store")
                            )
                        }

                        if (isClub) {
                            Text("Scale & Tables", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("small" to "Small", "medium" to "Medium", "large" to "Large").forEach { (size, label) ->
                                    FilterChip(
                                        selected = businessSize == size,
                                        onClick = { businessSize = size },
                                        label = { Text(label, fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Counters configuration
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Service Counters", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Number of dispensing stations", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val current = counterCount.toIntOrNull() ?: 1
                                            if (current > 1) counterCount = (current - 1).toString()
                                        },
                                        modifier = Modifier.size(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease counters", modifier = Modifier.size(16.dp))
                                    }
                                    Text(counterCount, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    OutlinedButton(
                                        onClick = {
                                            val current = counterCount.toIntOrNull() ?: 1
                                            if (current < 20) counterCount = (current + 1).toString()
                                        },
                                        modifier = Modifier.size(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase counters", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Text("Inventory Mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = inventoryMode == "shared",
                                    onClick = { inventoryMode = "shared" },
                                    label = { Text("Shared Stock") },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = inventoryMode == "per_counter",
                                    onClick = { inventoryMode = "per_counter" },
                                    label = { Text("By Counter") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onRegisterBusiness(
                                    email.trim(),
                                    password,
                                    businessName.trim(),
                                    businessType,
                                    businessSize,
                                    inventoryMode,
                                    parsedCounterCount
                                )
                            },
                            enabled = canSubmitRegister,
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("register_business_submit_button")
                        ) {
                            if (isAuthenticating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(19.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(if (isAuthenticating) "Setting up business…" else "Register Business & Start POS", fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = {
                                authTab = 0
                                onClearError()
                            },
                            enabled = !isAuthenticating,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Already have an account? Sign in")
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("OR SIGN IN WITH QR / PASSKEY", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
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
                            enabled = credential.isNotBlank() && !isAuthenticating,
                            modifier = Modifier.weight(1.1f).testTag("continue_signin_button")
                        ) {
                            if (isAuthenticating) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else Text("Continue", maxLines = 1)
                        }
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
    notice: String?,
    isCreating: Boolean,
    onClearError: () -> Unit,
    onCreateBusiness: (String, String, String, String, Int) -> Unit,
    onSignOut: () -> Unit
) {
    var businessName by remember { mutableStateOf("") }
    var businessType by remember { mutableStateOf("big_club") }
    var businessSize by remember { mutableStateOf("medium") }
    var inventoryMode by remember { mutableStateOf("shared") }
    var counterCount by remember { mutableStateOf("1") }
    val isClub = businessType == "big_club"
    val parsedCounterCount = counterCount.toIntOrNull()
    val canCreate = businessName.trim().isNotEmpty() && businessName.length <= 120 &&
        (!isClub || (parsedCounterCount ?: 0) in 1..50) && !isCreating

    Box(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Set up your business", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text(
                "Signed in as $email. Create the business profile for this owner account.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Business profile", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { businessName = it.take(120); onClearError() },
                        modifier = Modifier.fillMaxWidth().testTag("owner_business_name_field"),
                        label = { Text("Business name") },
                        placeholder = { Text("e.g. Euphoria Lounge") },
                        singleLine = true,
                        enabled = !isCreating
                    )
                    Text("Business type", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isClub,
                            onClick = {
                                businessType = "big_club"
                                onClearError()
                            },
                            label = { Text("Bar & Club") },
                            modifier = Modifier.weight(1f),
                            enabled = !isCreating
                        )
                        FilterChip(
                            selected = !isClub,
                            onClick = {
                                businessType = "counter_seller"
                                businessSize = "medium"
                                inventoryMode = "shared"
                                counterCount = "1"
                                onClearError()
                            },
                            label = { Text("Liquor Store") },
                            modifier = Modifier.weight(1f),
                            enabled = !isCreating
                        )
                    }
                    if (isClub) {
                        Text("Club size", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("small" to "Small", "medium" to "Medium", "large" to "Large").forEach { (value, label) ->
                                FilterChip(
                                    selected = businessSize == value,
                                    onClick = { businessSize = value; onClearError() },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isCreating
                                )
                            }
                        }
                        Text("Inventory strategy", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = inventoryMode == "shared",
                                onClick = { inventoryMode = "shared"; onClearError() },
                                label = { Text("Shared stock") },
                                modifier = Modifier.weight(1f),
                                enabled = !isCreating
                            )
                            FilterChip(
                                selected = inventoryMode == "per_counter",
                                onClick = { inventoryMode = "per_counter"; onClearError() },
                                label = { Text("By counter") },
                                modifier = Modifier.weight(1f),
                                enabled = !isCreating
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Service counters", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Choose 1–50 stations", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { counterCount = ((parsedCounterCount ?: 1) - 1).coerceAtLeast(1).toString(); onClearError() },
                                enabled = !isCreating && (parsedCounterCount ?: 1) > 1
                            ) { Text("−") }
                            Text(
                                parsedCounterCount?.toString() ?: "1",
                                modifier = Modifier.width(34.dp),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedButton(
                                onClick = { counterCount = ((parsedCounterCount ?: 1) + 1).coerceAtMost(50).toString(); onClearError() },
                                enabled = !isCreating && (parsedCounterCount ?: 1) < 50
                            ) { Text("+") }
                        }
                    } else {
                        Text(
                            "Liquor stores use one counter with shared stock and retail checkout.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Text(
                "You will be the owner of this business. It will appear in the platform admin business list.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            if (!notice.isNullOrBlank()) {
                Text(notice, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
            if (!error.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(error, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                }
            }
            Button(
                onClick = {
                    onCreateBusiness(
                        businessName.trim(),
                        businessType,
                        businessSize,
                        inventoryMode,
                        if (isClub) parsedCounterCount ?: 1 else 1
                    )
                },
                enabled = canCreate,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("create_owner_business_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text("Creating business…")
                } else Text("Create business")
            }
            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth(), enabled = !isCreating) { Text("Sign out") }
        }
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
