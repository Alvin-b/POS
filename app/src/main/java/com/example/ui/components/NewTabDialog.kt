package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VipGold

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewTabDialog(
    defaultBartender: String,
    onDismiss: () -> Unit,
    onConfirm: (tabName: String, tableNumber: String, bartenderName: String, isVipTable: Boolean, cardLast4: String?, notes: String) -> Unit
) {
    var tabName by remember { mutableStateOf("") }
    var selectedTable by remember { mutableStateOf("BAR-01") }
    var cardLast4 by remember { mutableStateOf("") }
    var isVipTable by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    val tables = listOf("BAR-01", "BAR-02", "VIP-BOOTH-1", "VIP-BOOTH-2", "DANCE-04", "PATIO-07", "LOUNGE-03")

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
                            text = "START NEW BAR TAB",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonPurple,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Open Guest / VIP Tab",
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

                // Tab Name
                Text(text = "GUEST / TAB NAME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = tabName,
                    onValueChange = { tabName = it },
                    placeholder = { Text("e.g. Alex Johnson (Card #8812)", fontSize = 12.sp, color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tab_name_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SurfaceElevated,
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Table / Section quick chips
                Text(text = "BAR LOCATION / SECTION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tables.forEach { tbl ->
                        val isSelected = selectedTable == tbl
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) NeonPurple.copy(alpha = 0.25f) else SurfaceCard,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) NeonPurple else SurfaceElevated
                            ),
                            modifier = Modifier.clickable {
                                selectedTable = tbl
                                if (tbl.startsWith("VIP")) isVipTable = true
                            }
                        ) {
                            Text(
                                text = tbl,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) NeonPurple else TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Card Last 4 Pre-auth
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "CARD PRE-AUTH (OPTIONAL)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = cardLast4,
                            onValueChange = { if (it.length <= 4) cardLast4 = it },
                            placeholder = { Text("Last 4 digits", fontSize = 12.sp, color = TextMuted) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.CreditCard, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = SurfaceElevated,
                                focusedContainerColor = SurfaceCard,
                                unfocusedContainerColor = SurfaceCard
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // VIP Table toggle
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isVipTable) VipGold.copy(alpha = 0.15f) else SurfaceCard,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isVipTable) VipGold else SurfaceElevated
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = "VIP",
                                tint = if (isVipTable) VipGold else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "VIP BOOTH / BOTTLE SERVICE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isVipTable) VipGold else TextPrimary
                                )
                                Text(
                                    text = "Enables sparkler alerts & minimum spend tracker",
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Switch(
                            checked = isVipTable,
                            onCheckedChange = { isVipTable = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VipGold,
                                checkedTrackColor = VipGold.copy(alpha = 0.3f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Confirm Open Tab Button
                Button(
                    onClick = {
                        val finalName = if (tabName.isNotBlank()) tabName else "Guest Tab ($selectedTable)"
                        onConfirm(
                            finalName,
                            selectedTable,
                            defaultBartender,
                            isVipTable,
                            cardLast4.takeIf { it.isNotBlank() },
                            notes
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("submit_new_tab_button")
                ) {
                    Text(
                        text = "OPEN TAB NOW",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
