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
}
