package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VipGold

@Composable
fun PaymentDialog(
    subtotal: Double,
    tabName: String,
    onDismiss: () -> Unit,
    onCompletePayment: (paymentMethod: String, tenderAmount: Double, tipAmount: Double, cardLast4: String?) -> Unit
) {
    var selectedMethod by remember { mutableStateOf("CARD") } // "CARD", "CASH", "VIP_COMP"
    var selectedTipPercent by remember { mutableIntStateOf(20) } // 18, 20, 25, 0, -1 (custom)
    var customTipInput by remember { mutableStateOf("") }

    val tax = subtotal * 0.0825
    val tipAmount = when {
        selectedMethod == "VIP_COMP" -> 0.0
        selectedTipPercent == -1 -> customTipInput.toDoubleOrNull() ?: 0.0
        selectedTipPercent > 0 -> subtotal * (selectedTipPercent / 100.0)
        else -> 0.0
    }
    val totalWithTip = if (selectedMethod == "VIP_COMP") 0.0 else subtotal + tax + tipAmount

    // Cash Tender state
    var cashTenderAmount by remember { mutableDoubleStateOf(totalWithTip) }
    val changeDue = if (cashTenderAmount >= totalWithTip) cashTenderAmount - totalWithTip else 0.0

    // Card state
    var isSimulatingNfc by remember { mutableStateOf(false) }
    var nfcApproved by remember { mutableStateOf(false) }

    // Comp Reason
    var compReason by remember { mutableStateOf("VIP Host Courtesy") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceElevated),
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
                    Column {
                        Text(
                            text = "SETTLE PAYMENT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreen,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = tabName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
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

                // Payment Method Selector Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val methods = listOf(
                        Triple("CARD", "CARD / NFC", Icons.Default.CreditCard),
                        Triple("CASH", "CASH", Icons.Default.LocalAtm),
                        Triple("VIP_COMP", "VIP COMP", Icons.Default.Stars)
                    )

                    methods.forEach { (methodId, label, icon) ->
                        val isSelected = selectedMethod == methodId
                        val accentColor = when (methodId) {
                            "CARD" -> NeonCyan
                            "CASH" -> NeonGreen
                            else -> VipGold
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) accentColor.copy(alpha = 0.2f) else SurfaceCard,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) accentColor else SurfaceElevated
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedMethod = methodId }
                                .testTag("pay_method_$methodId")
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) accentColor else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) TextPrimary else TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tip Selection (if not comp)
                if (selectedMethod != "VIP_COMP") {
                    Text(
                        text = "BARTENDER GRATUITY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(18, 20, 25, 0).forEach { percent ->
                            val isSelected = selectedTipPercent == percent
                            val label = if (percent == 0) "No Tip" else "$percent%"
                            val calculatedTip = subtotal * (percent / 100.0)

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) NeonPurple.copy(alpha = 0.25f) else SurfaceCard,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) NeonPurple else SurfaceElevated
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedTipPercent = percent
                                        cashTenderAmount = subtotal + tax + calculatedTip
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) NeonPurple else TextPrimary
                                    )
                                    if (percent > 0) {
                                        Text(
                                            text = "$${"%.2f".format(calculatedTip)}",
                                            fontSize = 9.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Breakdown Totals Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Subtotal", fontSize = 12.sp, color = TextSecondary)
                            Text(text = "$${"%.2f".format(subtotal)}", fontSize = 12.sp, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Liquor & Sales Tax (8.25%)", fontSize = 12.sp, color = TextSecondary)
                            Text(text = "$${"%.2f".format(tax)}", fontSize = 12.sp, color = TextPrimary)
                        }
                        if (selectedMethod != "VIP_COMP") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Tip ($selectedTipPercent%)", fontSize = 12.sp, color = NeonPurple)
                                Text(
                                    text = "$${"%.2f".format(tipAmount)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonPurple
                                )
                            }
                        }
                        HorizontalDivider(
                            color = SurfaceElevated,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedMethod == "VIP_COMP") "COMP TOTAL" else "TOTAL DUE",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            Text(
                                text = if (selectedMethod == "VIP_COMP") "$0.00 (COMPED)" else "$${"%.2f".format(totalWithTip)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = if (selectedMethod == "VIP_COMP") VipGold else NeonGreen
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Method Specific Interface
                when (selectedMethod) {
                    "CARD" -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (nfcApproved) NeonGreen else NeonCyan.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isSimulatingNfc) {
                                        isSimulatingNfc = true
                                        // Auto complete tap after 600ms
                                        nfcApproved = true
                                        isSimulatingNfc = false
                                    }
                                }
                                .testTag("nfc_tap_simulator")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (nfcApproved) Icons.Default.CheckCircle else Icons.Default.Nfc,
                                    contentDescription = "Contactless",
                                    tint = if (nfcApproved) NeonGreen else NeonCyan,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (nfcApproved) "CARD APPROVED (VISA •••• 4242)" else "TAP / INSERT CARD",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (nfcApproved) NeonGreen else TextPrimary
                                    )
                                    Text(
                                        text = if (nfcApproved) "Pre-authorization successful" else "Tap here to simulate customer contactless payment",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }

                    "CASH" -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "QUICK CASH TENDER",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val exactVal = totalWithTip
                                val bills = listOf(
                                    Pair("Exact", exactVal),
                                    Pair("$20", 20.0),
                                    Pair("$50", 50.0),
                                    Pair("$100", 100.0)
                                )
                                bills.forEach { (label, amt) ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (cashTenderAmount == amt) NeonGreen.copy(alpha = 0.2f) else SurfaceCard,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (cashTenderAmount == amt) NeonGreen else SurfaceElevated
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { cashTenderAmount = amt }
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (cashTenderAmount == amt) NeonGreen else TextPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Change due display
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceElevated)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tendered: $${"%.2f".format(cashTenderAmount)}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "CHANGE DUE: $${"%.2f".format(changeDue)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NeonAmber
                                )
                            }
                        }
                    }

                    "VIP_COMP" -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "SUPERVISOR COMP REASON",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = VipGold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val compReasons = listOf("VIP Host Courtesy", "Birthday Celebration", "Promoter / DJ", "Spill / Re-Pour")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                compReasons.take(2).forEach { r ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (compReason == r) VipGold.copy(alpha = 0.2f) else SurfaceCard,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (compReason == r) VipGold else SurfaceElevated
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { compReason = r }
                                    ) {
                                        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                            Text(text = r, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (compReason == r) VipGold else TextPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Complete Payment Button
                Button(
                    onClick = {
                        onCompletePayment(
                            selectedMethod,
                            if (selectedMethod == "CASH") cashTenderAmount else totalWithTip,
                            tipAmount,
                            if (selectedMethod == "CARD") "4242" else null
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (selectedMethod) {
                            "CARD" -> NeonCyan
                            "CASH" -> NeonGreen
                            else -> VipGold
                        },
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("confirm_settle_button")
                ) {
                    Text(
                        text = if (selectedMethod == "VIP_COMP") "AUTHORIZE $0.00 COMP" else "COMPLETE SALE • $${"%.2f".format(totalWithTip)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
