package com.alvinb.counterpos.data.remote

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.util.concurrent.TimeUnit

class SupabaseRestClient(
    context: Context,
    private val projectUrl: String,
    private val publishableKey: String
) {
    private data class Session(
        val accessToken: String,
        val refreshToken: String,
        val expiresAtSeconds: Long,
        val userId: String,
        val email: String
    )

    private val preferences = context.getSharedPreferences("counterpos-supabase-session", Context.MODE_PRIVATE)
    private val refreshMutex = Mutex()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
    private val baseUrl = projectUrl.trimEnd('/')

    suspend fun signIn(email: String, password: String): JSONObject = withContext(Dispatchers.IO) {
        val result = requestJson(
            method = "POST",
            path = "/auth/v1/token?grant_type=password",
            body = JSONObject().put("email", email.trim()).put("password", password)
        ) as? JSONObject ?: throw SupabaseException("The sign-in response was invalid.")
        saveSessionIfPresent(result)
        result
    }

    suspend fun signUp(email: String, password: String): JSONObject = withContext(Dispatchers.IO) {
        val result = requestJson(
            method = "POST",
            path = "/auth/v1/signup",
            body = JSONObject().put("email", email.trim()).put("password", password)
        ) as? JSONObject ?: throw SupabaseException("The sign-up response was invalid.")
        saveSessionIfPresent(result)
        result
    }

    suspend fun currentUser(): JSONObject? = withContext(Dispatchers.IO) {
        if (freshAccessToken() == null) return@withContext null
        try {
            requestAuthenticatedJson("GET", "/auth/v1/user") as? JSONObject
        } catch (error: SupabaseException) {
            if (error.statusCode == 401 || error.statusCode == 403) clearSession()
            throw error
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val token = freshAccessToken()
        try {
            if (token != null) requestAuthenticatedJson("POST", "/auth/v1/logout", JSONObject(), token)
        } finally {
            clearSession()
        }
    }

    suspend fun shopMemberships(userId: String): JSONArray = withContext(Dispatchers.IO) {
        val query = "/rest/v1/shop_members?select=shop_id,user_id,role,shops(id,name,currency,low_stock_threshold)" +
            "&user_id=eq.${Uri.encode(userId)}&order=created_at.asc"
        requestAuthenticatedJson("GET", query) as? JSONArray
            ?: throw SupabaseException("The shop-membership response was invalid.")
    }

    suspend fun createShop(name: String): JSONObject = withContext(Dispatchers.IO) {
        val result = requestAuthenticatedJson(
            "POST", "/rest/v1/rpc/create_shop", JSONObject().put("_name", name.trim())
        )
        result.asObject() ?: throw SupabaseException("The create-shop response was invalid.")
    }

    suspend fun catalogItems(): JSONArray = withContext(Dispatchers.IO) {
        requestAuthenticatedJson(
            "GET",
            "/rest/v1/catalog_items?select=id,code,barcode,name,brand,size,category,product_type,suggested_price,image_url,body_color,label_color,label_text_color&order=name.asc"
        ) as? JSONArray ?: throw SupabaseException("The catalog response was invalid.")
    }

    suspend fun products(shopId: String): JSONArray = withContext(Dispatchers.IO) {
        requestAuthenticatedJson(
            "GET",
            "/rest/v1/products?select=id,shop_id,catalog_item_id,code,barcode,name,brand,size,category,product_type,body_color,label_color,label_text_color,price,stock,photo_url,photo_path,archived" +
                "&shop_id=eq.${Uri.encode(shopId)}&archived=eq.false&order=name.asc"
        ) as? JSONArray ?: throw SupabaseException("The stock response was invalid.")
    }

    suspend fun sales(shopId: String): JSONArray = withContext(Dispatchers.IO) {
        requestAuthenticatedJson(
            "GET",
            "/rest/v1/sales?select=id,shop_id,product_id,product_name,qty,unit_price,total,created_at,undone_at" +
                "&shop_id=eq.${Uri.encode(shopId)}&order=created_at.desc&limit=200"
        ) as? JSONArray ?: throw SupabaseException("The sales response was invalid.")
    }

    suspend fun dailySummary(shopId: String): JSONObject = withContext(Dispatchers.IO) {
        requestAuthenticatedJson(
            "POST", "/rest/v1/rpc/daily_summary", JSONObject().put("_shop", shopId)
        ).asObject() ?: throw SupabaseException("The daily summary response was invalid.")
    }

    suspend fun recordSale(productId: String, quantity: Int): JSONObject = rpcObject(
        "record_sale", JSONObject().put("_product", productId).put("_qty", quantity)
    )

    suspend fun undoLatestSale(shopId: String): JSONObject = rpcObject(
        "undo_sale", JSONObject().put("_shop", shopId)
    )

    suspend fun receiveStock(productId: String, quantity: Int): JSONObject = rpcObject(
        "receive_stock", JSONObject().put("_product", productId).put("_qty", quantity)
    )

    suspend fun addFromCatalog(shopId: String, catalogId: String, quantity: Int, priceKsh: Int): JSONObject = rpcObject(
        "add_from_catalog",
        JSONObject().put("_shop", shopId).put("_catalog", catalogId).put("_qty", quantity).put("_price", priceKsh)
    )

    suspend fun uploadProductPhoto(shopId: String, bytes: ByteArray, mimeType: String): String = withContext(Dispatchers.IO) {
        val extension = when (mimeType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val path = "$shopId/${java.util.UUID.randomUUID()}.$extension"
        val encodedPath = path.split('/').joinToString("/") { Uri.encode(it) }
        val token = freshAccessToken() ?: throw SupabaseException("Sign in to upload a product photo.")
        val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        requestBytes("POST", "/storage/v1/object/product-photos/$encodedPath", body, token)
        path
    }

    suspend fun signedPhotoUrl(path: String): String = withContext(Dispatchers.IO) {
        val encodedPath = path.split('/').joinToString("/") { Uri.encode(it) }
        val result = requestAuthenticatedJson(
            "POST",
            "/storage/v1/object/sign/product-photos/$encodedPath",
            JSONObject().put("expiresIn", 3600)
        ) as? JSONObject ?: throw SupabaseException("The signed-photo response was invalid.")
        val signed = result.optString("signedURL").ifBlank { result.optString("signedUrl") }
        if (signed.isBlank()) throw SupabaseException("The backend did not return a photo link.")
        when {
            signed.startsWith("http://") || signed.startsWith("https://") -> signed
            signed.startsWith("/") -> "$baseUrl/storage/v1$signed"
            else -> "$baseUrl/storage/v1/$signed"
        }
    }

    suspend fun setProductPhoto(productId: String, path: String) = withContext(Dispatchers.IO) {
        requestAuthenticatedJson(
            "PATCH",
            "/rest/v1/products?id=eq.${Uri.encode(productId)}",
            JSONObject().put("photo_path", path),
            extraHeaders = mapOf("Prefer" to "return=minimal")
        )
    }

    private suspend fun rpcObject(name: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        requestAuthenticatedJson("POST", "/rest/v1/rpc/$name", body).asObject()
            ?: throw SupabaseException("The $name response was invalid.")
    }

    private suspend fun requestAuthenticatedJson(
        method: String,
        path: String,
        body: JSONObject? = null,
        initialToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): Any? {
        val token = initialToken ?: freshAccessToken() ?: throw SupabaseException("Please sign in again.", 401)
        return try {
            requestJson(method, path, body, token, extraHeaders)
        } catch (error: SupabaseException) {
            if (error.statusCode != 401 || initialToken != null) throw error
            val refreshed = freshAccessToken(force = true) ?: throw SupabaseException("Please sign in again.", 401)
            requestJson(method, path, body, refreshed, extraHeaders)
        }
    }

    private suspend fun freshAccessToken(force: Boolean = false): String? = refreshMutex.withLock {
        val current = readSession() ?: return@withLock null
        val now = System.currentTimeMillis() / 1000
        if (!force && current.expiresAtSeconds > now + 60) return@withLock current.accessToken
        try {
            val response = requestJson(
                "POST",
                "/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", current.refreshToken)
            ) as? JSONObject ?: throw SupabaseException("The session refresh response was invalid.")
            saveSessionIfPresent(response)
            readSession()?.accessToken ?: throw SupabaseException("The session could not be refreshed.", 401)
        } catch (error: Exception) {
            clearSession()
            throw error
        }
    }

    private fun requestJson(
        method: String,
        path: String,
        body: JSONObject? = null,
        accessToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): Any? {
        val requestBody = body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        return parseJson(execute(method, path, requestBody, accessToken, extraHeaders))
    }

    private fun requestBytes(method: String, path: String, body: okhttp3.RequestBody, accessToken: String) {
        execute(method, path, body, accessToken, emptyMap())
    }

    private fun execute(
        method: String,
        path: String,
        body: okhttp3.RequestBody?,
        accessToken: String?,
        extraHeaders: Map<String, String>
    ): String {
        val builder = Request.Builder()
            .url("$baseUrl$path")
            .header("apikey", publishableKey)
            .header("Accept", "application/json")
        if (accessToken != null) builder.header("Authorization", "Bearer $accessToken")
        extraHeaders.forEach { (name, value) -> builder.header(name, value) }
        builder.method(method, body)
        try {
            http.newCall(builder.build()).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorJson = runCatching { JSONObject(responseText) }.getOrNull()
                    val message = errorJson?.optString("msg")?.takeIf(String::isNotBlank)
                        ?: errorJson?.optString("message")?.takeIf(String::isNotBlank)
                        ?: errorJson?.optString("error_description")?.takeIf(String::isNotBlank)
                        ?: errorJson?.optString("error")?.takeIf(String::isNotBlank)
                        ?: "Backend request failed (${response.code})."
                    throw SupabaseException(message, response.code)
                }
                return responseText
            }
        } catch (error: SupabaseException) {
            throw error
        } catch (error: IOException) {
            throw SupabaseException("Could not reach the POS backend. Check your internet connection.", cause = error)
        }
    }

    private fun parseJson(text: String): Any? = if (text.isBlank()) null else JSONTokener(text).nextValue()

    private fun Any?.asObject(): JSONObject? = when (this) {
        is JSONObject -> this
        is JSONArray -> if (length() > 0) optJSONObject(0) else null
        else -> null
    }

    private fun saveSessionIfPresent(response: JSONObject) {
        val access = response.optString("access_token")
        val refresh = response.optString("refresh_token")
        if (access.isBlank() || refresh.isBlank()) return
        val old = readSession()
        val user = response.optJSONObject("user")
        val userId = user?.optString("id")?.takeIf(String::isNotBlank) ?: old?.userId.orEmpty()
        val email = user?.optString("email")?.takeIf(String::isNotBlank) ?: old?.email.orEmpty()
        val now = System.currentTimeMillis() / 1000
        val expiresAt = response.optLong("expires_at", now + response.optLong("expires_in", 3600))
        preferences.edit()
            .putString("access_token", access)
            .putString("refresh_token", refresh)
            .putLong("expires_at", expiresAt)
            .putString("user_id", userId)
            .putString("email", email)
            .apply()
    }

    private fun readSession(): Session? {
        val access = preferences.getString("access_token", null) ?: return null
        val refresh = preferences.getString("refresh_token", null) ?: return null
        val userId = preferences.getString("user_id", null).orEmpty()
        val email = preferences.getString("email", null).orEmpty()
        return Session(access, refresh, preferences.getLong("expires_at", 0), userId, email)
    }

    private fun clearSession() {
        preferences.edit().clear().apply()
    }
}

class SupabaseException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)
