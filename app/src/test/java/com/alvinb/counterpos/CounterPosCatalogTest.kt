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

        assertEquals(78, catalog.length())
        var stockedProducts = 0
        var libraryProducts = 0
        var openingUnits = 0

        for (index in 0 until catalog.length()) {
            val product = catalog.getJSONObject(index)
            val imageName = product.getString("imageAssetName")
            val drawableId = context.resources.getIdentifier(imageName, "drawable", context.packageName)
            assertTrue("Missing drawable for ${product.getString("name")}: $imageName", drawableId != 0)

            if (product.getBoolean("inMyStock")) {
                stockedProducts += 1
                openingUnits += product.getInt("stockQuantity")
            } else {
                libraryProducts += 1
            }
        }

        assertEquals(19, stockedProducts)
        assertEquals(59, libraryProducts)
        assertEquals(316, openingUnits)
    }
}
