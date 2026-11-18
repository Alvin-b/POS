package com.alvinb.counterpos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.compose.ui.text.TextStyle

private val ThirtyDayAxisLabelsKey = ExtraStore.Key<List<String>>()
private val ThirtyDayPointsKey = ExtraStore.Key<List<DailyRevenuePoint>>()

@Composable
internal fun ThirtyDayRevenueVicoCard(
    insights: ThirtyDayRevenueInsights,
    scopeLabel: String,
    isDarkSurface: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trendLineColor = if (isDarkSurface) Color(0xFFF5B041) else Color(0xFFD97706)
    val positiveAccent = if (isDarkSurface) Color(0xFF5EE6B0) else Color(0xFF087F5B)
    val warningAccent = if (isDarkSurface) Color(0xFFF5B041) else Color(0xFFD97706)
    val infoAccent = if (isDarkSurface) Color(0xFF38BDF8) else Color(0xFF0284C7)

    val modelProducer = remember { CartesianChartModelProducer() }
    val scrollState = rememberVicoScrollState(
        scrollEnabled = true,
        initialScroll = Scroll.Absolute.End
    )

    val hasAnyRevenue = insights.totalRevenueKsh > 0L

    LaunchedEffect(insights.days) {
        if (insights.days.isNotEmpty() && insights.totalRevenueKsh > 0L) {
            val dailyValues = insights.days.map { it.revenueKsh.toDouble() }
            val rollingValues = insights.days.map { it.rolling7DayAvgKsh.toDouble() }
            val axisLabels = insights.days.map { it.axisLabel }

            modelProducer.runTransaction {
                columnSeries { series(dailyValues) }
                lineSeries { series(rollingValues) }
                extras { extraStore ->
                    extraStore[ThirtyDayAxisLabelsKey] = axisLabels
                    extraStore[ThirtyDayPointsKey] = insights.days
                }
            }
        }
    }

    val yAxisFormatter = remember {
        CartesianValueFormatter { _, value, _ ->
            formatCompactKshAxis(value)
        }
    }
    val xAxisFormatter = remember {
        CartesianValueFormatter { context, value, _ ->
            val labels = context.model.extraStore.getOrNull(ThirtyDayAxisLabelsKey)
            val index = value.toInt()
            if (labels != null && index in labels.indices) labels[index] else ""
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("report_thirty_day_vico_card"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = primaryColor.copy(alpha = 0.14f)
                    ) {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier
                                .padding(7.dp)
                                .size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Last 30 Days Daily Sales",
                            fontSize = 15.sp,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Daily revenue & 7-day rolling trend",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = scopeLabel,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 4-Metric 30-Day Summary Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThirtyDayMiniMetric(
                    label = "30D Total",
                    value = formatCompactKshAxis(insights.totalRevenueKsh.toDouble()).let {
                        if (insights.totalRevenueKsh >= 1_000L) "KSh $it" else formatKshLong(insights.totalRevenueKsh)
                    },
                    modifier = Modifier.weight(1f)
                )
                ThirtyDayMiniMetric(
                    label = "Daily Avg",
                    value = formatCompactKshAxis(insights.dailyAverageKsh.toDouble()).let {
                        if (insights.dailyAverageKsh >= 1_000L) "KSh $it" else formatKshLong(insights.dailyAverageKsh)
                    },
                    modifier = Modifier.weight(1f)
                )
                val paceValue = insights.weekOverWeekDeltaPercent?.let(::formatSignedPercent)
                    ?: "${insights.activeDaysCount}/30d"
                val paceAccent = when {
                    insights.weekOverWeekDeltaPercent == null -> MaterialTheme.colorScheme.onSurface
                    insights.weekOverWeekDeltaPercent >= 0.0 -> positiveAccent
                    else -> warningAccent
                }
                ThirtyDayMiniMetric(
                    label = "7D Pace",
                    value = paceValue,
                    valueColor = paceAccent,
                    modifier = Modifier.weight(0.85f)
                )
                ThirtyDayMiniMetric(
                    label = "Best Day",
                    value = insights.bestDay?.shortLabel ?: "—",
                    modifier = Modifier.weight(0.9f)
                )
            }

            // Chart Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(
                            Modifier
                                .size(width = 10.dp, height = 10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(primaryColor)
                        )
                        Text("Daily Revenue", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(
                            Modifier
                                .size(width = 12.dp, height = 3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(trendLineColor)
                        )
                        Text("7-Day Avg", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (hasAnyRevenue) {
                    Text(
                        text = "${insights.activeDaysCount}/30 active days",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor
                    )
                }
            }

            // Vico 30-Day Combo Chart
            if (!hasAnyRevenue) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 26.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Insights,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            "No Revenue Recorded in Last 30 Days",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Once sales are completed, daily bars and your 7-day rolling revenue average will appear here.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val markerLabel = rememberTextComponent(
                    style = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    padding = Insets(horizontal = 10.dp, vertical = 5.dp),
                    background = rememberShapeComponent(
                        fill = Fill(MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    )
                )
                val markerFormatter = remember {
                    DefaultCartesianMarker.ValueFormatter { context, targets ->
                        val xIndex = targets.firstOrNull()?.x?.toInt()
                        val points = context.model.extraStore.getOrNull(ThirtyDayPointsKey)
                        val point = if (xIndex != null && points != null && xIndex in points.indices) {
                            points[xIndex]
                        } else null
                        if (point != null) {
                            "${point.shortLabel} · ${formatKshLong(point.revenueKsh)} · 7d avg ${formatCompactKshAxis(point.rolling7DayAvgKsh.toDouble())}"
                        } else {
                            ""
                        }
                    }
                }
                val marker = rememberDefaultCartesianMarker(
                    label = markerLabel,
                    valueFormatter = markerFormatter
                )

                ProvideVicoTheme(rememberM3VicoTheme()) {
                    CartesianChartHost(
                        chart = rememberCartesianChart(
                            rememberColumnCartesianLayer(
                                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                                    rememberLineComponent(
                                        fill = Fill(primaryColor),
                                        thickness = 10.dp,
                                        shape = RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)
                                    )
                                )
                            ),
                            rememberLineCartesianLayer(
                                lineProvider = LineCartesianLayer.LineProvider.series(
                                    LineCartesianLayer.Line(
                                        fill = LineCartesianLayer.LineFill.single(Fill(trendLineColor)),
                                        areaFill = LineCartesianLayer.AreaFill.single(
                                            Fill(trendLineColor.copy(alpha = 0.14f))
                                        )
                                    )
                                )
                            ),
                            startAxis = VerticalAxis.rememberStart(
                                valueFormatter = yAxisFormatter
                            ),
                            bottomAxis = HorizontalAxis.rememberBottom(
                                valueFormatter = xAxisFormatter,
                                itemPlacer = HorizontalAxis.ItemPlacer.aligned(
                                    spacing = { 5 },
                                    addExtremeLabelPadding = true
                                )
                            ),
                            marker = marker
                        ),
                        modelProducer = modelProducer,
                        scrollState = scrollState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .testTag("report_thirty_day_vico_chart")
                    )
                }
            }

            // Actionable Business Insights Section
            if (insights.actionableInsights.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Actionable Insights",
                        fontSize = 13.sp,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    insights.actionableInsights.forEachIndexed { idx, insight ->
                        val (accent, icon) = when (insight.tone) {
                            InsightTone.POSITIVE -> positiveAccent to Icons.AutoMirrored.Filled.TrendingUp
                            InsightTone.WARNING -> warningAccent to Icons.Default.WarningAmber
                            InsightTone.INFO -> infoAccent to Icons.Default.Insights
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = accent.copy(alpha = if (isDarkSurface) 0.12f else 0.08f),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("report_actionable_insight_$idx")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = accent.copy(alpha = 0.18f),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = insight.title,
                                        fontSize = 12.sp,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = insight.detail,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThirtyDayMiniMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                fontSize = 12.sp,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
