package com.alvinb.counterpos.data

import android.content.res.AssetManager
import com.alvinb.counterpos.data.local.dao.CounterPosDao
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray

class CounterPosRepository(
    private val dao: CounterPosDao,
    private val assets: AssetManager
) {
    val products: Flow<List<ProductEntity>> = dao.observeProducts()
    val sales: Flow<List<SaleEntity>> = dao.observeSales()

    suspend fun seedCatalogIfEmpty() = withContext(Dispatchers.IO) {
        if (dao.getProductCount() > 0) return@withContext
        val payload = assets.open("counterpos_catalog.json").bufferedReader().use { it.readText() }
        val array = JSONArray(payload)
        val seededProducts = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                fun nullableString(key: String): String? =
                    if (item.isNull(key)) null else item.optString(key).takeIf(String::isNotBlank)

                add(
                    ProductEntity(
                        catalogCode = item.getString("catalogCode"),
                        verifiedBarcode = nullableString("verifiedBarcode"),
                        name = item.getString("name"),
                        brand = item.optString("brand", ""),
                        size = item.optString("size", ""),
                        category = item.optString("category", "Beverage"),
                        suggestedPriceKsh = item.optInt("suggestedPriceKsh", 0),
                        sellingPriceKsh = item.optInt("sellingPriceKsh", 0),
                        stockQuantity = item.optInt("stockQuantity", 0),
                        inMyStock = item.optBoolean("inMyStock", false),
                        lowStockThreshold = item.optInt("lowStockThreshold", 5),
                        imageAssetName = item.getString("imageAssetName"),
                        productSourceUrl = nullableString("productSourceUrl"),
                        imageSourceUrl = nullableString("imageSourceUrl"),
                        verificationNote = item.optString("verificationNote", "")
                    )
                )
            }
        }
        dao.insertProducts(seededProducts)
    }

    suspend fun sell(code: String, quantity: Int): Boolean =
        dao.completeSale(code, quantity, System.currentTimeMillis())

    suspend fun addFromLibrary(code: String, quantity: Int, priceKsh: Int): Boolean =
        dao.addCatalogProduct(code, quantity, priceKsh)

    suspend fun restock(code: String, quantity: Int): Boolean =
        quantity > 0 && dao.receiveStock(code, quantity) == 1

    suspend fun undoLatestSale(): Boolean = dao.undoLatestSale()

    suspend fun updatePhoto(code: String, uri: String): Boolean =
        dao.updatePhotoOverride(code, uri) == 1
}
