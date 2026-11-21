package com.alvinb.counterpos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import com.alvinb.counterpos.data.butchery.ButcheryCategory
import com.alvinb.counterpos.data.butchery.ButcheryPricing
import com.alvinb.counterpos.data.local.entity.ButcheryConfigEntity
import com.alvinb.counterpos.data.local.entity.ButcheryCutEntity

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
        enabledCategories: List<String>
    ) -> Unit,
    onApplyCategoryPrice: (category: String, pricePerKgKsh: Int) -> Unit,
    onUpdateCutPrice: (cutId: String, pricePerKgKsh: Int) -> Unit,
    onUpdateCutStockKg: (cutId: String, stockKg: Double) -> Unit,
    onToggleCutActive: (cutId: String, active: Boolean) -> Unit,
    onAddCut: (name: String, category: String, pricePerKgKsh: Int, stockKg: Double, iconKey: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var enabledDraft by remember(config?.enabled) { mutableStateOf(config?.enabled ?: true) }
    var nameDraft by remember(config?.butcheryName, businessName) {
        mutableStateOf(
            config?.butcheryName?.takeIf(String::isNotBlank)
                ?: if (businessName.isNotBlank()) "$businessName Butchery" else "Main Butchery"
        )
    }
    var enabledCategoriesDraft by remember(config?.enabledCategoriesJson, config?.beefOnlyMode) {
        mutableStateOf(ButcheryPricing.resolveEnabledCategories(config))
    }
    var defaultRateDraft by remember(config?.defaultBeefPricePerKgKsh) {
        mutableStateOf((config?.defaultBeefPricePerKgKsh ?: 680).toString())
    }
    var paymentMethodsDraft by remember(config?.paymentMethodsJson) {
        mutableStateOf(ButcheryPricing.parsePaymentMethods(config?.paymentMethodsJson))
    }

    var editingCut by remember { mutableStateOf<ButcheryCutEntity?>(null) }
    var editPriceInput by remember { mutableStateOf("") }
    var editStockKgInput by remember { mutableStateOf("") }
    var showAddCutDialog by remember { mutableStateOf(false) }

    val primaryRateCategory = remember(enabledCategoriesDraft) {
        if (enabledCategoriesDraft.size == 1) enabledCategoriesDraft.first() else ButcheryCategory.BEEF.label
    }
    val isBeefOnlyDraft = remember(enabledCategoriesDraft) {
        enabledCategoriesDraft.size == 1 &&
            enabledCategoriesDraft.first().equals(ButcheryCategory.BEEF.label, ignoreCase = true)
    }

    val visibleCuts = remember(cuts, enabledCategoriesDraft) {
        cuts.filter { cut ->
            enabledCategoriesDraft.any { it.equals(cut.category, ignoreCase = true) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ButcheryPalette.SoftSurface)
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
                        text = "Configure cuts, price per kg & payment methods",
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
                            selectedCategories = enabledCategoriesDraft,
                            enabled = ownerCanEdit,
                            onCategoriesChange = { enabledCategoriesDraft = it }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = defaultRateDraft,
                                onValueChange = { defaultRateDraft = it.filter(Char::isDigit).take(5) },
                                label = { Text("$primaryRateCategory price / kg (KES)") },
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
                                    onApplyCategoryPrice(primaryRateCategory, price)
                                },
                                enabled = ownerCanEdit && (defaultRateDraft.toIntOrNull() ?: 0) > 0,
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("butchery_apply_beef_price_button")
                            ) {
                                Text("Apply to $primaryRateCategory", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                    enabledCategoriesDraft
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
                    }
                }
            }

            // Pricing per kg header + Add Cut
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (enabledCategoriesDraft.size == 1) {
                                "${enabledCategoriesDraft.first()} Cuts & Price per kg"
                            } else {
                                "Meat Cuts & Price per kg"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Used to calculate exact kg when customers buy KES 100 / 150 / 200 worth of meat.",
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
                            Text("Add Cut", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                            Text(
                                text = "${cut.category} · KES ${cut.pricePerKgKsh}/kg" +
                                    if (cut.stockGrams > 0) " · ${ButcheryPricing.formatWeightKg(cut.stockGrams, 1)} in stock" else "",
                                fontSize = 12.sp,
                                color = ButcheryPalette.EmeraldHeader,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (ownerCanEdit) {
                            IconButton(
                                onClick = {
                                    editingCut = cut
                                    editPriceInput = cut.pricePerKgKsh.toString()
                                    editStockKgInput = if (cut.stockGrams > 0) {
                                        String.format(java.util.Locale.US, "%.1f", cut.stockGrams / 1000.0)
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
        AlertDialog(
            onDismissRequest = { editingCut = null },
            title = { Text("Edit ${cutToEdit.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editPriceInput,
                        onValueChange = { editPriceInput = it.filter(Char::isDigit).take(5) },
                        label = { Text("Selling Price per kg (KES)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("butchery_edit_cut_price_input")
                    )
                    OutlinedTextField(
                        value = editStockKgInput,
                        onValueChange = { raw ->
                            val clean = raw.filter { it.isDigit() || it == '.' }
                            if (clean.count { it == '.' } <= 1) editStockKgInput = clean.take(7)
                        },
                        label = { Text("Available Stock (kg, optional)") },
                        placeholder = { Text("e.g. 45.0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val price = editPriceInput.toIntOrNull()
                        if (price != null && price > 0) {
                            onUpdateCutPrice(cutToEdit.id, price)
                        }
                        val stockKg = editStockKgInput.toDoubleOrNull()
                        if (stockKg != null && stockKg >= 0.0) {
                            onUpdateCutStockKg(cutToEdit.id, stockKg)
                        }
                        editingCut = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ButcheryPalette.EmeraldHeader),
                    modifier = Modifier.testTag("butchery_confirm_edit_cut_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCut = null }) { Text("Cancel") }
            }
        )
    }

    if (showAddCutDialog) {
        AddButcheryCutDialog(
            enabledCategories = enabledCategoriesDraft,
            defaultPrice = defaultRateDraft.toIntOrNull() ?: 680,
            onDismiss = { showAddCutDialog = false },
            onConfirm = { name, category, price, stockKg, iconKey ->
                onAddCut(name, category, price, stockKg, iconKey)
                showAddCutDialog = false
            }
        )
    }
}

@Composable
private fun AddButcheryCutDialog(
    enabledCategories: List<String>,
    defaultPrice: Int,
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: String, pricePerKgKsh: Int, stockKg: Double, iconKey: String) -> Unit
) {
    val availableCategories = remember(enabledCategories) {
        enabledCategories.ifEmpty { ButcheryPricing.ALL_MEAT_CATEGORIES }
    }
    var name by remember { mutableStateOf("") }
    var category by remember(availableCategories) { mutableStateOf(availableCategories.first()) }
    var priceText by remember { mutableStateOf(defaultPrice.toString()) }
    var stockKgText by remember { mutableStateOf("") }

    fun defaultIconFor(cat: String): String = when (cat) {
        ButcheryCategory.GOAT.label -> "goat"
        ButcheryCategory.CHICKEN.label -> "chicken"
        ButcheryCategory.OFFAL.label -> "matumbo"
        ButcheryCategory.PORK.label -> "pork"
        else -> "beef"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Meat Cut", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Cut Name") },
                    placeholder = { Text("e.g. Beef Ribs") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("butchery_new_cut_name_input")
                )
                Text("Category", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableCategories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter(Char::isDigit).take(5) },
                    label = { Text("Selling Price per kg (KES)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("butchery_new_cut_price_input")
                )
                OutlinedTextField(
                    value = stockKgText,
                    onValueChange = { raw ->
                        val clean = raw.filter { it.isDigit() || it == '.' }
                        if (clean.count { it == '.' } <= 1) stockKgText = clean.take(7)
                    },
                    label = { Text("Opening Stock (kg, optional)") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val parsedPrice = priceText.toIntOrNull() ?: 0
            Button(
                onClick = {
                    val stockKg = stockKgText.toDoubleOrNull() ?: 0.0
                    onConfirm(name.trim(), category, parsedPrice, stockKg, defaultIconFor(category))
                },
                enabled = name.trim().isNotEmpty() && parsedPrice > 0,
                colors = ButtonDefaults.buttonColors(containerColor = ButcheryPalette.EmeraldHeader),
                modifier = Modifier.testTag("butchery_confirm_add_cut_button")
            ) {
                Text("Add Cut")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
