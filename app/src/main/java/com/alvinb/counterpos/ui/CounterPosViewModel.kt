package com.alvinb.counterpos.ui

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
import com.alvinb.counterpos.ui.scanner.ScannerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

enum class PosTab { SCAN, STOCK, SALES, REPORTS, TABLES }
enum class StockMode { MY_STOCK, LIBRARY }

data class AdminEnrollmentCredential(
    val businessId: String,
    val businessName: String,
    val role: String,
    val passkey: String,
    val expiresInHours: Int
)

data class CounterPosUiState(
    val tab: PosTab = PosTab.SCAN,
    val stockMode: StockMode = StockMode.MY_STOCK,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val libraryCategory: String = "All",
    val selectedTableId: String? = null,
    val selectedCounterId: String? = null,
    val counterReport: List<CounterReportRow> = emptyList(),
    val isLoadingCounterReport: Boolean = false,
    val counterStockByProduct: Map<String, Int> = emptyMap(),
    val isCreatingTable: Boolean = false,
    val adminSelectedBusinessId: String? = null,
    val adminQrRole: String = "owner",
    val isCreatingAdminBusiness: Boolean = false,
    val isTeamPanelOpen: Boolean = false,
    val loginQrPayload: String? = null,
    val isQrPolling: Boolean = false,
    val employeeMembers: List<JSONObject> = emptyList(),
    val isSavingBusinessName: Boolean = false,
    val isAccessLocked: Boolean = false,
    val accessLockMessage: String? = null,
    val employeePasskey: String? = null,
    val adminEnrollmentCredential: AdminEnrollmentCredential? = null,

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

class CounterPosViewModel(private val repository: CounterPosRepository) : ViewModel() {
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
    private var mutationInProgress = false

    init {
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
            .onSuccess { token ->
                _uiState.update {
                    it.copy(
                        loginQrPayload = token,
                        isQrPolling = true,
                        startupError = null,
                        notice = "QR ready. Scan it with the Superadmin phone; this phone will sign in automatically."
                    )
                }
                pollAccessChallengeUntilApproved(token)
            }
            .onFailure { error ->
                val message = error.message.orEmpty()
                val friendly = if (message.contains("function was not found", ignoreCase = true) || message.contains("404")) {
                    "Secure QR sign-in is not deployed on this Supabase project. Deploy create-access-challenge, then tap Retry."
                } else message.ifBlank { "Could not prepare QR sign-in" }
                _uiState.update { it.copy(loginQrPayload = null, isQrPolling = false, startupError = friendly, notice = null) }
            }
    }

    fun signInWithQrRequested() = viewModelScope.launch {
        if (_uiState.value.isQrPolling) return@launch
        if (_uiState.value.startupError?.contains("expired", ignoreCase = true) == true) {
            prepareQrChallenge()
            return@launch
        }
        val token = _uiState.value.loginQrPayload
        if (token.isNullOrBlank()) {
            prepareQrChallenge()
            return@launch
        }
        _uiState.update { it.copy(isQrPolling = true, startupError = null, notice = "Waiting for Superadmin approval…") }
        pollAccessChallengeUntilApproved(token)
    }

    private suspend fun pollAccessChallengeUntilApproved(token: String, missingQrRetries: Int = 0) {
        repeat(300) {
            try {
                val result = repository.pollAccessChallenge(token)
                if (result != null) {
                    _uiState.update { it.copy(isQrPolling = false, notice = "Signed in successfully") }
                    return
                }
            } catch (error: Exception) {
                val message = error.message.orEmpty()
                val qrNotFound = message.contains("qr_not_found", ignoreCase = true) ||
                    (message.contains("qr", ignoreCase = true) && message.contains("not found", ignoreCase = true))
                if (qrNotFound && missingQrRetries == 0) {
                    val replacement = runCatching { repository.createAccessChallenge() }.getOrNull()
                    if (!replacement.isNullOrBlank()) {
                        _uiState.update {
                            it.copy(
                                loginQrPayload = replacement,
                                isQrPolling = true,
                                startupError = null,
                                notice = "The previous QR was not registered. A fresh QR is ready; ask the Superadmin to scan this new code."
                            )
                        }
                        return pollAccessChallengeUntilApproved(replacement, missingQrRetries + 1)
                    }
                }
                val friendly = if (qrNotFound) {
                    "The login QR is not registered by the connected backend. Deploy the current create-access-challenge and poll-access-challenge functions to this Supabase project, then tap Retry QR setup."
                } else message.ifBlank { "QR sign-in failed" }
                _uiState.update { it.copy(loginQrPayload = null, isQrPolling = false, startupError = friendly, notice = null) }
                return
            }
            delay(2_000)
        }
        _uiState.update { it.copy(isQrPolling = false, startupError = "QR request expired. Generate a new QR code and try again.") }
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
        _uiState.update { it.copy(loginQrPayload = null, isQrPolling = false, adminEnrollmentCredential = null, startupError = null, notice = null) }
        prepareQrChallenge()
    }

    fun clearStartupError() = _uiState.update { it.copy(startupError = null) }

    fun openTeamPanel() = _uiState.update { it.copy(isTeamPanelOpen = true, actionError = null) }

    fun closeTeamPanel() = _uiState.update { it.copy(isTeamPanelOpen = false, actionError = null) }

    private fun monitorActiveHours() = viewModelScope.launch {
        while (isActive) {
            runCatching { repository.canOperateNow() }
                .onSuccess { allowed ->
                    _uiState.update { state ->
                        state.copy(
                            isAccessLocked = !allowed,
                            accessLockMessage = if (allowed) null else "This employee account is outside its configured working hours. The workspace will reopen automatically when the next active period begins."
                        )
                    }
                }
            delay(30_000)
        }
    }

    fun refreshAccessNow() = viewModelScope.launch {
        runCatching { repository.canOperateNow() }
            .onSuccess { allowed -> _uiState.update { it.copy(isAccessLocked = !allowed, accessLockMessage = if (allowed) null else "You are outside your configured working hours.") } }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not verify working hours") } }
    }

    fun issueEmployeePass(role: String) = viewModelScope.launch {
        if (role != "cashier") {
            _uiState.update { it.copy(actionError = "Waiter is the only employee position currently available.") }
            return@launch
        }
        runCatching { repository.createEmployeeAccessPass(role) }
            .onSuccess { pass -> _uiState.update { it.copy(employeePasskey = pass.optString("passkey"), notice = "One-time ${role.replace('_', ' ')} pass created. Share it privately.") } }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not create employee pass") } }
    }

    fun clearEmployeePass() = _uiState.update { it.copy(employeePasskey = null) }

    fun loadEmployeeMembers() = viewModelScope.launch {
        runCatching { repository.shopMembers() }
            .onSuccess { rows -> _uiState.update { it.copy(employeeMembers = (0 until rows.length()).mapNotNull(rows::optJSONObject)) } }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not load employees") } }
    }

    fun setEmployeeStatus(userId: String, status: String) = viewModelScope.launch {
        runCatching { repository.setEmployeeStatus(userId, status, if (status == "active") "Reactivated by business admin" else "Disabled by business admin") }
            .onSuccess { rows -> _uiState.update { it.copy(employeeMembers = (0 until rows.length()).mapNotNull(rows::optJSONObject), notice = "Employee status updated") } }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not update employee") } }
    }

    fun setEmployeeRole(userId: String, role: String) = viewModelScope.launch {
        runCatching { repository.setEmployeeRole(userId, role) }
            .onSuccess { rows -> _uiState.update { it.copy(employeeMembers = (0 until rows.length()).mapNotNull(rows::optJSONObject), notice = "Employee role updated") } }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not update employee role") } }
    }

    fun removeEmployeeAccess(userId: String) = viewModelScope.launch {
        runCatching { repository.removeEmployeeAccess(userId, "Access removed by business administrator") }
            .onSuccess { rows -> _uiState.update { it.copy(employeeMembers = (0 until rows.length()).mapNotNull(rows::optJSONObject), actionError = null, notice = "Employee access removed; their history is retained") } }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not remove employee access") } }
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
            .onSuccess { rows -> _uiState.update { it.copy(employeeMembers = (0 until rows.length()).mapNotNull(rows::optJSONObject), notice = "Working hours saved") } }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not save working hours") } }
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
        if (qr.startsWith("CPR1|")) {
            val currentSession = session.value
            val currentState = _uiState.value
            val isPlatformAdmin = currentSession.isPlatformAdmin
            val shopId = if (isPlatformAdmin) currentState.adminSelectedBusinessId else currentSession.activeBusiness?.shopId
            val business = _adminBusinesses.value.firstOrNull { it.shopId == shopId }
            val businessRole = currentSession.activeBusiness?.role
            val role = if (isPlatformAdmin) currentState.adminQrRole else "cashier"
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
                                    notice = "Approved for $businessName as ${role.replace('_', ' ')}. The other phone should sign in automatically."
                                )
                            }
                        }
                        .onFailure { error -> _uiState.update { it.copy(isScannerOpen = false, startupError = error.message ?: "Could not approve sign-in QR") } }
                }
            }
            return
        }
        val parts = qr.split('|', limit = 4)
        if (parts.size < 3 || !parts[0].equals("COUNTERPOS", ignoreCase = true)) {
            _uiState.update { it.copy(isScannerOpen = false, startupError = "Unrecognized CounterPOS QR. Use a business or user enrollment QR.") }
            return
        }
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
            }
            "USER" -> {
                val shopId = _uiState.value.adminSelectedBusinessId
                val email = parts.getOrNull(2).orEmpty().trim()
                val role = parts.getOrNull(3).orEmpty().trim().ifBlank { "cashier" }
                if (shopId.isNullOrBlank()) {
                    _uiState.update { it.copy(isScannerOpen = false, startupError = "Open a business profile before scanning a staff QR.") }
                } else {
                    viewModelScope.launch {
                        runCatching { repository.addMemberByEmail(shopId, email, role) }
                            .onSuccess {
                                _adminBusinesses.value = it
                                _uiState.update { state -> state.copy(isScannerOpen = false, notice = "$email attached as $role") }
                            }
                            .onFailure { error -> _uiState.update { state -> state.copy(isScannerOpen = false, startupError = error.message ?: "Could not attach user") } }
                    }
                }
            }
            else -> _uiState.update { it.copy(isScannerOpen = false, startupError = "Unsupported CounterPOS QR type.") }
        }
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

    fun confirmSale() {
        val state = _uiState.value
        val code = state.selectedSaleCode ?: return
        if (state.selectedCounterId.isNullOrBlank()) {
            _uiState.update { it.copy(actionError = "Choose a serving counter first.") }
            return
        }
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
                        selectedCounterId = state.selectedCounterId
                    )
                } else {
                    repository.sellWithUnit(
                        code = code,
                        quantity = state.saleQuantity,
                        unitName = state.selectedUnitName,
                        unitPriceKsh = state.selectedUnitPrice,
                        selectedCounterId = state.selectedCounterId
                    )
                }
                if (success) {
                    refreshCounterStock(state.selectedCounterId)
                    val product = products.value.firstOrNull { it.catalogCode == code }
                    val total = state.selectedUnitPrice * state.saleQuantity
                    _uiState.update {
                        it.copy(
                            selectedSaleCode = null,
                            saleQuantity = 1,
                            actionError = null,
                            notice = if (tableId != null) {
                                "✓ Added ${state.saleQuantity} × ${product?.name ?: ""} to table order · KSh $total"
                            } else {
                                "✓ Sold ${state.saleQuantity}x ${product?.name ?: ""} (${state.selectedUnitName}) for KSh $total"
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
            val success = repository.addFromLibrary(code, quantity, priceKsh, unitCostKsh, counterId)
            if (success) {
                val item = products.value.firstOrNull { it.catalogCode == code }
                _uiState.update {
                    it.copy(
                        selectedCatalogCode = null,
                        stockMode = StockMode.MY_STOCK,
                        notice = "✓ Added ${item?.name ?: "Product"} to your shop stock!"
                    )
                }
                refreshCounterStock(counterId)
            } else {
                _uiState.update { it.copy(actionError = "Could not add to stock.") }
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

class CounterPosViewModelFactory(private val repository: CounterPosRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(CounterPosViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        return CounterPosViewModel(repository) as T
    }
}
