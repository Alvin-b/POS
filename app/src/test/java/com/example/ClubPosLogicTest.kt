package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class ClubPosLogicTest {

    @Test
    fun testSubtotalAndTaxCalculation() {
        val tequilaShotPrice = 15.00
        val vodkaRedBullPrice = 16.00
        val quantityTequila = 2
        val quantityVodka = 3

        val subtotal = (tequilaShotPrice * quantityTequila) + (vodkaRedBullPrice * quantityVodka)
        assertEquals(78.00, subtotal, 0.001)

        val taxRate = 0.0825
        val tax = subtotal * taxRate
        assertEquals(6.435, tax, 0.001)

        val tipPercent = 20
        val tip = subtotal * (tipPercent / 100.0)
        assertEquals(15.60, tip, 0.001)

        val total = subtotal + tax + tip
        assertEquals(100.035, total, 0.001)
    }

    @Test
    fun testSplitBillCalculation() {
        val total = 120.00
        val twoWay = total / 2
        val threeWay = total / 3
        val fourWay = total / 4

        assertEquals(60.00, twoWay, 0.001)
        assertEquals(40.00, threeWay, 0.001)
        assertEquals(30.00, fourWay, 0.001)
    }
}
