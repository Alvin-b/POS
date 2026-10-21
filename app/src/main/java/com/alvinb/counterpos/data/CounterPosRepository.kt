package com.alvinb.counterpos.data

import android.content.Context
import android.content.res.AssetManager
import com.alvinb.counterpos.data.local.dao.CounterPosDao
import com.alvinb.counterpos.data.local.entity.DiningTableEntity
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.local.entity.SaleUnit
import com.alvinb.counterpos.data.local.entity.TableOrderEntity
import com.alvinb.counterpos.data.local.entity.serializeSaleUnits
import com.alvinb.counterpos.data.remote.AccountSnapshot
import com.alvinb.counterpos.data.remote.AdminAuditEvent
import com.alvinb.counterpos.data.remote.AdminBusiness
import com.alvinb.counterpos.data.remote.AuthSession
import com.alvinb.counterpos.data.remote.BusinessMembership
import com.alvinb.counterpos.data.remote.CounterReportRow
import com.alvinb.counterpos.data.remote.ServiceCounter
import com.alvinb.counterpos.data.remote.SupabaseApiException
import com.alvinb.counterpos.data.remote.SupabaseRestClient
import com.alvinb.counterpos.data.model.TimeSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import com.alvinb.counterpos.data.model.BusinessHours
import java.io.IOException
import java.util.Date
import java.util.Locale
import java.util.UUID
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

data class AdminCreateBusinessResult(
    val businesses: List<AdminBusiness>,
    val createdShopId: String?
)

class CounterPosRepository(
    context: Context,
    private val dao: CounterPosDao,
    private val assets: AssetManager,
    private val remote: SupabaseRestClient
) {
    private val offlineAccountPrefs = context.getSharedPreferences("counterpos_offline_account", Context.MODE_PRIVATE)

    data class AccessChallenge(
        val token: String,
        val expiresAtMillis: Long
    )

    data class ApprovedAccess(
        val session: SessionSnapshot,
        val businessId: String?
    )

    val products: Flow<List<ProductEntity>> = dao.observeProducts()
    val sales: Flow<List<SaleEntity>> = dao.observeSales()
    val diningTables: Flow<List<DiningTableEntity>> = dao.observeDiningTables()
    val tableOrders: Flow<List<TableOrderEntity>> = dao.observeTableOrders()
    private val _serviceCounters = MutableStateFlow<List<ServiceCounter>>(emptyList())
    val serviceCounters = _serviceCounters.asStateFlow()

    private val _session = MutableStateFlow(SessionSnapshot())
    val session = _session.asStateFlow()
    private var account: AccountSnapshot? = null
    private var activeBusiness: BusinessMembership? = null

    suspend fun restoreSession(): SessionSnapshot = withContext(Dispatchers.IO) {
        if (remote.restoreSession() == null) {
            clearLocalSession()
            return@withContext _session.value
        }
        runCatching { loadAccount(autoSelectFirstBusiness = true) }
            .getOrElse { restoreCachedSession() ?: throw it }
    }

    suspend fun signIn(email: String, password: String): SessionSnapshot = withContext(Dispatchers.IO) {
        remote.signIn(email.trim(), password)
        loadAccount(autoSelectFirstBusiness = true)
    }

    suspend fun signInWithPasskey(passkey: String): SessionSnapshot = withContext(Dispatchers.IO) {
        require(passkey.trim().isNotBlank()) { "Enter the passkey sent by the business owner." }
        val redemption = remote.redeemAccessPass(passkey.trim())
        loadAccount(autoSelectFirstBusiness = true, preferredShopId = redemption.businessId)
    }

    suspend fun signInWithSuperadminToken(token: String): SessionSnapshot = withContext(Dispatchers.IO) {
        require(token.trim().isNotBlank()) { "Enter the current Superadmin token." }
        remote.redeemSuperadminToken(token.trim())
        loadAccount(autoSelectFirstBusiness = false)
    }

    suspend fun createAccessPass(role: String): JSONObject = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: throw IllegalStateException("Open a business before creating an access pass.")
        require(shop.role == "owner" || shop.role == "manager") { "Only a business owner or manager can add staff." }
        require(role in setOf("manager", "inventory_clerk", "cashier")) { "Invalid staff role." }
        remote.createAccessPass(shop.shopId, role)
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
        remote.signIn(result.optString("email"), result.optString("password"))
        val shopId = result.optString("shop_id")
            .ifBlank { result.optString("business_id") }
            .takeIf(String::isNotBlank)
        var session = loadAccount(autoSelectFirstBusiness = true, preferredShopId = shopId)
        if (shopId != null && session.activeBusiness?.shopId != shopId) {
            kotlinx.coroutines.delay(600)
            session = loadAccount(autoSelectFirstBusiness = true, preferredShopId = shopId)
        }
        ApprovedAccess(
            session = session,
            businessId = shopId ?: session.activeBusiness?.shopId ?: session.memberships.firstOrNull { it.status == "active" }?.shopId
        )
    }

    suspend fun approveAccessChallenge(token: String, role: String = "cashier") = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: throw IllegalStateException("Open a business first.")
        require(shop.role == "owner" || shop.role == "manager") { "Only an owner or manager can approve staff QR requests." }
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
        loadAccount(autoSelectFirstBusiness = false)
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
                put(JSONObject().apply {
                    put("weekday", day.dayOfWeek - 1)
                    put("open_time", day.openTime.format())
                    put("close_time", day.closeTime.format())
                    put("enabled", day.isOpen)
                })
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
        require(shop.role == "owner" || shop.role == "manager") { "Only an owner or manager can issue staff passes." }
        remote.createAccessPass(shop.shopId, role)
    }

    suspend fun signUp(email: String, password: String): SessionSnapshot = withContext(Dispatchers.IO) {
        val created = remote.signUp(email.trim(), password)
        if (created == null) {
            _session.value = SessionSnapshot(
                message = "Account created. Confirm your email, then sign in to continue."
            )
        } else {
            loadAccount(autoSelectFirstBusiness = true)
        }
        _session.value
    }

    suspend fun createBusiness(name: String): SessionSnapshot = withContext(Dispatchers.IO) {
        require(name.trim().isNotBlank()) { "Enter a business name." }
        remote.createShop(name.trim())
        loadAccount(autoSelectFirstBusiness = true)
    }

    suspend fun selectBusiness(shopId: String): SessionSnapshot = withContext(Dispatchers.IO) {
        val membership = account?.memberships?.firstOrNull { it.shopId == shopId }
            ?: run {
                account = remote.account()
                account?.memberships?.firstOrNull { it.shopId == shopId }
            }
            ?: run {
                if (account?.isPlatformAdmin == true) {
                    val adminBiz = remote.adminBusinesses().firstOrNull { it.shopId == shopId }
                    if (adminBiz != null) createAdminBusinessMembership(adminBiz) else null
                } else null
            }
            ?: throw IllegalArgumentException("Business membership not found")
        if (membership.status == "suspended" || membership.status == "closed" || membership.status == "terminated") {
            throw IllegalStateException("This business is ${membership.status}. Contact the platform administrator.")
        }
        if (membership.role != "owner" && (membership.employmentStatus == "suspended" || membership.employmentStatus == "terminated" || membership.employmentStatus == "locked")) {
            throw IllegalStateException("Your employee access is ${membership.employmentStatus}. Contact the business administrator.")
        }
        activeBusiness = membership
        syncBusiness(membership)
        publishAccount()
    }

    suspend fun adminOpenBusiness(shopId: String): SessionSnapshot = withContext(Dispatchers.IO) {
        val businesses = remote.adminBusinesses()
        val biz = businesses.firstOrNull { it.shopId == shopId }
            ?: throw IllegalArgumentException("Business profile not found in portfolio")
        val membership = createAdminBusinessMembership(biz)
        activeBusiness = membership
        syncBusiness(membership)
        publishAccount()
    }

    suspend fun exitToAdminPortal(): SessionSnapshot = withContext(Dispatchers.IO) {
        activeBusiness = null
        dao.clearTenantCache()
        publishAccount()
    }

    private fun createAdminBusinessMembership(biz: AdminBusiness): BusinessMembership {
        val isLiquorStore = biz.businessType.equals("counter_seller", ignoreCase = true) || biz.businessType.equals("liquor_store", ignoreCase = true)
        val flags = if (isLiquorStore) {
            setOf("employee_management", "working_hours", "fifo_inventory", "sales_analytics")
        } else {
            setOf("tables", "employee_management", "working_hours", "fifo_inventory", "sales_analytics")
        }
        return BusinessMembership(
            shopId = biz.shopId,
            shopName = biz.shopName,
            role = "owner",
            status = "active",
            employmentStatus = "active",
            businessType = biz.businessType,
            businessSize = biz.businessSize,
            counterInventoryMode = biz.counterInventoryMode,
            counterCount = if (isLiquorStore) 1 else biz.counterCount.toInt().coerceAtLeast(1),
            featureFlags = flags,
            currency = "KES",
            lowStockThreshold = 5
        )
    }

    suspend fun refreshAccount(): SessionSnapshot = withContext(Dispatchers.IO) {
        loadAccount(autoSelectFirstBusiness = false)
    }

    /**
     * Reconcile offline work as soon as connectivity returns. Local Room is
     * authoritative while offline; pending sales are uploaded first so the
     * following tenant refresh cannot clear them from the local cache.
     */
    suspend fun synchronizeWhenOnline(): SessionSnapshot = withContext(Dispatchers.IO) {
        if (remote.restoreSession() == null || activeBusiness == null) return@withContext _session.value
        uploadPendingLocalSales()
        loadAccount(autoSelectFirstBusiness = false)
    }

    private suspend fun uploadPendingLocalSales() {
        if (activeBusiness?.role != "owner") return
        val shopId = activeBusiness?.shopId ?: return
        val knownCounters = _serviceCounters.value
        for (sale in dao.getPendingRemoteSales()) {
            val productId = dao.getProductByCode(sale.catalogCode)?.remoteId ?: continue
            val counterId = sale.counterId?.takeIf { id -> knownCounters.any { it.id == id } }
                ?: knownCounters.singleOrNull()?.id
                ?: continue
            val tableId = sale.tableId?.takeIf(String::isNotBlank)
            // Older app versions queued table orders without an idempotency key.
            // Do not replay them into a live running bill where that could duplicate a line.
            if (tableId != null) continue
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

    suspend fun canOperateNow(): Boolean = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: return@withContext true
        remote.canSellForShop(shop.shopId)
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        clearLocalSession()
    }

    suspend fun adminBusinesses(): List<AdminBusiness> = remote.adminBusinesses()

    suspend fun counterSalesReport(fromIso: String, toIso: String): List<CounterReportRow> = withActiveBusiness {
        remote.counterSalesReport(activeBusiness!!.shopId, fromIso, toIso)
    }

    suspend fun adminAuditEvents(shopId: String): List<AdminAuditEvent> = remote.auditEvents(shopId)

    suspend fun adminCreateAccessPass(shopId: String, role: String): JSONObject =
        remote.createAccessPass(shopId, role)

    suspend fun createServiceCounter(name: String): ServiceCounter = withActiveBusiness {
        remote.createServiceCounter(activeBusiness!!.shopId, name).also {
            _serviceCounters.value = normalizeSingleCounterLabel(_serviceCounters.value + it)
            persistServiceCounters(activeBusiness!!.shopId, _serviceCounters.value)
        }
    }

    suspend fun loadCounterStock(counterId: String): Map<String, Int> = withActiveBusiness {
        remote.counterStock(activeBusiness!!.shopId, counterId)
    }

    suspend fun renameServiceCounter(counterId: String, name: String): List<ServiceCounter> = withActiveBusiness {
        remote.renameServiceCounter(activeBusiness!!.shopId, counterId, name)
        remote.serviceCounters(activeBusiness!!.shopId).let(::normalizeSingleCounterLabel)
            .also {
                _serviceCounters.value = it
                persistServiceCounters(activeBusiness!!.shopId, it)
            }
    }

    suspend fun transferCounterStock(productCode: String, fromCounter: String, toCounter: String, quantity: Int): Boolean =
        withActiveProduct(productCode) { product ->
            remote.transferCounterStock(activeBusiness!!.shopId, product.remoteId!!, fromCounter, toCounter, quantity, "Counter transfer from mobile app")
            true
        }

    suspend fun adminSetBusinessStatus(shopId: String, status: String, reason: String?): List<AdminBusiness> {
        remote.adminSetBusinessStatus(shopId, status, reason)
        return remote.adminBusinesses()
    }

    suspend fun createAdminBusinessAccessPass(shopId: String, role: String): JSONObject = withContext(Dispatchers.IO) {
        require(account?.isPlatformAdmin == true) { "Only a platform administrator can issue an access pass." }
        remote.createAccessPass(shopId, role)
    }

    suspend fun adminCreateBusiness(
        name: String,
        ownerEmail: String,
        businessType: String = "big_club",
        businessSize: String = "medium",
        inventoryMode: String = "shared",
        counterCount: Int = 1
    ): AdminCreateBusinessResult = withContext(Dispatchers.IO) {
        val created = remote.adminCreateBusiness(
            name,
            ownerEmail.takeIf { it.isNotBlank() },
            businessType,
            businessSize,
            inventoryMode,
            counterCount
        )
        val createdShopId = created.optString("shop_id").ifBlank { created.optString("id") }.takeIf { it.isNotBlank() }
        val businesses = remote.adminBusinesses()
        AdminCreateBusinessResult(businesses, createdShopId)
    }

    suspend fun adminCreateBusiness(
        name: String,
        ownerEmail: String,
        inventoryMode: String,
        counterCount: Int
    ): AdminCreateBusinessResult =
        adminCreateBusiness(name, ownerEmail, "big_club", "medium", inventoryMode, counterCount)

    suspend fun addMemberByEmail(shopId: String, email: String, role: String): List<AdminBusiness> {
        require(role in setOf("manager", "inventory_clerk", "cashier")) { "Choose a valid staff role." }
        remote.addMemberByEmail(shopId, email, role)
        return remote.adminBusinesses()
    }

    private suspend fun loadAccount(autoSelectFirstBusiness: Boolean, preferredShopId: String? = null): SessionSnapshot {
        account = remote.account()
        cacheAccount(account!!)
        val allMemberships = account?.memberships.orEmpty()
        val activeMemberships = allMemberships.filter {
            it.status != "suspended" && it.status != "terminated" && it.status != "closed"
        }
        val targetMembership = if (!preferredShopId.isNullOrBlank()) {
            allMemberships.firstOrNull { it.shopId == preferredShopId }
        } else if (autoSelectFirstBusiness) {
            activeMemberships.firstOrNull { it.role == "owner" }
                ?: activeMemberships.firstOrNull()
                ?: allMemberships.firstOrNull()
        } else null

        if (targetMembership != null) {
            activeBusiness = targetMembership
            syncBusiness(targetMembership)
        } else if (activeBusiness != null) {
            val refreshed = allMemberships.firstOrNull { it.shopId == activeBusiness?.shopId }
            if (refreshed != null && refreshed.status != "suspended" && refreshed.status != "terminated" && refreshed.status != "closed") {
                activeBusiness = refreshed
                syncBusiness(refreshed)
            } else {
                activeBusiness = null
                dao.clearTenantCache()
            }
        }
        cacheAccount(account!!)
        return publishAccount()
    }

    private fun cacheAccount(snapshot: AccountSnapshot) {
        val memberships = JSONArray()
        snapshot.memberships.forEach { membership ->
            memberships.put(JSONObject().apply {
                put("shop_id", membership.shopId)
                put("shop_name", membership.shopName)
                put("role", membership.role)
                put("status", membership.status)
                put("employment_status", membership.employmentStatus)
                put("business_type", membership.businessType)
                put("business_size", membership.businessSize)
                put("counter_inventory_mode", membership.counterInventoryMode)
                put("counter_count", membership.counterCount)
                put("feature_flags", JSONArray(membership.featureFlags.toList()))
                put("currency", membership.currency)
                put("low_stock_threshold", membership.lowStockThreshold)
            })
        }
        offlineAccountPrefs.edit()
            .putString("user_id", snapshot.userId)
            .putString("email", snapshot.email)
            .putBoolean("is_platform_admin", snapshot.isPlatformAdmin)
            .putString("active_shop_id", activeBusiness?.shopId)
            .putString("memberships", memberships.toString())
            .apply()
    }

    private fun normalizeSingleCounterLabel(counters: List<ServiceCounter>): List<ServiceCounter> =
        if (counters.size == 1 && counters.single().name.trim().equals("Counter 1", ignoreCase = true)) {
            listOf(counters.single().copy(name = "Main Counter"))
        } else counters

    private fun resolveRegisteredWaiter(value: String?, emailsById: Map<String, String>): String? {
        val candidate = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        return if (candidate.contains('@')) {
            emailsById.values.firstOrNull { it.equals(candidate, ignoreCase = true) }
        } else {
            emailsById[candidate]
        }
    }

    private fun persistServiceCounters(shopId: String, counters: List<ServiceCounter>) {
        val rows = JSONArray().apply {
            counters.forEach { counter ->
                put(JSONObject().put("id", counter.id).put("name", counter.name).put("active", counter.active))
            }
        }
        offlineAccountPrefs.edit()
            .putString("service_counters_shop_id", shopId)
            .putString("service_counters", rows.toString())
            .apply()
    }

    private fun cachedServiceCounters(shopId: String): List<ServiceCounter> {
        if (offlineAccountPrefs.getString("service_counters_shop_id", null) != shopId) return emptyList()
        val rows = runCatching { JSONArray(offlineAccountPrefs.getString("service_counters", "[]")) }.getOrNull()
            ?: return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val id = row.optString("id").takeIf(String::isNotBlank) ?: continue
                if (id.matches(Regex("counter_\\d+"))) continue
                val name = row.optString("name").takeIf(String::isNotBlank) ?: continue
                if (!row.optBoolean("active", true)) continue
                add(ServiceCounter(id, name, row.optBoolean("active", true)))
            }
        }.let(::normalizeSingleCounterLabel)
    }

    private suspend fun restoreCachedSession(): SessionSnapshot? {
        val userId = offlineAccountPrefs.getString("user_id", null) ?: return null
        val rows = JSONArray(offlineAccountPrefs.getString("memberships", "[]"))
        val memberships = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val flags = buildSet {
                    val values = row.optJSONArray("feature_flags") ?: JSONArray()
                    for (flagIndex in 0 until values.length()) add(values.optString(flagIndex))
                }
                add(BusinessMembership(
                    shopId = row.optString("shop_id"),
                    shopName = row.optString("shop_name"),
                    role = row.optString("role"),
                    status = row.optString("status", "active"),
                    employmentStatus = row.optString("employment_status", "active"),
                    businessType = row.optString("business_type", "big_club"),
                    businessSize = row.optString("business_size", "medium"),
                    counterInventoryMode = row.optString("counter_inventory_mode", "shared"),
                    counterCount = row.optInt("counter_count", 1),
                    featureFlags = flags,
                    currency = row.optString("currency", "KES"),
                    lowStockThreshold = row.optInt("low_stock_threshold", 5)
                ))
            }
        }.filter { it.shopId.isNotBlank() }
        val active = memberships.firstOrNull { it.shopId == offlineAccountPrefs.getString("active_shop_id", null) }
            ?: memberships.firstOrNull { it.status == "active" }
        activeBusiness = active
        if (active != null) {
            seedCatalogIfEmpty()
            _serviceCounters.value = cachedServiceCounters(active.shopId)
        }
        return publishAccount().copy(
            isAuthenticated = true,
            userId = userId,
            email = offlineAccountPrefs.getString("email", "").orEmpty(),
            isPlatformAdmin = offlineAccountPrefs.getBoolean("is_platform_admin", false),
            memberships = memberships,
            activeBusiness = active,
            message = "Offline mode — changes will sync automatically when connected"
        ).also { _session.value = it }
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
        seedCatalogIfEmpty()
        val remoteCounters = remote.serviceCounters(membership.shopId)
        val counters = normalizeSingleCounterLabel(remoteCounters)
        _serviceCounters.value = counters
        persistServiceCounters(membership.shopId, counters)
        val memberEmailsById = runCatching {
            val current = account
            if (membership.role.equals("cashier", ignoreCase = true)) {
                val userId = current?.userId?.takeIf(String::isNotBlank)
                val email = current?.email?.takeIf(String::isNotBlank)
                if (userId != null && email != null) mapOf(userId to email) else emptyMap()
            } else {
                val rows = remote.listShopMembers(membership.shopId)
                buildMap {
                    for (index in 0 until rows.length()) {
                        val row = rows.optJSONObject(index) ?: continue
                        if (!row.optString("role").equals("cashier", ignoreCase = true)) continue
                        val userId = row.optString("member_user_id").takeIf(String::isNotBlank) ?: continue
                        val email = row.optString("email").trim().takeIf(String::isNotBlank) ?: continue
                        put(userId, email)
                    }
                }
            }
        }.getOrDefault(emptyMap())

        // Remote catalog rows augment the bundled image fallback. The remote
        // product snapshot then replaces matching catalog rows with tenant data.
        val catalogRows = remote.catalog()
        val catalogProducts = buildList {
            for (index in 0 until catalogRows.length()) {
                catalogRows.optJSONObject(index)?.let { row ->
                    val code = row.optString("code")
                    val fallback = dao.getProductByCode(code)?.imageAssetName ?: "product_stock_001"
                    add(
                        ProductEntity(
                            catalogCode = code,
                            verifiedBarcode = row.optNullableString("barcode"),
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
                            productSourceUrl = row.optNullableString("image_url"),
                            imageSourceUrl = row.optNullableString("image_url"),
                            verificationNote = "Shared backend catalog",
                            remoteId = row.optString("id").takeIf(String::isNotBlank)
                        )
                    )
                }
            }
        }
        val productRows = remote.products(membership.shopId)
        val products = buildList {
            for (index in 0 until productRows.length()) {
                productRows.optJSONObject(index)?.let { row ->
                    val code = row.optString("code")
                    val fallback = dao.getProductByCode(code)?.imageAssetName ?: "product_stock_001"
                    add(
                        ProductEntity(
                            catalogCode = code,
                            verifiedBarcode = row.optNullableString("barcode"),
                            name = row.optString("name"),
                            brand = row.optString("brand"),
                            size = row.optString("size"),
                            category = row.optString("category", "Beverage"),
                            suggestedPriceKsh = row.optInt("price", 0),
                            sellingPriceKsh = row.optInt("price", 0),
                            stockQuantity = row.optInt("stock", 0),
                            inMyStock = true,
                            lowStockThreshold = membership.lowStockThreshold,
                            imageAssetName = fallback,
                            photoOverrideUri = row.optNullableString("photo_url"),
                            productSourceUrl = row.optNullableString("photo_url"),
                            imageSourceUrl = row.optNullableString("photo_url"),
                            verificationNote = "Business inventory",
                            salesUnitsJson = serializeSaleUnits(listOf(SaleUnit("Bottle", row.optInt("price", 0)))),
                            remoteId = row.optString("id").takeIf(String::isNotBlank),
                            averageCostKsh = row.optInt("average_cost", 0)
                        )
                    )
                }
            }
        }
        val sales = buildList {
            val rows = remote.sales(membership.shopId)
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val remoteId = row.optString("id").takeIf(String::isNotBlank) ?: continue
                val productId = row.optString("product_id")
                val product = products.firstOrNull { it.remoteId == productId }
                val cId = row.optNullableString("source_counter_id") ?: row.optNullableString("counter_id")
                val cName = counters.firstOrNull { it.id == cId }?.name ?: row.optNullableString("counter_name")
                add(
                    SaleEntity(
                        id = stableLocalId(remoteId),
                        catalogCode = product?.catalogCode ?: row.optString("product_code", productId),
                        productName = row.optString("product_name").ifBlank { product?.name ?: "Product unavailable" },
                        imageAssetName = product?.imageAssetName ?: "product_stock_001",
                        unitName = row.optString("unit_name", "Bottle"),
                        quantity = row.optInt("qty", row.optInt("quantity", 1)),
                        unitPriceKsh = row.optInt("unit_price", row.optInt("unit_price_ksh", 0)),
                        totalKsh = row.optInt("total", row.optInt("total_ksh", 0)),
                        createdAt = parseTimestamp(row.optString("created_at")),
                        undone = !row.isNull("undone_at"),
                        remoteId = remoteId,
                        counterId = cId,
                        counterName = cName,
                        waiterName = resolveRegisteredWaiter(row.optNullableString("waiter_name"), memberEmailsById)
                            ?: resolveRegisteredWaiter(row.optNullableString("served_by"), memberEmailsById)
                            ?: resolveRegisteredWaiter(row.optNullableString("sold_by"), memberEmailsById),
                        tableId = row.optNullableString("table_id"),
                        tableName = row.optNullableString("table_name")
                    )
                )
            }
        }
        val remoteTables = buildList {
            val rows = remote.diningTables(membership.shopId)
            for (index in 0 until rows.length()) {
                rows.optJSONObject(index)?.let { row ->
                    add(
                        DiningTableEntity(
                            id = row.optString("id"),
                            name = row.optString("name"),
                            createdAt = parseTimestamp(row.optString("created_at"))
                        )
                    )
                }
            }
        }
        val orders = buildList {
            val rows = remote.tableOrders(membership.shopId)
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val saleRemoteId = row.optString("sale_id").takeIf(String::isNotBlank) ?: continue
                val sale = sales.firstOrNull { it.remoteId == saleRemoteId }
                val productId = row.optString("product_id")
                val product = products.firstOrNull { it.remoteId == productId }
                val quantity = row.optInt("qty", row.optInt("quantity", 1))
                val unitPrice = row.optInt("unit_price", row.optInt("unit_price_ksh", 0))
                val cId = row.optNullableString("source_counter_id")
                    ?: row.optNullableString("counter_id")
                    ?: sale?.counterId
                add(
                    TableOrderEntity(
                        tableId = row.optString("table_id"),
                        saleId = stableLocalId(saleRemoteId),
                        catalogCode = product?.catalogCode ?: row.optString("product_code", productId),
                        productName = row.optString("product_name").ifBlank { product?.name ?: "Product unavailable" },
                        unitName = row.optString("unit_name", "Bottle"),
                        quantity = quantity,
                        unitPriceKsh = unitPrice,
                        totalKsh = row.optInt("total", row.optInt("total_ksh", quantity * unitPrice)),
                        servedAt = parseTimestamp(row.optString("served_at", row.optString("created_at"))),
                        counterId = cId,
                        counterName = counters.firstOrNull { it.id == cId }?.name
                            ?: row.optNullableString("counter_name") ?: sale?.counterName,
                        waiterName = resolveRegisteredWaiter(row.optNullableString("waiter_name"), memberEmailsById)
                            ?: resolveRegisteredWaiter(row.optNullableString("served_by"), memberEmailsById)
                            ?: sale?.waiterName
                    )
                )
            }
        }
        dao.clearTenantCache()
        seedCatalogIfEmpty()
        dao.insertProducts(catalogProducts)
        dao.insertProducts(products)
        dao.insertSales(sales)
        val seededTableNames = setOf("table 1", "table 2", "table 3", "vip 1", "vip 2")
        val tables = remoteTables.filter { table ->
            table.name.trim().lowercase() !in seededTableNames || orders.any { it.tableId == table.id }
        }
        dao.clearTableOrders()
        dao.clearDiningTables()
        dao.insertDiningTables(tables)
        orders.forEach { dao.insertTableOrder(it) }
    }

    suspend fun seedCatalogIfEmpty() = withContext(Dispatchers.IO) {
        val payload = assets.open("counterpos_catalog.json").bufferedReader().use { it.readText() }
        val array = JSONArray(payload)
        val seededProducts = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val code = item.getString("catalogCode")
                // Upsert only missing bundled entries. Never replace an existing
                // tenant product because it may contain stock, price, or image
                // changes made by the business owner.
                if (dao.getProductByCode(code) != null) continue
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
            val row = remote.createDiningTable(activeBusiness!!.shopId, cleanName)
            val remoteId = row.optString("id").takeIf(String::isNotBlank)
                ?: throw IllegalStateException("The server did not return the new table ID.")
            val table = DiningTableEntity(
                id = remoteId,
                name = row.optString("name", cleanName),
                createdAt = parseTimestamp(row.optString("created_at"))
            )
            dao.insertDiningTable(table)
            table
        }
    }

    private fun requireServiceCounter(selectedCounterId: String?): ServiceCounter {
        val counters = _serviceCounters.value
        return if (selectedCounterId != null) {
            counters.firstOrNull { it.id == selectedCounterId }
                ?: throw IllegalStateException("That counter is not configured for this business. Refresh Settings and try again.")
        } else {
            counters.singleOrNull()
                ?: throw IllegalStateException("Select an active counter in Settings before recording a sale.")
        }
    }

    private suspend fun requireCurrentEmployeeCanSell() {
        val shop = activeBusiness ?: throw IllegalStateException("Select an active business first.")
        if (shop.role != "owner" && !remote.canSellForShop(shop.shopId)) {
            throw IllegalStateException("Waiter access is locked outside configured duty hours or has been disabled by the owner.")
        }
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
        requireCurrentEmployeeCanSell()
        val counter = requireServiceCounter(selectedCounterId)
        val table = dao.getDiningTable(tableId)
            ?: throw IllegalStateException("This table is no longer available. Refresh the table list before adding items.")
        val resolvedWaiter = waiterName?.takeIf(String::isNotBlank)
            ?: account?.email?.takeIf(String::isNotBlank)
        val response = remote.recordTableSaleFromCounter(
            activeBusiness!!.shopId, table.id, product.remoteId!!, counter.id, quantity, unitName, unitPriceKsh
        )
        val remoteSaleId = response.optString("sale_id").takeIf(String::isNotBlank)
        if (remoteSaleId != null) {
            val localSaleId = stableLocalId(remoteSaleId)
            val servedAt = parseTimestamp(response.optString("served_at"))
            val sale = SaleEntity(
                id = localSaleId,
                catalogCode = product.catalogCode,
                productName = product.name,
                imageAssetName = product.imageAssetName,
                unitName = unitName,
                quantity = quantity,
                unitPriceKsh = unitPriceKsh,
                totalKsh = quantity * unitPriceKsh,
                createdAt = servedAt,
                undone = false,
                remoteId = remoteSaleId,
                counterId = counter.id,
                counterName = counter.name,
                waiterName = resolvedWaiter,
                tableId = table.id,
                tableName = table.name
            )
            val order = TableOrderEntity(
                tableId = table.id,
                saleId = localSaleId,
                catalogCode = product.catalogCode,
                productName = product.name,
                unitName = unitName,
                quantity = quantity,
                unitPriceKsh = unitPriceKsh,
                totalKsh = quantity * unitPriceKsh,
                servedAt = servedAt,
                counterId = counter.id,
                counterName = counter.name,
                waiterName = resolvedWaiter
            )
            runCatching { dao.insertCommittedTableSale(sale, order) }
        }
        // The remote transaction already committed; a refresh failure must never
        // fall through to a second local sale or make the user retry blindly.
        runCatching { syncBusiness(activeBusiness!!) }
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
    ): Boolean = withActiveProduct(code) { product ->
        requireCurrentEmployeeCanSell()
        val counter = requireServiceCounter(selectedCounterId)
        val resolvedWaiter = waiterName?.takeIf(String::isNotBlank)
            ?: account?.email?.takeIf(String::isNotBlank)
        try {
            remote.recordSaleUnitFromCounter(product.remoteId!!, counter.id, quantity, unitName, unitPriceKsh)
        } catch (error: IOException) {
            val shop = activeBusiness
            if (error is SupabaseApiException || shop?.role != "owner" || shop.counterInventoryMode == "per_counter") throw error
            val savedLocally = dao.completeSaleWithUnit(
                code = code,
                quantity = quantity,
                unitName = unitName,
                unitPriceKsh = unitPriceKsh,
                createdAt = System.currentTimeMillis(),
                counterId = counter.id,
                counterName = counter.name,
                waiterName = resolvedWaiter,
                tableId = tableId,
                tableName = tableName
            )
            if (!savedLocally) throw IllegalStateException("Sale could not be saved. Check stock and try again.")
            return@withActiveProduct true
        }
        // The sale RPC committed successfully. Never turn a later refresh error
        // into a second local transaction.
        runCatching { syncBusiness(activeBusiness!!) }
        true
    }

    suspend fun addFromLibrary(code: String, quantity: Int, priceKsh: Int, unitCostKsh: Int, selectedCounterId: String? = null): Boolean =
        withActiveBusiness {
            val catalogProduct = dao.getProductByCode(code)
                ?: throw IllegalStateException("Catalog product not found on this device. Refresh the product list and try again.")
            val counterId = selectedCounterId?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Choose the receiving counter first.")
            val catalogId = catalogProduct.remoteId
            if (catalogId.isNullOrBlank()) {
                // Some products are bundled in the APK and do not exist in the
                // Supabase catalog. Register them directly in this business so
                // they can still be stocked and sold.
                try {
                    remote.registerProductAtCounter(
                        shopId = activeBusiness!!.shopId,
                        code = catalogProduct.catalogCode,
                        name = catalogProduct.name,
                        category = catalogProduct.category,
                        price = priceKsh,
                        quantity = quantity,
                        unitCost = unitCostKsh,
                        brand = catalogProduct.brand,
                        size = catalogProduct.size,
                        counterId = counterId
                    )
                } catch (error: Exception) {
                    val message = error.message.orEmpty()
                    val missingRpc = message.contains("function", ignoreCase = true) &&
                        (message.contains("not found", ignoreCase = true) || message.contains("does not exist", ignoreCase = true)) ||
                        message.contains("PGRST202", ignoreCase = true) || message.contains("404")
                    if (missingRpc) {
                        remote.registerProduct(
                            shopId = activeBusiness!!.shopId,
                            code = catalogProduct.catalogCode,
                            name = catalogProduct.name,
                            category = catalogProduct.category,
                            price = priceKsh,
                            quantity = quantity,
                            unitCost = unitCostKsh,
                            brand = catalogProduct.brand,
                            size = catalogProduct.size
                        )
                    } else {
                        throw error
                    }
                }
            } else {
                try {
                    remote.addFromCatalogAtCounter(activeBusiness!!.shopId, catalogId, counterId, quantity, priceKsh, unitCostKsh)
                } catch (error: Exception) {
                    // Older deployed Supabase projects may not yet have the new
                    // counter-aware RPC. Preserve catalog intake through legacy.
                    val message = error.message.orEmpty()
                    val missingRpc = message.contains("function", ignoreCase = true) &&
                        (message.contains("not found", ignoreCase = true) || message.contains("does not exist", ignoreCase = true)) ||
                        message.contains("PGRST202", ignoreCase = true) || message.contains("404")
                    if (missingRpc) remote.addFromCatalog(activeBusiness!!.shopId, catalogId, quantity, priceKsh, unitCostKsh)
                    else throw error
                }
            }
            syncBusiness(activeBusiness!!)
            true
        }

    suspend fun restock(code: String, quantity: Int, unitCostKsh: Int, selectedCounterId: String? = null): Boolean =
        withActiveProduct(code) { product ->
            val counterId = selectedCounterId?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Choose the receiving counter first.")
            remote.receiveStockAtCounter(counterId, product.remoteId!!, quantity, unitCostKsh)
            syncBusiness(activeBusiness!!)
            true
        }

    suspend fun setStock(code: String, newStock: Int, selectedCounterId: String? = null): Boolean =
        withActiveProduct(code) { product ->
            val counterId = selectedCounterId?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Choose a counter before changing its stock count.")
            remote.adjustCounterStock(counterId, product.remoteId!!, newStock, "Stock count edited in mobile app")
            syncBusiness(activeBusiness!!)
            true
        }

    suspend fun updateProduct(product: ProductEntity, selectedCounterId: String? = null): Boolean =
        withActiveProduct(product.catalogCode) { current ->
            dao.updateProduct(product)
            val payload = JSONObject()
                .put("name", product.name)
                .put("price", product.sellingPriceKsh)
                .put("brand", product.brand)
                .put("size", product.size)
                .put("category", product.category)
            if (!product.verifiedBarcode.isNullOrBlank()) {
                payload.put("barcode", product.verifiedBarcode)
            }
            remote.updateProduct(current.remoteId!!, payload)
            val counterId = selectedCounterId?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Choose a counter before changing its stock count.")
            if (activeBusiness!!.counterInventoryMode == "per_counter" || product.stockQuantity != current.stockQuantity) {
                remote.adjustCounterStock(counterId, current.remoteId!!, product.stockQuantity, "Product edit stock count")
            }
            syncBusiness(activeBusiness!!)
            true
        }

    suspend fun undoLatestSale(): Boolean = withActiveBusiness {
        remote.undoSaleFromCounter(activeBusiness!!.shopId)
        syncBusiness(activeBusiness!!)
        true
    }

    suspend fun updatePhoto(product: ProductEntity, uri: String): Boolean {
        // The local URI is intentionally not treated as a backend authority.
        // A future upload flow can put it in Storage and then PATCH photo_path.
        return dao.updatePhotoOverride(product.catalogCode, uri) == 1
    }

    suspend fun assignBarcode(code: String, barcode: String): Boolean =
        withActiveProduct(code) { product ->
            remote.updateProduct(product.remoteId!!, JSONObject().put("barcode", barcode.trim()))
            syncBusiness(activeBusiness!!)
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
        val code = barcode.trim().ifBlank { "PROD_" + UUID.randomUUID().toString().take(8) }
        val counterId = selectedCounterId?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Choose the receiving counter first.")
        remote.registerProductAtCounter(
            shopId = activeBusiness!!.shopId,
            code = code,
            name = name.trim(),
            category = category.trim().ifBlank { "Beverage" },
            price = priceKsh,
            quantity = initialStock,
            unitCost = unitCostKsh,
            brand = brand,
            size = size,
            counterId = counterId
        )
        syncBusiness(activeBusiness!!)
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
            if (product.remoteId.isNullOrBlank() || !product.inMyStock) {
                throw IllegalStateException("Product is not in the active business stock")
            }
            block(product)
        }
    }

    private suspend fun clearLocalSession() {
        activeBusiness = null
        account = null
        _serviceCounters.value = emptyList()
        offlineAccountPrefs.edit().clear().apply()
        dao.clearTenantCache()
        remote.clearSession()
        _session.value = SessionSnapshot()
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
