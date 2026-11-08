package com.alvinb.counterpos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvinb.counterpos.R

/**
 * RegisterBusinessScreen
 *
 * Dedicated self-service onboarding screen allowing new users to sign up
 * with email and password and configure their business, exactly mirroring
 * the admin-managed business creation architecture.
 */
@Composable
fun RegisterBusinessScreen(
    viewModel: CounterPosViewModel,
    onNavigateToSignIn: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    RegisterBusinessScreen(
        error = state.startupError,
        notice = state.notice,
        isCreating = state.isAuthenticating || state.isCreatingBusiness,
        onRegister = { email, password, name, type, size, mode, count ->
            viewModel.registerOwnerAndBusiness(
                email = email,
                password = password,
                businessName = name,
                businessType = type,
                businessSize = size,
                inventoryMode = mode,
                counterCount = count
            )
        },
        onNavigateToSignIn = onNavigateToSignIn,
        onClearError = viewModel::clearStartupError
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterBusinessScreen(
    error: String? = null,
    notice: String? = null,
    isCreating: Boolean = false,
    onRegister: (
        email: String,
        password: String,
        businessName: String,
        businessType: String,
        businessSize: String,
        inventoryMode: String,
        counterCount: Int
    ) -> Unit,
    onNavigateToSignIn: () -> Unit,
    onClearError: () -> Unit = {}
) {
    BackHandler { onNavigateToSignIn() }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirmation by remember { mutableStateOf("") }
    var businessName by remember { mutableStateOf("") }
    var businessType by remember { mutableStateOf("big_club") } // "big_club" vs "counter_seller"
    var businessSize by remember { mutableStateOf("medium") } // "small", "medium", "large"
    var inventoryMode by remember { mutableStateOf("shared") } // "shared" vs "per_counter"
    var counterCount by remember { mutableStateOf("1") }

    val isClub = businessType == "big_club" || businessType == "club"
    val isLiquorStore = businessType == "counter_seller" || businessType == "liquor_store"

    val emailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val passwordValid = password.length >= 6
    val passwordsMatch = password.isNotBlank() && password == passwordConfirmation
    val businessNameValid = businessName.trim().length in 2..120
    val counterCountValid = !isClub || (counterCount.toIntOrNull()?.let { it in 1..50 } == true)

    val canSubmit = emailValid && passwordValid && passwordsMatch &&
        businessNameValid && counterCountValid && !isCreating

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Register Business",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateToSignIn,
                        modifier = Modifier.testTag("register_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to sign in"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero card mirroring admin architecture
            item {
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
                                text = "Create Your Business Account",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Sign up with email & password and launch your customized counter POS & floor plan.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Error & Notice banners
            if (!error.isNullOrBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (!notice.isNullOrBlank()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = notice,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // SECTION 1: Account Credentials
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
                            text = "1. Account Credentials",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; onClearError() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_email_input"),
                            label = { Text("Owner Email Address") },
                            placeholder = { Text("e.g. owner@example.com") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            enabled = !isCreating,
                            isError = email.isNotBlank() && !emailValid,
                            supportingText = {
                                if (email.isNotBlank() && !emailValid) {
                                    Text("Please enter a valid email address.")
                                } else {
                                    Text("This email will be your business administrator login.")
                                }
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; onClearError() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_password_input"),
                            label = { Text("Password (min 6 characters)") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            enabled = !isCreating,
                            isError = password.isNotEmpty() && !passwordValid,
                            supportingText = {
                                if (password.isNotEmpty() && !passwordValid) {
                                    Text("Password must be at least 6 characters.")
                                }
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                        OutlinedTextField(
                            value = passwordConfirmation,
                            onValueChange = { passwordConfirmation = it; onClearError() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_password_confirm_input"),
                            label = { Text("Confirm Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            enabled = !isCreating,
                            isError = passwordConfirmation.isNotEmpty() && !passwordsMatch,
                            supportingText = {
                                if (passwordConfirmation.isNotEmpty() && !passwordsMatch) {
                                    Text("Passwords do not match.")
                                }
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
            }

            // SECTION 2: Business Identity
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
                            text = "2. Business Identity",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = businessName,
                            onValueChange = { businessName = it; onClearError() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_business_name_input"),
                            label = { Text("Business / Bar Name") },
                            placeholder = { Text("e.g. Euphoria Lounge & Grill") },
                            singleLine = true,
                            enabled = !isCreating,
                            isError = businessName.isNotEmpty() && !businessNameValid,
                            supportingText = {
                                Text("Display name visible on receipts, counters, and table checks.")
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
            }

            // SECTION 3: Business Model Selection (Mirroring Admin Cards)
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
                            text = "3. Business Model & Floor Plan",
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
                                onClick = {
                                    businessType = "big_club"
                                    if (counterCount.toIntOrNull() == null || counterCount.toIntOrNull()!! < 1) {
                                        counterCount = "1"
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(140.dp)
                                    .testTag("register_select_club"),
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
                                onClick = {
                                    businessType = "counter_seller"
                                    counterCount = "1"
                                    inventoryMode = "shared"
                                    businessSize = "medium"
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(140.dp)
                                    .testTag("register_select_liquor_store"),
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

            // SECTION 4: Service Counters & Stock (for Clubs)
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
                                text = "4. Service Counters & Inventory Architecture",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Text("Club Scale", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("small" to "Small Club", "medium" to "Medium Club", "large" to "Large Club").forEach { (value, label) ->
                                    FilterChip(
                                        selected = businessSize == value,
                                        onClick = { businessSize = value },
                                        label = { Text(label, fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Text("Stock Balance Strategy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = inventoryMode == "shared",
                                    onClick = { inventoryMode = "shared" },
                                    label = { Text("Shared Central Stock") },
                                    modifier = Modifier.weight(1f),
                                    leadingIcon = if (inventoryMode == "shared") ({
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                                    }) else null
                                )
                                FilterChip(
                                    selected = inventoryMode == "per_counter",
                                    onClick = { inventoryMode = "per_counter" },
                                    label = { Text("Per-Counter Isolated") },
                                    modifier = Modifier.weight(1f),
                                    leadingIcon = if (inventoryMode == "per_counter") ({
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                                    }) else null
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
                                            if (currentCount > 1) counterCount = (currentCount - 1).toString()
                                        },
                                        enabled = currentCount > 1 && !isCreating
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease counters")
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
                                            if (currentCount < 50) counterCount = (currentCount + 1).toString()
                                        },
                                        enabled = currentCount < 50 && !isCreating
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase counters")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 5: Sign Up Action & Navigation
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            val parsedCount = if (isClub) counterCount.toIntOrNull() ?: 1 else 1
                            onRegister(
                                email.trim(),
                                password,
                                businessName.trim(),
                                businessType,
                                businessSize,
                                inventoryMode,
                                parsedCount
                            )
                        },
                        enabled = canSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("sign_up_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Setting up business…", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign Up & Launch Business", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }

                    TextButton(
                        onClick = onNavigateToSignIn,
                        enabled = !isCreating,
                        modifier = Modifier.testTag("navigate_to_signin_button")
                    ) {
                        Text(
                            "Already have an account? Sign In",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
