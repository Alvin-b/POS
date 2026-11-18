package com.alvinb.counterpos.data

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import com.alvinb.counterpos.OfflineOutboxWorker
import com.alvinb.counterpos.BuildConfig
import com.alvinb.counterpos.data.local.dao.CounterPosDao
import com.alvinb.counterpos.data.local.entity.DiningTableEntity
import com.alvinb.counterpos.data.local.entity.OfflineSessionEntity
import com.alvinb.counterpos.data.local.entity.OfflineTransactionLogEntity
import com.alvinb.counterpos.data.local.entity.OfflineTransactionStatus
import com.alvinb.counterpos.data.local.entity.OutboxEntity
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.local.entity.SaleUnit
import com.alvinb.counterpos.data.local.entity.TableOrderEntity
import com.alvinb.counterpos.data.local.entity.parseSaleUnits
import com.alvinb.counterpos.data.local.entity.serializeSaleUnits
import com.alvinb.counterpos.data.model.BusinessHours
import com.alvinb.counterpos.data.model.TimeSlot
import com.alvinb.counterpos.data.remote.AccountSnapshot
import com.alvinb.counterpos.data.remote.AdminAuditEvent
import com.alvinb.counterpos.data.remote.AdminBusiness
import com.alvinb.counterpos.data.remote.PlatformBillingSettings
import com.alvinb.counterpos.data.remote.AuthSession
import com.alvinb.counterpos.data.remote.BusinessMembership
import com.alvinb.counterpos.data.remote.CounterReportRow
import com.alvinb.counterpos.data.remote.ServiceCounter
import com.alvinb.counterpos.data.remote.SupabaseHttpException
import com.alvinb.counterpos.data.remote.SupabaseRestClient
import com.alvinb.counterpos.data.remote.SupabaseRealtimeClient
import com.alvinb.counterpos.data.remote.SupabaseUnauthorizedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

data class SessionSnapshot(
    val isAuthenticated: Boolean = false,
    val userId: String = "",
    val email: String = "",
    val isPlatformAdmin: Boolean = false,
    val memberships: List<BusinessMembership> = emptyList(),
    val activeBusiness: BusinessMembership? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

data class TableSettlementResult(val amountKsh: Long, val synced: Boolean)

data class AdminBusinessCreationResult(
    val createdShopId: String?,
    val businesses: List<AdminBusiness>
)

private data class BundledProductMetadata(
    val imageAssetName: String,
    val verifiedBarcode: String?,
    val productSourceUrl: String?,
    val imageSourceUrl: String?
)

class CounterPosRepository(
    private val context: Context,
    private val dao: CounterPosDao,
    private val assets: AssetManager,
    private val remote: SupabaseRestClient
) {
    data class AccessChallenge(val token: String, val expiresAtMillis: Long)
    data class ApprovedAccess(val session: SessionSnapshot, val businessId: String?)

    val products: Flow<List<ProductEntity>> = dao.observeProducts()
    val lowStockProducts: Flow<List<ProductEntity>> = dao.observeLowStockProducts()
    val sales: Flow<List<SaleEntity>> = dao.observeSales()
    val diningTables: Flow<List<DiningTableEntity>> = dao.observeDiningTables()
    val tableOrders: Flow<List<TableOrderEntity>> = dao.observeTableOrders()
    private val _session = MutableStateFlow(SessionSnapshot())
    val session = _session.asStateFlow()
    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingSyncCount: Flow<Int> = _session
        .map { it.activeBusiness?.shopId }
        .distinctUntilChanged()
        .flatMapLatest { shopId ->
            if (shopId.isNullOrBlank()) dao.observePendingOutboxCount()
            else dao.observePendingOutboxCountForShop(shopId)
        }
    fun observeBlockedOutbox(shopId: String): Flow<List<OutboxEntity>> =
        dao.observeBlockedOutbox(shopId)
    fun observeTransactionLogs(shopId: String): Flow<List<OfflineTransactionLogEntity>> =
        dao.observeTransactionLogs(shopId)
    fun observePendingTransactionLogCount(shopId: String): Flow<Int> =
        dao.observePendingTransactionLogCount(shopId)
    fun observeTableSettlementOutbox(shopId: String): Flow<List<OutboxEntity>> =
        dao.observeTableSettlementOutbox(shopId)
    fun observeCounterStock(shopId: String, counterId: String): Flow<Map<String, Int>> =
        dao.observeCounterStock(shopId, counterId).map { rows ->
            buildMap {
                rows.forEach { row ->
                    if (row.productId.isNotBlank()) put(row.productId, row.quantity)
                    put(row.catalogCode, row.quantity)
                }
            }
        }
    private val _serviceCounters = MutableStateFlow<List<ServiceCounter>>(emptyList())
    val serviceCounters = _serviceCounters.asStateFlow()
    private val _isOutboxSyncing = MutableStateFlow(false)
    val isOutboxSyncing = _isOutboxSyncing.asStateFlow()

    private var account: AccountSnapshot? = null
    private var activeBusiness: BusinessMembership? = null
    private val syncMutex = Mutex()
    private val outboxClock = AtomicLong(0L)
    private val lastSyncCompletedAt = AtomicLong(0L)
    private val lastOutboxPushAt = AtomicLong(0L)
    private val lastSessionKeepAliveAt = AtomicLong(0L)
    private val inFlightOperationId = AtomicReference<String?>(null)
    private val inFlightCompletion = AtomicReference<CompletableDeferred<Unit>?>(null)
    @Volatile private var startupSyncJob: Job? = null
    @Volatile private var cachedCatalogRows: JSONArray? = null
    @Volatile private var cachedBundledMetadata: Pair<Map<String, BundledProductMetadata>, Map<String, BundledProductMetadata>>? = null
    private val realtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var realtimeRefreshJob: Job? = null
    private var realtimeShopRefreshJob: Job? = null
    private val realtime = SupabaseRealtimeClient(
        baseUrl = BuildConfig.SUPABASE_URL,
        publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        accessToken = remote::currentAccessToken,
        onDataChanged = ::scheduleRealtimeRefresh,
        onShopChanged = ::scheduleShopSubscriptionRefresh
    )

    init {
        remote.onAccessTokenUpdated = { token ->
            realtime.updateAccessToken(token)
        }
    }

    private fun startRealtimeFor(membership: BusinessMembership) {
        realtime.start(membership.shopId)
    }

    fun reconnectRealtimeIfNeeded() {
        if (activeBusiness != null) realtime.reconnectIfNeeded()
    }

    suspend fun hasPendingOutboxAll(): Boolean = withContext(Dispatchers.IO) {
        dao.outboxCountAll() > 0
    }

    private fun scheduleShopSubscriptionRefresh() {
        val activeShop = activeBusiness ?: return
        realtimeShopRefreshJob?.cancel()
        realtimeShopRefreshJob = realtimeScope.launch {
            delay(150L)
            if (activeBusiness?.shopId == activeShop.shopId) {
                refreshActiveBusinessSubscription()
            }
        }
    }

    private fun scheduleRealtimeRefresh() {
        val activeShop = activeBusiness ?: return
        val recentSelfPush = (System.currentTimeMillis() - lastOutboxPushAt.get()) < 1_500L
        realtimeRefreshJob?.cancel()
        realtimeRefreshJob = realtimeScope.launch {
            delay(if (recentSelfPush) 900L else 250L)
            if (activeBusiness?.shopId == activeShop.shopId) {
                if (dao.outboxCount(activeShop.shopId) > 0) {
                    OfflineOutboxWorker.enqueueOutboxFlush(context, triggerSource = "realtime")
                } else {
                    runCatching {
                        syncPendingOutbox(
                            refreshSnapshot = true,
                            includeCatalog = false
                        )
                    }
                }
            }
        }
    }

    fun enqueueOutboxSync(triggerSource: String = "manual_enqueue") {
        OfflineOutboxWorker.enqueueOutboxFlush(context, triggerSource = triggerSource)
    }

    suspend fun flushOutboxViaWorker() = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: return@withContext
        if (remote.cachedAuthSession() == null) return@withContext
        val pendingCount = dao.outboxCount(shop.shopId)
        if (pendingCount > 0) {
            val stale = (System.currentTimeMillis() - lastSyncCompletedAt.get()) >= 15_000L
            syncPendingOutbox(
                refreshSnapshot = stale,
                includeCatalog = false
            )
        } else {
            val stale = (System.currentTimeMillis() - lastSyncCompletedAt.get()) >= 60_000L
            if (stale) {
                runCatching {
                    syncPendingOutbox(
                        refreshSnapshot = true,
                        includeCatalog = (cachedCatalogRows == null)
                    )
                }
            }
        }
    }

    suspend fun pendingOutboxCount(): Int = activeBusiness?.let { dao.outboxCount(it.shopId) } ?: 0

    suspend fun blockedOutboxItems(): List<OutboxEntity> = activeBusiness?.let { dao.blockedOutbox(it.shopId) }.orEmpty()

    suspend fun retryBlockedOutbox(operationId: String) = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: throw IllegalStateException("Select an active business first.")
        val userId = account?.userId?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Sign in with the account that created this change.")
        val item = dao.blockedOutbox(shop.shopId).firstOrNull { it.operationId == operationId }
            ?: throw IllegalStateException("This saved change is no longer waiting for attention.")
        check(item.actorUserId == userId || shop.role == "owner") { "Sign in with the account that created this change before retrying it." }
        check(dao.unblockSingleOutboxAndLog(operationId) == 1) { "Could not retry this saved change." }
        syncPendingOutbox()
        val stillBlocked = dao.blockedOutbox(shop.shopId).firstOrNull { it.operationId == operationId }
        if (stillBlocked != null) {
            throw IllegalStateException(stillBlocked.lastError ?: "The server still cannot accept this saved change.")
        }
    }

    suspend fun retryAllBlockedOutbox(): Int = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: throw IllegalStateException("Select an active business first.")
        val unblocked = dao.unblockShopOutboxAndLogs(shop.shopId)
        if (unblocked > 0) {
            syncPendingOutbox()
        }
        unblocked
    }

    suspend fun restoreSessionFromCacheOnly(): SessionSnapshot = withContext(Dispatchers.IO) {
        if (_session.value.isAuthenticated && activeBusiness != null) {
            return@withContext _session.value
        }
        val saved = dao.loadOfflineSession()
        if (saved != null) {
            val cached = runCatching { parseOfflineAccount(saved) }.getOrNull()
            if (cached != null) {
                account = cached
                activeBusiness = cached.memberships.firstOrNull { it.shopId == saved.activeShopId }
                    ?.takeIf {
                        it.status !in setOf("suspended", "terminated", "closed") &&
                            it.employmentStatus !in setOf("suspended", "terminated", "locked")
                    }
                    ?: cached.memberships.firstOrNull {
                        it.status !in setOf("suspended", "terminated", "closed") &&
                            it.employmentStatus !in setOf("suspended", "terminated", "locked")
                    }
                activeBusiness?.let { shop ->
                    restoreCachedCounters(shop.shopId)
                }
                seedCatalogIfEmpty()
                publishAccount()
            }
        }
        return@withContext _session.value
    }

    suspend fun restoreSession(): SessionSnapshot = withContext(Dispatchers.IO) {
        // Step 1: Immediately restore cached local session from Room so cold start never blocks on network.
        val cachedAuth = remote.cachedAuthSession()
        val saved = dao.loadOfflineSession()
        if (saved != null) {
            val cached = runCatching { parseOfflineAccount(saved) }.getOrNull()
            if (cached != null) {
                account = cached
                activeBusiness = cached.memberships.firstOrNull { it.shopId == saved.activeShopId }
                    ?.takeIf {
                        it.status !in setOf("suspended", "terminated", "closed") &&
                            it.employmentStatus !in setOf("suspended", "terminated", "locked")
                    }
                    ?: cached.memberships.firstOrNull {
                        it.status !in setOf("suspended", "terminated", "closed") &&
                            it.employmentStatus !in setOf("suspended", "terminated", "locked")
                    }
                activeBusiness?.let { shop ->
                    restoreCachedCounters(shop.shopId)
                    startRealtimeFor(shop)
                }
                seedCatalogIfEmpty()
                publishAccount()
            }
        }

        // Step 2: If a local authenticated snapshot is ready, return immediately and
        // delegate outbox flushing to constrained WorkManager while refreshing session metadata cleanly.
        if (_session.value.isAuthenticated) {
            if (cachedAuth != null && startupSyncJob?.isActive != true) {
                startupSyncJob = realtimeScope.launch {
                    val shopId = activeBusiness?.shopId
                    if (shopId != null && dao.outboxCount(shopId) > 0) {
                        OfflineOutboxWorker.enqueueOutboxFlush(context, triggerSource = "startup")
                    }
                    runCatching { loadAccount(autoSelectSingleBusiness = true) }
                }
            }
            return@withContext _session.value
        }

        // Step 3: Fresh session with encrypted auth token but no Room snapshot yet.
        if (cachedAuth != null) {
            runCatching {
                remote.restoreSession()
                loadAccount(autoSelectSingleBusiness = true)
            }.onSuccess { refreshed ->
                return@withContext refreshed
            }
        }

        return@withContext _session.value
    }

    suspend fun signIn(email: String, password: String): SessionSnapshot = withContext(Dispatchers.IO) {
        remote.signIn(email.trim(), password)
        loadAccount(autoSelectSingleBusiness = true)
    }

    suspend fun signInWithPasskey(passkey: String): SessionSnapshot = withContext(Dispatchers.IO) {
        val supplied = passkey.trim()
        val withoutQrPrefix = if (supplied.startsWith("CPX1.", ignoreCase = true)) {
            supplied.substringAfter('.')
        } else supplied
        val clean = withoutQrPrefix.trim().uppercase(Locale.ROOT)
        require(clean.isNotBlank()) { "Enter the passkey sent by the business owner." }
        val redemption = remote.redeemAccessPass(clean)
        val enrolledShopId = redemption.businessId?.takeIf(String::isNotBlank)
        if (enrolledShopId == null) {
            remote.clearSession()
            throw IllegalStateException("The enrollment response did not identify a business. Update the backend enrollment function and ask the owner for a new passkey.")
        }
        loadAccount(autoSelectSingleBusiness = true, preferredShopId = enrolledShopId)
    }

    suspend fun signInWithSuperadminToken(token: String): SessionSnapshot = withContext(Dispatchers.IO) {
        require(token.trim().isNotBlank()) { "Enter the current Superadmin token." }
        remote.redeemSuperadminToken(token.trim())
        loadAccount(autoSelectSingleBusiness = false)
    }

    suspend fun createAccessPass(role: String): JSONObject = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: throw IllegalStateException("Open a business before creating an access pass.")
        require(shop.role == "owner") { "Only the business owner can enroll Waiters." }
        require(role == "cashier") { "Only the Waiter role is currently supported for employee enrollment." }
        remote.createAccessPass(shop.shopId, role)
    }

    suspend fun createAdminBusinessAccessPass(shopId: String, role: String): JSONObject = withContext(Dispatchers.IO) {
        require(_session.value.isPlatformAdmin) { "Only a platform administrator can issue a client business passkey." }
        require(role in setOf("owner", "manager", "inventory_clerk", "cashier")) { "Invalid business role." }
        remote.createAccessPass(shopId, role)
    }

    suspend fun createAccessChallenge(): AccessChallenge = withContext(Dispatchers.IO) {
        val response = remote.createAccessChallenge()
        val token = response.optString("token").takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Could not create QR sign-in request.")
        val lifetimeSeconds = response.optLong("expires_in_seconds", 600L).coerceAtLeast(30L)
        AccessChallenge(token, System.currentTimeMillis() + lifetimeSeconds * 1_000L)
    }

    suspend fun pollAccessChallenge(token: String): ApprovedAccess? = withContext(Dispatchers.IO) {
        val result = remote.pollAccessChallenge(token)
        if (result.optString("status") != "approved") return@withContext null
        try {
            remote.signIn(result.optString("email"), result.optString("password"))
        } catch (error: Exception) {
            throw IllegalStateException("QR was approved, but Supabase rejected the enrollment credentials: ${error.message.orEmpty()}", error)
        }
        val shopId = result.optString("shop_id").ifBlank { result.optString("business_id") }.takeIf(String::isNotBlank)
        if (shopId == null) {
            remote.clearSession()
            throw IllegalStateException("The enrollment response did not identify a business. Update the backend enrollment function and try again.")
        }
        val session = try {
            loadAccount(autoSelectSingleBusiness = true, preferredShopId = shopId)
        } catch (error: Exception) {
            throw IllegalStateException("QR was approved, but the enrolled business data could not load: ${error.message.orEmpty()}", error)
        }
        ApprovedAccess(session, shopId ?: session.activeBusiness?.shopId)
    }

    suspend fun approveAccessChallenge(token: String, role: String = "cashier") = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: throw IllegalStateException("Open a business first.")
        require(shop.role == "owner") { "Only the business owner can approve Waiter QR enrollment." }
        require(role == "cashier") { "Only the Waiter role is currently supported for employee enrollment." }
        remote.approveAccessChallenge(token, shop.shopId, role)
    }

    suspend fun adminApproveAccessChallenge(token: String, shopId: String, role: String) = withContext(Dispatchers.IO) {
        require(account?.isPlatformAdmin == true) { "Only a platform administrator can enroll a user into a business." }
        require(role in setOf("owner", "manager", "inventory_clerk", "cashier")) { "Choose a valid business role." }
        remote.approveAccessChallenge(token, shopId, role)
    }

    suspend fun shopMembers(): JSONArray = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: throw IllegalStateException("Open a business first.")
        remote.listShopMembers(shop.shopId)
    }

    suspend fun renameEmployee(userId: String, displayName: String): JSONArray = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: throw IllegalStateException("Open a business before managing employees.")
        require(shop.role in setOf("owner", "manager")) { "Only an owner or manager can manage employees." }
        require(userId.isNotBlank()) { "Select a staff account to rename." }
        require(displayName.trim().length in 1..80) { "Enter a name between 1 and 80 characters." }
        remote.setEmployeeDisplayName(shop.shopId, userId, displayName.trim())
        remote.listShopMembers(shop.shopId)
    }

    suspend fun createEmployeeRecoveryPass(userId: String): JSONObject = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: throw IllegalStateException("Open a business before issuing a recovery code.")
        require(shop.role == "owner") { "Only the business owner can issue employee recovery codes." }
        require(userId.isNotBlank()) { "Select a staff account to recover." }
        remote.createEmployeeRecoveryPass(shop.shopId, userId)
    }

    suspend fun setEmployeeStatus(userId: String, status: String, reason: String?): JSONArray = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: throw IllegalStateException("Open a business first.")
        remote.setEmployeeStatus(shop.shopId, userId, status, reason)
        remote.listShopMembers(shop.shopId)
    }

    suspend fun setEmployeeRole(userId: String, role: String): JSONArray = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: throw IllegalStateException("Open a business first.")
        require(role in setOf("manager", "inventory_clerk", "cashier")) { "Choose a valid employee role." }
        remote.setEmployeeRole(shop.shopId, userId, role)
        remote.listShopMembers(shop.shopId)
    }

    suspend fun removeEmployeeAccess(userId: String, reason: String?): JSONArray = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: throw IllegalStateException("Open a business before managing employees.")
        require(shop.role in setOf("owner", "manager")) { "Only an owner or manager can manage employees." }
        remote.removeEmployeeAccess(shop.shopId, userId, reason)
        remote.listShopMembers(shop.shopId)
    }

    suspend fun updateBusinessName(name: String): SessionSnapshot = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: throw IllegalStateException("Open a business before editing its profile.")
        require(shop.role == "owner") { "Only the business owner can edit the business name." }
        require(name.trim().isNotBlank()) { "Enter a business name." }
        remote.updateShopName(shop.shopId, name.trim())
        loadAccount(autoSelectSingleBusiness = false)
    }

    suspend fun setEmployeeHours(userId: String, weekday: Int, start: String, end: String, enabled: Boolean): JSONArray = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: throw IllegalStateException("Open a business first.")
        remote.setEmployeeWorkingHours(shop.shopId, userId, weekday, start, end, enabled)
        remote.listShopMembers(shop.shopId)
    }

    suspend fun loadBusinessHours(): BusinessHours = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: throw IllegalStateException("Open a business first.")
        remote.getBusinessHours(shop.shopId).toBusinessHours()
    }

    suspend fun saveBusinessHours(hours: BusinessHours): BusinessHours = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: throw IllegalStateException("Open a business first.")
        require(shop.role == "owner") { "Only the business owner can change business hours." }
        val schedule = hours.weeklySchedule
        require(schedule.size == 7 && schedule.map { it.dayOfWeek }.toSet() == (1..7).toSet()) {
            "Configure each weekday from Monday through Sunday exactly once."
        }
        require(!hours.isEnabled || schedule.none { it.isOpen && it.openTime == it.closeTime }) {
            "Opening and closing times cannot be the same on an enabled day."
        }
        val rows = JSONArray().apply {
            schedule.sortedBy { it.dayOfWeek }.forEach { day ->
                put(
                    JSONObject()
                        .put("weekday", day.dayOfWeek - 1)
                        .put("open_time", day.openTime.format())
                        .put("close_time", day.closeTime.format())
                        .put("enabled", day.isOpen)
                )
            }
        }
        remote.setBusinessHours(shop.shopId, hours.isEnabled, rows)
        remote.getBusinessHours(shop.shopId).toBusinessHours()
    }

    private fun JSONObject.toBusinessHours(): BusinessHours {
        val rows = optJSONArray("days") ?: JSONArray()
        val byWeekday = buildMap {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                put(row.optInt("weekday", -1), row)
            }
        }
        val weeklySchedule = BusinessHours.defaultWeeklySchedule().map { template ->
            val row = byWeekday[template.dayOfWeek - 1]
            template.copy(
                isOpen = row?.optBoolean("enabled", false) ?: false,
                openTime = TimeSlot.parse(row?.optString("open_time") ?: template.openTime.format()),
                closeTime = TimeSlot.parse(row?.optString("close_time") ?: template.closeTime.format())
            )
        }
        return BusinessHours(
            isEnabled = optBoolean("is_enabled", false),
            weeklySchedule = weeklySchedule,
            timezoneId = optString("timezone").ifBlank { "Africa/Nairobi" }
        )
    }

    suspend fun createEmployeeAccessPass(role: String): JSONObject = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: throw IllegalStateException("Open a business first.")
        require(shop.role == "owner") { "Only the business owner can enroll Waiters." }
        require(role == "cashier") { "Only the Waiter role is currently supported for employee enrollment." }
        remote.createAccessPass(shop.shopId, role)
    }

    suspend fun signUp(email: String, password: String): SessionSnapshot = withContext(Dispatchers.IO) {
        val created = remote.signUp(email.trim(), password)
        if (created == null) {
            _session.value = SessionSnapshot(
                message = "Account created. Confirm your email, then sign in to continue."
            )
        } else {
            loadAccount(autoSelectSingleBusiness = true)
        }
        _session.value
    }

    suspend fun createBusiness(
        name: String,
        businessType: String = "big_club",
        businessSize: String = "medium",
        inventoryMode: String = "shared",
        counterCount: Int = 1,
        ownerPhone: String = ""
    ): SessionSnapshot = withContext(Dispatchers.IO) {
        require(name.trim().length in 1..120) { "Enter a business name (up to 120 characters)." }
        val normalizedOwnerPhone = SubscriptionManager.normalizeKenyanPhone(ownerPhone)
            ?: throw IllegalArgumentException("Enter a valid Kenyan owner mobile number.")
        require(businessType in setOf("big_club", "counter_seller")) { "Choose a valid business type." }
        val isLiquorStore = businessType == "counter_seller"
        val selectedSize = if (isLiquorStore) "medium" else businessSize
        val selectedInventoryMode = if (isLiquorStore) "shared" else inventoryMode
        val selectedCounterCount = if (isLiquorStore) 1 else counterCount
        require(selectedSize in setOf("small", "medium", "large")) { "Choose a valid business size." }
        require(selectedInventoryMode in setOf("shared", "per_counter")) { "Choose a valid inventory mode." }
        try {
            remote.createOwnerBusinessProfile(
                name.trim(),
                businessType,
                selectedSize,
                selectedInventoryMode,
                selectedCounterCount
            )
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("PGRST202", ignoreCase = true) ||
                (msg.contains("create_owner_business_profile", ignoreCase = true) && msg.contains("not found", ignoreCase = true))
            ) {
                val created = remote.createShop(name.trim())
                val shopId = created.optString("id").ifBlank { created.optString("shop_id") }
                if (shopId.isNotBlank()) {
                    runCatching {
                        remote.patchShopProfile(
                            shopId = shopId,
                            businessType = businessType,
                            businessSize = selectedSize,
                            inventoryMode = selectedInventoryMode,
                            counterCount = selectedCounterCount
                        )
                    }
                }
            } else {
                throw e
            }
        }
        val createdSession = loadAccount(autoSelectSingleBusiness = true)
        val shopId = createdSession.activeBusiness?.shopId
            ?: throw IllegalStateException("Business was created, but its owner profile could not be loaded.")
        remote.setOwnerBusinessPhone(shopId, normalizedOwnerPhone)
        loadAccount(autoSelectSingleBusiness = true)
    }

    suspend fun selectBusiness(shopId: String): SessionSnapshot = withContext(Dispatchers.IO) {
        val membership = account?.memberships?.firstOrNull { it.shopId == shopId }
            ?: throw IllegalArgumentException("Business membership not found")
        if (membership.status != "active") {
            throw IllegalStateException("This business is ${membership.status}. Contact the platform administrator.")
        }
        if (membership.employmentStatus != "active") {
            throw IllegalStateException("Your employee access is ${membership.employmentStatus}. Contact the business administrator.")
        }
        val saved = dao.loadOfflineSession()
        val savedUserId = saved?.let { runCatching { JSONObject(it.snapshotJson).optString("user_id") }.getOrNull() }
        val cachedShopId = saved?.activeShopId.takeIf { savedUserId == account?.userId }
        if (cachedShopId != membership.shopId) {
            check(dao.outboxCountAll() == 0 && dao.getPendingRemoteSales().isEmpty()) {
                "Sync the previous business's saved POS changes before switching businesses."
            }
            clearBusinessSnapshot()
        }
        activeBusiness = membership
        startRealtimeFor(membership)
        dao.setOfflineActiveShop(membership.shopId, System.currentTimeMillis())
        try {
            syncBusiness(membership)
            _session.value = publishAccount()
        } catch (error: Exception) {
            if (!isRetryable(error)) throw error
            _session.value = publishAccount().copy(message = offlineMessage())
            restoreCachedCounters(membership.shopId)
        }
        _session.value
    }

    suspend fun refreshAccount(): SessionSnapshot = withContext(Dispatchers.IO) {
        loadAccount(autoSelectSingleBusiness = false)
    }

    suspend fun synchronizeWhenOnline(force: Boolean = false): SessionSnapshot = withContext(Dispatchers.IO) {
        if (remote.cachedAuthSession() == null || activeBusiness == null) return@withContext _session.value
        val shop = activeBusiness ?: return@withContext _session.value
        val hasPending = dao.outboxCount(shop.shopId) > 0
        val stale = (System.currentTimeMillis() - lastSyncCompletedAt.get()) >= 4_000L
        if (!force && !hasPending && !stale) return@withContext _session.value
        if (force) cachedCatalogRows = null
        runCatching {
            syncPendingOutbox(
                refreshSnapshot = true,
                includeCatalog = force || (cachedCatalogRows == null)
            )
        }
        runCatching {
            val freshAccount = remote.account()
            val refreshedMembership = freshAccount.memberships.firstOrNull { it.shopId == shop.shopId }
            val resolvedAccount = if (refreshedMembership != null) {
                val authoritativeStatus = runCatching { remote.checkDarajaStkStatus(shop.shopId) }.getOrNull()
                val mergedMembership = if (authoritativeStatus != null) {
                    SubscriptionManager.mergeServerSubscriptionStatus(
                        membership = refreshedMembership,
                        status = authoritativeStatus,
                        verifiedAtMillis = System.currentTimeMillis()
                    )
                } else {
                    refreshedMembership
                }
                freshAccount.copy(
                    memberships = freshAccount.memberships.map {
                        if (it.shopId == shop.shopId) mergedMembership else it
                    }
                )
            } else {
                freshAccount
            }
            val latestMembership = resolvedAccount.memberships.firstOrNull { it.shopId == shop.shopId }
            account = resolvedAccount
            if (latestMembership != null) {
                if (latestMembership.status in setOf("suspended", "terminated", "closed") ||
                    latestMembership.employmentStatus in setOf("suspended", "terminated", "locked")
                ) {
                    activeBusiness = resolvedAccount.memberships.firstOrNull {
                        it.status !in setOf("suspended", "terminated", "closed") &&
                            it.employmentStatus !in setOf("suspended", "terminated", "locked")
                    }
                } else {
                    activeBusiness = latestMembership
                }
            }
            saveOfflineAccount(resolvedAccount, activeBusiness?.shopId)
            publishAccount()
        }
        _session.value
    }

    /** Silently refreshes the persisted auth session in the background. */
    suspend fun keepSessionAlive(): Boolean = withContext(Dispatchers.IO) {
        if (remote.cachedAuthSession() == null) return@withContext false
        runCatching {
            val refreshed = remote.silentRefreshIfNeeded()
            if (refreshed) lastSessionKeepAliveAt.set(System.currentTimeMillis())
            refreshed || remote.cachedAuthSession() != null
        }.getOrDefault(true)
    }

    /** Refreshes the persisted auth session when it has not been refreshed in the last 4 minutes. */
    suspend fun keepSessionAliveIfStale(maxAgeMillis: Long = 4 * 60 * 1_000L): Boolean = withContext(Dispatchers.IO) {
        if (remote.cachedAuthSession() == null) return@withContext false
        val now = System.currentTimeMillis()
        if (now - lastSessionKeepAliveAt.get() < maxAgeMillis) return@withContext true
        keepSessionAlive()
    }

    private val adminCachePrefs by lazy {
        context.getSharedPreferences("counterpos_admin_cache", Context.MODE_PRIVATE)
    }

    fun getCachedAdminBusinesses(): List<AdminBusiness> {
        val raw = adminCachePrefs.getString("admin_businesses", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { obj ->
                        val flagsArray = obj.optJSONArray("feature_flags") ?: JSONArray()
                        val flags = (0 until flagsArray.length()).mapNotNull { flagsArray.optString(it).takeIf(String::isNotBlank) }.toSet()
                        add(
                            AdminBusiness(
                                shopId = obj.optString("shop_id"),
                                shopName = obj.optString("shop_name"),
                                status = obj.optString("status"),
                                businessType = obj.optString("business_type"),
                                businessSize = obj.optString("business_size"),
                                featureFlags = flags,
                                ownerEmail = obj.optString("owner_email"),
                                memberCount = obj.optLong("member_count"),
                                productCount = obj.optLong("product_count"),
                                stockUnits = obj.optLong("stock_units"),
                                createdAt = obj.optString("created_at"),
                                suspendedAt = obj.optString("suspended_at").takeIf(String::isNotBlank),
                                closedAt = obj.optString("closed_at").takeIf(String::isNotBlank),
                                lifecycleReason = obj.optString("lifecycle_reason").takeIf(String::isNotBlank),
                                counterInventoryMode = obj.optString("counter_inventory_mode", "shared"),
                                counterCount = obj.optLong("counter_count", 1),
                                subscriptionStatus = obj.optString("subscription_status", "unknown"),
                                trialEndsAt = obj.optString("trial_ends_at").takeIf(String::isNotBlank),
                                subscriptionEndsAt = obj.optString("subscription_ends_at").takeIf(String::isNotBlank),
                                subscriptionPriceKsh = obj.optInt("subscription_price_ksh", 2500).let { if (it <= 0) 2500 else it },
                                subscriptionPlanDays = obj.optInt("subscription_plan_days", 30).let { if (it <= 0) 30 else it },
                                lastPaymentAt = obj.optString("last_payment_at").takeIf(String::isNotBlank),
                                lastPaymentReceipt = obj.optString("last_payment_receipt").takeIf(String::isNotBlank),
                                lastPaymentPhone = obj.optString("last_payment_phone").takeIf(String::isNotBlank),
                                customBillingNote = obj.optString("custom_billing_note").takeIf(String::isNotBlank),
                                paymentLockActive = obj.optBoolean("payment_lock_active", false),
                                paymentLockAmountKsh = obj.optInt("payment_lock_amount_ksh", 0),
                                paymentLockReason = obj.optString("payment_lock_reason").takeIf(String::isNotBlank),
                                paymentLockStartedAt = obj.optString("payment_lock_started_at").takeIf(String::isNotBlank),
                                ownerPhone = obj.optString("owner_phone").takeIf(String::isNotBlank)
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCachedAdminBusinesses(businesses: List<AdminBusiness>) {
        try {
            val array = JSONArray()
            businesses.forEach { b ->
                array.put(
                    JSONObject()
                        .put("shop_id", b.shopId)
                        .put("shop_name", b.shopName)
                        .put("status", b.status)
                        .put("business_type", b.businessType)
                        .put("business_size", b.businessSize)
                        .put("feature_flags", JSONArray(b.featureFlags.toList()))
                        .put("owner_email", b.ownerEmail)
                        .put("member_count", b.memberCount)
                        .put("product_count", b.productCount)
                        .put("stock_units", b.stockUnits)
                        .put("created_at", b.createdAt)
                        .put("suspended_at", b.suspendedAt ?: "")
                        .put("closed_at", b.closedAt ?: "")
                        .put("lifecycle_reason", b.lifecycleReason ?: "")
                        .put("counter_inventory_mode", b.counterInventoryMode)
                        .put("counter_count", b.counterCount)
                        .put("subscription_status", b.subscriptionStatus)
                        .put("trial_ends_at", b.trialEndsAt ?: "")
                        .put("subscription_ends_at", b.subscriptionEndsAt ?: "")
                        .put("subscription_price_ksh", b.subscriptionPriceKsh)
                        .put("subscription_plan_days", b.subscriptionPlanDays)
                        .put("last_payment_at", b.lastPaymentAt ?: "")
                        .put("last_payment_receipt", b.lastPaymentReceipt ?: "")
                        .put("last_payment_phone", b.lastPaymentPhone ?: "")
                        .put("custom_billing_note", b.customBillingNote ?: "")
                        .put("payment_lock_active", b.paymentLockActive)
                        .put("payment_lock_amount_ksh", b.paymentLockAmountKsh)
                        .put("payment_lock_reason", b.paymentLockReason ?: "")
                        .put("payment_lock_started_at", b.paymentLockStartedAt ?: "")
                        .put("owner_phone", b.ownerPhone ?: "")
                )
            }
            adminCachePrefs.edit().putString("admin_businesses", array.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun getCachedAdminAuditEvents(): List<AdminAuditEvent> {
        val raw = adminCachePrefs.getString("admin_audit_events", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { obj ->
                        add(
                            AdminAuditEvent(
                                action = obj.optString("action"),
                                entityType = obj.optString("entity_type"),
                                actorUserId = obj.optString("actor_user_id").takeIf(String::isNotBlank),
                                metadata = obj.optString("metadata"),
                                createdAt = obj.optString("created_at")
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCachedAdminAuditEvents(events: List<AdminAuditEvent>) {
        try {
            val array = JSONArray()
            events.take(100).forEach { e ->
                array.put(
                    JSONObject()
                        .put("action", e.action)
                        .put("entity_type", e.entityType)
                        .put("actor_user_id", e.actorUserId ?: "")
                        .put("metadata", e.metadata)
                        .put("created_at", e.createdAt)
                )
            }
            adminCachePrefs.edit().putString("admin_audit_events", array.toString()).apply()
        } catch (_: Exception) {
        }
    }

    suspend fun canOperateNow(): Boolean = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: return@withContext true
        if (account?.isPlatformAdmin == true || shop.role.equals("owner", ignoreCase = true)) {
            return@withContext true
        }
        try {
            remote.canSellForShop(shop.shopId)
        } catch (_: Exception) {
            // Offline mode: permit POS actions locally so employees can operate without internet
            true
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val pending = dao.outboxCountAll() + dao.getPendingRemoteSales().size
        check(pending == 0) { "Connect to the internet so the pending POS changes can sync before signing out." }
        clearLocalSession()
    }

    suspend fun adminBusinesses(): List<AdminBusiness> = withContext(Dispatchers.IO) {
        val cached = getCachedAdminBusinesses()
        try {
            val fresh = remote.adminBusinesses()
            saveCachedAdminBusinesses(fresh)
            fresh
        } catch (error: Exception) {
            if (cached.isNotEmpty()) cached else throw error
        }
    }

    suspend fun refreshAdminBusinesses(): List<AdminBusiness> = withContext(Dispatchers.IO) {
        remote.adminBusinesses().also(::saveCachedAdminBusinesses)
    }

    suspend fun adminPlatformBillingSettings(): PlatformBillingSettings = withContext(Dispatchers.IO) {
        require(account?.isPlatformAdmin == true) { "Only a platform administrator can view global billing settings." }
        remote.adminPlatformBillingSettings()
    }

    suspend fun adminUpdatePlatformBillingSettings(
        trialDurationDays: Int,
        subscriptionPriceKsh: Int
    ): PlatformBillingSettings = withContext(Dispatchers.IO) {
        require(account?.isPlatformAdmin == true) { "Only a platform administrator can change global billing settings." }
        val updated = remote.adminUpdatePlatformBillingSettings(trialDurationDays, subscriptionPriceKsh)
        val freshBusinesses = remote.adminBusinesses()
        saveCachedAdminBusinesses(freshBusinesses)
        updated
    }

    suspend fun adminAuditEvents(shopId: String = ""): List<AdminAuditEvent> = withContext(Dispatchers.IO) {
        val cached = getCachedAdminAuditEvents()
        try {
            val fresh = remote.auditEvents(shopId)
            if (shopId.isBlank()) saveCachedAdminAuditEvents(fresh)
            fresh
        } catch (error: Exception) {
            if (cached.isNotEmpty()) cached else emptyList()
        }
    }

    suspend fun counterSalesReport(fromIso: String, toIso: String): List<CounterReportRow> = withActiveBusiness {
        remote.counterSalesReport(activeBusiness!!.shopId, fromIso, toIso)
    }

    suspend fun createServiceCounter(name: String): ServiceCounter = withActiveBusiness {
        val shopId = activeBusiness!!.shopId
        remote.createServiceCounter(shopId, name).also { created ->
            dao.insertServiceCounters(
                listOf(
                    com.alvinb.counterpos.data.local.entity.ServiceCounterEntity(
                        id = created.id,
                        shopId = shopId,
                        name = created.name,
                        active = created.active
                    )
                )
            )
            _serviceCounters.value = (_serviceCounters.value.filterNot { it.id == created.id } + created)
                .sortedBy { it.name.lowercase(Locale.ROOT) }
        }
    }

    suspend fun loadCounterStock(counterId: String): Map<String, Int> = withActiveBusiness {
        val shopId = activeBusiness!!.shopId
        try {
            val balances = remote.counterStock(shopId, counterId)
            dao.clearCounterStock(shopId, counterId)
            val cached = balances.mapNotNull { (productId, quantity) ->
                dao.getProductByRemoteId(productId)?.let { product ->
                    com.alvinb.counterpos.data.local.entity.CounterStockEntity(
                        shopId = shopId,
                        counterId = counterId,
                        catalogCode = product.catalogCode,
                        productId = productId,
                        quantity = quantity
                    )
                }
            }
            if (cached.isNotEmpty()) dao.insertCounterStock(cached)
            buildMap {
                putAll(balances)
                cached.forEach { put(it.catalogCode, it.quantity) }
            }
        } catch (error: Exception) {
            if (!isRetryable(error)) throw error
            buildMap {
                dao.getCounterStockRows(counterId).forEach {
                    if (it.productId.isNotBlank()) put(it.productId, it.quantity)
                    put(it.catalogCode, it.quantity)
                }
            }
        }
    }

    suspend fun renameServiceCounter(counterId: String, name: String): List<ServiceCounter> = withActiveBusiness {
        val shopId = activeBusiness!!.shopId
        remote.renameServiceCounter(shopId, counterId, name)
        val updated = remote.serviceCounters(shopId)
        dao.insertServiceCounters(
            updated.map {
                com.alvinb.counterpos.data.local.entity.ServiceCounterEntity(
                    id = it.id,
                    shopId = shopId,
                    name = it.name,
                    active = it.active
                )
            }
        )
        _serviceCounters.value = updated
        updated
    }

    suspend fun deleteServiceCounter(counterId: String): List<ServiceCounter> = withActiveBusiness {
        val business = activeBusiness!!
        require(business.role in setOf("owner", "manager")) {
            "Only an owner or manager can delete service counters."
        }
        val currentCounters = _serviceCounters.value
        require(currentCounters.size > 1) {
            "Keep at least 1 active service counter for this business."
        }
        remote.deleteServiceCounter(business.shopId, counterId)
        dao.deleteServiceCounter(business.shopId, counterId)
        val refreshed = runCatching {
            remote.serviceCounters(business.shopId).filterNot { it.id == counterId }
        }.getOrElse {
            currentCounters.filterNot { it.id == counterId }
        }
        if (refreshed.isNotEmpty()) {
            dao.insertServiceCounters(
                refreshed.map {
                    com.alvinb.counterpos.data.local.entity.ServiceCounterEntity(
                        id = it.id,
                        shopId = business.shopId,
                        name = it.name,
                        active = it.active
                    )
                }
            )
            dao.deleteServiceCountersNotIn(business.shopId, refreshed.map { it.id })
        }
        _serviceCounters.value = refreshed
        refreshed
    }

    suspend fun transferCounterStock(productCode: String, fromCounter: String, toCounter: String, quantity: Int): Boolean =
        withActiveProduct(productCode) { product ->
            require(activeBusiness!!.role in setOf("owner", "manager", "inventory_clerk")) { "Only owners and managers can transfer counter stock." }
            require(quantity > 0 && fromCounter != toCounter) { "Choose two different counters and a positive quantity." }
            require(activeBusiness!!.counterInventoryMode == "per_counter") { "Counter transfers are only available in per-counter inventory mode." }
            val source = dao.getCounterStockQuantity(fromCounter, productCode)
                ?: throw IllegalStateException("Connect once to load the source counter's stock.")
            val destination = dao.getCounterStockQuantity(toCounter, productCode) ?: 0
            val target = makeCounterBalance(product, toCounter, destination + quantity)
            val payload = JSONObject()
                .put("product_code", productCode)
                .put("from_counter_id", fromCounter)
                .put("to_counter_id", toCounter)
                .put("quantity", quantity)
                .put("reason", "Counter transfer from mobile app")
            val outbox = newOutbox("transfer_stock", payload)
            val log = newTransactionLog(
                outbox = outbox,
                summary = "Transfer ${quantity}x ${product.name}"
            )
            val queued = dao.queueCounterTransfer(outbox, fromCounter, target, quantity, log)
            if (!queued) throw IllegalStateException("Not enough saved stock at the source counter to transfer.")
            check(source >= quantity)
            triggerBackgroundSync()
            true
        }

    suspend fun adminSetBusinessStatus(shopId: String, status: String, reason: String?): List<AdminBusiness> {
        remote.adminSetBusinessStatus(shopId, status, reason)
        val fresh = remote.adminBusinesses()
        saveCachedAdminBusinesses(fresh)
        return fresh
    }

    suspend fun adminCreateBusiness(
        name: String,
        ownerEmail: String,
        businessType: String,
        businessSize: String,
        inventoryMode: String,
        counterCount: Int,
        ownerPhone: String
    ): AdminBusinessCreationResult {
        val normalizedOwnerPhone = SubscriptionManager.normalizeKenyanPhone(ownerPhone)
            ?: throw IllegalArgumentException("Enter a valid Kenyan owner mobile number.")
        val createdRow = remote.adminCreateBusiness(name, ownerEmail.takeIf { it.isNotBlank() }, businessType, businessSize, inventoryMode, counterCount)
        val createdShopId = createdRow.optString("id").takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Business was created but no business ID was returned.")
        remote.setOwnerBusinessPhone(createdShopId, normalizedOwnerPhone)
        val fresh = remote.adminBusinesses()
        saveCachedAdminBusinesses(fresh)
        return AdminBusinessCreationResult(createdShopId, fresh)
    }

    suspend fun addMemberByEmail(shopId: String, email: String, role: String): List<AdminBusiness> {
        require(role in setOf("manager", "inventory_clerk", "cashier")) { "Choose a valid staff role." }
        remote.addMemberByEmail(shopId, email, role)
        val fresh = remote.adminBusinesses()
        saveCachedAdminBusinesses(fresh)
        return fresh
    }

    suspend fun adminOpenBusiness(shopId: String): SessionSnapshot = withContext(Dispatchers.IO) {
        val business = remote.adminBusinesses().firstOrNull { it.shopId == shopId }
            ?: throw IllegalArgumentException("Business profile not found in portfolio")
        val membership = createAdminBusinessMembership(business)
        account = account?.copy(memberships = account!!.memberships.filterNot { it.shopId == shopId } + membership)
        activeBusiness = membership
        syncBusiness(membership)
        publishAccount()
    }

    suspend fun exitToAdminPortal(): SessionSnapshot = withContext(Dispatchers.IO) {
        realtime.stop()
        activeBusiness = null
        dao.clearTenantCache()
        publishAccount()
    }

    suspend fun adminUpdateShopSubscription(
        shopId: String,
        priceKsh: Int? = null,
        endsAtIso: String? = null,
        status: String? = null,
        planDays: Int? = null,
        trialEndsAtIso: String? = null,
        note: String? = null
    ): List<AdminBusiness> = withContext(Dispatchers.IO) {
        remote.adminUpdateShopSubscription(
            shopId = shopId,
            priceKsh = priceKsh,
            endsAtIso = endsAtIso,
            status = status,
            planDays = planDays,
            trialEndsAtIso = trialEndsAtIso,
            note = note
        )
        val fresh = remote.adminBusinesses()
        saveCachedAdminBusinesses(fresh)
        fresh
    }

    suspend fun adminSetShopPaymentLock(
        shopId: String,
        locked: Boolean,
        amountKsh: Int? = null,
        reason: String? = null
    ): List<AdminBusiness> = withContext(Dispatchers.IO) {
        require(account?.isPlatformAdmin == true) { "Only a platform administrator can change a payment lock." }
        remote.adminSetShopPaymentLock(shopId, locked, amountKsh, reason)
        val fresh = remote.adminBusinesses()
        saveCachedAdminBusinesses(fresh)
        fresh
    }

    suspend fun adminUpdateBusinessPhone(shopId: String, phone: String): List<AdminBusiness> = withContext(Dispatchers.IO) {
        require(account?.isPlatformAdmin == true) { "Only a platform administrator can update client phone numbers." }
        val normalized = SubscriptionManager.normalizeKenyanPhone(phone)
            ?: throw IllegalArgumentException("Enter a valid Kenyan mobile number (e.g. 0712 345 678).")
        remote.setOwnerBusinessPhone(shopId, normalized)
        val fresh = remote.adminBusinesses()
        saveCachedAdminBusinesses(fresh)
        fresh
    }

    suspend fun adminDeleteBusiness(shopId: String): List<AdminBusiness> = withContext(Dispatchers.IO) {
        require(account?.isPlatformAdmin == true) { "Only a platform administrator can delete a business." }
        remote.adminDeleteBusiness(shopId)
        val updated = runCatching { remote.adminBusinesses() }
            .getOrElse { getCachedAdminBusinesses().filterNot { it.shopId == shopId } }
        saveCachedAdminBusinesses(updated)
        updated
    }

    suspend fun adminSetBusinessType(shopId: String, businessType: String): List<AdminBusiness> = withContext(Dispatchers.IO) {
        require(account?.isPlatformAdmin == true) { "Only a platform administrator can elevate a business." }
        remote.adminSetBusinessType(shopId, businessType)
        val fresh = remote.adminBusinesses()
        saveCachedAdminBusinesses(fresh)
        fresh.firstOrNull { it.shopId == shopId }?.let { updatedAdminBiz ->
            val currentActive = activeBusiness
            if (currentActive != null && currentActive.shopId == shopId) {
                val upgradedMembership = createAdminBusinessMembership(updatedAdminBiz).copy(
                    role = currentActive.role,
                    status = currentActive.status,
                    employmentStatus = currentActive.employmentStatus
                )
                activeBusiness = upgradedMembership
                account = account?.let { acc ->
                    acc.copy(memberships = acc.memberships.map {
                        if (it.shopId == shopId) upgradedMembership else it
                    })
                }
                account?.let { saveOfflineAccount(it, shopId) }
                publishAccount()
            }
        }
        fresh
    }

    suspend fun initiateSubscriptionStkPush(
        phoneNumber: String
    ): com.alvinb.counterpos.data.remote.DarajaStkResponse = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: throw IllegalStateException("Select an active business first.")
        remote.initiateDarajaStkPush(
            shopId = shop.shopId,
            phoneNumber = phoneNumber
        )
    }

    suspend fun checkSubscriptionStatus(shopId: String? = null): com.alvinb.counterpos.data.remote.SubscriptionStatusResult = withContext(Dispatchers.IO) {
        val targetShopId = shopId ?: activeBusiness?.shopId ?: throw IllegalStateException("Select an active business first.")
        remote.checkDarajaStkStatus(targetShopId)
    }

    suspend fun verifySubscriptionPaymentStatus(
        checkoutRequestId: String? = null
    ): com.alvinb.counterpos.data.remote.DarajaPaymentStatusResponse = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: throw IllegalStateException("Select an active business first.")
        remote.verifyDarajaPaymentStatus(shop.shopId, checkoutRequestId)
    }

    suspend fun refreshActiveBusinessSubscription(): SessionSnapshot = withContext(Dispatchers.IO) {
        val currentActive = activeBusiness ?: return@withContext session.value
        val currentAccount = account ?: return@withContext session.value
        try {
            val statusResult = remote.checkDarajaStkStatus(currentActive.shopId)
            val updatedMembership = SubscriptionManager.mergeServerSubscriptionStatus(
                membership = currentActive,
                status = statusResult,
                verifiedAtMillis = System.currentTimeMillis()
            )
            val updatedAccount = currentAccount.copy(
                memberships = currentAccount.memberships.map {
                    if (it.shopId == currentActive.shopId) updatedMembership else it
                }
            )
            activeBusiness = updatedMembership
            account = updatedAccount
            saveOfflineAccount(updatedAccount, currentActive.shopId)
            val snapshot = publishAccount()
            if (SubscriptionManager.evaluate(updatedMembership, isOnline = true).isEntitled) {
                dao.unblockShopOutboxAndLogs(currentActive.shopId)
                OfflineOutboxWorker.enqueueOutboxFlush(context, triggerSource = "subscription_restored")
            }
            snapshot
        } catch (_: Exception) {
            session.value
        }
    }

    private fun createAdminBusinessMembership(business: AdminBusiness): BusinessMembership {
        val liquor = business.businessType == "counter_seller" || business.businessType == "liquor_store"
        val featureFlags = if (liquor) {
            setOf("employee_management", "fifo_inventory")
        } else {
            business.featureFlags.ifEmpty { setOf("tables", "employee_management", "working_hours", "fifo_inventory", "sales_analytics") }
        }
        return BusinessMembership(
            shopId = business.shopId,
            shopName = business.shopName,
            role = "owner",
            status = "active",
            employmentStatus = "active",
            businessType = business.businessType,
            businessSize = business.businessSize,
            counterInventoryMode = business.counterInventoryMode,
            counterCount = if (liquor) 1 else business.counterCount.toInt().coerceAtLeast(1),
            featureFlags = featureFlags,
            currency = "KES",
            lowStockThreshold = 5,
            subscriptionStatus = business.subscriptionStatus,
            trialEndsAt = business.trialEndsAt,
            subscriptionEndsAt = business.subscriptionEndsAt,
            subscriptionPriceKsh = business.subscriptionPriceKsh,
            subscriptionPlanDays = business.subscriptionPlanDays,
            lastPaymentAt = business.lastPaymentAt,
            lastPaymentReceipt = business.lastPaymentReceipt,
            lastPaymentPhone = business.lastPaymentPhone,
            lastVerifiedEntitlementAt = System.currentTimeMillis(),
            ownerPhone = business.ownerPhone,
            paymentLockActive = business.paymentLockActive,
            paymentLockAmountKsh = business.paymentLockAmountKsh,
            paymentLockReason = business.paymentLockReason,
            paymentLockStartedAt = business.paymentLockStartedAt
        )
    }

    private suspend fun loadAccount(autoSelectSingleBusiness: Boolean, preferredShopId: String? = null): SessionSnapshot {
        var saved = dao.loadOfflineSession()
        account = try {
            remote.account().also {
                val userShopIds = it.memberships.map { m -> m.shopId }
                if (userShopIds.isNotEmpty()) {
                    check(dao.outboxCountForOtherShops(userShopIds) == 0) {
                        "This phone has unsynced POS changes from another business. Connect and sync them first."
                    }
                }
                val cachedUserId = saved?.let { cached ->
                    runCatching { JSONObject(cached.snapshotJson).optString("user_id") }.getOrNull()
                }
                val cachedShopId = saved?.activeShopId
                val switchingToDifferentShop = cachedShopId != null && userShopIds.none { id -> id == cachedShopId }
                if (!cachedUserId.isNullOrBlank() && cachedUserId != it.userId && switchingToDifferentShop) {
                    check(dao.getPendingRemoteSales().isEmpty() && dao.outboxCountAll() == 0) {
                        "This phone has unsynced POS changes from another business. Sign in with that business's account first so the saved work can sync."
                    }
                    clearBusinessSnapshot()
                    dao.clearOfflineSession()
                    saved = null
                    activeBusiness = null
                }
                saveOfflineAccount(it, activeBusiness?.shopId ?: saved?.activeShopId)
            }
        } catch (error: Exception) {
            val cached = saved?.let(::parseOfflineAccount) ?: throw error
            val userShopIds = cached.memberships.map { m -> m.shopId }
            if (userShopIds.isNotEmpty()) {
                check(dao.outboxCountForOtherShops(userShopIds) == 0) {
                    "This phone has unsynced POS changes from another business. Connect and sign in with the account that created them."
                }
            }
            account = cached
            activeBusiness = cached.memberships.firstOrNull { it.shopId == saved?.activeShopId }
                ?.takeIf { it.status !in setOf("suspended", "terminated", "closed") && it.employmentStatus !in setOf("suspended", "terminated", "locked") }
            if (activeBusiness == null && autoSelectSingleBusiness) {
                val eligibleCachedMemberships = cached.memberships.filter {
                    it.status !in setOf("suspended", "terminated", "closed") &&
                        it.employmentStatus !in setOf("suspended", "terminated", "locked")
                }
                if (eligibleCachedMemberships.isNotEmpty()) activeBusiness = eligibleCachedMemberships.first()
            }
            if (activeBusiness != null && saved?.activeShopId != null && saved.activeShopId != activeBusiness?.shopId) {
                check(dao.outboxCountAll() == 0 && dao.getPendingRemoteSales().isEmpty()) {
                    "Sync the previous business's saved POS changes before opening another business."
                }
                clearBusinessSnapshot()
            }
            val offlineSnapshot = publishAccount().copy(message = offlineMessage())
            _session.value = offlineSnapshot
            activeBusiness?.let {
                restoreCachedCounters(it.shopId)
                if (dao.outboxCount(it.shopId) > 0) OfflineOutboxWorker.enqueue(context)
            }
            return offlineSnapshot
        }

        val activeMemberships = account?.memberships?.filter {
            it.status !in setOf("suspended", "terminated", "closed") &&
            it.employmentStatus !in setOf("suspended", "terminated", "locked")
        }.orEmpty()
        val savedShopId = saved?.activeShopId
        if (!preferredShopId.isNullOrBlank()) {
            activeBusiness = activeMemberships.firstOrNull { it.shopId == preferredShopId }
                ?: throw IllegalStateException("This account is not an active member of the business that enrolled this device. Ask the owner to check the staff account and try again.")
        }
        if (activeBusiness == null && !savedShopId.isNullOrBlank()) {
            activeBusiness = activeMemberships.firstOrNull { it.shopId == savedShopId }
        }
        if (autoSelectSingleBusiness && activeBusiness == null && activeMemberships.isNotEmpty()) {
            activeBusiness = activeMemberships.firstOrNull()
        } else if (activeBusiness != null) {
            val refreshed = activeMemberships.firstOrNull { it.shopId == activeBusiness?.shopId }
            if (refreshed != null) {
                activeBusiness = refreshed
            } else if (activeMemberships.isNotEmpty()) {
                activeBusiness = activeMemberships.firstOrNull()
            }
        }
        val selected = activeBusiness
        if (selected != null) {
            startRealtimeFor(selected)
            val cachedUserId = saved?.let { runCatching { JSONObject(it.snapshotJson).optString("user_id") }.getOrNull() }
            val cachedShopId = saved?.activeShopId.takeIf { cachedUserId == account?.userId }
            if (cachedShopId != null && cachedShopId != selected.shopId) {
                check(dao.outboxCountAll() == 0 && dao.getPendingRemoteSales().isEmpty()) {
                    "Sync the previous business's saved POS changes before opening another business."
                }
                clearBusinessSnapshot()
            }
            try {
                syncBusiness(selected)
            } catch (error: Exception) {
                if (!isRetryable(error)) throw error
                _session.value = publishAccount().copy(message = offlineMessage())
                restoreCachedCounters(selected.shopId)
            }
            if (dao.outboxCount(selected.shopId) > 0) {
                OfflineOutboxWorker.enqueueOutboxFlush(context, triggerSource = "account_load")
            }
        }
        saveOfflineAccount(account!!, activeBusiness?.shopId ?: savedShopId)
        return publishAccount()
    }

    private suspend fun saveOfflineAccount(snapshot: AccountSnapshot, activeShopId: String?) {
        val memberships = JSONArray()
        snapshot.memberships.forEach { membership ->
            memberships.put(
                JSONObject()
                    .put("shop_id", membership.shopId)
                    .put("shop_name", membership.shopName)
                    .put("role", membership.role)
                    .put("status", membership.status)
                    .put("employment_status", membership.employmentStatus)
                    .put("business_type", membership.businessType)
                    .put("business_size", membership.businessSize)
                    .put("counter_inventory_mode", membership.counterInventoryMode)
                    .put("counter_count", membership.counterCount)
                    .put("feature_flags", JSONArray(membership.featureFlags.toList()))
                    .put("currency", membership.currency)
                    .put("low_stock_threshold", membership.lowStockThreshold)
                    .put("subscription_status", membership.subscriptionStatus)
                    .put("trial_ends_at", membership.trialEndsAt ?: "")
                    .put("subscription_ends_at", membership.subscriptionEndsAt ?: "")
                    .put("subscription_price_ksh", membership.subscriptionPriceKsh)
                    .put("subscription_plan_days", membership.subscriptionPlanDays)
                    .put("last_payment_at", membership.lastPaymentAt ?: "")
                    .put("last_payment_receipt", membership.lastPaymentReceipt ?: "")
                    .put("last_payment_phone", membership.lastPaymentPhone ?: "")
                    .put("last_verified_entitlement_at", membership.lastVerifiedEntitlementAt)
                    .put("owner_phone", membership.ownerPhone ?: "")
                    .put("payment_lock_active", membership.paymentLockActive)
                    .put("payment_lock_amount_ksh", membership.paymentLockAmountKsh)
                    .put("payment_lock_reason", membership.paymentLockReason ?: "")
                    .put("payment_lock_started_at", membership.paymentLockStartedAt ?: "")
            )
        }
        val json = JSONObject()
            .put("user_id", snapshot.userId)
            .put("email", snapshot.email)
            .put("is_platform_admin", snapshot.isPlatformAdmin)
            .put("memberships", memberships)
        dao.saveOfflineSession(OfflineSessionEntity(snapshotJson = json.toString(), activeShopId = activeShopId))
    }

    private fun parseOfflineAccount(saved: OfflineSessionEntity): AccountSnapshot {
        val json = JSONObject(saved.snapshotJson)
        val rows = json.optJSONArray("memberships") ?: JSONArray()
        val memberships = buildList {
            for (index in 0 until rows.length()) rows.optJSONObject(index)?.let { row ->
                val flags = row.optJSONArray("feature_flags") ?: JSONArray()
                add(
                    BusinessMembership(
                        shopId = row.optString("shop_id"),
                        shopName = row.optString("shop_name"),
                        role = row.optString("role"),
                        status = row.optString("status", "active"),
                        employmentStatus = row.optString("employment_status", "active"),
                        businessType = row.optString("business_type", "big_club"),
                        businessSize = row.optString("business_size", "medium"),
                        counterInventoryMode = row.optString("counter_inventory_mode", "shared"),
                        counterCount = row.optInt("counter_count", 1).coerceAtLeast(1),
                        featureFlags = (0 until flags.length()).mapNotNull { flags.optString(it).takeIf(String::isNotBlank) }.toSet(),
                        currency = row.optString("currency", "KES"),
                        lowStockThreshold = row.optInt("low_stock_threshold", 5),
                        subscriptionStatus = row.optString("subscription_status", "trial"),
                        trialEndsAt = row.optString("trial_ends_at").takeIf(String::isNotBlank),
                        subscriptionEndsAt = row.optString("subscription_ends_at").takeIf(String::isNotBlank),
                        subscriptionPriceKsh = row.optInt("subscription_price_ksh", 2500).let { if (it <= 0) 2500 else it },
                        subscriptionPlanDays = row.optInt("subscription_plan_days", 30).let { if (it <= 0) 30 else it },
                        lastPaymentAt = row.optString("last_payment_at").takeIf(String::isNotBlank),
                        lastPaymentReceipt = row.optString("last_payment_receipt").takeIf(String::isNotBlank),
                        lastPaymentPhone = row.optString("last_payment_phone").takeIf(String::isNotBlank),
                        lastVerifiedEntitlementAt = row.optLong("last_verified_entitlement_at", 0L),
                        ownerPhone = row.optString("owner_phone").takeIf(String::isNotBlank),
                        paymentLockActive = row.optBoolean("payment_lock_active", false),
                        paymentLockAmountKsh = row.optInt("payment_lock_amount_ksh", 0),
                        paymentLockReason = row.optString("payment_lock_reason").takeIf(String::isNotBlank),
                        paymentLockStartedAt = row.optString("payment_lock_started_at").takeIf(String::isNotBlank)
                    )
                )
            }
        }
        return AccountSnapshot(json.optString("user_id"), json.optString("email"), json.optBoolean("is_platform_admin"), memberships)
    }

    private fun offlineMessage(): String = "Offline mode: changes are saved on this phone and will sync automatically when internet returns."

    private suspend fun restoreCachedCounters(shopId: String) {
        _serviceCounters.value = dao.getServiceCounters(shopId).map {
            ServiceCounter(id = it.id, name = it.name, active = it.active)
        }
    }

    private fun isRetryable(error: Throwable): Boolean = when (error) {
        is SupabaseUnauthorizedException -> true
        is SupabaseHttpException -> error.statusCode == 300 || error.statusCode == 401 || error.statusCode == 404 || error.statusCode == 408 || error.statusCode == 429 || error.statusCode >= 500
        else -> error is java.io.IOException
    }

    private fun publishAccount(): SessionSnapshot {
        val current = account
        val next = if (current == null) {
            SessionSnapshot()
        } else {
            SessionSnapshot(
                isAuthenticated = true,
                userId = current.userId,
                email = current.email,
                isPlatformAdmin = current.isPlatformAdmin,
                memberships = current.memberships,
                activeBusiness = activeBusiness,
                message = _session.value.message
            )
        }
        _session.value = next
        return next
    }

    private fun loadBundledMetadata(): Pair<Map<String, BundledProductMetadata>, Map<String, BundledProductMetadata>> {
        cachedBundledMetadata?.let { return it }
        val byCode = mutableMapOf<String, BundledProductMetadata>()
        val byName = mutableMapOf<String, BundledProductMetadata>()
        runCatching {
            val bundledRows = assets.open("counterpos_catalog.json").bufferedReader().use { JSONArray(it.readText()) }
            for (index in 0 until bundledRows.length()) {
                val row = bundledRows.optJSONObject(index) ?: continue
                val metadata = BundledProductMetadata(
                    imageAssetName = row.optString("imageAssetName", "product_stock_001"),
                    verifiedBarcode = row.optNullableString("verifiedBarcode"),
                    productSourceUrl = row.optNullableString("productSourceUrl"),
                    imageSourceUrl = row.optNullableString("imageSourceUrl")
                )
                row.optString("catalogCode").takeIf(String::isNotBlank)?.let { byCode[it] = metadata }
                row.optString("name").trim().lowercase(Locale.ROOT).takeIf(String::isNotBlank)?.let { byName[it] = metadata }
            }
        }
        return (byCode.toMap() to byName.toMap()).also { cachedBundledMetadata = it }
    }

    private suspend fun syncBusiness(
        membership: BusinessMembership,
        includeCatalog: Boolean = (cachedCatalogRows == null)
    ) {
        if (dao.outboxCount(membership.shopId) > 0) {
            restoreCachedCounters(membership.shopId)
            runCatching { syncPendingOutbox(refreshSnapshot = false) }
            if (dao.outboxCount(membership.shopId) > 0) return
        }
        seedCatalogIfEmpty()
        val existingProductsList = dao.getAllProducts()
        val existingProductsByCode = existingProductsList.associateBy { it.catalogCode }
        val preservedCatalogItems = existingProductsList.filter { !it.inMyStock }
        val existingTables = dao.getDiningTables()

        runCatching { uploadPendingLocalSales() }
        val hasPendingSales = dao.getPendingRemoteSales().isNotEmpty()

        val shouldFetchCatalog = includeCatalog || (cachedCatalogRows == null && existingProductsList.none { !it.inMyStock && it.remoteId != null })
        val (counters, catalogRows, productRows, saleRows, tableRows, tableOrderRows) = coroutineScope {
            val countersDeferred = async { remote.serviceCounters(membership.shopId) }
            val catalogDeferred = async {
                when {
                    cachedCatalogRows != null -> cachedCatalogRows!!
                    shouldFetchCatalog -> remote.catalog().also { cachedCatalogRows = it }
                    else -> JSONArray()
                }
            }
            val productsDeferred = async { remote.products(membership.shopId) }
            val salesDeferred = async { remote.sales(membership.shopId) }
            val tablesDeferred = async { remote.diningTables(membership.shopId) }
            val tableOrdersDeferred = async { remote.tableOrders(membership.shopId) }
            Tuple6(
                countersDeferred.await(),
                catalogDeferred.await(),
                productsDeferred.await(),
                salesDeferred.await(),
                tablesDeferred.await(),
                tableOrdersDeferred.await()
            )
        }

        val counterStockSnapshots = coroutineScope {
            counters.map { counter ->
                async {
                    counter.id to runCatching { remote.counterStock(membership.shopId, counter.id) }.getOrDefault(emptyMap())
                }
            }.awaitAll().toMap()
        }

        val (bundledMetadataByCode, bundledMetadataByName) = loadBundledMetadata()
        fun metadataFor(row: JSONObject): BundledProductMetadata? {
            val code = row.optString("code").ifBlank { row.optString("catalog_code") }
            return bundledMetadataByCode[code]
                ?: bundledMetadataByName[row.optString("name").trim().lowercase(Locale.ROOT)]
        }

        val localPhotosByPrefix = localProductPhotosMap(membership.shopId)

        // Remote catalog rows augment the bundled image fallback. The remote
        // product snapshot then replaces matching catalog rows with tenant data.
        val catalogProducts = buildList {
            for (index in 0 until catalogRows.length()) {
                catalogRows.optJSONObject(index)?.let { row ->
                    val code = row.optString("code").ifBlank { row.optString("catalog_code") }
                    val bundled = metadataFor(row)
                    val fallback = bundled?.imageAssetName ?: existingProductsByCode[code]?.imageAssetName ?: "product_stock_001"
                    val imageUrl = row.optNullableString("image_url") ?: bundled?.imageSourceUrl
                    add(
                        ProductEntity(
                            catalogCode = code,
                            verifiedBarcode = row.optNullableString("barcode") ?: bundled?.verifiedBarcode,
                            name = row.optString("name"),
                            brand = row.optString("brand"),
                            size = row.optString("size"),
                            category = row.optString("category", "Beverage"),
                            suggestedPriceKsh = row.optInt("suggested_price", 0),
                            sellingPriceKsh = 0,
                            stockQuantity = 0,
                            inMyStock = false,
                            lowStockThreshold = membership.lowStockThreshold,
                            imageAssetName = fallback,
                            productSourceUrl = row.optNullableString("product_source_url") ?: bundled?.productSourceUrl,
                            imageSourceUrl = imageUrl,
                            verificationNote = "Shared backend catalog",
                            remoteId = row.optString("id").takeIf(String::isNotBlank)
                        )
                    )
                }
            }
        }
        val products = buildList {
            for (index in 0 until productRows.length()) {
                productRows.optJSONObject(index)?.let { row ->
                    val code = row.optString("code").ifBlank { row.optString("catalog_code") }
                    val safePrefix = code.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                        .take(64).ifBlank { "product" }
                    val remoteId = row.optString("id").takeIf(String::isNotBlank)
                    val bundled = metadataFor(row)
                    val existingLocal = existingProductsByCode[code]
                    val fallback = bundled?.imageAssetName ?: existingLocal?.imageAssetName ?: "product_stock_001"
                    val imageUrl = row.optNullableString("photo_url") ?: bundled?.imageSourceUrl
                    val rawStock = row.optInt("stock", row.optInt("quantity", row.optInt("stock_quantity", row.optInt("current_stock", 0))))
                    val hasCounterBalance = remoteId != null && counterStockSnapshots.values.any { it.containsKey(remoteId) }
                    val counterStockTotal = if (remoteId != null) counterStockSnapshots.values.sumOf { it[remoteId] ?: 0 } else 0
                    val finalStock = if (membership.counterInventoryMode == "per_counter" && hasCounterBalance) {
                        counterStockTotal
                    } else {
                        rawStock
                    }
                    val price = row.optInt("price", row.optInt("selling_price", 0))
                    val cost = row.optInt("average_cost", row.optInt("unit_cost", 0))
                    val remoteSaleUnits = row.optJSONArray("sale_units")?.takeIf { it.length() > 0 }?.toString()
                    add(
                        ProductEntity(
                            catalogCode = code,
                            verifiedBarcode = row.optNullableString("barcode") ?: bundled?.verifiedBarcode,
                            name = row.optString("name"),
                            brand = row.optString("brand"),
                            size = row.optString("size"),
                            category = row.optString("category", "Beverage"),
                            suggestedPriceKsh = price,
                            sellingPriceKsh = price,
                            stockQuantity = finalStock,
                            inMyStock = true,
                            lowStockThreshold = membership.lowStockThreshold,
                            imageAssetName = fallback,
                            photoOverrideUri = localPhotosByPrefix[safePrefix],
                            productSourceUrl = row.optNullableString("product_source_url") ?: bundled?.productSourceUrl,
                            imageSourceUrl = imageUrl,
                            verificationNote = "Business inventory",
                            salesUnitsJson = remoteSaleUnits
                                ?: existingLocal?.salesUnitsJson?.takeIf(String::isNotBlank)
                                ?: serializeSaleUnits(listOf(SaleUnit("Bottle", price))),
                            remoteId = remoteId,
                            averageCostKsh = cost
                        )
                    )
                }
            }
        }
        val productCodeById = buildMap {
            for (index in 0 until productRows.length()) {
                productRows.optJSONObject(index)?.let { row ->
                    val code = row.optString("code").ifBlank { row.optString("catalog_code") }
                    put(row.optString("id"), code)
                }
            }
        }
        val counterStockRows = counterStockSnapshots.flatMap { (counterId, balances) ->
            balances.mapNotNull { (productId, quantity) ->
                productCodeById[productId]?.takeIf(String::isNotBlank)?.let { code ->
                    com.alvinb.counterpos.data.local.entity.CounterStockEntity(
                        shopId = membership.shopId,
                        counterId = counterId,
                        catalogCode = code,
                        productId = productId,
                        quantity = quantity
                    )
                }
            }
        }
        val sales = buildList {
            for (index in 0 until saleRows.length()) {
                saleRows.optJSONObject(index)?.let { row ->
                    val remoteId = row.optString("id")
                    val counterId = row.optNullableString("counter_id") ?: row.optNullableString("source_counter_id")
                    val counterName = counters.firstOrNull { it.id == counterId }?.name ?: row.optNullableString("counter_name")
                    val tableId = row.optNullableString("table_id")
                    val tableName = row.optNullableString("table_name") ?: existingTables.firstOrNull { it.id == tableId || it.remoteId == tableId }?.name
                    val waiterName = row.optNullableString("waiter_name") ?: row.optNullableString("waiter_email") ?: row.optNullableString("created_by")
                    add(
                        SaleEntity(
                            id = stableLocalId(remoteId),
                            catalogCode = productCodeById[row.optString("product_id")] ?: row.optString("product_id"),
                            productName = row.optString("product_name"),
                            imageAssetName = "product_stock_001",
                            unitName = row.optString("unit_name", "Bottle"),
                            quantity = row.optInt("qty", row.optInt("quantity", 1)),
                            unitPriceKsh = row.optInt("unit_price", 0),
                            totalKsh = row.optInt("total", 0),
                            createdAt = parseTimestamp(row.optString("created_at")),
                            undone = !row.isNull("undone_at"),
                            remoteId = remoteId,
                            sourceCounterId = counterId,
                            counterId = counterId,
                            counterName = counterName,
                            waiterName = waiterName,
                            tableId = tableId,
                            tableName = tableName,
                            paymentMethod = row.optString("payment_method", "cash")
                        )
                    )
                }
            }
        }
        val localTableIdsByRemoteId = mutableMapOf<String, String>()
        val tables = buildList {
            for (index in 0 until tableRows.length()) {
                tableRows.optJSONObject(index)?.let { row ->
                    val remoteId = row.optString("id")
                    val localId = existingTables.firstOrNull { it.remoteId == remoteId || it.id == remoteId }?.id ?: remoteId
                    localTableIdsByRemoteId[remoteId] = localId
                    add(
                        DiningTableEntity(
                            id = localId,
                            name = row.optString("name"),
                            createdAt = parseTimestamp(row.optString("created_at")),
                            remoteId = remoteId
                        )
                    )
                }
            }
        }
        val orders = buildList {
            for (index in 0 until tableOrderRows.length()) {
                tableOrderRows.optJSONObject(index)?.let { row ->
                    add(
                        TableOrderEntity(
                            tableId = localTableIdsByRemoteId[row.optString("table_id")] ?: row.optString("table_id"),
                            saleId = stableLocalId(row.optString("sale_id")),
                            catalogCode = productCodeById[row.optString("product_id")] ?: row.optString("product_id"),
                            productName = row.optString("product_name"),
                            unitName = row.optString("unit_name", "Bottle"),
                            quantity = row.optInt("qty", 1),
                            unitPriceKsh = row.optInt("unit_price", 0),
                            totalKsh = row.optInt("total", 0),
                            servedAt = parseTimestamp(row.optString("served_at")),
                            counterId = row.optNullableString("counter_id"),
                            counterName = row.optNullableString("counter_name"),
                            waiterName = row.optNullableString("waiter_name") ?: row.optNullableString("waiter_email") ?: row.optNullableString("created_by"),
                            settlementId = row.optNullableString("settlement_id")
                        )
                    )
                }
            }
        }
        val mergedProducts = linkedMapOf<String, ProductEntity>().apply {
            preservedCatalogItems.forEach { put(it.catalogCode, it) }
            catalogProducts.forEach { put(it.catalogCode, it) }
            products.forEach { put(it.catalogCode, it) }
        }.values.toList()
        val serviceCounterEntities = counters.map {
            com.alvinb.counterpos.data.local.entity.ServiceCounterEntity(it.id, membership.shopId, it.name, it.active)
        }
        dao.replaceBusinessSnapshot(
            shopId = membership.shopId,
            products = mergedProducts,
            sales = sales,
            tables = tables,
            orders = orders,
            serviceCounters = serviceCounterEntities,
            counterStock = counterStockRows,
            preservePendingSales = hasPendingSales
        )
        _serviceCounters.value = counters
        lastSyncCompletedAt.set(System.currentTimeMillis())
    }

    private suspend fun uploadPendingLocalSales() {
        if (activeBusiness?.role != "owner") return
        val knownCounters = _serviceCounters.value
        for (sale in dao.getPendingRemoteSales()) {
            val productId = dao.getProductByCode(sale.catalogCode)?.remoteId ?: continue
            val counterId = sale.counterId?.takeIf { id -> knownCounters.any { it.id == id } }
                ?: knownCounters.singleOrNull()?.id
                ?: continue
            // Legacy table orders have no operation UUID; replaying them could
            // duplicate a running bill line, so keep them local for safe recovery.
            if (!sale.tableId.isNullOrBlank()) continue
            val result = remote.recordSaleUnitFromCounter(
                productId = productId,
                counterId = counterId,
                quantity = sale.quantity,
                unitName = sale.unitName,
                unitPrice = sale.unitPriceKsh,
                paymentMethod = sale.paymentMethod
            )
            val remoteId = result.optString("id").takeIf(String::isNotBlank) ?: "offline-${sale.id}"
            dao.markSaleUploaded(sale.id, remoteId)
        }
    }

    suspend fun seedCatalogIfEmpty() = withContext(Dispatchers.IO) {
        if (dao.getProductCount() > 0) return@withContext
        val payload = assets.open("counterpos_catalog.json").bufferedReader().use { it.readText() }
        val array = JSONArray(payload)
        val seededProducts = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val code = item.getString("catalogCode")
                val sellingPrice = item.optInt("sellingPriceKsh", 0)
                add(
                    ProductEntity(
                        catalogCode = code,
                        verifiedBarcode = item.optNullableString("verifiedBarcode"),
                        name = item.getString("name"),
                        brand = item.optString("brand", ""),
                        size = item.optString("size", ""),
                        category = item.optString("category", "Beverage"),
                        suggestedPriceKsh = item.optInt("suggestedPriceKsh", 0),
                        sellingPriceKsh = sellingPrice,
                        stockQuantity = item.optInt("stockQuantity", 0),
                        inMyStock = item.optBoolean("inMyStock", false),
                        lowStockThreshold = item.optInt("lowStockThreshold", 5),
                        imageAssetName = item.getString("imageAssetName"),
                        productSourceUrl = item.optNullableString("productSourceUrl"),
                        imageSourceUrl = item.optNullableString("imageSourceUrl"),
                        verificationNote = item.optString("verificationNote", ""),
                        salesUnitsJson = serializeSaleUnits(listOf(SaleUnit("Bottle", sellingPrice)))
                    )
                )
            }
        }
        dao.insertProducts(seededProducts)
    }

    suspend fun createDiningTable(name: String): DiningTableEntity {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Enter a name for the table." }
        require(cleanName.length <= 40) { "Table names must be 40 characters or fewer." }
        return withActiveBusiness {
            require(activeBusiness!!.role in setOf("owner", "manager", "cashier")) {
                "Only business staff can create tables."
            }
            require(dao.getDiningTableByName(cleanName) == null) { "A table with this name already exists." }
            val table = DiningTableEntity(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                createdAt = System.currentTimeMillis(),
                remoteId = null
            )
            val outbox = newOutbox("create_dining_table", JSONObject().put("local_table_id", table.id).put("name", cleanName))
            dao.queueDiningTable(
                table,
                outbox,
                newTransactionLog(outbox, "Open table $cleanName")
            )
            triggerBackgroundSync()
            table
        }
    }

    suspend fun deleteDiningTable(tableId: String): Boolean = withActiveBusiness {
        require(activeBusiness!!.role in setOf("owner", "manager")) {
            "Only owners and managers can delete tables."
        }
        val table = dao.getDiningTable(tableId) ?: return@withActiveBusiness false
        val openCount = dao.openTableOrderCount(tableId)
        check(openCount == 0) { "Cannot delete table with open unpaid orders. Please settle or clear the bill first." }
        val shop = activeBusiness ?: throw IllegalStateException("Select an active business first.")
        val remoteId = table.remoteId?.takeIf(String::isNotBlank)
        dao.deleteTableOrdersForTable(tableId)
        dao.deleteDiningTable(tableId)
        if (!remoteId.isNullOrBlank()) {
            val payload = JSONObject().put("table_id", remoteId).put("local_table_id", tableId)
            val outbox = newOutbox("delete_dining_table", payload)
            dao.enqueueOutboxAndLog(outbox, newTransactionLog(outbox, "Delete table ${table.name}"))
            triggerBackgroundSync()
        }
        true
    }

    suspend fun settleTableBill(tableId: String, paymentMethod: String): TableSettlementResult = withActiveBusiness {
        val method = paymentMethod.trim().lowercase(Locale.ROOT)
        require(method in setOf("cash", "mpesa", "card", "other")) { "Choose a valid payment method." }
        val shop = activeBusiness ?: throw IllegalStateException("Select an active business first.")
        if (dao.outboxCount(shop.shopId) > 0) {
            // Flush any queued orders for this shop right now before settling the bill
            runCatching { syncPendingOutbox(refreshSnapshot = false) }
        }
        check(dao.outboxCount(shop.shopId) == 0) { "Sync pending POS changes before closing a table bill." }
        val table = dao.getDiningTable(tableId) ?: throw IllegalStateException("This table is no longer available.")
        check(dao.openTableOrderCount(tableId) > 0) { "There is no open bill to close." }
        val amount = dao.openTableBillTotal(tableId)
        check(amount > 0L) { "There is no open bill to close." }
        val operation = newOutbox(
            "settle_table_bill",
            JSONObject().put("table_local_id", table.id).put("payment_method", method)
        )
        dao.enqueueOutboxAndLog(
            operation,
            newTransactionLog(operation, "Settle ${table.name} (${method.uppercase(Locale.ROOT)})", amountKsh = amount)
        )
        val syncedResults = scheduleAndAttemptSync(refreshSnapshot = false)
        val stillPending = dao.outboxOperationCount(operation.operationId) > 0
        val serverAmount = syncedResults[operation.operationId]?.optLong("amount", amount) ?: amount
        TableSettlementResult(serverAmount, synced = !stillPending)
    }

    suspend fun sellToTable(
        tableId: String,
        code: String,
        quantity: Int,
        unitName: String,
        unitPriceKsh: Int,
        selectedCounterId: String? = null,
        counterName: String? = null,
        waiterName: String? = null,
        paymentMethod: String = "cash"
    ): Boolean = withActiveProduct(code) { product ->
        require(quantity > 0 && unitPriceKsh > 0) { "Enter a valid sale quantity and price." }
        val shopId = activeBusiness?.shopId ?: throw IllegalStateException("Select an active business first.")
        val tableOperations = dao.pendingOutbox(shopId) + dao.blockedOutbox(shopId)
        val settlementPending = tableOperations.any { item ->
            item.operationType == "settle_table_bill" &&
                JSONObject(item.payloadJson).optString("table_local_id") == tableId
        }
        check(!settlementPending) { "This table bill is waiting for payment settlement to sync before more items can be added." }
        val counterId = selectedCounterId?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Choose the serving counter before adding items to a table.")
        val productUnits = parseSaleUnits(product.salesUnitsJson, product.sellingPriceKsh)
        val matchedUnit = productUnits.firstOrNull { it.name.equals(unitName, ignoreCase = true) }
        val unitsPerSale = matchedUnit?.stockUnitsPerSale ?: 1
        val stockToDecrement = quantity * unitsPerSale
        val createdAt = System.currentTimeMillis()
        val sale = SaleEntity(
            catalogCode = product.catalogCode,
            productName = product.name,
            imageAssetName = product.imageAssetName,
            unitName = unitName,
            quantity = quantity,
            unitPriceKsh = unitPriceKsh,
            totalKsh = unitPriceKsh * quantity,
            createdAt = createdAt,
            photoOverrideUri = product.photoOverrideUri,
            counterId = counterId,
            counterName = counterName ?: _serviceCounters.value.firstOrNull { it.id == counterId }?.name,
            waiterName = waiterName ?: account?.email?.substringBefore("@"),
            tableId = tableId,
            tableName = dao.getDiningTable(tableId)?.name,
            sourceCounterId = counterId,
            paymentMethod = paymentMethod
        )
        val resolvedCounterName = counterName ?: _serviceCounters.value.firstOrNull { it.id == counterId }?.name
        val resolvedWaiterName = waiterName ?: account?.email?.substringBefore("@")
        val order = TableOrderEntity(
            tableId = tableId,
            saleId = 0L,
            catalogCode = product.catalogCode,
            productName = product.name,
            unitName = unitName,
            quantity = quantity,
            unitPriceKsh = unitPriceKsh,
            totalKsh = unitPriceKsh * quantity,
            servedAt = createdAt,
            counterId = counterId,
            counterName = resolvedCounterName,
            waiterName = resolvedWaiterName
        )
        val payload = JSONObject()
            .put("table_local_id", tableId)
            .put("product_code", product.catalogCode)
            .put("counter_id", counterId)
            .put("quantity", quantity)
            .put("unit_name", unitName)
            .put("unit_price", unitPriceKsh)
            .put("payment_method", paymentMethod)
            .put("created_at", createdAt)
            .apply {
                if (resolvedCounterName != null) put("counter_name", resolvedCounterName)
                if (resolvedWaiterName != null) put("waiter_name", resolvedWaiterName)
            }
        val tableOutbox = newOutbox("table_sale", payload)
        val tableLabel = sale.tableName?.takeIf(String::isNotBlank) ?: "Table"
        val tableLog = newTransactionLog(
            outbox = tableOutbox,
            summary = "$tableLabel · ${quantity}x ${product.name} ($unitName)",
            amountKsh = sale.totalKsh.toLong()
        )
        val queued = dao.queueTableSaleAtCounter(
            order, sale, tableOutbox, counterId, stockToDecrement,
            activeBusiness!!.counterInventoryMode == "per_counter",
            tableLog
        ) ?: throw IllegalStateException("Not enough cached counter stock to complete this sale offline.")
        check(queued > 0L)
        triggerBackgroundSync()
        true
    }

    suspend fun sellWithUnit(
        code: String,
        quantity: Int,
        unitName: String,
        unitPriceKsh: Int,
        selectedCounterId: String? = null,
        counterName: String? = null,
        waiterName: String? = null,
        paymentMethod: String = "cash",
        tableId: String? = null,
        tableName: String? = null
    ): Boolean =
        withActiveProduct(code) { product ->
            require(quantity > 0 && unitPriceKsh > 0) { "Enter a valid sale quantity and price." }
            val counterId = selectedCounterId?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Choose the serving counter before recording a sale.")
            val productUnits = parseSaleUnits(product.salesUnitsJson, product.sellingPriceKsh)
            val matchedUnit = productUnits.firstOrNull { it.name.equals(unitName, ignoreCase = true) }
            val unitsPerSale = matchedUnit?.stockUnitsPerSale ?: 1
            val stockToDecrement = quantity * unitsPerSale
            val createdAt = System.currentTimeMillis()
            val sale = SaleEntity(
                catalogCode = product.catalogCode,
                productName = product.name,
                imageAssetName = product.imageAssetName,
                unitName = unitName,
                quantity = quantity,
                unitPriceKsh = unitPriceKsh,
                totalKsh = unitPriceKsh * quantity,
                createdAt = createdAt,
                photoOverrideUri = product.photoOverrideUri,
                counterId = counterId,
                counterName = counterName ?: _serviceCounters.value.firstOrNull { it.id == counterId }?.name,
                waiterName = waiterName ?: account?.email?.substringBefore("@"),
                tableId = tableId,
                tableName = tableName,
                sourceCounterId = counterId,
                paymentMethod = paymentMethod
            )
            val resolvedCounterName = counterName ?: _serviceCounters.value.firstOrNull { it.id == counterId }?.name
            val resolvedWaiterName = waiterName ?: account?.email?.substringBefore("@")
            val payload = JSONObject()
                .put("product_code", product.catalogCode)
                .put("counter_id", counterId)
                .put("quantity", quantity)
                .put("unit_name", unitName)
                .put("unit_price", unitPriceKsh)
                .put("payment_method", paymentMethod)
                .put("created_at", createdAt)
                .apply {
                    if (resolvedCounterName != null) put("counter_name", resolvedCounterName)
                    if (resolvedWaiterName != null) put("waiter_name", resolvedWaiterName)
                }
            val saleOutbox = newOutbox("sale", payload)
            val saleLog = newTransactionLog(
                outbox = saleOutbox,
                summary = "${quantity}x ${product.name} ($unitName) · ${paymentMethod.uppercase(Locale.ROOT)}",
                amountKsh = sale.totalKsh.toLong()
            )
            val queued = dao.queueSaleAtCounter(
                sale, saleOutbox, counterId, stockToDecrement,
                activeBusiness!!.counterInventoryMode == "per_counter",
                saleLog
            ) ?: throw IllegalStateException("Not enough cached counter stock to complete this sale offline.")
            check(queued > 0L)
            triggerBackgroundSync()
            true
        }

    suspend fun addFromLibrary(code: String, quantity: Int, priceKsh: Int, unitCostKsh: Int, selectedCounterId: String? = null): Boolean =
        withActiveBusiness {
            require(activeBusiness!!.role in setOf("owner", "manager", "inventory_clerk")) { "Only business owners and managers can add products to stock." }
            require(quantity > 0 && priceKsh > 0 && unitCostKsh >= 0) { "Enter valid stock quantity, price, and cost." }
            val product = dao.getProductByCode(code) ?: return@withActiveBusiness false
            val counterId = selectedCounterId?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Choose the receiving counter first.")
            val catalogId = product.remoteId
            if (catalogId.isNullOrBlank()) {
                return@withActiveBusiness registerNewProduct(
                    barcode = product.verifiedBarcode ?: code,
                    name = product.name,
                    category = product.category,
                    priceKsh = priceKsh,
                    initialStock = quantity,
                    unitCostKsh = unitCostKsh,
                    brand = product.brand,
                    size = product.size,
                    salesUnits = listOf(SaleUnit("Bottle", priceKsh)),
                    selectedCounterId = counterId
                )
            }
            val perCounter = activeBusiness!!.counterInventoryMode == "per_counter"
            val balance = if (perCounter) makeCounterBalance(product, counterId, quantity) else null
            val updated = product.copy(
                inMyStock = true,
                sellingPriceKsh = priceKsh,
                stockQuantity = if (perCounter) product.stockQuantity else product.stockQuantity + quantity,
                averageCostKsh = unitCostKsh
            )
            val payload = JSONObject()
                .put("product_code", code)
                .put("name", product.name)
                .put("brand", product.brand)
                .put("size", product.size)
                .put("category", product.category)
                .put("counter_id", counterId)
                .put("quantity", quantity)
                .put("price", priceKsh)
                .put("unit_cost", unitCostKsh)
                .put("catalog_id", catalogId)
            val addCatalogOutbox = newOutbox("add_catalog_product", payload)
            dao.queueProductChange(
                updated,
                addCatalogOutbox,
                balance,
                newTransactionLog(
                    outbox = addCatalogOutbox,
                    summary = "Add ${product.name} ($quantity units)",
                    amountKsh = priceKsh.toLong() * quantity
                )
            )
            triggerBackgroundSync()
            true
        }

    suspend fun restock(code: String, quantity: Int, unitCostKsh: Int, selectedCounterId: String? = null): Boolean =
        withActiveProduct(code) { product ->
            require(activeBusiness!!.role in setOf("owner", "manager", "inventory_clerk")) { "Only business owners and managers can restock inventory." }
            require(quantity > 0 && unitCostKsh >= 0) { "Enter a valid stock quantity and cost." }
            val counterId = selectedCounterId?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Choose the receiving counter first.")
            val perCounter = activeBusiness!!.counterInventoryMode == "per_counter"
            val existingQuantity = if (perCounter) dao.getCounterStockQuantity(counterId, code)
                ?: throw IllegalStateException("This counter has no saved stock snapshot. Connect once to load its inventory.") else product.stockQuantity
            val nextTotal = product.stockQuantity + quantity
            val nextAverageCost = if (nextTotal > 0) {
                (((product.averageCostKsh.toLong() * product.stockQuantity) + (unitCostKsh.toLong() * quantity)) / nextTotal).toInt()
            } else unitCostKsh
            val updated = product.copy(
                stockQuantity = if (perCounter) product.stockQuantity else nextTotal,
                averageCostKsh = nextAverageCost
            )
            val balance = if (perCounter) makeCounterBalance(product, counterId, existingQuantity + quantity) else null
            val payload = JSONObject()
                .put("product_code", code)
                .put("counter_id", counterId)
                .put("quantity", quantity)
                .put("unit_cost", unitCostKsh)
            val restockOutbox = newOutbox("receive_stock", payload)
            dao.queueProductChange(
                updated,
                restockOutbox,
                balance,
                newTransactionLog(
                    outbox = restockOutbox,
                    summary = "Restock +$quantity ${product.name}",
                    amountKsh = unitCostKsh.toLong() * quantity
                )
            )
            triggerBackgroundSync()
            true
        }

    suspend fun setStock(code: String, newStock: Int, selectedCounterId: String? = null): Boolean =
        withActiveProduct(code) { product ->
            require(activeBusiness!!.role in setOf("owner", "manager", "inventory_clerk")) { "Only business owners and managers can adjust stock." }
            require(newStock >= 0) { "Stock cannot be negative." }
            val counterId = selectedCounterId?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Choose a counter before changing its stock count.")
            val perCounter = activeBusiness!!.counterInventoryMode == "per_counter"
            if (perCounter && dao.getCounterStockQuantity(counterId, code) == null) {
                throw IllegalStateException("This counter has no saved stock snapshot. Connect once to load its inventory.")
            }
            val updated = if (perCounter) product else product.copy(stockQuantity = newStock)
            val balance = if (perCounter) makeCounterBalance(product, counterId, newStock) else null
            val payload = JSONObject()
                .put("product_code", code)
                .put("counter_id", counterId)
                .put("new_stock", newStock)
                .put("reason", "Stock count edited in mobile app")
            val adjustOutbox = newOutbox("adjust_stock", payload)
            dao.queueProductChange(
                updated,
                adjustOutbox,
                balance,
                newTransactionLog(adjustOutbox, "Adjust ${product.name} stock to $newStock")
            )
            triggerBackgroundSync()
            true
        }

    suspend fun updateProduct(product: ProductEntity, selectedCounterId: String? = null): Boolean =
        withActiveProduct(product.catalogCode) { current ->
            require(activeBusiness!!.role in setOf("owner", "manager", "inventory_clerk")) { "Only business owners and managers can edit products." }
            val counterId = selectedCounterId?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Choose a counter before changing this product.")
            val perCounter = activeBusiness!!.counterInventoryMode == "per_counter"
            val oldCount = if (perCounter) dao.getCounterStockQuantity(counterId, product.catalogCode)
                ?: throw IllegalStateException("This counter has no saved stock snapshot. Connect once to load its inventory.")
            else current.stockQuantity
            val targetCount = product.stockQuantity
            require(targetCount >= 0) { "Stock cannot be negative." }
            val stockChanged = targetCount != oldCount
            val photoChanged = product.photoOverrideUri != current.photoOverrideUri
            val updatedPhotoUri = when {
                !photoChanged -> current.photoOverrideUri
                product.photoOverrideUri.isNullOrBlank() -> null
                else -> {
                    require(activeBusiness!!.role in setOf("owner", "manager")) {
                        "Only owners and managers can change product photos."
                    }
                    persistProductPhoto(activeBusiness!!.shopId, product.catalogCode, product.photoOverrideUri)
                }
            }
            val localProduct = product.copy(
                remoteId = current.remoteId,
                inMyStock = current.inMyStock,
                stockQuantity = if (perCounter) current.stockQuantity else targetCount,
                photoOverrideUri = updatedPhotoUri
            )
            val fields = JSONObject()
                .put("product_code", product.catalogCode)
                .put("name", product.name)
                .put("price", product.sellingPriceKsh)
                .put("brand", product.brand)
                .put("size", product.size)
                .put("category", product.category)
                .put("sale_units", JSONArray(serializeSaleUnits(parseSaleUnits(product.salesUnitsJson, product.sellingPriceKsh))))
            if (!product.verifiedBarcode.isNullOrBlank()) fields.put("barcode", product.verifiedBarcode)
            val updateOp = newOutbox("update_product", fields)
            val operations = mutableListOf(updateOp)
            val logs = mutableListOf(newTransactionLog(updateOp, "Update ${product.name}"))
            if (stockChanged) {
                val stockOp = newOutbox(
                    "adjust_stock",
                    JSONObject()
                        .put("product_code", product.catalogCode)
                        .put("counter_id", counterId)
                        .put("new_stock", targetCount)
                        .put("reason", "Product edit stock count")
                )
                operations += stockOp
                logs += newTransactionLog(stockOp, "Adjust ${product.name} stock to $targetCount")
            }
            val balance = if (perCounter && stockChanged) makeCounterBalance(current, counterId, targetCount) else null
            dao.queueProductChanges(localProduct, operations, balance, logs)
            triggerBackgroundSync()
            true
        }

    suspend fun undoLatestSale(): Boolean = withActiveBusiness {
        var sale = dao.getLatestActiveSale() ?: return@withActiveBusiness false
        val perCounter = activeBusiness!!.counterInventoryMode == "per_counter"
        if (sale.remoteId.isNullOrBlank()) {
            val outbox = dao.outboxForSale(sale.id)
            if (outbox != null && inFlightOperationId.get() == outbox.operationId) {
                val activeDeferred = inFlightCompletion.get()
                if (activeDeferred != null) {
                    withTimeoutOrNull(2_500L) { activeDeferred.await() }
                }
                sale = dao.getSaleById(sale.id) ?: return@withActiveBusiness false
            }
            if (sale.remoteId.isNullOrBlank()) {
                val activeOutbox = dao.outboxForSale(sale.id)
                if (activeOutbox != null && inFlightOperationId.get() == activeOutbox.operationId) {
                    throw IllegalStateException("This sale is currently syncing to the server. Wait a second and tap Undo again.")
                }
                if (dao.cancelUnsentSale(sale, perCounter)) return@withActiveBusiness true
                throw IllegalStateException("This pending sale cannot be safely cancelled offline.")
            }
        }
        val payload = JSONObject()
            .put("sale_id", sale.remoteId)
            .put("product_code", sale.catalogCode)
            .put("counter_id", sale.sourceCounterId)
        val undoOutbox = newOutbox("undo_sale", payload)
        val undoLog = newTransactionLog(
            outbox = undoOutbox,
            summary = "Undo ${sale.quantity}x ${sale.productName}",
            amountKsh = sale.totalKsh.toLong()
        )
        if (!dao.queueUndoSale(sale, undoOutbox, perCounter, undoLog)) {
            throw IllegalStateException("The sale could not be undone using the saved stock state.")
        }
        triggerBackgroundSync()
        true
    }

    suspend fun updatePhoto(product: ProductEntity, uri: String): Boolean =
        withActiveProduct(product.catalogCode) { current ->
            val shop = activeBusiness!!
            require(shop.role in setOf("owner", "manager")) { "Only owners and managers can change product photos." }
            check(current.remoteId == product.remoteId && current.name == product.name) {
                "This product changed businesses. Reopen the active shop’s product list and try again."
            }
            val sourceUri = Uri.parse(uri)
            val mimeType = context.contentResolver.getType(sourceUri)?.lowercase(Locale.ROOT) ?: "image/jpeg"
            val extension = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                    ?: throw IOException("Could not read the selected photo. Choose the image again and retry.")
            }
            val remoteId = current.remoteId ?: throw IllegalStateException("This product is not synced yet. Sync the product before adding a photo.")
            remote.uploadProductPhoto(
                productId = remoteId,
                shopId = shop.shopId,
                productCode = product.catalogCode,
                bytes = bytes,
                contentType = mimeType,
                extension = extension
            )
            val storedUri = persistProductPhoto(shop.shopId, product.catalogCode, uri)
            dao.updatePhotoOverride(product.catalogCode, storedUri) == 1
        }

    private suspend fun persistProductPhoto(shopId: String, productCode: String, source: String): String =
        withContext(Dispatchers.IO) {
            val sourceUri = Uri.parse(source)
            val extension = when (context.contentResolver.getType(sourceUri)?.lowercase(Locale.ROOT)) {
                "image/jpeg", "image/jpg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "img"
            }
            val safeShopId = shopId.filter { it.isLetterOrDigit() || it == '-' }
            val safeProductCode = productCode.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)
            val photoDirectory = File(context.filesDir, "product-photos/$safeShopId")
            if (!photoDirectory.exists() && !photoDirectory.mkdirs()) {
                throw IOException("Could not save the selected product photo.")
            }
            val destination = File(photoDirectory, "${safeProductCode.ifBlank { "product" }}_${UUID.randomUUID()}.$extension")
            val input = context.contentResolver.openInputStream(sourceUri)
                ?: throw IOException("Could not read the selected photo. Choose the image again and retry.")
            try {
                input.use { sourceStream ->
                    destination.outputStream().use { targetStream -> sourceStream.copyTo(targetStream) }
                }
            } catch (error: Exception) {
                destination.delete()
                throw error
            }
            Uri.fromFile(destination).toString()
        }

    private fun localProductPhotosMap(shopId: String): Map<String, String> {
        val safeShopId = shopId.filter { it.isLetterOrDigit() || it == '-' }
        val directory = File(context.filesDir, "product-photos/$safeShopId")
        val files = directory.listFiles() ?: return emptyMap()
        val bestByPrefix = mutableMapOf<String, Pair<Long, String>>()
        for (file in files) {
            if (!file.isFile) continue
            val idx = file.name.lastIndexOf('_')
            if (idx <= 0) continue
            val prefix = file.name.substring(0, idx)
            val modified = file.lastModified()
            val prev = bestByPrefix[prefix]
            if (prev == null || modified > prev.first) {
                bestByPrefix[prefix] = modified to Uri.fromFile(file).toString()
            }
        }
        return bestByPrefix.mapValues { it.value.second }
    }

    suspend fun assignBarcode(code: String, barcode: String): Boolean =
        withActiveProduct(code) { product ->
            val clean = barcode.trim()
            require(clean.isNotBlank()) { "Enter a barcode." }
            val updated = product.copy(verifiedBarcode = clean)
            val barcodeOutbox = newOutbox("update_product", JSONObject().put("product_code", code).put("barcode", clean))
            dao.queueProductChange(
                updated,
                barcodeOutbox,
                log = newTransactionLog(barcodeOutbox, "Assign barcode to ${product.name}")
            )
            triggerBackgroundSync()
            true
        }

    suspend fun registerNewProduct(
        barcode: String,
        name: String,
        category: String,
        priceKsh: Int,
        initialStock: Int,
        unitCostKsh: Int,
        brand: String = "",
        size: String = "",
        salesUnits: List<SaleUnit> = emptyList(),
        selectedCounterId: String? = null,
        photoOverrideUri: String? = null
    ): Boolean = withActiveBusiness {
        require(activeBusiness!!.role in setOf("owner", "manager", "inventory_clerk")) { "Only business owners and managers can register products." }
        val bulkContainerProduct = salesUnits.isNotEmpty() && salesUnits.all { it.stockUnitsPerSale == 0 }
        require(name.trim().isNotBlank() && priceKsh > 0 && initialStock >= 0 && unitCostKsh >= 0) {
            "Enter a product name, price, and valid stock and cost values."
        }
        require(salesUnits.isNotEmpty() && salesUnits.all { it.name.isNotBlank() && it.priceKsh > 0 }) {
            "Add at least one sale unit with a valid name and price."
        }
        if (bulkContainerProduct) {
            require(initialStock == 0 && unitCostKsh > 0) {
                "Bulk products start with no opening stock; enter the drum or barrel buying price."
            }
        } else {
            require(initialStock > 0) { "Enter a positive opening stock for this product." }
        }
        val code = barcode.trim().ifBlank { "PROD_" + UUID.randomUUID().toString().take(8) }
        require(dao.getProductByCode(code) == null) { "A product with this barcode already exists." }
        val counterId = selectedCounterId?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Choose the receiving counter first.")
        val perCounter = activeBusiness!!.counterInventoryMode == "per_counter"
        val storedPhotoUri = photoOverrideUri?.takeIf(String::isNotBlank)?.let {
            persistProductPhoto(activeBusiness!!.shopId, code, it)
        }
        val product = ProductEntity(
            catalogCode = code,
            verifiedBarcode = barcode.trim().takeIf(String::isNotBlank),
            name = name.trim(),
            brand = brand,
            size = size,
            category = category.trim().ifBlank { "Beverage" },
            suggestedPriceKsh = priceKsh,
            sellingPriceKsh = priceKsh,
            stockQuantity = if (perCounter) 0 else initialStock,
            inMyStock = true,
            lowStockThreshold = activeBusiness!!.lowStockThreshold,
            imageAssetName = "product_stock_001",
            photoOverrideUri = storedPhotoUri,
            verificationNote = "Created on this device; awaiting sync",
            salesUnitsJson = serializeSaleUnits(salesUnits.ifEmpty { listOf(SaleUnit("Bottle", priceKsh)) }),
            remoteId = null,
            averageCostKsh = unitCostKsh
        )
        val payload = JSONObject()
            .put("product_code", code)
            .put("name", name.trim())
            .put("category", category.trim().ifBlank { "Beverage" })
            .put("price", priceKsh)
            .put("quantity", initialStock)
            .put("unit_cost", unitCostKsh)
            .put("brand", brand)
            .put("size", size)
            .put("sale_units", JSONArray(serializeSaleUnits(salesUnits)))
            .put("counter_id", counterId)
        val balance = if (perCounter) makeCounterBalance(product, counterId, initialStock) else null
        val registerOutbox = newOutbox("register_product", payload)
        dao.queueProductChange(
            product,
            registerOutbox,
            balance,
            newTransactionLog(
                outbox = registerOutbox,
                summary = "Register ${product.name} ($initialStock units)",
                amountKsh = priceKsh.toLong() * initialStock
            )
        )
        triggerBackgroundSync()
        true
    }

    suspend fun recordDispensation(
        code: String,
        quantity: Int,
        unitName: String,
        counterId: String,
        waiterName: String,
        tableId: String? = null,
        tableName: String? = null
    ): Boolean = withActiveProduct(code) { product ->
        require(activeBusiness!!.role in setOf("owner", "manager", "cashier")) {
            "Only business staff can record dispensations."
        }
        require(quantity > 0) { "Quantity must be greater than zero." }
        val stockToDecrement = when (unitName.lowercase(Locale.ROOT)) {
            "bottle", "can", "unit" -> quantity
            else -> 1
        }
        val note = buildString {
            append("Taken by ").append(waiterName.ifBlank { "staff" })
            tableName?.takeIf(String::isNotBlank)?.let { append(" at ").append(it) }
            tableId?.takeIf(String::isNotBlank)?.let { append(" (").append(it).append(")") }
        }
        val payload = JSONObject()
            .put("product_code", product.catalogCode)
            .put("counter_id", counterId)
            .put("quantity", quantity)
            .put("unit_name", unitName)
            .put("note", note)
        val dispensationOutbox = newOutbox("dispensation", payload)
        val queued = dao.queueDispensationAtCounter(
            dispensationOutbox,
            product.catalogCode,
            counterId,
            stockToDecrement,
            activeBusiness!!.counterInventoryMode == "per_counter",
            newTransactionLog(
                outbox = dispensationOutbox,
                summary = "Dispense ${quantity}x ${product.name} ($unitName)"
            )
        )
        check(queued) { "Not enough stock is available at this counter." }
        triggerBackgroundSync()
        true
    }

    private suspend fun <T> withActiveBusiness(block: suspend () -> T): T {
        check(activeBusiness != null) { "Select an active business first." }
        return block()
    }

    private suspend fun <T> withActiveProduct(code: String, block: suspend (ProductEntity) -> T): T {
        return withActiveBusiness {
            val product = dao.getProductByCode(code)
                ?: throw IllegalArgumentException("Product not found")
            val awaitingCreation = product.remoteId.isNullOrBlank() && dao.pendingOutbox(activeBusiness!!.shopId).any { item ->
                item.operationType in setOf("register_product", "add_catalog_product") &&
                    JSONObject(item.payloadJson).optString("product_code") == product.catalogCode
            }
            if ((product.remoteId.isNullOrBlank() && !awaitingCreation) || !product.inMyStock) {
                throw IllegalStateException("Product is not in the active business stock")
            }
            block(product)
        }
    }

    suspend fun syncPendingOutbox(
        refreshSnapshot: Boolean = true,
        includeCatalog: Boolean = (cachedCatalogRows == null)
    ): Map<String, JSONObject> = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: return@withContext emptyMap()
        val userId = account?.userId?.takeIf(String::isNotBlank) ?: return@withContext emptyMap()
        syncMutex.withLock {
            _isOutboxSyncing.value = true
            try {
                val syncedResults = mutableMapOf<String, JSONObject>()
                val skippedOperationIds = mutableSetOf<String>()
                dao.resetStaleSyncingTransactionLogs(shop.shopId, System.currentTimeMillis())
                val maxIterations = (dao.outboxCount(shop.shopId) + 32).coerceAtLeast(32)
                var iterations = 0
                while (iterations < maxIterations) {
                    iterations++
                    // Yield between queued operations so Room WAL UI reads and POS writes execute first.
                    yield()
                    val candidate = dao.pendingOutbox(shop.shopId)
                        .firstOrNull { it.operationId !in skippedOperationIds }
                        ?: break
                    val item = dao.claimPendingOutboxForSync(candidate.operationId, System.currentTimeMillis())
                    if (item == null) {
                        skippedOperationIds += candidate.operationId
                        continue
                    }
                    val dependencyError = findBlockedDependencyMessage(shop.shopId, item)
                    if (dependencyError != null) {
                        dao.recordOutboxFailureRetry(item.operationId, dependencyError, System.currentTimeMillis())
                        skippedOperationIds += item.operationId
                        continue
                    }
                    val completion = CompletableDeferred<Unit>()
                    inFlightOperationId.set(item.operationId)
                    inFlightCompletion.set(completion)
                    try {
                        val payload = prepareOutboxPayload(item)
                        val result = when (item.operationType) {
                            "dispensation" -> remote.recordDispensationFromCounter(
                                shopId = item.shopId,
                                productId = payload.optString("product_id"),
                                counterId = payload.optString("counter_id"),
                                quantity = payload.optInt("qty", 1),
                                unitName = payload.optString("unit_name", "Bottle"),
                                note = payload.optString("note").takeIf(String::isNotBlank)
                            )
                            "settle_table_bill" -> remote.settleTableBill(
                                item.shopId,
                                payload.optString("table_id"),
                                item.operationId,
                                payload.optString("payment_method")
                            )
                            "create_dining_table" -> remote.createDiningTable(
                                item.shopId,
                                payload.optString("name")
                            )
                            "delete_dining_table" -> remote.deleteDiningTable(
                                item.shopId,
                                payload.optString("table_id")
                            )
                            else -> remote.applyPosOperation(item.shopId, item.operationId, item.operationType, payload)
                        }
                        reconcileOutboxResult(item, JSONObject(item.payloadJson), result)
                        val now = System.currentTimeMillis()
                        dao.completeOutboxSync(item.operationId, now)
                        lastOutboxPushAt.set(now)
                        syncedResults[item.operationId] = result
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        val now = System.currentTimeMillis()
                        if (error is SupabaseUnauthorizedException) {
                            dao.recordOutboxFailureRetry(item.operationId, error.message, now)
                            _session.value = _session.value.copy(
                                message = "Saved POS changes are waiting for this account to sign in again."
                            )
                            throw error
                        }
                        if (error is SupabaseHttpException && !isRetryable(error)) {
                            val message = error.message.orEmpty()
                            val entitlementDenied = message.contains("subscription_expired", ignoreCase = true) ||
                                message.contains("subscription expired", ignoreCase = true) ||
                                message.contains("payment lock", ignoreCase = true) ||
                                message.contains("payment_locked", ignoreCase = true) ||
                                activeBusiness?.let { !SubscriptionManager.evaluate(it, isOnline = true).isEntitled } == true
                            if (entitlementDenied) {
                                dao.recordOutboxFailureRetry(item.operationId, error.message, now)
                                _session.value = _session.value.copy(
                                    message = "Subscription access is required to sync saved POS changes. Your local queue is preserved; it will retry after access is restored."
                                )
                                break
                            }
                            dao.blockOutboxAndLog(item.operationId, error.message ?: "The server rejected this saved change.", now)
                            _session.value = _session.value.copy(
                                message = "A saved POS change needs attention before it can sync: ${error.message.orEmpty()}"
                            )
                            skippedOperationIds += item.operationId
                            continue
                        }
                        if (error is IllegalArgumentException || error is JSONException) {
                            dao.blockOutboxAndLog(item.operationId, error.message ?: "Malformed offline POS payload.", now)
                            skippedOperationIds += item.operationId
                            continue
                        }
                        dao.recordOutboxFailureRetry(item.operationId, error.message, now)
                        if (error is IOException) throw error
                        throw IOException("Could not sync a saved POS change.", error)
                    } finally {
                        inFlightOperationId.compareAndSet(item.operationId, null)
                        inFlightCompletion.compareAndSet(completion, null)
                        completion.complete(Unit)
                    }
                }
                if (syncedResults.isNotEmpty()) {
                    runCatching { dao.pruneOldSyncedTransactionLogs(shop.shopId) }
                }
                if (refreshSnapshot && dao.outboxCount(shop.shopId) == 0) {
                    yield()
                    runCatching { syncBusiness(shop, includeCatalog = includeCatalog) }
                        .onFailure { error -> if (isRetryable(error)) _session.value = _session.value.copy(message = offlineMessage()) }
                }
                syncedResults
            } finally {
                _isOutboxSyncing.value = false
            }
        }
    }

    private suspend fun findBlockedDependencyMessage(shopId: String, item: OutboxEntity): String? {
        val blocked = dao.blockedOutbox(shopId)
        if (blocked.isEmpty()) return null
        val local = runCatching { JSONObject(item.payloadJson) }.getOrNull() ?: return null
        val productCode = local.optString("product_code").takeIf(String::isNotBlank)
        if (productCode != null) {
            val productBlocked = blocked.any { b ->
                b.operationType in setOf("register_product", "add_catalog_product") &&
                    runCatching { JSONObject(b.payloadJson).optString("product_code") == productCode }.getOrDefault(false)
            }
            if (productBlocked) return "Waiting for blocked product creation to be resolved first."
        }
        val tableLocalId = local.optString("table_local_id").takeIf(String::isNotBlank)
        if (tableLocalId != null) {
            val tableBlocked = blocked.any { b ->
                b.operationType == "create_dining_table" &&
                    runCatching { JSONObject(b.payloadJson).optString("local_table_id") == tableLocalId }.getOrDefault(false)
            }
            if (tableBlocked) return "Waiting for blocked table creation to be resolved first."
        }
        return null
    }

    internal suspend fun prepareOutboxPayload(item: OutboxEntity): JSONObject {
        val local = JSONObject(item.payloadJson)
        suspend fun productId(): String {
            val code = local.optString("product_code")
            val product = dao.getProductByCode(code)
            return product?.remoteId?.takeIf(String::isNotBlank)
                ?: throw IOException("Waiting for the queued product creation to sync first.")
        }
        suspend fun coreSaleFields(): JSONObject = JSONObject()
            .put("product_id", productId())
            .put("counter_id", local.optString("counter_id"))
            .put("qty", local.optInt("quantity", 1))
            .put("unit_name", local.optString("unit_name", "Bottle"))
            .put("unit_price", local.optInt("unit_price", 0))
            .put("payment_method", local.optString("payment_method", "cash").ifBlank { "cash" })
            .put("client_created_at", local.optLong("created_at", item.createdAt))
            .apply {
                local.optString("counter_name").takeIf(String::isNotBlank)?.let { put("counter_name", it) }
                local.optString("waiter_name").takeIf(String::isNotBlank)?.let { put("waiter_name", it) }
            }
        return when (item.operationType) {
            "create_dining_table" -> JSONObject().put("name", local.optString("name")).put("local_table_id", local.optString("local_table_id"))
            "delete_dining_table" -> {
                val table = dao.getDiningTable(local.optString("local_table_id"))
                val tableId = table?.remoteId?.takeIf(String::isNotBlank) ?: local.optString("table_id")
                JSONObject().put("table_id", tableId).put("local_table_id", local.optString("local_table_id"))
            }
            "settle_table_bill" -> {
                val table = dao.getDiningTable(local.optString("table_local_id"))
                    ?: throw IOException("Waiting for the queued table to be available.")
                val tableId = table.remoteId?.takeIf(String::isNotBlank)
                    ?: throw IOException("Waiting for the queued table creation to sync first.")
                JSONObject().put("table_id", tableId).put("payment_method", local.optString("payment_method"))
            }
            "dispensation" -> JSONObject()
                .put("product_id", productId())
                .put("counter_id", local.optString("counter_id"))
                .put("qty", local.optInt("quantity", 1))
                .put("unit_name", local.optString("unit_name", "Bottle"))
                .put("note", local.optString("note"))
            "sale" -> coreSaleFields()
            "table_sale" -> {
                val table = dao.getDiningTable(local.optString("table_local_id"))
                    ?: throw IOException("Waiting for the queued table to be available.")
                val tableId = table.remoteId?.takeIf(String::isNotBlank)
                    ?: throw IOException("Waiting for the queued table creation to sync first.")
                coreSaleFields().put("table_id", tableId)
            }
            "receive_stock" -> JSONObject()
                .put("counter_id", local.optString("counter_id"))
                .put("product_id", productId())
                .put("qty", local.optInt("quantity"))
                .put("unit_cost", local.optInt("unit_cost"))
            "add_catalog_product" -> {
                val code = local.optString("product_code")
                val product = if (code.isNotBlank()) dao.getProductByCode(code) else null
                JSONObject()
                    .put("catalog_id", local.optString("catalog_id").ifBlank { product?.remoteId.orEmpty() })
                    .put("product_code", code)
                    .put("counter_id", local.optString("counter_id"))
                    .put("qty", local.optInt("quantity", local.optInt("qty", 1)))
                    .put("price", local.optInt("price"))
                    .put("unit_cost", local.optInt("unit_cost"))
                    .put("name", product?.name.orEmpty())
                    .put("brand", product?.brand.orEmpty())
                    .put("size", product?.size.orEmpty())
                    .put("category", product?.category ?: "Beverage")
            }
            "register_product" -> JSONObject()
                .put("code", local.optString("product_code"))
                .put("name", local.optString("name"))
                .put("price", local.optInt("price"))
                .put("qty", local.optInt("quantity"))
                .put("counter_id", local.optString("counter_id"))
                .put("photo_path", JSONObject.NULL)
                .put("unit_cost", local.optInt("unit_cost"))
                .put("size", local.optString("size"))
                .put("type", "beer")
                .put("brand", local.optString("brand"))
                .put("category", local.optString("category"))
                .put("sale_units", local.optJSONArray("sale_units") ?: JSONArray())
            "adjust_stock" -> JSONObject()
                .put("counter_id", local.optString("counter_id"))
                .put("product_id", productId())
                .put("new_stock", local.optInt("new_stock"))
                .put("reason", local.optString("reason"))
            "undo_sale" -> JSONObject().put("sale_id", local.optString("sale_id"))
            "transfer_stock" -> JSONObject()
                .put("product_id", productId())
                .put("from_counter_id", local.optString("from_counter_id"))
                .put("to_counter_id", local.optString("to_counter_id"))
                .put("qty", local.optInt("quantity"))
                .put("reason", local.optString("reason"))
            "update_product" -> {
                val fields = JSONObject()
                listOf("name", "brand", "size", "category", "price", "barcode", "sale_units").forEach { key ->
                    if (local.has(key)) fields.put(key, local.opt(key))
                }
                val patch = local.optJSONObject("patch") ?: fields
                JSONObject().put("product_id", productId()).put("patch", patch)
            }
            else -> throw IllegalArgumentException("Unsupported offline operation: ${item.operationType}")
        }
    }

    private suspend fun reconcileOutboxResult(item: OutboxEntity, payload: JSONObject, result: JSONObject) {
        val returnedSaleId = result.optString("sale_id").takeIf(String::isNotBlank)
            ?: result.optString("id").takeIf(String::isNotBlank)
        if (item.localSaleId != null && returnedSaleId != null) {
            dao.setSaleRemoteId(item.localSaleId, returnedSaleId)
        }
        when (item.operationType) {
            "create_dining_table" -> {
                val localId = payload.optString("local_table_id")
                val remoteId = result.optString("table_id").takeIf(String::isNotBlank)
                    ?: result.optString("id").takeIf(String::isNotBlank)
                if (localId.isNotBlank() && remoteId != null) dao.setDiningTableRemoteId(localId, remoteId)
            }
            "delete_dining_table" -> {
                val localId = payload.optString("local_table_id")
                if (localId.isNotBlank()) {
                    dao.deleteTableOrdersForTable(localId)
                    dao.deleteDiningTable(localId)
                }
            }
            "settle_table_bill" -> {
                val tableId = payload.optString("table_local_id")
                val settlementId = result.optString("settlement_id").takeIf(String::isNotBlank)
                    ?: throw IllegalStateException("The server did not confirm the table settlement.")
                dao.markTableOrdersSettled(tableId, settlementId)
            }
            "register_product", "add_catalog_product" -> {
                val code = payload.optString("product_code")
                val remoteId = result.optString("product_id").takeIf(String::isNotBlank)
                    ?: result.optString("id").takeIf(String::isNotBlank)
                if (code.isNotBlank() && remoteId != null) {
                    dao.setProductRemoteId(code, remoteId)
                    dao.setCounterStockProductId(code, remoteId)
                }
            }
        }
    }

    private suspend fun makeCounterBalance(product: ProductEntity, counterId: String, quantity: Int) =
        com.alvinb.counterpos.data.local.entity.CounterStockEntity(
            shopId = activeBusiness?.shopId ?: throw IllegalStateException("Select an active business first."),
            counterId = counterId,
            catalogCode = product.catalogCode,
            productId = product.remoteId.orEmpty(),
            quantity = quantity
        )

    private fun newOutbox(kind: String, payload: JSONObject): OutboxEntity {
        val shop = activeBusiness ?: throw IllegalStateException("Select an active business first.")
        val userId = account?.userId?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Sign in before recording business changes.")
        return OutboxEntity(
            operationId = UUID.randomUUID().toString(),
            shopId = shop.shopId,
            actorUserId = userId,
            operationType = kind,
            payloadJson = payload.toString(),
            createdAt = outboxClock.updateAndGet { previous -> maxOf(System.currentTimeMillis(), previous + 1L) }
        )
    }

    private fun newTransactionLog(
        outbox: OutboxEntity,
        summary: String,
        amountKsh: Long = 0L,
        localSaleId: Long? = outbox.localSaleId
    ): OfflineTransactionLogEntity = OfflineTransactionLogEntity(
        operationId = outbox.operationId,
        shopId = outbox.shopId,
        actorUserId = outbox.actorUserId,
        operationType = outbox.operationType,
        summary = summary,
        amountKsh = amountKsh,
        status = OfflineTransactionStatus.QUEUED,
        recordedOffline = !OfflineOutboxWorker.isStableInternetConnected(context),
        attempts = 0,
        lastError = null,
        localSaleId = localSaleId,
        createdAt = outbox.createdAt,
        updatedAt = outbox.createdAt,
        syncedAt = null
    )

    private fun triggerBackgroundSync(triggerSource: String = "pos_mutation") {
        // Delegate background queue flushing exclusively to constrained WorkManager so UI
        // writes and Room reads stay instant without spawning competing foreground sync jobs.
        OfflineOutboxWorker.enqueueOutboxFlush(context, triggerSource = triggerSource)
    }

    private suspend fun scheduleAndAttemptSync(refreshSnapshot: Boolean = false): Map<String, JSONObject> {
        OfflineOutboxWorker.enqueueOutboxFlush(context, triggerSource = "settle_table_bill")
        try {
            return syncPendingOutbox(refreshSnapshot = refreshSnapshot)
        } catch (error: Exception) {
            if (!isRetryable(error)) throw error
        }
        return emptyMap()
    }

    private suspend fun clearLocalSession() {
        realtime.stop()
        realtimeRefreshJob?.cancel()
        startupSyncJob?.cancel()
        cachedCatalogRows = null
        lastSyncCompletedAt.set(0L)
        check(dao.outboxCountAll() == 0) { "Cannot clear local POS data while changes are waiting to sync." }
        activeBusiness = null
        account = null
        _serviceCounters.value = emptyList()
        // Keep the last account/business snapshot and inventory on-device. The
        // next successful sign-in validates its user and shop before reusing it;
        // this also allows a brief offline login without showing another tenant.
        remote.clearSession()
        _session.value = SessionSnapshot()
    }

    private suspend fun clearBusinessSnapshot() {
        cachedCatalogRows = null
        lastSyncCompletedAt.set(0L)
        dao.clearTenantCache()
        dao.clearAllServiceCounters()
        dao.clearAllCounterStock()
        _serviceCounters.value = emptyList()
    }

    private fun stableLocalId(remoteId: String): Long {
        val value = abs(remoteId.hashCode().toLong())
        return if (value == 0L) 1L else value
    }

    private fun parseTimestamp(value: String): Long = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).parse(value)?.time
    }.getOrNull() ?: runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(value)?.time
    }.getOrNull() ?: Date().time
}

private data class Tuple6<A, B, C, D, E, F>(
    val a: A,
    val b: B,
    val c: C,
    val d: D,
    val e: E,
    val f: F
)

private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
