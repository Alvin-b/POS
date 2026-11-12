package com.alvinb.counterpos.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SaleUnitSerializationTest {
    @Test
    fun preservesConfiguredBulkSaleUnitsAndPrices() {
        val configured = listOf(
            SaleUnit(name = "Cup", priceKsh = 100, stockUnitsPerSale = 0),
            SaleUnit(name = "Jug", priceKsh = 450, stockUnitsPerSale = 0)
        )

        val restored = parseSaleUnits(serializeSaleUnits(configured), fallbackPrice = 0)

        assertEquals(configured, restored)
    }

    @Test
    fun zeroUnitPriceFallsBackToConfiguredShopPrice() {
        val product = product(sellingPriceKsh = 95, suggestedPriceKsh = 80, unitsJson = """[{"name":"Bottle","priceKsh":0}]""")

        assertEquals(listOf(SaleUnit("Bottle", 95, 1)), resolveSaleUnits(product))
    }

    @Test
    fun catalogSuggestionIsNotUsedAsSalePriceWhenShopPriceIsUnset() {
        val product = product(sellingPriceKsh = 0, suggestedPriceKsh = 80, unitsJson = """[{"name":"Bottle","priceKsh":0}]""")

        assertEquals(listOf(SaleUnit("Bottle", 0, 1)), resolveSaleUnits(product))
    }

    @Test
    fun configuredUnitPriceTakesPrecedenceOverProductBasePrice() {
        val product = product(sellingPriceKsh = 95, suggestedPriceKsh = 80, unitsJson = """[{"name":"Jug","priceKsh":450}]""")

        assertEquals(listOf(SaleUnit("Jug", 450, 1)), resolveSaleUnits(product))
    }

    @Test
    fun keepsZeroWhenNeitherShopPriceNorSuggestionExists() {
        val product = product(sellingPriceKsh = 0, suggestedPriceKsh = 0, unitsJson = "")

        assertEquals(listOf(SaleUnit("Bottle", 0, 1)), resolveSaleUnits(product))
    }

    private fun product(sellingPriceKsh: Int, suggestedPriceKsh: Int, unitsJson: String) = ProductEntity(
        catalogCode = "AFIA-500",
        verifiedBarcode = null,
        name = "Afia Fruit Drink Apple 500ml",
        brand = "Afia",
        size = "500ml",
        category = "Juice",
        suggestedPriceKsh = suggestedPriceKsh,
        sellingPriceKsh = sellingPriceKsh,
        stockQuantity = 72,
        inMyStock = true,
        lowStockThreshold = 5,
        imageAssetName = "",
        salesUnitsJson = unitsJson
    )
}
