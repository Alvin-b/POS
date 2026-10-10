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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Liquor
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.data.local.entity.MenuItemEntity
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
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
fun InventoryScreen(
    menuItems: List<MenuItemEntity>,
    onRestockItem: (Long, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }

    val totalUnits = menuItems.sumOf { it.stockQuantity }
    val lowStockCount = menuItems.count { it.stockQuantity < 10 }
    val vipBottleValue = menuItems
        .filter { it.isVipExclusive }
        .sumOf { it.price * it.stockQuantity }

    val categories = listOf("ALL", "Cocktails", "Shots", "Bottle Service", "Beer & Seltzers", "Non-Alc & Mixers")

    val filteredList = menuItems.filter { item ->
        if (selectedCategoryFilter == "ALL") true else item.category.equals(selectedCategoryFilter, ignoreCase = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NightClubDark)
            .padding(horizontal = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Top Metrics Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Low Stock Alert Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (lowStockCount > 0) NeonRed.copy(alpha = 0.15f) else SurfaceCard,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (lowStockCount > 0) NeonRed.copy(alpha = 0.5f) else SurfaceElevated
                ),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (lowStockCount > 0) NeonRed else NeonGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "LOW STOCK", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (lowStockCount > 0) NeonRed else NeonGreen)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$lowStockCount items",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                }
            }

            // Total Bar Units
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceCard,
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(text = "BAR INVENTORY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$totalUnits units",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                }
            }

            // VIP Bottle Value
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceCard,
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(text = "VIP STOCK VALUE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = VipGold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$${"%.0f".format(vipBottleValue)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = VipGold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategoryFilter == cat
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) NeonAmber.copy(alpha = 0.2f) else SurfaceCard,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) NeonAmber else SurfaceElevated
                    ),
                    modifier = Modifier.clickable { selectedCategoryFilter = cat }
                ) {
                    Text(
                        text = cat,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) NeonAmber else TextSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Inventory Items List
        LazyColumn(
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredList, key = { it.id }) { item ->
                val isLow = item.stockQuantity < 10
                val progress = (item.stockQuantity / 100f).coerceIn(0f, 1f)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceCard,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isLow) NeonRed.copy(alpha = 0.5f) else SurfaceElevated
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inventory_item_${item.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    if (item.isVipExclusive) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(VipGold.copy(alpha = 0.2f))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(text = "VIP", fontSize = 8.sp, fontWeight = FontWeight.Black, color = VipGold)
                                        }
                                    }
                                }
                                Text(
                                    text = "${item.category} • ${item.volumeOrSize.ifBlank { "Standard Pour" }}",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }

                            // Current Stock Badge
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${item.stockQuantity} in bar",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isLow) NeonRed else NeonGreen
                                )
                                Text(
                                    text = "$${"%.2f".format(item.price)} ea",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Stock Bar
                        LinearProgressIndicator(
                            progress = { progress },
                            color = if (isLow) NeonRed else if (item.stockQuantity < 30) NeonAmber else NeonGreen,
                            trackColor = SurfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Restock Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RESTOCK:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            // +6 button
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceElevated,
                                modifier = Modifier
                                    .clickable { onRestockItem(item.id, 6) }
                                    .testTag("restock_6_${item.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(12.dp))
                                    Text(text = "6", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // +12 Case button
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = NeonCyan.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .clickable { onRestockItem(item.id, 12) }
                                    .testTag("restock_12_${item.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    Text(text = "12 (CASE)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
