package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NightClubDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.PosTab

data class NavItem(
    val tab: PosTab,
    val title: String,
    val icon: ImageVector,
    val accentColor: Color,
    val badgeCount: Int = 0
)

@Composable
fun ClubBottomNavigation(
    currentTab: PosTab,
    openTabsCount: Int,
    onTabSelected: (PosTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavItem(
            tab = PosTab.SPEED_RAIL,
            title = "SPEED RAIL",
            icon = Icons.Default.FlashOn,
            accentColor = NeonCyan
        ),
        NavItem(
            tab = PosTab.OPEN_TABS,
            title = "OPEN TABS",
            icon = Icons.Default.ReceiptLong,
            accentColor = NeonPurple,
            badgeCount = openTabsCount
        ),
        NavItem(
            tab = PosTab.INVENTORY,
            title = "BAR STOCK",
            icon = Icons.Default.Inventory2,
            accentColor = NeonAmber
        ),
        NavItem(
            tab = PosTab.SHIFT_DRAWER,
            title = "DRAWER & Z",
            icon = Icons.Default.Assessment,
            accentColor = NeonPink
        )
    )

    Surface(
        color = SurfaceDark,
        tonalElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = currentTab == item.tab
                val itemColor = if (isSelected) item.accentColor else TextMuted

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTabSelected(item.tab) }
                        .padding(vertical = 8.dp)
                        .testTag("nav_tab_${item.tab.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BadgedBox(
                            badge = {
                                if (item.badgeCount > 0) {
                                    Badge(
                                        containerColor = NeonPurple,
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = item.badgeCount.toString(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = itemColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = item.title,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                            color = itemColor,
                            letterSpacing = 0.5.sp
                        )

                        if (isSelected) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .size(width = 18.dp, height = 3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(item.accentColor)
                            )
                        }
                    }
                }
            }
        }
    }
}
