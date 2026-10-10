package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.example.data.local.entity.MenuItemEntity
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
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
fun ModifierDialog(
    item: MenuItemEntity,
    onDismiss: () -> Unit,
    onConfirm: (modifiers: String, extraCharge: Double, quantity: Int) -> Unit
) {
    var isDoubleShot by remember { mutableStateOf(false) }
    var selectedStyle by remember { mutableStateOf("On the Rocks") }
    val selectedAddOns = remember { mutableStateListOf<String>() }
    var customNotes by remember { mutableStateOf("") }
    var quantity by remember { mutableIntStateOf(1) }

    val doubleCharge = if (isDoubleShot) 5.00 else 0.00
    val addOnsCharge = selectedAddOns.size * 1.50
    val totalExtraCharge = doubleCharge + addOnsCharge
    val finalUnitPrice = item.price + totalExtraCharge

    val styles = listOf("On the Rocks", "Neat", "Chilled", "Tall Highball", "Straight Up")
    val addOns = listOf(
        "Add Red Bull (+1.50)",
        "Splash Cranberry",
        "Tajín Rim",
        "Extra Lime & Orange",
        "Light Ice",
        "Sparkler Presentation (+15.00)"
    )

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
                            text = "CUSTOMIZE DRINK",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonAmber,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = item.name,
                            fontSize = 18.sp,
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

                // Pour Size: Single vs Double
                Text(
                    text = "POUR SIZE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Single
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (!isDoubleShot) NeonCyan.copy(alpha = 0.2f) else SurfaceCard,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (!isDoubleShot) NeonCyan else SurfaceElevated
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isDoubleShot = false }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Standard (Single)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isDoubleShot) NeonCyan else TextPrimary
                            )
                        }
                    }

                    // Double
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDoubleShot) NeonPurple.copy(alpha = 0.25f) else SurfaceCard,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDoubleShot) NeonPurple else SurfaceElevated
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isDoubleShot = true }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "MAKE DOUBLE (+ $5.00)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDoubleShot) NeonPurple else TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Preparation Style
                Text(
                    text = "PREPARATION STYLE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    styles.forEach { style ->
                        val isSelected = selectedStyle == style
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) NeonGreen.copy(alpha = 0.2f) else SurfaceCard,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) NeonGreen else SurfaceElevated
                            ),
                            modifier = Modifier.clickable { selectedStyle = style }
                        ) {
                            Text(
                                text = style,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) NeonGreen else TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Add-ons & Mixers
                Text(
                    text = "MIXERS & ADD-ONS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    addOns.forEach { addOn ->
                        val isSelected = selectedAddOns.contains(addOn)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) NeonAmber.copy(alpha = 0.2f) else SurfaceCard,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) NeonAmber else SurfaceElevated
                            ),
                            modifier = Modifier.clickable {
                                if (isSelected) selectedAddOns.remove(addOn) else selectedAddOns.add(addOn)
                            }
                        ) {
                            Text(
                                text = addOn,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) NeonAmber else TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bartender Notes
                OutlinedTextField(
                    value = customNotes,
                    onValueChange = { customNotes = it },
                    placeholder = { Text("Special bartender notes (e.g. VIP Host comp, extra dry)", fontSize = 11.sp, color = TextMuted) },
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

                Spacer(modifier = Modifier.height(18.dp))

                // Confirm Button
                Button(
                    onClick = {
                        val modList = mutableListOf<String>()
                        if (isDoubleShot) modList.add("Double (+5.00)")
                        modList.add(selectedStyle)
                        modList.addAll(selectedAddOns)
                        if (customNotes.isNotBlank()) modList.add("Note: $customNotes")

                        onConfirm(
                            modList.joinToString(", "),
                            totalExtraCharge,
                            quantity
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("confirm_modifier_button")
                ) {
                    Text(
                        text = "ADD TO TICKET • $${"%.2f".format(finalUnitPrice * quantity)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
