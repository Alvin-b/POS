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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ClubOrderEntity
import com.example.data.local.entity.MenuItemEntity
import com.example.data.local.entity.OrderItemEntity
import com.example.ui.components.ActiveTicketPanel
import com.example.ui.components.SpeedRailItemCard
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NightClubDark
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VipGold
import com.example.ui.viewmodel.CartItem

@Composable
fun PosSpeedRailScreen(
    menuItems: List<MenuItemEntity>,
    selectedCategory: String,
    searchQuery: String,
    activeOrder: ClubOrderEntity?,
    activeOrderItems: List<OrderItemEntity>,
    draftCart: List<CartItem>,
    onSelectCategory: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onQuickAddItem: (MenuItemEntity) -> Unit,
    onOpenModifiers: (MenuItemEntity) -> Unit,
    onIncrementDraft: (Int) -> Unit,
    onDecrementDraft: (Int) -> Unit,
    onClearDraft: () -> Unit,
    onUpdateDbItemQty: (Long, Int) -> Unit,
    onRemoveDbItem: (Long) -> Unit,
    onHoldToTab: () -> Unit,
    onChargePayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf(
        "All",
        "⚡ Speed Rail",
        "Cocktails",
        "Shots",
        "Bottle Service",
        "Beer & Seltzers",
        "Non-Alc & Mixers",
        "Late Night Bites"
    )

    val filteredItems = menuItems.filter { item ->
        val matchesCategory = when (selectedCategory) {
            "All" -> true
            "⚡ Speed Rail" -> item.popularSpeedRail
            else -> item.category.equals(selectedCategory, ignoreCase = true)
        }
        val matchesSearch = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                item.alcoholType.contains(searchQuery, ignoreCase = true)

        matchesCategory && matchesSearch
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NightClubDark)
    ) {
        // Search & Category Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        "Search drinks, tequila, shots, bottles...",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onSearchQueryChange("") }
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("pos_search_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = SurfaceElevated,
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Category Chips Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { category ->
                    val isSelected = selectedCategory == category
                    val chipColor = when {
                        category == "⚡ Speed Rail" -> NeonAmber
                        category == "Bottle Service" -> VipGold
                        isSelected -> NeonCyan
                        else -> TextSecondary
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) chipColor.copy(alpha = 0.2f) else SurfaceCard,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) chipColor else SurfaceElevated
                        ),
                        modifier = Modifier
                            .clickable { onSelectCategory(category) }
                            .testTag("category_chip_${category.replace(" ", "_").lowercase()}")
                    ) {
                        Text(
                            text = category,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // Speed Rail Items Grid (takes remaining flexible height above ticket)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No drinks found matching \"$searchQuery\"",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 145.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        SpeedRailItemCard(
                            item = item,
                            onQuickAdd = { onQuickAddItem(item) },
                            onOpenModifiers = { onOpenModifiers(item) }
                        )
                    }
                }
            }
        }

        // Active Ticket Bottom Drawer / Bar
        ActiveTicketPanel(
            activeOrder = activeOrder,
            activeOrderItems = activeOrderItems,
            draftCart = draftCart,
            onIncrementDraft = onIncrementDraft,
            onDecrementDraft = onDecrementDraft,
            onClearDraft = onClearDraft,
            onUpdateDbItemQty = onUpdateDbItemQty,
            onRemoveDbItem = onRemoveDbItem,
            onHoldToTab = onHoldToTab,
            onChargePayClick = onChargePayClick
        )
    }
}
