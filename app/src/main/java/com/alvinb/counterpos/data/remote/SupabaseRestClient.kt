package com.alvinb.counterpos.data.remote

import android.content.Context
import com.alvinb.counterpos.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.net.HttpURLConnection
import java.net.URL

 data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String?
)

data class BusinessMembership(
    val shopId: String,
    val shopName: String,
    val role: String,
    val status: String,
    val employmentStatus: String = "active",
    val businessType: String = "big_club",
    val businessSize: String = "medium",
    val counterInventoryMode: String = "shared",
    val counterCount: Int = 1,
    val featureFlags: Set<String> = setOf("tables", "employee_management", "working_hours", "fifo_inventory", "sales_analytics"),
    val currency: String,
    val lowStockThreshold: Int
)

data class AccountSnapshot(
    val userId: String,
    val email: String,
    val isPlatformAdmin: Boolean,
    val memberships: List<BusinessMembership>
)

data class AdminBusiness(
    val shopId: String,
    val shopName: String,
    val status: String,
    val businessType: String,
    val businessSize: String,
    val featureFlags: Set<String>,
    val ownerEmail: String,
    val memberCount: Long,
    val productCount: Long,
    val stockUnits: Long,
    val createdAt: String,
    val suspendedAt: String?,
    val closedAt: String?,
    val lifecycleReason: String?,
    val counterInventoryMode: String = "shared",
    val counterCount: Long = 1
)

data class AdminAuditEvent(
    val action: String,
    val entityType: String,
    val actorUserId: String?,
    val metadata: String,
    val createdAt: String
)

data class ServiceCounter(
    val id: String,
    val name: String,
    val active: Boolean = true
)

data class CounterReportRow(
    val counterId: String?,
    val counterName: String,
    val saleCount: Long,
    val units: Long,
    val revenueKsh: Long
)

class SupabaseRestClient(context: Context) {
    private val sessionStore = EncryptedSessionStore(context)
    private var session: AuthSession? = loadSession()

    val hasSession: Boolean
        get() = session != null

    suspend fun signIn(email: String, password: String): AuthSession =
        authenticate("password", email, password)

    suspend fun redeemSuperadminToken(token: String): AuthSession = withContext(Dispatchers.IO) {
        require(token.trim().isNotBlank()) { "Enter the current Superadmin token." }
        val json = parseObject(request("/functions/v1/redeem-superadmin-token", "POST", JSONObject().put("token", token.trim()).toString(), false))
        val accessToken = json.optString("access_token").takeIf(String::isNotBlank)
            ?: throw IOException(json.optString("error", "Invalid or expired Superadmin token"))
        val refreshToken = json.optString("refresh_token").takeIf(String::isNotBlank)
            ?: throw IOException("Superadmin token response did not include a refresh session")
        val user = json.optJSONObject("user")
        val created = AuthSession(accessToken, refreshToken, user?.optString("id").orEmpty(), user?.optString("email").takeIf { !it.isNullOrBlank() })
        saveSession(created)
        created
    }

    suspend fun redeemAccessPass(passkey: String): AuthSession = withContext(Dispatchers.IO) {
        val json = parseObject(request("/functions/v1/redeem-access-pass", "POST", JSONObject().put("passkey", passkey.trim()).toString(), false))
        val email = json.optString("email").takeIf(String::isNotBlank)
            ?: throw IOException(json.optString("error", "Invalid passkey"))
        authenticate("password", email, json.optString("password"))
    }

    suspend fun createAccessPass(shopId: String, role: String): JSONObject =
        parseObject(request("/functions/v1/create-access-pass", "POST", JSONObject().put("shop_id", shopId).put("role", role).toString(), true))

    suspend fun createAccessChallenge(): JSONObject =
        parseObject(request("/functions/v1/create-access-challenge", "POST", "{}", false))

    suspend fun pollAccessChallenge(token: String): JSONObject =
        parseObject(request("/functions/v1/poll-access-challenge", "POST", JSONObject().put("token", token).toString(), false))

    suspend fun approveAccessChallenge(token: String, shopId: String, role: String): JSONObject =
        parseObject(request("/functions/v1/approve-access-challenge", "POST", JSONObject().put("token", token).put("shop_id", shopId).put("role", role).toString(), true))

    suspend fun listShopMembers(shopId: String): JSONArray =
        rpcArray("admin_list_shop_members", JSONObject().put("_shop", shopId))

    suspend fun setEmployeeStatus(shopId: String, userId: String, status: String, reason: String?): JSONObject =
        rpcVoid("set_employee_status", JSONObject().put("_shop", shopId).put("_user", userId).put("_status", status).putNullable("_reason", reason))

    suspend fun setEmployeeRole(shopId: String, userId: String, role: String): JSONObject =
        rpcVoid("set_employee_role", JSONObject().put("_shop", shopId).put("_user", userId).put("_role", role))

    suspend fun removeEmployeeAccess(shopId: String, userId: String, reason: String?): JSONObject =
        rpcVoid("remove_employee_access", JSONObject().put("_shop", shopId).put("_user", userId).putNullable("_reason", reason))

    suspend fun updateShopName(shopId: String, name: String): JSONObject =
        rpcVoid("update_shop_name", JSONObject().put("_shop", shopId).put("_name", name))

    suspend fun setEmployeeWorkingHours(shopId: String, userId: String, weekday: Int, start: String, end: String, enabled: Boolean): JSONObject =
        rpcVoid("set_employee_working_hours", JSONObject().put("_shop", shopId).put("_user", userId).put("_weekday", weekday).put("_start", start).put("_end", end).put("_enabled", enabled))

    suspend fun clearEmployeeSchedule(shopId: String, userId: String): JSONObject =
        rpcVoid("clear_employee_schedule", JSONObject().put("_shop", shopId).put("_user", userId))

    suspend fun canSellForShop(shopId: String): Boolean =
        rpcBoolean("can_sell_for_shop", JSONObject().put("_shop", shopId))

    suspend fun signUp(email: String, password: String): AuthSession? = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("email", email.trim()).put("password", password)
        val response = requestRaw(
            path = "/auth/v1/signup",
            method = "POST",
            body = payload.toString(),
            authenticated = false
        )
        val json = parseObject(response.body)
        val accessToken = json.optString("access_token").takeIf(String::isNotBlank)
        val refreshToken = json.optString("refresh_token").takeIf(String::isNotBlank)
        val user = json.optJSONObject("user")
        if (accessToken == null || refreshToken == null || user == null) {
            // Supabase commonly requires email confirmation and returns a user
            // without a session. The caller can show the confirmation notice.
            return@withContext null
        }
        val created = AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = user.optString("id"),
            email = user.optString("email").takeIf(String::isNotBlank)
        )
        saveSession(created)
        created
    }

    suspend fun restoreSession(): AuthSession? = withContext(Dispatchers.IO) {
        session?.let { current ->
            runCatching { request("/auth/v1/user", "GET", null, true) }
                .onFailure { if (it is UnauthorizedException) refreshSession() }
        }
        session
    }

    fun clearSession() {
        session = null
        sessionStore.clear()
    }

    suspend fun account(): AccountSnapshot {
        val json = rpcObject("my_account", JSONObject())
        val memberships = buildList {
            val rows = json.optJSONArray("memberships") ?: JSONArray()
            for (index in 0 until rows.length()) {
                rows.optJSONObject(index)?.let { row ->
                    val bType = row.optString("business_type", "big_club")
                    val counterCount = row.optInt("counter_count", 1)
                    val defaultFlags = if (bType == "counter_seller") {
                        setOf("employee_management", "working_hours", "fifo_inventory", "sales_analytics")
                    } else {
                        setOf("tables", "employee_management", "working_hours", "fifo_inventory", "sales_analytics")
                    }
                    val rawFlags = row.optJSONObject("feature_flags")?.keys()?.asSequence()?.filter { key -> row.optJSONObject("feature_flags")?.optBoolean(key, false) == true }?.toSet()
                    val resolvedFlags = if (rawFlags != null && rawFlags.isNotEmpty()) {
                        if (bType == "counter_seller") rawFlags - "tables" else rawFlags
                    } else defaultFlags
                    add(
                        BusinessMembership(
                            shopId = row.optString("shop_id"),
                            shopName = row.optString("shop_name"),
                            role = row.optString("role"),
                            status = row.optString("status", "active"),
                            employmentStatus = row.optString("employment_status", "active"),
                            businessType = bType,
                            businessSize = row.optString("business_size", "medium"),
                            counterInventoryMode = row.optString("counter_inventory_mode", "shared"),
                            counterCount = counterCount,
                            featureFlags = resolvedFlags,
                            currency = row.optString("currency", "KES"),
                            lowStockThreshold = row.optInt("low_stock_threshold", 5)
                        )
                    )
                }
            }
        }
        return AccountSnapshot(
            userId = json.optString("user_id"),
            email = json.optString("email"),
            isPlatformAdmin = json.optBoolean("is_platform_admin"),
            memberships = memberships
        )
    }

    suspend fun createShop(name: String): JSONObject =
        rpcObject("create_shop", JSONObject().put("_name", name.trim()))

    suspend fun products(shopId: String): JSONArray = query(
        "products?select=*&shop_id=eq.$shopId&archived=eq.false&order=name.asc"
    )

    suspend fun catalog(): JSONArray = query("catalog_items?select=*&order=name.asc")

    suspend fun sales(shopId: String): JSONArray = query(
        "sales?select=*&shop_id=eq.$shopId&order=created_at.desc&limit=200"
    )

    suspend fun serviceCounters(shopId: String): List<ServiceCounter> = buildList {
        val rows = query("service_counters?select=id,name,active&shop_id=eq.$shopId&active=eq.true&order=name.asc")
        for (index in 0 until rows.length()) rows.optJSONObject(index)?.let { row ->
            add(ServiceCounter(row.optString("id"), row.optString("name"), row.optBoolean("active", true)))
        }
    }

    suspend fun counterStock(shopId: String, counterId: String): Map<String, Int> = buildMap {
        val rows = rpcArray("counter_stock_report", JSONObject().put("_shop", shopId).put("_counter", counterId))
        for (index in 0 until rows.length()) rows.optJSONObject(index)?.let { row ->
            put(row.optString("product_id"), row.optInt("quantity"))
        }
    }

    suspend fun createServiceCounter(shopId: String, name: String): ServiceCounter {
        val row = rpcObject("create_service_counter", JSONObject().put("_shop", shopId).put("_name", name.trim()))
        return ServiceCounter(row.optString("id"), row.optString("name", name.trim()), row.optBoolean("active", true))
    }

    suspend fun renameServiceCounter(shopId: String, counterId: String, name: String): ServiceCounter {
        val row = rpcObject("rename_service_counter", JSONObject().put("_shop", shopId).put("_counter", counterId).put("_name", name.trim()))
        return ServiceCounter(row.optString("id", counterId), row.optString("name", name.trim()), row.optBoolean("active", true))
    }

    suspend fun counterSalesReport(shopId: String, fromIso: String, toIso: String): List<CounterReportRow> = buildList {
        val rows = rpcArray("counter_sales_report", JSONObject().put("_shop", shopId).put("_from", fromIso).put("_to", toIso))
        for (index in 0 until rows.length()) rows.optJSONObject(index)?.let { row ->
            add(CounterReportRow(
                counterId = row.optString("counter_id").takeIf(String::isNotBlank),
                counterName = row.optString("counter_name", "Unassigned"),
                saleCount = row.optLong("sale_count"),
                units = row.optLong("units"),
                revenueKsh = row.optLong("revenue")
            ))
        }
    }

    suspend fun diningTables(shopId: String): JSONArray = query(
        "dining_tables?select=*&shop_id=eq.$shopId&order=created_at.asc,name.asc"
    )

    suspend fun tableOrders(shopId: String): JSONArray = query(
        "table_orders?select=*&shop_id=eq.$shopId&order=served_at.asc"
    )

    suspend fun createDiningTable(shopId: String, name: String): JSONObject =
        rpcObject("create_dining_table", JSONObject().put("_shop", shopId).put("_name", name.trim()))

    suspend fun recordTableSale(
        shopId: String,
        tableId: String,
        productId: String,
        quantity: Int,
        unitName: String,
        unitPrice: Int
    ): JSONObject = rpcObject(
        "record_table_sale",
        JSONObject()
            .put("_shop", shopId)
            .put("_table", tableId)
            .put("_product", productId)
            .put("_qty", quantity)
            .put("_unit_name", unitName)
            .put("_unit_price", unitPrice)
    )

    suspend fun recordTableSaleFromCounter(
        shopId: String, tableId: String, productId: String, counterId: String,
        quantity: Int, unitName: String, unitPrice: Int
    ): JSONObject = rpcObject(
        "record_table_sale_from_counter",
        JSONObject().put("_shop", shopId).put("_table", tableId).put("_product", productId)
            .put("_counter", counterId).put("_qty", quantity).put("_unit_name", unitName).put("_unit_price", unitPrice)
    )

    suspend fun recordSaleUnit(productId: String, quantity: Int, unitName: String, unitPrice: Int): JSONObject =
        rpcObject(
            "record_sale_unit",
            JSONObject()
                .put("_product", productId)
                .put("_qty", quantity)
                .put("_unit_name", unitName)
                .put("_unit_price", unitPrice)
        )

    suspend fun recordSaleUnitFromCounter(productId: String, counterId: String, quantity: Int, unitName: String, unitPrice: Int): JSONObject =
        rpcObject(
            "record_sale_unit_from_counter",
            JSONObject().put("_product", productId).put("_counter", counterId).put("_qty", quantity)
                .put("_unit_name", unitName).put("_unit_price", unitPrice)
        )

    suspend fun undoSale(shopId: String): JSONObject =
        rpcObject("undo_sale", JSONObject().put("_shop", shopId))

    suspend fun receiveStock(
        productId: String,
        quantity: Int,
        unitCost: Int,
        supplier: String? = null,
        reference: String? = null,
        notes: String? = null
    ): JSONObject = rpcObject(
        "receive_stock_with_cost",
        JSONObject()
            .put("_product", productId)
            .put("_qty", quantity)
            .put("_unit_cost", unitCost)
            .putNullable("_supplier", supplier)
            .putNullable("_reference", reference)
            .putNullable("_notes", notes)
    )

    suspend fun addFromCatalog(
        shopId: String,
        catalogId: String,
        quantity: Int,
        sellingPrice: Int,
        unitCost: Int
    ): JSONObject = rpcObject(
        "add_from_catalog_with_cost",
        JSONObject()
            .put("_shop", shopId)
            .put("_catalog", catalogId)
            .put("_qty", quantity)
            .put("_price", sellingPrice)
            .put("_unit_cost", unitCost)
    )

    suspend fun registerProduct(
        shopId: String,
        code: String,
        name: String,
        category: String,
        price: Int,
        quantity: Int,
        unitCost: Int,
        brand: String,
        size: String
    ): JSONObject = rpcObject(
        "register_product_with_cost",
        JSONObject()
            .put("_shop", shopId)
            .put("_code", code)
            .put("_name", name)
            .put("_price", price)
            .put("_qty", quantity.coerceAtLeast(1))
            .put("_photo_path", JSONObject.NULL)
            .put("_unit_cost", unitCost)
            .putNullable("_size", size.ifBlank { null })
            .put("_type", "beer")
            .putNullable("_brand", brand.ifBlank { null })
            .putNullable("_category", category.ifBlank { null })
    )

    suspend fun adjustStock(productId: String, newStock: Int, reason: String?): JSONObject =
        rpcObject(
            "adjust_stock",
            JSONObject()
                .put("_product", productId)
                .put("_new_stock", newStock)
                .putNullable("_reason", reason)
        )

    suspend fun receiveStockAtCounter(counterId: String, productId: String, quantity: Int, unitCost: Int): JSONObject =
        rpcObject("receive_stock_at_counter", JSONObject().put("_counter", counterId).put("_product", productId).put("_qty", quantity).put("_unit_cost", unitCost))

    suspend fun addFromCatalogAtCounter(shopId: String, catalogId: String, counterId: String, quantity: Int, price: Int, unitCost: Int): JSONObject =
        rpcObject("add_from_catalog_at_counter", JSONObject().put("_shop", shopId).put("_catalog", catalogId).put("_counter", counterId).put("_qty", quantity).put("_price", price).put("_unit_cost", unitCost))

    suspend fun registerProductAtCounter(shopId: String, code: String, name: String, category: String, price: Int, quantity: Int, unitCost: Int, brand: String, size: String, counterId: String): JSONObject =
        rpcObject("register_product_at_counter", JSONObject().put("_shop", shopId).put("_code", code).put("_name", name).put("_price", price).put("_qty", quantity.coerceAtLeast(1)).put("_counter", counterId).put("_photo_path", JSONObject.NULL).put("_unit_cost", unitCost).putNullable("_size", size.ifBlank { null }).put("_type", "beer").putNullable("_brand", brand.ifBlank { null }).putNullable("_category", category.ifBlank { null }))

    suspend fun adjustCounterStock(counterId: String, productId: String, newStock: Int, reason: String?): JSONObject =
        rpcObject("adjust_counter_stock", JSONObject().put("_counter", counterId).put("_product", productId).put("_new_stock", newStock).putNullable("_reason", reason))

    suspend fun transferCounterStock(shopId: String, productId: String, fromCounter: String, toCounter: String, quantity: Int, reason: String?) {
        rpcVoid("transfer_counter_stock", JSONObject().put("_shop", shopId).put("_product", productId).put("_from_counter", fromCounter).put("_to_counter", toCounter).put("_qty", quantity).putNullable("_reason", reason))
    }

    suspend fun undoSaleFromCounter(shopId: String): JSONObject =
        rpcObject("undo_sale_from_counter", JSONObject().put("_shop", shopId))

    suspend fun updateProduct(productId: String, fields: JSONObject): JSONObject =
        patch("products?id=eq.$productId", fields)

    suspend fun adminBusinesses(): List<AdminBusiness> {
        val rows = rpcArray("admin_list_businesses", JSONObject())
        return buildList {
            for (index in 0 until rows.length()) {
                rows.optJSONObject(index)?.let { row ->
                    add(
                        AdminBusiness(
                            shopId = row.optString("shop_id"),
                            shopName = row.optString("shop_name"),
                            status = row.optString("status"),
                            businessType = row.optString("business_type", "big_club"),
                            businessSize = row.optString("business_size", "medium"),
                            featureFlags = row.optJSONObject("feature_flags")?.keys()?.asSequence()?.filter { key -> row.optJSONObject("feature_flags")?.optBoolean(key, false) == true }?.toSet() ?: emptySet(),
                            ownerEmail = row.optString("owner_email"),
                            memberCount = row.optLong("member_count"),
                            productCount = row.optLong("product_count"),
                            stockUnits = row.optLong("stock_units"),
                            createdAt = row.optString("created_at"),
                            suspendedAt = row.optString("suspended_at").takeIf(String::isNotBlank),
                            closedAt = row.optString("closed_at").takeIf(String::isNotBlank),
                            lifecycleReason = row.optString("lifecycle_reason").takeIf(String::isNotBlank),
                            counterInventoryMode = row.optString("counter_inventory_mode", "shared"),
                            counterCount = row.optLong("counter_count", 1)
                        )
                    )
                }
            }
        }
    }

    suspend fun adminSetBusinessStatus(shopId: String, status: String, reason: String?): AdminBusiness? {
        rpcObject(
            "admin_set_business_status",
            JSONObject()
                .put("_shop", shopId)
                .put("_status", status)
                .putNullable("_reason", reason)
        )
        return adminBusinesses().firstOrNull { it.shopId == shopId }
    }

    suspend fun adminCreateBusiness(
        name: String,
        ownerEmail: String?,
        businessType: String,
        businessSize: String,
        inventoryMode: String,
        counterCount: Int
    ): JSONObject =
        rpcObject(
            "admin_create_business_profile",
            JSONObject().put("_name", name.trim())
                .putNullable("_owner_email", ownerEmail?.trim()?.takeIf(String::isNotBlank))
                .put("_business_type", businessType)
                .put("_business_size", businessSize)
                .put("_counter_inventory_mode", inventoryMode)
                .put("_counter_count", counterCount)
        )

    suspend fun addMemberByEmail(shopId: String, email: String, role: String): JSONObject =
        rpcObject(
            "add_member_by_email",
            JSONObject()
                .put("_shop", shopId)
                .put("_email", email.trim())
                .put("_role", role)
        )

    suspend fun auditEvents(shopId: String): List<AdminAuditEvent> {
        val rows = query("audit_events?shop_id=eq.$shopId&select=action,entity_type,actor_user_id,metadata,created_at&order=created_at.desc&limit=30")
        return buildList {
            for (index in 0 until rows.length()) rows.optJSONObject(index)?.let { row ->
                add(AdminAuditEvent(row.optString("action"), row.optString("entity_type"), row.optString("actor_user_id").takeIf(String::isNotBlank), row.optJSONObject("metadata")?.toString() ?: "{}", row.optString("created_at")))
            }
        }
    }

    private suspend fun authenticate(grantType: String, email: String, password: String): AuthSession =
        withContext(Dispatchers.IO) {
            val response = requestRaw(
                path = "/auth/v1/token?grant_type=$grantType",
                method = "POST",
                body = JSONObject().put("email", email.trim()).put("password", password).toString(),
                authenticated = false
            )
            val json = parseObject(response.body)
            val user = json.optJSONObject("user")
                ?: throw IOException(json.optString("msg", "Authentication failed"))
            val created = AuthSession(
                accessToken = json.optString("access_token"),
                refreshToken = json.optString("refresh_token"),
                userId = user.optString("id"),
                email = user.optString("email").takeIf(String::isNotBlank)
            )
            if (created.accessToken.isBlank() || created.refreshToken.isBlank()) {
                throw IOException("Authentication response did not include a session")
            }
            saveSession(created)
            created
        }

    private suspend fun query(path: String): JSONArray =
        parseArray(request("/rest/v1/$path", "GET", null, true))

    private suspend fun patch(path: String, body: JSONObject): JSONObject {
        val response = request("/rest/v1/$path", "PATCH", body.toString(), true)
        val rows = parseArray(response)
        return rows.optJSONObject(0) ?: body
    }

    private suspend fun rpcObject(name: String, args: JSONObject): JSONObject {
        val body = request("/rest/v1/rpc/$name", "POST", args.toString(), true)
        val trimmed = body.trim()
        return when {
            trimmed.startsWith("[") -> parseArray(trimmed).optJSONObject(0)
                ?: throw IOException("RPC $name returned no row")
            trimmed.startsWith("{") -> parseObject(trimmed)
            else -> {
                val clean = trimmed.removeSurrounding("\"").trim()
                JSONObject().put("shop_id", clean).put("id", clean)
            }
        }
    }

    private suspend fun rpcVoid(name: String, args: JSONObject): JSONObject {
        request("/rest/v1/rpc/$name", "POST", args.toString(), true)
        return JSONObject()
    }

    private suspend fun rpcBoolean(name: String, args: JSONObject): Boolean =
        request("/rest/v1/rpc/$name", "POST", args.toString(), true).trim().equals("true", ignoreCase = true)

    private suspend fun rpcArray(name: String, args: JSONObject): JSONArray {
        val body = request("/rest/v1/rpc/$name", "POST", args.toString(), true)
        return parseArray(body)
    }

    private suspend fun request(path: String, method: String, body: String?, authenticated: Boolean): String {
        val response = requestRaw(path, method, body, authenticated)
        return response.body
    }

    private suspend fun requestRaw(
        path: String,
        method: String,
        body: String?,
        authenticated: Boolean,
        retryOnUnauthorized: Boolean = true
    ): HttpResponse = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            doInput = true
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (authenticated) {
                val token = session?.accessToken ?: throw IOException("Not signed in")
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (status == HttpURLConnection.HTTP_UNAUTHORIZED && authenticated && retryOnUnauthorized) {
            if (refreshSession()) {
                return@withContext requestRaw(path, method, body, authenticated, false)
            }
            clearSession()
            throw UnauthorizedException("Session expired")
        }
        if (status !in 200..299) throw IOException(extractError(text, status))
        HttpResponse(status, text)
    }

    private suspend fun refreshSession(): Boolean = withContext(Dispatchers.IO) {
        val old = session ?: return@withContext false
        runCatching {
            val response = requestRaw(
                "/auth/v1/token?grant_type=refresh_token",
                "POST",
                JSONObject().put("refresh_token", old.refreshToken).toString(),
                authenticated = false,
                retryOnUnauthorized = false
            )
            val json = parseObject(response.body)
            val user = json.optJSONObject("user")
            val renewed = AuthSession(
                accessToken = json.optString("access_token"),
                refreshToken = json.optString("refresh_token", old.refreshToken),
                userId = user?.optString("id") ?: old.userId,
                email = user?.optString("email") ?: old.email
            )
            if (renewed.accessToken.isBlank()) return@runCatching false
            saveSession(renewed)
            true
        }.getOrDefault(false)
    }

    private fun saveSession(value: AuthSession) {
        session = value
        sessionStore.save(
            JSONObject()
                .put("access_token", value.accessToken)
                .put("refresh_token", value.refreshToken)
                .put("user_id", value.userId)
                .putNullable("email", value.email)
                .toString()
        )
    }

    private fun loadSession(): AuthSession? = runCatching {
        val json = sessionStore.read()?.let(::JSONObject) ?: return@runCatching null
        AuthSession(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            userId = json.getString("user_id"),
            email = json.optString("email").takeIf(String::isNotBlank)
        )
    }.getOrNull()

    private fun parseObject(body: String): JSONObject =
        JSONObject(body.ifBlank { "{}" })

    private fun parseArray(body: String): JSONArray =
        JSONArray(body.ifBlank { "[]" })

    private fun extractError(body: String, status: Int): String = runCatching {
        val json = JSONObject(body)
        json.optString("message").ifBlank {
            json.optString("error_description").ifBlank {
                json.optString("msg").ifBlank {
                    json.optString("error").ifBlank {
                        json.optString("code").ifBlank { "Request failed ($status)" }
                    }
                }
            }
        }
    }.getOrDefault(body.ifBlank { "Request failed ($status)" })

    private fun JSONObject.putNullable(key: String, value: String?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private data class HttpResponse(val status: Int, val body: String)
    private class UnauthorizedException(message: String) : IOException(message)

    private val baseUrl: String
        get() = BuildConfig.SUPABASE_URL.trimEnd('/')

    private val publishableKey: String
        get() = BuildConfig.SUPABASE_PUBLISHABLE_KEY
}
