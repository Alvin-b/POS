package com.alvinb.counterpos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CounterPosCatalogTest {
    @Test
    fun catalogHasAllProductsAndEveryProductHasAnImageResource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val json = context.assets.open("counterpos_catalog.json").bufferedReader().use { it.readText() }
        val catalog = JSONArray(json)

        assertEquals(306, catalog.length())
        var stockedProducts = 0
        var libraryProducts = 0

        for (index in 0 until catalog.length()) {
            val product = catalog.getJSONObject(index)
            val imageName = product.getString("imageAssetName")
            val drawableId = context.resources.getIdentifier(imageName, "drawable", context.packageName)
            assertTrue("Missing product name at catalog row $index", product.getString("name").isNotBlank())
            assertTrue("Missing image key at catalog row $index", imageName.isNotBlank())
            assertTrue("Missing drawable for ${product.getString("name")}: $imageName", drawableId != 0)
            assertEquals("Catalog entries must not contain tenant stock", 0, product.getInt("stockQuantity"))

            if (product.getBoolean("inMyStock")) {
                stockedProducts += 1
            } else {
                libraryProducts += 1
            }
        }

        assertEquals(0, stockedProducts)
        assertEquals(306, libraryProducts)
    }
}
