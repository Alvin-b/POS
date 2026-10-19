package com.alvinb.counterpos.data

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
import com.alvinb.counterpos.data.remote.SupabaseRestClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
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
    private val dao: CounterPosDao,
    private val assets: AssetManager,
    private val remote: SupabaseRestClient
) {
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
    private val defaultCounters = listOf(
        ServiceCounter("counter_1", "Counter 1"),
        ServiceCounter("counter_2", "Counter 2"),
        ServiceCounter("counter_3", "Counter 3"),
        ServiceCounter("counter_4", "Counter 4")
    )
    private val _serviceCounters = MutableStateFlow<List<ServiceCounter>>(defaultCounters)
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
        loadAccount(autoSelectFirstBusiness = true)
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
        val isLiquorStore = biz.businessType == "counter_seller"
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

    suspend fun canOperateNow(): Boolean = withContext(Dispatchers.IO) {
        val shop = activeBusiness ?: return@withContext true
        if (shop.role == "owner") return@withContext true
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
        try {
            remote.createServiceCounter(activeBusiness!!.shopId, name).also { _serviceCounters.value = _serviceCounters.value + it }
        } catch (_: Exception) {
            val local = ServiceCounter("counter_${System.currentTimeMillis()}", name.trim())
            _serviceCounters.value = _serviceCounters.value + local
            local
        }
    }

    suspend fun loadCounterStock(counterId: String): Map<String, Int> = withActiveBusiness {
        remote.counterStock(activeBusiness!!.shopId, counterId)
    }

    suspend fun renameServiceCounter(counterId: String, name: String): List<ServiceCounter> = withActiveBusiness {
        remote.renameServiceCounter(activeBusiness!!.shopId, counterId, name)
        remote.serviceCounters(activeBusiness!!.shopId).also { _serviceCounters.value = it }
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
        return publishAccount()
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
        dao.clearTenantCache()
        seedCatalogIfEmpty()
        val remoteCounters = runCatching { remote.serviceCounters(membership.shopId) }.getOrDefault(emptyList())
        val counters = if (remoteCounters.isNotEmpty()) {
            remoteCounters
        } else if (membership.businessType == "counter_seller") {
            listOf(ServiceCounter("counter_1", "Main Counter"))
        } else {
            val count = membership.counterCount.coerceIn(1, 50)
            (1..count).map { num ->
                ServiceCounter("counter_$num", "Counter $num")
            }
        }
        _serviceCounters.value = counters

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
        dao.insertProducts(catalogProducts)

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
        dao.insertProducts(products)

        val sales = buildList {
            val rows = remote.sales(membership.shopId)
            for (index in 0 until rows.length()) {
                rows.optJSONObject(index)?.let { row ->
                    val remoteId = row.optString("id")
                    val cId = row.optNullableString("counter_id")
                    val cName = counters.firstOrNull { it.id == cId }?.name ?: row.optNullableString("counter_name") ?: "Counter 1"
                    add(
                        SaleEntity(
                            id = stableLocalId(remoteId),
                            catalogCode = row.optString("product_id"),
                            productName = row.optString("product_name"),
                            imageAssetName = "product_stock_001",
                            unitName = row.optString("unit_name", "Bottle"),
                            quantity = row.optInt("qty", 1),
                            unitPriceKsh = row.optInt("unit_price", 0),
                            totalKsh = row.optInt("total", 0),
                            createdAt = parseTimestamp(row.optString("created_at")),
                            undone = !row.isNull("undone_at"),
                            remoteId = remoteId,
                            counterId = cId,
                            counterName = cName,
                            waiterName = row.optNullableString("waiter_name") ?: row.optNullableString("served_by") ?: "Amina",
                            tableId = row.optNullableString("table_id"),
                            tableName = row.optNullableString("table_name")
                        )
                    )
                }
            }
        }
        dao.clearSales()
        dao.insertSales(sales)
        val tables = buildList {
            // Tables are optional for liquor stores and older Supabase projects may
            // not have the Tables migration yet. Do not block passkey/POS startup.
            val rows = runCatching { remote.diningTables(membership.shopId) }.getOrDefault(JSONArray())
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
            val rows = runCatching { remote.tableOrders(membership.shopId) }.getOrDefault(JSONArray())
            for (index in 0 until rows.length()) {
                rows.optJSONObject(index)?.let { row ->
                    val cId = row.optNullableString("counter_id")
                    val cName = counters.firstOrNull { it.id == cId }?.name ?: "Counter 1"
                    add(
                        TableOrderEntity(
                            tableId = row.optString("table_id"),
                            saleId = stableLocalId(row.optString("sale_id")),
                            catalogCode = row.optString("product_id"),
                            productName = row.optString("product_name"),
                            unitName = row.optString("unit_name", "Bottle"),
                            quantity = row.optInt("qty", 1),
                            unitPriceKsh = row.optInt("unit_price", 0),
                            totalKsh = row.optInt("total", 0),
                            servedAt = parseTimestamp(row.optString("served_at")),
                            counterId = cId,
                            counterName = cName,
                            waiterName = row.optNullableString("waiter_name") ?: "Amina"
                        )
                    )
                }
            }
        }
        dao.clearTableOrders()
        dao.clearDiningTables()
        dao.insertDiningTables(tables)
        orders.forEach { dao.insertTableOrder(it) }
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
        if (dao.getDiningTableCount() == 0) {
            val defaultTables = listOf(
                DiningTableEntity("table_1", "Table 1", System.currentTimeMillis()),
                DiningTableEntity("table_2", "Table 2", System.currentTimeMillis() + 1),
                DiningTableEntity("table_3", "Table 3", System.currentTimeMillis() + 2),
                DiningTableEntity("table_4", "Table 4", System.currentTimeMillis() + 3),
                DiningTableEntity("table_5", "Table 5", System.currentTimeMillis() + 4)
            )
            dao.insertDiningTables(defaultTables)
        }
    }

    suspend fun createDiningTable(name: String): DiningTableEntity {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Enter a name for the table." }
        require(cleanName.length <= 40) { "Table names must be 40 characters or fewer." }
        return try {
            withActiveBusiness {
                require(dao.getDiningTableByName(cleanName) == null) { "A table with this name already exists." }
                val row = remote.createDiningTable(activeBusiness!!.shopId, cleanName)
                val table = DiningTableEntity(
                    id = row.optString("id"),
                    name = row.optString("name", cleanName),
                    createdAt = parseTimestamp(row.optString("created_at"))
                )
                dao.insertDiningTable(table)
                table
            }
        } catch (_: Exception) {
            val localTable = DiningTableEntity(
                id = "table_${System.currentTimeMillis()}",
                name = cleanName,
                createdAt = System.currentTimeMillis()
            )
            dao.insertDiningTable(localTable)
            localTable
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
        val counterId = selectedCounterId?.takeIf(String::isNotBlank)
            ?: _serviceCounters.value.firstOrNull()?.id ?: "counter_1"
        val resolvedCounterName = counterName ?: _serviceCounters.value.firstOrNull { it.id == counterId }?.name ?: "Counter 1"
        val resolvedWaiter = waiterName?.takeIf(String::isNotBlank) ?: account?.email?.substringBefore("@") ?: "Amina"
        val now = System.currentTimeMillis()
        val tableName = dao.getDiningTable(tableId)?.name ?: "Table"

        try {
            remote.recordTableSaleFromCounter(activeBusiness!!.shopId, tableId, product.remoteId!!, counterId, quantity, unitName, unitPriceKsh)
            syncBusiness(activeBusiness!!)
        } catch (_: Exception) {
            dao.completeTableSaleWithUnit(
                tableId = tableId,
                code = code,
                quantity = quantity,
                unitName = unitName,
                unitPriceKsh = unitPriceKsh,
                createdAt = now,
                counterId = counterId,
                counterName = resolvedCounterName,
                waiterName = resolvedWaiter,
                tableName = tableName
            )
        }
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
        val counterId = selectedCounterId?.takeIf(String::isNotBlank)
            ?: _serviceCounters.value.firstOrNull()?.id ?: "counter_1"
        val resolvedCounterName = counterName ?: _serviceCounters.value.firstOrNull { it.id == counterId }?.name ?: "Counter 1"
        val resolvedWaiter = waiterName?.takeIf(String::isNotBlank) ?: account?.email?.substringBefore("@") ?: "Amina"
        val now = System.currentTimeMillis()

        try {
            remote.recordSaleUnitFromCounter(product.remoteId!!, counterId, quantity, unitName, unitPriceKsh)
            syncBusiness(activeBusiness!!)
        } catch (_: Exception) {
            dao.completeSaleWithUnit(
                code = code,
                quantity = quantity,
                unitName = unitName,
                unitPriceKsh = unitPriceKsh,
                createdAt = now,
                counterId = counterId,
                counterName = resolvedCounterName,
                waiterName = resolvedWaiter,
                tableId = tableId,
                tableName = tableName
            )
        }
        true
    }

    suspend fun addFromLibrary(code: String, quantity: Int, priceKsh: Int, unitCostKsh: Int, selectedCounterId: String? = null): Boolean =
        withActiveBusiness {
            val catalogId = dao.getProductByCode(code)?.remoteId ?: return@withActiveBusiness false
            val counterId = selectedCounterId?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Choose the receiving counter first.")
            remote.addFromCatalogAtCounter(activeBusiness!!.shopId, catalogId, counterId, quantity, priceKsh, unitCostKsh)
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
        _serviceCounters.value = defaultCounters
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
