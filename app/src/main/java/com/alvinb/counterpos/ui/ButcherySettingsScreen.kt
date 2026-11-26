package com.alvinb.counterpos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvinb.counterpos.data.butchery.ButcheryPricing
import com.alvinb.counterpos.data.local.entity.ButcheryConfigEntity
import com.alvinb.counterpos.data.local.entity.ButcheryCutEntity
import java.util.Locale

private enum class ButcherySettingsTab(val label: String, val tag: String) {
    STATION("Station", "butchery_settings_tab_station"),
    MEATS("Meats & Stock", "butchery_settings_tab_meats"),
    PAYMENTS("Payments", "butchery_settings_tab_payments")
}

private val ButcheryTextPrimary = Color(0xFF0F172A)
private val ButcheryTextSecondary = Color(0xFF475569)
private val ButcheryBorderLight = Color(0xFFCBD5E1)

@Composable
private fun butcheryOutlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ButcheryTextPrimary,
    unfocusedTextColor = ButcheryTextPrimary,
    disabledTextColor = Color(0xFF64748B),
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color(0xFFF8FAF9),
    disabledContainerColor = Color(0xFFF1F5F9),
    focusedLabelColor = ButcheryPalette.EmeraldHeader,
    unfocusedLabelColor = ButcheryTextSecondary,
    disabledLabelColor = Color(0xFF64748B),
    focusedPlaceholderColor = Color(0xFF64748B),
    unfocusedPlaceholderColor = Color(0xFF64748B),
    focusedBorderColor = ButcheryPalette.EmeraldHeader,
    unfocusedBorderColor = ButcheryBorderLight,
    disabledBorderColor = Color(0xFFE2E8F0),
    cursorColor = ButcheryPalette.EmeraldHeader
)

@Composable
private fun butcherySwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = ButcheryPalette.EmeraldHeader,
    uncheckedThumbColor = Color(0xFF475569),
    uncheckedTrackColor = Color(0xFFE2E8F0),
    uncheckedBorderColor = Color(0xFF94A3B8)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ButcherySettingsScreen(
    config: ButcheryConfigEntity?,
    cuts: List<ButcheryCutEntity>,
    businessName: String,
    ownerCanEdit: Boolean,
    onBack: () -> Unit,
    onSaveConfig: (
        enabled: Boolean,
        name: String,
        beefOnly: Boolean,
        defaultBeefPricePerKgKsh: Int,
        paymentMethods: List<String>,
        enabledMeats: List<String>
    ) -> Unit,
    onApplyMeatPrice: (meatName: String, pricePerKgKsh: Int) -> Unit,
    onUpdateCutDetails: (
        cutId: String,
        name: String,
        pricePerKgKsh: Int,
        buyingPricePerKgKsh: Int,
        stockKg: Double
    ) -> Unit,
    onToggleCutActive: (cutId: String, active: Boolean) -> Unit,
    onAddCut: (name: String, pricePerKgKsh: Int, buyingPricePerKgKsh: Int, stockKg: Double) -> Unit,
    onOpenButcheryEnrollment: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ButcheryLightTheme {
        var selectedTabName by rememberSaveable { mutableStateOf(ButcherySettingsTab.STATION.name) }
        val selectedTab = ButcherySettingsTab.entries.firstOrNull { it.name == selectedTabName }
            ?: ButcherySettingsTab.STATION

        var enabledDraft by remember(config?.enabled) { mutableStateOf(config?.enabled ?: true) }
        var nameDraft by remember(config?.butcheryName, businessName) {
            mutableStateOf(
                config?.butcheryName?.takeIf(String::isNotBlank)
                    ?: if (businessName.isNotBlank()) "$businessName Butchery" else "Main Butchery"
            )
        }
        var enabledMeatsDraft by remember(config?.enabledCategoriesJson, config?.beefOnlyMode) {
            mutableStateOf(ButcheryPricing.resolveEnabledMeats(config))
        }
        var defaultRateDraft by remember(config?.defaultBeefPricePerKgKsh) {
            mutableStateOf((config?.defaultBeefPricePerKgKsh ?: 680).toString())
        }
        var paymentMethodsDraft by remember(config?.paymentMethodsJson) {
            mutableStateOf(ButcheryPricing.parsePaymentMethods(config?.paymentMethodsJson))
        }

        var editingCut by remember { mutableStateOf<ButcheryCutEntity?>(null) }
        var editNameInput by remember { mutableStateOf("") }
        var editPriceInput by remember { mutableStateOf("") }
        var editBuyingPriceInput by remember { mutableStateOf("") }
        var editStockKgInput by remember { mutableStateOf("") }
        var showAddCutDialog by remember { mutableStateOf(false) }

        val availableMeatChoices = remember(cuts, enabledMeatsDraft) {
            ButcheryPricing.availableMeatChoices(cuts, enabledMeatsDraft)
        }
        val primaryRateMeat = remember(enabledMeatsDraft) {
            if (enabledMeatsDraft.size == 1) enabledMeatsDraft.first() else "Beef"
        }
        val isBeefOnlyDraft = remember(enabledMeatsDraft) {
            enabledMeatsDraft.size == 1 && enabledMeatsDraft.first().equals("Beef", ignoreCase = true)
        }
        val visibleCuts = remember(cuts, enabledMeatsDraft) {
            cuts.filter { cut ->
                enabledMeatsDraft.any { it.equals(cut.name, ignoreCase = true) }
            }
        }

        fun persistDraft(nextEnabled: Boolean = enabledDraft, nextMeats: List<String> = enabledMeatsDraft) {
            val price = defaultRateDraft.toIntOrNull() ?: 680
            val nextBeefOnly = nextMeats.size == 1 && nextMeats.first().equals("Beef", ignoreCase = true)
            onSaveConfig(
                nextEnabled,
                nameDraft.trim(),
                nextBeefOnly,
                price,
                paymentMethodsDraft,
                nextMeats
            )
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ButcheryPalette.SoftSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(ButcheryPalette.EmeraldHeader, Color(0xFF146C3B))
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("butchery_settings_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Butchery Settings",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val subtitleParts = buildList {
                                if (businessName.isNotBlank()) add(businessName)
                                add("${enabledMeatsDraft.size} meat${if (enabledMeatsDraft.size == 1) "" else "s"}")
                            }.joinToString(" · ")
                            Text(
                                text = subtitleParts,
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.White.copy(alpha = if (enabledDraft) 0.24f else 0.16f)
                        ) {
                            Text(
                                text = if (enabledDraft) "ON" else "OFF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }

                Surface(
                    color = Color.White,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        TabRow(
                            selectedTabIndex = selectedTab.ordinal,
                            containerColor = Color.White,
                            contentColor = ButcheryPalette.EmeraldHeader,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                                    height = 3.dp,
                                    color = ButcheryPalette.EmeraldHeader
                                )
                            },
                            divider = { HorizontalDivider(color = ButcheryPalette.KeypadBorder) }
                        ) {
                            ButcherySettingsTab.entries.forEach { tab ->
                                val selected = tab == selectedTab
                                Tab(
                                    selected = selected,
                                    onClick = { selectedTabName = tab.name },
                                    selectedContentColor = ButcheryPalette.EmeraldHeader,
                                    unselectedContentColor = ButcheryTextSecondary,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag(tab.tag),
                                    text = {
                                        Text(
                                            text = tab.label,
                                            fontSize = 13.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (selected) ButcheryPalette.EmeraldHeader else ButcheryTextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    when (selectedTab) {
                        ButcherySettingsTab.STATION -> {
                            ButcheryStationTabContent(
                                enabledDraft = enabledDraft,
                                onEnabledChange = { enabledDraft = it },
                                nameDraft = nameDraft,
                                onNameChange = { nameDraft = it },
                                enabledMeatsDraft = enabledMeatsDraft,
                                availableMeatChoices = availableMeatChoices,
                                onMeatsChange = { nextMeats ->
                                    enabledMeatsDraft = nextMeats
                                    if (ownerCanEdit && nameDraft.trim().isNotEmpty()) {
                                        persistDraft(nextMeats = nextMeats)
                                    }
                                },
                                defaultRateDraft = defaultRateDraft,
                                onDefaultRateChange = { defaultRateDraft = it },
                                primaryRateMeat = primaryRateMeat,
                                isBeefOnlyDraft = isBeefOnlyDraft,
                                ownerCanEdit = ownerCanEdit,
                                onApplyMeatPrice = onApplyMeatPrice,
                                onSave = {
                                    val price = defaultRateDraft.toIntOrNull() ?: 680
                                    onSaveConfig(
                                        enabledDraft,
                                        nameDraft.trim(),
                                        isBeefOnlyDraft,
                                        price,
                                        paymentMethodsDraft,
                                        enabledMeatsDraft
                                    )
                                },
                                onOpenButcheryEnrollment = onOpenButcheryEnrollment
                            )
                        }

                        ButcherySettingsTab.MEATS -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 600.dp)
                                    .testTag("butchery_settings_list"),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (enabledMeatsDraft.size == 1) {
                                                    "${enabledMeatsDraft.first()} Pricing"
                                                } else {
                                                    "Meats (${visibleCuts.size})"
                                                },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = ButcheryTextPrimary
                                            )
                                            Text(
                                                text = "Tap edit for price, buying cost & stock",
                                                fontSize = 12.sp,
                                                color = ButcheryTextSecondary
                                            )
                                        }
                                        if (ownerCanEdit) {
                                            Button(
                                                onClick = { showAddCutDialog = true },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = ButcheryPalette.SlateButton,
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("butchery_add_cut_button")
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    "Add Meat",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }

                                if (visibleCuts.isEmpty()) {
                                    item {
                                        Card(
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "No meats in this selection",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = ButcheryTextPrimary
                                                )
                                                Text(
                                                    text = "Add a meat above or turn meats on in the Station tab.",
                                                    fontSize = 12.sp,
                                                    color = ButcheryTextSecondary
                                                )
                                            }
                                        }
                                    }
                                }

                                items(visibleCuts, key = { it.id }) { cut ->
                                    ButcheryCutSettingsRow(
                                        cut = cut,
                                        ownerCanEdit = ownerCanEdit,
                                        onEditClick = {
                                            editingCut = cut
                                            editNameInput = cut.name
                                            editPriceInput = cut.pricePerKgKsh.toString()
                                            editBuyingPriceInput = if (cut.buyingPricePerKgKsh > 0) {
                                                cut.buyingPricePerKgKsh.toString()
                                            } else {
                                                ""
                                            }
                                            editStockKgInput = if (cut.stockGrams > 0) {
                                                String.format(Locale.US, "%.1f", cut.stockGrams / 1000.0)
                                            } else {
                                                ""
                                            }
                                        },
                                        onToggleActive = { onToggleCutActive(cut.id, it) }
                                    )
                                }

                                item {
                                    Spacer(
                                        modifier = Modifier
                                            .height(16.dp)
                                            .navigationBarsPadding()
                                    )
                                }
                            }
                        }

                        ButcherySettingsTab.PAYMENTS -> {
                            ButcheryPaymentsTabContent(
                                paymentMethodsDraft = paymentMethodsDraft,
                                onMethodsChange = { paymentMethodsDraft = it },
                                ownerCanEdit = ownerCanEdit,
                                canSave = ownerCanEdit && nameDraft.trim().isNotEmpty(),
                                onSave = {
                                    val price = defaultRateDraft.toIntOrNull() ?: 680
                                    onSaveConfig(
                                        enabledDraft,
                                        nameDraft.trim(),
                                        isBeefOnlyDraft,
                                        price,
                                        paymentMethodsDraft,
                                        enabledMeatsDraft
                                    )
                                }
                            )
                        }
                    }
                }
            }

            val cutToEdit = editingCut
            if (cutToEdit != null) {
                val parsedSell = editPriceInput.toIntOrNull() ?: 0
                val parsedBuy = editBuyingPriceInput.toIntOrNull() ?: 0
                BackHandler { editingCut = null }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .imePadding()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 440.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ButcherySectionBadge(icon = Icons.Default.Edit)
                                Text(
                                    text = "Edit ${cutToEdit.name}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ButcheryTextPrimary
                                )
                            }
                            OutlinedTextField(
                                value = editNameInput,
                                onValueChange = { editNameInput = it.take(40) },
                                label = { Text("Meat Name") },
                                singleLine = true,
                                colors = butcheryOutlinedFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("butchery_edit_cut_name_input")
                            )
                            OutlinedTextField(
                                value = editPriceInput,
                                onValueChange = { editPriceInput = it.filter(Char::isDigit).take(5) },
                                label = { Text("Selling Price per kg (KES)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = butcheryOutlinedFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("butchery_edit_cut_price_input")
                            )
                            OutlinedTextField(
                                value = editBuyingPriceInput,
                                onValueChange = { editBuyingPriceInput = it.filter(Char::isDigit).take(5) },
                                label = { Text("Buying Price per kg (KES, optional)") },
                                placeholder = { Text("Leave blank to omit profit") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = butcheryOutlinedFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("butchery_edit_cut_buying_price_input")
                            )
                            if (parsedSell > 0 && parsedBuy > 0) {
                                MarginPreviewPill(marginPerKg = parsedSell - parsedBuy)
                            }
                            OutlinedTextField(
                                value = editStockKgInput,
                                onValueChange = { raw ->
                                    val clean = raw.filter { it.isDigit() || it == '.' }
                                    if (clean.count { it == '.' } <= 1) editStockKgInput = clean.take(7)
                                },
                                label = { Text("Stock in kg (optional)") },
                                placeholder = { Text("e.g. 45.0") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                colors = butcheryOutlinedFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("butchery_edit_cut_stock_input")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { editingCut = null },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF334155)),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.width(8.dp))
                                val parsedPrice = editPriceInput.toIntOrNull() ?: 0
                                val cleanName = editNameInput.trim()
                                Button(
                                    onClick = {
                                        val buyingPrice = editBuyingPriceInput.toIntOrNull()?.coerceAtLeast(0) ?: 0
                                        val stockKg = editStockKgInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                                        onUpdateCutDetails(cutToEdit.id, cleanName, parsedPrice, buyingPrice, stockKg)
                                        editingCut = null
                                    },
                                    enabled = cleanName.isNotEmpty() && parsedPrice > 0,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ButcheryPalette.EmeraldHeader,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("butchery_confirm_edit_cut_button")
                                ) {
                                    Text("Save", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            if (showAddCutDialog) {
                AddButcheryCutModal(
                    defaultPrice = defaultRateDraft.toIntOrNull() ?: 680,
                    onDismiss = { showAddCutDialog = false },
                    onConfirm = { name, price, buyingPrice, stockKg ->
                        onAddCut(name, price, buyingPrice, stockKg)
                        if (enabledMeatsDraft.none { it.equals(name, ignoreCase = true) }) {
                            enabledMeatsDraft = enabledMeatsDraft + name
                        }
                        showAddCutDialog = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ButcheryStationTabContent(
    enabledDraft: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    nameDraft: String,
    onNameChange: (String) -> Unit,
    enabledMeatsDraft: List<String>,
    availableMeatChoices: List<String>,
    onMeatsChange: (List<String>) -> Unit,
    defaultRateDraft: String,
    onDefaultRateChange: (String) -> Unit,
    primaryRateMeat: String,
    isBeefOnlyDraft: Boolean,
    ownerCanEdit: Boolean,
    onApplyMeatPrice: (meatName: String, pricePerKgKsh: Int) -> Unit,
    onSave: () -> Unit,
    onOpenButcheryEnrollment: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Station Identity Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(
                1.dp,
                if (enabledDraft) ButcheryPalette.EmeraldHeader.copy(alpha = 0.45f) else ButcheryPalette.KeypadBorder
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = if (enabledDraft) {
                        ButcheryPalette.EmeraldHeader.copy(alpha = 0.08f)
                    } else {
                        Color(0xFFF8FAFC)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ButcherySectionBadge(icon = Icons.Default.Storefront)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Butchery Module",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = ButcheryTextPrimary
                            )
                            Text(
                                text = if (enabledDraft) "Active counter" else "Paused",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (enabledDraft) ButcheryPalette.EmeraldHeader else ButcheryTextSecondary
                            )
                        }
                        Switch(
                            checked = enabledDraft,
                            onCheckedChange = onEnabledChange,
                            enabled = ownerCanEdit,
                            colors = butcherySwitchColors(),
                            modifier = Modifier.testTag("butchery_settings_enabled_switch")
                        )
                    }
                }
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { onNameChange(it.take(80)) },
                        label = { Text("Butchery name") },
                        placeholder = { Text("e.g. Prime Cuts Butchery") },
                        singleLine = true,
                        enabled = ownerCanEdit,
                        colors = butcheryOutlinedFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("butchery_name_input")
                    )
                }
            }
        }

        // 2. Meats & Default Pricing Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ButcherySectionBadge(icon = Icons.Default.Scale)
                    Text(
                        text = "Meats & Default Rate",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ButcheryTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = ButcheryPalette.EmeraldHeader.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (isBeefOnlyDraft) "Beef only" else "${enabledMeatsDraft.size} active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ButcheryPalette.EmeraldHeader,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAF9),
                    border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ButcheryMeatTypesEditor(
                        selectedMeats = enabledMeatsDraft,
                        availableMeats = availableMeatChoices,
                        enabled = ownerCanEdit,
                        onMeatsChange = onMeatsChange,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = defaultRateDraft,
                        onValueChange = { onDefaultRateChange(it.filter(Char::isDigit).take(5)) },
                        label = { Text("$primaryRateMeat price / kg (KES)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = ownerCanEdit,
                        colors = butcheryOutlinedFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("butchery_default_beef_price_input")
                    )
                    OutlinedButton(
                        onClick = {
                            val price = defaultRateDraft.toIntOrNull() ?: 680
                            onApplyMeatPrice(primaryRateMeat, price)
                        },
                        enabled = ownerCanEdit && (defaultRateDraft.toIntOrNull() ?: 0) > 0,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ButcheryPalette.EmeraldHeader,
                            disabledContentColor = Color(0xFF94A3B8)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (ownerCanEdit && (defaultRateDraft.toIntOrNull() ?: 0) > 0) {
                                ButcheryPalette.EmeraldHeader
                            } else {
                                Color(0xFFCBD5E1)
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("butchery_apply_beef_price_button")
                    ) {
                        Text(
                            text = "Apply",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 3. Actions Card
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSave,
                enabled = ownerCanEdit && nameDraft.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButcheryPalette.EmeraldHeader,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("butchery_save_settings_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save", fontWeight = FontWeight.Bold, color = Color.White)
            }

            if (ownerCanEdit && onOpenButcheryEnrollment != null) {
                OutlinedButton(
                    onClick = onOpenButcheryEnrollment,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ButcheryTextPrimary),
                    border = BorderStroke(1.dp, ButcheryBorderLight),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("butchery_settings_enroll_device_button")
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = ButcheryPalette.EmeraldHeader,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Enroll Phone", fontWeight = FontWeight.SemiBold, maxLines = 1, color = ButcheryTextPrimary)
                }
            }
        }
    }
}

@Composable
private fun ButcheryPaymentsTabContent(
    paymentMethodsDraft: List<String>,
    onMethodsChange: (List<String>) -> Unit,
    ownerCanEdit: Boolean,
    canSave: Boolean,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ButcherySectionBadge(icon = Icons.Default.Payments)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Checkout Payment Methods",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ButcheryTextPrimary
                        )
                        Text(
                            text = "Shown as payment buttons on Quick Sale",
                            fontSize = 12.sp,
                            color = ButcheryTextSecondary
                        )
                    }
                }

                HorizontalDivider(color = ButcheryPalette.KeypadBorder)

                ButcheryPaymentMethodsEditor(
                    methods = paymentMethodsDraft,
                    enabled = ownerCanEdit,
                    onMethodsChange = onMethodsChange
                )
            }
        }

        Button(
            onClick = onSave,
            enabled = canSave,
            colors = ButtonDefaults.buttonColors(
                containerColor = ButcheryPalette.EmeraldHeader,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("butchery_save_settings_button")
        ) {
            Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Save Payment Methods", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButcheryCutSettingsRow(
    cut: ButcheryCutEntity,
    ownerCanEdit: Boolean,
    onEditClick: () -> Unit,
    onToggleActive: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(
            1.dp,
            if (cut.active) ButcheryPalette.KeypadBorder else Color(0xFFE2E8F0)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
                modifier = Modifier.size(52.dp)
            ) {
                Image(
                    painter = painterResource(id = butcheryDrawableRes(cut.iconKey)),
                    contentDescription = cut.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = cut.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (cut.active) ButcheryTextPrimary else ButcheryTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!cut.active) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = "Hidden",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ButcheryTextSecondary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MeatMetricChip(
                        text = "Sell KES ${cut.pricePerKgKsh}/kg",
                        containerColor = ButcheryPalette.EmeraldHeader.copy(alpha = 0.12f),
                        textColor = Color(0xFF07553F)
                    )
                    if (cut.buyingPricePerKgKsh > 0) {
                        MeatMetricChip(
                            text = "Buy KES ${cut.buyingPricePerKgKsh}/kg",
                            containerColor = Color(0xFFF1F5F9),
                            textColor = Color(0xFF1E293B)
                        )
                        val margin = cut.pricePerKgKsh - cut.buyingPricePerKgKsh
                        MeatMetricChip(
                            text = if (margin >= 0) "+$margin/kg" else "-${-margin}/kg",
                            containerColor = if (margin >= 0) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                            textColor = if (margin >= 0) Color(0xFF166534) else Color(0xFFB91C1C)
                        )
                    }
                    if (cut.stockGrams > 0) {
                        MeatMetricChip(
                            text = ButcheryPricing.formatWeightKg(cut.stockGrams, 1),
                            containerColor = Color(0xFFFEF3C7),
                            textColor = Color(0xFF92400E)
                        )
                    }
                }
            }
            if (ownerCanEdit) {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("butchery_edit_cut_${cut.id}")
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit ${cut.name}",
                        tint = Color(0xFF1E293B)
                    )
                }
                Switch(
                    checked = cut.active,
                    onCheckedChange = onToggleActive,
                    colors = butcherySwitchColors()
                )
            }
        }
    }
}

@Composable
private fun ButcherySectionBadge(
    icon: ImageVector,
    containerColor: Color = ButcheryPalette.EmeraldHeader.copy(alpha = 0.14f),
    iconTint: Color = ButcheryPalette.EmeraldHeader
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MeatMetricChip(
    text: String,
    containerColor: Color,
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun MarginPreviewPill(marginPerKg: Int) {
    val isProfit = marginPerKg >= 0
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isProfit) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (isProfit) {
                "Est. profit: KES $marginPerKg/kg"
            } else {
                "Loss: -KES ${-marginPerKg}/kg"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isProfit) Color(0xFF166534) else Color(0xFFB91C1C),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun AddButcheryCutModal(
    defaultPrice: Int,
    onDismiss: () -> Unit,
    onConfirm: (name: String, pricePerKgKsh: Int, buyingPricePerKgKsh: Int, stockKg: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf(defaultPrice.toString()) }
    var buyingPriceText by remember { mutableStateOf("") }
    var stockKgText by remember { mutableStateOf("") }

    val parsedSell = priceText.toIntOrNull() ?: 0
    val parsedBuy = buyingPriceText.toIntOrNull() ?: 0

    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .imePadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ButcherySectionBadge(icon = Icons.Default.Add)
                    Text(
                        text = "Add Meat",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ButcheryTextPrimary
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Meat Name") },
                    placeholder = { Text("e.g. Beef, Goat Meat, Mutton") },
                    singleLine = true,
                    colors = butcheryOutlinedFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("butchery_new_cut_name_input")
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter(Char::isDigit).take(5) },
                    label = { Text("Selling Price per kg (KES)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = butcheryOutlinedFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("butchery_new_cut_price_input")
                )
                OutlinedTextField(
                    value = buyingPriceText,
                    onValueChange = { buyingPriceText = it.filter(Char::isDigit).take(5) },
                    label = { Text("Buying Price per kg (KES, optional)") },
                    placeholder = { Text("Leave blank to omit profit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = butcheryOutlinedFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("butchery_new_cut_buying_price_input")
                )
                if (parsedSell > 0 && parsedBuy > 0) {
                    MarginPreviewPill(marginPerKg = parsedSell - parsedBuy)
                }
                OutlinedTextField(
                    value = stockKgText,
                    onValueChange = { raw ->
                        val clean = raw.filter { it.isDigit() || it == '.' }
                        if (clean.count { it == '.' } <= 1) stockKgText = clean.take(7)
                    },
                    label = { Text("Stock in kg (optional)") },
                    placeholder = { Text("e.g. 25.0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = butcheryOutlinedFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("butchery_new_cut_stock_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF334155)),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val buyingPrice = buyingPriceText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                            val stockKg = stockKgText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                            onConfirm(name.trim(), parsedSell, buyingPrice, stockKg)
                        },
                        enabled = name.trim().isNotEmpty() && parsedSell > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ButcheryPalette.EmeraldHeader,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("butchery_confirm_add_cut_button")
                    ) {
                        Text("Add Meat", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
