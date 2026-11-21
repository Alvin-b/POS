package com.alvinb.counterpos.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.remote.ServiceCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrandedPdfReportsTest {

    private class BitmapRecordingPdfSession : PdfDocumentSession {
        val pageBitmaps = mutableListOf<Bitmap>()
        var finishedPages = 0

        override fun beginPage(width: Int, height: Int, pageNumber: Int): Canvas {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            pageBitmaps += bmp
            return Canvas(bmp)
        }

        override fun endPage() {
            finishedPages += 1
        }

        override fun finishToByteArray(): ByteArray {
            return "%PDF-1.4\n%%Pages:${pageBitmaps.size}\n%%EOF".toByteArray(Charsets.US_ASCII)
        }
    }

    @Test
    fun brandedPdfDrawsColoredPagesAndPaginatesLargeLedgers() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val now = System.currentTimeMillis()
        val products = listOf(
            ProductEntity(
                catalogCode = "p1",
                verifiedBarcode = null,
                name = "Heineken 330ml",
                brand = "Heineken",
                size = "330ml",
                category = "Beer",
                suggestedPriceKsh = 350,
                sellingPriceKsh = 350,
                stockQuantity = 40,
                inMyStock = true,
                lowStockThreshold = 6,
                imageAssetName = "",
                averageCostKsh = 230
            )
        )
        val counters = listOf(
            ServiceCounter(id = "c1", name = "Main Bar"),
            ServiceCounter(id = "c2", name = "Rooftop")
        )
        val sales = (1..28).map { idx ->
            SaleEntity(
                id = idx.toLong(),
                catalogCode = "p1",
                productName = "Heineken 330ml",
                imageAssetName = "",
                unitName = "Bottle",
                quantity = 2,
                unitPriceKsh = 350,
                totalKsh = 700,
                createdAt = now - idx * 60_000L,
                counterId = if (idx % 2 == 0) "c1" else "c2",
                counterName = if (idx % 2 == 0) "Main Bar" else "Rooftop",
                waiterName = "staff@bar.co.ke",
                paymentMethod = if (idx % 3 == 0) "mpesa" else "cash"
            )
        }

        val multiSession = BitmapRecordingPdfSession()
        val multiPageBytes = buildBrandedReportPdf(
            context = context,
            businessName = "Serenity Lounge & Bar",
            sales = sales,
            products = products,
            counters = counters,
            startMs = now - 86_400_000L,
            endMs = now,
            title = "Performance Report",
            filterSummary = "Today · All Payments · All Counters",
            sessionFactory = { multiSession }
        )

        assertTrue(multiSession.pageBitmaps.size >= 2)
        assertEquals(multiSession.pageBitmaps.size, multiSession.finishedPages)
        assertTrue(String(multiPageBytes, Charsets.US_ASCII).startsWith("%PDF-"))

        val emptySession = BitmapRecordingPdfSession()
        val emptyBytes = buildBrandedReportPdf(
            context = context,
            businessName = "Serenity Lounge & Bar",
            sales = emptyList(),
            products = products,
            counters = counters,
            startMs = now - 86_400_000L,
            endMs = now,
            title = "Sales Statement",
            sessionFactory = { emptySession }
        )
        assertEquals(1, emptySession.pageBitmaps.size)
        assertEquals(1, emptySession.finishedPages)
        assertTrue(String(emptyBytes, Charsets.US_ASCII).startsWith("%PDF-"))
    }
}
