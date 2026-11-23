package com.alvinb.counterpos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvinb.counterpos.data.butchery.ButcheryDailySalesTrendSnapshot
import com.alvinb.counterpos.data.butchery.ButcheryPricing
import com.alvinb.counterpos.data.butchery.ButcheryTrendRange
import com.alvinb.counterpos.data.local.entity.ButcheryConfigEntity
import com.alvinb.counterpos.data.local.entity.ButcherySaleEntity
import java.util.Locale

@Composable
fun ButcheryDailySalesScreen(
    config: ButcheryConfigEntity?,
    sales: List<ButcherySaleEntity>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    referenceMs: Long? = null
) {
    val enabledMeats = remember(config?.enabledCategoriesJson, config?.beefOnlyMode) {
        ButcheryPricing.resolveEnabledMeats(config)
    }
    val meatChips = remember(enabledMeats) {
        if (enabledMeats.size <= 1) {
            enabledMeats
        } else {
            listOf(ButcheryPricing.ALL_MEATS_LABEL) + enabledMeats
        }
    }

    var selectedRange by rememberSaveable { mutableStateOf(ButcheryTrendRange.FOURTEEN_DAYS) }
    var selectedMeatName by rememberSaveable {
        mutableStateOf(
            if (enabledMeats.size == 1) enabledMeats.first() else ButcheryPricing.ALL_MEATS_LABEL
        )
    }

    LaunchedEffect(enabledMeats) {
        if (enabledMeats.size == 1) {
            selectedMeatName = enabledMeats.first()
        } else if (selectedMeatName != ButcheryPricing.ALL_MEATS_LABEL &&
            enabledMeats.none { it.equals(selectedMeatName, ignoreCase = true) }
        ) {
            selectedMeatName = ButcheryPricing.ALL_MEATS_LABEL
        }
    }

    val snapshot = remember(sales, selectedRange, selectedMeatName, referenceMs) {
        ButcheryPricing.buildDailySalesTrend(
            allSales = sales,
            range = selectedRange,
            meatNameFilter = selectedMeatName,
            referenceMs = referenceMs ?: System.currentTimeMillis()
        )
    }

    val butcheryName = config?.butcheryName?.takeIf(String::isNotBlank) ?: "Butchery Analytics"
    val revenueAccent = ButcheryPalette.EmeraldHeader
    val quantityAccent = Color(0xFF0F766E)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ButcheryPalette.SoftSurface)
    ) {
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
                        text = "$butcheryName · Revenue & Meat Sold Summary",
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
            // Range & Meat Name Filter Controls
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
                        if (enabledMeats.size == 1) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF1F2D27)
                            ) {
                                Text(
                                    text = "${enabledMeats.first()} Only",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        } else {
                            meatChips.forEach { meatName ->
                                val tagSuffix = meatName.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_")
                                FilterChip(
                                    selected = selectedMeatName.equals(meatName, ignoreCase = true),
                                    onClick = { selectedMeatName = meatName },
                                    label = { Text(meatName, fontSize = 11.sp) },
                                    modifier = Modifier.testTag("butchery_trend_meat_$tagSuffix")
                                )
                            }
                        }
                    }
                }
            }

            // Two Main KPI Cards (Total Revenue + Total Meat Sold kg)
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

            // Conditional Profit / Loss Card (ONLY when buying prices were provided)
            val netProfit = snapshot.totalProfitKsh
            if (netProfit != null) {
                item {
                    val isProfit = netProfit >= 0L
                    val accent = if (isProfit) revenueAccent else Color(0xFFB91C1C)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = if (isProfit) "Estimated Net Profit" else "Estimated Net Loss",
                                fontSize = 11.sp,
                                color = Color(0xFF475569),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = ButcheryPricing.formatSignedKsh(netProfit),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = accent,
                                modifier = Modifier.testTag("butchery_daily_sales_profit_loss")
                            )
                            Text(
                                text = "Total Cost: ${ButcheryPricing.formatKsh(snapshot.totalCostKsh ?: 0L)} · ${snapshot.costTrackedTransactions} cost-tracked sales",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
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

            // Day-by-Day Performance Log (pure numeric summary, no graphs/bars)
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
private fun DailyBreakdownCard(
    snapshot: ButcheryDailySalesTrendSnapshot,
    revenueColor: Color
) {
    val activeRows = remember(snapshot.days) {
        snapshot.days.filter { it.salesCount > 0 || it.revenueKsh > 0L || it.weightGrams > 0L }.reversed()
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("butchery_daily_breakdown_row_${day.axisLabel}"),
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
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = ButcheryPricing.formatKsh(day.revenueKsh),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = revenueColor
                            )
                            val dayProfit = day.profitKsh
                            if (dayProfit != null) {
                                Text(
                                    text = if (dayProfit >= 0L) {
                                        "Profit ${ButcheryPricing.formatKsh(dayProfit)}"
                                    } else {
                                        "Loss ${ButcheryPricing.formatSignedKsh(dayProfit)}"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (dayProfit >= 0L) revenueColor else Color(0xFFB91C1C)
                                )
                            }
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

private fun formatTrendDelta(percent: Double): String {
    val prefix = if (percent >= 0.0) "+" else ""
    return String.format(Locale.US, "%s%.1f%%", prefix, percent)
}
