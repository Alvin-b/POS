package com.alvinb.counterpos.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvinb.counterpos.data.model.BusinessHours
import com.alvinb.counterpos.data.model.DayBusinessHours
import com.alvinb.counterpos.data.model.TimeSlot
import com.alvinb.counterpos.data.service.BusinessHoursValidationResult
import com.alvinb.counterpos.data.service.BusinessHoursValidationService
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

private val EmployeeWeekdayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
private val EmployeeTimePattern = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")

private fun readEmployeeSchedule(member: JSONObject): List<EmployeeScheduleDay> {
    val rows = member.optJSONArray("schedule") ?: JSONArray()
    val byWeekday = buildMap {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val weekday = row.optInt("weekday", -1)
            if (weekday in 0..6) put(weekday, row)
        }
    }
    return (0..6).map { weekday ->
        val row = byWeekday[weekday]
        EmployeeScheduleDay(
            weekday = weekday,
            start = row?.optString("start_time").orEmpty().take(5),
            end = row?.optString("end_time").orEmpty().take(5),
            enabled = row?.optBoolean("enabled", false) ?: false
        )
    }
}

private fun employeeScheduleSummary(member: JSONObject): String {
    val enabled = readEmployeeSchedule(member).filter(EmployeeScheduleDay::enabled)
    if (enabled.isEmpty()) return "No personal duty hours configured"
    return enabled.joinToString(" · ") { day ->
        "${EmployeeWeekdayNames[day.weekday].take(3)} ${day.start}–${day.end}"
    }
}

private enum class StaffManagementTab(val title: String) {
    ENROLL("Enroll Waiter"),
    STAFF_LIST("Staff Roster"),
    HOURS("Business Hours")
}

private enum class EnrollmentMode {
    PASSKEY,
    QR_CODE
}

@Composable
fun StaffManagementScreen(
    state: CounterPosUiState,
    businessName: String,
    role: String,
    businessType: String,
    startOnBusinessHours: Boolean = false,
    onClose: () -> Unit,
    onLoadMembers: () -> Unit,
    onIssueWaiterPass: (String) -> Unit,
    onGenerateWaiterQr: (String) -> Unit,
    onClearWaiterQr: () -> Unit,
    onSetEmployeeStatus: (String, String) -> Unit,
    onSetEmployeeRole: (String, String) -> Unit,
    onRemoveEmployee: (String) -> Unit,
    onSaveEmployeeSchedule: (String, Int, String, String, Boolean) -> Unit,
    onSaveBusinessHours: (BusinessHours) -> Unit,
    onScanWaiterCode: () -> Unit
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ownerCanEdit = role.equals("owner", ignoreCase = true) || role.equals("manager", ignoreCase = true)
    val ownerCanEditHours = role.equals("owner", ignoreCase = true)

    var selectedTab by remember(startOnBusinessHours) {
        mutableStateOf(if (startOnBusinessHours) StaffManagementTab.HOURS else StaffManagementTab.ENROLL)
    }
    var enrollmentMode by remember { mutableStateOf(EnrollmentMode.PASSKEY) }
    var selectedRoleToEnroll by remember { mutableStateOf("cashier") }

    // Roster state
    var staffSearchQuery by remember { mutableStateOf("") }
    var staffStatusFilter by remember { mutableStateOf("all") } // "all", "active", "suspended"
    var expandedStaffUserId by remember { mutableStateOf<String?>(null) }
    var deleteConfirmStaff by remember { mutableStateOf<Pair<String, String>?>(null) } // userId to name

    // Business hours draft state
    val validationService = remember { BusinessHoursValidationService() }
    var businessHoursDraft by remember(state.businessHours) { mutableStateOf(state.businessHours) }
    val liveValidationResult by remember(businessHoursDraft) {
        derivedStateOf { validationService.validate(businessHoursDraft) }
    }

    LaunchedEffect(Unit) {
        onLoadMembers()
    }
    LaunchedEffect(state.notice, state.actionError) {
        when {
            !state.actionError.isNullOrBlank() -> snackbarHostState.showSnackbar(state.actionError)
            state.notice == "Business hours saved." -> snackbarHostState.showSnackbar("Business hours saved. POS access rules updated.")
        }
    }

    // Filter staff members
    val allStaffMembers = remember(state.employeeMembers) {
        state.employeeMembers.mapNotNull { member ->
            val userId = member.optString("user_id").ifBlank {
                member.optString("member_user_id").ifBlank { member.optString("id") }
            }
            val email = member.optString("email").trim()
            val staffRole = member.optString("role", "cashier")
            val status = member.optString("employment_status", member.optString("status", "active"))
            if (userId.isNotBlank() || email.isNotBlank()) {
                member
            } else null
        }
    }

    val filteredStaff = remember(allStaffMembers, staffSearchQuery, staffStatusFilter) {
        allStaffMembers.filter { member ->
            val email = member.optString("email")
            val name = member.optString("name", member.optString("full_name"))
            val status = member.optString("employment_status", member.optString("status", "active")).lowercase()
            val queryMatch = staffSearchQuery.isBlank() ||
                email.contains(staffSearchQuery, ignoreCase = true) ||
                name.contains(staffSearchQuery, ignoreCase = true)
            val statusMatch = when (staffStatusFilter) {
                "active" -> status == "active"
                "suspended" -> status in setOf("suspended", "terminated", "locked")
                else -> true
            }
            queryMatch && statusMatch
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose, modifier = Modifier.testTag("staff_screen_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to workspace")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Staff Management",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$businessName · Waiters & Duty Hours",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onLoadMembers() }, modifier = Modifier.testTag("refresh_staff_button")) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh staff list")
                        }
                    }

                    TabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        StaffManagementTab.values().forEach { tab ->
                            Tab(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                text = {
                                    Text(
                                        text = when (tab) {
                                            StaffManagementTab.STAFF_LIST -> "Staff (${allStaffMembers.size})"
                                            else -> tab.title
                                        },
                                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTab) {
                StaffManagementTab.ENROLL -> {
                    EnrollWaitersTabContent(
                        state = state,
                        ownerCanEdit = ownerCanEdit,
                        enrollmentMode = enrollmentMode,
                        selectedRole = selectedRoleToEnroll,
                        onRoleChange = { selectedRoleToEnroll = it },
                        onModeChange = { enrollmentMode = it },
                        onIssuePasskey = { onIssueWaiterPass(selectedRoleToEnroll) },
                        onGenerateQr = { onGenerateWaiterQr(selectedRoleToEnroll) },
                        onClearQr = onClearWaiterQr,
                        onCopy = { text ->
                            clipboardManager.setText(AnnotatedString(text))
                            scope.launch { snackbarHostState.showSnackbar("Copied to clipboard!") }
                        },
                        onShare = { text ->
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Your Counter POS waiter enrollment code for $businessName: $text\nOpen the app and scan or enter this code to join.")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Waiter Passkey"))
                        },
                        onScanWaiterCode = onScanWaiterCode
                    )
                }

                StaffManagementTab.STAFF_LIST -> {
                    StaffRosterTabContent(
                        state = state,
                        staffList = filteredStaff,
                        allStaffCount = allStaffMembers.size,
                        searchQuery = staffSearchQuery,
                        onSearchChange = { staffSearchQuery = it },
                        statusFilter = staffStatusFilter,
                        onStatusFilterChange = { staffStatusFilter = it },
                        ownerCanEdit = ownerCanEdit,
                        expandedUserId = expandedStaffUserId,
                        onToggleExpand = { userId ->
                            expandedStaffUserId = if (expandedStaffUserId == userId) null else userId
                        },
                        onSetStatus = onSetEmployeeStatus,
                        onRequestDelete = { userId, name -> deleteConfirmStaff = userId to name },
                        onSaveSchedule = onSaveEmployeeSchedule,
                        onGoToEnroll = { selectedTab = StaffManagementTab.ENROLL }
                    )
                }

                StaffManagementTab.HOURS -> {
                    BusinessHoursTabContent(
                        hours = businessHoursDraft,
                        onHoursChange = { businessHoursDraft = it },
                        validationResult = liveValidationResult,
                        ownerCanEdit = ownerCanEditHours,
                        isSaving = state.isSavingBusinessHours,
                        error = state.actionError,
                        onSaveHours = {
                            onSaveBusinessHours(businessHoursDraft)
                        }
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    deleteConfirmStaff?.let { (userId, name) ->
        AlertDialog(
            onDismissRequest = { deleteConfirmStaff = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove Staff Member") },
            text = { Text("Are you sure you want to revoke workspace access for $name? Their account will be detached from $businessName.") },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveEmployee(userId)
                        deleteConfirmStaff = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove Staff")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmStaff = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ==============================================================================
// TAB 1: ENROLL WAITERS (Passkey & QR Code Generation)
// ==============================================================================

@Composable
private fun EnrollWaitersTabContent(
    state: CounterPosUiState,
    ownerCanEdit: Boolean,
    enrollmentMode: EnrollmentMode,
    selectedRole: String,
    onRoleChange: (String) -> Unit,
    onModeChange: (EnrollmentMode) -> Unit,
    onIssuePasskey: () -> Unit,
    onGenerateQr: () -> Unit,
    onClearQr: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onScanWaiterCode: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Enroll New Staff", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Issue an instant passkey or QR code", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Mode switcher: Passkey vs QR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = enrollmentMode == EnrollmentMode.PASSKEY,
                    onClick = { onModeChange(EnrollmentMode.PASSKEY) },
                    label = { Text("Passkey Code") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = enrollmentMode == EnrollmentMode.QR_CODE,
                    onClick = { onModeChange(EnrollmentMode.QR_CODE) },
                    label = { Text("QR Code") },
                    leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Role selection
            Text("Assigned Role", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("cashier" to "Waiter / Cashier", "inventory_clerk" to "Inventory", "manager" to "Manager").forEach { (roleKey, roleLabel) ->
                    FilterChip(
                        selected = selectedRole == roleKey,
                        onClick = { onRoleChange(roleKey) },
                        label = { Text(roleLabel, fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (enrollmentMode == EnrollmentMode.PASSKEY) {
                // Passkey Enrollment Mode
                Text(
                    text = "A one-time passkey creates the staff account automatically when redeemed on the staff phone. Share it privately via messaging or text.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onIssuePasskey,
                    enabled = ownerCanEdit && !state.isIssuingEmployeePass,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_waiter_passkey_button")
                ) {
                    if (state.isIssuingEmployeePass) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Generate Waiter Passkey")
                }

                state.employeePasskey?.takeIf { it.isNotBlank() }?.let { passkey ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("New Waiter Passkey", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text("One-time use", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Text(
                                text = passkey,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onCopy(passkey) },
                                    modifier = Modifier.weight(1f).testTag("copy_passkey_button")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy")
                                }
                                Button(
                                    onClick = { onShare(passkey) },
                                    modifier = Modifier.weight(1f).testTag("share_passkey_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Share")
                                }
                            }
                        }
                    }
                }
            } else {
                // QR Code Generation Mode
                Text(
                        text = "Display this QR code for the waiter to join the business.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onGenerateQr,
                    enabled = ownerCanEdit && !state.isGeneratingEnrollmentQr,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_waiter_qr_button")
                ) {
                    if (state.isGeneratingEnrollmentQr) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.waiterEnrollmentQrPayload != null) "Regenerate QR Code" else "Generate Enrollment QR")
                }

                state.waiterEnrollmentQrPayload?.let { qrPayload ->
                    val qrBitmap = remember(qrPayload) { createLoginQr(qrPayload) }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Scan with Waiter's Phone", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                modifier = Modifier.size(180.dp),
                                shadowElevation = 3.dp
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Waiter Enrollment QR Code",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                )
                            }

                            Text(
                                text = "Waiter points camera from the sign-in screen to enroll automatically.",
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onCopy(qrPayload) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Copy Code", fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = onClearQr,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Hide QR", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Alternative: Scan waiter's challenge code if waiter generated one first
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Waiter has a QR code on their phone?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Scan their QR challenge to authorize them right now.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onScanWaiterCode, modifier = Modifier.testTag("scan_waiter_challenge_button")) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan Phone")
                }
            }
        }
    }
}

// ==============================================================================
// TAB 2: STAFF ROSTER LIST (View, Suspend, Activate, Remove, Hours)
// ==============================================================================

@Composable
private fun StaffRosterTabContent(
    state: CounterPosUiState,
    staffList: List<JSONObject>,
    allStaffCount: Int,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    statusFilter: String,
    onStatusFilterChange: (String) -> Unit,
    ownerCanEdit: Boolean,
    expandedUserId: String?,
    onToggleExpand: (String) -> Unit,
    onSetStatus: (String, String) -> Unit,
    onRequestDelete: (String, String) -> Unit,
    onSaveSchedule: (String, Int, String, String, Boolean) -> Unit,
    onGoToEnroll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Search & Filter Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search staff by name or email…") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = if (searchQuery.isNotBlank()) {
                {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = statusFilter == "all",
                onClick = { onStatusFilterChange("all") },
                label = { Text("All ($allStaffCount)") }
            )
            FilterChip(
                selected = statusFilter == "active",
                onClick = { onStatusFilterChange("active") },
                label = { Text("Active") },
                leadingIcon = if (statusFilter == "active") ({ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }) else null
            )
            FilterChip(
                selected = statusFilter == "suspended",
                onClick = { onStatusFilterChange("suspended") },
                label = { Text("Suspended") },
                leadingIcon = if (statusFilter == "suspended") ({ Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp)) }) else null
            )
        }

        if (staffList.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (searchQuery.isNotBlank() || statusFilter != "all") "No staff members match the selected filter." else "No staff members enrolled yet.",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Use the 'Enroll Waiter' tab to generate a passkey or QR code for your staff to sign in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = onGoToEnroll, modifier = Modifier.padding(top = 6.dp)) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enroll First Waiter")
                    }
                }
            }
        } else {
            staffList.forEach { member ->
                val userId = member.optString("user_id").ifBlank {
                    member.optString("member_user_id").ifBlank { member.optString("id") }
                }
                val email = member.optString("email").trim()
                val name = member.optString("name", member.optString("full_name", email)).ifBlank { "Staff Member" }
                val role = member.optString("role", "cashier")
                val employmentStatus = member.optString("employment_status", member.optString("status", "active")).lowercase()
                val isSuspended = employmentStatus in setOf("suspended", "terminated", "locked")
                val isExpanded = expandedUserId == userId

                StaffMemberCard(
                    member = member,
                    userId = userId,
                    name = name,
                    email = email,
                    role = role,
                    isSuspended = isSuspended,
                    isExpanded = isExpanded,
                    ownerCanEdit = ownerCanEdit && !role.equals("owner", ignoreCase = true),
                    onToggleExpand = { onToggleExpand(userId) },
                    onToggleSuspend = {
                        val newStatus = if (isSuspended) "active" else "suspended"
                        onSetStatus(userId, newStatus)
                    },
                    onRequestDelete = { onRequestDelete(userId, name) },
                    onSaveSchedule = onSaveSchedule
                )
            }
        }
    }
}

@Composable
private fun StaffMemberCard(
    member: JSONObject,
    userId: String,
    name: String,
    email: String,
    role: String,
    isSuspended: Boolean,
    isExpanded: Boolean,
    ownerCanEdit: Boolean,
    onToggleExpand: () -> Unit,
    onToggleSuspend: () -> Unit,
    onRequestDelete: () -> Unit,
    onSaveSchedule: (String, Int, String, String, Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("staff_card_${userId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSuspended) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isSuspended) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar
                Surface(
                    shape = CircleShape,
                    color = if (isSuspended) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = name.firstOrNull()?.uppercase() ?: "W",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = if (isSuspended) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = email.ifBlank { "ID: $userId" },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSuspended) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, if (isSuspended) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isSuspended) MaterialTheme.colorScheme.error else Color(0xFF10B981))
                        )
                        Text(
                            text = if (isSuspended) "Suspended" else "Active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSuspended) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Quick Actions: Suspend / Reactivate & Duty Hours toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Suspend/Reactivate Button
                if (isSuspended) {
                    Button(
                        onClick = onToggleSuspend,
                        enabled = ownerCanEdit,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Reactivate", fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = onToggleSuspend,
                        enabled = ownerCanEdit,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Suspend Staff", fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (isExpanded) "Hide Schedule" else "Duty Hours", fontSize = 12.sp)
                }

                IconButton(
                    onClick = onRequestDelete,
                    enabled = ownerCanEdit
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove staff member", tint = MaterialTheme.colorScheme.error)
                }
            }

            Text(
                text = employeeScheduleSummary(member),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Per-weekday drafts initialize from the persisted member schedule; no sample shifts are shown.
            AnimatedVisibility(visible = isExpanded) {
                var scheduleDraft by remember(member.toString()) { mutableStateOf(readEmployeeSchedule(member)) }
                var scheduleError by remember(member.toString()) { mutableStateOf<String?>(null) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text("Staff Duty Shift Schedule", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        "Each day can have its own shift. Outside an enabled shift, this staff account is locked.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    scheduleDraft.forEach { day ->
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Switch(
                                    checked = day.enabled,
                                    enabled = ownerCanEdit,
                                    onCheckedChange = { enabled ->
                                        scheduleDraft = scheduleDraft.map {
                                            if (it.weekday == day.weekday) it.copy(enabled = enabled) else it
                                        }
                                    }
                                )
                                Text(EmployeeWeekdayNames[day.weekday], fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(if (day.enabled) "On duty" else "Off", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = day.start,
                                    onValueChange = { value ->
                                        scheduleDraft = scheduleDraft.map {
                                            if (it.weekday == day.weekday) it.copy(start = value.take(5)) else it
                                        }
                                    },
                                    label = { Text("Start (HH:mm)") },
                                    singleLine = true,
                                    enabled = ownerCanEdit,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = day.end,
                                    onValueChange = { value ->
                                        scheduleDraft = scheduleDraft.map {
                                            if (it.weekday == day.weekday) it.copy(end = value.take(5)) else it
                                        }
                                    },
                                    label = { Text("End (HH:mm)") },
                                    singleLine = true,
                                    enabled = ownerCanEdit,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    scheduleError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                    Button(
                        onClick = {
                            val invalid = scheduleDraft.any { day ->
                                day.enabled && (
                                    !EmployeeTimePattern.matches(day.start) ||
                                        !EmployeeTimePattern.matches(day.end) || day.start == day.end
                                    )
                            }
                            if (invalid) {
                                scheduleError = "Enter valid HH:mm start and end times for every enabled day."
                            } else {
                                scheduleDraft.forEach { day ->
                                    val startTime = day.start.takeIf { EmployeeTimePattern.matches(it) } ?: "00:00"
                                    val endTime = day.end.takeIf { EmployeeTimePattern.matches(it) } ?: "00:00"
                                    onSaveSchedule(userId, day.weekday, startTime, endTime, day.enabled)
                                }
                                scheduleError = null
                            }
                        },
                        enabled = ownerCanEdit,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Duty Schedule", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ==============================================================================
// TAB 3: BUSINESS HOURS CONFIGURATION & POS LOCK VALIDATION
// ==============================================================================

@Composable
private fun BusinessHoursTabContent(
    hours: BusinessHours,
    onHoursChange: (BusinessHours) -> Unit,
    validationResult: BusinessHoursValidationResult,
    ownerCanEdit: Boolean,
    isSaving: Boolean,
    error: String?,
    onSaveHours: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Status Card showing live validation
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (validationResult.isOpen) Color(0xFFECFDF5) else Color(0xFFFEF2F2)
            ),
            border = BorderStroke(
                1.5.dp,
                if (validationResult.isOpen) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFEF4444).copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (validationResult.isOpen) Icons.Default.CheckCircle else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (validationResult.isOpen) Color(0xFF059669) else Color(0xFFDC2626),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = if (validationResult.isOpen) "POS IS CURRENTLY OPEN" else "POS IS CURRENTLY LOCKED",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = if (validationResult.isOpen) Color(0xFF065F46) else Color(0xFF991B1B)
                            )
                            Text(
                                text = if (hours.isEnabled) "Automated locking active" else "Hours enforcement disabled",
                                fontSize = 11.sp,
                                color = if (validationResult.isOpen) Color(0xFF047857) else Color(0xFFB91C1C)
                            )
                        }
                    }

                    Switch(
                        checked = hours.isEnabled,
                        onCheckedChange = { enabled ->
                            onHoursChange(hours.copy(isEnabled = enabled))
                        },
                        enabled = ownerCanEdit
                    )
                }

                HorizontalDivider(color = if (validationResult.isOpen) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f))

                if (validationResult is BusinessHoursValidationResult.Open) {
                    Text(
                        text = "Closes at ${validationResult.closesAt} (${validationResult.minutesRemaining / 60}h ${validationResult.minutesRemaining % 60}m remaining in current shift).",
                        fontSize = 12.sp,
                        color = Color(0xFF065F46)
                    )
                } else if (validationResult is BusinessHoursValidationResult.Closed) {
                    Text(
                        text = "${validationResult.reason}. Staff workspaces lock automatically until reopening ${validationResult.nextOpeningDay} at ${validationResult.nextOpeningTime}.",
                        fontSize = 12.sp,
                        color = Color(0xFF991B1B)
                    )
                }
            }
        }

        // Quick Presets
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Schedule Presets", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onHoursChange(hours.copy(weeklySchedule = BusinessHours.defaultWeeklySchedule(8, 0, 22, 0)))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Retail (8-22)", fontSize = 11.sp, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            onHoursChange(hours.copy(weeklySchedule = BusinessHours.clubNightSchedule()))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Club (18-4am)", fontSize = 11.sp, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            onHoursChange(hours.copy(weeklySchedule = BusinessHours.full24HoursSchedule()))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("24/7 Always", fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }

        // Days Schedule List
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Weekly Operating Schedule", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                if (!error.isNullOrBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                hours.weeklySchedule.forEachIndexed { index, daySchedule ->
                    DayScheduleEditorRow(
                        day = daySchedule,
                        ownerCanEdit = ownerCanEdit,
                        onUpdate = { updatedDay ->
                            val updatedList = hours.weeklySchedule.toMutableList()
                            updatedList[index] = updatedDay
                            onHoursChange(hours.copy(weeklySchedule = updatedList))
                        }
                    )
                    if (index < hours.weeklySchedule.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    }
                }

                Button(
                    onClick = onSaveHours,
                    enabled = ownerCanEdit && !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("save_business_hours_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (isSaving) "Saving…" else "Save & Enforce Business Hours")
                }
            }
        }
    }
}

@Composable
private fun DayScheduleEditorRow(
    day: DayBusinessHours,
    ownerCanEdit: Boolean,
    onUpdate: (DayBusinessHours) -> Unit
) {
    var openTimeStr by remember(day.openTime) { mutableStateOf(day.openTime.format()) }
    var closeTimeStr by remember(day.closeTime) { mutableStateOf(day.closeTime.format()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Switch(
            checked = day.isOpen,
            onCheckedChange = { open ->
                onUpdate(day.copy(isOpen = open))
            },
            enabled = ownerCanEdit,
            modifier = Modifier.size(36.dp)
        )

        Column(modifier = Modifier.width(76.dp)) {
            Text(day.dayName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                if (day.isOpen) (if (day.isOvernight) "Overnight" else "Open") else "Closed",
                fontSize = 10.sp,
                color = if (day.isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (day.isOpen) {
            OutlinedTextField(
                value = openTimeStr,
                onValueChange = {
                    openTimeStr = it.take(5)
                    val parsed = TimeSlot.parse(openTimeStr, day.openTime.hour, day.openTime.minute)
                    onUpdate(day.copy(openTime = parsed))
                },
                singleLine = true,
                enabled = ownerCanEdit,
                label = { Text("Open", fontSize = 9.sp) },
                modifier = Modifier.weight(1f)
            )
            Text("–", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = closeTimeStr,
                onValueChange = {
                    closeTimeStr = it.take(5)
                    val parsed = TimeSlot.parse(closeTimeStr, day.closeTime.hour, day.closeTime.minute)
                    onUpdate(day.copy(closeTime = parsed))
                },
                singleLine = true,
                enabled = ownerCanEdit,
                label = { Text("Close", fontSize = 9.sp) },
                modifier = Modifier.weight(1f)
            )
        } else {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Closed all day", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
