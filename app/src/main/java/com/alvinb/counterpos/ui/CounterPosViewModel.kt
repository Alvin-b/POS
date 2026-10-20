package com.alvinb.counterpos.ui

import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alvinb.counterpos.data.CounterPosRepository
import com.alvinb.counterpos.data.remote.AdminBusiness
import com.alvinb.counterpos.data.remote.CounterReportRow
import com.alvinb.counterpos.data.remote.ServiceCounter
import com.alvinb.counterpos.data.SessionSnapshot
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.local.entity.DiningTableEntity
import com.alvinb.counterpos.data.local.entity.SaleUnit
import com.alvinb.counterpos.data.local.entity.parseSaleUnits
import com.alvinb.counterpos.data.local.entity.serializeSaleUnits
import com.alvinb.counterpos.data.model.BusinessHours
import com.alvinb.counterpos.data.service.BusinessHoursValidationResult
import com.alvinb.counterpos.data.service.BusinessHoursValidationService
import com.alvinb.counterpos.ui.scanner.ScannerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.regex.Pattern

enum class PosTab { SCAN, STOCK, SALES, REPORTS, TABLES }
enum class StockMode { MY_STOCK, LIBRARY }

data class AdminEnrollmentCredential(
    val businessId: String,
    val businessName: String,
    val role: String,
    val passkey: String,
    val expiresInHours: Int
)

/** A single Sunday–Saturday employee duty window. Weekdays use 0 = Sunday through 6 = Saturday. */
data class EmployeeScheduleDay(
    val weekday: Int,
    val start: String = "",
    val end: String = "",
    val enabled: Boolean = false
)

private val HourMinutePattern = Pattern.compile("^(?:[01]\\d|2[0-3]):[0-5]\\d$")

private fun JSONArray.toEmployeeMemberList(): List<JSONObject> = buildList {
    for (index in 0 until length()) optJSONObject(index)?.let(::add)
}

private fun isValidHourMinute(value: String): Boolean = HourMinutePattern.matcher(value.trim()).matches()

data class CounterPosUiState(
    val tab: PosTab = PosTab.SCAN,
    val stockMode: StockMode = StockMode.MY_STOCK,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val libraryCategory: String = "All",
    val selectedTableId: String? = null,
    val selectedCounterId: String? = null,
    val selectedServingCounterId: String? = null,
    val selectedServingCounterName: String? = null,
    val selectedWaiterName: String = "",
    val availableWaiters: List<String> = emptyList(),
    val reportFilter: String = "today",
    val reportCounterFilter: String = "all",
    val isRecordDispensationOpen: Boolean = false,
    val counterReport: List<CounterReportRow> = emptyList(),
    val isLoadingCounterReport: Boolean = false,
    val counterStockByProduct: Map<String, Int> = emptyMap(),
    val isCreatingTable: Boolean = false,
    val adminSelectedBusinessId: String? = null,
    val adminQrRole: String = "owner",
    val isCreatingAdminBusiness: Boolean = false,
    val isTeamPanelOpen: Boolean = false,
    val loginQrPayload: String? = null,
    val loginQrExpiresAtMillis: Long? = null,
    val isQrPolling: Boolean = false,
    val employeeMembers: List<JSONObject> = emptyList(),
    val isSavingBusinessName: Boolean = false,
    val isLoadingEmployeeMembers: Boolean = false,
    val isIssuingEmployeePass: Boolean = false,
    val isSavingEmployeeSchedule: Boolean = false,
    val isClearingEmployeeSchedule: Boolean = false,
    val isAccessLocked: Boolean = false,
    val accessLockMessage: String? = null,
    val employeePasskey: String? = null,
    val adminEnrollmentCredential: AdminEnrollmentCredential? = null,
    val isStaffManagementOpen: Boolean = false,
    val waiterEnrollmentQrPayload: String? = null,
    val isGeneratingEnrollmentQr: Boolean = false,
    val businessHours: BusinessHours = BusinessHours(),

    // Sale sheet
    val selectedSaleCode: String? = null,
    val selectedUnitName: String = "Bottle",
    val selectedUnitPrice: Int = 0,
    val saleQuantity: Int = 1,

    // Add bottles to stock dialog
    val addStockProductCode: String? = null,

    // Edit product & stock dialog
    val editProductCode: String? = null,

    // Add new product dialog
    val isAddingNewProduct: Boolean = false,
    val prefilledBarcode: String? = null,

    // Add catalog item to shop dialog
    val selectedCatalogCode: String? = null,
    val catalogInitialStock: Int = 12,
    val catalogSellingPrice: String = "",

    // Camera Scanner
    val isScannerOpen: Boolean = false,
    val scannerMode: ScannerMode = ScannerMode.SELL,
    val barcodeAssignmentCode: String? = null,
    val unrecognizedBarcode: String? = null,

    val actionError: String? = null,
    val notice: String? = null,
    val isReady: Boolean = false,
    val startupError: String? = null
)

class CounterPosViewModel(
    private val repository: CounterPosRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModel() {
    val session = repository.session.stateIn(viewModelScope, SharingStarted.Eagerly, SessionSnapshot())
    private val _adminBusinesses = MutableStateFlow<List<AdminBusiness>>(emptyList())
    val adminBusinesses = _adminBusinesses.asStateFlow()
    val products = repository.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sales = repository.sales.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val diningTables = repository.diningTables.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tableOrders = repository.tableOrders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val serviceCounters = repository.serviceCounters.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(CounterPosUiState())
    val uiState = _uiState.asStateFlow()
    private val businessHoursValidationService = BusinessHoursValidationService()
    private var mutationInProgress = false
    private var reconnectSyncJob: Job? = null
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            reconnectSyncJob?.cancel()
            reconnectSyncJob = viewModelScope.launch {
                delay(750)
                runCatching { repository.synchronizeWhenOnline() }
                    .onSuccess { _uiState.update { it.copy(notice = "Synced automatically") } }
                    .onFailure { _uiState.update { it.copy(notice = "Offline changes will sync automatically when connected") } }
            }
        }
    }

    init {
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
        viewModelScope.launch {
            repository.session.collect { sess ->
                val hours = repository.getBusinessHours(sess.activeBusiness?.shopId)
                _uiState.update { it.copy(businessHours = hours) }
            }
        }
        viewModelScope.launch {
            try {
                repository.restoreSession()
                _uiState.update { it.copy(isReady = true) }
                if (!repository.session.value.isAuthenticated) prepareQrChallenge()
                monitorActiveHours()
            } catch (e: Exception) {
                _uiState.update { it.copy(isReady = true, startupError = e.message) }
                if (!repository.session.value.isAuthenticated) prepareQrChallenge()
                monitorActiveHours()
            }
        }
    }

    override fun onCleared() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        reconnectSyncJob?.cancel()
        super.onCleared()
    }

    fun signIn(email: String, password: String) = viewModelScope.launch {
        _uiState.update { it.copy(startupError = null, notice = null) }
        runCatching { repository.signIn(email, password) }
            .onFailure { error -> _uiState.update { it.copy(startupError = error.message ?: "Sign in failed") } }
    }

    fun signInWithPasskey(passkey: String) = _uiState.update {
        it.copy(startupError = null, notice = null)
    }.also {
        viewModelScope.launch {
            runCatching { repository.signInWithPasskey(passkey) }
                .onFailure { error -> _uiState.update { state -> state.copy(startupError = error.message ?: "Passkey sign-in failed") } }
        }
    }

    fun signInWithSuperadminToken(token: String) = _uiState.update {
        it.copy(startupError = null, notice = "Verifying Superadmin token…")
    }.also {
        viewModelScope.launch {
            runCatching { repository.signInWithSuperadminToken(token) }
                .onSuccess { _uiState.update { state -> state.copy(notice = "Superadmin access granted") } }
                .onFailure { error -> _uiState.update { state -> state.copy(notice = null, startupError = error.message ?: "Superadmin token sign-in failed") } }
        }
    }

    fun signInWithCredential(credential: String) {
        val value = credential.trim()
        if (value.startsWith("CPSA1|", ignoreCase = true)) {
            signInWithSuperadminToken(value)
        } else if (value.startsWith("CPX1.", ignoreCase = true)) {
            // Access-pass QR payloads are intentionally prefixed so the scanner can
            // distinguish them from ordinary text. Redemption uses the passkey body.
            signInWithPasskey(value.substringAfter('.').trim())
        } else {
            signInWithPasskey(value)
        }
    }

    fun prepareQrChallenge() = viewModelScope.launch {
        if (_uiState.value.isQrPolling) return@launch
        _uiState.update { it.copy(loginQrPayload = null, isQrPolling = false, startupError = null, notice = "Connecting to secure QR sign-in…") }
        runCatching { repository.createAccessChallenge() }
            .onSuccess { challenge ->
                _uiState.update {
                    it.copy(
                        loginQrPayload = challenge.token,
                        loginQrExpiresAtMillis = challenge.expiresAtMillis,
                        isQrPolling = true,
                        startupError = null,
                        notice = "QR ready. Ask an authorized administrator to scan it; this phone will sign in automatically."
                    )
                }
                pollAccessChallengeUntilApproved(challenge.token)
            }
            .onFailure { error ->
                val message = error.message.orEmpty()
                val friendly = if (message.contains("function was not found", ignoreCase = true) || message.contains("404")) {
                    "Secure QR sign-in is not deployed on this Supabase project. Deploy create-access-challenge, then tap Retry."
                } else if (message.contains("dining_tables", ignoreCase = true) || message.contains("schema cache", ignoreCase = true)) {
                    "QR sign-in cannot start because public.dining_tables is missing in Supabase. Apply supabase/migrations/202610190001_tables.sql, then refresh QR setup. Passkey sign-in is still available."
                } else message.ifBlank { "Could not prepare QR sign-in" }
                _uiState.update { it.copy(loginQrPayload = null, isQrPolling = false, startupError = friendly, notice = null) }
            }
    }

    fun signInWithQrRequested() = viewModelScope.launch {
        if (_uiState.value.isQrPolling) return@launch
        if (_uiState.value.startupError?.contains("expired", ignoreCase = true) == true ||
            (_uiState.value.loginQrExpiresAtMillis ?: Long.MAX_VALUE) <= System.currentTimeMillis()
        ) {
            prepareQrChallenge()
            return@launch
        }
        val token = _uiState.value.loginQrPayload
        if (token.isNullOrBlank()) {
            prepareQrChallenge()
            return@launch
        }
        _uiState.update { it.copy(isQrPolling = true, startupError = null, notice = "Waiting for administrator approval…") }
        pollAccessChallengeUntilApproved(token)
    }

    private suspend fun pollAccessChallengeUntilApproved(token: String, missingQrRetries: Int = 0) {
        repeat(300) {
            try {
                val result = repository.pollAccessChallenge(token)
                if (result != null) {
                    val targetBusinessId = result.businessId ?: result.session.activeBusiness?.shopId
                    if (targetBusinessId != null) {
                        runCatching { repository.selectBusiness(targetBusinessId) }
                            .onFailure { error ->
                                _uiState.update { state -> state.copy(startupError = error.message ?: "Could not open the enrolled business") }
                            }
                    }
                    _uiState.update { it.copy(isQrPolling = false, notice = "Signed in successfully") }
                    return
                }
            } catch (error: Exception) {
                val message = error.message.orEmpty()
                val qrNotFound = message.contains("qr_not_found", ignoreCase = true) ||
                    (message.contains("qr", ignoreCase = true) && message.contains("not found", ignoreCase = true))
                if (qrNotFound && missingQrRetries == 0) {
                    val replacement = runCatching { repository.createAccessChallenge() }.getOrNull()
                    if (replacement != null) {
                        _uiState.update {
                            it.copy(
                                loginQrPayload = replacement.token,
                                loginQrExpiresAtMillis = replacement.expiresAtMillis,
                                isQrPolling = true,
                                startupError = null,
                                notice = "The previous QR was not registered. A fresh QR is ready; ask the Superadmin to scan this new code."
                            )
                        }
                        return pollAccessChallengeUntilApproved(replacement.token, missingQrRetries + 1)
                    }
                }
                val friendly = if (qrNotFound) {
                    "The login QR is not registered by the connected backend. Deploy the current create-access-challenge and poll-access-challenge functions to this Supabase project, then tap Retry QR setup."
                } else if (message.contains("expired", ignoreCase = true) || message.contains("used", ignoreCase = true)) {
                    "This QR code has expired or was already used. Tap Refresh QR to generate a new one."
                } else message.ifBlank { "QR sign-in failed" }
                _uiState.update { it.copy(loginQrPayload = null, isQrPolling = false, startupError = friendly, notice = null) }
                return
            }
            delay(2_000)
        }
        _uiState.update {
            it.copy(
                isQrPolling = false,
                startupError = "This QR code has expired. Tap Refresh QR to generate a new one.",
                notice = null
            )
        }
    }

    fun signUp(email: String, password: String) = viewModelScope.launch {
        _uiState.update { it.copy(startupError = null, notice = null) }
        runCatching { repository.signUp(email, password) }
            .onSuccess { snapshot ->
                snapshot.message?.let { message -> _uiState.update { it.copy(notice = message) } }
            }
            .onFailure { error -> _uiState.update { it.copy(startupError = error.message ?: "Could not create account") } }
    }

    fun createBusiness(name: String) = viewModelScope.launch {
        runCatching { repository.createBusiness(name) }
            .onFailure { error -> _uiState.update { it.copy(startupError = error.message ?: "Could not create business") } }
    }

    fun selectBusiness(shopId: String) = viewModelScope.launch {
        runCatching { repository.selectBusiness(shopId) }
            .onFailure { error -> _uiState.update { it.copy(startupError = error.message ?: "Could not open business") } }
    }

    fun signOut() = viewModelScope.launch {
        repository.signOut()
        _uiState.update { it.copy(loginQrPayload = null, loginQrExpiresAtMillis = null, isQrPolling = false, adminEnrollmentCredential = null, startupError = null, notice = null) }
        prepareQrChallenge()
    }

    fun clearStartupError() = _uiState.update { it.copy(startupError = null) }

    fun openTeamPanel() = _uiState.update { it.copy(isTeamPanelOpen = true, actionError = null) }

    fun closeTeamPanel() = _uiState.update { it.copy(isTeamPanelOpen = false, actionError = null) }

    fun openStaffManagement() = _uiState.update {
        it.copy(isStaffManagementOpen = true, actionError = null)
    }.also {
        loadEmployeeMembers()
    }

    fun closeStaffManagement() = _uiState.update {
        it.copy(isStaffManagementOpen = false, actionError = null, waiterEnrollmentQrPayload = null)
    }

    fun saveBusinessHours(hours: BusinessHours) {
        val shopId = session.value.activeBusiness?.shopId
        repository.saveBusinessHours(hours, shopId)
        _uiState.update { it.copy(businessHours = hours, notice = "Business hours configuration saved.") }
        refreshAccessNow()
    }

    fun generateWaiterEnrollmentQr(role: String = "cashier") = viewModelScope.launch {
        if (_uiState.value.isGeneratingEnrollmentQr) return@launch
        _uiState.update { it.copy(isGeneratingEnrollmentQr = true, actionError = null) }
        try {
            val pass = repository.createEmployeeAccessPass(role)
            val passkey = pass.optString("passkey").trim()
            require(passkey.isNotBlank()) { "Server did not return a waiter passkey." }
            val payload = "CPX1.$passkey"
            _uiState.update {
                it.copy(
                    waiterEnrollmentQrPayload = payload,
                    employeePasskey = passkey,
                    actionError = null,
                    notice = "Waiter enrollment QR code generated. Waiter can scan it from their sign-in screen."
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(actionError = e.message ?: "Could not generate enrollment QR") }
        } finally {
            _uiState.update { it.copy(isGeneratingEnrollmentQr = false) }
        }
    }

    fun clearWaiterEnrollmentQr() = _uiState.update { it.copy(waiterEnrollmentQrPayload = null) }

    private fun monitorActiveHours() = viewModelScope.launch {
        while (isActive) {
            val isOwner = session.value.activeBusiness?.role == "owner"
            val hours = _uiState.value.businessHours
            val validation = businessHoursValidationService.validate(hours)

            if (!isOwner && hours.isEnabled && !validation.isOpen) {
                val closed = validation as? BusinessHoursValidationResult.Closed
                _uiState.update { state ->
                    state.copy(
                        isAccessLocked = true,
                        accessLockMessage = "The POS is locked outside configured business hours (${closed?.reason ?: "Closed"}). Reopens ${closed?.nextOpeningDay ?: "upcoming shift"} at ${closed?.nextOpeningTime ?: "--:--"}."
                    )
                }
            } else {
                runCatching { repository.canOperateNow() }
                    .onSuccess { allowed ->
                        _uiState.update { state ->
                            state.copy(
                                isAccessLocked = !allowed,
                                accessLockMessage = if (allowed) null else "This employee account is outside its configured working hours. The workspace will reopen automatically when the next active period begins."
                            )
                        }
                    }
                    .onFailure {
                        if (!isOwner) {
                            _uiState.update { state ->
                                state.copy(
                                    isAccessLocked = true,
                                    accessLockMessage = "Waiter access could not be verified. Reconnect to the internet to check your account status and duty hours."
                                )
                            }
                        }
                    }
            }
            delay(30_000)
        }
    }

    fun refreshAccessNow() = viewModelScope.launch {
        val isOwner = session.value.activeBusiness?.role == "owner"
        val hours = _uiState.value.businessHours
        val validation = businessHoursValidationService.validate(hours)

        if (!isOwner && hours.isEnabled && !validation.isOpen) {
            val closed = validation as? BusinessHoursValidationResult.Closed
            _uiState.update { state ->
                state.copy(
                    isAccessLocked = true,
                    accessLockMessage = "The POS is locked outside configured business hours (${closed?.reason ?: "Closed"}). Reopens ${closed?.nextOpeningDay ?: "upcoming shift"} at ${closed?.nextOpeningTime ?: "--:--"}."
                )
            }
            return@launch
        }

        runCatching { repository.canOperateNow() }
            .onSuccess { allowed -> _uiState.update { it.copy(isAccessLocked = !allowed, accessLockMessage = if (allowed) null else "You are outside your configured working hours.") } }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAccessLocked = !isOwner,
                        accessLockMessage = if (!isOwner) {
                            "Waiter access could not be verified. Reconnect to the internet to check your account status and duty hours."
                        } else null,
                        actionError = error.message ?: "Could not verify working hours"
                    )
                }
            }
    }

    fun issueEmployeePass(role: String = "cashier") = viewModelScope.launch {
        if (_uiState.value.isIssuingEmployeePass) return@launch
        if (role != "cashier") {
            _uiState.update { it.copy(actionError = "Waiter is the only employee position currently available.") }
            return@launch
        }
        _uiState.update { it.copy(isIssuingEmployeePass = true, employeePasskey = null, actionError = null) }
        try {
            val pass = repository.createEmployeeAccessPass("cashier")
            val passkey = pass.optString("passkey").trim()
            require(passkey.isNotBlank()) { "The server did not return a waiter passkey." }
            _uiState.update {
                it.copy(
                    employeePasskey = passkey,
                    actionError = null,
                    notice = "One-time waiter passkey created. Share it privately; the waiter account is created when it is redeemed."
                )
            }
        } catch (error: Exception) {
            _uiState.update { it.copy(actionError = error.message ?: "Could not create waiter passkey") }
        } finally {
            _uiState.update { it.copy(isIssuingEmployeePass = false) }
        }
    }

    fun clearEmployeePass() = _uiState.update { it.copy(employeePasskey = null) }

    fun loadEmployeeMembers() = viewModelScope.launch {
        if (_uiState.value.isLoadingEmployeeMembers) return@launch
        _uiState.update { it.copy(isLoadingEmployeeMembers = true, actionError = null) }
        try {
            val rows = repository.shopMembers()
            _uiState.update { it.copy(employeeMembers = rows.toEmployeeMemberList(), actionError = null) }
        } catch (error: Exception) {
            _uiState.update { it.copy(actionError = error.message ?: "Could not load registered waiters") }
        } finally {
            _uiState.update { it.copy(isLoadingEmployeeMembers = false) }
        }
    }

    fun setEmployeeStatus(userId: String, status: String) = viewModelScope.launch {
        val normalizedStatus = status.trim().lowercase()
        if (userId.isBlank()) {
            _uiState.update { it.copy(actionError = "This waiter has no registered account ID.") }
            return@launch
        }
        if (normalizedStatus !in setOf("active", "suspended")) {
            _uiState.update { it.copy(actionError = "Choose Reactivate or Log out waiter.") }
            return@launch
        }
        runCatching {
            repository.setEmployeeStatus(
                userId,
                normalizedStatus,
                if (normalizedStatus == "active") "Reactivated by business admin" else "Logged out by business admin"
            )
        }
            .onSuccess { rows ->
                _uiState.update {
                    it.copy(
                        employeeMembers = rows.toEmployeeMemberList(),
                        actionError = null,
                        notice = if (normalizedStatus == "active") "Waiter reactivated." else "Waiter logged out. Business access is locked on the next server authorization check."
                    )
                }
            }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not update waiter status") } }
    }

    fun setEmployeeRole(userId: String, role: String) = viewModelScope.launch {
        runCatching { repository.setEmployeeRole(userId, role) }
            .onSuccess { rows -> _uiState.update { it.copy(employeeMembers = (0 until rows.length()).mapNotNull(rows::optJSONObject), notice = "Employee role updated") } }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not update employee role") } }
    }

    fun removeEmployeeAccess(userId: String) = viewModelScope.launch {
        if (userId.isBlank()) {
            _uiState.update { it.copy(actionError = "This waiter has no registered account ID.") }
            return@launch
        }
        runCatching { repository.removeEmployeeAccess(userId, "Access removed by business administrator") }
            .onSuccess { rows -> _uiState.update { it.copy(employeeMembers = rows.toEmployeeMemberList(), actionError = null, notice = "Waiter access removed; sales history is retained.") } }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not remove waiter access") } }
    }

    fun updateBusinessName(name: String) = viewModelScope.launch {
        _uiState.update { it.copy(isSavingBusinessName = true, actionError = null) }
        try {
            repository.updateBusinessName(name)
            _uiState.update { it.copy(notice = "Business name updated", actionError = null) }
        } catch (error: Exception) {
            _uiState.update { it.copy(actionError = error.message ?: "Could not update business name") }
        } finally {
            _uiState.update { it.copy(isSavingBusinessName = false) }
        }
    }

    fun setEmployeeHours(userId: String, weekday: Int, start: String, end: String, enabled: Boolean) = viewModelScope.launch {
        runCatching { repository.setEmployeeHours(userId, weekday, start, end, enabled) }
            .onSuccess { rows -> _uiState.update { it.copy(employeeMembers = rows.toEmployeeMemberList(), notice = "Working hours saved") } }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not save working hours") } }
    }

    /**
     * Saves all seven days in order. Disabled days are still sent so a previous schedule cannot
     * remain active when a day is switched off in the settings screen.
     */
    fun saveEmployeeSchedule(userId: String, schedule: List<EmployeeScheduleDay>) = viewModelScope.launch {
        if (_uiState.value.isSavingEmployeeSchedule || _uiState.value.isClearingEmployeeSchedule) return@launch
        val byWeekday = schedule.associateBy(EmployeeScheduleDay::weekday)
        val enabledDays = (0..6).mapNotNull { byWeekday[it] }.filter(EmployeeScheduleDay::enabled)
        val invalidTime = enabledDays.firstOrNull { !isValidHourMinute(it.start) || !isValidHourMinute(it.end) }
        when {
            userId.isBlank() -> {
                _uiState.update { it.copy(actionError = "This waiter has no registered account ID.") }
                return@launch
            }
            enabledDays.isEmpty() -> {
                _uiState.update { it.copy(actionError = "Enable at least one day, or use Clear schedule for unrestricted access.") }
                return@launch
            }
            invalidTime != null -> {
                _uiState.update { it.copy(actionError = "Use HH:mm time values such as 08:30 and 17:00 for every enabled day.") }
                return@launch
            }
        }

        _uiState.update { it.copy(isSavingEmployeeSchedule = true, actionError = null) }
        try {
            for (weekday in 0..6) {
                val day = byWeekday[weekday]
                repository.setEmployeeHours(
                    userId = userId,
                    weekday = weekday,
                    start = if (day?.enabled == true) day.start.trim() else "00:00",
                    end = if (day?.enabled == true) day.end.trim() else "00:00",
                    enabled = day?.enabled == true
                )
            }
            val rows = repository.shopMembers()
            _uiState.update {
                it.copy(
                    employeeMembers = rows.toEmployeeMemberList(),
                    actionError = null,
                    notice = "Duty schedule saved for this waiter."
                )
            }
        } catch (error: Exception) {
            _uiState.update { it.copy(actionError = error.message ?: "Could not save the waiter duty schedule") }
        } finally {
            _uiState.update { it.copy(isSavingEmployeeSchedule = false) }
        }
    }

    /** Disables every weekday; the backend then treats the waiter as unrestricted. */
    fun clearEmployeeSchedule(userId: String) = viewModelScope.launch {
        if (_uiState.value.isSavingEmployeeSchedule || _uiState.value.isClearingEmployeeSchedule) return@launch
        if (userId.isBlank()) {
            _uiState.update { it.copy(actionError = "This waiter has no registered account ID.") }
            return@launch
        }
        _uiState.update { it.copy(isClearingEmployeeSchedule = true, actionError = null) }
        try {
            for (weekday in 0..6) {
                repository.setEmployeeHours(userId, weekday, "00:00", "00:00", false)
            }
            val rows = repository.shopMembers()
            _uiState.update {
                it.copy(
                    employeeMembers = rows.toEmployeeMemberList(),
                    actionError = null,
                    notice = "Duty schedule cleared. This waiter has unrestricted access."
                )
            }
        } catch (error: Exception) {
            _uiState.update { it.copy(actionError = error.message ?: "Could not clear the waiter duty schedule") }
        } finally {
            _uiState.update { it.copy(isClearingEmployeeSchedule = false) }
        }
    }

    fun loadAdminBusinesses() = viewModelScope.launch {
        runCatching { repository.adminBusinesses() }
            .onSuccess { _adminBusinesses.value = it }
            .onFailure { error -> _uiState.update { it.copy(startupError = error.message ?: "Could not load businesses") } }
    }

    fun setBusinessStatus(shopId: String, status: String, reason: String?) = viewModelScope.launch {
        runCatching { repository.adminSetBusinessStatus(shopId, status, reason) }
            .onSuccess { _adminBusinesses.value = it }
            .onFailure { error -> _uiState.update { it.copy(startupError = error.message ?: "Could not change business status") } }
    }

    fun createBusinessForOwner(
        name: String,
        ownerEmail: String,
        businessType: String = "big_club",
        businessSize: String = "medium",
        inventoryMode: String = "shared",
        counterCount: Int = 1,
        onCreated: (() -> Unit)? = null
    ) = viewModelScope.launch {
        if (_uiState.value.isCreatingAdminBusiness) return@launch
        _uiState.update { it.copy(isCreatingAdminBusiness = true, startupError = null) }
        runCatching { repository.adminCreateBusiness(name, ownerEmail, businessType, businessSize, inventoryMode, counterCount) }
            .onSuccess { result ->
                val businesses = result.businesses
                _adminBusinesses.value = businesses
                val created = result.createdShopId?.let { createdId -> businesses.firstOrNull { it.shopId == createdId } }
                _uiState.update {
                    it.copy(
                        adminSelectedBusinessId = created?.shopId,
                        adminEnrollmentCredential = null,
                        isCreatingAdminBusiness = false,
                        startupError = if (created == null) "Business profile was created, but its exact ID was not present in the refreshed list. Refresh the portfolio before enrolling its owner." else null,
                        notice = if (created != null) "Business profile created and selected. Scan the owner's QR below." else null
                    )
                }
                if (created != null) onCreated?.invoke()
            }
            .onFailure { error -> _uiState.update { it.copy(isCreatingAdminBusiness = false, startupError = error.message ?: "Could not create business") } }
    }

    fun createAdminEnrollmentPass(shopId: String, role: String) = viewModelScope.launch {
        if (!session.value.isPlatformAdmin || role != "owner") {
            _uiState.update { it.copy(startupError = "Superadmin can issue an owner passkey only. The owner enrolls waiters later.") }
            return@launch
        }
        runCatching { repository.createAdminBusinessAccessPass(shopId, role) }
            .onSuccess { pass ->
                _uiState.update {
                    it.copy(
                        adminEnrollmentCredential = AdminEnrollmentCredential(
                            businessId = shopId,
                            businessName = pass.optString("business_name"),
                            role = role,
                            passkey = pass.optString("passkey"),
                            expiresInHours = pass.optInt("expires_in_hours", 24)
                        ),
                        startupError = null,
                        notice = "One-time ${role.replace('_', ' ')} passkey created. Share it privately."
                    )
                }
            }
            .onFailure { error -> _uiState.update { it.copy(startupError = error.message ?: "Could not create passkey") } }
    }

    fun clearAdminEnrollmentCredential() = _uiState.update { it.copy(adminEnrollmentCredential = null) }

    fun selectAdminBusiness(shopId: String) = _uiState.update {
        it.copy(
            adminSelectedBusinessId = shopId,
            adminEnrollmentCredential = null,
            notice = "Selected. Open QR sign-in on the other phone and keep its code visible; scan it here to enroll that phone."
        )
    }

    fun launchBusinessWorkspace(shopId: String) = viewModelScope.launch {
        _uiState.update { it.copy(startupError = null, actionError = null, isScannerOpen = false, notice = "Loading business dashboard…") }
        runCatching { repository.adminOpenBusiness(shopId) }
            .onSuccess {
                _uiState.update { it.copy(startupError = null, actionError = null, isScannerOpen = false, notice = "Business dashboard ready") }
            }
            .onFailure { error ->
                _uiState.update { it.copy(isScannerOpen = false, startupError = error.message ?: "Could not open business dashboard") }
            }
    }

    fun exitToAdminPortal() = viewModelScope.launch {
        _uiState.update { it.copy(startupError = null, actionError = null, notice = null) }
        runCatching { repository.exitToAdminPortal() }
            .onSuccess {
                loadAdminBusinesses()
            }
            .onFailure { error ->
                _uiState.update { it.copy(startupError = error.message ?: "Could not return to Superadmin portal") }
            }
    }

    fun openAdminEnrollmentScanner(shopId: String, role: String) {
        val business = _adminBusinesses.value.firstOrNull { it.shopId == shopId }
        if (!session.value.isPlatformAdmin) {
            _uiState.update { it.copy(startupError = "Only a platform administrator can use this enrollment flow.") }
            return
        }
        if (business == null || business.status != "active") {
            _uiState.update { it.copy(startupError = "Select an active business before enrolling a phone.") }
            return
        }
        if (role != "owner") {
            _uiState.update { it.copy(startupError = "Superadmin QR enrollment is for the business owner only. The owner enrolls waiters later.") }
            return
        }
        _uiState.update {
            it.copy(
                adminSelectedBusinessId = shopId,
                adminQrRole = role,
                isScannerOpen = true,
                scannerMode = ScannerMode.ADMIN_QR,
                startupError = null,
                notice = "Scan the business owner's pending sign-in QR to grant Owner access to this business."
            )
        }
    }

    fun handleAdminQrCode(payload: String) {
        val qr = payload.trim()
        val currentSession = session.value
        val currentState = _uiState.value
        val isPlatformAdmin = currentSession.isPlatformAdmin
        val shopId = if (isPlatformAdmin) currentState.adminSelectedBusinessId else currentSession.activeBusiness?.shopId
        val business = _adminBusinesses.value.firstOrNull { it.shopId == shopId }
        val businessRole = currentSession.activeBusiness?.role
        val role = if (isPlatformAdmin) currentState.adminQrRole else "cashier"

        if (qr.startsWith("CPR1|")) {
            if (shopId.isNullOrBlank()) {
                _uiState.update { it.copy(isScannerOpen = false, startupError = "Select a business before scanning the phone's sign-in QR.") }
            } else if (isPlatformAdmin && (business == null || business.status != "active")) {
                _uiState.update { it.copy(isScannerOpen = false, startupError = "The selected business is not active. Reopen it before enrolling a phone.") }
            } else if (!isPlatformAdmin && (businessRole !in setOf("owner", "manager"))) {
                _uiState.update { it.copy(isScannerOpen = false, actionError = "Only a business owner, manager, or platform administrator can approve sign-in QR requests.") }
            } else {
                val businessName = business?.shopName ?: currentSession.activeBusiness?.shopName ?: "the business"
                viewModelScope.launch {
                    runCatching {
                        if (isPlatformAdmin) repository.adminApproveAccessChallenge(qr, shopId, role)
                        else repository.approveAccessChallenge(qr)
                    }
                        .onSuccess {
                            _uiState.update {
                                it.copy(
                                    isScannerOpen = false,
                                    startupError = null,
                                    actionError = null,
                                    notice = if (isPlatformAdmin) {
                                        "Approved and enrolled the other phone for $businessName as ${role.replace('_', ' ')}. You remain in Superadmin."
                                    } else {
                                        "Approved and enrolled for $businessName as ${role.replace('_', ' ')}."
                                    }
                                )
                            }
                        }
                        .onFailure { error ->
                            val message = error.message.orEmpty()
                            val friendly = if (message.contains("expired", ignoreCase = true) || message.contains("session", ignoreCase = true) && message.contains("used", ignoreCase = true)) {
                                "This QR session has expired. Ask the other phone to tap Refresh QR, then scan the new code."
                            } else message.ifBlank { "Could not approve sign-in QR" }
                            _uiState.update { it.copy(isScannerOpen = false, startupError = friendly) }
                        }
                }
            }
            return
        }

        // Passkey or access pass QR code scan
        if (qr.startsWith("CPX1.", ignoreCase = true) || (qr.length in 8..64 && !qr.contains('|'))) {
            if (isPlatformAdmin && !shopId.isNullOrBlank()) {
                _uiState.update { it.copy(isScannerOpen = false, startupError = null, notice = "Opening business dashboard…") }
                launchBusinessWorkspace(shopId)
                return
            }
        }

        val parts = qr.split('|', limit = 4)
        if (parts.size >= 3 && parts[0].equals("COUNTERPOS", ignoreCase = true)) {
            when (parts[1].uppercase()) {
                "BUSINESS" -> {
                    val name = parts.getOrNull(2).orEmpty().trim()
                    val ownerEmail = parts.getOrNull(3).orEmpty().trim()
                    if (name.isBlank() || !ownerEmail.contains("@")) {
                        _uiState.update { it.copy(isScannerOpen = false, startupError = "Business QR must contain a name and registered owner email.") }
                    } else {
                        _uiState.update { it.copy(isScannerOpen = false, startupError = null, notice = "Registering $name…") }
                        createBusinessForOwner(name, ownerEmail)
                    }
                    return
                }
                "USER" -> {
                    val targetShopId = _uiState.value.adminSelectedBusinessId
                    val email = parts.getOrNull(2).orEmpty().trim()
                    val userRole = parts.getOrNull(3).orEmpty().trim().ifBlank { "cashier" }
                    if (targetShopId.isNullOrBlank()) {
                        _uiState.update { it.copy(isScannerOpen = false, startupError = "Open a business profile before scanning a staff QR.") }
                    } else {
                        viewModelScope.launch {
                            runCatching { repository.addMemberByEmail(targetShopId, email, userRole) }
                                .onSuccess {
                                    _adminBusinesses.value = it
                                    _uiState.update { state -> state.copy(isScannerOpen = false, notice = "$email attached as $userRole") }
                                    if (isPlatformAdmin) {
                                        launchBusinessWorkspace(targetShopId)
                                    }
                                }
                                .onFailure { error -> _uiState.update { state -> state.copy(isScannerOpen = false, startupError = error.message ?: "Could not attach user") } }
                        }
                    }
                    return
                }
            }
        }

        // Fallback for scanning when superadmin is enrolling
        if (isPlatformAdmin && !shopId.isNullOrBlank()) {
            _uiState.update { it.copy(isScannerOpen = false, startupError = null, notice = "Enrolled. Loading business dashboard…") }
            launchBusinessWorkspace(shopId)
            return
        }

        _uiState.update { it.copy(isScannerOpen = false, startupError = "Unrecognized CounterPOS QR. Use a business or user enrollment QR.") }
    }

    fun selectTab(tab: PosTab) = _uiState.update {
        it.copy(tab = tab, actionError = null, isSearchActive = false, searchQuery = "")
    }

    fun selectCounter(counterId: String) {
        _uiState.update { it.copy(selectedCounterId = counterId, actionError = null) }
        refreshCounterStock(counterId)
    }

    fun refreshCounterStock(counterId: String? = _uiState.value.selectedCounterId) = viewModelScope.launch {
        val business = session.value.activeBusiness ?: return@launch
        if (business.counterInventoryMode != "per_counter" || counterId.isNullOrBlank()) {
            _uiState.update { it.copy(counterStockByProduct = emptyMap()) }
            return@launch
        }
        runCatching { repository.loadCounterStock(counterId) }
            .onSuccess { stock -> _uiState.update { it.copy(counterStockByProduct = stock) } }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not load counter stock") } }
    }

    fun createServiceCounter(name: String) = viewModelScope.launch {
        runCatching { repository.createServiceCounter(name) }
            .onSuccess { counter -> _uiState.update { it.copy(notice = "Added ${counter.name}.", actionError = null) } }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not add counter") } }
    }

    fun renameServiceCounter(counterId: String, name: String) = viewModelScope.launch {
        runCatching { repository.renameServiceCounter(counterId, name) }
            .onSuccess { _uiState.update { it.copy(notice = "Counter name updated.", actionError = null) } }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not rename counter") } }
    }

    fun transferCounterStock(productCode: String, destinationCounterId: String, quantity: Int) = viewModelScope.launch {
        val sourceCounterId = _uiState.value.selectedCounterId
        if (sourceCounterId.isNullOrBlank() || sourceCounterId == destinationCounterId || quantity < 1) {
            _uiState.update { it.copy(actionError = "Choose another destination and a valid quantity.") }
            return@launch
        }
        runCatching { repository.transferCounterStock(productCode, sourceCounterId, destinationCounterId, quantity) }
            .onSuccess {
                val destination = serviceCounters.value.firstOrNull { it.id == destinationCounterId }?.name ?: "counter"
                _uiState.update { it.copy(notice = "Stock transferred to $destination.", actionError = null) }
                refreshCounterStock()
            }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not transfer stock") } }
    }

    fun loadCounterReport(fromIso: String, toIso: String) = viewModelScope.launch {
        _uiState.update { it.copy(isLoadingCounterReport = true, actionError = null) }
        runCatching { repository.counterSalesReport(fromIso, toIso) }
            .onSuccess { rows -> _uiState.update { it.copy(counterReport = rows) } }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not load counter report") } }
        _uiState.update { it.copy(isLoadingCounterReport = false) }
    }

    fun openTable(table: DiningTableEntity) = _uiState.update {
        it.copy(selectedTableId = table.id, actionError = null)
    }

    fun backToTables() = _uiState.update { it.copy(selectedTableId = null, actionError = null) }

    fun createDiningTable(name: String) {
        if (_uiState.value.isCreatingTable) return
        _uiState.update { it.copy(isCreatingTable = true, actionError = null) }
        viewModelScope.launch {
            try {
                val table = repository.createDiningTable(name)
                _uiState.update {
                    it.copy(
                        selectedTableId = table.id,
                        isCreatingTable = false,
                        notice = "${table.name} is ready for orders"
                    )
                }
            } catch (error: Exception) {
                _uiState.update { it.copy(isCreatingTable = false, actionError = error.message ?: "Could not create table.") }
            }
        }
    }

    fun openTableSale(tableId: String, product: ProductEntity) {
        if (!product.inMyStock || product.stockQuantity <= 0) {
            _uiState.update { it.copy(actionError = "${product.name} is out of stock.") }
            return
        }
        val units = parseSaleUnits(product.salesUnitsJson, product.sellingPriceKsh)
        val defaultUnit = units.firstOrNull() ?: SaleUnit("Bottle", product.sellingPriceKsh)
        _uiState.update {
            it.copy(
                selectedTableId = tableId,
                selectedSaleCode = product.catalogCode,
                selectedUnitName = defaultUnit.name,
                selectedUnitPrice = defaultUnit.priceKsh,
                saleQuantity = 1,
                actionError = null
            )
        }
    }

    fun setStockMode(mode: StockMode) = _uiState.update {
        it.copy(stockMode = mode, libraryCategory = "All", actionError = null)
    }

    fun setSearchActive(active: Boolean) = _uiState.update {
        it.copy(isSearchActive = active, searchQuery = if (!active) "" else it.searchQuery)
    }

    fun setSearchQuery(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun clearSearch() = _uiState.update { it.copy(searchQuery = "", isSearchActive = false) }

    fun setLibraryCategory(category: String) = _uiState.update { it.copy(libraryCategory = category) }

    fun clearNotice() = _uiState.update { it.copy(notice = null) }

    // Scanner
    fun openScanner(mode: ScannerMode) = _uiState.update {
        it.copy(isScannerOpen = true, scannerMode = mode, actionError = null)
    }

    fun closeScanner() = _uiState.update {
        it.copy(isScannerOpen = false, unrecognizedBarcode = null)
    }

    fun startBarcodeAssignment(product: ProductEntity) = _uiState.update {
        it.copy(isScannerOpen = true, scannerMode = ScannerMode.ASSIGN_BARCODE, barcodeAssignmentCode = product.catalogCode, actionError = null)
    }

    fun handleAssignedBarcode(barcode: String) {
        val code = _uiState.value.barcodeAssignmentCode ?: return
        viewModelScope.launch {
            val success = repository.assignBarcode(code, barcode)
            _uiState.update {
                it.copy(isScannerOpen = false, barcodeAssignmentCode = null, notice = if (success) "Barcode linked to product" else "Barcode is already assigned or could not be saved")
            }
        }
    }

    fun handleScannedSale(product: ProductEntity) {
        if (!product.inMyStock || product.stockQuantity < 1) {
            _uiState.update {
                it.copy(
                    isScannerOpen = false,
                    notice = "${product.name} is not available on the stock screen"
                )
            }
            return
        }
        _uiState.update { it.copy(isScannerOpen = false) }
        openSale(product)
    }

    fun handleScannedTableProduct(product: ProductEntity) {
        val tableId = _uiState.value.selectedTableId
        if (tableId.isNullOrBlank()) {
            _uiState.update { it.copy(isScannerOpen = false, notice = "Open a table before scanning a product.") }
            return
        }
        if (!product.inMyStock || product.stockQuantity < 1) {
            _uiState.update {
                it.copy(
                    isScannerOpen = false,
                    notice = "${product.name} is not available on the stock screen"
                )
            }
            return
        }
        _uiState.update { it.copy(isScannerOpen = false, actionError = null) }
        openTableSale(tableId, product)
    }

    fun handleScannedStock(product: ProductEntity) {
        if (product.inMyStock) {
            _uiState.update {
                it.copy(
                    isScannerOpen = false,
                    tab = PosTab.STOCK,
                    stockMode = StockMode.MY_STOCK,
                    addStockProductCode = product.catalogCode,
                    notice = "Scanned ${product.name}. Enter bottles to add."
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isScannerOpen = false,
                    tab = PosTab.STOCK,
                    stockMode = StockMode.LIBRARY,
                    selectedCatalogCode = product.catalogCode,
                    catalogInitialStock = 12,
                    catalogSellingPrice = product.suggestedPriceKsh.toString(),
                    notice = "Found ${product.name}. Set price & bottles to add to stock."
                )
            }
        }
    }

    fun handleUnrecognizedBarcode(barcode: String) {
        _uiState.update {
            it.copy(
                isScannerOpen = false,
                notice = "Product is not available on the stock screen ($barcode)"
            )
        }
    }

    // Selling & Unit of Sales
    fun openSale(product: ProductEntity) {
        val units = parseSaleUnits(product.salesUnitsJson, product.sellingPriceKsh)
        val defaultUnit = units.firstOrNull() ?: SaleUnit("Bottle", product.sellingPriceKsh)
        _uiState.update {
            it.copy(
                selectedSaleCode = product.catalogCode,
                selectedUnitName = defaultUnit.name,
                selectedUnitPrice = defaultUnit.priceKsh,
                saleQuantity = 1,
                actionError = null
            )
        }
    }

    fun selectSaleUnit(unit: SaleUnit) = _uiState.update {
        it.copy(
            selectedUnitName = unit.name,
            selectedUnitPrice = unit.priceKsh
        )
    }

    fun adjustSaleQuantity(delta: Int) = _uiState.update { state ->
        val product = products.value.firstOrNull { it.catalogCode == state.selectedSaleCode }
        val max = product?.stockQuantity ?: 99
        val newQty = (state.saleQuantity + delta).coerceIn(1, max.coerceAtLeast(1))
        state.copy(saleQuantity = newQty)
    }

    fun dismissSale() = _uiState.update { it.copy(selectedSaleCode = null, actionError = null) }

    fun selectServingCounter(counterId: String, counterName: String) = _uiState.update {
        it.copy(
            selectedCounterId = counterId,
            selectedServingCounterId = counterId,
            selectedServingCounterName = counterName,
            actionError = null
        )
    }

    fun selectWaiter(waiterName: String) = _uiState.update {
        it.copy(selectedWaiterName = waiterName)
    }

    fun setReportFilter(filter: String) = _uiState.update {
        it.copy(reportFilter = filter)
    }

    fun setReportCounterFilter(filter: String) = _uiState.update {
        it.copy(reportCounterFilter = filter)
    }

    fun openRecordDispensation() = _uiState.update {
        it.copy(isRecordDispensationOpen = true, actionError = null)
    }

    fun dismissRecordDispensation() = _uiState.update {
        it.copy(isRecordDispensationOpen = false, actionError = null)
    }

    fun recordDirectDispensation(
        counterId: String,
        counterName: String,
        waiterName: String,
        tableId: String?,
        tableName: String?,
        productCode: String,
        quantity: Int,
        unitName: String,
        unitPriceKsh: Int
    ) = viewModelScope.launch {
        if (quantity < 1 || unitPriceKsh < 1) {
            _uiState.update { it.copy(actionError = "Select a valid quantity and sale price.") }
            return@launch
        }
        try {
            if (tableId != null) {
                repository.sellToTable(
                    tableId = tableId,
                    code = productCode,
                    quantity = quantity,
                    unitName = unitName,
                    unitPriceKsh = unitPriceKsh,
                    selectedCounterId = counterId,
                    counterName = counterName,
                    waiterName = waiterName
                )
            } else {
                repository.sellWithUnit(
                    code = productCode,
                    quantity = quantity,
                    unitName = unitName,
                    unitPriceKsh = unitPriceKsh,
                    selectedCounterId = counterId,
                    counterName = counterName,
                    waiterName = waiterName,
                    tableId = null,
                    tableName = "Direct Walk-in"
                )
            }
            runCatching { refreshCounterStock(counterId) }
            _uiState.update {
                it.copy(
                    isRecordDispensationOpen = false,
                    notice = "✓ Recorded $quantity × $unitName taken from $counterName by $waiterName"
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(actionError = e.message ?: "Could not record dispensation") }
        }
    }

    fun confirmSale() {
        val state = _uiState.value
        val code = state.selectedSaleCode ?: return
        if (state.saleQuantity < 1 || state.selectedUnitPrice < 1) {
            _uiState.update { it.copy(actionError = "Select a valid quantity and sale price.") }
            return
        }
        val counters = serviceCounters.value
        val requestedCounterId = state.selectedServingCounterId ?: state.selectedCounterId ?: counters.singleOrNull()?.id
        val selectedCounter = counters.firstOrNull { it.id == requestedCounterId }
        if (selectedCounter == null) {
            _uiState.update { it.copy(actionError = "No valid service counter is selected. Check the business counters in Settings and try again.") }
            return
        }
        val counterId = selectedCounter.id
        val counterName = selectedCounter.name
        val waiterName = state.selectedWaiterName.takeIf(String::isNotBlank) ?: session.value.email

        if (mutationInProgress) return
        mutationInProgress = true

        viewModelScope.launch {
            try {
                val tableId = state.selectedTableId.takeIf { state.tab == PosTab.TABLES }
                val success = if (tableId != null) {
                    repository.sellToTable(
                        tableId = tableId,
                        code = code,
                        quantity = state.saleQuantity,
                        unitName = state.selectedUnitName,
                        unitPriceKsh = state.selectedUnitPrice,
                        selectedCounterId = counterId,
                        counterName = counterName,
                        waiterName = waiterName
                    )
                } else {
                    repository.sellWithUnit(
                        code = code,
                        quantity = state.saleQuantity,
                        unitName = state.selectedUnitName,
                        unitPriceKsh = state.selectedUnitPrice,
                        selectedCounterId = counterId,
                        counterName = counterName,
                        waiterName = waiterName,
                        tableId = null,
                        tableName = "Direct Sale"
                    )
                }
                if (success) {
                    runCatching { refreshCounterStock(counterId) }
                    val product = products.value.firstOrNull { it.catalogCode == code }
                    val total = state.selectedUnitPrice * state.saleQuantity
                    _uiState.update {
                        it.copy(
                            selectedSaleCode = null,
                            saleQuantity = 1,
                            actionError = null,
                            notice = if (tableId != null) {
                                if (counters.size > 1) {
                                    "Added ${state.saleQuantity} × ${product?.name.orEmpty()} to table from $counterName · KSh $total"
                                } else {
                                    "Added ${state.saleQuantity} × ${product?.name.orEmpty()} to table · KSh $total"
                                }
                            } else {
                                if (counters.size > 1) {
                                    "Sold ${state.saleQuantity} × ${product?.name.orEmpty()} from $counterName · KSh $total"
                                } else {
                                    "Sold ${state.saleQuantity} × ${product?.name.orEmpty()} · KSh $total"
                                }
                            }
                        )
                    }
                } else {
                    _uiState.update { it.copy(actionError = "Could not record sale. Check stock.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionError = e.message ?: "Sale failed") }
            } finally {
                mutationInProgress = false
            }
        }
    }

    // Add bottles to stock
    fun openAddStock(product: ProductEntity) = _uiState.update {
        it.copy(addStockProductCode = product.catalogCode, actionError = null)
    }

    fun dismissAddStock() = _uiState.update { it.copy(addStockProductCode = null, actionError = null) }

    fun confirmAddStock(product: ProductEntity, additionalBottles: Int, unitCostKsh: Int) {
        if (additionalBottles < 1) {
            _uiState.update { it.copy(actionError = "Please enter at least 1 bottle.") }
            return
        }
        if (unitCostKsh < 0) {
            _uiState.update { it.copy(actionError = "Enter a valid unit cost.") }
            return
        }
        viewModelScope.launch {
            val counterId = _uiState.value.selectedCounterId
            if (counterId.isNullOrBlank()) { _uiState.update { it.copy(actionError = "Choose a receiving counter first.") }; return@launch }
            val success = repository.restock(product.catalogCode, additionalBottles, unitCostKsh, counterId)
            if (success) {
                _uiState.update {
                    it.copy(
                        addStockProductCode = null,
                        notice = "✓ Added $additionalBottles bottles at KSh $unitCostKsh to ${product.name}"
                    )
                }
                refreshCounterStock(counterId)
            } else {
                _uiState.update { it.copy(actionError = "Failed to update stock.") }
            }
        }
    }

    // Edit Product & Stock
    fun openEditProduct(product: ProductEntity) = _uiState.update {
        it.copy(editProductCode = product.catalogCode, actionError = null)
    }

    fun dismissEditProduct() = _uiState.update { it.copy(editProductCode = null, actionError = null) }

    fun confirmEditProduct(
        product: ProductEntity,
        name: String,
        newStock: Int,
        newPrice: Int,
        units: List<SaleUnit>,
        barcode: String? = null
    ) {
        if (newStock < 0 || newPrice < 1) {
            _uiState.update { it.copy(actionError = "Enter valid stock count and selling price.") }
            return
        }
        viewModelScope.launch {
            val updated = product.copy(
                name = name.ifBlank { product.name },
                stockQuantity = newStock,
                sellingPriceKsh = newPrice,
                salesUnitsJson = serializeSaleUnits(units),
                verifiedBarcode = barcode?.trim()?.ifBlank { null } ?: product.verifiedBarcode
            )
            val counterId = _uiState.value.selectedCounterId
            if (counterId.isNullOrBlank()) { _uiState.update { it.copy(actionError = "Choose a counter first.") }; return@launch }
            val success = repository.updateProduct(updated, counterId)
            if (success) {
                _uiState.update {
                    it.copy(
                        editProductCode = null,
                        notice = "✓ Updated ${updated.name}: Stock set to $newStock bottles, price KSh $newPrice"
                    )
                }
                refreshCounterStock(counterId)
            } else {
                _uiState.update { it.copy(actionError = "Failed to save product changes.") }
            }
        }
    }

    // Add New Product with Configurable Units of Sale
    fun openAddNewProduct(prefilledBarcode: String? = null) = _uiState.update {
        it.copy(
            isAddingNewProduct = true,
            prefilledBarcode = prefilledBarcode,
            actionError = null
        )
    }

    fun dismissAddNewProduct() = _uiState.update {
        it.copy(isAddingNewProduct = false, prefilledBarcode = null, actionError = null)
    }

    fun confirmCreateProduct(
        name: String,
        barcode: String,
        category: String,
        basePrice: Int,
        initialStock: Int,
        unitCostKsh: Int,
        units: List<SaleUnit>,
        brand: String = "",
        size: String = ""
    ) {
        if (name.isBlank() || basePrice < 1) {
            _uiState.update { it.copy(actionError = "Enter product name and valid base price.") }
            return
        }
        viewModelScope.launch {
            val counterId = _uiState.value.selectedCounterId
            if (counterId.isNullOrBlank()) { _uiState.update { it.copy(actionError = "Choose a receiving counter first.") }; return@launch }
            val code = barcode.trim().ifBlank { "PROD_" + UUID.randomUUID().toString().take(8) }
            val success = repository.registerNewProduct(
                barcode = code,
                name = name.trim(),
                category = category.trim().ifBlank { "Local Liquor" },
                priceKsh = basePrice,
                initialStock = initialStock.coerceAtLeast(0),
                unitCostKsh = unitCostKsh,
                brand = brand,
                size = size,
                salesUnits = units,
                selectedCounterId = counterId
            )
            if (success) {
                _uiState.update {
                    it.copy(
                        isAddingNewProduct = false,
                        prefilledBarcode = null,
                        actionError = null,
                        notice = "✓ Added new product '$name' with $initialStock bottles and ${units.size} sales units."
                    )
                }
                refreshCounterStock(counterId)
            } else {
                _uiState.update { it.copy(actionError = "Failed to add product.") }
            }
        }
    }

    // Add Catalog Item to Shop
    fun openCatalogItem(product: ProductEntity) = _uiState.update {
        it.copy(
            selectedCatalogCode = product.catalogCode,
            catalogInitialStock = 12,
            catalogSellingPrice = product.suggestedPriceKsh.toString(),
            actionError = null
        )
    }

    fun dismissCatalogItem() = _uiState.update {
        it.copy(selectedCatalogCode = null, actionError = null)
    }

    fun confirmAddCatalogItem(code: String, quantity: Int, priceKsh: Int, unitCostKsh: Int) {
        if (quantity < 1 || priceKsh < 1 || unitCostKsh < 0) {
            _uiState.update { it.copy(actionError = "Enter valid quantity, cost, and selling price.") }
            return
        }
        viewModelScope.launch {
            val counterId = _uiState.value.selectedCounterId
            if (counterId.isNullOrBlank()) { _uiState.update { it.copy(actionError = "Choose a receiving counter first.") }; return@launch }
            runCatching { repository.addFromLibrary(code, quantity, priceKsh, unitCostKsh, counterId) }
                .onSuccess { success ->
                    if (success) {
                        val item = products.value.firstOrNull { it.catalogCode == code }
                        _uiState.update {
                            it.copy(
                                selectedCatalogCode = null,
                                stockMode = StockMode.MY_STOCK,
                                actionError = null,
                                notice = "✓ Added ${item?.name ?: "Product"} to your shop stock!"
                            )
                        }
                        refreshCounterStock(counterId)
                    } else {
                        _uiState.update { it.copy(actionError = "This catalog item is not linked to the backend catalog. Refresh products and try again.") }
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(actionError = error.message ?: "Could not add to stock.") }
                }
        }
    }

    fun undoLastSale() {
        if (mutationInProgress) return
        mutationInProgress = true
        viewModelScope.launch {
            try {
                val success = repository.undoLatestSale()
                _uiState.update {
                    it.copy(notice = if (success) "✓ Last sale undone and stock restored" else "Nothing to undo")
                }
                refreshCounterStock()
            } catch (e: Exception) {
                _uiState.update { it.copy(notice = e.message ?: "Failed to undo") }
            } finally {
                mutationInProgress = false
            }
        }
    }

    fun updatePhoto(product: ProductEntity, uri: String) {
        viewModelScope.launch {
            repository.updatePhoto(product, uri)
            _uiState.update { it.copy(notice = "Photo updated for ${product.name}") }
        }
    }
}

class CounterPosViewModelFactory(
    private val repository: CounterPosRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(CounterPosViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        return CounterPosViewModel(repository, connectivityManager) as T
    }
}
