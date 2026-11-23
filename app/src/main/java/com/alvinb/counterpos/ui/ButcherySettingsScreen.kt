package com.alvinb.counterpos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
        Surface(color = ButcheryPalette.EmeraldHeader, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("butchery_settings_back_button")) {
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
                    Text(
                        text = "Configure meats, selling/buying price per kg, stock & payments",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("butchery_settings_list"),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Core Enrollment & Package Controls
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = ButcheryPalette.EmeraldHeader)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Butchery Package", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                                Text(
                                    "Manage your meat counter independently from bar & club drinks.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Switch(
                                checked = enabledDraft,
                                onCheckedChange = { enabledDraft = it },
                                enabled = ownerCanEdit,
                                modifier = Modifier.testTag("butchery_settings_enabled_switch")
                            )
                        }

                        OutlinedTextField(
                            value = nameDraft,
                            onValueChange = { nameDraft = it.take(80) },
                            label = { Text("Enrolled Butchery Name") },
                            placeholder = { Text("e.g. Prime Cuts Butchery") },
                            singleLine = true,
                            enabled = ownerCanEdit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("butchery_name_input")
                        )

                        ButcheryMeatTypesEditor(
                            selectedMeats = enabledMeatsDraft,
                            availableMeats = availableMeatChoices,
                            enabled = ownerCanEdit,
                            onMeatsChange = { nextMeats ->
                                enabledMeatsDraft = nextMeats
                                if (ownerCanEdit && nameDraft.trim().isNotEmpty()) {
                                    val price = defaultRateDraft.toIntOrNull() ?: 680
                                    val nextBeefOnly = nextMeats.size == 1 &&
                                        nextMeats.first().equals("Beef", ignoreCase = true)
                                    onSaveConfig(
                                        enabledDraft,
                                        nameDraft.trim(),
                                        nextBeefOnly,
                                        price,
                                        paymentMethodsDraft,
                                        nextMeats
                                    )
                                }
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = defaultRateDraft,
                                onValueChange = { defaultRateDraft = it.filter(Char::isDigit).take(5) },
                                label = { Text("$primaryRateMeat price / kg (KES)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                enabled = ownerCanEdit,
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
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("butchery_apply_beef_price_button")
                            ) {
                                Text("Apply to $primaryRateMeat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        ButcheryPaymentMethodsEditor(
                            methods = paymentMethodsDraft,
                            enabled = ownerCanEdit,
                            onMethodsChange = { paymentMethodsDraft = it }
                        )

                        Button(
                            onClick = {
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
                            enabled = ownerCanEdit && nameDraft.trim().isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = ButcheryPalette.EmeraldHeader),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("butchery_save_settings_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save Butchery Settings", fontWeight = FontWeight.Bold)
                        }

                        if (ownerCanEdit && onOpenButcheryEnrollment != null) {
                            OutlinedButton(
                                onClick = onOpenButcheryEnrollment,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("butchery_settings_enroll_device_button")
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Enroll Butchery Phone (Passkey / QR)", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Pricing per kg header + Add Meat
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (enabledMeatsDraft.size == 1) {
                                "${enabledMeatsDraft.first()} · Price & Cost per kg"
                            } else {
                                "Meats · Price, Buying Cost & Stock"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Optional buying price & stock (kg) enable Profit/Loss tracking in reports.",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    if (ownerCanEdit) {
                        Button(
                            onClick = { showAddCutDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ButcheryPalette.SlateButton),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("butchery_add_cut_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Meat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            items(visibleCuts, key = { it.id }) { cut ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, ButcheryPalette.KeypadBorder),
                            modifier = Modifier.size(48.dp)
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cut.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val detailParts = buildList {
                                add("Sell KES ${cut.pricePerKgKsh}/kg")
                                if (cut.buyingPricePerKgKsh > 0) add("Buy KES ${cut.buyingPricePerKgKsh}/kg")
                                if (cut.stockGrams > 0) add("${ButcheryPricing.formatWeightKg(cut.stockGrams, 1)} stock")
                            }.joinToString(" · ")
                            Text(
                                text = detailParts,
                                fontSize = 11.sp,
                                color = ButcheryPalette.EmeraldHeader,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (ownerCanEdit) {
                            IconButton(
                                onClick = {
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
                                modifier = Modifier.testTag("butchery_edit_cut_${cut.id}")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit ${cut.name}", tint = Color(0xFF334155))
                            }
                            Switch(
                                checked = cut.active,
                                onCheckedChange = { onToggleCutActive(cut.id, it) }
                            )
                        }
                    }
                }
            }
        }
    }

    val cutToEdit = editingCut
    if (cutToEdit != null) {
        val parsedSell = editPriceInput.toIntOrNull() ?: 0
        val parsedBuy = editBuyingPriceInput.toIntOrNull() ?: 0
        androidx.activity.compose.BackHandler { editingCut = null }
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Edit ${cutToEdit.name}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it.take(40) },
                        label = { Text("Meat Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("butchery_edit_cut_name_input")
                    )
                    OutlinedTextField(
                        value = editPriceInput,
                        onValueChange = { editPriceInput = it.filter(Char::isDigit).take(5) },
                        label = { Text("Selling Price per kg (KES)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("butchery_edit_cut_price_input")
                    )
                    OutlinedTextField(
                        value = editBuyingPriceInput,
                        onValueChange = { editBuyingPriceInput = it.filter(Char::isDigit).take(5) },
                        label = { Text("Buying Price per kg (KES, optional)") },
                        placeholder = { Text("Leave blank to skip Profit/Loss") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("butchery_edit_cut_buying_price_input")
                    )
                    if (parsedSell > 0 && parsedBuy > 0) {
                        val marginPerKg = parsedSell - parsedBuy
                        Text(
                            text = if (marginPerKg >= 0) {
                                "Est. profit: KES $marginPerKg/kg"
                            } else {
                                "Loss: -KES ${-marginPerKg}/kg"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (marginPerKg >= 0) ButcheryPalette.EmeraldHeader else Color(0xFFB91C1C)
                        )
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
                        modifier = Modifier.fillMaxWidth().testTag("butchery_edit_cut_stock_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { editingCut = null }) { Text("Cancel") }
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
                            colors = ButtonDefaults.buttonColors(containerColor = ButcheryPalette.EmeraldHeader),
                            modifier = Modifier.testTag("butchery_confirm_edit_cut_button")
                        ) {
                            Text("Save")
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

    androidx.activity.compose.BackHandler(onBack = onDismiss)
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Add Meat",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Meat Name") },
                    placeholder = { Text("e.g. Beef, Goat Meat, Mutton") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("butchery_new_cut_name_input")
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter(Char::isDigit).take(5) },
                    label = { Text("Selling Price per kg (KES)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("butchery_new_cut_price_input")
                )
                OutlinedTextField(
                    value = buyingPriceText,
                    onValueChange = { buyingPriceText = it.filter(Char::isDigit).take(5) },
                    label = { Text("Buying Price per kg (KES, optional)") },
                    placeholder = { Text("Leave blank to skip Profit/Loss") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("butchery_new_cut_buying_price_input")
                )
                if (parsedSell > 0 && parsedBuy > 0) {
                    val marginPerKg = parsedSell - parsedBuy
                    Text(
                        text = if (marginPerKg >= 0) {
                            "Est. profit: KES $marginPerKg/kg"
                        } else {
                            "Loss: -KES ${-marginPerKg}/kg"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (marginPerKg >= 0) ButcheryPalette.EmeraldHeader else Color(0xFFB91C1C)
                    )
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
                    modifier = Modifier.fillMaxWidth().testTag("butchery_new_cut_stock_input")
                )
                Text(
                    text = "Enter Buying Price and Stock (kg) to calculate Profit/Loss & inventory. Leave Buying Price blank to omit Profit/Loss from reports.",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val buyingPrice = buyingPriceText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                            val stockKg = stockKgText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                            onConfirm(name.trim(), parsedSell, buyingPrice, stockKg)
                        },
                        enabled = name.trim().isNotEmpty() && parsedSell > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = ButcheryPalette.EmeraldHeader),
                        modifier = Modifier.testTag("butchery_confirm_add_cut_button")
                    ) {
                        Text("Add Meat")
                    }
                }
            }
        }
    }
}
