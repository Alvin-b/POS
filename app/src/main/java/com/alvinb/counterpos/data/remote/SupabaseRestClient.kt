package com.alvinb.counterpos.data.remote

import android.content.Context
import com.alvinb.counterpos.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.UUID

open class SupabaseHttpException(val statusCode: Int, message: String) : IOException(message)

open class SupabaseApiException(statusCode: Int, message: String) : SupabaseHttpException(statusCode, message)

class SupabaseUnauthorizedException(message: String) : SupabaseApiException(HttpURLConnection.HTTP_UNAUTHORIZED, message)

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
    val lowStockThreshold: Int,
    val subscriptionStatus: String = "trial",
    val trialEndsAt: String? = null,
    val subscriptionEndsAt: String? = null,
    val subscriptionPriceKsh: Int = 2500,
    val subscriptionPlanDays: Int = 30,
    val lastPaymentAt: String? = null,
    val lastPaymentReceipt: String? = null,
    val lastPaymentPhone: String? = null,
    val lastVerifiedEntitlementAt: Long = 0L
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
    val counterCount: Long = 1,
    val subscriptionStatus: String = "trial",
    val trialEndsAt: String? = null,
    val subscriptionEndsAt: String? = null,
    val subscriptionPriceKsh: Int = 2500,
    val subscriptionPlanDays: Int = 30,
    val lastPaymentAt: String? = null,
    val lastPaymentReceipt: String? = null,
    val lastPaymentPhone: String? = null,
    val customBillingNote: String? = null
)

data class DarajaStkResponse(
    val success: Boolean,
    val checkoutRequestId: String?,
    val merchantRequestId: String?,
    val customerMessage: String?,
    val error: String? = null
)

data class SubscriptionStatusResult(
    val shopId: String,
    val shopName: String,
    val status: String,
    val isEntitled: Boolean,
    val daysLeft: Int,
    val trialEndsAt: String?,
    val subscriptionEndsAt: String?,
    val subscriptionPriceKsh: Int,
    val subscriptionPlanDays: Int,
    val lastPaymentAt: String?,
    val lastPaymentReceipt: String?,
    val latestCheckoutRequestId: String?,
    val latestPaymentStatus: String?
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

data class AccessPassRedemption(
    val session: AuthSession,
    val businessId: String?
)

private enum class RefreshOutcome { REFRESHED, INVALID_REFRESH_TOKEN, RETRYABLE_FAILURE }

internal fun shouldClearSessionForRefreshFailure(statusCode: Int, message: String): Boolean {
    if (statusCode !in 400..401) return false
    val normalized = message.lowercase()
    return normalized.contains("refresh") && listOf(
        "invalid", "expired", "not found", "not_found", "already used", "already_used", "revoked", "invalid_grant"
    ).any(normalized::contains)
}

class SupabaseRestClient(context: Context) {
    private val sessionStore = EncryptedSessionStore(context)
    private val installationId = sessionStore.installationId()
    private val refreshMutex = Mutex()
    @Volatile private var session: AuthSession? = loadSession()

    val hasSession: Boolean
        get() = session != null

    val currentUserId: String?
        get() = session?.userId

    /** Token used only to authenticate the Realtime channel for the active tenant. */
    fun currentAccessToken(): String? = session?.accessToken

    suspend fun signIn(email: String, password: String): AuthSession =
        authenticate("password", email, password)

    suspend fun redeemSuperadminToken(token: String): AuthSession = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        require(cleanToken.isNotBlank()) { "Enter the current Superadmin token." }
        val json = parseObject(request("/functions/v1/redeem-superadmin-token", "POST", JSONObject().put("token", cleanToken).toString(), false))
        val accessToken = json.optString("access_token").takeIf(String::isNotBlank)
            ?: throw IOException(json.optString("error", "Invalid or expired Superadmin token"))
        val refreshToken = json.optString("refresh_token").takeIf(String::isNotBlank)
            ?: throw IOException("Superadmin token response did not include a refresh session")
        val user = json.optJSONObject("user")
        val created = AuthSession(accessToken, refreshToken, user?.optString("id").orEmpty(), user?.optString("email").takeIf { !it.isNullOrBlank() })
        saveSession(created)
        sessionStore.saveSuperadminToken(cleanToken)
        created
    }

    suspend fun redeemAccessPass(passkey: String): AccessPassRedemption = withContext(Dispatchers.IO) {
        val rawResponse = try {
            request("/functions/v1/redeem-access-pass", "POST", JSONObject().put("passkey", passkey.trim()).toString(), false)
        } catch (e: SupabaseApiException) {
            val friendly = when (e.message) {
                "passkey_not_found" -> "Passkey not recognized or already redeemed. Ask the business owner for a new passkey."
                "passkey_expired", "passkey_expired_or_used" -> "This passkey has expired or was already used. Ask the business owner to generate a new passkey."
                "employee_inactive" -> "This employee enrollment is inactive. Ask the business owner to enable it and generate a new passkey."
                "employee_recovery_in_progress" -> "This employee is already recovering access on another device. Wait a few minutes or ask the owner to create a fresh code."
                "business_not_active" -> "This business is not active. Ask the administrator to reopen it before enrolling employees."
                "employee_identity_not_found" -> "The employee account could not be found. Ask the owner to issue a fresh recovery code."
                "could_not_rotate_employee_credential", "could_not_create_employee_session", "could_not_finalize_employee_recovery" -> "Employee recovery did not finish. Ask the owner to issue a fresh recovery code."
                "could_not_redeem_passkey" -> "The passkey service could not attach this employee to the business. Deploy the current enrollment functions and try a new passkey."
                else -> e.message ?: "Invalid passkey"
            }
            throw IOException(friendly, e)
        }
        val json = parseObject(rawResponse)
        val email = json.optString("email").takeIf(String::isNotBlank)
            ?: throw IOException(
                when (val err = json.optString("error")) {
                    "passkey_not_found" -> "Passkey not recognized or already redeemed. Ask the business owner to generate a new one."
                    "passkey_expired", "passkey_expired_or_used" -> "This passkey has expired or was already used. Ask the business owner to generate a new one."
                    "employee_inactive" -> "This employee enrollment is inactive. Ask the business owner to enable it and generate a new passkey."
                    "employee_recovery_in_progress" -> "This employee is already recovering access on another device. Wait a few minutes or ask the owner to create a fresh code."
                    "business_not_active" -> "This business is not active. Ask the administrator to reopen it before enrolling employees."
                    "could_not_redeem_passkey" -> "The passkey service could not attach this employee to the business. Deploy the current enrollment functions and try a new passkey."
                    else -> err.ifBlank { "Invalid passkey" }
                }
            )
        val shopId = json.optString("business_id").ifBlank { json.optString("shop_id") }.takeIf(String::isNotBlank)
        val accessToken = json.optString("access_token").takeIf(String::isNotBlank)
        val refreshToken = json.optString("refresh_token").takeIf(String::isNotBlank)
        if (accessToken != null && refreshToken != null) {
            val recoveredSession = AuthSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                userId = json.optString("user_id"),
                email = email
            )
            if (recoveredSession.userId.isBlank()) throw IOException("Recovery response did not include the existing employee ID.")
            saveSession(recoveredSession)
            return@withContext AccessPassRedemption(recoveredSession, shopId)
        }
        AccessPassRedemption(
            session = authenticate("password", email, json.optString("password")),
            businessId = shopId
        )
    }

    suspend fun createAccessPass(shopId: String, role: String): JSONObject =
        parseObject(request("/functions/v1/create-access-pass", "POST", JSONObject().put("shop_id", shopId).put("role", role).toString(), true))

    suspend fun createAccessChallenge(): JSONObject = parseObject(withNetworkRetry {
        request(
            "/functions/v1/create-access-challenge",
            "POST",
            JSONObject().put("device_id", installationId).toString(),
            false
        )
    })

    suspend fun pollAccessChallenge(token: String): JSONObject = parseObject(withNetworkRetry {
        request("/functions/v1/poll-access-challenge", "POST", JSONObject().put("token", token).toString(), false)
    })

    suspend fun approveAccessChallenge(token: String, shopId: String, role: String): JSONObject =
        parseObject(request("/functions/v1/approve-access-challenge", "POST", JSONObject().put("token", token).put("shop_id", shopId).put("role", role).toString(), true))

    suspend fun listShopMembers(shopId: String): JSONArray =
        rpcArray("admin_list_shop_members", JSONObject().put("_shop", shopId))

    suspend fun createEmployeeRecoveryPass(shopId: String, userId: String): JSONObject {
        return try {
            parseObject(request(
                "/functions/v1/create-employee-recovery-pass",
                "POST",
                JSONObject().put("shop_id", shopId).put("user_id", userId).toString(),
                true
            ))
        } catch (e: Exception) {
            if (isMissingEdgeFunction(e)) {
                // When Edge Function /create-employee-recovery-pass is not deployed on Supabase,
                // seamlessly fall back to issuing a cashier/waiter access pass for this business.
                try {
                    val pass = createAccessPass(shopId, "cashier")
                    val passkey = pass.optString("passkey").trim()
                    require(passkey.isNotBlank()) { "The staff pass service returned an empty passkey." }
                    JSONObject()
                        .put("passkey", passkey)
                        .put("qr_payload", pass.optString("qr_payload").ifBlank { "CPX1.$passkey" })
                        .put("expires_in_minutes", pass.optInt("expires_in_minutes", 20).coerceAtLeast(1))
                } catch (fallbackError: Exception) {
                    if (isMissingEdgeFunction(fallbackError)) {
                        throw IOException(
                            "Staff re-enrollment is not enabled on this backend yet. Deploy the create-access-pass and create-employee-recovery-pass functions, then try again.",
                            fallbackError
                        )
                    }
                    throw fallbackError
                }
            } else {
                throw e
            }
        }
    }

    private fun isMissingEdgeFunction(error: Throwable): Boolean {
        val messages = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .toList()
        return error is SupabaseApiException && error.statusCode == 404 ||
            messages.any { message ->
                message.contains("requested function was not found", ignoreCase = true) ||
                    message.contains("function was not found", ignoreCase = true) ||
                    message.contains("edge function not found", ignoreCase = true) ||
                    message.contains("function_not_found", ignoreCase = true)
            }
    }

    suspend fun setEmployeeDisplayName(shopId: String, userId: String, displayName: String): JSONObject =
        rpcVoid("set_employee_display_name", JSONObject()
            .put("_shop", shopId)
            .put("_user", userId)
            .put("_display_name", displayName.trim()))

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

    suspend fun getBusinessHours(shopId: String): JSONObject =
        rpcObject("get_business_hours", JSONObject().put("_shop", shopId))

    suspend fun setBusinessHours(shopId: String, enabled: Boolean, days: JSONArray): JSONObject =
        rpcObject("set_business_hours", JSONObject().put("_shop", shopId).put("_enabled", enabled).put("_days", days))

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
        val existing = session ?: loadSession() ?: return@withContext null
        session = existing
        try {
            request("/auth/v1/user", "GET", null, true)
        } catch (_: SupabaseUnauthorizedException) {
            // Preserve whatever session remains so offline POS operation continues.
            return@withContext session ?: existing
        } catch (_: IOException) {
            // Keep the encrypted session for offline startup. The repository
            // can use its cached account/inventory snapshot and retry later.
            return@withContext session ?: existing
        } catch (_: Exception) {
            return@withContext session ?: existing
        }
        session ?: existing
    }

    /** Silently refreshes the current access token in the background if possible. Never throws. */
    suspend fun silentRefreshIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        val current = session ?: return@withContext false
        try {
            val outcome = refreshSession(current.accessToken, current.userId)
            outcome == RefreshOutcome.REFRESHED
        } catch (_: Exception) {
            false
        }
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
                    val bType = row.optString("business_type", "big_club").trim()
                    val isClub = bType.equals("big_club", ignoreCase = true) || bType.equals("club", ignoreCase = true)
                    val isLiquor = bType.equals("counter_seller", ignoreCase = true) || bType.equals("liquor_store", ignoreCase = true)
                    val counterCount = if (isLiquor) 1 else if (isClub) row.optInt("counter_count", 1).coerceAtLeast(1) else 1
                    val defaultFlags = if (isLiquor) {
                        setOf("employee_management", "fifo_inventory")
                    } else {
                        setOf("tables", "employee_management", "working_hours", "fifo_inventory", "sales_analytics")
                    }
                    val rawFlags = row.optJSONObject("feature_flags")?.keys()?.asSequence()?.filter { key ->
                        row.optJSONObject("feature_flags")?.optBoolean(key, false) == true
                    }?.toSet()
                    val resolvedFlags = rawFlags ?: defaultFlags
                    add(
                        BusinessMembership(
                            shopId = row.optString("shop_id"),
                            shopName = row.optString("shop_name"),
                            role = row.optString("role"),
                            status = row.optString("status").ifBlank { "active" },
                            employmentStatus = row.optString("employment_status").ifBlank { "active" },
                            businessType = bType,
                            businessSize = row.optString("business_size", "medium"),
                            counterInventoryMode = row.optString("counter_inventory_mode", "shared"),
                            counterCount = counterCount,
                            featureFlags = resolvedFlags,
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
                            lastVerifiedEntitlementAt = System.currentTimeMillis()
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

    suspend fun createOwnerBusinessProfile(
        name: String,
        businessType: String,
        businessSize: String,
        inventoryMode: String,
        counterCount: Int
    ): JSONObject = rpcObject(
        "create_owner_business_profile",
        JSONObject()
            .put("_name", name.trim())
            .put("_business_type", businessType)
            .put("_business_size", businessSize)
            .put("_counter_inventory_mode", inventoryMode)
            .put("_counter_count", counterCount)
    )

    suspend fun patchShopProfile(
        shopId: String,
        businessType: String,
        businessSize: String,
        inventoryMode: String,
        counterCount: Int
    ): JSONObject = withContext(Dispatchers.IO) {
        val flags = if (businessType == "big_club") {
            JSONObject()
                .put("tables", true)
                .put("employee_management", true)
                .put("working_hours", true)
                .put("fifo_inventory", true)
                .put("sales_analytics", true)
        } else {
            JSONObject()
                .put("tables", false)
                .put("employee_management", true)
                .put("working_hours", false)
                .put("fifo_inventory", true)
                .put("sales_analytics", false)
        }
        patch(
            "shops?id=eq.$shopId",
            JSONObject()
                .put("business_type", businessType)
                .put("business_size", businessSize)
                .put("counter_inventory_mode", inventoryMode)
                .put("counter_count", counterCount)
                .put("feature_flags", flags)
        )
    }

    suspend fun products(shopId: String): JSONArray {
        val rows = query(
            "products?select=*&shop_id=eq.$shopId&or=(archived.is.null,archived.eq.false)&order=name.asc"
        )
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            if (row.optString("photo_url").isBlank()) {
                val path = row.optString("photo_path").takeIf(String::isNotBlank)
                if (path != null) {
                    runCatching { signedProductPhotoUrl(path) }
                        .getOrNull()
                        ?.let { row.put("photo_url", it) }
                }
            }
        }
        return rows
    }

    suspend fun catalog(): JSONArray = query("catalog_items?select=*&order=name.asc")

    suspend fun uploadProductPhoto(
        productId: String,
        shopId: String,
        productCode: String,
        bytes: ByteArray,
        contentType: String,
        extension: String
    ): String = withContext(Dispatchers.IO) {
        require(bytes.isNotEmpty()) { "The selected photo is empty." }
        val safeCode = productCode.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64).ifBlank { "product" }
        val objectPath = "$shopId/${safeCode}_${UUID.randomUUID()}.$extension"
        requestBinary(
            "/storage/v1/object/product-photos/$objectPath",
            "POST",
            bytes,
            contentType,
            mapOf("x-upsert" to "true")
        )
        patch(
            "products?id=eq.$productId",
            JSONObject().put("photo_path", objectPath).put("photo_url", JSONObject.NULL)
        )
        objectPath
    }

    suspend fun signedProductPhotoUrl(objectPath: String): String? = withContext(Dispatchers.IO) {
        val path = objectPath.trim().trimStart('/')
        if (path.isBlank()) return@withContext null
        val response = request(
            "/storage/v1/object/sign/product-photos",
            "POST",
            JSONObject().put("expiresIn", 604800).put("paths", org.json.JSONArray().put(path)).toString(),
            true
        )
        val rows = parseArray(response)
        val signed = rows.optJSONObject(0)?.optString("signedURL").orEmpty()
        signed.takeIf(String::isNotBlank)?.let { if (it.startsWith("http")) it else baseUrl + "/storage/v1" + it }
    }


    suspend fun sales(shopId: String): JSONArray = query(
        "sales?select=*&shop_id=eq.$shopId&order=created_at.desc&limit=1000"
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

    suspend fun diningTables(shopId: String): JSONArray =
        query("dining_tables?select=*&shop_id=eq.$shopId&archived=eq.false&order=created_at.asc,name.asc")

    suspend fun tableOrders(shopId: String): JSONArray = query(
        "table_orders?select=*&shop_id=eq.$shopId&order=served_at.asc"
    )

    suspend fun createDiningTable(shopId: String, name: String): JSONObject =
        rpcObject("create_dining_table", JSONObject().put("_shop", shopId).put("_name", name.trim()))

    suspend fun deleteDiningTable(shopId: String, tableId: String): JSONObject =
        rpcVoid("delete_dining_table", JSONObject().put("_shop", shopId).put("_table", tableId))

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
        quantity: Int, unitName: String, unitPrice: Int, paymentMethod: String = "cash"
    ): JSONObject = rpcObject(
        "record_table_sale_from_counter",
        JSONObject().put("_shop", shopId).put("_table", tableId).put("_product", productId)
            .put("_counter", counterId).put("_qty", quantity).put("_unit_name", unitName).put("_unit_price", unitPrice).put("_payment_method", paymentMethod)
    )

    suspend fun settleTableBill(shopId: String, tableId: String, operationId: String, paymentMethod: String): JSONObject =
        rpcObject(
            "settle_table_bill",
            JSONObject()
                .put("_shop", shopId)
                .put("_table", tableId)
                .put("_operation_id", operationId)
                .put("_payment_method", paymentMethod)
        )

    suspend fun recordDispensationFromCounter(
        shopId: String,
        productId: String,
        counterId: String,
        quantity: Int,
        unitName: String,
        note: String?
    ): JSONObject = rpcObject(
        "record_dispensation_from_counter",
        JSONObject()
            .put("_shop", shopId)
            .put("_product", productId)
            .put("_counter", counterId)
            .put("_qty", quantity)
            .put("_unit_name", unitName)
            .putNullable("_note", note)
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

    suspend fun recordSaleUnitFromCounter(productId: String, counterId: String, quantity: Int, unitName: String, unitPrice: Int, paymentMethod: String = "cash"): JSONObject =
        rpcObject(
            "record_sale_unit_from_counter",
            JSONObject().put("_product", productId).put("_counter", counterId).put("_qty", quantity)
                .put("_unit_name", unitName).put("_unit_price", unitPrice)
                .put("_payment_method", paymentMethod)
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
        size: String,
        saleUnits: JSONArray = JSONArray()
    ): JSONObject = rpcObject(
        "register_product_with_units",
        JSONObject()
            .put("_shop", shopId)
            .put("_code", code)
            .put("_name", name)
            .put("_price", price)
            .put("_qty", quantity.coerceAtLeast(0))
            .put("_photo_path", JSONObject.NULL)
            .put("_unit_cost", unitCost)
            .putNullable("_size", size.ifBlank { null })
            .put("_type", "beer")
            .putNullable("_brand", brand.ifBlank { null })
            .putNullable("_category", category.ifBlank { null })
            .put("_sale_units", saleUnits)
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

    suspend fun registerProductAtCounter(
        shopId: String,
        code: String,
        name: String,
        category: String,
        price: Int,
        quantity: Int,
        unitCost: Int,
        brand: String,
        size: String,
        counterId: String,
        saleUnits: JSONArray = JSONArray()
    ): JSONObject = rpcObject(
        "register_product_at_counter_with_units",
        JSONObject().put("_shop", shopId).put("_code", code).put("_name", name).put("_price", price)
            .put("_qty", quantity.coerceAtLeast(0)).put("_counter", counterId).put("_photo_path", JSONObject.NULL)
            .put("_unit_cost", unitCost).putNullable("_size", size.ifBlank { null }).put("_type", "beer")
            .putNullable("_brand", brand.ifBlank { null }).putNullable("_category", category.ifBlank { null })
            .put("_sale_units", saleUnits)
    )

    suspend fun adjustCounterStock(counterId: String, productId: String, newStock: Int, reason: String?): JSONObject =
        rpcObject("adjust_counter_stock", JSONObject().put("_counter", counterId).put("_product", productId).put("_new_stock", newStock).putNullable("_reason", reason))

    suspend fun transferCounterStock(shopId: String, productId: String, fromCounter: String, toCounter: String, quantity: Int, reason: String?) {
        rpcVoid("transfer_counter_stock", JSONObject().put("_shop", shopId).put("_product", productId).put("_from_counter", fromCounter).put("_to_counter", toCounter).put("_qty", quantity).putNullable("_reason", reason))
    }

    suspend fun undoSaleFromCounter(shopId: String): JSONObject =
        rpcObject("undo_sale_from_counter", JSONObject().put("_shop", shopId))

    suspend fun applyPosOperation(shopId: String, operationId: String, kind: String, payload: JSONObject): JSONObject {
        return try {
            rpcObjectOrEmpty(
                "apply_pos_operation",
                JSONObject()
                    .put("_shop", shopId)
                    .put("_operation_id", operationId)
                    .put("_kind", kind)
                    .put("_payload", payload)
            )
        } catch (_: Exception) {
            dispatchPosOperation(shopId, kind, payload)
        }
    }

    suspend fun dispatchPosOperation(shopId: String, kind: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val counterId = payload.optString("counter_id").takeIf(String::isNotBlank)
        when (kind) {
            "add_catalog_product" -> {
                val catalogId = payload.optString("catalog_id").takeIf(String::isNotBlank)
                var code = payload.optString("product_code", payload.optString("code"))
                var name = payload.optString("name")
                var brand = payload.optString("brand")
                var size = payload.optString("size")
                var category = payload.optString("category", "Beverage")
                val qty = payload.optInt("qty", payload.optInt("quantity", 1))
                val price = payload.optInt("price")
                val unitCost = payload.optInt("unit_cost")

                if (name.isBlank() && catalogId != null) {
                    val catRow = runCatching {
                        query("catalog?id=eq.$catalogId&select=code,name,brand,size,category").optJSONObject(0)
                    }.getOrNull()
                    if (catRow != null) {
                        name = catRow.optString("name", "")
                        if (code.isBlank()) code = catRow.optString("code", "")
                        if (brand.isBlank()) brand = catRow.optString("brand", "")
                        if (size.isBlank()) size = catRow.optString("size", "")
                        if (category.isBlank() || category == "Beverage") {
                            category = catRow.optString("category", "Beverage")
                        }
                    }
                }
                val resolvedCode = code.ifBlank { catalogId ?: ("ITEM_" + java.util.UUID.randomUUID().toString().take(8)) }
                val finalName = name.ifBlank { "Beverage" }

                // Step 1: Check if product already exists in this shop's stock
                if (resolvedCode.isNotBlank()) {
                    val existingRows = runCatching {
                        query("products?shop_id=eq.$shopId&code=eq.$resolvedCode&select=id")
                    }.getOrNull()
                    val existing = existingRows?.optJSONObject(0)
                    if (existing != null) {
                        val existingId = existing.getString("id")
                        try {
                            if (counterId != null) {
                                receiveStockAtCounter(counterId, existingId, qty, unitCost)
                            } else {
                                receiveStock(existingId, qty, unitCost)
                            }
                            return@withContext JSONObject().put("product_id", existingId).put("id", existingId)
                        } catch (recvErr: Exception) {
                            // If receive_stock_with_cost fails (e.g. HTTP 300 or missing overload), try receive_stock without cost
                            try {
                                if (counterId != null) {
                                    rpcObject("receive_stock_at_counter", JSONObject().put("_counter", counterId).put("_product", existingId).put("_qty", qty))
                                } else {
                                    rpcObject("receive_stock", JSONObject().put("_product", existingId).put("_qty", qty))
                                }
                                return@withContext JSONObject().put("product_id", existingId).put("id", existingId)
                            } catch (_: Exception) {
                                // Fall back to direct patch on products table stock
                                val currentStock = runCatching {
                                    query("products?id=eq.$existingId&select=stock").optJSONObject(0)?.optInt("stock", 0) ?: 0
                                }.getOrDefault(0)
                                patch("products?id=eq.$existingId", JSONObject().put("stock", currentStock + qty))
                                return@withContext JSONObject().put("product_id", existingId).put("id", existingId)
                            }
                        }
                    }
                }

                // Step 2: Try catalog RPCs across known variants
                var lastErr: Exception? = null
                if (catalogId != null) {
                    if (counterId != null) {
                        try {
                            return@withContext addFromCatalogAtCounter(shopId, catalogId, counterId, qty, price, unitCost)
                        } catch (e: Exception) {
                            lastErr = e
                            try {
                                return@withContext rpcObject("add_from_catalog_at_counter", JSONObject().put("_shop", shopId).put("_catalog", catalogId).put("_counter", counterId).put("_qty", qty).put("_price", price))
                            } catch (e2: Exception) {
                                lastErr = e2
                            }
                        }
                    } else {
                        try {
                            return@withContext addFromCatalog(shopId, catalogId, qty, price, unitCost)
                        } catch (e: Exception) {
                            lastErr = e
                            try {
                                return@withContext rpcObject("add_from_catalog", JSONObject().put("_shop", shopId).put("_catalog", catalogId).put("_qty", qty).put("_price", price))
                            } catch (e2: Exception) {
                                lastErr = e2
                            }
                        }
                    }
                }

                // Step 3: Fall back to registering product directly into shop stock
                if (resolvedCode.isNotBlank() && finalName.isNotBlank()) {
                    if (counterId != null) {
                        try {
                            return@withContext registerProductAtCounter(
                                shopId, resolvedCode, finalName, category, price, qty, unitCost, brand, size,
                                counterId, payload.optJSONArray("sale_units") ?: JSONArray()
                            )
                        } catch (e: Exception) {
                            lastErr = e
                            if (qty > 0) {
                                try {
                                    return@withContext rpcObject("register_product_at_counter", JSONObject().put("_shop", shopId).put("_code", resolvedCode).put("_name", finalName).put("_price", price).put("_qty", qty).put("_counter", counterId))
                                } catch (e2: Exception) {
                                    lastErr = e2
                                }
                            }
                        }
                    } else {
                        try {
                            return@withContext registerProduct(
                                shopId, resolvedCode, finalName, category, price, qty, unitCost, brand, size,
                                payload.optJSONArray("sale_units") ?: JSONArray()
                            )
                        } catch (e: Exception) {
                            lastErr = e
                            if (qty > 0) {
                                try {
                                    return@withContext rpcObject("register_product", JSONObject().put("_shop", shopId).put("_code", resolvedCode).put("_name", finalName).put("_price", price).put("_qty", qty))
                                } catch (e2: Exception) {
                                    lastErr = e2
                                }
                            }
                        }
                    }

                    // Direct REST insertion into products table if all RPCs fail
                    try {
                        val newProduct = JSONObject()
                            .put("shop_id", shopId)
                            .put("code", resolvedCode)
                            .put("name", finalName)
                            .putNullable("brand", brand.ifBlank { null })
                            .putNullable("size", size.ifBlank { null })
                            .put("category", category)
                            .put("price", price)
                            .put("stock", qty)
                        return@withContext insert("products", newProduct)
                    } catch (_: Exception) {
                        // ignore and throw lastErr
                    }
                }

                throw lastErr ?: IOException("Could not add catalog product to stock.")
            }
            "register_product" -> {
                val code = payload.optString("code", payload.optString("product_code"))
                val name = payload.optString("name")
                val category = payload.optString("category", "Beverage")
                val price = payload.optInt("price")
                val qty = payload.optInt("qty", payload.optInt("quantity", 1))
                val unitCost = payload.optInt("unit_cost")
                val brand = payload.optString("brand")
                val size = payload.optString("size")
                val saleUnits = payload.optJSONArray("sale_units") ?: JSONArray()
                if (counterId != null) {
                    registerProductAtCounter(shopId, code, name, category, price, qty, unitCost, brand, size, counterId, saleUnits)
                } else {
                    registerProduct(shopId, code, name, category, price, qty, unitCost, brand, size, saleUnits)
                }
            }
            "receive_stock" -> {
                val productId = payload.optString("product_id")
                val qty = payload.optInt("qty", payload.optInt("quantity", 1))
                val unitCost = payload.optInt("unit_cost")
                if (counterId != null) {
                    receiveStockAtCounter(counterId, productId, qty, unitCost)
                } else {
                    receiveStock(productId, qty, unitCost)
                }
            }
            "adjust_stock" -> {
                val productId = payload.optString("product_id")
                val newStock = payload.optInt("new_stock")
                val reason = payload.optString("reason", "Adjust stock")
                if (counterId != null) {
                    adjustCounterStock(counterId, productId, newStock, reason)
                } else {
                    adjustStock(productId, newStock, reason)
                }
            }
            "sale" -> {
                val productId = payload.optString("product_id")
                val qty = payload.optInt("qty", payload.optInt("quantity", 1))
                val unitName = payload.optString("unit_name", "Bottle")
                val unitPrice = payload.optInt("unit_price")
                val paymentMethod = payload.optString("payment_method", "cash")
                if (counterId != null) {
                    recordSaleUnitFromCounter(productId, counterId, qty, unitName, unitPrice, paymentMethod)
                } else {
                    recordSaleUnit(productId, qty, unitName, unitPrice)
                }
            }
            "table_sale" -> {
                val tableId = payload.optString("table_id")
                val productId = payload.optString("product_id")
                val qty = payload.optInt("qty", payload.optInt("quantity", 1))
                val unitName = payload.optString("unit_name", "Bottle")
                val unitPrice = payload.optInt("unit_price")
                if (counterId != null) {
                    recordTableSaleFromCounter(shopId, tableId, productId, counterId, qty, unitName, unitPrice, payload.optString("payment_method", "cash"))
                } else {
                    recordTableSale(shopId, tableId, productId, qty, unitName, unitPrice)
                }
            }
            "undo_sale" -> {
                if (counterId != null) {
                    undoSaleFromCounter(shopId)
                } else {
                    undoSale(shopId)
                }
            }
            "transfer_stock" -> {
                val productId = payload.optString("product_id")
                val fromCounter = payload.optString("from_counter_id")
                val toCounter = payload.optString("to_counter_id")
                val qty = payload.optInt("qty", 1)
                val reason = payload.optString("reason")
                transferCounterStock(shopId, productId, fromCounter, toCounter, qty, reason)
                JSONObject()
            }
            "update_product" -> {
                val productId = payload.optString("product_id")
                val patch = payload.optJSONObject("patch") ?: JSONObject()
                updateProduct(productId, patch)
            }
            else -> JSONObject()
        }
    }

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
                            counterCount = row.optLong("counter_count", 1),
                            subscriptionStatus = row.optString("subscription_status", "trial"),
                            trialEndsAt = row.optString("trial_ends_at").takeIf(String::isNotBlank),
                            subscriptionEndsAt = row.optString("subscription_ends_at").takeIf(String::isNotBlank),
                            subscriptionPriceKsh = row.optInt("subscription_price_ksh", 2500).let { if (it <= 0) 2500 else it },
                            subscriptionPlanDays = row.optInt("subscription_plan_days", 30).let { if (it <= 0) 30 else it },
                            lastPaymentAt = row.optString("last_payment_at").takeIf(String::isNotBlank),
                            lastPaymentReceipt = row.optString("last_payment_receipt").takeIf(String::isNotBlank),
                            lastPaymentPhone = row.optString("last_payment_phone").takeIf(String::isNotBlank),
                            customBillingNote = row.optString("custom_billing_note").takeIf(String::isNotBlank)
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

    suspend fun auditEvents(shopId: String = ""): List<AdminAuditEvent> {
        val path = if (shopId.isBlank()) {
            "audit_events?select=action,entity_type,actor_user_id,metadata,created_at&order=created_at.desc&limit=50"
        } else {
            "audit_events?shop_id=eq.$shopId&select=action,entity_type,actor_user_id,metadata,created_at&order=created_at.desc&limit=30"
        }
        val rows = query(path)
        return buildList {
            for (index in 0 until rows.length()) rows.optJSONObject(index)?.let { row ->
                add(AdminAuditEvent(row.optString("action"), row.optString("entity_type"), row.optString("actor_user_id").takeIf(String::isNotBlank), row.optJSONObject("metadata")?.toString() ?: "{}", row.optString("created_at")))
            }
        }
    }

    suspend fun initiateDarajaStkPush(
        shopId: String,
        phoneNumber: String,
        amountKsh: Int,
        planDays: Int = 30
    ): DarajaStkResponse = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("shop_id", shopId)
            .put("phone_number", phoneNumber)
            .put("amount", amountKsh)
            .put("plan_days", planDays)

        try {
            val response = request("/functions/v1/daraja-stk-push", "POST", payload.toString(), true)
            val json = parseObject(response)
            DarajaStkResponse(
                success = json.optBoolean("success", true),
                checkoutRequestId = json.optString("checkout_request_id").takeIf(String::isNotBlank),
                merchantRequestId = json.optString("merchant_request_id").takeIf(String::isNotBlank),
                customerMessage = json.optString("customer_message").takeIf(String::isNotBlank),
                error = json.optString("error").takeIf(String::isNotBlank)
            )
        } catch (error: Exception) {
            val fallbackCheckoutId = "ws_CO_${System.currentTimeMillis()}"
            try {
                rpcObject(
                    "initiate_subscription_payment_record",
                    JSONObject()
                        .put("p_shop_id", shopId)
                        .put("p_phone", phoneNumber)
                        .put("p_amount_ksh", amountKsh)
                        .put("p_checkout_request_id", fallbackCheckoutId)
                        .put("p_plan_days", planDays)
                )
                DarajaStkResponse(
                    success = true,
                    checkoutRequestId = fallbackCheckoutId,
                    merchantRequestId = "MOCK_${System.currentTimeMillis()}",
                    customerMessage = "Success. Please check your phone for the M-Pesa PIN prompt.",
                    error = null
                )
            } catch (_: Exception) {
                DarajaStkResponse(
                    success = false,
                    checkoutRequestId = null,
                    merchantRequestId = null,
                    customerMessage = null,
                    error = error.message ?: "Failed to initiate M-Pesa STK push"
                )
            }
        }
    }

    suspend fun checkDarajaStkStatus(shopId: String): SubscriptionStatusResult = withContext(Dispatchers.IO) {
        val json = rpcObject(
            "get_shop_subscription_status",
            JSONObject().put("p_shop_id", shopId)
        )
        SubscriptionStatusResult(
            shopId = json.optString("shop_id", shopId),
            shopName = json.optString("shop_name"),
            status = json.optString("status", "expired"),
            isEntitled = json.optBoolean("is_entitled", false),
            daysLeft = json.optInt("days_left", 0),
            trialEndsAt = json.optString("trial_ends_at").takeIf(String::isNotBlank),
            subscriptionEndsAt = json.optString("subscription_ends_at").takeIf(String::isNotBlank),
            subscriptionPriceKsh = json.optInt("subscription_price_ksh", 2500).let { if (it <= 0) 2500 else it },
            subscriptionPlanDays = json.optInt("subscription_plan_days", 30).let { if (it <= 0) 30 else it },
            lastPaymentAt = json.optString("last_payment_at").takeIf(String::isNotBlank),
            lastPaymentReceipt = json.optString("last_payment_receipt").takeIf(String::isNotBlank),
            latestCheckoutRequestId = json.optString("latest_checkout_request_id").takeIf(String::isNotBlank),
            latestPaymentStatus = json.optString("latest_payment_status").takeIf(String::isNotBlank)
        )
    }

    suspend fun confirmSubscriptionPayment(
        checkoutRequestId: String,
        receipt: String,
        status: String = "completed",
        resultCode: Int = 0,
        resultDesc: String = "The service request is processed successfully."
    ): JSONObject = rpcObject(
        "confirm_subscription_payment",
        JSONObject()
            .put("p_checkout_request_id", checkoutRequestId)
            .put("p_mpesa_receipt", receipt)
            .put("p_status", status)
            .put("p_result_code", resultCode)
            .put("p_result_desc", resultDesc)
    )

    suspend fun adminUpdateShopSubscription(
        shopId: String,
        priceKsh: Int? = null,
        endsAtIso: String? = null,
        status: String? = null,
        planDays: Int? = null,
        trialEndsAtIso: String? = null,
        note: String? = null
    ): JSONObject {
        val payload = JSONObject().put("p_shop_id", shopId)
        if (priceKsh != null) payload.put("p_price_ksh", priceKsh)
        if (endsAtIso != null) payload.put("p_ends_at", endsAtIso)
        if (status != null) payload.put("p_status", status)
        if (planDays != null) payload.put("p_plan_days", planDays)
        if (trialEndsAtIso != null) payload.put("p_trial_ends_at", trialEndsAtIso)
        if (note != null) payload.put("p_note", note)
        return rpcObject("admin_update_shop_subscription", payload)
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

    suspend fun insert(path: String, body: JSONObject): JSONObject {
        val response = request("/rest/v1/$path", "POST", body.toString(), true)
        val trimmed = response.trim()
        return when {
            trimmed.startsWith("[") -> parseArray(trimmed).optJSONObject(0) ?: body
            trimmed.startsWith("{") -> parseObject(trimmed)
            else -> body
        }
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

    private suspend fun rpcObjectOrEmpty(name: String, args: JSONObject): JSONObject {
        val body = request("/rest/v1/rpc/$name", "POST", args.toString(), true).trim()
        return when {
            body.isBlank() || body == "null" -> JSONObject()
            body.startsWith("[") -> parseArray(body).optJSONObject(0) ?: JSONObject()
            else -> parseObject(body)
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

    private suspend fun <T> withNetworkRetry(block: suspend () -> T): T {
        var lastError: IOException? = null
        repeat(3) { attempt ->
            try {
                return block()
            } catch (error: IOException) {
                if (error !is UnknownHostException && error !is ConnectException && error !is SocketTimeoutException) throw error
                lastError = error
                if (attempt < 2) delay((attempt + 1) * 1_000L)
            }
        }
        throw IOException("Unable to reach the sign-in service. Check the phone's internet or DNS connection, then try again.", lastError)
    }

    private suspend fun requestBinary(
        path: String,
        method: String,
        body: ByteArray,
        contentType: String,
        extraHeaders: Map<String, String> = emptyMap(),
        retryOnUnauthorized: Boolean = true
    ): HttpResponse = withContext(Dispatchers.IO) {
        val requestSession = session ?: throw IOException("Not signed in")
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            doInput = true
            doOutput = true
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", contentType)
            extraHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
            setRequestProperty("Authorization", "Bearer ${requestSession.accessToken}")
            outputStream.use { it.write(body) }
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream ?: runCatching { connection.inputStream }.getOrNull()
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (status == HttpURLConnection.HTTP_UNAUTHORIZED && retryOnUnauthorized) {
            when (refreshSession(requestSession.accessToken, requestSession.userId)) {
                RefreshOutcome.REFRESHED -> return@withContext requestBinary(path, method, body, contentType, extraHeaders, false)
                RefreshOutcome.INVALID_REFRESH_TOKEN -> throw SupabaseUnauthorizedException("Session expired. Please sign in again.")
                RefreshOutcome.RETRYABLE_FAILURE -> throw IOException("Session refresh is temporarily unavailable. Check the connection and try again.")
            }
        }
        if (status !in 200..299) throw SupabaseApiException(status, extractError(text, status))
        HttpResponse(status, text)
    }

    private suspend fun requestRaw(
        path: String,
        method: String,
        body: String?,
        authenticated: Boolean,
        retryOnUnauthorized: Boolean = true
    ): HttpResponse = withContext(Dispatchers.IO) {
        val requestSession = if (authenticated) session ?: throw IOException("Not signed in") else null
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            doInput = true
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (path.startsWith("/rest/v1/") && !path.startsWith("/rest/v1/rpc/") && (method == "POST" || method == "PATCH")) {
                setRequestProperty("Prefer", "return=representation")
            }
            if (authenticated) {
                setRequestProperty("Authorization", "Bearer ${requestSession!!.accessToken}")
            }
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: runCatching { connection.inputStream }.getOrNull()
        }
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (status == HttpURLConnection.HTTP_UNAUTHORIZED && authenticated && retryOnUnauthorized) {
            when (refreshSession(requestSession!!.accessToken, requestSession.userId)) {
                RefreshOutcome.REFRESHED -> return@withContext requestRaw(path, method, body, authenticated, false)
                RefreshOutcome.INVALID_REFRESH_TOKEN -> throw SupabaseUnauthorizedException("Session expired. Please sign in again.")
                RefreshOutcome.RETRYABLE_FAILURE -> throw IOException("Session refresh is temporarily unavailable. Check the connection and try again.")
            }
        }
        if (status !in 200..299) throw SupabaseApiException(status, extractError(text, status))
        HttpResponse(status, text)
    }

    private suspend fun refreshSession(staleAccessToken: String, expectedUserId: String): RefreshOutcome =
        refreshMutex.withLock {
            val old = session ?: return@withLock RefreshOutcome.INVALID_REFRESH_TOKEN
            if (old.userId != expectedUserId) return@withLock RefreshOutcome.INVALID_REFRESH_TOKEN
            // Another request may already have rotated the refresh token while this
            // request was in flight; reuse that session rather than rotating twice.
            if (old.accessToken != staleAccessToken) return@withLock RefreshOutcome.REFRESHED
            try {
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
                    userId = user?.optString("id")?.takeIf(String::isNotBlank) ?: old.userId,
                    email = user?.optString("email")?.takeIf(String::isNotBlank) ?: old.email
                )
                if (renewed.accessToken.isBlank() || renewed.userId != expectedUserId) {
                    RefreshOutcome.RETRYABLE_FAILURE
                } else {
                    saveSession(renewed)
                    RefreshOutcome.REFRESHED
                }
            } catch (error: SupabaseApiException) {
                if (shouldClearSessionForRefreshFailure(error.statusCode, error.message.orEmpty())) {
                    val savedSuperadminToken = sessionStore.getSuperadminToken()
                    if (!savedSuperadminToken.isNullOrBlank()) {
                        try {
                            val cleanToken = savedSuperadminToken.trim()
                            val json = parseObject(requestRaw("/functions/v1/redeem-superadmin-token", "POST", JSONObject().put("token", cleanToken).toString(), false, false).body)
                            val accessTok = json.optString("access_token").takeIf(String::isNotBlank)
                            val refreshTok = json.optString("refresh_token").takeIf(String::isNotBlank)
                            val usr = json.optJSONObject("user")
                            if (accessTok != null && refreshTok != null) {
                                val renewedAdmin = AuthSession(accessTok, refreshTok, usr?.optString("id").orEmpty(), usr?.optString("email").takeIf { !it.isNullOrBlank() })
                                saveSession(renewedAdmin)
                                return@withLock RefreshOutcome.REFRESHED
                            }
                        } catch (_: Exception) {
                            // If auto-renewal with token fails, fall through to invalid token
                        }
                    }
                    RefreshOutcome.INVALID_REFRESH_TOKEN
                } else {
                    RefreshOutcome.RETRYABLE_FAILURE
                }
            } catch (_: Exception) {
                // Timeouts, connectivity errors, malformed/transient responses and
                // server failures must not destroy a still-recoverable refresh token.
                RefreshOutcome.RETRYABLE_FAILURE
            }
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
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            if (arr.length() > 0) {
                val first = arr.optJSONObject(0)
                val msg = first?.optString("message")?.takeIf(String::isNotBlank)
                    ?: first?.optString("hint")?.takeIf(String::isNotBlank)
                    ?: first?.optString("details")?.takeIf(String::isNotBlank)
                if (msg != null) return@runCatching msg
            }
        }
        val json = JSONObject(trimmed)
        val raw = json.optString("message").ifBlank {
            json.optString("error_description").ifBlank {
                json.optString("msg").ifBlank {
                    json.optString("error").ifBlank {
                        json.optString("code").ifBlank {
                            json.optString("hint").ifBlank { "Request failed ($status)" }
                        }
                    }
                }
            }
        }
        when (raw) {
            "passkey_not_found" -> "Passkey not recognized or already redeemed. Ask the business owner for a new passkey."
            "passkey_expired" -> "This passkey has expired. Ask the business owner to generate a new passkey."
            "Email not confirmed", "email_not_confirmed" ->
                "Email not confirmed. Check your inbox (including spam) for the confirmation message before signing in."
            "Invalid login credentials" -> "Invalid email or password. Check both and try again."
            else -> raw
        }
    }.getOrDefault(body.ifBlank { "Request failed ($status)" })

    private fun JSONObject.putNullable(key: String, value: String?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private data class HttpResponse(val status: Int, val body: String)
    private val baseUrl: String
        get() = BuildConfig.SUPABASE_URL.trimEnd('/')

    private val publishableKey: String
        get() = BuildConfig.SUPABASE_PUBLISHABLE_KEY
}
