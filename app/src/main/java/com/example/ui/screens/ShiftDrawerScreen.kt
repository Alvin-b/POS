package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ClubOrderEntity
import com.example.data.local.entity.ShiftRecordEntity
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NightClubDark
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VipGold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ShiftDrawerScreen(
    activeShift: ShiftRecordEntity?,
    recentPaidOrders: List<ClubOrderEntity>,
    onSwitchBartender: (String, String) -> Unit,
    onCloseShiftAndRunZ: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bartenders = listOf(
        Pair("Elena V. (Lead)", "Main Bar Station 1"),
        Pair("Marcus K. (Bartender)", "VIP Lounge Bar"),
        Pair("Sophia L. (Speed Rail)", "Dance Floor Bar"),
        Pair("Leo T. (DJ Bar)", "Rooftop Terrace")
    )

    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.US) }
    val shiftStartTimeStr = remember(activeShift?.startTime) {
        activeShift?.startTime?.let { timeFormatter.format(Date(it)) } ?: "10:00 PM"
    }

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxSize()
            .background(NightClubDark)
    ) {
        // Active Bartender Station Profile Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonPink.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_shift_profile_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(NeonPink.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = NeonPink,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = activeShift?.bartenderName ?: "Elena V. (Lead)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = activeShift?.stationName ?: "Main Bar Station 1",
                                    fontSize = 11.sp,
                                    color = NeonCyan
                                )
                            }
                        }

                        // Shift Live Status
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "ACTIVE SHIFT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Shift Clock-In: $shiftStartTimeStr",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Terminal ID: #PULSE-01",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        // Key Shift Performance Metrics (Gross Sales, Total Tips, Drinks Poured)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "LIVE SHIFT TOTALS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Total Sales
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceCard,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AttachMoney, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                                Text(text = "GROSS SALES", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$${"%.2f".format(activeShift?.totalSales ?: 0.0)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }

                    // Total Gratuity / Tips
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceCard,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Stars, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(16.dp))
                                Text(text = "TIPS EARNED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$${"%.2f".format(activeShift?.totalTips ?: 0.0)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonPurple
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Card vs Cash breakdown
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceCard,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CreditCard, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                                Text(text = "CARD REVENUE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$${"%.2f".format(activeShift?.cardCollected ?: 0.0)}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

                    // Cash in Drawer
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceCard,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalAtm, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(16.dp))
                                Text(text = "CASH DRAWER", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$${"%.2f".format(activeShift?.cashCollected ?: 0.0)}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Drinks Poured Count
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceCard,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalBar, contentDescription = null, tint = NeonPink, modifier = Modifier.size(16.dp))
                                Text(text = "DRINKS POURED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${activeShift?.drinksPouredCount ?: 0} pours",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

                    // Checks Closed
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceCard,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Receipt, contentDescription = null, tint = VipGold, modifier = Modifier.size(16.dp))
                                Text(text = "TABS CLOSED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${activeShift?.ordersCount ?: 0} checks",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // Switch Bartender Station
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SWITCH BARTENDER / STATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                bartenders.forEach { (name, station) ->
                    val isCurrent = activeShift?.bartenderName == name
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCurrent) NeonPurple.copy(alpha = 0.2f) else SurfaceCard,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCurrent) NeonPurple else SurfaceElevated
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { onSwitchBartender(name, station) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) NeonPurple else TextPrimary
                                )
                                Text(
                                    text = station,
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                            if (isCurrent) {
                                Text(
                                    text = "LOGGED IN",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NeonPurple
                                )
                            }
                        }
                    }
                }
            }
        }

        // End of Shift Z-Report Cashout Button
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onCloseShiftAndRunZ,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonPink,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("end_shift_z_report_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Assessment,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "END SHIFT & RUN Z-REPORT",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }

        // Recent Settled Transactions List
        item {
            Text(
                text = "RECENT SETTLED TRANSACTIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (recentPaidOrders.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "No closed checks in this shift yet.", fontSize = 12.sp, color = TextMuted)
                }
            }
        } else {
            items(recentPaidOrders.take(10), key = { it.id }) { paidOrder ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = paidOrder.tabName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(
                                text = "${paidOrder.paymentMethod ?: "CARD"} • Table ${paidOrder.tableNumber}",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$${"%.2f".format(paidOrder.total)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonGreen
                            )
                            if (paidOrder.tip > 0) {
                                Text(
                                    text = "+$${"%.2f".format(paidOrder.tip)} tip",
                                    fontSize = 10.sp,
                                    color = NeonPurple
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
