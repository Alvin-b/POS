package com.alvinb.counterpos.data

import android.content.Context
import android.content.res.AssetManager
import com.alvinb.counterpos.OfflineOutboxWorker
import com.alvinb.counterpos.BuildConfig
import com.alvinb.counterpos.data.local.dao.CounterPosDao
import com.alvinb.counterpos.data.local.entity.DiningTableEntity
import com.alvinb.counterpos.data.local.entity.OfflineSessionEntity
import com.alvinb.counterpos.data.local.entity.OutboxEntity
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.local.entity.SaleUnit
import com.alvinb.counterpos.data.local.entity.TableOrderEntity
import com.alvinb.counterpos.data.local.entity.serializeSaleUnits
import com.alvinb.counterpos.data.model.BusinessHours
import com.alvinb.counterpos.data.model.TimeSlot
import com.alvinb.counterpos.data.remote.AccountSnapshot
import com.alvinb.counterpos.data.remote.AdminAuditEvent
import com.alvinb.counterpos.data.remote.AdminBusiness
import com.alvinb.counterpos.data.remote.AuthSession
import com.alvinb.counterpos.data.remote.BusinessMembership
import com.alvinb.counterpos.data.remote.CounterReportRow
import com.alvinb.counterpos.data.remote.ServiceCounter
import com.alvinb.counterpos.data.remote.SupabaseHttpException
import com.alvinb.counterpos.data.remote.SupabaseRestClient
import com.alvinb.counterpos.data.remote.SupabaseRealtimeClient
import com.alvinb.counterpos.data.remote.SupabaseUnauthorizedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
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
    val sales: Flow<List<SaleEntity>> = dao.observeSales()
    val diningTables: Flow<List<DiningTableEntity>> = dao.observeDiningTables()
    val tableOrders: Flow<List<TableOrderEntity>> = dao.observeTableOrders()
    val pendingSyncCount: Flow<Int> = dao.observePendingOutboxCount()
    private val _serviceCounters = MutableStateFlow<List<ServiceCounter>>(emptyList())
    val serviceCounters = _serviceCounters.asStateFlow()

    private val _session = MutableStateFlow(SessionSnapshot())
    val session = _session.asStateFlow()
    private var account: AccountSnapshot? = null
    private var activeBusiness: BusinessMembership? = null
    private val syncMutex = Mutex()
    private val outboxClock = AtomicLong(0L)
    private val realtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var realtimeRefreshJob: Job? = null
    private val realtime = SupabaseRealtimeClient(
        baseUrl = BuildConfig.SUPABASE_URL,
        publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        accessToken = remote::currentAccessToken,
        onDataChanged = ::scheduleRealtimeRefresh
    )

    private fun startRealtimeFor(membership: BusinessMembership) {
        realtime.start(membership.shopId)
    }

    private fun scheduleRealtimeRefresh() {
        realtimeRefreshJob?.cancel()
        realtimeRefreshJob = realtimeScope.launch {
            delay(250)
            if (activeBusiness != null) runCatching { syncPendingOutbox() }
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
        check(item.actorUserId == userId) { "Sign in with the account that created this change before retrying it." }
        check(dao.unblockOutboxItem(operationId) == 1) { "Could not retry this saved change." }
        syncPendingOutbox()
        val stillBlocked = dao.blockedOutbox(shop.shopId).firstOrNull { it.operationId == operationId }
        if (stillBlocked != null) {
            throw IllegalStateException(stillBlocked.lastError ?: "The server still cannot accept this saved change.")
        }
    }

    suspend fun restoreSession(): SessionSnapshot = withContext(Dispatchers.IO) {
        if (remote.restoreSession() == null) {
            if (dao.outboxCountAll() > 0) {
                _session.value = SessionSnapshot(
                    message = "Saved POS changes are waiting. Sign in with the same account to sync them; the changes remain stored on this phone."
                )
                return@withContext _session.value
            }
            clearLocalSession()
            return@withContext _session.value
        }
        loadAccount(autoSelectSingleBusiness = true)
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

    suspend fun createBusiness(name: String): SessionSnapshot = withContext(Dispatchers.IO) {
        require(name.trim().isNotBlank()) { "Enter a business name." }
        remote.createShop(name.trim())
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

    suspend fun synchronizeWhenOnline(): SessionSnapshot = withContext(Dispatchers.IO) {
        if (remote.restoreSession() == null || activeBusiness == null) return@withContext _session.value
        syncPendingOutbox(refreshSnapshot = false)
        loadAccount(autoSelectSingleBusiness = false)
    }

    /** Refresh the persisted auth session even when no business workspace is open. */
    suspend fun keepSessionAlive(): Boolean = withContext(Dispatchers.IO) {
        remote.restoreSession() != null
    }

    suspend fun canOperateNow(): Boolean = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: return@withContext true
        if (account?.isPlatformAdmin == true || shop.role.equals("owner", ignoreCase = true)) {
            return@withContext true
        }
        remote.canSellForShop(shop.shopId)
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val pending = dao.outboxCountAll() + dao.getPendingRemoteSales().size
        check(pending == 0) { "Connect to the internet so the pending POS changes can sync before signing out." }
        clearLocalSession()
    }

    suspend fun adminBusinesses(): List<AdminBusiness> = remote.adminBusinesses()

    suspend fun adminAuditEvents(shopId: String = ""): List<AdminAuditEvent> = remote.auditEvents(shopId)

    suspend fun counterSalesReport(fromIso: String, toIso: String): List<CounterReportRow> = withActiveBusiness {
        remote.counterSalesReport(activeBusiness!!.shopId, fromIso, toIso)
    }

    suspend fun createServiceCounter(name: String): ServiceCounter = withActiveBusiness {
        remote.createServiceCounter(activeBusiness!!.shopId, name).also { _serviceCounters.value = _serviceCounters.value + it }
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
        remote.renameServiceCounter(activeBusiness!!.shopId, counterId, name)
        remote.serviceCounters(activeBusiness!!.shopId).also { _serviceCounters.value = it }
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
            val queued = dao.queueCounterTransfer(newOutbox("transfer_stock", payload), fromCounter, target, quantity)
            if (!queued) throw IllegalStateException("Not enough saved stock at the source counter to transfer.")
            check(source >= quantity)
            scheduleAndAttemptSync()
            true
        }

    suspend fun adminSetBusinessStatus(shopId: String, status: String, reason: String?): List<AdminBusiness> {
        remote.adminSetBusinessStatus(shopId, status, reason)
        return remote.adminBusinesses()
    }

    suspend fun adminCreateBusiness(
        name: String,
        ownerEmail: String,
        businessType: String,
        businessSize: String,
        inventoryMode: String,
        counterCount: Int
    ): AdminBusinessCreationResult {
        val createdRow = remote.adminCreateBusiness(name, ownerEmail.takeIf { it.isNotBlank() }, businessType, businessSize, inventoryMode, counterCount)
        val createdShopId = createdRow.optString("id").takeIf(String::isNotBlank)
        return AdminBusinessCreationResult(createdShopId, remote.adminBusinesses())
    }

    suspend fun addMemberByEmail(shopId: String, email: String, role: String): List<AdminBusiness> {
        require(role in setOf("manager", "inventory_clerk", "cashier")) { "Choose a valid staff role." }
        remote.addMemberByEmail(shopId, email, role)
        return remote.adminBusinesses()
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

    private fun createAdminBusinessMembership(business: AdminBusiness): BusinessMembership {
        val liquor = business.businessType == "counter_seller" || business.businessType == "liquor_store"
        val featureFlags = if (liquor) {
            setOf("employee_management", "working_hours", "fifo_inventory", "sales_analytics")
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
            lowStockThreshold = 5
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
            if (!remote.hasSession || !isRetryable(error)) throw error
            val cached = saved?.let(::parseOfflineAccount) ?: throw error
            val userShopIds = cached.memberships.map { m -> m.shopId }
            if (remote.currentUserId != cached.userId && (cached.memberships.isEmpty() || !cached.memberships.any { m -> m.shopId == saved?.activeShopId })) {
                clearBusinessSnapshot()
                dao.clearOfflineSession()
                saved = null
                activeBusiness = null
                account = null
                throw error
            }
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
                if (eligibleCachedMemberships.size == 1) activeBusiness = eligibleCachedMemberships.single()
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
        if (autoSelectSingleBusiness && activeBusiness == null && activeMemberships.size == 1) {
            activeBusiness = activeMemberships.single()
        } else if (activeBusiness != null) {
            val refreshed = activeMemberships.firstOrNull { it.shopId == activeBusiness?.shopId }
            if (refreshed != null) {
                activeBusiness = refreshed
            } else {
                activeBusiness = null
                dao.clearTenantCache()
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
            if (dao.outboxCount(selected.shopId) > 0) scheduleAndAttemptSync()
        }
        saveOfflineAccount(account!!, activeBusiness?.shopId)
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
                    .put("feature_flags", JSONArray(membership.featureFlags.toList()))
                    .put("currency", membership.currency)
                    .put("low_stock_threshold", membership.lowStockThreshold)
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
                        featureFlags = (0 until flags.length()).mapNotNull { flags.optString(it).takeIf(String::isNotBlank) }.toSet(),
                        currency = row.optString("currency", "KES"),
                        lowStockThreshold = row.optInt("low_stock_threshold", 5)
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
        is SupabaseUnauthorizedException -> false
        is SupabaseHttpException -> error.statusCode == 404 || error.statusCode == 408 || error.statusCode == 429 || error.statusCode >= 500
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

    private suspend fun syncBusiness(membership: BusinessMembership) {
        if (dao.outboxCount(membership.shopId) > 0) {
            restoreCachedCounters(membership.shopId)
            runCatching { syncPendingOutbox(refreshSnapshot = false) }
            if (dao.outboxCount(membership.shopId) > 0) return
        }
        seedCatalogIfEmpty()
        val bundledCatalogFallbacks = dao.getAllProducts().filter { it.remoteId == null && !it.inMyStock }
        val counters = remote.serviceCounters(membership.shopId)
        runCatching { uploadPendingLocalSales() }
        val hasPendingSales = dao.getPendingRemoteSales().isNotEmpty()

        val catalogRows = remote.catalog()
        val productRows = remote.products(membership.shopId)
        val saleRows = remote.sales(membership.shopId)
        val tableRows = remote.diningTables(membership.shopId)
        val tableOrderRows = remote.tableOrders(membership.shopId)
        val existingTables = dao.getDiningTables()
        val counterStockSnapshots = counters.associate { it.id to runCatching { remote.counterStock(membership.shopId, it.id) }.getOrDefault(emptyMap()) }

        val bundledMetadataByCode = mutableMapOf<String, BundledProductMetadata>()
        val bundledMetadataByName = mutableMapOf<String, BundledProductMetadata>()
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
                row.optString("catalogCode").takeIf(String::isNotBlank)?.let { bundledMetadataByCode[it] = metadata }
                row.optString("name").trim().lowercase(Locale.ROOT).takeIf(String::isNotBlank)?.let { bundledMetadataByName[it] = metadata }
            }
        }
        fun metadataFor(row: JSONObject): BundledProductMetadata? {
            val code = row.optString("code").ifBlank { row.optString("catalog_code") }
            return bundledMetadataByCode[code]
                ?: bundledMetadataByName[row.optString("name").trim().lowercase(Locale.ROOT)]
        }

        // Remote catalog rows augment the bundled image fallback. The remote
        // product snapshot then replaces matching catalog rows with tenant data.
        val catalogProducts = buildList {
            for (index in 0 until catalogRows.length()) {
                catalogRows.optJSONObject(index)?.let { row ->
                    val code = row.optString("code").ifBlank { row.optString("catalog_code") }
                    val bundled = metadataFor(row)
                    val fallback = bundled?.imageAssetName ?: dao.getProductByCode(code)?.imageAssetName ?: "product_stock_001"
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
                    val remoteId = row.optString("id").takeIf(String::isNotBlank)
                    val bundled = metadataFor(row)
                    val fallback = bundled?.imageAssetName ?: dao.getProductByCode(code)?.imageAssetName ?: "product_stock_001"
                    val imageUrl = row.optNullableString("photo_url") ?: bundled?.imageSourceUrl
                    val rawStock = row.optInt("stock", row.optInt("quantity", row.optInt("stock_quantity", row.optInt("current_stock", 0))))
                    val hasCounterBalance = remoteId != null && counterStockSnapshots.values.any { it.containsKey(remoteId) }
                    val counterStockTotal = if (remoteId != null) counterStockSnapshots.values.sumOf { it[remoteId] ?: 0 } else 0
                    // In per-counter mode the balance rows are the authoritative
                    // tenant inventory, including a legitimate total of zero.
                    // Falling back only when no balance row exists prevents a
                    // stale products.stock value from restoring the wrong stock
                    // after logout/login or after a counter is sold out.
                    val finalStock = if (membership.counterInventoryMode == "per_counter" && hasCounterBalance) {
                        counterStockTotal
                    } else {
                        rawStock
                    }
                    val price = row.optInt("price", row.optInt("selling_price", 0))
                    val cost = row.optInt("average_cost", row.optInt("unit_cost", 0))
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
                            photoOverrideUri = row.optNullableString("photo_url"),
                            productSourceUrl = row.optNullableString("product_source_url") ?: bundled?.productSourceUrl,
                            imageSourceUrl = imageUrl,
                            verificationNote = "Business inventory",
                            salesUnitsJson = serializeSaleUnits(listOf(SaleUnit("Bottle", price))),
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
                            tableName = tableName
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
            bundledCatalogFallbacks.forEach { put(it.catalogCode, it) }
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
                unitPrice = sale.unitPriceKsh
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
            require(dao.getDiningTableByName(cleanName) == null) { "A table with this name already exists." }
            val table = DiningTableEntity(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                createdAt = System.currentTimeMillis(),
                remoteId = null
            )
            dao.queueDiningTable(
                table,
                newOutbox("create_dining_table", JSONObject().put("local_table_id", table.id).put("name", cleanName))
            )
            scheduleAndAttemptSync()
            table
        }
    }

    suspend fun deleteDiningTable(tableId: String): Boolean = withActiveBusiness {
        require(activeBusiness!!.role in setOf("owner", "manager", "cashier")) {
            "Only business staff can delete a table."
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
            dao.enqueueOutbox(newOutbox("delete_dining_table", payload))
            scheduleAndAttemptSync()
        }
        true
    }

    suspend fun settleTableBill(tableId: String, paymentMethod: String): TableSettlementResult = withActiveBusiness {
        val method = paymentMethod.trim().lowercase(Locale.ROOT)
        require(method in setOf("cash", "mpesa", "card", "other")) { "Choose a valid payment method." }
        val shop = activeBusiness ?: throw IllegalStateException("Select an active business first.")
        check(dao.outboxCount(shop.shopId) == 0) { "Sync pending POS changes before closing a table bill." }
        val table = dao.getDiningTable(tableId) ?: throw IllegalStateException("This table is no longer available.")
        check(dao.openTableOrderCount(tableId) > 0) { "There is no open bill to close." }
        val amount = dao.openTableBillTotal(tableId)
        check(amount > 0L) { "There is no open bill to close." }
        val operation = newOutbox(
            "settle_table_bill",
            JSONObject().put("table_local_id", table.id).put("payment_method", method)
        )
        dao.enqueueOutbox(operation)
        val syncedResults = scheduleAndAttemptSync()
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
        waiterName: String? = null
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
        val stockToDecrement = if (unitName.equals("Bottle", true) || unitName.equals("Can", true)) quantity else 1
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
            sourceCounterId = counterId
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
            .apply {
                if (resolvedCounterName != null) put("counter_name", resolvedCounterName)
                if (resolvedWaiterName != null) put("waiter_name", resolvedWaiterName)
            }
        val queued = dao.queueTableSaleAtCounter(
            order, sale, newOutbox("table_sale", payload), counterId, stockToDecrement,
            activeBusiness!!.counterInventoryMode == "per_counter"
        ) ?: throw IllegalStateException("Not enough cached counter stock to complete this sale offline.")
        check(queued > 0L)
        scheduleAndAttemptSync()
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
        tableId: String? = null,
        tableName: String? = null
    ): Boolean =
        withActiveProduct(code) { product ->
            require(quantity > 0 && unitPriceKsh > 0) { "Enter a valid sale quantity and price." }
            val counterId = selectedCounterId?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Choose the serving counter before recording a sale.")
            val stockToDecrement = if (unitName.equals("Bottle", true) || unitName.equals("Can", true)) quantity else 1
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
                sourceCounterId = counterId
            )
            val payload = JSONObject()
                .put("product_code", product.catalogCode)
                .put("counter_id", counterId)
                .put("quantity", quantity)
                .put("unit_name", unitName)
                .put("unit_price", unitPriceKsh)
            val queued = dao.queueSaleAtCounter(
                sale, newOutbox("sale", payload), counterId, stockToDecrement,
                activeBusiness!!.counterInventoryMode == "per_counter"
            ) ?: throw IllegalStateException("Not enough cached counter stock to complete this sale offline.")
            check(queued > 0L)
            scheduleAndAttemptSync()
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
                .put("counter_id", counterId)
                .put("quantity", quantity)
                .put("price", priceKsh)
                .put("unit_cost", unitCostKsh)
                .put("catalog_id", catalogId)
            dao.queueProductChange(updated, newOutbox("add_catalog_product", payload), balance)
            scheduleAndAttemptSync()
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
            dao.queueProductChange(updated, newOutbox("receive_stock", payload), balance)
            scheduleAndAttemptSync()
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
            dao.queueProductChange(updated, newOutbox("adjust_stock", payload), balance)
            scheduleAndAttemptSync()
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
            val localProduct = product.copy(
                remoteId = current.remoteId,
                inMyStock = current.inMyStock,
                stockQuantity = if (perCounter) current.stockQuantity else targetCount
            )
            val fields = JSONObject()
                .put("product_code", product.catalogCode)
                .put("name", product.name)
                .put("price", product.sellingPriceKsh)
                .put("brand", product.brand)
                .put("size", product.size)
                .put("category", product.category)
            if (!product.verifiedBarcode.isNullOrBlank()) fields.put("barcode", product.verifiedBarcode)
            val operations = mutableListOf(newOutbox("update_product", fields))
            if (stockChanged) {
                operations += newOutbox(
                    "adjust_stock",
                    JSONObject()
                        .put("product_code", product.catalogCode)
                        .put("counter_id", counterId)
                        .put("new_stock", targetCount)
                        .put("reason", "Product edit stock count")
                )
            }
            val balance = if (perCounter && stockChanged) makeCounterBalance(current, counterId, targetCount) else null
            dao.queueProductChanges(localProduct, operations, balance)
            scheduleAndAttemptSync()
            true
        }

    suspend fun undoLatestSale(): Boolean = withActiveBusiness {
        val sale = dao.getLatestActiveSale() ?: return@withActiveBusiness false
        val perCounter = activeBusiness!!.counterInventoryMode == "per_counter"
        if (sale.remoteId.isNullOrBlank()) {
            if (dao.cancelUnsentSale(sale, perCounter)) return@withActiveBusiness true
            throw IllegalStateException("This pending sale cannot be safely cancelled offline.")
        }
        val payload = JSONObject()
            .put("sale_id", sale.remoteId)
            .put("product_code", sale.catalogCode)
            .put("counter_id", sale.sourceCounterId)
        if (!dao.queueUndoSale(sale, newOutbox("undo_sale", payload), perCounter)) {
            throw IllegalStateException("The sale could not be undone using the saved stock state.")
        }
        scheduleAndAttemptSync()
        true
    }

    suspend fun updatePhoto(product: ProductEntity, uri: String): Boolean {
        // The local URI is intentionally not treated as a backend authority.
        // A future upload flow can put it in Storage and then PATCH photo_path.
        return dao.updatePhotoOverride(product.catalogCode, uri) == 1
    }

    suspend fun assignBarcode(code: String, barcode: String): Boolean =
        withActiveProduct(code) { product ->
            val clean = barcode.trim()
            require(clean.isNotBlank()) { "Enter a barcode." }
            val updated = product.copy(verifiedBarcode = clean)
            dao.queueProductChange(
                updated,
                newOutbox("update_product", JSONObject().put("product_code", code).put("barcode", clean))
            )
            scheduleAndAttemptSync()
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
        selectedCounterId: String? = null
    ): Boolean = withActiveBusiness {
        require(activeBusiness!!.role in setOf("owner", "manager", "inventory_clerk")) { "Only business owners and managers can register products." }
        require(name.trim().isNotBlank() && priceKsh > 0 && initialStock > 0 && unitCostKsh >= 0) {
            "Enter a product name, price, and positive opening stock."
        }
        val code = barcode.trim().ifBlank { "PROD_" + UUID.randomUUID().toString().take(8) }
        require(dao.getProductByCode(code) == null) { "A product with this barcode already exists." }
        val counterId = selectedCounterId?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Choose the receiving counter first.")
        val perCounter = activeBusiness!!.counterInventoryMode == "per_counter"
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
            .put("counter_id", counterId)
        val balance = if (perCounter) makeCounterBalance(product, counterId, initialStock) else null
        dao.queueProductChange(product, newOutbox("register_product", payload), balance)
        scheduleAndAttemptSync()
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

    suspend fun syncPendingOutbox(refreshSnapshot: Boolean = true): Map<String, JSONObject> = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: return@withContext emptyMap()
        val userId = account?.userId?.takeIf(String::isNotBlank) ?: return@withContext emptyMap()
        syncMutex.withLock {
            val syncedResults = mutableMapOf<String, JSONObject>()
            while (true) {
                val item = dao.nextPendingOutbox(shop.shopId) ?: break
                try {
                    val payload = prepareOutboxPayload(item)
                    val result = when (item.operationType) {
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
                    dao.acknowledgeOutboxItem(item.operationId)
                    syncedResults[item.operationId] = result
                } catch (error: Exception) {
                    if (error is SupabaseUnauthorizedException) {
                        dao.recordOutboxAttempt(item.operationId, error.message)
                        _session.value = _session.value.copy(message = "Saved POS changes are waiting for this account to reconnect securely.")
                        throw java.io.IOException("Authentication is required to sync queued POS changes.", error)
                    }
                    if (error is SupabaseHttpException && !isRetryable(error)) {
                        dao.blockOutboxItem(item.operationId, error.message ?: "The server rejected this saved change.")
                        _session.value = _session.value.copy(message = "A saved POS change needs attention before it can sync: ${error.message.orEmpty()}")
                        break
                    }
                    dao.recordOutboxAttempt(item.operationId, error.message)
                    if (error is java.io.IOException) throw error
                    throw java.io.IOException("Could not sync a saved POS change.", error)
                }
            }
            if (refreshSnapshot && dao.outboxCount(shop.shopId) == 0) {
                runCatching { syncBusiness(shop) }
                    .onFailure { error -> if (isRetryable(error)) _session.value = _session.value.copy(message = offlineMessage()) }
            }
            syncedResults
        }
    }

    private suspend fun prepareOutboxPayload(item: OutboxEntity): JSONObject {
        val local = JSONObject(item.payloadJson)
        suspend fun productId(): String {
            val code = local.optString("product_code")
            val product = dao.getProductByCode(code)
            return product?.remoteId?.takeIf(String::isNotBlank)
                ?: throw java.io.IOException("Waiting for the queued product creation to sync first.")
        }
        suspend fun coreSaleFields(): JSONObject = JSONObject()
            .put("product_id", productId())
            .put("counter_id", local.optString("counter_id"))
            .put("qty", local.optInt("quantity", 1))
            .put("unit_name", local.optString("unit_name", "Bottle"))
            .put("unit_price", local.optInt("unit_price", 0))
        return when (item.operationType) {
            "create_dining_table" -> JSONObject().put("name", local.optString("name")).put("local_table_id", local.optString("local_table_id"))
            "delete_dining_table" -> {
                val table = dao.getDiningTable(local.optString("local_table_id"))
                val tableId = table?.remoteId?.takeIf(String::isNotBlank) ?: local.optString("table_id")
                JSONObject().put("table_id", tableId).put("local_table_id", local.optString("local_table_id"))
            }
            "settle_table_bill" -> {
                val table = dao.getDiningTable(local.optString("table_local_id"))
                    ?: throw java.io.IOException("Waiting for the queued table to be available.")
                val tableId = table.remoteId?.takeIf(String::isNotBlank)
                    ?: throw java.io.IOException("Waiting for the queued table creation to sync first.")
                JSONObject().put("table_id", tableId).put("payment_method", local.optString("payment_method"))
            }
            "sale" -> coreSaleFields()
            "table_sale" -> {
                val table = dao.getDiningTable(local.optString("table_local_id"))
                    ?: throw java.io.IOException("Waiting for the queued table to be available.")
                val tableId = table.remoteId?.takeIf(String::isNotBlank)
                    ?: throw java.io.IOException("Waiting for the queued table creation to sync first.")
                coreSaleFields().put("table_id", tableId)
            }
            "receive_stock" -> JSONObject()
                .put("counter_id", local.optString("counter_id"))
                .put("product_id", productId())
                .put("qty", local.optInt("quantity"))
                .put("unit_cost", local.optInt("unit_cost"))
            "add_catalog_product" -> JSONObject()
                .put("catalog_id", local.optString("catalog_id"))
                .put("counter_id", local.optString("counter_id"))
                .put("qty", local.optInt("quantity"))
                .put("price", local.optInt("price"))
                .put("unit_cost", local.optInt("unit_cost"))
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
                listOf("name", "brand", "size", "category", "price", "barcode").forEach { key ->
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

    private suspend fun scheduleAndAttemptSync(): Map<String, JSONObject> {
        OfflineOutboxWorker.enqueue(context)
        try {
            return syncPendingOutbox()
        } catch (error: Exception) {
            if (!isRetryable(error)) throw error
        }
        return emptyMap()
    }

    private suspend fun clearLocalSession() {
        realtime.stop()
        realtimeRefreshJob?.cancel()
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

private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
