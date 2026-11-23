package com.alvinb.counterpos.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import com.alvinb.counterpos.R
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.remote.ServiceCounter
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private object PdfPalette {
    val DeepForest = android.graphics.Color.rgb(11, 46, 36)       // #0B2E24
    val ForestMid = android.graphics.Color.rgb(20, 71, 56)        // #144738
    val Emerald = android.graphics.Color.rgb(8, 127, 91)          // #087F5B
    val Mint = android.graphics.Color.rgb(94, 230, 176)           // #5EE6B0
    val MintSoft = android.graphics.Color.rgb(231, 248, 241)      // #E7F8F1
    val Teal = android.graphics.Color.rgb(15, 118, 110)           // #0F766E
    val TealSoft = android.graphics.Color.rgb(230, 245, 243)      // #E6F5F3
    val Amber = android.graphics.Color.rgb(217, 119, 6)           // #D97706
    val AmberAccent = android.graphics.Color.rgb(232, 160, 74)    // #E8A04A
    val AmberSoft = android.graphics.Color.rgb(255, 246, 229)     // #FFF6E5
    val Cobalt = android.graphics.Color.rgb(2, 132, 199)          // #0284C7
    val CobaltSoft = android.graphics.Color.rgb(230, 244, 254)    // #E6F4FE
    val SlateSoft = android.graphics.Color.rgb(241, 245, 244)     // #F1F5F4
    val Ink = android.graphics.Color.rgb(20, 34, 29)              // #14221D
    val Muted = android.graphics.Color.rgb(91, 110, 102)          // #5B6E66
    val SoftText = android.graphics.Color.rgb(183, 224, 206)      // #B7E0CE
    val Border = android.graphics.Color.rgb(215, 228, 222)        // #D7E4DE
    val Zebra = android.graphics.Color.rgb(245, 250, 247)         // #F5FAF7
    val White = android.graphics.Color.WHITE
}

internal interface PdfDocumentSession {
    fun beginPage(width: Int, height: Int, pageNumber: Int): Canvas
    fun endPage()
    fun finishToByteArray(): ByteArray
}

private class AndroidPdfDocumentSession : PdfDocumentSession {
    private val document = PdfDocument()
    private var currentPage: PdfDocument.Page? = null

    override fun beginPage(width: Int, height: Int, pageNumber: Int): Canvas {
        val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
        currentPage = page
        return page.canvas
    }

    override fun endPage() {
        currentPage?.let { document.finishPage(it) }
        currentPage = null
    }

    override fun finishToByteArray(): ByteArray {
        val output = ByteArrayOutputStream()
        document.writeTo(output)
        document.close()
        return output.toByteArray()
    }
}

internal fun buildBrandedReportPdf(
    context: Context?,
    businessName: String,
    sales: List<SaleEntity>,
    products: List<ProductEntity> = emptyList(),
    counters: List<ServiceCounter> = emptyList(),
    snapshot: ReportAnalyticsSnapshot? = null,
    startMs: Long,
    endMs: Long,
    title: String,
    filterSummary: String? = null,
    sessionFactory: () -> PdfDocumentSession = { AndroidPdfDocumentSession() }
): ByteArray {
    val pageWidth = 595f
    val pageHeight = 842f
    val margin = 32f
    val contentWidth = pageWidth - margin * 2f // 531f
    val contentBottomLimit = 784f

    val safeBusiness = businessName.trim().ifBlank { "Counter POS" }
    val activeSales = sales.filterNot { it.undone }
        .sortedWith(compareBy<SaleEntity> { it.createdAt }.thenBy { it.id })

    val effectiveSnapshot = snapshot ?: buildReportAnalyticsSnapshot(
        sales = activeSales,
        products = products,
        counters = counters,
        remoteRows = emptyList(),
        registeredWaiterEmails = emptySet(),
        diningTables = emptyList(),
        tableOrders = emptyList(),
        reportFilter = "all",
        reportPaymentFilter = "all",
        reportCounterFilter = "all"
    )

    val displayFont = context?.let {
        runCatching { ResourcesCompat.getFont(it, R.font.space_grotesk) }.getOrNull()
    } ?: Typeface.DEFAULT
    val bodyFont = context?.let {
        runCatching { ResourcesCompat.getFont(it, R.font.inter) }.getOrNull()
    } ?: Typeface.DEFAULT
    val displayBold = Typeface.create(displayFont, Typeface.BOLD)
    val bodyRegular = Typeface.create(bodyFont, Typeface.NORMAL)
    val bodyBold = Typeface.create(bodyFont, Typeface.BOLD)

    val logoBitmap: Bitmap? = context?.let {
        runCatching { BitmapFactory.decodeResource(it.resources, R.drawable.ic_counterpos_logo) }.getOrNull()
    }

    val session = sessionFactory()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val integerFormat = NumberFormat.getIntegerInstance(Locale.US)
    val dateTimeFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    val generatedLabel = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date())

    fun money(value: Long): String = "KSh ${integerFormat.format(value)}"

    fun paymentLabel(method: String): String = when (method.lowercase(Locale.ROOT)) {
        "mpesa" -> "M-Pesa"
        "cash" -> "Cash"
        else -> method.replaceFirstChar { it.uppercase(Locale.getDefault()) }.ifBlank { "Other" }
    }

    fun applyFont(size: Float, bold: Boolean, display: Boolean = false) {
        paint.textSize = size
        paint.typeface = when {
            display -> displayBold
            bold -> bodyBold
            else -> bodyRegular
        }
    }

    fun fittingText(value: String, maxWidth: Float, size: Float, bold: Boolean = false, display: Boolean = false): String {
        applyFont(size, bold, display)
        if (paint.measureText(value) <= maxWidth) return value
        var result = value
        while (result.isNotEmpty() && paint.measureText("$result…") > maxWidth) {
            result = result.dropLast(1)
        }
        return if (result.isEmpty()) "" else "$result…"
    }

    fun Canvas.drawStyledText(
        text: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int,
        bold: Boolean = false,
        display: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        applyFont(size, bold, display)
        paint.color = color
        paint.style = Paint.Style.FILL
        paint.textAlign = align
        drawText(text, x, baseline, paint)
    }

    fun Canvas.fillRoundBox(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
        fillColor: Int,
        strokeColor: Int? = null,
        strokeWidth: Float = 1f
    ) {
        val rect = RectF(left, top, right, bottom)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = fillColor
        drawRoundRect(rect, radius, radius, paint)
        if (strokeColor != null) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            paint.color = strokeColor
            drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.FILL
        }
    }

    fun Canvas.drawLogoMark(left: Float, top: Float, boxSize: Float) {
        fillRoundBox(left, top, left + boxSize, top + boxSize, 9f, PdfPalette.White, PdfPalette.Mint, 1.2f)
        if (logoBitmap != null && !logoBitmap.isRecycled) {
            val inset = 4f
            val dest = RectF(left + inset, top + inset, left + boxSize - inset, top + boxSize - inset)
            drawBitmap(logoBitmap, null, dest, paint)
        } else {
            val cx = left + boxSize / 2f
            val cy = top + boxSize / 2f
            paint.color = PdfPalette.Emerald
            paint.style = Paint.Style.FILL
            val path = Path().apply {
                moveTo(cx, top + 8f)
                lineTo(left + boxSize - 8f, cy)
                lineTo(cx, top + boxSize - 8f)
                lineTo(left + 8f, cy)
                close()
            }
            drawPath(path, paint)
            paint.color = PdfPalette.Mint
            drawCircle(cx, cy, 4f, paint)
        }
    }

    var pageNumber = 1
    var currentCanvas = session.beginPage(pageWidth.toInt(), pageHeight.toInt(), pageNumber)
    var y = 0f

    fun Canvas.drawFooter() {
        val footerTop = 804f
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = PdfPalette.Emerald
        drawRect(margin, footerTop, margin + contentWidth * 0.72f, footerTop + 1.5f, paint)
        paint.color = PdfPalette.AmberAccent
        drawRect(margin + contentWidth * 0.72f, footerTop, pageWidth - margin, footerTop + 1.5f, paint)

        drawStyledText(
            fittingText("$safeBusiness  ·  Counter POS Analytics Suite  ·  Generated $generatedLabel", 420f, 7.5f),
            margin,
            823f,
            7.5f,
            PdfPalette.Muted
        )

        val pillRight = pageWidth - margin
        val pillLeft = pillRight - 52f
        fillRoundBox(pillLeft, 811f, pillRight, 827f, 8f, PdfPalette.DeepForest)
        drawStyledText(
            "Page $pageNumber",
            (pillLeft + pillRight) / 2f,
            822f,
            7.5f,
            PdfPalette.Mint,
            bold = true,
            align = Paint.Align.CENTER
        )
    }

    fun Canvas.drawTableHeader(startY: Float): Float {
        val headerHeight = 26f
        val path = Path().apply {
            addRoundRect(
                RectF(margin, startY, pageWidth - margin, startY + headerHeight),
                floatArrayOf(7f, 7f, 7f, 7f, 0f, 0f, 0f, 0f),
                Path.Direction.CW
            )
        }
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = PdfPalette.DeepForest
        drawPath(path, paint)

        drawStyledText("TIME", margin + 10f, startY + 16.5f, 7.2f, PdfPalette.Mint, bold = true)
        drawStyledText("PRODUCT / UNIT", margin + 88f, startY + 16.5f, 7.2f, PdfPalette.White, bold = true)
        drawStyledText("COUNTER / STAFF", margin + 275f, startY + 16.5f, 7.2f, PdfPalette.White, bold = true)
        drawStyledText("METHOD", margin + 390f, startY + 16.5f, 7.2f, PdfPalette.White, bold = true)
        drawStyledText("QTY", margin + 455f, startY + 16.5f, 7.2f, PdfPalette.White, bold = true, align = Paint.Align.RIGHT)
        drawStyledText("AMOUNT", pageWidth - margin - 10f, startY + 16.5f, 7.2f, PdfPalette.Mint, bold = true, align = Paint.Align.RIGHT)
        return startY + headerHeight
    }

    fun startContinuationPage() {
        currentCanvas.drawFooter()
        session.endPage()
        pageNumber += 1
        currentCanvas = session.beginPage(pageWidth.toInt(), pageHeight.toInt(), pageNumber)
        val canvas = currentCanvas

        // Compact branded emerald continuation banner
        canvas.fillRoundBox(margin, 24f, pageWidth - margin, 64f, 10f, PdfPalette.DeepForest)
        canvas.drawLogoMark(margin + 10f, 31f, 26f)
        canvas.drawStyledText(
            fittingText(safeBusiness.uppercase(Locale.ROOT), 240f, 7.2f, bold = true),
            margin + 44f,
            41f,
            7.2f,
            PdfPalette.Mint,
            bold = true
        )
        canvas.drawStyledText(
            fittingText("$title  ·  Transaction Ledger (Cont.)", 300f, 12.5f, bold = true, display = true),
            margin + 44f,
            55f,
            12.5f,
            PdfPalette.White,
            bold = true,
            display = true
        )
        canvas.drawStyledText(
            "${formatReportDateFriendly(startMs)} — ${formatReportDateFriendly(endMs)}",
            pageWidth - margin - 12f,
            49f,
            8f,
            PdfPalette.SoftText,
            align = Paint.Align.RIGHT
        )

        y = 76f
        y = canvas.drawTableHeader(y)
    }

    // ==================== PAGE 1: BRANDED HERO BANNER ====================
    val c1 = currentCanvas
    val heroTop = 24f
    val heroBottom = 128f
    val heroRect = RectF(margin, heroTop, pageWidth - margin, heroBottom)

    c1.save()
    val heroClip = Path().apply { addRoundRect(heroRect, 14f, 14f, Path.Direction.CW) }
    c1.clipPath(heroClip)
    paint.shader = LinearGradient(
        margin, heroTop, pageWidth - margin, heroBottom,
        PdfPalette.DeepForest, PdfPalette.ForestMid, Shader.TileMode.CLAMP
    )
    paint.style = Paint.Style.FILL
    c1.drawRect(heroRect, paint)
    paint.shader = null

    // Atmospheric emerald & amber glow circles inside hero header
    paint.color = android.graphics.Color.argb(38, 94, 230, 176)
    c1.drawCircle(pageWidth - margin - 34f, heroTop + 18f, 68f, paint)
    paint.color = android.graphics.Color.argb(26, 232, 160, 74)
    c1.drawCircle(pageWidth - margin - 108f, heroBottom + 14f, 44f, paint)

    // Bottom dual-color brand stripe
    paint.color = PdfPalette.Mint
    c1.drawRect(margin, heroBottom - 3.5f, margin + contentWidth * 0.72f, heroBottom, paint)
    paint.color = PdfPalette.AmberAccent
    c1.drawRect(margin + contentWidth * 0.72f, heroBottom - 3.5f, pageWidth - margin, heroBottom, paint)
    c1.restore()

    // Logo + Title inside hero
    c1.drawLogoMark(margin + 16f, heroTop + 18f, 46f)
    val textLeft = margin + 74f
    c1.drawStyledText(
        fittingText(safeBusiness.uppercase(Locale.ROOT), 285f, 8.5f, bold = true),
        textLeft,
        heroTop + 31f,
        8.5f,
        PdfPalette.Mint,
        bold = true
    )
    c1.drawStyledText(
        fittingText(title, 295f, 20f, bold = true, display = true),
        textLeft,
        heroTop + 54f,
        20f,
        PdfPalette.White,
        bold = true,
        display = true
    )
    val periodLine = "${formatReportDateFriendly(startMs)} — ${formatReportDateFriendly(endMs)}" +
        if (!filterSummary.isNullOrBlank()) "   •   $filterSummary" else ""
    c1.drawStyledText(
        fittingText(periodLine, 320f, 8.2f),
        textLeft,
        heroTop + 73f,
        8.2f,
        PdfPalette.SoftText
    )

    // Hero top-right badge & timestamp
    val badgeRight = pageWidth - margin - 16f
    val badgeLeft = badgeRight - 108f
    c1.fillRoundBox(badgeLeft, heroTop + 18f, badgeRight, heroTop + 36f, 9f, android.graphics.Color.argb(40, 94, 230, 176), PdfPalette.Mint, 0.8f)
    c1.drawStyledText(
        "COUNTER POS",
        (badgeLeft + badgeRight) / 2f,
        heroTop + 30f,
        7.5f,
        PdfPalette.Mint,
        bold = true,
        align = Paint.Align.CENTER
    )
    c1.drawStyledText(
        "REPORT SUMMARY",
        badgeRight,
        heroTop + 54f,
        7.5f,
        PdfPalette.SoftText,
        bold = true,
        align = Paint.Align.RIGHT
    )
    c1.drawStyledText(
        generatedLabel,
        badgeRight,
        heroTop + 67f,
        7.5f,
        PdfPalette.White,
        align = Paint.Align.RIGHT
    )

    y = heroBottom + 14f

    // ==================== 4 COLORED EXECUTIVE KPI CARDS ====================
    val cashTotal = activeSales.filter { it.paymentMethod.equals("cash", true) }.sumOf { it.totalKsh.toLong() }
    val mpesaTotal = activeSales.filter { it.paymentMethod.equals("mpesa", true) }.sumOf { it.totalKsh.toLong() }
    val grandTotal = activeSales.sumOf { it.totalKsh.toLong() }
    val otherTotal = grandTotal - cashTotal - mpesaTotal
    val totalUnits = activeSales.sumOf { it.quantity }
    val avgTicket = if (activeSales.isNotEmpty()) grandTotal / activeSales.size else 0L
    val mpesaSharePct = if (grandTotal > 0L) ((mpesaTotal.toDouble() / grandTotal.toDouble()) * 100.0).roundToInt() else 0
    val hasMargin = effectiveSnapshot.costTrackedRevenueKsh > 0L && effectiveSnapshot.profitMarginPercent != null

    val cardGap = 8f
    val kpiW = (contentWidth - cardGap * 3f) / 4f
    val kpiH = 60f

    data class PdfKpiSpec(
        val label: String,
        val value: String,
        val sub: String,
        val bg: Int,
        val accent: Int
    )

    val kpis = listOf(
        PdfKpiSpec(
            label = "GROSS REVENUE",
            value = money(grandTotal),
            sub = "Avg ticket ${money(avgTicket)}",
            bg = PdfPalette.MintSoft,
            accent = PdfPalette.Emerald
        ),
        if (hasMargin) {
            PdfKpiSpec(
                label = "EST. PROFIT",
                value = money(effectiveSnapshot.estimatedProfitKsh),
                sub = "${((effectiveSnapshot.profitMarginPercent ?: 0.0) * 10).roundToInt() / 10.0}% margin",
                bg = PdfPalette.TealSoft,
                accent = PdfPalette.Teal
            )
        } else {
            PdfKpiSpec(
                label = "UNITS SOLD",
                value = integerFormat.format(totalUnits),
                sub = "${activeSales.size} receipts issued",
                bg = PdfPalette.AmberSoft,
                accent = PdfPalette.Amber
            )
        },
        PdfKpiSpec(
            label = "TRANSACTIONS",
            value = integerFormat.format(activeSales.size),
            sub = "$totalUnits items dispensed",
            bg = PdfPalette.SlateSoft,
            accent = PdfPalette.DeepForest
        ),
        PdfKpiSpec(
            label = "M-PESA SHARE",
            value = "$mpesaSharePct%",
            sub = money(mpesaTotal),
            bg = PdfPalette.CobaltSoft,
            accent = PdfPalette.Cobalt
        )
    )

    kpis.forEachIndexed { idx, spec ->
        val left = margin + idx * (kpiW + cardGap)
        c1.fillRoundBox(left, y, left + kpiW, y + kpiH, 8f, spec.bg)
        // Left colored accent bar
        paint.color = spec.accent
        paint.style = Paint.Style.FILL
        c1.drawRoundRect(RectF(left, y + 6f, left + 3.5f, y + kpiH - 6f), 2f, 2f, paint)
        c1.drawStyledText(spec.label, left + 10f, y + 17f, 6.8f, spec.accent, bold = true)
        c1.drawStyledText(
            fittingText(spec.value, kpiW - 16f, 12.5f, bold = true, display = true),
            left + 10f,
            y + 37f,
            12.5f,
            PdfPalette.Ink,
            bold = true,
            display = true
        )
        c1.drawStyledText(
            fittingText(spec.sub, kpiW - 16f, 7.2f),
            left + 10f,
            y + 51f,
            7.2f,
            PdfPalette.Muted
        )
    }

    y += kpiH + 12f

    // ==================== PAYMENT COLLECTION MIX SUMMARY ====================
    val mixBoxH = 44f
    c1.fillRoundBox(margin, y, pageWidth - margin, y + mixBoxH, 9f, PdfPalette.White, PdfPalette.Border, 1f)
    c1.drawStyledText("PAYMENT COLLECTION MIX", margin + 12f, y + 16f, 7.5f, PdfPalette.Emerald, bold = true)
    c1.drawStyledText(
        "Reconciled total: ${money(grandTotal)}",
        pageWidth - margin - 12f,
        y + 16f,
        7.5f,
        PdfPalette.Muted,
        bold = true,
        align = Paint.Align.RIGHT
    )

    val cashPct = if (grandTotal > 0L) ((cashTotal.toDouble() / grandTotal.toDouble()) * 100.0).roundToInt() else 0
    val otherPct = if (grandTotal > 0L) (100 - cashPct - mpesaSharePct).coerceAtLeast(0) else 0
    val legendY = y + 33f
    c1.drawStyledText("Cash: ${money(cashTotal)} ($cashPct%)", margin + 12f, legendY, 7.8f, PdfPalette.Emerald, bold = true)
    c1.drawStyledText("M-Pesa: ${money(mpesaTotal)} ($mpesaSharePct%)", margin + 195f, legendY, 7.8f, PdfPalette.Cobalt, bold = true)
    if (otherTotal > 0L) {
        c1.drawStyledText("Other: ${money(otherTotal)} ($otherPct%)", margin + 382f, legendY, 7.8f, PdfPalette.Amber, bold = true)
    } else {
        c1.drawStyledText("Counters: ${counters.size.coerceAtLeast(1)}", margin + 382f, legendY, 7.8f, PdfPalette.Ink, bold = true)
    }

    y += mixBoxH + 12f

    // ==================== TOP SELLING PRODUCTS TABLE ====================
    val topFive = effectiveSnapshot.topProducts.take(5)
    if (topFive.isNotEmpty()) {
        val topBoxH = 26f + topFive.size * 20f + 10f
        c1.fillRoundBox(margin, y, pageWidth - margin, y + topBoxH, 9f, PdfPalette.White, PdfPalette.Border, 1f)
        c1.drawStyledText("TOP PERFORMING PRODUCTS", margin + 12f, y + 17f, 7.5f, PdfPalette.Emerald, bold = true)
        c1.drawStyledText(
            "QTY  ·  REVENUE",
            pageWidth - margin - 12f,
            y + 17f,
            7.0f,
            PdfPalette.Muted,
            bold = true,
            align = Paint.Align.RIGHT
        )
        var rowY = y + 26f
        topFive.forEachIndexed { idx, prod ->
            val rankColor = when (idx) {
                0 -> PdfPalette.Emerald
                1 -> PdfPalette.Teal
                2 -> PdfPalette.Cobalt
                else -> PdfPalette.Amber
            }
            c1.fillRoundBox(margin + 12f, rowY + 2f, margin + 28f, rowY + 16f, 4f, rankColor)
            c1.drawStyledText("#${idx + 1}", margin + 20f, rowY + 12f, 6.8f, PdfPalette.White, bold = true, align = Paint.Align.CENTER)
            c1.drawStyledText(
                fittingText("${prod.productName} (${prod.unitName})", 300f, 8.2f, bold = true),
                margin + 36f,
                rowY + 12.5f,
                8.2f,
                PdfPalette.Ink,
                bold = true
            )
            c1.drawStyledText(
                "${prod.totalQuantity} sold",
                margin + 430f,
                rowY + 12.5f,
                7.8f,
                PdfPalette.Muted,
                align = Paint.Align.RIGHT
            )
            c1.drawStyledText(
                money(prod.totalKsh),
                pageWidth - margin - 12f,
                rowY + 12.5f,
                8.2f,
                PdfPalette.Ink,
                bold = true,
                align = Paint.Align.RIGHT
            )
            rowY += 20f
        }
        y += topBoxH + 12f
    }

    // ==================== COUNTER BREAKDOWN STRIP (IF MULTIPLE COUNTERS) ====================
    val activeCounters = effectiveSnapshot.counterReports.filter { it.totalKsh > 0L || it.salesCount > 0 }
    if (activeCounters.size > 1) {
        val visibleCounters = activeCounters.take(4)
        val colGap = 8f
        val colW = (contentWidth - colGap * (visibleCounters.size - 1)) / visibleCounters.size
        val cBoxH = 48f
        if (y + cBoxH + 40f > contentBottomLimit) {
            startContinuationPage()
        }
        visibleCounters.forEachIndexed { idx, cData ->
            val left = margin + idx * (colW + colGap)
            currentCanvas.fillRoundBox(left, y, left + colW, y + cBoxH, 8f, PdfPalette.Zebra, PdfPalette.Border, 0.9f)
            currentCanvas.drawStyledText(
                fittingText(cData.counterName.uppercase(Locale.ROOT), colW - 14f, 6.8f, bold = true),
                left + 8f,
                y + 14f,
                6.8f,
                PdfPalette.Emerald,
                bold = true
            )
            currentCanvas.drawStyledText(
                fittingText(money(cData.totalKsh), colW - 14f, 10.5f, bold = true, display = true),
                left + 8f,
                y + 29f,
                10.5f,
                PdfPalette.Ink,
                bold = true,
                display = true
            )
            currentCanvas.drawStyledText(
                "${cData.totalItems} units · ${cData.salesCount} sales",
                left + 8f,
                y + 41f,
                6.8f,
                PdfPalette.Muted
            )
        }
        y += cBoxH + 12f
    }

    // ==================== TRANSACTION LEDGER HEADER ====================
    if (y + 64f > contentBottomLimit) {
        startContinuationPage()
    } else {
        y = currentCanvas.drawTableHeader(y)
    }

    if (activeSales.isEmpty()) {
        currentCanvas.fillRoundBox(margin, y, pageWidth - margin, y + 48f, 0f, PdfPalette.Zebra, PdfPalette.Border, 0.8f)
        currentCanvas.drawStyledText(
            "No completed sales match the selected period and filters.",
            pageWidth / 2f,
            y + 28f,
            9f,
            PdfPalette.Muted,
            align = Paint.Align.CENTER
        )
        y += 56f
    } else {
        val rowHeight = 36f
        activeSales.forEachIndexed { index, sale ->
            if (y + rowHeight > contentBottomLimit) {
                startContinuationPage()
            }
            val canvas = currentCanvas
            if (index % 2 == 1) {
                paint.color = PdfPalette.Zebra
                paint.style = Paint.Style.FILL
                canvas.drawRect(margin, y, pageWidth - margin, y + rowHeight, paint)
            }
            // Side hairlines for crisp table framing
            paint.color = PdfPalette.Border
            paint.strokeWidth = 0.7f
            canvas.drawLine(margin, y, margin, y + rowHeight, paint)
            canvas.drawLine(pageWidth - margin, y, pageWidth - margin, y + rowHeight, paint)

            canvas.drawStyledText(dateTimeFormat.format(Date(sale.createdAt)), margin + 10f, y + 15.5f, 7.3f, PdfPalette.Ink)
            if (!sale.tableName.isNullOrBlank()) {
                canvas.drawStyledText(
                    fittingText("Table: ${sale.tableName}", 72f, 6.6f),
                    margin + 10f,
                    y + 27f,
                    6.6f,
                    PdfPalette.Emerald,
                    bold = true
                )
            }

            canvas.drawStyledText(
                fittingText(sale.productName, 178f, 8.2f, bold = true),
                margin + 88f,
                y + 15f,
                8.2f,
                PdfPalette.Ink,
                bold = true
            )
            val unitDetail = "${sale.quantity} × ${sale.unitName} @ ${money(sale.unitPriceKsh.toLong())}"
            canvas.drawStyledText(
                fittingText(unitDetail, 178f, 7.0f),
                margin + 88f,
                y + 27f,
                7.0f,
                PdfPalette.Muted
            )

            val counterStaff = listOfNotNull(
                sale.counterName?.takeIf { it.isNotBlank() },
                sale.waiterName?.takeIf { it.isNotBlank() }?.substringBefore("@")
            ).joinToString(" · ").ifBlank { "Main Counter" }
            canvas.drawStyledText(
                fittingText(counterStaff, 108f, 7.2f),
                margin + 275f,
                y + 20f,
                7.2f,
                PdfPalette.Muted
            )

            // Colored Payment Pill Badge
            val methodLower = sale.paymentMethod.lowercase(Locale.ROOT)
            val (pillBg, pillFg) = when (methodLower) {
                "mpesa" -> PdfPalette.CobaltSoft to PdfPalette.Cobalt
                "cash" -> PdfPalette.MintSoft to PdfPalette.Emerald
                else -> PdfPalette.AmberSoft to PdfPalette.Amber
            }
            val pillX = margin + 390f
            canvas.fillRoundBox(pillX, y + 10f, pillX + 44f, y + 25f, 7.5f, pillBg)
            canvas.drawStyledText(
                paymentLabel(sale.paymentMethod),
                pillX + 22f,
                y + 20f,
                6.8f,
                pillFg,
                bold = true,
                align = Paint.Align.CENTER
            )

            canvas.drawStyledText(
                sale.quantity.toString(),
                margin + 455f,
                y + 20.5f,
                8.0f,
                PdfPalette.Ink,
                bold = true,
                align = Paint.Align.RIGHT
            )
            canvas.drawStyledText(
                money(sale.totalKsh.toLong()),
                pageWidth - margin - 10f,
                y + 20.5f,
                8.2f,
                PdfPalette.Ink,
                bold = true,
                align = Paint.Align.RIGHT
            )

            paint.color = PdfPalette.Border
            paint.strokeWidth = 0.7f
            canvas.drawLine(margin, y + rowHeight, pageWidth - margin, y + rowHeight, paint)
            y += rowHeight
        }
    }

    // ==================== RECONCILIATION & BRANDED TOTALS BOX ====================
    val summaryBoxHeight = 96f + (if (otherTotal != 0L) 16f else 0f) + (if (hasMargin) 16f else 0f)
    if (y + summaryBoxHeight + 14f > contentBottomLimit) {
        startContinuationPage()
    }
    y += 14f

    val sumLeft = pageWidth - margin - 260f
    val sumRight = pageWidth - margin
    val sumCanvas = currentCanvas

    sumCanvas.fillRoundBox(sumLeft, y, sumRight, y + summaryBoxHeight, 10f, PdfPalette.Zebra, PdfPalette.Border, 1f)
    var sy = y + 20f
    sumCanvas.drawStyledText("Cash Subtotal", sumLeft + 14f, sy, 8.2f, PdfPalette.Muted, bold = true)
    sumCanvas.drawStyledText(money(cashTotal), sumRight - 14f, sy, 8.5f, PdfPalette.Emerald, bold = true, align = Paint.Align.RIGHT)

    sy += 17f
    sumCanvas.drawStyledText("M-Pesa Subtotal", sumLeft + 14f, sy, 8.2f, PdfPalette.Muted, bold = true)
    sumCanvas.drawStyledText(money(mpesaTotal), sumRight - 14f, sy, 8.5f, PdfPalette.Cobalt, bold = true, align = Paint.Align.RIGHT)

    if (otherTotal != 0L) {
        sy += 17f
        sumCanvas.drawStyledText("Other Payments", sumLeft + 14f, sy, 8.2f, PdfPalette.Muted, bold = true)
        sumCanvas.drawStyledText(money(otherTotal), sumRight - 14f, sy, 8.5f, PdfPalette.Amber, bold = true, align = Paint.Align.RIGHT)
    }

    if (hasMargin) {
        sy += 17f
        sumCanvas.drawStyledText("Est. Gross Profit", sumLeft + 14f, sy, 8.2f, PdfPalette.Muted, bold = true)
        sumCanvas.drawStyledText(money(effectiveSnapshot.estimatedProfitKsh), sumRight - 14f, sy, 8.5f, PdfPalette.Teal, bold = true, align = Paint.Align.RIGHT)
    }

    val totalBannerTop = y + summaryBoxHeight - 36f
    val totalBannerPath = Path().apply {
        addRoundRect(
            RectF(sumLeft, totalBannerTop, sumRight, y + summaryBoxHeight),
            floatArrayOf(0f, 0f, 0f, 0f, 10f, 10f, 10f, 10f),
            Path.Direction.CW
        )
    }
    paint.shader = null
    paint.style = Paint.Style.FILL
    paint.color = PdfPalette.DeepForest
    sumCanvas.drawPath(totalBannerPath, paint)

    sumCanvas.drawStyledText("GRAND TOTAL", sumLeft + 14f, totalBannerTop + 22f, 9.5f, PdfPalette.White, bold = true, display = true)
    sumCanvas.drawStyledText(
        money(grandTotal),
        sumRight - 14f,
        totalBannerTop + 22.5f,
        12f,
        PdfPalette.Mint,
        bold = true,
        display = true,
        align = Paint.Align.RIGHT
    )

    currentCanvas.drawFooter()
    session.endPage()
    return session.finishToByteArray()
}

internal fun buildButcheryReportPdf(
    context: Context?,
    butcheryName: String,
    snapshot: com.alvinb.counterpos.data.butchery.ButcheryDailyReportSnapshot,
    beefOnlyMode: Boolean = false,
    singleMeatLabel: String? = null,
    sessionFactory: () -> PdfDocumentSession = { AndroidPdfDocumentSession() }
): ByteArray {
    val pageWidth = 595f
    val pageHeight = 842f
    val margin = 32f
    val contentWidth = pageWidth - margin * 2f
    val contentBottomLimit = 784f

    val safeName = butcheryName.trim().ifBlank { "Butchery POS" }
    val displayFont = context?.let {
        runCatching { ResourcesCompat.getFont(it, R.font.space_grotesk) }.getOrNull()
    } ?: Typeface.DEFAULT
    val bodyFont = context?.let {
        runCatching { ResourcesCompat.getFont(it, R.font.inter) }.getOrNull()
    } ?: Typeface.DEFAULT
    val displayBold = Typeface.create(displayFont, Typeface.BOLD)
    val bodyRegular = Typeface.create(bodyFont, Typeface.NORMAL)
    val bodyBold = Typeface.create(bodyFont, Typeface.BOLD)

    val logoBitmap: Bitmap? = context?.let {
        runCatching { BitmapFactory.decodeResource(it.resources, R.drawable.ic_counterpos_logo) }.getOrNull()
    }

    val session = sessionFactory()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val integerFormat = NumberFormat.getIntegerInstance(Locale.US)
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val generatedLabel = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date())

    fun money(value: Long): String = "KES ${integerFormat.format(value)}"
    fun weightLabel(grams: Long): String = String.format(Locale.US, "%.2f kg", grams / 1000.0)

    fun applyFont(size: Float, bold: Boolean, display: Boolean = false) {
        paint.textSize = size
        paint.typeface = when {
            display -> displayBold
            bold -> bodyBold
            else -> bodyRegular
        }
    }

    fun fittingText(value: String, maxWidth: Float, size: Float, bold: Boolean = false, display: Boolean = false): String {
        applyFont(size, bold, display)
        if (paint.measureText(value) <= maxWidth) return value
        var result = value
        while (result.isNotEmpty() && paint.measureText("$result…") > maxWidth) {
            result = result.dropLast(1)
        }
        return if (result.isEmpty()) "" else "$result…"
    }

    fun Canvas.drawStyledText(
        text: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int,
        bold: Boolean = false,
        display: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        applyFont(size, bold, display)
        paint.color = color
        paint.style = Paint.Style.FILL
        paint.textAlign = align
        drawText(text, x, baseline, paint)
    }

    fun Canvas.fillRoundBox(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
        fillColor: Int,
        strokeColor: Int? = null,
        strokeWidth: Float = 1f
    ) {
        val rect = RectF(left, top, right, bottom)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = fillColor
        drawRoundRect(rect, radius, radius, paint)
        if (strokeColor != null) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            paint.color = strokeColor
            drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.FILL
        }
    }

    var pageNumber = 1
    var currentCanvas = session.beginPage(pageWidth.toInt(), pageHeight.toInt(), pageNumber)
    var y = 0f

    fun Canvas.drawFooter() {
        val footerTop = 804f
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = PdfPalette.Emerald
        drawRect(margin, footerTop, margin + contentWidth * 0.72f, footerTop + 1.5f, paint)
        paint.color = PdfPalette.AmberAccent
        drawRect(margin + contentWidth * 0.72f, footerTop, pageWidth - margin, footerTop + 1.5f, paint)
        drawStyledText(
            fittingText("$safeName  ·  Butchery Daily Report  ·  Generated $generatedLabel", 420f, 7.5f),
            margin,
            823f,
            7.5f,
            PdfPalette.Muted
        )
        val pillRight = pageWidth - margin
        val pillLeft = pillRight - 52f
        fillRoundBox(pillLeft, 811f, pillRight, 827f, 8f, PdfPalette.DeepForest)
        drawStyledText("Page $pageNumber", (pillLeft + pillRight) / 2f, 822f, 7.5f, PdfPalette.Mint, bold = true, align = Paint.Align.CENTER)
    }

    fun Canvas.drawTableHeader(startY: Float): Float {
        val headerHeight = 26f
        val path = Path().apply {
            addRoundRect(
                RectF(margin, startY, pageWidth - margin, startY + headerHeight),
                floatArrayOf(7f, 7f, 7f, 7f, 0f, 0f, 0f, 0f),
                Path.Direction.CW
            )
        }
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = PdfPalette.DeepForest
        drawPath(path, paint)
        drawStyledText("TIME", margin + 10f, startY + 16.5f, 7.2f, PdfPalette.Mint, bold = true)
        drawStyledText("MEAT CUT", margin + 72f, startY + 16.5f, 7.2f, PdfPalette.White, bold = true)
        drawStyledText("RATE / KG", margin + 250f, startY + 16.5f, 7.2f, PdfPalette.White, bold = true, align = Paint.Align.RIGHT)
        drawStyledText("WEIGHT", margin + 332f, startY + 16.5f, 7.2f, PdfPalette.White, bold = true, align = Paint.Align.RIGHT)
        drawStyledText("METHOD", margin + 390f, startY + 16.5f, 7.2f, PdfPalette.White, bold = true)
        drawStyledText("AMOUNT", pageWidth - margin - 10f, startY + 16.5f, 7.2f, PdfPalette.Mint, bold = true, align = Paint.Align.RIGHT)
        return startY + headerHeight
    }

    fun startContinuationPage() {
        currentCanvas.drawFooter()
        session.endPage()
        pageNumber += 1
        currentCanvas = session.beginPage(pageWidth.toInt(), pageHeight.toInt(), pageNumber)
        currentCanvas.fillRoundBox(margin, 24f, pageWidth - margin, 60f, 10f, PdfPalette.DeepForest)
        currentCanvas.drawStyledText(
            fittingText("$safeName  ·  Daily Butchery Ledger (Cont.)", 380f, 12f, bold = true, display = true),
            margin + 14f,
            46f,
            12f,
            PdfPalette.White,
            bold = true,
            display = true
        )
        y = currentCanvas.drawTableHeader(72f)
    }

    // Hero Header
    val c1 = currentCanvas
    val heroTop = 24f
    val heroBottom = 118f
    val heroRect = RectF(margin, heroTop, pageWidth - margin, heroBottom)
    c1.save()
    c1.clipPath(Path().apply { addRoundRect(heroRect, 14f, 14f, Path.Direction.CW) })
    paint.shader = LinearGradient(margin, heroTop, pageWidth - margin, heroBottom, PdfPalette.DeepForest, PdfPalette.ForestMid, Shader.TileMode.CLAMP)
    paint.style = Paint.Style.FILL
    c1.drawRect(heroRect, paint)
    paint.shader = null
    paint.color = PdfPalette.Mint
    c1.drawRect(margin, heroBottom - 3.5f, margin + contentWidth * 0.72f, heroBottom, paint)
    paint.color = PdfPalette.AmberAccent
    c1.drawRect(margin + contentWidth * 0.72f, heroBottom - 3.5f, pageWidth - margin, heroBottom, paint)
    c1.restore()

    if (logoBitmap != null && !logoBitmap.isRecycled) {
        c1.fillRoundBox(margin + 16f, heroTop + 18f, margin + 58f, heroTop + 60f, 9f, PdfPalette.White, PdfPalette.Mint, 1.2f)
        c1.drawBitmap(logoBitmap, null, RectF(margin + 20f, heroTop + 22f, margin + 54f, heroTop + 56f), paint)
    }
    val textLeft = margin + 70f
    c1.drawStyledText(
        fittingText(safeName.uppercase(Locale.ROOT), 280f, 8.2f, bold = true),
        textLeft,
        heroTop + 30f,
        8.2f,
        PdfPalette.Mint,
        bold = true
    )
    c1.drawStyledText(
        "Daily Sales Report",
        textLeft,
        heroTop + 53f,
        19f,
        PdfPalette.White,
        bold = true,
        display = true
    )
    val scopeLabel = when {
        !singleMeatLabel.isNullOrBlank() -> "${singleMeatLabel}-Only Butchery"
        beefOnlyMode -> "Beef-Only Butchery"
        else -> "Full Meat Counter"
    }
    val subLine = "${dateFormat.format(Date(snapshot.startMs))}  •  $scopeLabel"
    c1.drawStyledText(subLine, textLeft, heroTop + 72f, 8.2f, PdfPalette.SoftText)

    val badgeRight = pageWidth - margin - 16f
    c1.drawStyledText("BUTCHERY POS", badgeRight, heroTop + 31f, 8.0f, PdfPalette.Mint, bold = true, align = Paint.Align.RIGHT)
    c1.drawStyledText(generatedLabel, badgeRight, heroTop + 52f, 7.6f, PdfPalette.SoftText, align = Paint.Align.RIGHT)

    y = heroBottom + 14f

    // 4 KPI Cards
    val gap = 8f
    val kpiW = (contentWidth - gap * 3f) / 4f
    val kpiH = 58f
    data class BCard(val label: String, val value: String, val sub: String, val bg: Int, val accent: Int)
    val cards = listOf(
        BCard("TOTAL SALES", money(snapshot.totalSalesKsh), "${snapshot.sales.size} transactions", PdfPalette.MintSoft, PdfPalette.Emerald),
        BCard("WEIGHT SOLD", weightLabel(snapshot.totalWeightGrams), "${snapshot.totalWeightGrams} g total", PdfPalette.AmberSoft, PdfPalette.Amber),
        BCard("M-PESA", money(snapshot.mpesaTotalKsh), "${snapshot.mpesaPercent}% of revenue", PdfPalette.TealSoft, PdfPalette.Teal),
        BCard("CASH PAYMENT", money(snapshot.cashTotalKsh), "${snapshot.cashPercent}% of revenue", PdfPalette.SlateSoft, PdfPalette.DeepForest)
    )
    cards.forEachIndexed { idx, card ->
        val left = margin + idx * (kpiW + gap)
        c1.fillRoundBox(left, y, left + kpiW, y + kpiH, 8f, card.bg)
        paint.color = card.accent
        paint.style = Paint.Style.FILL
        c1.drawRoundRect(RectF(left, y + 6f, left + 3.5f, y + kpiH - 6f), 2f, 2f, paint)
        c1.drawStyledText(card.label, left + 10f, y + 17f, 6.8f, card.accent, bold = true)
        c1.drawStyledText(fittingText(card.value, kpiW - 14f, 11.5f, bold = true, display = true), left + 10f, y + 36f, 11.5f, PdfPalette.Ink, bold = true, display = true)
        c1.drawStyledText(fittingText(card.sub, kpiW - 14f, 7.0f), left + 10f, y + 50f, 7.0f, PdfPalette.Muted)
    }
    y += kpiH + 12f

    // Conditional Profit / Loss summary strip — rendered ONLY when buying prices are provided
    val totalProfitKsh = snapshot.totalProfitKsh
    val totalCostKsh = snapshot.totalCostKsh
    if (totalProfitKsh != null && totalCostKsh != null) {
        val isLoss = totalProfitKsh < 0L
        val stripBg = if (isLoss) 0xFFFEF2F2.toInt() else PdfPalette.MintSoft
        val stripAccent = if (isLoss) 0xFFB91C1C.toInt() else PdfPalette.Emerald
        val stripTitle = if (isLoss) "EST. NET LOSS" else "EST. NET PROFIT"
        val profitText = com.alvinb.counterpos.data.butchery.ButcheryPricing.formatSignedKsh(totalProfitKsh)
        val stripH = 38f
        c1.fillRoundBox(margin, y, pageWidth - margin, y + stripH, 8f, stripBg, PdfPalette.Border, 0.9f)
        c1.drawStyledText(stripTitle, margin + 12f, y + 15f, 7.0f, stripAccent, bold = true)
        c1.drawStyledText(profitText, margin + 12f, y + 30f, 11.5f, stripAccent, bold = true, display = true)
        c1.drawStyledText(
            "Buying Cost: ${money(totalCostKsh)} · ${snapshot.costTrackedSalesCount}/${snapshot.sales.size} sales cost-tracked",
            pageWidth - margin - 12f,
            y + 24f,
            7.8f,
            PdfPalette.Muted,
            bold = true,
            align = Paint.Align.RIGHT
        )
        y += stripH + 10f
    }

    // Meat Breakdown (numeric tabular layout, no bar graphs)
    val topCuts = snapshot.topCuts.take(6)
    if (topCuts.isNotEmpty()) {
        val showProfitCol = totalProfitKsh != null
        val boxH = 26f + topCuts.size * 20f + 8f
        c1.fillRoundBox(margin, y, pageWidth - margin, y + boxH, 9f, PdfPalette.White, PdfPalette.Border, 1f)
        c1.drawStyledText("MEAT BREAKDOWN", margin + 12f, y + 17f, 7.5f, PdfPalette.Emerald, bold = true)
        if (showProfitCol) {
            c1.drawStyledText("WEIGHT", margin + 275f, y + 17f, 7.0f, PdfPalette.Muted, bold = true, align = Paint.Align.RIGHT)
            c1.drawStyledText("REVENUE", margin + 395f, y + 17f, 7.0f, PdfPalette.Muted, bold = true, align = Paint.Align.RIGHT)
            c1.drawStyledText("PROFIT / LOSS", pageWidth - margin - 12f, y + 17f, 7.0f, PdfPalette.Muted, bold = true, align = Paint.Align.RIGHT)
        } else {
            c1.drawStyledText("WEIGHT", margin + 395f, y + 17f, 7.0f, PdfPalette.Muted, bold = true, align = Paint.Align.RIGHT)
            c1.drawStyledText("REVENUE", pageWidth - margin - 12f, y + 17f, 7.0f, PdfPalette.Muted, bold = true, align = Paint.Align.RIGHT)
        }
        var rowY = y + 26f
        topCuts.forEach { cut ->
            c1.drawStyledText(fittingText(cut.cutName, 190f, 8.2f, bold = true), margin + 14f, rowY + 12.5f, 8.2f, PdfPalette.Ink, bold = true)
            if (showProfitCol) {
                c1.drawStyledText(weightLabel(cut.totalGrams), margin + 275f, rowY + 12.5f, 7.8f, PdfPalette.Muted, align = Paint.Align.RIGHT)
                c1.drawStyledText(money(cut.totalKsh), margin + 395f, rowY + 12.5f, 8.2f, PdfPalette.Ink, bold = true, align = Paint.Align.RIGHT)
                val pVal = cut.totalProfitKsh
                if (pVal != null) {
                    val pColor = if (pVal < 0L) 0xFFB91C1C.toInt() else PdfPalette.Emerald
                    c1.drawStyledText(
                        com.alvinb.counterpos.data.butchery.ButcheryPricing.formatSignedKsh(pVal),
                        pageWidth - margin - 12f,
                        rowY + 12.5f,
                        8.0f,
                        pColor,
                        bold = true,
                        align = Paint.Align.RIGHT
                    )
                }
            } else {
                c1.drawStyledText(weightLabel(cut.totalGrams), margin + 395f, rowY + 12.5f, 7.8f, PdfPalette.Muted, align = Paint.Align.RIGHT)
                c1.drawStyledText(money(cut.totalKsh), pageWidth - margin - 12f, rowY + 12.5f, 8.2f, PdfPalette.Ink, bold = true, align = Paint.Align.RIGHT)
            }
            rowY += 20f
        }
        y += boxH + 12f
    }

    // Transaction Ledger
    y = if (y + 60f > contentBottomLimit) {
        startContinuationPage()
        y
    } else {
        currentCanvas.drawTableHeader(y)
    }

    if (snapshot.sales.isEmpty()) {
        currentCanvas.fillRoundBox(margin, y, pageWidth - margin, y + 44f, 0f, PdfPalette.Zebra, PdfPalette.Border, 0.8f)
        currentCanvas.drawStyledText("No butchery sales recorded for this day.", pageWidth / 2f, y + 26f, 9f, PdfPalette.Muted, align = Paint.Align.CENTER)
    } else {
        val rowH = 30f
        snapshot.sales.forEachIndexed { idx, sale ->
            if (y + rowH > contentBottomLimit) startContinuationPage()
            val canvas = currentCanvas
            if (idx % 2 == 1) {
                paint.color = PdfPalette.Zebra
                paint.style = Paint.Style.FILL
                canvas.drawRect(margin, y, pageWidth - margin, y + rowH, paint)
            }
            paint.color = PdfPalette.Border
            paint.strokeWidth = 0.7f
            canvas.drawLine(margin, y, margin, y + rowH, paint)
            canvas.drawLine(pageWidth - margin, y, pageWidth - margin, y + rowH, paint)

            canvas.drawStyledText(timeFormat.format(Date(sale.createdAt)), margin + 10f, y + 18.5f, 7.8f, PdfPalette.Ink)
            canvas.drawStyledText(fittingText(sale.cutName, 120f, 8.2f, bold = true), margin + 72f, y + 18.5f, 8.2f, PdfPalette.Ink, bold = true)
            canvas.drawStyledText("KES ${sale.pricePerKgKsh}", margin + 250f, y + 18.5f, 7.6f, PdfPalette.Muted, align = Paint.Align.RIGHT)
            canvas.drawStyledText(weightLabel(sale.weightGrams.toLong()), margin + 332f, y + 18.5f, 8.0f, PdfPalette.Ink, bold = true, align = Paint.Align.RIGHT)

            val isMpesa = com.alvinb.counterpos.data.butchery.ButcheryPricing.isMpesaPayment(sale.paymentMethod)
            val methodLabel = com.alvinb.counterpos.data.butchery.ButcheryPricing.paymentMethodDisplayLabel(sale.paymentMethod)
            val pillBg = when {
                isMpesa -> PdfPalette.MintSoft
                com.alvinb.counterpos.data.butchery.ButcheryPricing.isCashPayment(sale.paymentMethod) -> PdfPalette.SlateSoft
                else -> PdfPalette.AmberSoft
            }
            val pillFg = when {
                isMpesa -> PdfPalette.Emerald
                com.alvinb.counterpos.data.butchery.ButcheryPricing.isCashPayment(sale.paymentMethod) -> PdfPalette.DeepForest
                else -> PdfPalette.Amber
            }
            val pillX = margin + 382f
            val pillW = 64f
            canvas.fillRoundBox(pillX, y + 8f, pillX + pillW, y + 22f, 7f, pillBg)
            canvas.drawStyledText(fittingText(methodLabel, pillW - 6f, 6.6f, bold = true), pillX + pillW / 2f, y + 17.5f, 6.6f, pillFg, bold = true, align = Paint.Align.CENTER)
            canvas.drawStyledText(money(sale.totalKsh.toLong()), pageWidth - margin - 10f, y + 18.5f, 8.2f, PdfPalette.Ink, bold = true, align = Paint.Align.RIGHT)

            paint.color = PdfPalette.Border
            canvas.drawLine(margin, y + rowH, pageWidth - margin, y + rowH, paint)
            y += rowH
        }
    }

    currentCanvas.drawFooter()
    session.endPage()
    return session.finishToByteArray()
}
