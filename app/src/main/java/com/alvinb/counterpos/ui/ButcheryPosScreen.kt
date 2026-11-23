package com.alvinb.counterpos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvinb.counterpos.R
import com.alvinb.counterpos.data.butchery.ButcheryCategory
import com.alvinb.counterpos.data.butchery.ButcheryEntryMode
import com.alvinb.counterpos.data.butchery.ButcheryPricing
import com.alvinb.counterpos.data.local.entity.ButcheryConfigEntity
import com.alvinb.counterpos.data.local.entity.ButcheryCutEntity

internal object ButcheryPalette {
    val EmeraldHeader = Color(0xFF1F8E4E)
    val EmeraldSelected = Color(0xFF1E8D4E)
    val SlateButton = Color(0xFF2B3E50)
    val SoftSurface = Color(0xFFF3F6F4)
    val KeypadCellBg = Color(0xFFFFFFFF)
    val KeypadBorder = Color(0xFFE1E8E4)
}

internal fun butcheryDrawableRes(iconKey: String): Int = when (iconKey.lowercase()) {
    "goat" -> R.drawable.img_butchery_goat
    "chicken" -> R.drawable.img_butchery_chicken
    "matumbo" -> R.drawable.img_butchery_matumbo
    "pork" -> R.drawable.img_butchery_pork
    else -> R.drawable.img_butchery_beef
}

@Composable
fun ButcheryPosScreen(
    config: ButcheryConfigEntity?,
    cuts: List<ButcheryCutEntity>,
    canUndoLastSale: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onUndoLastSale: () -> Unit,
    onRecordSale: (
        cut: ButcheryCutEntity,
        weightGrams: Int,
        totalKsh: Int,
        paymentMethod: String,
        entryMode: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val enabledCategories = remember(config?.enabledCategoriesJson, config?.beefOnlyMode) {
        ButcheryPricing.resolveEnabledCategories(config)
    }
    val singleMeatOnly = enabledCategories.size == 1
    val singleMeatLabel = enabledCategories.firstOrNull().orEmpty()
    val butcheryTitle = config?.butcheryName?.takeIf(String::isNotBlank) ?: "Quick Sale - Butchery POS"
    val configuredPaymentMethods = remember(config?.paymentMethodsJson) {
        ButcheryPricing.parsePaymentMethods(config?.paymentMethodsJson)
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(ButcheryCategory.ALL.label) }
    var selectedCutId by rememberSaveable { mutableStateOf<String?>(null) }
    var entryMode by rememberSaveable { mutableStateOf(ButcheryEntryMode.AMOUNT_KES) }
    var keypadInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(enabledCategories) {
        selectedCategory = if (enabledCategories.size == 1) {
            enabledCategories.first()
        } else {
            ButcheryCategory.ALL.label
        }
    }

    val categories = remember(enabledCategories, cuts) {
        ButcheryPricing.visibleCategories(enabledCategories, cuts)
    }

    val filteredCuts = remember(cuts, enabledCategories, selectedCategory, searchQuery) {
        ButcheryPricing.filterCuts(
            cuts = cuts,
            enabledCategories = enabledCategories,
            selectedCategory = selectedCategory,
            searchQuery = searchQuery
        )
    }

    val selectedCut = remember(filteredCuts, cuts, enabledCategories, searchQuery, selectedCutId) {
        filteredCuts.firstOrNull { it.id == selectedCutId }
            ?: filteredCuts.firstOrNull()
            ?: if (searchQuery.isBlank()) {
                cuts.firstOrNull { cut ->
                    cut.active && enabledCategories.any { it.equals(cut.category, ignoreCase = true) }
                }
            } else {
                null
            }
    }

    LaunchedEffect(filteredCuts) {
        if (selectedCutId == null || filteredCuts.none { it.id == selectedCutId }) {
            selectedCutId = filteredCuts.firstOrNull()?.id
        }
    }

    val calculation = remember(keypadInput, entryMode, selectedCut) {
        val cutPrice = selectedCut?.pricePerKgKsh ?: 0
        when (entryMode) {
            ButcheryEntryMode.WEIGHT_KG -> {
                val weight = keypadInput.toDoubleOrNull() ?: 0.0
                ButcheryPricing.computeFromWeightKg(weight, cutPrice)
            }
            ButcheryEntryMode.AMOUNT_KES -> {
                val amount = keypadInput.toDoubleOrNull()?.toInt() ?: 0
                ButcheryPricing.computeFromAmountKsh(amount, cutPrice)
            }
        }
    }

    fun appendDigit(token: String) {
        when (token) {
            "CLR" -> keypadInput = ""
            "DEL" -> keypadInput = if (keypadInput.isNotEmpty()) keypadInput.dropLast(1) else ""
            "." -> {
                if (entryMode == ButcheryEntryMode.WEIGHT_KG && !keypadInput.contains(".")) {
                    keypadInput = if (keypadInput.isEmpty()) "0." else "$keypadInput."
                }
            }
            "+" -> {
                if (entryMode == ButcheryEntryMode.WEIGHT_KG) {
                    val next = ((keypadInput.toDoubleOrNull() ?: 0.0) + 0.5).coerceAtMost(500.0)
                    keypadInput = String.format(java.util.Locale.US, "%.2f", next)
                } else {
                    val next = ((keypadInput.toIntOrNull() ?: 0) + 50).coerceAtMost(500_000)
                    keypadInput = next.toString()
                }
            }
            else -> {
                if (keypadInput.length < 8) {
                    if (entryMode == ButcheryEntryMode.AMOUNT_KES && keypadInput == "0") {
                        keypadInput = token
                    } else {
                        keypadInput += token
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ButcheryPalette.SoftSurface)
    ) {
        // Emerald Top App Bar matching mockup
        Surface(color = ButcheryPalette.EmeraldHeader, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("butchery_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to main POS",
                        tint = Color.White
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quick Sale - Butchery POS",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (singleMeatOnly) "$butcheryTitle · $singleMeatLabel Only" else butcheryTitle,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (canUndoLastSale) {
                    IconButton(
                        onClick = onUndoLastSale,
                        modifier = Modifier.testTag("butchery_undo_sale_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo last butchery sale",
                            tint = Color.White
                        )
                    }
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("butchery_header_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Butchery Settings",
                        tint = Color.White
                    )
                }
            }
        }

        val searchAndChips: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search meat cuts...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = ButcheryPalette.EmeraldHeader,
                        unfocusedBorderColor = ButcheryPalette.KeypadBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("butchery_search_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    categories.forEach { category ->
                        val selected = if (singleMeatOnly) category == singleMeatLabel else selectedCategory == category
                        Surface(
                            onClick = { if (!singleMeatOnly) selectedCategory = category },
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) Color(0xFF1F2D27) else Color.White,
                            border = BorderStroke(1.dp, if (selected) Color(0xFF1F2D27) else ButcheryPalette.KeypadBorder),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (singleMeatOnly) "$singleMeatLabel Only" else category,
                                    color = if (selected) Color.White else Color(0xFF334155),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        val keypadAndPaymentSheet: @Composable () -> Unit = {
            // Bottom Keypad & Payment Sheet (Digital Scale Weight <-> KES Price Mode)
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Big Value Display + Mode Switcher (Weight kg <-> Price KES)
                    Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (entryMode == ButcheryEntryMode.WEIGHT_KG) {
                                "${keypadInput.ifBlank { "0" }} kg"
                            } else {
                                "KES ${keypadInput.ifBlank { "0" }}"
                            },
                            fontSize = 27.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("butchery_keypad_display")
                        )
                        Text(
                            text = if (entryMode == ButcheryEntryMode.WEIGHT_KG) {
                                "Entering weight in kg · Tap to enter KES amount"
                            } else {
                                "Key in money sold (KES) · Tap M-Pesa or Cash to save"
                            },
                            fontSize = 10.sp,
                            color = ButcheryPalette.EmeraldHeader,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Surface(
                        onClick = {
                            entryMode = if (entryMode == ButcheryEntryMode.WEIGHT_KG) {
                                keypadInput = ""
                                ButcheryEntryMode.AMOUNT_KES
                            } else {
                                keypadInput = "1.0"
                                ButcheryEntryMode.WEIGHT_KG
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (entryMode == ButcheryEntryMode.AMOUNT_KES) {
                            ButcheryPalette.EmeraldHeader.copy(alpha = 0.14f)
                        } else {
                            Color(0xFFF1F5F9)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (entryMode == ButcheryEntryMode.AMOUNT_KES) ButcheryPalette.EmeraldHeader else ButcheryPalette.KeypadBorder
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("butchery_toggle_entry_mode")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (entryMode == ButcheryEntryMode.AMOUNT_KES) Icons.Default.LocalOffer else Icons.Default.Scale,
                                contentDescription = "Switch weight or KES price mode",
                                tint = if (entryMode == ButcheryEntryMode.AMOUNT_KES) ButcheryPalette.EmeraldHeader else Color(0xFF334155),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (entryMode == ButcheryEntryMode.AMOUNT_KES) "KES → kg" else "kg → KES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (entryMode == ButcheryEntryMode.AMOUNT_KES) ButcheryPalette.EmeraldHeader else Color(0xFF334155)
                            )
                        }
                    }
                }

                // Quick Preset Chips (100, 150, 200, 300, 500 KES OR 0.25, 0.5, 1, 1.5, 2 kg)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (entryMode == ButcheryEntryMode.AMOUNT_KES) {
                        ButcheryPricing.quickAmountPresetsKsh.forEach { preset ->
                            val selectedPreset = keypadInput == preset.toString()
                            Surface(
                                onClick = { keypadInput = preset.toString() },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedPreset) ButcheryPalette.EmeraldHeader else Color(0xFFF1F5F9),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("butchery_preset_kes_$preset")
                            ) {
                                Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "KES $preset",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedPreset) Color.White else Color(0xFF1E293B)
                                    )
                                }
                            }
                        }
                    } else {
                        ButcheryPricing.quickWeightPresetsKg.forEach { preset ->
                            val selectedPreset = keypadInput == preset
                            Surface(
                                onClick = { keypadInput = preset },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedPreset) ButcheryPalette.EmeraldHeader else Color(0xFFF1F5F9),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("butchery_preset_kg_$preset")
                            ) {
                                Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$preset kg",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedPreset) Color.White else Color(0xFF1E293B)
                                    )
                                }
                            }
                        }
                    }
                }

                // Numeric Keypad Grid (4 rows x 4 cols compact & 48dp accessible)
                val rows = listOf(
                    listOf("1", "2", "3", "+"),
                    listOf("4", "5", "6", "CLR"),
                    listOf("7", "8", "9", if (entryMode == ButcheryEntryMode.WEIGHT_KG) "kg" else "KES"),
                    listOf(".", "0", "00", "DEL")
                )
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    rows.forEach { rowTokens ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowTokens.forEach { token ->
                                val isDisabledDot = token == "." && entryMode == ButcheryEntryMode.AMOUNT_KES
                                val isModeIndicator = token == "kg" || token == "KES"
                                val keyTag = when (token) {
                                    "CLR" -> "butchery_key_clr"
                                    "DEL" -> "butchery_key_del"
                                    "." -> "butchery_key_dot"
                                    "+" -> "butchery_key_plus"
                                    "kg", "KES" -> "butchery_key_mode"
                                    else -> "butchery_key_$token"
                                }
                                Surface(
                                    onClick = {
                                        when {
                                            isDisabledDot -> Unit
                                            isModeIndicator -> {
                                                entryMode = if (entryMode == ButcheryEntryMode.WEIGHT_KG) {
                                                    keypadInput = ""
                                                    ButcheryEntryMode.AMOUNT_KES
                                                } else {
                                                    keypadInput = "1.0"
                                                    ButcheryEntryMode.WEIGHT_KG
                                                }
                                            }
                                            else -> appendDigit(token)
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isModeIndicator) Color(0xFFE8F5EE) else Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag(keyTag)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (token == "DEL") {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Backspace",
                                                tint = Color(0xFF334155),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Text(
                                                text = token,
                                                fontSize = if (token == "CLR" || isModeIndicator) 12.sp else 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    isDisabledDot -> Color(0xFFCBD5E1)
                                                    isModeIndicator -> ButcheryPalette.EmeraldHeader
                                                    else -> Color(0xFF0F172A)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Live Calculation Banner
                val conversionText = if (selectedCut != null && calculation.isValid) {
                    val kgText = ButcheryPricing.formatWeightKg(calculation.weightGrams)
                    val kesText = ButcheryPricing.formatKsh(calculation.totalKsh)
                    if (entryMode == ButcheryEntryMode.WEIGHT_KG) {
                        "$kgText = $kesText"
                    } else {
                        "$kesText = $kgText (${calculation.weightGrams} g) @ KES ${selectedCut.pricePerKgKsh}/kg"
                    }
                } else {
                    "Enter weight (kg) or amount (KES)"
                }
                Text(
                    text = conversionText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .testTag("butchery_calculation_banner")
                )

                // Direct Payment Mode Buttons (NO STK PUSH — cashier picks from configured methods)
                val canRecord = selectedCut != null && calculation.isValid
                val methodRows = remember(configuredPaymentMethods) { configuredPaymentMethods.chunked(2) }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var methodOffset = 0
                    methodRows.forEach { rowMethods ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowMethods.forEach { methodLabel ->
                                val idx = methodOffset++
                                val isMpesa = ButcheryPricing.isMpesaPayment(methodLabel)
                                val isCash = ButcheryPricing.isCashPayment(methodLabel)
                                val buttonColor = when {
                                    isMpesa -> ButcheryPalette.EmeraldHeader
                                    isCash -> ButcheryPalette.SlateButton
                                    idx % 2 == 0 -> Color(0xFF0F766E)
                                    else -> Color(0xFFB45309)
                                }
                                val buttonIcon = when {
                                    isMpesa || methodLabel.contains("airtel", ignoreCase = true) ||
                                        methodLabel.contains("pochi", ignoreCase = true) ||
                                        methodLabel.contains("till", ignoreCase = true) -> Icons.Default.Smartphone
                                    methodLabel.contains("card", ignoreCase = true) -> Icons.Default.CreditCard
                                    methodLabel.contains("bank", ignoreCase = true) -> Icons.Default.AccountBalance
                                    else -> Icons.Default.Payments
                                }
                                val buttonTag = when {
                                    isMpesa -> "butchery_pay_mpesa_button"
                                    isCash -> "butchery_pay_cash_button"
                                    else -> "butchery_pay_method_$idx"
                                }
                                val storedMethod = when {
                                    isMpesa -> "mpesa"
                                    isCash -> "cash"
                                    else -> methodLabel
                                }
                                Button(
                                    onClick = {
                                        val cut = selectedCut ?: return@Button
                                        onRecordSale(
                                            cut,
                                            calculation.weightGrams,
                                            calculation.totalKsh,
                                            storedMethod,
                                            if (entryMode == ButcheryEntryMode.AMOUNT_KES) "amount" else "weight"
                                        )
                                        keypadInput = ""
                                    },
                                    enabled = canRecord,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = buttonColor,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag(buttonTag)
                                ) {
                                    Icon(buttonIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = methodLabel,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (rowMethods.size == 1 && configuredPaymentMethods.size > 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (maxHeight < 520.dp) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        searchAndChips()
                    }
                    if (filteredCuts.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matching meat cuts. Check Butchery Settings to add or enable cuts.",
                                    color = Color(0xFF64748B),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(filteredCuts, key = { it.id }) { cut ->
                            val isSelected = selectedCut?.id == cut.id
                            ButcheryCutTile(
                                cut = cut,
                                isSelected = isSelected,
                                onClick = { selectedCutId = cut.id }
                            )
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(modifier = Modifier.padding(top = 4.dp)) {
                            keypadAndPaymentSheet()
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        searchAndChips()
                        if (filteredCuts.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No matching meat cuts. Check Butchery Settings to add or enable cuts.",
                                    color = Color(0xFF64748B),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredCuts, key = { it.id }) { cut ->
                                    val isSelected = selectedCut?.id == cut.id
                                    ButcheryCutTile(
                                        cut = cut,
                                        isSelected = isSelected,
                                        onClick = { selectedCutId = cut.id }
                                    )
                                }
                            }
                        }
                    }
                    keypadAndPaymentSheet()
                }
            }
        }
    }
}

@Composable
private fun ButcheryCutTile(
    cut: ButcheryCutEntity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) ButcheryPalette.EmeraldSelected else Color.White
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) ButcheryPalette.EmeraldHeader else ButcheryPalette.KeypadBorder
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("butchery_cut_card_${cut.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = ButcheryPalette.EmeraldHeader,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color.White.copy(alpha = 0.9f) else ButcheryPalette.KeypadBorder
                    ),
                    modifier = Modifier.size(56.dp)
                ) {
                    Image(
                        painter = painterResource(id = butcheryDrawableRes(cut.iconKey)),
                        contentDescription = cut.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                Text(
                    text = cut.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "KES ${cut.pricePerKgKsh}/kg",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) Color.White.copy(alpha = 0.92f) else Color(0xFF475569),
                    maxLines = 1
                )
                if (cut.stockGrams > 0) {
                    Text(
                        text = ButcheryPricing.formatWeightKg(cut.stockGrams, decimals = 1) + " left",
                        fontSize = 9.sp,
                        color = if (isSelected) Color.White.copy(alpha = 0.85f) else ButcheryPalette.EmeraldHeader,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
