package com.alvinb.counterpos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvinb.counterpos.data.butchery.ButcheryPricing
import com.alvinb.counterpos.data.local.entity.ButcheryConfigEntity
import com.alvinb.counterpos.data.local.entity.ButcheryCutEntity
import com.alvinb.counterpos.data.remote.ServiceCounter
import java.util.Locale

/**
 * Clean, well-structured Owner Settings Hub.
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
    onOpenButcheryEnrollment: () -> Unit = {},
    onOpenStaffManagement: () -> Unit = {},
    onOpenSubscription: () -> Unit = {}
) {
    BackHandler(onBack = onClose)
    val ownerCanEdit = role.equals("owner", ignoreCase = true)
    var businessNameDraft by remember(businessName) { mutableStateOf(businessName) }
    var butcheryEnabledDraft by remember(butcheryConfig?.enabled) { mutableStateOf(butcheryConfig?.enabled ?: false) }
    var butcheryNameDraft by remember(butcheryConfig?.butcheryName, businessName) {
        mutableStateOf(
            butcheryConfig?.butcheryName?.takeIf(String::isNotBlank)
                ?: if (businessName.isNotBlank()) "$businessName Butchery" else "Main Butchery"
        )
    }
    var butcheryCategoriesDraft by remember(butcheryConfig?.enabledCategoriesJson, butcheryConfig?.beefOnlyMode) {
        mutableStateOf(ButcheryPricing.resolveEnabledMeats(butcheryConfig))
    }
    var butcheryBeefPriceDraft by remember(butcheryConfig?.defaultBeefPricePerKgKsh) {
        mutableStateOf((butcheryConfig?.defaultBeefPricePerKgKsh ?: 680).toString())
    }
    var butcheryPaymentMethodsDraft by remember(butcheryConfig?.paymentMethodsJson) {
        mutableStateOf(ButcheryPricing.parsePaymentMethods(butcheryConfig?.paymentMethodsJson))
    }
    var showButcheryPaymentsEditor by remember { mutableStateOf(false) }

    val butcheryIsBeefOnly = butcheryCategoriesDraft.size == 1 &&
        butcheryCategoriesDraft.first().equals("Beef", ignoreCase = true)
    val primaryMeatLabel = if (butcheryCategoriesDraft.size == 1) {
        butcheryCategoriesDraft.first()
    } else {
        "Beef"
    }

    var newCounterName by remember { mutableStateOf("") }
    var editingCounterId by remember { mutableStateOf<String?>(null) }
    var editingCounterName by remember { mutableStateOf("") }
    var counterPendingDeletion by remember { mutableStateOf<ServiceCounter?>(null) }

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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (ownerCanEdit) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = if (ownerCanEdit) "Owner" else "View Only",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (ownerCanEdit) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!state.actionError.isNullOrBlank()) {
                    SettingsMessage(message = state.actionError.orEmpty(), isError = true)
                }
                if (!state.notice.isNullOrBlank()) {
                    SettingsMessage(message = state.notice.orEmpty(), isError = false)
                }

                // 1. Business Profile (compact inline row)
                SettingsCard {
                    SettingsHeaderRow(
                        icon = Icons.Default.Business,
                        title = "Business"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = businessNameDraft,
                            onValueChange = { businessNameDraft = it.take(80) },
                            label = { Text("Business name") },
                            singleLine = true,
                            enabled = ownerCanEdit && !state.isSavingBusinessName,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onBusinessNameSave(businessNameDraft.trim()) },
                            enabled = ownerCanEdit && businessNameDraft.trim().isNotBlank() &&
                                businessNameDraft.trim() != businessName.trim() && !state.isSavingBusinessName,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            if (state.isSavingBusinessName) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Save", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 2. Team & Subscription (clean grouped action rows)
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsIconBadge(Icons.Default.Badge)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Team & Waiters",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (waiters.isEmpty()) "No waiters yet" else "${waiters.size} active waiter${if (waiters.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.isLoadingEmployeeMembers) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(
                                onClick = onLoadMembers,
                                enabled = role in setOf("owner", "manager")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh waiters")
                            }
                        }
                        FilledTonalButton(
                            onClick = onOpenStaffManagement,
                            enabled = role in setOf("owner", "manager"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("Manage", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                    val entitlement = state.subscriptionEntitlement
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsIconBadge(Icons.Default.CreditCard)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Subscription",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (entitlement != null) {
                                    "${entitlement.statusLabel} · Until ${entitlement.expiryFormatted}"
                                } else {
                                    "M-Pesa billing"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (entitlement?.isEntitled == false) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = onOpenSubscription,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A349)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("Renew", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                // 3. Butchery Module (clean, structured, concise)
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsIconBadge(
                            icon = Icons.Default.Scale,
                            containerColor = Color(0xFF1F8E4E).copy(alpha = 0.14f),
                            iconTint = Color(0xFF1F8E4E)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Butchery Module",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (butcheryEnabledDraft) "Enabled" else "Turn on to manage meat counter",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = butcheryEnabledDraft,
                            onCheckedChange = { enabled ->
                                butcheryEnabledDraft = enabled
                                if (ownerCanEdit) {
                                    val price = butcheryBeefPriceDraft.toIntOrNull() ?: 680
                                    onSaveButcheryConfig(
                                        enabled,
                                        butcheryNameDraft.trim(),
                                        butcheryIsBeefOnly,
                                        price,
                                        butcheryPaymentMethodsDraft,
                                        butcheryCategoriesDraft
                                    )
                                }
                            },
                            enabled = ownerCanEdit,
                            modifier = Modifier.testTag("owner_settings_butchery_switch")
                        )
                    }

                    AnimatedVisibility(visible = butcheryEnabledDraft) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                            OutlinedTextField(
                                value = butcheryNameDraft,
                                onValueChange = { butcheryNameDraft = it.take(80) },
                                label = { Text("Butchery name") },
                                placeholder = { Text("e.g. Prime Cuts Butchery") },
                                singleLine = true,
                                enabled = ownerCanEdit,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("owner_settings_butchery_name_input")
                            )

                            OutlinedTextField(
                                value = butcheryBeefPriceDraft,
                                onValueChange = { butcheryBeefPriceDraft = it.filter(Char::isDigit).take(5) },
                                label = { Text("$primaryMeatLabel price / kg (KES)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                enabled = ownerCanEdit,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("owner_settings_beef_price_input")
                            )

                            ButcheryMeatTypesEditor(
                                selectedMeats = butcheryCategoriesDraft,
                                enabled = ownerCanEdit,
                                availableMeats = ButcheryPricing.availableMeatChoices(butcheryCuts, butcheryCategoriesDraft),
                                onMeatsChange = { nextMeats ->
                                    butcheryCategoriesDraft = nextMeats
                                    if (ownerCanEdit && butcheryNameDraft.trim().isNotEmpty()) {
                                        val price = butcheryBeefPriceDraft.toIntOrNull() ?: 680
                                        val nextBeefOnly = nextMeats.size == 1 &&
                                            nextMeats.first().equals("Beef", ignoreCase = true)
                                        onSaveButcheryConfig(
                                            true,
                                            butcheryNameDraft.trim(),
                                            nextBeefOnly,
                                            price,
                                            butcheryPaymentMethodsDraft,
                                            nextMeats
                                        )
                                    }
                                }
                            )

                            // Compact Payment Methods disclosure row
                            Surface(
                                onClick = { showButcheryPaymentsEditor = !showButcheryPaymentsEditor },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Payments,
                                        contentDescription = null,
                                        tint = Color(0xFF1F8E4E),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Payment Methods",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = butcheryPaymentMethodsDraft.joinToString(" · "),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = if (showButcheryPaymentsEditor) {
                                            Icons.Default.KeyboardArrowUp
                                        } else {
                                            Icons.Default.KeyboardArrowDown
                                        },
                                        contentDescription = if (showButcheryPaymentsEditor) "Collapse" else "Edit",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            AnimatedVisibility(visible = showButcheryPaymentsEditor) {
                                ButcheryPaymentMethodsEditor(
                                    methods = butcheryPaymentMethodsDraft,
                                    enabled = ownerCanEdit,
                                    onMethodsChange = { butcheryPaymentMethodsDraft = it }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                            // Clean 2x2 action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val price = butcheryBeefPriceDraft.toIntOrNull() ?: 680
                                        onSaveButcheryConfig(
                                            true,
                                            butcheryNameDraft.trim(),
                                            butcheryIsBeefOnly,
                                            price,
                                            butcheryPaymentMethodsDraft,
                                            butcheryCategoriesDraft
                                        )
                                    },
                                    enabled = ownerCanEdit && butcheryNameDraft.trim().isNotEmpty(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("owner_settings_save_butchery_button")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        val price = butcheryBeefPriceDraft.toIntOrNull() ?: 680
                                        if (ownerCanEdit) {
                                            onSaveButcheryConfig(
                                                true,
                                                butcheryNameDraft.trim(),
                                                butcheryIsBeefOnly,
                                                price,
                                                butcheryPaymentMethodsDraft,
                                                butcheryCategoriesDraft
                                            )
                                        }
                                        onOpenButcheryWorkspace(ButcheryTab.SELL)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F8E4E)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("owner_settings_open_butchery_pos_button")
                                ) {
                                    Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open POS", fontWeight = FontWeight.Bold)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        val price = butcheryBeefPriceDraft.toIntOrNull() ?: 680
                                        if (ownerCanEdit) {
                                            onSaveButcheryConfig(
                                                true,
                                                butcheryNameDraft.trim(),
                                                butcheryIsBeefOnly,
                                                price,
                                                butcheryPaymentMethodsDraft,
                                                butcheryCategoriesDraft
                                            )
                                        }
                                        onOpenButcheryWorkspace(ButcheryTab.SETTINGS)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("owner_settings_butchery_cuts_button")
                                ) {
                                    Text("Meats & Stock", fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }

                                if (ownerCanEdit) {
                                    FilledTonalButton(
                                        onClick = {
                                            val price = butcheryBeefPriceDraft.toIntOrNull() ?: 680
                                            onSaveButcheryConfig(
                                                true,
                                                butcheryNameDraft.trim(),
                                                butcheryIsBeefOnly,
                                                price,
                                                butcheryPaymentMethodsDraft,
                                                butcheryCategoriesDraft
                                            )
                                            onOpenButcheryEnrollment()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .testTag("owner_settings_enroll_butchery_button")
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Enroll Phone", fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Service Counters (compact list + inline Add)
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsIconBadge(Icons.Default.Tune)
                        Text(
                            text = "Service Counters",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${registeredCounters.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (registeredCounters.isEmpty()) {
                        Text(
                            text = "No counters yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                registeredCounters.forEachIndexed { index, counter ->
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
                                    if (index < registeredCounters.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    }
                                }
                            }
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
                            label = { Text("New counter") },
                            placeholder = {
                                Text(if (registeredCounters.isEmpty()) "Main Counter" else "Counter ${registeredCounters.size + 1}")
                            },
                            singleLine = true,
                            enabled = ownerCanEdit && !state.isManagingCounters,
                            shape = RoundedCornerShape(12.dp),
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
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("add_counter_button")
                        ) {
                            if (state.isManagingCounters) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
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
                    "Past sales and reports stay saved.",
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
                    Text("Delete", fontWeight = FontWeight.Bold)
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
private fun SettingsCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsIconBadge(
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun SettingsHeaderRow(
    icon: ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsIconBadge(icon)
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
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
    if (editing) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = editingName,
                onValueChange = onEditingNameChange,
                label = { Text("Counter name") },
                singleLine = true,
                enabled = ownerCanEdit,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onRename,
                enabled = ownerCanEdit && editingName.trim().isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save")
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = counter.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                if (!counter.active) {
                    Text(
                        text = "Inactive",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            IconButton(
                onClick = onStartEditing,
                enabled = ownerCanEdit,
                modifier = Modifier.testTag("rename_counter_${counter.id}")
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Rename ${counter.name}", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = onRequestDelete,
                enabled = ownerCanEdit && canDelete,
                modifier = Modifier.testTag("delete_counter_${counter.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete ${counter.name}",
                    tint = if (ownerCanEdit && canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
