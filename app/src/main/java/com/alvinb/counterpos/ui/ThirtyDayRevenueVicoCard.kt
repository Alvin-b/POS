package com.alvinb.counterpos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ThirtyDayRevenueVicoCard(
    insights: ThirtyDayRevenueInsights,
    scopeLabel: String,
    isDarkSurface: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val positiveAccent = if (isDarkSurface) Color(0xFF5EE6B0) else Color(0xFF087F5B)
    val warningAccent = if (isDarkSurface) Color(0xFFF5B041) else Color(0xFFD97706)
    val infoAccent = if (isDarkSurface) Color(0xFF38BDF8) else Color(0xFF0284C7)

    val hasAnyRevenue = insights.totalRevenueKsh > 0L

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
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier
                                .padding(7.dp)
                                .size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Last 30 Days Summary",
                            fontSize = 15.sp,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (hasAnyRevenue) {
                                "${insights.activeDaysCount}/30 active days recorded"
                            } else {
                                "30-day revenue metrics & insights"
                            },
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

            if (!hasAnyRevenue) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Insights,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            "No Revenue Recorded in Last 30 Days",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Once sales are completed, 30-day revenue totals and insights will appear here.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

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
