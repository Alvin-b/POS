package com.alvinb.counterpos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvinb.counterpos.data.butchery.ButcheryCategory
import com.alvinb.counterpos.data.butchery.ButcheryDailySalesTrendSnapshot
import com.alvinb.counterpos.data.butchery.ButcheryDailyTrendPoint
import com.alvinb.counterpos.data.butchery.ButcheryPricing
import com.alvinb.counterpos.data.butchery.ButcheryTrendRange
import com.alvinb.counterpos.data.local.entity.ButcheryConfigEntity
import com.alvinb.counterpos.data.local.entity.ButcherySaleEntity
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val DailySalesAxisLabelsKey = ExtraStore.Key<List<String>>()
private val DailySalesPointsKey = ExtraStore.Key<List<ButcheryDailyTrendPoint>>()

@Composable
fun ButcheryDailySalesScreen(
    config: ButcheryConfigEntity?,
    sales: List<ButcherySaleEntity>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    referenceMs: Long? = null
) {
    val enabledCategories = remember(config?.enabledCategoriesJson, config?.beefOnlyMode) {
        ButcheryPricing.resolveEnabledCategories(config)
    }
    val categoryChips = remember(enabledCategories) {
        if (enabledCategories.size <= 1) {
            enabledCategories
        } else {
            listOf(ButcheryCategory.ALL.label) + enabledCategories
        }
    }

    var selectedRange by rememberSaveable { mutableStateOf(ButcheryTrendRange.FOURTEEN_DAYS) }
    var selectedCategory by rememberSaveable {
        mutableStateOf(
            if (enabledCategories.size == 1) enabledCategories.first() else ButcheryCategory.ALL.label
        )
    }

    LaunchedEffect(enabledCategories) {
        if (enabledCategories.size == 1) {
            selectedCategory = enabledCategories.first()
        } else if (selectedCategory != ButcheryCategory.ALL.label &&
            enabledCategories.none { it.equals(selectedCategory, ignoreCase = true) }
        ) {
            selectedCategory = ButcheryCategory.ALL.label
        }
    }

    val snapshot = remember(sales, selectedRange, selectedCategory, referenceMs) {
        ButcheryPricing.buildDailySalesTrend(
            allSales = sales,
            range = selectedRange,
            categoryFilter = selectedCategory,
            referenceMs = referenceMs ?: System.currentTimeMillis()
        )
    }

    val butcheryName = config?.butcheryName?.takeIf(String::isNotBlank) ?: "Butchery Analytics"
    val revenueAccent = ButcheryPalette.EmeraldHeader
    val rollingAccent = Color(0xFFD97706)
    val quantityAccent = Color(0xFF0F766E)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ButcheryPalette.SoftSurface)
    ) {
        // Header
        Surface(color = ButcheryPalette.EmeraldHeader, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("butchery_daily_sales_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Daily Sales",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$butcheryName · Revenue & Meat Sold Trends",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = selectedRange.label,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("butchery_daily_sales_list"),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Range & Category Filter Controls
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = ButcheryPalette.EmeraldHeader,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Performance Window",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ButcheryTrendRange.entries.forEach { range ->
                                FilterChip(
                                    selected = selectedRange == range,
                                    onClick = { selectedRange = range },
                                    label = {
                                        Text(
                                            text = range.label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    modifier = Modifier.testTag("butchery_trend_range_${range.days}")
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (enabledCategories.size == 1) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF1F2D27)
                            ) {
                                Text(
                                    text = "${enabledCategories.first()} Only",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        } else {
                            categoryChips.forEach { cat ->
                                val tagSuffix = cat.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_")
                                FilterChip(
                                    selected = selectedCategory.equals(cat, ignoreCase = true),
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat, fontSize = 11.sp) },
                                    modifier = Modifier.testTag("butchery_trend_category_$tagSuffix")
                                )
                            }
                        }
                    }
                }
            }

            // Two Main Trend KPI Cards (Total Revenue + Total Meat Sold kg)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DailySalesTrendKpiCard(
                        title = "Total Revenue",
                        value = ButcheryPricing.formatKsh(snapshot.totalRevenueKsh),
                        subtitle = if (snapshot.activeDaysCount > 0) {
                            "Avg ${ButcheryPricing.formatKsh(snapshot.avgDailyRevenueKsh)} / active day"
                        } else {
                            "No revenue in window"
                        },
                        changePercent = snapshot.revenueChangePercent,
                        accentColor = revenueAccent,
                        valueTag = "butchery_daily_sales_total_revenue",
                        modifier = Modifier.weight(1f)
                    )
                    DailySalesTrendKpiCard(
                        title = "Meat Sold",
                        value = ButcheryPricing.formatWeightKg(snapshot.totalWeightGrams, decimals = 1),
                        subtitle = if (snapshot.activeDaysCount > 0) {
                            "Avg ${ButcheryPricing.formatWeightKg(snapshot.avgDailyWeightGrams, 1)} / day · ${snapshot.totalTransactions} sales"
                        } else {
                            "0 sales in window"
                        },
                        changePercent = snapshot.quantityChangePercent,
                        accentColor = quantityAccent,
                        valueTag = "butchery_daily_sales_total_kg",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Peak Highlights Strip
            if (snapshot.bestRevenueDay != null || snapshot.bestVolumeDay != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        snapshot.bestRevenueDay?.let { peakRev ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text("Peak Revenue Day", fontSize = 10.sp, color = Color(0xFF64748B))
                                    Text(
                                        text = peakRev.shortDateLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = ButcheryPricing.formatKsh(peakRev.revenueKsh),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = revenueAccent
                                    )
                                }
                            }
                        }
                        snapshot.bestVolumeDay?.let { peakVol ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text("Peak Volume Day", fontSize = 10.sp, color = Color(0xFF64748B))
                                    Text(
                                        text = peakVol.shortDateLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = ButcheryPricing.formatWeightKg(peakVol.weightGrams, decimals = 2),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = quantityAccent
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Vico Chart 1: Daily Total Revenue (KES) + Rolling Trend
            item {
                DailyRevenueVicoChartCard(
                    snapshot = snapshot,
                    barColor = revenueAccent,
                    trendColor = rollingAccent
                )
            }

            // Vico Chart 2: Daily Quantity of Meat Sold (kg) + Rolling Trend
            item {
                DailyQuantityVicoChartCard(
                    snapshot = snapshot,
                    lineColor = quantityAccent,
                    rollingColor = rollingAccent
                )
            }

            // Day-by-Day Performance Log
            item {
                DailyBreakdownCard(snapshot = snapshot, revenueColor = revenueAccent)
            }
        }
    }
}

@Composable
private fun DailySalesTrendKpiCard(
    title: String,
    value: String,
    subtitle: String,
    changePercent: Double?,
    accentColor: Color,
    valueTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.Medium
                )
                if (changePercent != null) {
                    val isUp = changePercent >= 0.0
                    val pillColor = if (isUp) ButcheryPalette.EmeraldHeader else Color(0xFFD97706)
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = pillColor.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = if (isUp) {
                                    Icons.AutoMirrored.Filled.TrendingUp
                                } else {
                                    Icons.AutoMirrored.Filled.TrendingDown
                                },
                                contentDescription = null,
                                tint = pillColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = formatTrendDelta(changePercent),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = pillColor
                            )
                        }
                    }
                }
            }
            Text(
                text = value,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(valueTag)
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DailyRevenueVicoChartCard(
    snapshot: ButcheryDailySalesTrendSnapshot,
    barColor: Color,
    trendColor: Color
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val scrollState = rememberVicoScrollState(
        scrollEnabled = true,
        initialScroll = Scroll.Absolute.End
    )
    val hasData = snapshot.totalRevenueKsh > 0L

    LaunchedEffect(snapshot.days) {
        if (snapshot.days.isNotEmpty() && snapshot.totalRevenueKsh > 0L) {
            val dailyRevenues = snapshot.days.map { it.revenueKsh.toDouble() }
            val rollingRevenues = snapshot.days.map { it.rollingAvgRevenueKsh.toDouble() }
            val axisLabels = snapshot.days.map { it.axisLabel }
            modelProducer.runTransaction {
                columnSeries { series(dailyRevenues) }
                lineSeries { series(rollingRevenues) }
                extras { extraStore ->
                    extraStore[DailySalesAxisLabelsKey] = axisLabels
                    extraStore[DailySalesPointsKey] = snapshot.days
                }
            }
        }
    }

    val yFormatter = remember {
        CartesianValueFormatter { _, value, _ ->
            formatCompactKsh(value)
        }
    }
    val xFormatter = remember {
        CartesianValueFormatter { context, value, _ ->
            val labels = context.model.extraStore.getOrNull(DailySalesAxisLabelsKey)
            val idx = value.toInt()
            if (labels != null && idx in labels.indices) labels[idx] else ""
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("butchery_daily_revenue_chart_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = barColor.copy(alpha = 0.14f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = barColor,
                            modifier = Modifier
                                .padding(7.dp)
                                .size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Total Revenue per Day",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Daily KES revenue & rolling pace",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = barColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = snapshot.range.subtitle,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = barColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendSwatch(color = barColor, label = "Daily Revenue (KES)", isBar = true)
                LegendSwatch(color = trendColor, label = "Rolling Trend", isBar = false)
            }

            if (!hasData) {
                DailySalesEmptyChartBox(
                    title = "No Revenue Recorded in ${snapshot.range.subtitle}",
                    subtitle = "Complete butchery sales to visualize daily KES revenue trends."
                )
            } else {
                val markerLabel = rememberTextComponent(
                    style = TextStyle(
                        color = Color(0xFF0F172A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    padding = Insets(horizontal = 10.dp, vertical = 5.dp),
                    background = rememberShapeComponent(
                        fill = Fill(Color(0xFFF1F5F9)),
                        shape = RoundedCornerShape(10.dp)
                    )
                )
                val markerFormatter = remember {
                    DefaultCartesianMarker.ValueFormatter { context, targets ->
                        val xIndex = targets.firstOrNull()?.x?.toInt()
                        val points = context.model.extraStore.getOrNull(DailySalesPointsKey)
                        val pt = if (xIndex != null && points != null && xIndex in points.indices) points[xIndex] else null
                        if (pt != null) {
                            "${pt.shortDateLabel} · ${ButcheryPricing.formatKsh(pt.revenueKsh)} (${ButcheryPricing.formatWeightKg(pt.weightGrams, 1)})"
                        } else ""
                    }
                }
                val marker = rememberDefaultCartesianMarker(label = markerLabel, valueFormatter = markerFormatter)
                val spacingStep = when (snapshot.range) {
                    ButcheryTrendRange.SEVEN_DAYS -> 1
                    ButcheryTrendRange.FOURTEEN_DAYS -> 2
                    ButcheryTrendRange.THIRTY_DAYS -> 5
                }

                ProvideVicoTheme(rememberM3VicoTheme()) {
                    CartesianChartHost(
                        chart = rememberCartesianChart(
                            rememberColumnCartesianLayer(
                                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                                    rememberLineComponent(
                                        fill = Fill(barColor),
                                        thickness = if (snapshot.range == ButcheryTrendRange.SEVEN_DAYS) 18.dp else 11.dp,
                                        shape = RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)
                                    )
                                )
                            ),
                            rememberLineCartesianLayer(
                                lineProvider = LineCartesianLayer.LineProvider.series(
                                    LineCartesianLayer.Line(
                                        fill = LineCartesianLayer.LineFill.single(Fill(trendColor)),
                                        areaFill = LineCartesianLayer.AreaFill.single(Fill(trendColor.copy(alpha = 0.14f)))
                                    )
                                )
                            ),
                            startAxis = VerticalAxis.rememberStart(valueFormatter = yFormatter),
                            bottomAxis = HorizontalAxis.rememberBottom(
                                valueFormatter = xFormatter,
                                itemPlacer = HorizontalAxis.ItemPlacer.aligned(
                                    spacing = { spacingStep },
                                    addExtremeLabelPadding = true
                                )
                            ),
                            marker = marker
                        ),
                        modelProducer = modelProducer,
                        scrollState = scrollState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .testTag("butchery_daily_revenue_vico_chart")
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyQuantityVicoChartCard(
    snapshot: ButcheryDailySalesTrendSnapshot,
    lineColor: Color,
    rollingColor: Color
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val scrollState = rememberVicoScrollState(
        scrollEnabled = true,
        initialScroll = Scroll.Absolute.End
    )
    val hasData = snapshot.totalWeightGrams > 0L

    LaunchedEffect(snapshot.days) {
        if (snapshot.days.isNotEmpty() && snapshot.totalWeightGrams > 0L) {
            val dailyKg = snapshot.days.map { it.weightKg }
            val rollingKg = snapshot.days.map { it.rollingAvgWeightKg }
            val axisLabels = snapshot.days.map { it.axisLabel }
            modelProducer.runTransaction {
                lineSeries {
                    series(dailyKg)
                    series(rollingKg)
                }
                extras { extraStore ->
                    extraStore[DailySalesAxisLabelsKey] = axisLabels
                    extraStore[DailySalesPointsKey] = snapshot.days
                }
            }
        }
    }

    val yFormatter = remember {
        CartesianValueFormatter { _, value, _ ->
            formatCompactKg(value)
        }
    }
    val xFormatter = remember {
        CartesianValueFormatter { context, value, _ ->
            val labels = context.model.extraStore.getOrNull(DailySalesAxisLabelsKey)
            val idx = value.toInt()
            if (labels != null && idx in labels.indices) labels[idx] else ""
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("butchery_daily_quantity_chart_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = lineColor.copy(alpha = 0.14f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Scale,
                            contentDescription = null,
                            tint = lineColor,
                            modifier = Modifier
                                .padding(7.dp)
                                .size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Quantity of Meat Sold per Day",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Daily kilograms (kg) weighed & sold",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = lineColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Kilograms",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = lineColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendSwatch(color = lineColor, label = "Daily Meat Sold (kg)", isBar = false)
                LegendSwatch(color = rollingColor, label = "Rolling Avg (kg)", isBar = false)
            }

            if (!hasData) {
                DailySalesEmptyChartBox(
                    title = "No Meat Weight Sold in ${snapshot.range.subtitle}",
                    subtitle = "Record weight-based or digital-scale price sales to track kg trends."
                )
            } else {
                val markerLabel = rememberTextComponent(
                    style = TextStyle(
                        color = Color(0xFF0F172A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    padding = Insets(horizontal = 10.dp, vertical = 5.dp),
                    background = rememberShapeComponent(
                        fill = Fill(Color(0xFFF1F5F9)),
                        shape = RoundedCornerShape(10.dp)
                    )
                )
                val markerFormatter = remember {
                    DefaultCartesianMarker.ValueFormatter { context, targets ->
                        val xIndex = targets.firstOrNull()?.x?.toInt()
                        val points = context.model.extraStore.getOrNull(DailySalesPointsKey)
                        val pt = if (xIndex != null && points != null && xIndex in points.indices) points[xIndex] else null
                        if (pt != null) {
                            "${pt.shortDateLabel} · ${ButcheryPricing.formatWeightKg(pt.weightGrams, 2)} (${pt.salesCount} sales)"
                        } else ""
                    }
                }
                val marker = rememberDefaultCartesianMarker(label = markerLabel, valueFormatter = markerFormatter)
                val spacingStep = when (snapshot.range) {
                    ButcheryTrendRange.SEVEN_DAYS -> 1
                    ButcheryTrendRange.FOURTEEN_DAYS -> 2
                    ButcheryTrendRange.THIRTY_DAYS -> 5
                }

                ProvideVicoTheme(rememberM3VicoTheme()) {
                    CartesianChartHost(
                        chart = rememberCartesianChart(
                            rememberLineCartesianLayer(
                                lineProvider = LineCartesianLayer.LineProvider.series(
                                    LineCartesianLayer.Line(
                                        fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
                                        areaFill = LineCartesianLayer.AreaFill.single(Fill(lineColor.copy(alpha = 0.18f)))
                                    ),
                                    LineCartesianLayer.Line(
                                        fill = LineCartesianLayer.LineFill.single(Fill(rollingColor))
                                    )
                                )
                            ),
                            startAxis = VerticalAxis.rememberStart(valueFormatter = yFormatter),
                            bottomAxis = HorizontalAxis.rememberBottom(
                                valueFormatter = xFormatter,
                                itemPlacer = HorizontalAxis.ItemPlacer.aligned(
                                    spacing = { spacingStep },
                                    addExtremeLabelPadding = true
                                )
                            ),
                            marker = marker
                        ),
                        modelProducer = modelProducer,
                        scrollState = scrollState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .testTag("butchery_daily_quantity_vico_chart")
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyBreakdownCard(
    snapshot: ButcheryDailySalesTrendSnapshot,
    revenueColor: Color
) {
    val activeRows = remember(snapshot.days) {
        snapshot.days.filter { it.salesCount > 0 || it.revenueKsh > 0L || it.weightGrams > 0L }.reversed()
    }
    val maxDayRevenue = remember(activeRows) {
        activeRows.maxOfOrNull { it.revenueKsh }?.coerceAtLeast(1L) ?: 1L
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("butchery_daily_breakdown_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daily Performance Breakdown",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "${snapshot.activeDaysCount}/${snapshot.range.days} active days",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }

            if (activeRows.isEmpty()) {
                Text(
                    text = "No daily sales activity recorded in ${snapshot.range.subtitle.lowercase(Locale.ROOT)}.",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                activeRows.forEachIndexed { idx, day ->
                    val ratio = (day.revenueKsh.toFloat() / maxDayRevenue.toFloat()).coerceIn(0.06f, 1f)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("butchery_daily_breakdown_row_${day.axisLabel}"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = day.shortDateLabel,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "${day.salesCount} sale${if (day.salesCount == 1) "" else "s"} · ${ButcheryPricing.formatWeightKg(day.weightGrams, 2)}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                            Text(
                                text = ButcheryPricing.formatKsh(day.revenueKsh),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = revenueColor
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFE2E8F0))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(ratio)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(revenueColor)
                            )
                        }
                    }
                    if (idx < activeRows.lastIndex) {
                        HorizontalDivider(color = ButcheryPalette.KeypadBorder)
                    }
                }
            }
        }
    }
}

@Composable
private fun DailySalesEmptyChartBox(
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ButcheryPalette.SoftSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = ButcheryPalette.EmeraldHeader.copy(alpha = 0.12f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Insights,
                        contentDescription = null,
                        tint = ButcheryPalette.EmeraldHeader,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String, isBar: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = if (isBar) 10.dp else 12.dp, height = if (isBar) 10.dp else 3.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xFF475569)
        )
    }
}

private fun formatCompactKsh(value: Double): String {
    val absVal = abs(value)
    return when {
        absVal >= 1_000_000.0 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        absVal >= 1_000.0 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> value.roundToInt().toString()
    }
}

private fun formatCompactKg(value: Double): String {
    val absVal = abs(value)
    return when {
        absVal >= 100.0 -> String.format(Locale.US, "%.0f kg", value)
        absVal >= 10.0 -> String.format(Locale.US, "%.1f kg", value)
        else -> String.format(Locale.US, "%.2f kg", value)
    }
}

private fun formatTrendDelta(percent: Double): String {
    val prefix = if (percent >= 0.0) "+" else ""
    return String.format(Locale.US, "%s%.1f%%", prefix, percent)
}
