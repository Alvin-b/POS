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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonRed
import com.example.ui.theme.NightClubDark
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VipGold

@Composable
fun OpenTabsScreen(
    openOrders: List<ClubOrderEntity>,
    onSelectTabForOrdering: (ClubOrderEntity) -> Unit,
    onSettleTabDirect: (ClubOrderEntity) -> Unit,
    onVoidTab: (ClubOrderEntity) -> Unit,
    onOpenNewTabModal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tabFilter by remember { mutableStateOf("ALL") } // "ALL", "VIP", "BAR"
    var tabSearch by remember { mutableStateOf("") }

    val filteredOrders = openOrders.filter { order ->
        val matchesFilter = when (tabFilter) {
            "VIP" -> order.isVipTable
            "BAR" -> !order.isVipTable
            else -> true
        }
        val matchesSearch = tabSearch.isBlank() ||
                order.tabName.contains(tabSearch, ignoreCase = true) ||
                order.tableNumber.contains(tabSearch, ignoreCase = true) ||
                (order.cardLast4?.contains(tabSearch) == true)

        matchesFilter && matchesSearch
    }

    val vipCount = openOrders.count { it.isVipTable }
    val barCount = openOrders.count { !it.isVipTable }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NightClubDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = tabSearch,
                onValueChange = { tabSearch = it },
                placeholder = { Text("Search by guest name, table #, card digits...", fontSize = 12.sp, color = TextMuted) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = NeonPurple,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("tabs_search_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = NeonPurple,
                    unfocusedBorderColor = SurfaceElevated,
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Pair("ALL", "All Open (${openOrders.size})"),
                    Pair("VIP", "VIP Tables ($vipCount)"),
                    Pair("BAR", "Main Bar ($barCount)")
                ).forEach { (code, title) ->
                    val isSelected = tabFilter == code
                    val chipColor = if (code == "VIP") VipGold else NeonPurple

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) chipColor.copy(alpha = 0.25f) else SurfaceCard,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) chipColor else SurfaceElevated
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { tabFilter = code }
                            .testTag("filter_tab_$code")
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) chipColor else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // List of Open Tabs
            if (filteredOrders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (openOrders.isEmpty()) "No active bar tabs" else "No matching tabs found",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Text(
                            text = "Tap [+ Open New Tab] below to open a guest tab",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredOrders, key = { it.id }) { order ->
                        val durationMinutes = ((System.currentTimeMillis() - order.createdAt) / 1000 / 60).coerceAtLeast(1)

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceCard,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (order.isVipTable) VipGold.copy(alpha = 0.7f) else SurfaceElevated
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tab_row_${order.id}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                // Top row: Tab Name, VIP badge, Table #, Time open
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (order.isVipTable) {
                                            Icon(
                                                imageVector = Icons.Default.Stars,
                                                contentDescription = "VIP",
                                                tint = VipGold,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = order.tabName,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }

                                    // Time Elapsed Pill
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SurfaceElevated)
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "${durationMinutes}m ago",
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Table & Card Details
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (order.tableNumber.isNotBlank()) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (order.isVipTable) VipGold.copy(alpha = 0.2f) else NeonCyan.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = order.tableNumber,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (order.isVipTable) VipGold else NeonCyan
                                                )
                                            }
                                        }

                                        if (order.cardLast4 != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.CreditCard,
                                                    contentDescription = null,
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "•••• ${order.cardLast4}",
                                                    fontSize = 11.sp,
                                                    color = TextMuted
                                                )
                                            }
                                        }
                                    }

                                    // Total Spend
                                    Text(
                                        text = "$${"%.2f".format(order.subtotal)}",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (order.isVipTable) VipGold else NeonGreen
                                    )
                                }

                                if (order.notes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Note: ${order.notes}",
                                        fontSize = 11.sp,
                                        color = NeonAmber,
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Action Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Add Drinks (Selects and opens POS)
                                    Button(
                                        onClick = { onSelectTabForOrdering(order) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NeonPurple.copy(alpha = 0.25f),
                                            contentColor = NeonPurple
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocalBar,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "ADD DRINKS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Settle / Pay Tab
                                    Button(
                                        onClick = { onSettleTabDirect(order) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NeonGreen,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Payment,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "CLOSE & PAY", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                    }

                                    // Void Button
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SurfaceElevated)
                                            .clickable { onVoidTab(order) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Void Tab",
                                            tint = NeonRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button for New Tab
        FloatingActionButton(
            onClick = onOpenNewTabModal,
            containerColor = NeonPurple,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
                .testTag("fab_new_tab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "New Tab", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "OPEN TAB", fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
