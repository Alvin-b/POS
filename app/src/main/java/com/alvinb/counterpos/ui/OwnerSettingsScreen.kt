package com.alvinb.counterpos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvinb.counterpos.data.butchery.ButcheryPricing
import com.alvinb.counterpos.data.local.entity.ButcheryConfigEntity
import com.alvinb.counterpos.data.local.entity.ButcheryCutEntity
import com.alvinb.counterpos.data.remote.ServiceCounter
import org.json.JSONObject
import java.util.Locale

/**
 * Full-screen owner controls. This screen deliberately receives callbacks for all mutations so its
 * parent remains responsible for navigation, scanner presentation, and ViewModel wiring.
 */
@Composable
fun OwnerSettingsScreen(
    state: CounterPosUiState,
    businessName: String,
    role: String,
    businessType: String = "big_club",
    registeredCounters: List<ServiceCounter>,
    butcheryConfig: ButcheryConfigEntity? = null,
    butcheryCuts: List<ButcheryCutEntity> = emptyList(),
    onClose: () -> Unit,
    onBusinessNameSave: (String) -> Unit,
    onLoadMembers: () -> Unit,
    onCounterCreate: (String) -> Unit,
    onCounterRename: (String, String) -> Unit,
    onCounterDelete: (String) -> Unit = {},
    onSaveButcheryConfig: (
        enabled: Boolean,
        name: String,
        beefOnly: Boolean,
        defaultBeefPricePerKgKsh: Int,
        paymentMethods: List<String>,
        enabledCategories: List<String>
    ) -> Unit = { _, _, _, _, _, _ -> },
    onOpenButcheryWorkspace: (ButcheryTab) -> Unit = {},
    onOpenStaffManagement: () -> Unit = {},
    onOpenSubscription: () -> Unit = {}
) {
    BackHandler(onBack = onClose)
    val ownerCanEdit = role.equals("owner", ignoreCase = true)
    val isLiquorStore = businessType.equals("counter_seller", ignoreCase = true) ||
        businessType.equals("liquor_store", ignoreCase = true)
    var businessNameDraft by remember(businessName) { mutableStateOf(businessName) }
    var butcheryEnabledDraft by remember(butcheryConfig?.enabled) { mutableStateOf(butcheryConfig?.enabled ?: false) }
    var butcheryNameDraft by remember(butcheryConfig?.butcheryName, businessName) {
        mutableStateOf(
            butcheryConfig?.butcheryName?.takeIf(String::isNotBlank)
                ?: if (businessName.isNotBlank()) "$businessName Butchery" else "Main Butchery"
        )
    }
    var butcheryCategoriesDraft by remember(butcheryConfig?.enabledCategoriesJson, butcheryConfig?.beefOnlyMode) {
        mutableStateOf(com.alvinb.counterpos.data.butchery.ButcheryPricing.resolveEnabledCategories(butcheryConfig))
    }
    var butcheryBeefPriceDraft by remember(butcheryConfig?.defaultBeefPricePerKgKsh) {
        mutableStateOf((butcheryConfig?.defaultBeefPricePerKgKsh ?: 680).toString())
    }
    var butcheryPaymentMethodsDraft by remember(butcheryConfig?.paymentMethodsJson) {
        mutableStateOf(com.alvinb.counterpos.data.butchery.ButcheryPricing.parsePaymentMethods(butcheryConfig?.paymentMethodsJson))
    }
    val butcheryIsBeefOnly = butcheryCategoriesDraft.size == 1 &&
        butcheryCategoriesDraft.first().equals(com.alvinb.counterpos.data.butchery.ButcheryCategory.BEEF.label, ignoreCase = true)
    val primaryMeatLabel = if (butcheryCategoriesDraft.size == 1) {
        butcheryCategoriesDraft.first()
    } else {
        com.alvinb.counterpos.data.butchery.ButcheryCategory.BEEF.label
    }
    var newCounterName by remember { mutableStateOf("") }
    var editingCounterId by remember { mutableStateOf<String?>(null) }
    var editingCounterName by remember { mutableStateOf("") }
    var counterPendingDeletion by remember { mutableStateOf<ServiceCounter?>(null) }

    // The roster is intentionally derived only from actual member rows. There are no sample names,
    // placeholder emails, or local fallback accounts in this screen.
    val waiters = remember(state.employeeMembers) {
        state.employeeMembers
            .asSequence()
            .filter { it.optString("role").equals("cashier", ignoreCase = true) }
            .mapNotNull { member ->
                val email = member.optString("email").trim()
                if (email.isBlank()) null else member to email
            }
            .distinctBy { it.second.lowercase(Locale.ROOT) }
            .toList()
    }

    LaunchedEffect(ownerCanEdit) {
        if (ownerCanEdit) onLoadMembers()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close settings")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Settings", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (ownerCanEdit) "Owner controls" else "View-only settings",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!state.actionError.isNullOrBlank()) {
                    SettingsMessage(
                        message = state.actionError.orEmpty(),
                        isError = true
                    )
                }
                if (!state.notice.isNullOrBlank()) {
                    SettingsMessage(
                        message = state.notice.orEmpty(),
                        isError = false
                    )
                }

                SettingsSection(
                    title = "Business profile",
                    description = "Change the business name.",
                    icon = Icons.Default.Business
                ) {
                    OutlinedTextField(
                        value = businessNameDraft,
                        onValueChange = { businessNameDraft = it.take(80) },
                        label = { Text("Business name") },
                        singleLine = true,
                        enabled = ownerCanEdit && !state.isSavingBusinessName,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onBusinessNameSave(businessNameDraft.trim()) },
                        enabled = ownerCanEdit && businessNameDraft.trim().isNotBlank() &&
                            businessNameDraft.trim() != businessName.trim() && !state.isSavingBusinessName,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isSavingBusinessName) {
                            CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Save business name")
                    }
                    if (!ownerCanEdit) ReadOnlyHint()
                }

                SettingsSection(
                    title = "Butchery Module",
                    description = "Enable Butchery POS with custom payment methods and Price per KG calculations.",
                    icon = Icons.Default.Scale
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (butcheryEnabledDraft) "Butchery Module ON" else "Enable Butchery Module",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Separate meat counter POS, weight/price calculator, payment methods & kg reports.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = butcheryEnabledDraft,
                            onCheckedChange = { enabled ->
                                butcheryEnabledDraft = enabled
                                if (ownerCanEdit) {
                                    val price = butcheryBeefPriceDraft.toIntOrNull() ?: 680
                                    onSaveButcheryConfig(enabled, butcheryNameDraft.trim(), butcheryIsBeefOnly, price, butcheryPaymentMethodsDraft, butcheryCategoriesDraft)
                                }
                            },
                            enabled = ownerCanEdit,
                            modifier = Modifier.testTag("owner_settings_butchery_switch")
                        )
                    }

                    if (butcheryEnabledDraft) {
                        OutlinedTextField(
                            value = butcheryNameDraft,
                            onValueChange = { butcheryNameDraft = it.take(80) },
                            label = { Text("Enrolled Butchery Name") },
                            placeholder = { Text("e.g. Prime Cuts Butchery") },
                            singleLine = true,
                            enabled = ownerCanEdit,
                            modifier = Modifier.fillMaxWidth().testTag("owner_settings_butchery_name_input")
                        )

                        ButcheryMeatTypesEditor(
                            selectedCategories = butcheryCategoriesDraft,
                            enabled = ownerCanEdit,
                            onCategoriesChange = { butcheryCategoriesDraft = it }
                        )

                        val chosenButcheryProducts = remember(butcheryCuts, butcheryCategoriesDraft, butcheryBeefPriceDraft, butcheryConfig?.shopId) {
                            val sourceCuts = butcheryCuts.ifEmpty {
                                ButcheryPricing.defaultCuts(
                                    shopId = butcheryConfig?.shopId ?: "preview",
                                    beefPricePerKgKsh = butcheryBeefPriceDraft.toIntOrNull() ?: 680
                                )
                            }
                            sourceCuts.filter { cut ->
                                cut.active && butcheryCategoriesDraft.any { it.equals(cut.category, ignoreCase = true) }
                            }
                        }

                        if (chosenButcheryProducts.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (chosenButcheryProducts.size == 1) {
                                        "Displayed Product"
                                    } else {
                                        "Displayed Products (${chosenButcheryProducts.size})"
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                chosenButcheryProducts.forEach { product ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("owner_butchery_chosen_product_${product.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color.White,
                                                border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Image(
                                                    painter = painterResource(id = butcheryDrawableRes(product.iconKey)),
                                                    contentDescription = product.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(RoundedCornerShape(10.dp))
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = product.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${product.category} · KES ${product.pricePerKgKsh}/kg",
                                                    fontSize = 11.sp,
                                                    color = ButcheryPalette.EmeraldHeader,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = butcheryBeefPriceDraft,
                            onValueChange = { butcheryBeefPriceDraft = it.filter(Char::isDigit).take(5) },
                            label = { Text("Default $primaryMeatLabel Price per kg (KES)") },
                            supportingText = {
                                Text("Digital-scale customers buying KES 100 / 150 / 200 compute exact kg from this rate.")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            enabled = ownerCanEdit,
                            modifier = Modifier.fillMaxWidth().testTag("owner_settings_beef_price_input")
                        )

                        ButcheryPaymentMethodsEditor(
                            methods = butcheryPaymentMethodsDraft,
                            enabled = ownerCanEdit,
                            onMethodsChange = { butcheryPaymentMethodsDraft = it }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val price = butcheryBeefPriceDraft.toIntOrNull() ?: 680
                                    onSaveButcheryConfig(true, butcheryNameDraft.trim(), butcheryIsBeefOnly, price, butcheryPaymentMethodsDraft, butcheryCategoriesDraft)
                                },
                                enabled = ownerCanEdit && butcheryNameDraft.trim().isNotEmpty(),
                                modifier = Modifier.weight(1f).testTag("owner_settings_save_butchery_button")
                            ) {
                                Text("Save Butchery", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    val price = butcheryBeefPriceDraft.toIntOrNull() ?: 680
                                    if (ownerCanEdit) {
                                        onSaveButcheryConfig(true, butcheryNameDraft.trim(), butcheryIsBeefOnly, price, butcheryPaymentMethodsDraft, butcheryCategoriesDraft)
                                    }
                                    onOpenButcheryWorkspace(ButcheryTab.SETTINGS)
                                },
                                modifier = Modifier.weight(1f).testTag("owner_settings_butchery_cuts_button")
                            ) {
                                Text("Cuts & Prices")
                            }
                        }

                        Button(
                            onClick = {
                                val price = butcheryBeefPriceDraft.toIntOrNull() ?: 680
                                if (ownerCanEdit) {
                                    onSaveButcheryConfig(true, butcheryNameDraft.trim(), butcheryIsBeefOnly, price, butcheryPaymentMethodsDraft, butcheryCategoriesDraft)
                                }
                                onOpenButcheryWorkspace(ButcheryTab.SELL)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F8E4E)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("owner_settings_open_butchery_pos_button")
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open Butchery POS", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (!ownerCanEdit) ReadOnlyHint()
                }

                SettingsSection(
                    title = "Subscription & M-Pesa billing",
                    description = "Manage Daraja M-Pesa renewals and store entitlement.",
                    icon = Icons.Default.CreditCard
                ) {
                    val entitlement = state.subscriptionEntitlement
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (entitlement != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (entitlement.isEntitled) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = entitlement.statusLabel,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (entitlement.isEntitled) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        }
                                    )
                                    Text(
                                        text = "Valid until ${entitlement.expiryFormatted} · KSh ${entitlement.amountDueKsh} / ${entitlement.planDays} days",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Text(
                            "One subscription covers all counters, staff, and devices in this shop.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onOpenSubscription,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A349)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Manage / Renew Subscription", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                SettingsSection(
                    title = "Team & working hours",
                    description = "Manage waiters, access, and schedules in one place.",
                    icon = Icons.Default.Badge
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                if (waiters.isEmpty()) "No waiters added yet" else "${waiters.size} waiter${if (waiters.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Passkeys, QR approvals, and duty hours",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (state.isLoadingEmployeeMembers) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = onLoadMembers, enabled = role in setOf("owner", "manager")) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh waiter count")
                            }
                        }
                    }
                    Button(
                        onClick = onOpenStaffManagement,
                        enabled = role in setOf("owner", "manager"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Manage team", fontWeight = FontWeight.SemiBold)
                    }
                    if (!ownerCanEdit) ReadOnlyHint()
                }

                SettingsSection(
                    title = "Service counters",
                    description = if (isLiquorStore) "Add, rename, or remove checkout counters." else "Add, rename, or remove service counters.",
                    icon = Icons.Default.Tune
                ) {
                    if (isLiquorStore && registeredCounters.size <= 1) {
                        Text(
                            text = "Liquor Store default is 1 counter. Add more counters below whenever needed.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (registeredCounters.isEmpty()) {
                        Text(
                            "No registered counters yet. Add your first counter below.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        registeredCounters.forEach { counter ->
                            CounterSettingsRow(
                                counter = counter,
                                ownerCanEdit = ownerCanEdit && !state.isManagingCounters,
                                canDelete = registeredCounters.size > 1,
                                editing = editingCounterId == counter.id,
                                editingName = editingCounterName,
                                onStartEditing = {
                                    editingCounterId = counter.id
                                    editingCounterName = counter.name
                                },
                                onEditingNameChange = { editingCounterName = it.take(60) },
                                onRename = {
                                    onCounterRename(counter.id, editingCounterName.trim())
                                    editingCounterId = null
                                },
                                onRequestDelete = { counterPendingDeletion = counter }
                            )
                        }
                        if (registeredCounters.size == 1) {
                            Text(
                                text = "At least 1 active service counter is required. Add another counter before removing this one.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newCounterName,
                            onValueChange = { newCounterName = it.take(60) },
                            label = { Text("New counter name") },
                            placeholder = {
                                Text(if (registeredCounters.isEmpty()) "Main Counter" else "Counter ${registeredCounters.size + 1}")
                            },
                            singleLine = true,
                            enabled = ownerCanEdit && !state.isManagingCounters,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("new_counter_name_input")
                        )
                        Button(
                            onClick = {
                                onCounterCreate(newCounterName.trim())
                                newCounterName = ""
                            },
                            enabled = ownerCanEdit && !state.isManagingCounters && newCounterName.trim().isNotBlank(),
                            modifier = Modifier.testTag("add_counter_button")
                        ) {
                            if (state.isManagingCounters) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add")
                            }
                        }
                    }
                    if (!ownerCanEdit) ReadOnlyHint()
                }

                Spacer(Modifier.size(6.dp))
            }
        }
    }

    val targetCounter = counterPendingDeletion
    if (targetCounter != null) {
        AlertDialog(
            onDismissRequest = { if (!state.isManagingCounters) counterPendingDeletion = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("Delete ${targetCounter.name}?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This counter will be removed from checkout and bar selectors. Historical sales and reports remain preserved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCounterDelete(targetCounter.id)
                        counterPendingDeletion = null
                    },
                    enabled = !state.isManagingCounters,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_counter_button")
                ) {
                    Text("Delete Counter", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { counterPendingDeletion = null },
                    enabled = !state.isManagingCounters
                ) {
                    Text("Cancel")
                }
            }
        )
    }

}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(9.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            content()
        }
    }
}

@Composable
private fun SettingsMessage(message: String, isError: Boolean) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(color = container, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(message, color = content, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun ReadOnlyHint() {
    Text(
        "Only the business owner can change these settings.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun CounterSettingsRow(
    counter: ServiceCounter,
    ownerCanEdit: Boolean,
    canDelete: Boolean,
    editing: Boolean,
    editingName: String,
    onStartEditing: () -> Unit,
    onEditingNameChange: (String) -> Unit,
    onRename: () -> Unit,
    onRequestDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        if (editing) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = editingName,
                    onValueChange = onEditingNameChange,
                    label = { Text("Counter name") },
                    singleLine = true,
                    enabled = ownerCanEdit,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onRename, enabled = ownerCanEdit && editingName.trim().isNotBlank()) { Text("Save") }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(counter.name, fontWeight = FontWeight.SemiBold)
                        if (!counter.active) Text("Inactive", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(
                        onClick = onStartEditing,
                        enabled = ownerCanEdit,
                        modifier = Modifier.testTag("rename_counter_${counter.id}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename ${counter.name}")
                    }
                    IconButton(
                        onClick = onRequestDelete,
                        enabled = ownerCanEdit && canDelete,
                        modifier = Modifier.testTag("delete_counter_${counter.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete ${counter.name}",
                            tint = if (ownerCanEdit && canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }
    }
}
