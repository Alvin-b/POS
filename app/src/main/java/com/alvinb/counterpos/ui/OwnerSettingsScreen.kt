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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvinb.counterpos.data.remote.ServiceCounter
import org.json.JSONObject
import java.util.Locale
import java.util.regex.Pattern

private val SettingsHourMinutePattern = Pattern.compile("^(?:[01]\\d|2[0-3]):[0-5]\\d$")
private val SettingsWeekdayLabels = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

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
    themeMode: String,
    onClose: () -> Unit,
    onBusinessNameSave: (String) -> Unit,
    onIssueWaiterPass: () -> Unit,
    onCopyWaiterPass: (String) -> Unit,
    onShareWaiterPass: (String) -> Unit,
    onScanWaiterQr: () -> Unit,
    onLoadMembers: () -> Unit,
    onWaiterStatusChange: (String, String) -> Unit,
    onRemoveWaiter: (String) -> Unit,
    onScheduleSave: (String, List<EmployeeScheduleDay>) -> Unit,
    onScheduleClear: (String) -> Unit,
    onCounterCreate: (String) -> Unit,
    onCounterRename: (String, String) -> Unit,
    onThemeModeChange: (String) -> Unit,
    onOpenStaffManagement: () -> Unit = {}
) {
    BackHandler(onBack = onClose)
    val ownerCanEdit = role.equals("owner", ignoreCase = true)
    val isLiquorStore = businessType.equals("counter_seller", ignoreCase = true)
    var businessNameDraft by remember(businessName) { mutableStateOf(businessName) }
    var newCounterName by remember { mutableStateOf("") }
    var editingCounterId by remember { mutableStateOf<String?>(null) }
    var editingCounterName by remember { mutableStateOf("") }
    var expandedWaiterId by remember { mutableStateOf<String?>(null) }
    var removeCandidate by remember { mutableStateOf<Pair<String, String>?>(null) }

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
                    title = "Waiter access",
                    description = "Invite staff or approve a phone.",
                    icon = Icons.Default.Key
                ) {
                    Button(
                        onClick = onOpenStaffManagement,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Staff & Business Hours Hub", fontWeight = FontWeight.SemiBold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onIssueWaiterPass,
                            enabled = ownerCanEdit && !state.isIssuingEmployeePass,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.isIssuingEmployeePass) {
                                CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("Issue waiter pass")
                        }
                        OutlinedButton(
                            onClick = onScanWaiterQr,
                            enabled = ownerCanEdit,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Scan waiter QR")
                        }
                    }
                    state.employeePasskey?.takeIf { it.isNotBlank() }?.let { passkey ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Text("New waiter passkey", fontWeight = FontWeight.SemiBold)
                                Text(passkey, style = MaterialTheme.typography.headlineSmall, letterSpacing = 1.2.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { onCopyWaiterPass(passkey) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("Copy")
                                    }
                                    Button(
                                        onClick = { onShareWaiterPass(passkey) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("Share")
                                    }
                                }
                            }
                        }
                    }
                    if (!ownerCanEdit) ReadOnlyHint()
                }

                SettingsSection(
                    title = "Registered waiters",
                    description = "Manage staff access.",
                    icon = Icons.Default.Store
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (waiters.isEmpty()) "No registered waiters" else "${waiters.size} registered waiter${if (waiters.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onLoadMembers, enabled = !state.isLoadingEmployeeMembers) {
                            if (state.isLoadingEmployeeMembers) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh registered waiters")
                            }
                        }
                    }
                    if (waiters.isEmpty()) {
                        Text(
                            "No staff yet. Use the actions above to add one.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    waiters.forEach { (member, email) ->
                        WaiterCard(
                            member = member,
                            email = email,
                            ownerCanEdit = ownerCanEdit,
                            expanded = expandedWaiterId == member.optString("member_user_id"),
                            isSavingSchedule = state.isSavingEmployeeSchedule || state.isClearingEmployeeSchedule,
                            onToggleExpanded = {
                                expandedWaiterId = if (expandedWaiterId == member.optString("member_user_id")) null else member.optString("member_user_id")
                            },
                            onStatusChange = { status -> onWaiterStatusChange(member.optString("member_user_id"), status) },
                            onRemove = { removeCandidate = member.optString("member_user_id") to email },
                            onScheduleSave = onScheduleSave,
                            onScheduleClear = onScheduleClear
                        )
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

                SettingsSection(
                    title = "Appearance",
                    description = "Choose the app theme.",
                    icon = Icons.Default.DarkMode
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                            FilterChip(
                                selected = themeMode.equals(value, ignoreCase = true),
                                onClick = { onThemeModeChange(value) },
                                enabled = ownerCanEdit,
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (!ownerCanEdit) ReadOnlyHint()
                }
                Spacer(Modifier.size(6.dp))
            }
        }
    }

    removeCandidate?.let { (userId, email) ->
        AlertDialog(
            onDismissRequest = { removeCandidate = null },
            title = { Text("Remove waiter access?") },
            text = {
                Text("Remove $email from this business? The account will lose access, while recorded sales history is retained. This cannot be undone from Settings.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        removeCandidate = null
                        expandedWaiterId = null
                        onRemoveWaiter(userId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Remove access") }
            },
            dismissButton = { TextButton(onClick = { removeCandidate = null }) { Text("Keep waiter") } }
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
private fun WaiterCard(
    member: JSONObject,
    email: String,
    ownerCanEdit: Boolean,
    expanded: Boolean,
    isSavingSchedule: Boolean,
    onToggleExpanded: () -> Unit,
    onStatusChange: (String) -> Unit,
    onRemove: () -> Unit,
    onScheduleSave: (String, List<EmployeeScheduleDay>) -> Unit,
    onScheduleClear: (String) -> Unit
) {
    val userId = member.optString("member_user_id").trim()
    val status = memberStatus(member)
    val normalizedStatus = status.lowercase(Locale.ROOT)
    val canReactivate = normalizedStatus in setOf("suspended", "locked")
    val canLogOut = normalizedStatus == "active"
    val dutySummary = remember(member.toString()) {
        val enabledDays = readEmployeeSchedule(member).filter(EmployeeScheduleDay::enabled)
        if (enabledDays.isEmpty()) {
            "Duty hours: no personal schedule configured (unrestricted)"
        } else {
            "Duty hours: " + enabledDays.joinToString(" · ") { day ->
                "${SettingsWeekdayLabels[day.weekday].take(3)} ${day.start}–${day.end}"
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(email, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Waiter · ${if (status.isBlank()) "STATUS UNAVAILABLE" else status.uppercase(Locale.ROOT)}",
                        color = if (normalizedStatus == "active") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        dutySummary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onToggleExpanded, enabled = ownerCanEdit && userId.isNotBlank()) {
                    Text(if (expanded) "Hide details" else "Manage")
                }
            }
            if (ownerCanEdit && userId.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    when {
                        canLogOut -> OutlinedButton(
                            onClick = { onStatusChange("suspended") },
                            modifier = Modifier.weight(1f)
                        ) { Text("Log out waiter") }
                        canReactivate -> Button(
                            onClick = { onStatusChange("active") },
                            modifier = Modifier.weight(1f)
                        ) { Text("Reactivate") }
                        else -> Text("Status changes are unavailable for ${status.ifBlank { "this status" }}.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }
                    OutlinedButton(
                        onClick = onRemove,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Remove")
                    }
                }
                Text(
                    if (canLogOut) "Logging out locks business access on the next server authorization check." else "Reactivate only when this registered waiter should regain business access.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (expanded && ownerCanEdit && userId.isNotBlank()) {
                WaiterScheduleEditor(
                    member = member,
                    userId = userId,
                    busy = isSavingSchedule,
                    onSave = onScheduleSave,
                    onClear = onScheduleClear
                )
            }
        }
    }
}

@Composable
private fun WaiterScheduleEditor(
    member: JSONObject,
    userId: String,
    busy: Boolean,
    onSave: (String, List<EmployeeScheduleDay>) -> Unit,
    onClear: (String) -> Unit
) {
    var days by remember(member.toString()) { mutableStateOf(readEmployeeSchedule(member)) }
    var validationError by remember(member.toString()) { mutableStateOf<String?>(null) }
    val hasEnabledDay = days.any(EmployeeScheduleDay::enabled)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Duty schedule", style = MaterialTheme.typography.titleSmall)
        if (!hasEnabledDay) {
            Text(
                "No enabled saved schedule. This waiter has unrestricted access until a schedule is saved.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                "Only enabled days limit access. Use HH:mm values; the server enforces these hours.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        days.forEachIndexed { index, day ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Switch(
                    checked = day.enabled,
                    onCheckedChange = { enabled -> days = days.updated(index, day.copy(enabled = enabled)) },
                    enabled = !busy
                )
                Text(SettingsWeekdayLabels[day.weekday], modifier = Modifier.width(84.dp), fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = day.start,
                    onValueChange = { value -> days = days.updated(index, day.copy(start = value.take(5))) },
                    label = { Text("Start") },
                    placeholder = { Text("HH:mm") },
                    singleLine = true,
                    enabled = day.enabled && !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(7.dp))
                OutlinedTextField(
                    value = day.end,
                    onValueChange = { value -> days = days.updated(index, day.copy(end = value.take(5))) },
                    label = { Text("End") },
                    placeholder = { Text("HH:mm") },
                    singleLine = true,
                    enabled = day.enabled && !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        validationError?.let { error -> Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    val enabledDays = days.filter(EmployeeScheduleDay::enabled)
                    val invalid = enabledDays.any {
                        !SettingsHourMinutePattern.matcher(it.start.trim()).matches() ||
                            !SettingsHourMinutePattern.matcher(it.end.trim()).matches() ||
                            it.start.trim() == it.end.trim()
                    }
                    validationError = when {
                        enabledDays.isEmpty() -> "Enable at least one day, or clear the schedule for unrestricted access."
                        invalid -> "Every enabled day needs distinct, valid HH:mm start and end values."
                        else -> null
                    }
                    if (validationError == null) onSave(userId, days)
                },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Save schedule")
            }
            OutlinedButton(
                onClick = { validationError = null; onClear(userId) },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text("Clear schedule") }
        }
    }
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

private fun memberStatus(member: JSONObject): String {
    val employmentStatus = member.optString("employment_status").trim()
    return if (employmentStatus.isNotBlank()) employmentStatus else member.optString("status").trim()
}

private fun readEmployeeSchedule(member: JSONObject): List<EmployeeScheduleDay> {
    val days = MutableList(7) { weekday -> EmployeeScheduleDay(weekday = weekday) }
    val schedule = member.optJSONArray("schedule") ?: return days
    for (index in 0 until schedule.length()) {
        val row = schedule.optJSONObject(index) ?: continue
        val weekday = readWeekday(row)
        if (weekday !in 0..6) continue
        val start = firstNonBlank(row, "start", "start_time", "startTime").take(5)
        val end = firstNonBlank(row, "end", "end_time", "endTime").take(5)
        val enabled = row.has("enabled") && row.optBoolean("enabled", false)
        days[weekday] = EmployeeScheduleDay(weekday, start, end, enabled)
    }
    return days
}

private fun readWeekday(row: JSONObject): Int {
    val raw = row.opt("weekday") ?: row.opt("day_of_week") ?: row.opt("day") ?: return -1
    if (raw is Number) return raw.toInt()
    return when (raw.toString().trim().lowercase(Locale.ROOT)) {
        "sunday", "sun" -> 0
        "monday", "mon" -> 1
        "tuesday", "tue", "tues" -> 2
        "wednesday", "wed" -> 3
        "thursday", "thu", "thur", "thurs" -> 4
        "friday", "fri" -> 5
        "saturday", "sat" -> 6
        else -> raw.toString().trim().toIntOrNull() ?: -1
    }
}

private fun firstNonBlank(row: JSONObject, vararg keys: String): String =
    keys.asSequence().map { row.optString(it).trim() }.firstOrNull { it.isNotBlank() }.orEmpty()

private fun <T> List<T>.updated(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }
