package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NightClubDark
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.PaymentDetails
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReceiptDialog(
    receipt: PaymentDetails,
    onDismiss: () -> Unit
) {
    var hasSimulatedPrint by remember { mutableStateOf(false) }
    val timeFormatter = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US) }
    val formattedTime = remember { timeFormatter.format(Date()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E222B),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Success Badge
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(NeonGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Paid",
                        tint = NeonGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "TRANSACTION APPROVED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonGreen,
                    letterSpacing = 1.5.sp
                )

                Text(
                    text = "PULSE NIGHTCLUB",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = Color.White
                )

                Text(
                    text = "1042 Neon Blvd, VIP Bar • Terminal #01",
                    fontSize = 10.sp,
                    color = TextMuted
                )
                Text(
                    text = formattedTime,
                    fontSize = 10.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Thermal receipt paper styling container
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF11141A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "TAB: ${receipt.tabName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = "#${receipt.orderId}", fontSize = 11.sp, color = TextMuted)
                        }

                        HorizontalDivider(
                            color = Color(0xFF282F3D),
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        // Itemized list
                        receipt.items.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.quantity}x ${item.itemName}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                    if (item.modifiers.isNotBlank()) {
                                        Text(
                                            text = item.modifiers,
                                            fontSize = 9.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                                Text(
                                    text = "$${"%.2f".format(item.unitPrice * item.quantity)}",
                                    fontSize = 11.sp,
                                    color = TextPrimary
                                )
                            }
                        }

                        HorizontalDivider(
                            color = Color(0xFF282F3D),
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Subtotal", fontSize = 11.sp, color = TextSecondary)
                            Text(text = "$${"%.2f".format(receipt.subtotal)}", fontSize = 11.sp, color = TextPrimary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Tax (8.25%)", fontSize = 11.sp, color = TextSecondary)
                            Text(text = "$${"%.2f".format(receipt.tax)}", fontSize = 11.sp, color = TextPrimary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Tip", fontSize = 11.sp, color = NeonPurple)
                            Text(text = "$${"%.2f".format(receipt.tipAmount)}", fontSize = 11.sp, color = NeonPurple)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "TOTAL PAID", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
                            Text(
                                text = "$${"%.2f".format(receipt.total)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonGreen
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = when (receipt.paymentMethod) {
                                "CARD" -> "Method: VISA CONTACTLESS •••• ${receipt.cardLast4 ?: "4242"}"
                                "CASH" -> "Method: CASH (Tendered: $${"%.2f".format(receipt.tenderAmount)} | Change: $${"%.2f".format(receipt.changeGiven)})"
                                else -> "Method: VIP COMP COURTESY"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons: Print thermal receipt & Done
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { hasSimulatedPrint = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print",
                            tint = if (hasSimulatedPrint) NeonGreen else NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasSimulatedPrint) "PRINTED" else "PRINT",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasSimulatedPrint) NeonGreen else NeonCyan
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("receipt_done_button")
                    ) {
                        Text(
                            text = "NEW SALE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}
