package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ClubOrderEntity
import com.example.data.local.entity.OrderItemEntity
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
import com.example.ui.viewmodel.CartItem

@Composable
fun ActiveTicketPanel(
    activeOrder: ClubOrderEntity?,
    activeOrderItems: List<OrderItemEntity>,
    draftCart: List<CartItem>,
    onIncrementDraft: (Int) -> Unit,
    onDecrementDraft: (Int) -> Unit,
    onClearDraft: () -> Unit,
    onUpdateDbItemQty: (Long, Int) -> Unit,
    onRemoveDbItem: (Long) -> Unit,
    onHoldToTab: () -> Unit,
    onChargePayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSplitSheet by remember { mutableStateOf(false) }

    val hasActiveTab = activeOrder != null
    val itemCount = if (hasActiveTab) {
        activeOrderItems.sumOf { it.quantity }
    } else {
        draftCart.sumOf { it.quantity }
    }

    val subtotal = if (hasActiveTab) {
        activeOrderItems.sumOf { it.unitPrice * it.quantity }
    } else {
        draftCart.sumOf { it.totalPrice }
    }

    val tax = subtotal * 0.0825
    val estimatedTip = subtotal * 0.20 // 20% standard bar tip preview
    val grandTotal = subtotal + tax

    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceElevated),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Ticket Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Ticket",
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasActiveTab) activeOrder?.tabName ?: "Current Tab" else "CURRENT TICKET",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SurfaceElevated)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$itemCount items",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonAmber
                        )
                    }
                }

                if (itemCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Split quick button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceElevated)
                                .clickable { showSplitSheet = !showSplitSheet }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CallSplit,
                                    contentDescription = "Split",
                                    tint = NeonPurple,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "SPLIT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonPurple
                                )
                            }
                        }

                        // Clear draft
                        if (!hasActiveTab) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceElevated)
                                    .clickable(onClick = onClearDraft)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("clear_cart_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "CLEAR",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonRed
                                )
                            }
                        }
                    }
                }
            }

            // Quick Split Breakdown Preview if toggled
            AnimatedVisibility(visible = showSplitSheet && grandTotal > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceCard)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "FAST BILL SPLIT ESTIMATE (INCL. TAX)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonPurple,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "2 Guests: $${"%.2f".format(grandTotal / 2)} ea",
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "3 Guests: $${"%.2f".format(grandTotal / 3)} ea",
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "4 Guests: $${"%.2f".format(grandTotal / 4)} ea",
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Items List (Limited height scrollable container)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
            ) {
                if (itemCount == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ticket empty. Tap drinks on speed rail above to add.",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                } else if (hasActiveTab) {
                    LazyColumn {
                        itemsIndexed(activeOrderItems) { _, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.itemName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.modifiers.isNotBlank()) {
                                        Text(
                                            text = item.modifiers,
                                            fontSize = 10.sp,
                                            color = NeonAmber,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // - button
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(SurfaceElevated)
                                            .clickable {
                                                onUpdateDbItemQty(item.id, item.quantity - 1)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Decrease",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }

                                    Text(
                                        text = "${item.quantity}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )

                                    // + button
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(SurfaceElevated)
                                            .clickable {
                                                onUpdateDbItemQty(item.id, item.quantity + 1)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Increase",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Text(
                                        text = "$${"%.2f".format(item.unitPrice * item.quantity)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        color = NeonGreen
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn {
                        itemsIndexed(draftCart) { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.menuItem.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.modifiers.isNotBlank()) {
                                        Text(
                                            text = item.modifiers,
                                            fontSize = 10.sp,
                                            color = NeonAmber,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // - button
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(SurfaceElevated)
                                            .clickable { onDecrementDraft(index) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Decrease",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }

                                    Text(
                                        text = "${item.quantity}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )

                                    // + button
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(SurfaceElevated)
                                            .clickable { onIncrementDraft(index) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Increase",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Text(
                                        text = "$${"%.2f".format(item.totalPrice)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        color = NeonGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = SurfaceElevated,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Calculations breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Subtotal: $${"%.2f".format(subtotal)}   Tax (8.25%): $${"%.2f".format(tax)}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Text(
                    text = "Total: $${"%.2f".format(grandTotal)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Action Row: HOLD TO TAB vs CHARGE / PAY
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!hasActiveTab) {
                    // Hold to open tab button
                    Button(
                        onClick = onHoldToTab,
                        enabled = itemCount > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceElevated,
                            contentColor = NeonPurple
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("hold_to_tab_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Hold",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "HOLD TAB",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Charge / Settle Button
                Button(
                    onClick = onChargePayClick,
                    enabled = itemCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(if (hasActiveTab) 1f else 1.3f)
                        .height(48.dp)
                        .testTag("pay_charge_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Payment,
                        contentDescription = "Pay",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PAY $${"%.2f".format(grandTotal)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
