package com.alvinb.counterpos.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onClose: () -> Unit,
    onBusinessNameSave: (String) -> Unit,
    onLoadMembers: () -> Unit,
    onCounterCreate: (String) -> Unit,
    onCounterRename: (String, String) -> Unit,
    onOpenStaffManagement: () -> Unit = {},
    onOpenSubscription: () -> Unit = {}
) {
    BackHandler(onBack = onClose)
    val ownerCanEdit = role.equals("owner", ignoreCase = true)
    val isLiquorStore = businessType.equals("counter_seller", ignoreCase = true)
    var businessNameDraft by remember(businessName) { mutableStateOf(businessName) }
    var newCounterName by remember { mutableStateOf("") }
    var editingCounterId by remember { mutableStateOf<String?>(null) }
    var editingCounterName by remember { mutableStateOf("") }

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
                    title = "Subscription & M-Pesa billing",
                    description = "Manage Daraja M-Pesa renewals and store entitlement.",
                    icon = Icons.Default.CreditCard
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "One subscription covers all counters, staff devices, and counters in this shop.",
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
                    title = if (isLiquorStore) "Service counter" else "Service counters",
                    description = if (isLiquorStore) "Your checkout point." else "Manage checkout points.",
                    icon = Icons.Default.Tune
                ) {
                    if (isLiquorStore) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = registeredCounters.firstOrNull()?.name ?: "Main Counter",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Single checkout with shared stock.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        if (registeredCounters.isEmpty()) {
                            Text("No registered counters were returned by the business service.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            registeredCounters.forEach { counter ->
                                CounterSettingsRow(
                                    counter = counter,
                                    ownerCanEdit = ownerCanEdit,
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
                                    }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = newCounterName,
                                onValueChange = { newCounterName = it.take(60) },
                                label = { Text("New counter name") },
                                placeholder = {
                                    Text(if (registeredCounters.isEmpty()) "Main Counter" else "Counter ${registeredCounters.size + 1}")
                                },
                                singleLine = true,
                                enabled = ownerCanEdit,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    onCounterCreate(newCounterName.trim())
                                    newCounterName = ""
                                },
                                enabled = ownerCanEdit && newCounterName.trim().isNotBlank()
                            ) {
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
    editing: Boolean,
    editingName: String,
    onStartEditing: () -> Unit,
    onEditingNameChange: (String) -> Unit,
    onRename: () -> Unit
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(counter.name, fontWeight = FontWeight.SemiBold)
                    if (!counter.active) Text("Inactive", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onStartEditing, enabled = ownerCanEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename ${counter.name}")
                }
            }
        }
    }
}
