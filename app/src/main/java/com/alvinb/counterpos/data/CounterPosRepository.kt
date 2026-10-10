package com.alvinb.counterpos.data

import android.content.ContentResolver
import android.content.res.AssetManager
import android.net.Uri
import com.alvinb.counterpos.data.local.dao.CounterPosDao
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.remote.SupabaseException
import com.alvinb.counterpos.data.remote.SupabaseRestClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class UserProfile(val id: String, val email: String)

data class ShopAccess(
    val id: String,
    val name: String,
    val role: String,
    val currency: String = "KES",
    val lowStockThreshold: Int = 5
)

class CounterPosRepository(
    private val dao: CounterPosDao,
    private val assets: AssetManager,
    private val supabase: SupabaseRestClient,
    private val contentResolver: ContentResolver
) {
    val products: Flow<List<ProductEntity>> = dao.observeProducts()
    val sales: Flow<List<SaleEntity>> = dao.observeSales()

    private val _todaySummary = MutableStateFlow(0 to 0)
    val todaySummary = _todaySummary.asStateFlow()
    private var activeShop: ShopAccess? = null
    private var localCatalog: Map<String, ProductEntity>? = null

    suspend fun restoreUser(): UserProfile? = supabase.currentUser()?.toUserProfile()

    suspend fun signIn(email: String, password: String): UserProfile =
        supabase.signIn(email, password).optJSONObject("user")?.toUserProfile()
            ?: throw SupabaseException("Sign-in succeeded but no user profile was returned.")

    suspend fun signUp(email: String, password: String): UserProfile? {
        val response = supabase.signUp(email, password)
        if (response.optString("access_token").isBlank()) return null
        return response.optJSONObject("user")?.toUserProfile()
    }

    suspend fun signOut() {
        runCatching { supabase.signOut() }
        activeShop = null
        _todaySummary.value = 0 to 0
        clearShopCache()
    }

    suspend fun shopMemberships(userId: String): List<ShopAccess> {
        val rows = supabase.shopMemberships(userId)
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val shop = when (val value = row.opt("shops")) {
                    is JSONObject -> value
                    is JSONArray -> value.optJSONObject(0)
                    else -> null
                } ?: continue
                val id = shop.optString("id", row.optString("shop_id"))
                if (id.isBlank()) continue
                add(
                    ShopAccess(
                        id = id,
                        name = shop.optString("name", "My shop"),
                        role = row.optString("role", "cashier"),
                        currency = shop.optString("currency", "KES"),
                        lowStockThreshold = shop.optInt("low_stock_threshold", 5)
                    )
                )
            }
        }
    }

    suspend fun createShop(name: String): ShopAccess {
        val shop = supabase.createShop(name)
        return ShopAccess(
            id = shop.optString("id").takeIf(String::isNotBlank)
                ?: throw SupabaseException("The backend did not return a shop ID."),
            name = shop.optString("name", name.trim()),
            role = "owner",
            currency = shop.optString("currency", "KES"),
            lowStockThreshold = shop.optInt("low_stock_threshold", 5)
        )
    }

    suspend fun selectShop(shop: ShopAccess) {
        activeShop = shop
        refreshShopData(shop)
    }

    suspend fun refreshShop() {
        val shop = activeShop ?: return
        refreshShopData(shop)
    }

    suspend fun sell(code: String, quantity: Int) {
        require(quantity > 0) { "Enter a quantity greater than zero." }
        val product = dao.getProductByCode(code) ?: throw SupabaseException("Product is no longer in this shop's stock.")
        val remoteId = product.backendProductId ?: throw SupabaseException("This product is not available for sale from the selected shop.")
        supabase.recordSale(remoteId, quantity)
        refreshShop()
    }

    suspend fun addFromLibrary(code: String, quantity: Int, priceKsh: Int) {
        require(quantity > 0) { "Enter the quantity you have in stock." }
        require(priceKsh > 0) { "Enter your shop selling price in KSh." }
        val shop = activeShop ?: throw SupabaseException("Select a shop before adding products.")
        val product = dao.getProductByCode(code) ?: throw SupabaseException("That catalog product is no longer available.")
        val catalogId = product.backendCatalogId ?: throw SupabaseException("The backend catalog ID is missing. Refresh the library and try again.")
        supabase.addFromCatalog(shop.id, catalogId, quantity, priceKsh)
        refreshShop()
    }

    suspend fun restock(code: String, quantity: Int) {
        require(quantity > 0) { "Enter a quantity greater than zero." }
        val product = dao.getProductByCode(code) ?: throw SupabaseException("Product is no longer in this shop's stock.")
        val remoteId = product.backendProductId ?: throw SupabaseException("This item is not in the shop's stock.")
        supabase.receiveStock(remoteId, quantity)
        refreshShop()
    }

    suspend fun undoLatestSale() {
        val shop = activeShop ?: throw SupabaseException("Select a shop before undoing a sale.")
        supabase.undoLatestSale(shop.id)
        refreshShop()
    }

    suspend fun updatePhoto(code: String, uri: String) {
        val shop = activeShop ?: throw SupabaseException("Select a shop before changing product photos.")
        if (shop.role != "owner") throw SupabaseException("Only a shop owner can change product photos.", 403)
        val product = dao.getProductByCode(code) ?: throw SupabaseException("Product is no longer in this shop's stock.")
        val remoteId = product.backendProductId ?: throw SupabaseException("Only products in My stock can have a shop photo.")
        val parsedUri = Uri.parse(uri)
        val bytes = withContext(Dispatchers.IO) {
            contentResolver.openInputStream(parsedUri)?.use { it.readBytes() }
                ?: throw SupabaseException("Could not read the selected image.")
        }
        if (bytes.size > 8 * 1024 * 1024) throw SupabaseException("Choose an image smaller than 8 MB.")
        val mime = contentResolver.getType(parsedUri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val path = supabase.uploadProductPhoto(shop.id, bytes, mime)
        supabase.setProductPhoto(remoteId, path)
        refreshShop()
    }

    suspend fun clearShopCache() = withContext(Dispatchers.IO) {
        dao.deleteAllProducts()
        dao.deleteAllSales()
    }

    private suspend fun refreshShopData(shop: ShopAccess) {
        val localByCode = localCatalogByCode()
        val catalogRows = supabase.catalogItems()
        val stockRows = supabase.products(shop.id)
        val productsById = mutableMapOf<String, ProductEntity>()
        val stockProducts = buildList {
            for (index in 0 until stockRows.length()) {
                val row = stockRows.optJSONObject(index) ?: continue
                val product = mapStockProduct(row, shop, localByCode)
                productsById[product.backendProductId.orEmpty()] = product
                add(product)
            }
        }
        val stockedCodes = stockProducts.mapTo(mutableSetOf()) { it.catalogCode }
        val libraryProducts = buildList {
            for (index in 0 until catalogRows.length()) {
                val row = catalogRows.optJSONObject(index) ?: continue
                val code = row.optString("code")
                if (code.isBlank() || code in stockedCodes) continue
                add(mapCatalogProduct(row, shop, localByCode))
            }
        }
        val allProducts = stockProducts + libraryProducts
        val salesRows = supabase.sales(shop.id)
        val localByName = localByCode.values.associateBy { normalizeName(it.name) }
        val sales = buildList {
            for (index in 0 until salesRows.length()) {
                val row = salesRows.optJSONObject(index) ?: continue
                val remoteProductId = row.optString("product_id")
                val productName = row.optString("product_name", "Product")
                val product = productsById[remoteProductId] ?: localByName[normalizeName(productName)]
                val undoneAt = row.optString("undone_at").takeIf(String::isNotBlank)
                add(
                    SaleEntity(
                        id = index.toLong() + 1,
                        catalogCode = product?.catalogCode ?: remoteProductId,
                        productName = productName,
                        imageAssetName = product?.imageAssetName.orEmpty(),
                        quantity = row.optInt("qty", 0),
                        unitPriceKsh = row.optInt("unit_price", 0),
                        totalKsh = row.optInt("total", 0),
                        createdAt = parseTimestamp(row.optString("created_at")),
                        undone = undoneAt != null,
                        photoOverrideUri = product?.photoOverrideUri,
                        backendSaleId = row.optString("id").takeIf(String::isNotBlank),
                        backendProductId = remoteProductId.takeIf(String::isNotBlank)
                    )
                )
            }
        }
        val summary = supabase.dailySummary(shop.id)
        _todaySummary.value = summary.optInt("total", 0) to summary.optInt("items_sold", 0)
        dao.replaceRemoteSnapshot(allProducts, sales)
    }

    private suspend fun mapStockProduct(
        row: JSONObject,
        shop: ShopAccess,
        localByCode: Map<String, ProductEntity>
    ): ProductEntity {
        val code = row.optString("code")
        val local = localByCode[code]
        val photoPath = row.optString("photo_path").takeIf(String::isNotBlank)
        val signedUrl = photoPath?.let { runCatching { supabase.signedPhotoUrl(it) }.getOrNull() }
        val remoteImage = signedUrl ?: row.optString("photo_url").takeIf(String::isNotBlank)
        val barcode = row.optString("barcode").takeIf(String::isNotBlank)
            ?.takeIf { it != code }
        return ProductEntity(
            catalogCode = code,
            verifiedBarcode = barcode ?: local?.verifiedBarcode,
            name = row.optString("name", local?.name ?: code),
            brand = row.optString("brand", local?.brand.orEmpty()),
            size = row.optString("size", local?.size.orEmpty()),
            category = row.optString("category", local?.category ?: "Beverage"),
            suggestedPriceKsh = local?.suggestedPriceKsh ?: row.optInt("price", 0),
            sellingPriceKsh = row.optInt("price", 0),
            stockQuantity = row.optInt("stock", 0),
            inMyStock = true,
            lowStockThreshold = shop.lowStockThreshold,
            imageAssetName = local?.imageAssetName.orEmpty(),
            photoOverrideUri = null,
            productSourceUrl = local?.productSourceUrl,
            imageSourceUrl = local?.imageSourceUrl,
            verificationNote = local?.verificationNote.orEmpty(),
            backendProductId = row.optString("id").takeIf(String::isNotBlank),
            backendCatalogId = row.optString("catalog_item_id").takeIf(String::isNotBlank),
            shopId = shop.id,
            remoteImageUrl = remoteImage,
            photoPath = photoPath
        )
    }

    private fun mapCatalogProduct(
        row: JSONObject,
        shop: ShopAccess,
        localByCode: Map<String, ProductEntity>
    ): ProductEntity {
        val code = row.optString("code")
        val local = localByCode[code]
        val barcode = row.optString("barcode").takeIf(String::isNotBlank)?.takeIf { it != code }
        val suggested = row.optInt("suggested_price", local?.suggestedPriceKsh ?: 0)
        return ProductEntity(
            catalogCode = code,
            verifiedBarcode = barcode ?: local?.verifiedBarcode,
            name = row.optString("name", local?.name ?: code),
            brand = row.optString("brand", local?.brand.orEmpty()),
            size = row.optString("size", local?.size.orEmpty()),
            category = row.optString("category", local?.category ?: "Beverage"),
            suggestedPriceKsh = suggested,
            sellingPriceKsh = suggested,
            stockQuantity = 0,
            inMyStock = false,
            lowStockThreshold = shop.lowStockThreshold,
            imageAssetName = local?.imageAssetName.orEmpty(),
            productSourceUrl = local?.productSourceUrl,
            imageSourceUrl = local?.imageSourceUrl,
            verificationNote = local?.verificationNote.orEmpty(),
            backendProductId = null,
            backendCatalogId = row.optString("id").takeIf(String::isNotBlank),
            shopId = shop.id,
            remoteImageUrl = row.optString("image_url").takeIf(String::isNotBlank)
        )
    }

    private suspend fun localCatalogByCode(): Map<String, ProductEntity> {
        localCatalog?.let { return it }
        return withContext(Dispatchers.IO) {
            localCatalog ?: run {
                val json = assets.open("counterpos_catalog.json").bufferedReader().use { it.readText() }
                val rows = JSONArray(json)
                buildMap {
                    for (index in 0 until rows.length()) {
                        val item = rows.optJSONObject(index) ?: continue
                        val code = item.optString("catalogCode")
                        if (code.isBlank()) continue
                        fun nullableString(key: String): String? =
                            if (item.isNull(key)) null else item.optString(key).takeIf(String::isNotBlank)
                        put(
                            code,
                            ProductEntity(
                                catalogCode = code,
                                verifiedBarcode = nullableString("verifiedBarcode"),
                                name = item.optString("name", code),
                                brand = item.optString("brand", ""),
                                size = item.optString("size", ""),
                                category = item.optString("category", "Beverage"),
                                suggestedPriceKsh = item.optInt("suggestedPriceKsh", 0),
                                sellingPriceKsh = item.optInt("sellingPriceKsh", 0),
                                stockQuantity = item.optInt("stockQuantity", 0),
                                inMyStock = item.optBoolean("inMyStock", false),
                                lowStockThreshold = item.optInt("lowStockThreshold", 5),
                                imageAssetName = item.optString("imageAssetName", ""),
                                productSourceUrl = nullableString("productSourceUrl"),
                                imageSourceUrl = nullableString("imageSourceUrl"),
                                verificationNote = item.optString("verificationNote", "")
                            )
                        )
                    }
                }.also { localCatalog = it }
            }
        }
    }

    private fun JSONObject.toUserProfile(): UserProfile {
        val id = optString("id")
        if (id.isBlank()) throw SupabaseException("The backend returned an invalid user profile.")
        return UserProfile(id, optString("email"))
    }

    private fun normalizeName(value: String): String = value.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)

    private fun parseTimestamp(value: String): Long {
        val normalized = value.replace(
            Regex("(\\.\\d{3})\\d+(?=(?:Z|[+-]\\d{2}:?\\d{2})$)"),
            "$1"
        )
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX")
        for (pattern in patterns) {
            val parser = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val position = ParsePosition(0)
            val parsed = parser.parse(normalized, position)
            if (parsed != null && position.index == normalized.length) return parsed.time
        }
        return System.currentTimeMillis()
    }
}
