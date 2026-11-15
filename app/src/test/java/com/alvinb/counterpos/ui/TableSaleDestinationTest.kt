package com.alvinb.counterpos.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TableSaleDestinationTest {
    @Test
    fun selectedTableIsOnlyAppliedToSalesFromTheTablesTab() {
        val tableId = "table-2"

        assertEquals(
            tableId,
            CounterPosUiState(
                tab = PosTab.TABLES,
                selectedTableId = tableId
            ).activeTableIdForSale()
        )

        listOf(PosTab.SCAN, PosTab.STOCK, PosTab.SALES, PosTab.REPORTS).forEach { tab ->
            assertNull(
                "A sale on $tab must not inherit the previously opened table",
                CounterPosUiState(tab = tab, selectedTableId = tableId).activeTableIdForSale()
            )
        }
    }
}
