package com.alvinb.counterpos.ui

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alvinb.counterpos.data.CounterPosRepository
import com.alvinb.counterpos.data.SubscriptionEntitlement
import com.alvinb.counterpos.data.SubscriptionManager
import com.alvinb.counterpos.data.TableSettlementResult
import com.alvinb.counterpos.data.remote.AdminBusiness
import com.alvinb.counterpos.data.remote.AdminAuditEvent
import com.alvinb.counterpos.data.remote.PlatformBillingSettings
import com.alvinb.counterpos.data.remote.CounterReportRow
import com.alvinb.counterpos.data.remote.ServiceCounter
import com.alvinb.counterpos.data.SessionSnapshot
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.local.entity.DiningTableEntity
import com.alvinb.counterpos.data.local.entity.OutboxEntity
import com.alvinb.counterpos.data.local.entity.SaleUnit
import com.alvinb.counterpos.data.local.entity.resolveSaleUnits
import com.alvinb.counterpos.data.local.entity.serializeSaleUnits
import com.alvinb.counterpos.data.model.BusinessHours
import com.alvinb.counterpos.data.model.DayBusinessHours
import com.alvinb.counterpos.ui.scanner.ScannerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
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

data class EmployeeRecoveryCredential(
    val employeeUserId: String,
    val employeeName: String,
    val passkey: String,
    val qrPayload: String,
    val expiresInMinutes: Int
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
    val seenKeys = mutableSetOf<String>()
    for (index in 0 until length()) {
        optJSONObject(index)?.let { obj ->
            val role = obj.optString("role", "").lowercase()
            // Business owner is NOT an employee/staff member
            if (role == "owner") return@let
            val userId = obj.optString("user_id").ifBlank {
                obj.optString("member_user_id").ifBlank { obj.optString("id") }
            }
            val email = obj.optString("email").trim().lowercase()
            val dedupeKey = if (userId.isNotBlank()) userId else email
            if (dedupeKey.isNotBlank()) {
                if (seenKeys.add(dedupeKey)) add(obj)
            } else {
                add(obj)
            }
        }
    }
}

private fun isValidHourMinute(value: String): Boolean = HourMinutePattern.matcher(value.trim()).matches()

data class CounterPosUiState(
    val tab: PosTab = PosTab.SCAN,
    val previousTab: PosTab? = null,
    val isOnline: Boolean = true,
    val stockMode: StockMode = StockMode.MY_STOCK,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val libraryCategory: String = "All",
    val selectedTableId: String? = null,
    val selectedCounterId: String? = null,
    val selectedServingCounterId: String? = null,
    val selectedServingCounterName: String? = null,
    val selectedWaiterName: String = "",
    val selectedPaymentMethod: String = "cash",
    val availableWaiters: List<String> = emptyList(),
    val reportFilter: String = "today",
    val reportCounterFilter: String = "all",
    val reportPaymentFilter: String = "all",
    val isRecordDispensationOpen: Boolean = false,
    val counterReport: List<CounterReportRow> = emptyList(),
    val isLoadingCounterReport: Boolean = false,
    val counterStockByProduct: Map<String, Int> = emptyMap(),
    val isCreatingTable: Boolean = false,
    val isDeletingTable: Boolean = false,
    val isSettlingTable: Boolean = false,
    val isSyncingData: Boolean = false,
    val isRefreshingBusinessData: Boolean = false,
    val pendingTableSettlementIds: Set<String> = emptySet(),
    val blockedTableSettlementIds: Set<String> = emptySet(),
    val adminSelectedBusinessId: String? = null,
    val adminQrRole: String = "owner",
    val isLoadingAdminBusinesses: Boolean = false,
    val hasFreshAdminBusinesses: Boolean = false,
    val adminBusinessesError: String? = null,
    val isCreatingAdminBusiness: Boolean = false,
    val isCreatingBusiness: Boolean = false,
    val adminAuditEvents: List<AdminAuditEvent> = emptyList(),
    val isLoadingAuditEvents: Boolean = false,
    val isTeamPanelOpen: Boolean = false,
    val loginQrPayload: String? = null,
    val loginQrExpiresAtMillis: Long? = null,
    val isQrPolling: Boolean = false,
    val isAuthenticating: Boolean = false,
    val employeeMembers: List<JSONObject> = emptyList(),
    val isSavingBusinessName: Boolean = false,
    val isLoadingEmployeeMembers: Boolean = false,
    val businessHours: BusinessHours = BusinessHours.Unrestricted,
    val isLoadingBusinessHours: Boolean = false,
    val isSavingBusinessHours: Boolean = false,
    val isIssuingEmployeePass: Boolean = false,
    val isSavingEmployeeSchedule: Boolean = false,
    val isClearingEmployeeSchedule: Boolean = false,
    val isIssuingEmployeeRecoveryPass: Boolean = false,
    val employeeRecoveryCredential: EmployeeRecoveryCredential? = null,
    val isAccessLocked: Boolean = false,
    val accessLockMessage: String? = null,
    val employeePasskey: String? = null,
    val adminEnrollmentCredential: AdminEnrollmentCredential? = null,
    val isStaffManagementOpen: Boolean = false,
    val staffManagementStartsOnHours: Boolean = false,

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
    val startupError: String? = null,
    val pendingSyncCount: Int = 0,
    val blockedOutbox: List<OutboxEntity> = emptyList(),
    val isSyncRecoveryOpen: Boolean = false,
    val syncRecoveryError: String? = null,
    val retryingOperationId: String? = null,

    // Subscription & M-Pesa STK Push
    val isSubscriptionModalOpen: Boolean = false,
    val isInitiatingStkPush: Boolean = false,
    val isPollingStkPayment: Boolean = false,
    val stkCheckoutRequestId: String? = null,
    val stkCustomerMessage: String? = null,
    val subscriptionPaymentError: String? = null,
    val isSubscriptionPaymentSuccess: Boolean = false,
    val isSubscriptionEntitled: Boolean = true,
    val subscriptionEntitlement: SubscriptionEntitlement? = null,
    val adminSelectedSubscriptionBusiness: AdminBusiness? = null,
    val isUpdatingSubscription: Boolean = false,
    val platformBillingSettings: PlatformBillingSettings? = null,
    val isLoadingPlatformBillingSettings: Boolean = false,
    val isSavingPlatformBillingSettings: Boolean = false
)

class CounterPosViewModel(
    private val repository: CounterPosRepository,
    private val connectivityManager: ConnectivityManager,
    private val subscriptionManager: SubscriptionManager
) : ViewModel() {
    val session = repository.session.stateIn(viewModelScope, SharingStarted.Eagerly, SessionSnapshot())
    private val _adminBusinesses = MutableStateFlow<List<AdminBusiness>>(repository.getCachedAdminBusinesses())
    val adminBusinesses = _adminBusinesses.asStateFlow()
    val products = repository.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sales = repository.sales.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val diningTables = repository.diningTables.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tableOrders = repository.tableOrders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val serviceCounters = repository.serviceCounters.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(CounterPosUiState(adminAuditEvents = repository.getCachedAdminAuditEvents()))
    val uiState = _uiState.asStateFlow()
    private var mutationInProgress = false
    private var reconnectSyncJob: Job? = null
    private var tableSettlementOutboxJob: Job? = null
    private var loginQrJob: Job? = null
    private var lastSessionKeepAliveAt = 0L
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _uiState.update { it.copy(isOnline = true) }
            refreshSubscriptionEntitlement()
            reconnectSyncJob?.cancel()
            reconnectSyncJob = viewModelScope.launch {
                delay(150)
                runCatching {
                    repository.keepSessionAlive()
                    repository.syncPendingOutbox()
                    repository.synchronizeWhenOnline()
                    refreshCounterStock()
                }
                    .onSuccess { _uiState.update { it.copy(notice = "Internet connected. Real-time data synchronized.") } }
                    .onFailure { _uiState.update { it.copy(notice = "Syncing in background…") } }
            }
        }

        override fun onLost(network: Network) {
            _uiState.update {
                it.copy(
                    isOnline = false,
                    notice = "Offline mode. Sales and tables are stored locally."
                )
            }
            refreshSubscriptionEntitlement()
        }
    }

    init {
        runCatching {
            val net = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(net)
            val isOnline = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            _uiState.update { it.copy(isOnline = isOnline) }
        }
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
        viewModelScope.launch {
            repository.session.collect { sess ->
                refreshSubscriptionEntitlement(sess)
                tableSettlementOutboxJob?.cancel()
                if (sess.isPlatformAdmin) {
                    loadAdminBusinesses()
                    loadAdminAuditEvents()
                }
                if (sess.activeBusiness == null) {
                    _uiState.update {
                        it.copy(
                            businessHours = BusinessHours.Unrestricted,
                            blockedOutbox = emptyList(),
                            pendingTableSettlementIds = emptySet(),
                            blockedTableSettlementIds = emptySet()
                        )
                    }
                } else {
                    val shopId = sess.activeBusiness.shopId
                    tableSettlementOutboxJob = viewModelScope.launch {
                        repository.observeTableSettlementOutbox(shopId).collect { items ->
                            val pendingIds = items.asSequence()
                                .filterNot { it.blocked }
                                .mapNotNull { runCatching { JSONObject(it.payloadJson).optString("table_local_id") }.getOrNull() }
                                .filter(String::isNotBlank)
                                .toSet()
                            val blockedIds = items.asSequence()
                                .filter { it.blocked }
                                .mapNotNull { runCatching { JSONObject(it.payloadJson).optString("table_local_id") }.getOrNull() }
                                .filter(String::isNotBlank)
                                .toSet()
                            _uiState.update {
                                it.copy(
                                    pendingTableSettlementIds = pendingIds,
                                    blockedTableSettlementIds = blockedIds
                                )
                            }
                        }
                    }
                    runCatching { repository.loadBusinessHours() }
                        .onSuccess { hours -> _uiState.update { it.copy(businessHours = hours) } }
                        .onFailure {
                            _uiState.update { it.copy(businessHours = BusinessHours.Unrestricted) }
                        }
                    runCatching { repository.blockedOutboxItems() }
                        .onSuccess { items -> _uiState.update { it.copy(blockedOutbox = items) } }
                }
            }
        }
        viewModelScope.launch {
            repository.pendingSyncCount.collect { count ->
                _uiState.update { it.copy(pendingSyncCount = count) }
            }
        }
        viewModelScope.launch {
            try {
                repository.restoreSession()
                _uiState.update { it.copy(isReady = true) }
                monitorActiveHours()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isReady = true,
                        startupError = if (repository.session.value.isAuthenticated) null else e.message
                    )
                }
                monitorActiveHours()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                // Realtime invalidation is the primary refresh path. This remains
                // a conservative fallback for background disconnects and missed events.
                delay(30_000)
                refreshSubscriptionEntitlement()
                if (repository.session.value.isAuthenticated) {
                    val now = System.currentTimeMillis()
                    if (repository.session.value.activeBusiness != null) {
                        runCatching {
                            repository.synchronizeWhenOnline()
                            refreshCounterStock()
                        }
                    } else if (now - lastSessionKeepAliveAt >= 5 * 60 * 1_000L) {
                        lastSessionKeepAliveAt = now
                        runCatching { repository.keepSessionAlive() }
                    }
                }
            }
        }
    }

    private fun refreshSubscriptionEntitlement(
        snapshot: SessionSnapshot = repository.session.value,
        online: Boolean = _uiState.value.isOnline
    ) {
        val business = snapshot.activeBusiness
        if (business == null || snapshot.isPlatformAdmin) {
            _uiState.update { it.copy(isSubscriptionEntitled = true, subscriptionEntitlement = null) }
            return
        }
        val entitlement = subscriptionManager.evaluateMembership(business, isOnline = online)
        _uiState.update {
            it.copy(
                isSubscriptionEntitled = entitlement.isEntitled,
                subscriptionEntitlement = entitlement
            )
        }
    }

    override fun onCleared() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        reconnectSyncJob?.cancel()
        super.onCleared()
    }

    private fun runAuthentication(
        progressMessage: String,
        fallbackMessage: String,
        action: suspend () -> SessionSnapshot,
        successNotice: (SessionSnapshot) -> String? = { snapshot ->
            snapshot.message
                ?: snapshot.activeBusiness?.shopName?.let { "Signed in to $it" }
                ?: if (snapshot.isPlatformAdmin) "Superadmin access granted" else null
        }
    ) = viewModelScope.launch {
        if (_uiState.value.isAuthenticating) return@launch
        _uiState.update { it.copy(isAuthenticating = true, startupError = null, notice = progressMessage) }
        try {
            val snapshot = action()
            _uiState.update { it.copy(startupError = null, notice = successNotice(snapshot)) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val rawMessage = error.message.orEmpty()
            val friendlyMessage = when {
                rawMessage.contains("invalid login credentials", ignoreCase = true) ->
                    "These credentials were not accepted. Check them and try again."
                rawMessage.contains("email not confirmed", ignoreCase = true) || rawMessage.contains("email_not_confirmed", ignoreCase = true) ->
                    "Confirm your email address before signing in."
                rawMessage.contains("user already registered", ignoreCase = true) || rawMessage.contains("already registered", ignoreCase = true) ->
                    "An account with this email already exists. Sign in instead."
                else -> rawMessage.ifBlank { fallbackMessage }
            }
            _uiState.update { it.copy(startupError = friendlyMessage, notice = null) }
        } finally {
            _uiState.update { it.copy(isAuthenticating = false) }
        }
    }

    fun signIn(email: String, password: String) = runAuthentication(
        progressMessage = "Signing in…",
        fallbackMessage = "Sign in failed",
        action = { repository.signIn(email, password) }
    )

    fun signInWithPasskey(passkey: String) = runAuthentication(
        progressMessage = "Verifying access passkey…",
        fallbackMessage = "Passkey sign-in failed",
        action = { repository.signInWithPasskey(passkey) }
    )

    fun signInWithSuperadminToken(token: String) = runAuthentication(
        progressMessage = "Verifying Superadmin token…",
        fallbackMessage = "Superadmin token sign-in failed",
        action = { repository.signInWithSuperadminToken(token) }
    ) { "Superadmin access granted" }

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

    fun prepareQrChallenge() {
        loginQrJob?.cancel()
        loginQrJob = viewModelScope.launch {
            _uiState.update {
                it.copy(loginQrPayload = null, isQrPolling = false, startupError = null, notice = "Connecting to secure QR sign-in…")
            }
            try {
                val challenge = repository.createAccessChallenge()
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
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = error.message.orEmpty()
                val friendly = when {
                    message.contains("404", ignoreCase = true) || message.contains("not found", ignoreCase = true) || message.contains("function", ignoreCase = true) ->
                        "QR sign-in is not available on the connected backend. Deploy the current enrollment functions, then refresh QR."
                    message.contains("401", ignoreCase = true) || message.contains("unauthorized", ignoreCase = true) ->
                        "The QR sign-in service rejected this request. Check the deployed enrollment functions, then refresh QR."
                    else -> "QR sign-in is unavailable right now. Tap Refresh QR to try again."
                }
                _uiState.update { it.copy(loginQrPayload = null, isQrPolling = false, startupError = friendly, notice = null) }
            }
        }
    }

    private suspend fun pollAccessChallengeUntilApproved(token: String, missingQrRetries: Int = 0) {
        repeat(300) {
            try {
                val result = repository.pollAccessChallenge(token)
                if (result != null) {
                    val targetBusinessId = result.businessId ?: result.session.activeBusiness?.shopId
                    val activeBusiness = result.session.activeBusiness
                    if (targetBusinessId.isNullOrBlank() || activeBusiness == null || activeBusiness.shopId != targetBusinessId) {
                        throw IllegalStateException("The QR was approved, but this account is not connected to the enrolled business. Ask the owner to verify the staff membership.")
                    }
                    _uiState.update {
                        it.copy(
                            isQrPolling = false,
                            startupError = null,
                            notice = "Signed in to ${activeBusiness.shopName}"
                        )
                    }
                    return
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val message = error.message.orEmpty()
                val qrNotFound = message.contains("qr_not_found", ignoreCase = true) ||
                    (message.contains("qr", ignoreCase = true) && message.contains("not found", ignoreCase = true))
                if (qrNotFound && missingQrRetries < 3) {
                    // Do not silently replace the displayed token: an owner or
                    // administrator may already be scanning this exact QR.
                    delay(1_500)
                    return pollAccessChallengeUntilApproved(token, missingQrRetries + 1)
                }
                val friendly = if (message.contains("expired", ignoreCase = true) || message.contains("used", ignoreCase = true)) {
                    "This QR code has expired or was already used. Tap Refresh QR to generate a new one."
                } else if (qrNotFound) {
                    "This QR is not registered by the connected backend. Deploy the current create-access-challenge and poll-access-challenge functions, then generate a new QR."
                } else {
                    message.takeIf { it.isNotBlank() }?.let { "QR sign-in failed: $it" }
                        ?: "QR sign-in is unavailable right now. Tap Refresh QR to try again."
                }
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

    fun signUp(email: String, password: String) = runAuthentication(
        progressMessage = "Creating your account…",
        fallbackMessage = "Could not create account",
        action = { repository.signUp(email, password) },
        successNotice = { it.message }
    )

    fun registerOwnerAndBusiness(
        email: String,
        password: String,
        businessName: String,
        businessType: String = "big_club",
        businessSize: String = "medium",
        inventoryMode: String = "shared",
        counterCount: Int = 1,
        ownerPhone: String = ""
    ) = viewModelScope.launch {
        if (_uiState.value.isAuthenticating || _uiState.value.isCreatingBusiness) return@launch
        val cleanEmail = email.trim()
        val cleanName = businessName.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            _uiState.update { it.copy(startupError = "Please enter a valid email address.") }
            return@launch
        }
        if (password.length < 6) {
            _uiState.update { it.copy(startupError = "Password must be at least 6 characters.") }
            return@launch
        }
        if (cleanName.isBlank()) {
            _uiState.update { it.copy(startupError = "Please enter your business name.") }
            return@launch
        }
        val normalizedOwnerPhone = SubscriptionManager.normalizeKenyanPhone(ownerPhone)
        if (normalizedOwnerPhone == null) {
            _uiState.update { it.copy(startupError = "Enter a valid Kenyan owner mobile number.") }
            return@launch
        }

        _uiState.update {
            it.copy(
                isAuthenticating = true,
                startupError = null,
                notice = "Creating your account…"
            )
        }

        try {
            var signupMessage: String? = null
            var signupFailure: Exception? = null
            try {
                signupMessage = repository.signUp(cleanEmail, password).message
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // A previous signup may have created the Auth user before the
                // app lost its response. Authenticate that account and resume
                // the idempotent owner-profile operation instead of dead-ending.
                signupFailure = error
            }
            val signupError = signupFailure?.message.orEmpty()
            val existingAccount = signupError.contains("already registered", ignoreCase = true) ||
                signupError.contains("user_already_exists", ignoreCase = true)
            var currentSession = repository.session.value
            var signInFailure: Exception? = null
            if (!currentSession.isAuthenticated) {
                try {
                    repository.signIn(cleanEmail, password)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    signInFailure = error
                }
                currentSession = repository.session.value
            }

            if (!currentSession.isAuthenticated) {
                val signInError = signInFailure?.message.orEmpty()
                val friendlyMessage = when {
                    signInError.contains("email not confirmed", ignoreCase = true) ||
                        signInError.contains("email_not_confirmed", ignoreCase = true) ->
                        "This account’s email is not confirmed yet. Confirm the message sent to $cleanEmail, then sign in to finish setting up $cleanName."
                    existingAccount ->
                        "An account with this email already exists, but these credentials could not sign in. Check the password for $cleanEmail or recover that account; $cleanName has not been set up yet."
                    signupFailure != null -> signupFailure?.message?.takeIf(String::isNotBlank)
                        ?: "Could not create or sign in to the account. Try again."
                    else -> signupMessage
                        ?: "Your account was created, but no session was returned. Confirm $cleanEmail if prompted, then sign in to finish setting up $cleanName."
                }
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        startupError = friendlyMessage,
                        notice = null
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isAuthenticating = false,
                    isCreatingBusiness = true,
                    notice = "Setting up $cleanName…"
                )
            }

            repository.createBusiness(cleanName, businessType, businessSize, inventoryMode, counterCount, normalizedOwnerPhone)
            _uiState.update {
                it.copy(
                    isCreatingBusiness = false,
                    startupError = null,
                    notice = "Business profile created. Welcome to $cleanName!"
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val msg = error.message.orEmpty()
            val friendly = when {
                msg.contains("User already registered", ignoreCase = true) || msg.contains("already registered", ignoreCase = true) ->
                    "An account with this email already exists. Please sign in with your password."
                msg.contains("business_name_already_exists", ignoreCase = true) ->
                    "A business named $cleanName already exists for this account."
                else -> msg.ifBlank { "Could not complete registration." }
            }
            _uiState.update {
                it.copy(
                    isAuthenticating = false,
                    isCreatingBusiness = false,
                    startupError = friendly,
                    notice = null
                )
            }
        }
    }

    fun createBusiness(
        name: String,
        businessType: String = "big_club",
        businessSize: String = "medium",
        inventoryMode: String = "shared",
        counterCount: Int = 1,
        ownerPhone: String = ""
    ) = viewModelScope.launch {
        if (_uiState.value.isCreatingBusiness) return@launch
        _uiState.update { it.copy(isCreatingBusiness = true, startupError = null, notice = "Creating your business…") }
        try {
            val normalizedOwnerPhone = SubscriptionManager.normalizeKenyanPhone(ownerPhone)
                ?: throw IllegalArgumentException("Enter a valid Kenyan owner mobile number.")
            repository.createBusiness(name, businessType, businessSize, inventoryMode, counterCount, normalizedOwnerPhone)
            _uiState.update { it.copy(startupError = null, notice = "Business created. Opening your workspace…") }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val message = error.message.orEmpty()
            val friendly = when {
                message.contains("PGRST202", ignoreCase = true) ||
                    (message.contains("create_owner_business_profile", ignoreCase = true) && message.contains("not found", ignoreCase = true)) ->
                    "Business setup is not enabled on this backend yet. Apply the latest backend migrations, then try again."
                message.contains("business_name_already_exists", ignoreCase = true) ->
                    "A business with this name already exists for this account. Sign in to it or choose a different name."
                else -> message.ifBlank { "Could not create business" }
            }
            _uiState.update { it.copy(startupError = friendly, notice = null) }
        } finally {
            _uiState.update { it.copy(isCreatingBusiness = false) }
        }
    }

    fun selectBusiness(shopId: String) = viewModelScope.launch {
        runCatching { repository.selectBusiness(shopId) }
            .onFailure { error -> _uiState.update { it.copy(startupError = error.message ?: "Could not open business") } }
    }

    fun signOut() = viewModelScope.launch {
        repository.signOut()
        _uiState.update { it.copy(loginQrPayload = null, loginQrExpiresAtMillis = null, isQrPolling = false, adminEnrollmentCredential = null, startupError = null, notice = null) }
    }

    fun clearStartupError() = _uiState.update { it.copy(startupError = null) }

    fun openTeamPanel() = _uiState.update { it.copy(isTeamPanelOpen = true, isAccessLocked = false, actionError = null) }

    fun closeTeamPanel() {
        _uiState.update { it.copy(isTeamPanelOpen = false, actionError = null) }
        refreshAccessNow()
    }

    fun loadBusinessHours() = viewModelScope.launch {
        if (_uiState.value.isLoadingBusinessHours) return@launch
        _uiState.update { it.copy(isLoadingBusinessHours = true) }
        runCatching { repository.loadBusinessHours() }
            .onSuccess { hours -> _uiState.update { it.copy(businessHours = hours, actionError = null) } }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not load business hours") } }
        _uiState.update { it.copy(isLoadingBusinessHours = false) }
    }

    fun saveBusinessHours(hours: BusinessHours) = viewModelScope.launch {
        if (_uiState.value.isSavingBusinessHours) return@launch
        val schedule = hours.weeklySchedule
        val weekdaySet = schedule.map(DayBusinessHours::dayOfWeek).toSet()
        val validationError = when {
            schedule.size != 7 || weekdaySet != (1..7).toSet() -> "Configure each weekday from Monday through Sunday exactly once."
            hours.isEnabled && schedule.any { it.isOpen && it.openTime == it.closeTime } -> "Opening and closing times cannot be the same. Disable that day if the business is closed."
            else -> null
        }
        if (validationError != null) {
            _uiState.update { it.copy(actionError = validationError) }
            return@launch
        }
        _uiState.update { it.copy(isSavingBusinessHours = true, actionError = null) }
        runCatching { repository.saveBusinessHours(hours) }
            .onSuccess { saved ->
                _uiState.update { it.copy(businessHours = saved, notice = "Business hours saved.", actionError = null) }
                refreshAccessNow()
            }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not save business hours") } }
        _uiState.update { it.copy(isSavingBusinessHours = false) }
    }

    fun openStaffManagement(openHours: Boolean = false) = _uiState.update {
        it.copy(
            isStaffManagementOpen = true,
            isAccessLocked = false,
            staffManagementStartsOnHours = openHours,
            actionError = null
        )
    }.also {
        loadBusinessHours()
    }

    fun closeStaffManagement() {
        _uiState.update {
            it.copy(
                isStaffManagementOpen = false,
                staffManagementStartsOnHours = false,
                actionError = null,
                employeeRecoveryCredential = null
            )
        }
        refreshAccessNow()
    }

    private fun isBusinessOwnerOrAdmin(): Boolean =
        session.value.isPlatformAdmin || session.value.activeBusiness?.role.equals("owner", ignoreCase = true)

    private fun monitorActiveHours() = viewModelScope.launch {
        while (isActive) {
            if (isBusinessOwnerOrAdmin()) {
                _uiState.update { it.copy(isAccessLocked = false, accessLockMessage = null) }
                delay(30_000)
                continue
            }
            runCatching { repository.canOperateNow() }
                .onSuccess { allowed ->
                    _uiState.update { state ->
                        state.copy(
                            isAccessLocked = !allowed,
                            accessLockMessage = if (allowed) null else
                                "The business is outside its configured hours or this waiter is off duty. The POS will reopen when access is allowed again."
                        )
                    }
                }
                .onFailure {
                    // Retain current lock state on temporary connection drops
                }
            delay(30_000)
        }
    }

    fun refreshAccessNow() = viewModelScope.launch {
        if (isBusinessOwnerOrAdmin()) {
            _uiState.update { it.copy(isAccessLocked = false, accessLockMessage = null) }
            return@launch
        }
        runCatching { repository.canOperateNow() }
            .onSuccess { allowed ->
                _uiState.update { state ->
                    state.copy(
                        isAccessLocked = !allowed,
                        accessLockMessage = if (allowed) null else
                            "The business is outside its operating hours or this waiter is off duty."
                    )
                }
            }
            .onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        actionError = error.message ?: "Could not verify working hours"
                    )
                }
            }
    }

    fun issueEmployeePass(role: String = "cashier") = viewModelScope.launch {
        if (_uiState.value.isIssuingEmployeePass) return@launch
        val normalizedRole = if (role.equals("waiter", ignoreCase = true)) "cashier" else role
        if (normalizedRole != "cashier") {
            _uiState.update { it.copy(actionError = "Only the Waiter role is currently supported for employee enrollment.") }
            return@launch
        }
        _uiState.update { it.copy(isIssuingEmployeePass = true, employeePasskey = null, actionError = null) }
        try {
            val pass = repository.createEmployeeAccessPass(normalizedRole)
            val passkey = pass.optString("passkey").trim()
            require(passkey.isNotBlank()) { "The server did not return a staff passkey." }
            val roleLabel = when (normalizedRole) {
                "manager" -> "manager"
                "inventory_clerk" -> "inventory clerk"
                else -> "waiter"
            }
            _uiState.update {
                it.copy(
                    employeePasskey = passkey,
                    actionError = null,
                    notice = "One-time $roleLabel passkey created. Share it privately; the account is activated when redeemed."
                )
            }
        } catch (error: Exception) {
            _uiState.update { it.copy(actionError = error.message ?: "Could not create staff passkey") }
        } finally {
            _uiState.update { it.copy(isIssuingEmployeePass = false) }
        }
    }

    fun clearEmployeePass() = _uiState.update { it.copy(employeePasskey = null) }

    fun renameEmployee(userId: String, displayName: String) = viewModelScope.launch {
        if (userId.isBlank()) {
            _uiState.update { it.copy(actionError = "Select a staff account to rename.") }
            return@launch
        }
        if (displayName.trim().length !in 1..80) {
            _uiState.update { it.copy(actionError = "Enter a staff name between 1 and 80 characters.") }
            return@launch
        }
        runCatching { repository.renameEmployee(userId, displayName.trim()) }
            .onSuccess { rows ->
                _uiState.update {
                    it.copy(
                        employeeMembers = rows.toEmployeeMemberList(),
                        actionError = null,
                        notice = "Staff profile name updated."
                    )
                }
            }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not rename staff profile") } }
    }

    fun issueEmployeeRecoveryPass(userId: String, employeeName: String) = viewModelScope.launch {
        if (_uiState.value.isIssuingEmployeeRecoveryPass) return@launch
        if (userId.isBlank()) {
            _uiState.update { it.copy(actionError = "This staff member has no account ID to recover.") }
            return@launch
        }
        _uiState.update { it.copy(isIssuingEmployeeRecoveryPass = true, employeeRecoveryCredential = null, actionError = null) }
        try {
            val result = repository.createEmployeeRecoveryPass(userId)
            val passkey = result.optString("passkey").trim()
            require(passkey.isNotBlank()) { "The server did not return a recovery code." }
            val payload = result.optString("qr_payload").ifBlank { "CPX1.$passkey" }
            val expiresInMinutes = result.optInt("expires_in_minutes", 20).coerceAtLeast(1)
            _uiState.update {
                it.copy(
                    employeeRecoveryCredential = EmployeeRecoveryCredential(
                        employeeUserId = userId,
                        employeeName = employeeName,
                        passkey = passkey,
                        qrPayload = payload,
                        expiresInMinutes = expiresInMinutes
                    ),
                    actionError = null,
                    notice = "A one-time recovery code was created for $employeeName."
                )
            }
        } catch (error: Exception) {
            val msg = error.message.orEmpty()
            val friendly = if (msg.contains("function was not found", ignoreCase = true) || msg.contains("404", ignoreCase = true) || msg.contains("Requested function", ignoreCase = true)) {
                "Staff QR re-enrollment requires the backend edge function. You can also re-add this staff member by their email address in the Enroll Waiter tab."
            } else msg.ifBlank { "Could not create employee recovery code" }
            _uiState.update { it.copy(actionError = friendly) }
        } finally {
            _uiState.update { it.copy(isIssuingEmployeeRecoveryPass = false) }
        }
    }

    fun clearEmployeeRecoveryCredential() = _uiState.update { it.copy(employeeRecoveryCredential = null) }

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
            .onSuccess { rows -> _uiState.update { it.copy(employeeMembers = rows.toEmployeeMemberList(), notice = "Employee role updated") } }
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
        val startTime = start.trim()
        val endTime = end.trim()
        when {
            userId.isBlank() -> {
                _uiState.update { it.copy(actionError = "This waiter has no registered account ID.") }
                return@launch
            }
            weekday !in 0..6 -> {
                _uiState.update { it.copy(actionError = "Choose a valid weekday for the waiter schedule.") }
                return@launch
            }
            enabled && (!isValidHourMinute(startTime) || !isValidHourMinute(endTime) || startTime == endTime) -> {
                _uiState.update { it.copy(actionError = "Enabled duty hours require valid HH:mm start and end times that differ.") }
                return@launch
            }
        }
        runCatching { repository.setEmployeeHours(userId, weekday, startTime, endTime, enabled) }
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
        val invalidTime = enabledDays.firstOrNull {
            !isValidHourMinute(it.start) || !isValidHourMinute(it.end) || it.start.trim() == it.end.trim()
        }
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
                _uiState.update { it.copy(actionError = "Use distinct, valid HH:mm start and end times for every enabled day.") }
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
        if (_uiState.value.isLoadingAdminBusinesses) return@launch
        val cached = repository.getCachedAdminBusinesses()
        if (cached.isNotEmpty() && _adminBusinesses.value.isEmpty()) {
            _adminBusinesses.value = cached
        }
        _uiState.update { it.copy(isLoadingAdminBusinesses = true, adminBusinessesError = null) }
        runCatching { repository.refreshAdminBusinesses() }
            .onSuccess {
                _adminBusinesses.value = it
                _uiState.update {
                    it.copy(
                        isLoadingAdminBusinesses = false,
                        hasFreshAdminBusinesses = true,
                        adminBusinessesError = null
                    )
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingAdminBusinesses = false,
                        hasFreshAdminBusinesses = false,
                        adminBusinessesError = error.message ?: "Could not refresh businesses"
                    )
                }
            }
    }

    fun loadAdminPlatformBillingSettings() = viewModelScope.launch {
        _uiState.update { it.copy(isLoadingPlatformBillingSettings = true) }
        runCatching { repository.adminPlatformBillingSettings() }
            .onSuccess { settings ->
                _uiState.update { it.copy(platformBillingSettings = settings, isLoadingPlatformBillingSettings = false) }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingPlatformBillingSettings = false,
                        startupError = error.message ?: "Could not load global billing settings"
                    )
                }
            }
    }

    fun adminUpdatePlatformBillingSettings(trialDurationDays: Int, subscriptionPriceKsh: Int) = viewModelScope.launch {
        if (_uiState.value.isSavingPlatformBillingSettings) return@launch
        _uiState.update { it.copy(isSavingPlatformBillingSettings = true, startupError = null) }
        runCatching {
            repository.adminUpdatePlatformBillingSettings(trialDurationDays, subscriptionPriceKsh)
        }.onSuccess { settings ->
            _adminBusinesses.value = repository.getCachedAdminBusinesses()
            _uiState.update {
                it.copy(
                    platformBillingSettings = settings,
                    isSavingPlatformBillingSettings = false,
                    notice = "Global billing settings saved. Current trials and paid terms remain unchanged."
                )
            }
        }.onFailure { error ->
            _uiState.update {
                it.copy(
                    isSavingPlatformBillingSettings = false,
                    startupError = error.message ?: "Could not update global billing settings"
                )
            }
        }
    }

    fun loadAdminAuditEvents(shopId: String = "") = viewModelScope.launch {
        val cached = repository.getCachedAdminAuditEvents()
        if (cached.isNotEmpty() && _uiState.value.adminAuditEvents.isEmpty()) {
            _uiState.update { it.copy(adminAuditEvents = cached) }
        }
        _uiState.update { it.copy(isLoadingAuditEvents = true) }
        runCatching { repository.adminAuditEvents(shopId) }
            .onSuccess { events ->
                _uiState.update { it.copy(adminAuditEvents = events, isLoadingAuditEvents = false) }
            }
            .onFailure {
                _uiState.update { it.copy(isLoadingAuditEvents = false) }
            }
    }

    fun setBusinessStatus(shopId: String, status: String, reason: String?) = viewModelScope.launch {
        runCatching { repository.adminSetBusinessStatus(shopId, status, reason) }
            .onSuccess { _adminBusinesses.value = it }
            .onFailure { error -> _uiState.update { it.copy(startupError = error.message ?: "Could not change business status") } }
    }

    fun createBusinessForOwner(
        name: String,
        ownerEmail: String,
        ownerPhone: String,
        businessType: String = "big_club",
        businessSize: String = "medium",
        inventoryMode: String = "shared",
        counterCount: Int = 1,
        onCreated: (() -> Unit)? = null
    ) = viewModelScope.launch {
        if (_uiState.value.isCreatingAdminBusiness) return@launch
        val normalizedOwnerPhone = SubscriptionManager.normalizeKenyanPhone(ownerPhone)
        if (normalizedOwnerPhone == null) {
            _uiState.update { it.copy(startupError = "Enter a valid Kenyan owner mobile number.") }
            return@launch
        }
        _uiState.update { it.copy(isCreatingAdminBusiness = true, startupError = null) }
        runCatching { repository.adminCreateBusiness(name, ownerEmail, businessType, businessSize, inventoryMode, counterCount, normalizedOwnerPhone) }
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
        if (!session.value.isPlatformAdmin || role !in setOf("owner", "manager", "inventory_clerk", "cashier")) {
            _uiState.update { it.copy(startupError = "Only a platform administrator can issue a passkey for a valid business role.") }
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

    private var subscriptionPollingJob: Job? = null

    fun dismissSubscriptionModal() {
        subscriptionPollingJob?.cancel()
        _uiState.update {
            it.copy(
                isSubscriptionModalOpen = false,
                isInitiatingStkPush = false,
                isPollingStkPayment = false,
                subscriptionPaymentError = null,
                isSubscriptionPaymentSuccess = false
            )
        }
    }

    fun openSubscriptionModal() {
        _uiState.update {
            it.copy(
                isSubscriptionModalOpen = true,
                subscriptionPaymentError = null,
                isSubscriptionPaymentSuccess = false
            )
        }
    }

    fun initiateSubscriptionPayment(phoneNumber: String) {
        val cleanPhone = com.alvinb.counterpos.data.SubscriptionManager.normalizeKenyanPhone(phoneNumber)
        if (cleanPhone == null) {
            _uiState.update { it.copy(subscriptionPaymentError = "Please enter a valid Kenyan phone number (e.g. 0712 345 678 or 254712345678).") }
            return
        }

        subscriptionPollingJob?.cancel()
        _uiState.update {
            it.copy(
                isInitiatingStkPush = true,
                isPollingStkPayment = false,
                subscriptionPaymentError = null,
                isSubscriptionPaymentSuccess = false,
                stkCheckoutRequestId = null,
                stkCustomerMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val response = repository.initiateSubscriptionStkPush(
                    phoneNumber = cleanPhone
                )
                if (response.success && response.checkoutRequestId != null) {
                    _uiState.update {
                        it.copy(
                            isInitiatingStkPush = false,
                            isPollingStkPayment = true,
                            stkCheckoutRequestId = response.checkoutRequestId,
                            stkCustomerMessage = response.customerMessage ?: "M-Pesa STK push sent. Enter your M-Pesa PIN on your phone."
                        )
                    }
                    startPollingSubscriptionStatus(response.checkoutRequestId)
                } else {
                    recoverOrShowSubscriptionPaymentFailure(
                        response.error ?: "Failed to initiate M-Pesa payment. Please check its status before retrying."
                    )
                }
            } catch (error: Exception) {
                recoverOrShowSubscriptionPaymentFailure(
                    error.message ?: "Could not confirm whether M-Pesa sent the prompt. Check payment status before retrying."
                )
            }
        }
    }

    private suspend fun recoverOrShowSubscriptionPaymentFailure(message: String) {
        val recovered = runCatching { repository.verifySubscriptionPaymentStatus() }.getOrNull()
        if (recovered != null && (recovered.paymentStatus == "initiating" || recovered.paymentStatus == "pending")) {
            _uiState.update {
                it.copy(
                    isInitiatingStkPush = false,
                    isPollingStkPayment = true,
                    subscriptionPaymentError = null,
                    stkCheckoutRequestId = recovered.checkoutRequestId,
                    stkCustomerMessage = recovered.message ?: message
                )
            }
            startPollingSubscriptionStatus(recovered.checkoutRequestId)
            return
        }
        _uiState.update {
            it.copy(
                isInitiatingStkPush = false,
                isPollingStkPayment = false,
                subscriptionPaymentError = message
            )
        }
    }

    private fun startPollingSubscriptionStatus(checkoutRequestId: String?) {
        subscriptionPollingJob?.cancel()
        subscriptionPollingJob = viewModelScope.launch {
            var attempts = 0
            val maxAttempts = 60
            while (isActive && attempts < maxAttempts) {
                delay(4000L)
                attempts++
                try {
                    if (attempts % 4 == 0) {
                        val payment = repository.verifySubscriptionPaymentStatus(checkoutRequestId)
                        if (payment.checkoutRequestId != null && payment.checkoutRequestId != _uiState.value.stkCheckoutRequestId) {
                            _uiState.update { it.copy(stkCheckoutRequestId = payment.checkoutRequestId) }
                        }
                        if (payment.paymentStatus == "failed") {
                            _uiState.update {
                                it.copy(
                                    isPollingStkPayment = false,
                                    subscriptionPaymentError = payment.message
                                        ?: "M-Pesa did not complete the payment. You can start a new attempt."
                                )
                            }
                            return@launch
                        }
                    }
                    val statusResult = repository.checkSubscriptionStatus()
                    val expectedCheckoutId = checkoutRequestId ?: statusResult.latestCheckoutRequestId
                    if (isConfirmedSubscriptionPayment(statusResult, expectedCheckoutId)) {
                        repository.refreshActiveBusinessSubscription()
                        _uiState.update {
                            it.copy(
                                isPollingStkPayment = false,
                                isSubscriptionPaymentSuccess = true,
                                subscriptionPaymentError = null,
                                notice = "Subscription activated successfully! Thank you for renewing."
                            )
                        }
                        return@launch
                    }
                } catch (_: Exception) {
                }
            }
            if (isActive) {
                _uiState.update {
                    it.copy(
                        isPollingStkPayment = false,
                        subscriptionPaymentError = "Payment is still unconfirmed. Tap Check Now to ask Safaricom again; do not pay again if you already entered your PIN."
                    )
                }
            }
        }
    }

    fun verifySubscriptionPayment() {
        viewModelScope.launch {
            _uiState.update { it.copy(isPollingStkPayment = true, subscriptionPaymentError = null) }
            try {
                val payment = repository.verifySubscriptionPaymentStatus(_uiState.value.stkCheckoutRequestId)
                repository.refreshActiveBusinessSubscription()
                val statusResult = repository.checkSubscriptionStatus()
                val expectedCheckoutId = payment.checkoutRequestId ?: _uiState.value.stkCheckoutRequestId
                if (isConfirmedSubscriptionPayment(statusResult, expectedCheckoutId)) {
                    _uiState.update {
                        it.copy(
                            isPollingStkPayment = false,
                            isSubscriptionPaymentSuccess = true,
                            subscriptionPaymentError = null,
                            notice = "Subscription is active! All features restored."
                        )
                    }
                } else if (payment.paymentStatus == "initiating" || payment.paymentStatus == "pending") {
                    _uiState.update {
                        it.copy(
                            stkCheckoutRequestId = payment.checkoutRequestId ?: it.stkCheckoutRequestId,
                            stkCustomerMessage = payment.message ?: it.stkCustomerMessage,
                            isPollingStkPayment = true,
                            subscriptionPaymentError = null
                        )
                    }
                    startPollingSubscriptionStatus(payment.checkoutRequestId ?: _uiState.value.stkCheckoutRequestId)
                } else {
                    _uiState.update {
                        it.copy(
                            isPollingStkPayment = false,
                            subscriptionPaymentError = payment.message
                                ?: "No completed M-Pesa payment was found. Check your phone or start a new payment attempt."
                        )
                    }
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isPollingStkPayment = false,
                        subscriptionPaymentError = error.message ?: "Could not verify payment status."
                    )
                }
            }
        }
    }

    private fun isConfirmedSubscriptionPayment(
        status: com.alvinb.counterpos.data.remote.SubscriptionStatusResult,
        checkoutRequestId: String?
    ): Boolean = !checkoutRequestId.isNullOrBlank() &&
        status.status.equals("active", ignoreCase = true) &&
        status.latestPaymentStatus.equals("paid", ignoreCase = true) &&
        status.latestCheckoutRequestId == checkoutRequestId

    // Superadmin Subscription Management
    fun adminOpenSubscriptionDialog(business: AdminBusiness) {
        _uiState.update { it.copy(adminSelectedSubscriptionBusiness = business) }
    }

    fun adminCloseSubscriptionDialog() {
        _uiState.update { it.copy(adminSelectedSubscriptionBusiness = null, isUpdatingSubscription = false) }
    }

    fun adminUpdateShopSubscription(
        shopId: String,
        priceKsh: Int? = null,
        endsAtIso: String? = null,
        status: String? = null,
        planDays: Int? = null,
        trialEndsAtIso: String? = null,
        note: String? = null
    ) = viewModelScope.launch {
        _uiState.update { it.copy(isUpdatingSubscription = true, startupError = null) }
        try {
            val updated = repository.adminUpdateShopSubscription(
                shopId = shopId,
                priceKsh = priceKsh,
                endsAtIso = endsAtIso,
                status = status,
                planDays = planDays,
                trialEndsAtIso = trialEndsAtIso,
                note = note
            )
            _adminBusinesses.value = updated
            _uiState.update {
                it.copy(
                    isUpdatingSubscription = false,
                    adminSelectedSubscriptionBusiness = updated.firstOrNull { b -> b.shopId == shopId },
                    notice = "Store subscription updated successfully"
                )
            }
        } catch (error: Exception) {
            _uiState.update {
                it.copy(
                    isUpdatingSubscription = false,
                    startupError = error.message ?: "Could not update subscription"
                )
            }
        }
    }

    fun adminExtendTrial(shopId: String, daysToAdd: Int = 14) {
        val target = _adminBusinesses.value.firstOrNull { it.shopId == shopId }
        val baseTime = target?.trialEndsAt?.let { com.alvinb.counterpos.data.SubscriptionManager.parseTimestamp(it) } ?: System.currentTimeMillis()
        val newTime = maxOf(baseTime, System.currentTimeMillis()) + (daysToAdd * 86400000L)
        val iso = java.time.Instant.ofEpochMilli(newTime).toString()
        adminUpdateShopSubscription(shopId = shopId, trialEndsAtIso = iso, status = "trial")
    }

    fun adminActivatePaidTerm(shopId: String, daysToAdd: Int = 30, priceKsh: Int? = null) {
        val target = _adminBusinesses.value.firstOrNull { it.shopId == shopId }
        val baseTime = target?.subscriptionEndsAt?.let { com.alvinb.counterpos.data.SubscriptionManager.parseTimestamp(it) } ?: System.currentTimeMillis()
        val newTime = maxOf(baseTime, System.currentTimeMillis()) + (daysToAdd * 86400000L)
        val iso = java.time.Instant.ofEpochMilli(newTime).toString()
        adminUpdateShopSubscription(shopId = shopId, endsAtIso = iso, status = "active", planDays = daysToAdd, priceKsh = priceKsh)
    }

    fun adminSetPaymentLock(shopId: String, locked: Boolean, amountKsh: Int? = null, reason: String? = null) {
        if (_uiState.value.isUpdatingSubscription) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingSubscription = true, startupError = null) }
            runCatching { repository.adminSetShopPaymentLock(shopId, locked, amountKsh, reason) }
                .onSuccess { updated ->
                    _adminBusinesses.value = updated
                    _uiState.update {
                        it.copy(
                            isUpdatingSubscription = false,
                            adminSelectedSubscriptionBusiness = updated.firstOrNull { b -> b.shopId == shopId },
                            notice = if (locked) "Payment lock started for the business" else "Payment lock cleared"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isUpdatingSubscription = false, startupError = error.message ?: "Could not update payment lock") }
                }
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
        if (role !in setOf("owner", "manager", "inventory_clerk", "cashier")) {
            _uiState.update { it.copy(startupError = "Choose a valid role before enrolling this phone.") }
            return
        }
        _uiState.update {
            it.copy(
                adminSelectedBusinessId = shopId,
                adminQrRole = role,
                isScannerOpen = true,
                scannerMode = ScannerMode.ADMIN_QR,
                startupError = null,
                notice = "Scan the pending sign-in QR to grant ${role.replace('_', ' ')} access to this business."
            )
        }
    }

    fun handleAdminQrCode(payload: String) {
        val qr = payload.trim()
        val currentSession = session.value
        val currentState = _uiState.value
        val isPlatformAdmin = currentSession.isPlatformAdmin
        val autoSelectedShopId = if (isPlatformAdmin && currentState.adminSelectedBusinessId.isNullOrBlank() && _adminBusinesses.value.size == 1) {
            _adminBusinesses.value.first().shopId
        } else currentState.adminSelectedBusinessId
        val shopId = if (isPlatformAdmin) autoSelectedShopId else currentSession.activeBusiness?.shopId
        val business = _adminBusinesses.value.firstOrNull { it.shopId == shopId }
        val businessRole = currentSession.activeBusiness?.role
        val role = if (isPlatformAdmin) currentState.adminQrRole else "cashier"

        if (qr.startsWith("CPR1|", ignoreCase = true)) {
            if (shopId.isNullOrBlank()) {
                _uiState.update { it.copy(isScannerOpen = false, startupError = "Select a business before scanning the phone's sign-in QR.") }
            } else if (isPlatformAdmin && (business == null || business.status != "active")) {
                _uiState.update { it.copy(isScannerOpen = false, startupError = "The selected business is not active. Reopen it before enrolling a phone.") }
            } else if (!isPlatformAdmin && businessRole != "owner") {
                _uiState.update { it.copy(isScannerOpen = false, actionError = "Only the business owner can approve a Waiter sign-in QR.") }
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
                                    isStaffManagementOpen = if (!isPlatformAdmin) true else it.isStaffManagementOpen,
                                    startupError = null,
                                    actionError = null,
                                    notice = if (isPlatformAdmin) {
                                        "Approved and enrolled the other phone for $businessName as ${role.replace('_', ' ')}. You remain in Superadmin."
                                    } else {
                                        "Approved and enrolled for $businessName as ${role.replace('_', ' ')}."
                                    }
                                )
                            }
                            if (isPlatformAdmin) {
                                loadAdminBusinesses()
                                loadAdminAuditEvents()
                            } else {
                                refreshAccessNow()
                            }
                        }
                        .onFailure { error ->
                            val message = error.message.orEmpty()
                            val friendly = if (message.contains("expired", ignoreCase = true) || message.contains("session", ignoreCase = true) && message.contains("used", ignoreCase = true)) {
                                "This QR session has expired. Ask the other phone to tap Refresh QR, then scan the new code."
                            } else message.ifBlank { "Could not approve sign-in QR" }
                            _uiState.update {
                                it.copy(
                                    isScannerOpen = false,
                                    isStaffManagementOpen = if (!isPlatformAdmin) true else it.isStaffManagementOpen,
                                    startupError = friendly
                                )
                            }
                        }
                }
            }
            return
        }

        // Passkey or access pass QR code scan
        if (qr.startsWith("CPX1.", ignoreCase = true) || (qr.length in 8..64 && !qr.contains('|'))) {
            if (isPlatformAdmin && !shopId.isNullOrBlank()) {
                _uiState.update { it.copy(isScannerOpen = false, startupError = null, notice = "Passkey verified for business. The owner can sign in on their phone.") }
                loadAdminBusinesses()
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
                        _uiState.update {
                            it.copy(
                                isScannerOpen = false,
                                startupError = "This older business QR does not include the owner's mobile number. Use Register Tenant and enter the owner's phone."
                            )
                        }
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
                                        loadAdminBusinesses()
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
            _uiState.update { it.copy(isScannerOpen = false, startupError = null, notice = "Owner enrolled successfully. You remain in Superadmin.") }
            loadAdminBusinesses()
            return
        }

        _uiState.update { it.copy(isScannerOpen = false, startupError = "Unrecognized CounterPOS QR. Use a business or user enrollment QR.") }
    }

    fun selectTab(tab: PosTab) = _uiState.update {
        if (it.tab == tab) it
        else it.copy(
            tab = tab,
            previousTab = it.tab,
            actionError = null,
            isSearchActive = false,
            searchQuery = "",
            isRefreshingBusinessData = it.isRefreshingBusinessData || tab == PosTab.TABLES
        )
    }

    fun goBackToPreviousTab() = _uiState.update {
        val previous = it.previousTab ?: return@update it
        it.copy(tab = previous, previousTab = null, actionError = null, isSearchActive = false, searchQuery = "")
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
        runCatching { repository.synchronizeWhenOnline() }
        runCatching { repository.counterSalesReport(fromIso, toIso) }
            .onSuccess { rows -> _uiState.update { it.copy(counterReport = rows) } }
            .onFailure { error -> _uiState.update { it.copy(actionError = error.message ?: "Could not load counter report") } }
        _uiState.update { it.copy(isLoadingCounterReport = false) }
    }

    fun refreshBusinessData() = viewModelScope.launch {
        _uiState.update { it.copy(isRefreshingBusinessData = true) }
        try {
            repository.synchronizeWhenOnline()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _uiState.update { it.copy(actionError = error.message ?: "Could not refresh business data") }
        } finally {
            _uiState.update { it.copy(isRefreshingBusinessData = false) }
        }
    }

    private fun syncInventoryQuietly(counterId: String?) {
        viewModelScope.launch {
            // Room is updated optimistically by the repository. Retry the
            // server flush immediately so other devices see stock changes
            // without waiting for the background worker interval.
            runCatching { repository.synchronizeWhenOnline() }
            refreshCounterStock(counterId)
        }
    }

    fun synchronizeData(showNotice: Boolean = false) = viewModelScope.launch {
        _uiState.update { it.copy(isSyncingData = true, actionError = null) }
        runCatching { repository.synchronizeWhenOnline() }
            .onSuccess {
                _uiState.update { it.copy(isSyncingData = false, notice = if (showNotice) "Data synced" else it.notice) }
            }
            .onFailure { error ->
                _uiState.update { it.copy(isSyncingData = false, actionError = error.message ?: "Could not refresh business data") }
            }
    }

    fun openStaffEnrollmentScanner(role: String) {
        val business = session.value.activeBusiness
        if (business == null) {
            _uiState.update { it.copy(actionError = "Select an active business before scanning an employee phone.") }
            return
        }
        if (!business.role.equals("owner", ignoreCase = true)) {
            _uiState.update { it.copy(actionError = "Only the business owner can approve employee phone enrollment.") }
            return
        }
        if (!role.equals("cashier", ignoreCase = true) && !role.equals("waiter", ignoreCase = true)) {
            _uiState.update { it.copy(actionError = "Only Waiter enrollment is supported in this screen.") }
            return
        }
        _uiState.update {
            it.copy(
                isScannerOpen = true,
                scannerMode = ScannerMode.ADMIN_QR,
                isAccessLocked = false,
                isStaffManagementOpen = false,
                actionError = null,
                notice = "Scan the employee's sign-in QR on their phone to enroll them in ${business.shopName}."
            )
        }
    }

    fun openTable(table: DiningTableEntity) = _uiState.update {
        it.copy(selectedTableId = table.id, actionError = null)
    }

    fun backToTables() = _uiState.update { it.copy(selectedTableId = null, actionError = null) }

    fun selectNextTable(direction: Int) {
        val currentList = diningTables.value
        if (currentList.isEmpty()) return
        val currentId = _uiState.value.selectedTableId ?: return
        val currentIndex = currentList.indexOfFirst { it.id == currentId }
        if (currentIndex == -1) return
        val nextIndex = (currentIndex + direction + currentList.size) % currentList.size
        _uiState.update { it.copy(selectedTableId = currentList[nextIndex].id, actionError = null) }
    }

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

    fun deleteDiningTable(tableId: String) {
        if (_uiState.value.isDeletingTable) return
        _uiState.update { it.copy(isDeletingTable = true, actionError = null) }
        viewModelScope.launch {
            runCatching { repository.deleteDiningTable(tableId) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isDeletingTable = false,
                            selectedTableId = null,
                            notice = "Table deleted",
                            actionError = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isDeletingTable = false, actionError = error.message ?: "Could not delete table.") }
                }
        }
    }

    fun settleTableBill(
        tableId: String,
        paymentMethod: String,
        onComplete: (TableSettlementResult?) -> Unit = {}
    ) {
        if (_uiState.value.isSettlingTable) return
        _uiState.update { it.copy(isSettlingTable = true, actionError = null) }
        viewModelScope.launch {
            runCatching { repository.settleTableBill(tableId, paymentMethod) }
                .onSuccess { settlement ->
                    val methodLabel = when (paymentMethod.lowercase()) {
                        "mpesa" -> "M-Pesa"
                        "card" -> "Card"
                        "other" -> "Other"
                        else -> "Cash"
                    }
                    _uiState.update {
                        it.copy(
                            isSettlingTable = false,
                            actionError = null,
                            notice = if (settlement.synced) {
                                "Bill paid and closed · $methodLabel · KSh ${"%,d".format(settlement.amountKsh)}. The table is ready for a new bill."
                            } else {
                                "Settlement saved to the offline outbox · $methodLabel · KSh ${"%,d".format(settlement.amountKsh)} preview. It will close after the server confirms sync."
                            }
                        )
                    }
                    onComplete(settlement)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isSettlingTable = false, actionError = error.message ?: "Could not close this table bill.")
                    }
                    onComplete(null)
                }
        }
    }

    fun openTableSale(tableId: String, product: ProductEntity) {
        val units = resolveSaleUnits(product)
        val bulkOnly = units.isNotEmpty() && units.all { it.stockUnitsPerSale == 0 }
        if (!product.inMyStock || (product.stockQuantity <= 0 && !bulkOnly)) {
            _uiState.update { it.copy(actionError = "${product.name} is out of stock.") }
            return
        }
        val defaultUnit = units.firstOrNull() ?: SaleUnit("Bottle", product.suggestedPriceKsh.coerceAtLeast(0))
        val defaultCounterId = _uiState.value.selectedServingCounterId
            ?: _uiState.value.selectedCounterId
            ?: serviceCounters.value.firstOrNull()?.id
        _uiState.update {
            it.copy(
                selectedTableId = tableId,
                selectedSaleCode = product.catalogCode,
                selectedUnitName = defaultUnit.name,
                selectedUnitPrice = defaultUnit.priceKsh,
                selectedServingCounterId = defaultCounterId,
                saleQuantity = 1,
                selectedPaymentMethod = "cash",
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

    fun openSyncRecovery() {
        _uiState.update { it.copy(isSyncRecoveryOpen = true, syncRecoveryError = null) }
        viewModelScope.launch {
            runCatching { repository.blockedOutboxItems() }
                .onSuccess { items -> _uiState.update { it.copy(blockedOutbox = items) } }
        }
    }

    fun closeSyncRecovery() = _uiState.update { it.copy(isSyncRecoveryOpen = false, syncRecoveryError = null) }

    fun retryBlockedOutbox(operationId: String) {
        if (_uiState.value.retryingOperationId != null) return
        _uiState.update { it.copy(retryingOperationId = operationId, syncRecoveryError = null) }
        viewModelScope.launch {
            runCatching { repository.retryBlockedOutbox(operationId) }
                .onSuccess {
                    runCatching { repository.blockedOutboxItems() }
                        .onSuccess { items -> _uiState.update { it.copy(blockedOutbox = items) } }
                    _uiState.update { it.copy(retryingOperationId = null, notice = "Saved POS change retried") }
                }
                .onFailure { error ->
                    runCatching { repository.blockedOutboxItems() }
                        .onSuccess { items -> _uiState.update { it.copy(blockedOutbox = items) } }
                    _uiState.update {
                        it.copy(retryingOperationId = null, syncRecoveryError = error.message ?: "Could not retry this saved change.")
                    }
                }
        }
    }

    // Scanner
    fun openScanner(mode: ScannerMode) = _uiState.update {
        val owner = session.value.activeBusiness?.role == "owner"
        it.copy(isScannerOpen = true, scannerMode = mode, actionError = null, isAccessLocked = if (owner) false else it.isAccessLocked)
    }

    fun closeScanner() = _uiState.update {
        val wasStaffScan = it.scannerMode == ScannerMode.ADMIN_QR && !session.value.isPlatformAdmin
        it.copy(
            isScannerOpen = false,
            unrecognizedBarcode = null,
            isStaffManagementOpen = if (wasStaffScan) true else it.isStaffManagementOpen
        )
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
        val units = resolveSaleUnits(product)
        val bulkOnly = units.isNotEmpty() && units.all { it.stockUnitsPerSale == 0 }
        if (!product.inMyStock || (product.stockQuantity < 1 && !bulkOnly)) {
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
        val units = resolveSaleUnits(product)
        val bulkOnly = units.isNotEmpty() && units.all { it.stockUnitsPerSale == 0 }
        if (!product.inMyStock || (product.stockQuantity < 1 && !bulkOnly)) {
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
        val role = session.value.activeBusiness?.role.orEmpty()
        val canManageStock = role in setOf("owner", "manager", "inventory_clerk")
        if (!canManageStock) {
            _uiState.update { it.copy(isScannerOpen = false, actionError = null) }
            openSale(product)
            return
        }
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
        val clean = barcode.trim()
        val cprIdx = clean.indexOf("CPR1|", ignoreCase = true)
        val cpxIdx = clean.indexOf("CPX1.", ignoreCase = true)
        val cptIdx = clean.indexOf("CPT1|", ignoreCase = true)
        if (cprIdx >= 0 || cpxIdx >= 0 || cptIdx >= 0) {
            val token = when {
                cprIdx >= 0 -> clean.substring(cprIdx)
                cpxIdx >= 0 -> clean.substring(cpxIdx)
                else -> clean.substring(cptIdx)
            }
            handleAdminQrCode(token)
            return
        }
        _uiState.update {
            it.copy(
                isScannerOpen = false,
                notice = "Product is not available on the stock screen ($barcode)"
            )
        }
    }

    // Selling & Unit of Sales
    fun openSale(product: ProductEntity) {
        val units = resolveSaleUnits(product)
        val defaultUnit = units.firstOrNull() ?: SaleUnit("Bottle", product.suggestedPriceKsh.coerceAtLeast(0))
        _uiState.update {
            it.copy(
                selectedSaleCode = product.catalogCode,
                selectedUnitName = defaultUnit.name,
                selectedUnitPrice = defaultUnit.priceKsh,
                saleQuantity = 1,
                selectedPaymentMethod = "cash",
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
    fun selectPaymentMethod(method: String) = _uiState.update {
        it.copy(selectedPaymentMethod = method.lowercase())
    }

    fun setReportFilter(filter: String) = _uiState.update {
        it.copy(reportFilter = filter)
    }

    fun setReportPaymentFilter(filter: String) = _uiState.update {
        it.copy(reportPaymentFilter = filter)
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
        if (quantity < 1) {
            _uiState.update { it.copy(actionError = "Select a valid quantity.") }
            return@launch
        }
        try {
            repository.recordDispensation(
                code = productCode,
                quantity = quantity,
                unitName = unitName,
                counterId = counterId,
                waiterName = waiterName,
                tableId = tableId,
                tableName = tableName
            )
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
        if (state.selectedPaymentMethod !in setOf("cash", "mpesa")) {
            _uiState.update { it.copy(actionError = "Choose Cash or M-Pesa before completing the sale.") }
            return
        }
        val counters = serviceCounters.value
        val requestedCounterId = state.selectedServingCounterId ?: state.selectedCounterId ?: counters.firstOrNull()?.id
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
                        waiterName = waiterName,
                        paymentMethod = state.selectedPaymentMethod
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
                        paymentMethod = state.selectedPaymentMethod,
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
                                null
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
                        notice = null,
                        actionError = null
                    )
                }
                syncInventoryQuietly(counterId)
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
        barcode: String? = null,
        photoUri: String? = null
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
                verifiedBarcode = barcode?.trim()?.ifBlank { null } ?: product.verifiedBarcode,
                photoOverrideUri = photoUri?.takeIf(String::isNotBlank) ?: product.photoOverrideUri
            )
            val counterId = _uiState.value.selectedCounterId
            if (counterId.isNullOrBlank()) { _uiState.update { it.copy(actionError = "Choose a counter first.") }; return@launch }
            val success = repository.updateProduct(updated, counterId)
            if (success) {
                _uiState.update {
                    it.copy(
                        editProductCode = null,
                        notice = null,
                        actionError = null
                    )
                }
                syncInventoryQuietly(counterId)
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
        size: String = "",
        photoUri: String? = null
    ) {
        if (name.isBlank() || basePrice < 1) {
            _uiState.update { it.copy(actionError = "Enter product name and valid base price.") }
            return
        }
        viewModelScope.launch {
            val counterId = _uiState.value.selectedCounterId
            if (counterId.isNullOrBlank()) { _uiState.update { it.copy(actionError = "Choose a receiving counter first.") }; return@launch }
            val code = barcode.trim().ifBlank { "PROD_" + UUID.randomUUID().toString().take(8) }
            try {
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
                    selectedCounterId = counterId,
                    photoOverrideUri = photoUri
                )
                if (success) {
                    _uiState.update {
                        it.copy(
                            isAddingNewProduct = false,
                            prefilledBarcode = null,
                            actionError = null,
                            notice = null
                        )
                    }
                    syncInventoryQuietly(counterId)
                } else {
                    _uiState.update { it.copy(actionError = "Failed to add product.") }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(actionError = error.message ?: "Failed to add product.") }
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
                        _uiState.update {
                            it.copy(
                                selectedCatalogCode = null,
                                stockMode = StockMode.MY_STOCK,
                                actionError = null,
                                notice = null
                            )
                        }
                        syncInventoryQuietly(counterId)
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
            try {
                val updated = repository.updatePhoto(product, uri)
                _uiState.update {
                    if (updated) it.copy(notice = "Photo updated for ${product.name}", actionError = null)
                    else it.copy(actionError = "Could not save the selected photo. Try choosing it again.")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(actionError = error.message ?: "Could not update the product photo.") }
            }
        }
    }
}

class CounterPosViewModelFactory(
    private val repository: CounterPosRepository,
    private val connectivityManager: ConnectivityManager,
    private val subscriptionManager: SubscriptionManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(CounterPosViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        return CounterPosViewModel(repository, connectivityManager, subscriptionManager) as T
    }
}
