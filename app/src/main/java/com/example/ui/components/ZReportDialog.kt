package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.ShiftRecordEntity
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
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
fun ZReportDialog(
    shift: ShiftRecordEntity?,
    onDismiss: () -> Unit
) {
    if (shift == null) return
    var hasPrinted by remember { mutableStateOf(false) }

    val formatter = remember { SimpleDateFormat("h:mm a • MMM d", Locale.US) }
    val startTimeStr = remember(shift.startTime) { formatter.format(Date(shift.startTime)) }
    val endTimeStr = remember(shift.endTime) { formatter.format(Date(shift.endTime ?: System.currentTimeMillis())) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF161922),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPink.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonPink.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = null,
                                tint = NeonPink,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "END OF SHIFT Z-REPORT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonPink,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = shift.stationName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Shift Time & Server
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0F1218),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Bartender / Lead:", fontSize = 11.sp, color = TextSecondary)
                            Text(text = shift.bartenderName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Shift Duration:", fontSize = 11.sp, color = TextSecondary)
                            Text(text = "$startTimeStr → $endTimeStr", fontSize = 10.sp, color = NeonCyan)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Financial Breakdown
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0F1218),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Gross Sales Volume", fontSize = 12.sp, color = TextSecondary)
                            Text(text = "$${"%.2f".format(shift.totalSales)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Card Transactions", fontSize = 11.sp, color = TextMuted)
                            Text(text = "$${"%.2f".format(shift.cardCollected)}", fontSize = 11.sp, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Cash Drawer Collected", fontSize = 11.sp, color = TextMuted)
                            Text(text = "$${"%.2f".format(shift.cashCollected)}", fontSize = 11.sp, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "VIP Comps & Spills", fontSize = 11.sp, color = TextMuted)
                            Text(text = "$${"%.2f".format(shift.compAmount)}", fontSize = 11.sp, color = VipGold)
                        }

                        HorizontalDivider(color = Color(0xFF282F3D), modifier = Modifier.padding(vertical = 6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "TOTAL GRATUITY / TIPS", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonGreen)
                            Text(text = "$${"%.2f".format(shift.totalTips)}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = NeonGreen)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Orders Succeeded", fontSize = 11.sp, color = TextSecondary)
                            Text(text = "${shift.ordersCount} checks", fontSize = 11.sp, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Total Drinks & Bottles Poured", fontSize = 11.sp, color = TextSecondary)
                            Text(text = "${shift.drinksPouredCount} pours", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonAmber)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { hasPrinted = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = null, tint = if (hasPrinted) NeonGreen else NeonCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (hasPrinted) "PRINTED" else "PRINT Z", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (hasPrinted) NeonGreen else NeonCyan)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonPink,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("close_z_report_button")
                    ) {
                        Text(text = "CLOSE & ARCHIVE", fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
