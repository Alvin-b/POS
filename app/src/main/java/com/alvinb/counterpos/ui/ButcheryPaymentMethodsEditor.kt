package com.alvinb.counterpos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvinb.counterpos.data.butchery.ButcheryPricing
import java.util.Locale

@Composable
fun ButcheryPaymentMethodsEditor(
    methods: List<String>,
    enabled: Boolean,
    onMethodsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var customInput by remember { mutableStateOf("") }

    fun addMethod(candidate: String) {
        val cleaned = candidate.trim().take(32)
        if (cleaned.isEmpty()) return
        val exists = methods.any { it.equals(cleaned, ignoreCase = true) }
        if (!exists && methods.size < 8) {
            onMethodsChange(methods + cleaned)
        }
    }

    val remainingSuggestions = remember(methods) {
        ButcheryPricing.SUGGESTED_PAYMENT_METHODS.filter { suggestion ->
            methods.none { it.equals(suggestion, ignoreCase = true) }
        }
    }

    val isLightSurface = MaterialTheme.colorScheme.surface.luminance() >= 0.5f
    val accentColor = if (isLightSurface) ButcheryPalette.EmeraldHeader else Color(0xFF5EE6B0)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Payments,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Payment Methods",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "${methods.size} active",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }

        WrapFlowLayout(
            modifier = Modifier.fillMaxWidth(),
            horizontalSpacing = 6.dp,
            verticalSpacing = 6.dp
        ) {
            methods.forEachIndexed { index, method ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                    modifier = Modifier.testTag("butchery_configured_method_$index")
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = if (methods.size > 1 && enabled) 2.dp else 10.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = method,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )
                        if (methods.size > 1 && enabled) {
                            IconButton(
                                onClick = {
                                    onMethodsChange(methods.filterNot { it.equals(method, ignoreCase = true) })
                                },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("butchery_remove_method_$index")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove $method",
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (enabled && methods.size < 8) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { customInput = it.take(32) },
                    label = { Text("Add payment method") },
                    placeholder = { Text("e.g. Till 8291, Pochi, Card") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("butchery_custom_payment_input")
                )
                OutlinedButton(
                    onClick = {
                        addMethod(customInput)
                        customInput = ""
                    },
                    enabled = customInput.trim().isNotEmpty() &&
                        methods.none { it.equals(customInput.trim(), ignoreCase = true) },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("butchery_add_payment_method_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add payment method", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            if (remainingSuggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Quick add:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    remainingSuggestions.forEach { suggestion ->
                        val tagSuffix = suggestion.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_")
                        FilterChip(
                            selected = false,
                            onClick = { addMethod(suggestion) },
                            label = { Text("+ $suggestion", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface) },
                            modifier = Modifier.testTag("butchery_suggest_method_$tagSuffix")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ButcheryMeatTypesEditor(
    selectedMeats: List<String>,
    enabled: Boolean,
    onMeatsChange: (List<String>) -> Unit,
    availableMeats: List<String> = ButcheryPricing.DEFAULT_MEAT_NAMES,
    modifier: Modifier = Modifier
) {
    val allMeats = remember(availableMeats, selectedMeats) {
        val seen = mutableSetOf<String>()
        (availableMeats + selectedMeats).mapNotNull { raw ->
            val cleaned = ButcheryPricing.normalizeLegacyMeatSelection(raw)
            if (cleaned.isNotEmpty() && seen.add(cleaned.lowercase(Locale.ROOT))) cleaned else null
        }.ifEmpty { ButcheryPricing.DEFAULT_MEAT_NAMES }
    }
    val singleSelected = selectedMeats.size == 1

    val isLightSurface = MaterialTheme.colorScheme.surface.luminance() >= 0.5f
    val accentColor = if (isLightSurface) ButcheryPalette.EmeraldHeader else Color(0xFF5EE6B0)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Meats Sold",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (singleSelected) accentColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = if (singleSelected) "${selectedMeats.first()} only" else "${selectedMeats.size} active",
                    fontSize = 11.sp,
                    color = if (singleSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        WrapFlowLayout(
            modifier = Modifier.fillMaxWidth(),
            horizontalSpacing = 6.dp,
            verticalSpacing = 6.dp
        ) {
            allMeats.forEach { meat ->
                val isSelected = selectedMeats.any { it.equals(meat, ignoreCase = true) }
                val tagSuffix = meat.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_")
                FilterChip(
                    selected = isSelected,
                    enabled = enabled,
                    onClick = {
                        if (isSelected) {
                            if (selectedMeats.size > 1) {
                                onMeatsChange(selectedMeats.filterNot { it.equals(meat, ignoreCase = true) })
                            }
                        } else {
                            onMeatsChange(
                                allMeats.filter { candidate ->
                                    candidate.equals(meat, ignoreCase = true) ||
                                        selectedMeats.any { it.equals(candidate, ignoreCase = true) }
                                }
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = if (isLightSurface) 0.15f else 0.22f),
                        selectedLabelColor = if (isLightSurface) Color(0xFF07553F) else Color(0xFFBFFAE1),
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    label = {
                        Text(
                            text = meat,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    modifier = Modifier.testTag("butchery_meat_type_$tagSuffix")
                )
            }
            if (selectedMeats.size < allMeats.size && enabled) {
                FilterChip(
                    selected = false,
                    onClick = { onMeatsChange(allMeats) },
                    label = { Text("Select All", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface) },
                    modifier = Modifier.testTag("butchery_meat_type_all")
                )
            }
        }
    }
}

@Composable
private fun WrapFlowLayout(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 6.dp,
    verticalSpacing: Dp = 6.dp,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val hGap = horizontalSpacing.roundToPx()
        val vGap = verticalSpacing.roundToPx()
        val maxWidth = constraints.maxWidth
        val itemConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(itemConstraints) }

        val rows = mutableListOf<List<Placeable>>()
        var currentRow = mutableListOf<Placeable>()
        var currentRowWidth = 0

        placeables.forEach { placeable ->
            val neededWidth = if (currentRow.isEmpty()) placeable.width else currentRowWidth + hGap + placeable.width
            if (currentRow.isNotEmpty() && maxWidth != androidx.compose.ui.unit.Constraints.Infinity && neededWidth > maxWidth) {
                rows += currentRow
                currentRow = mutableListOf(placeable)
                currentRowWidth = placeable.width
            } else {
                currentRow += placeable
                currentRowWidth = neededWidth
            }
        }
        if (currentRow.isNotEmpty()) {
            rows += currentRow
        }

        val totalWidth = if (constraints.hasFixedWidth) {
            constraints.maxWidth
        } else {
            rows.maxOfOrNull { row ->
                row.sumOf { it.width } + (row.size - 1).coerceAtLeast(0) * hGap
            }?.coerceIn(constraints.minWidth, constraints.maxWidth) ?: constraints.minWidth
        }
        val totalHeight = rows.foldIndexed(0) { idx, acc, row ->
            val rowHeight = row.maxOfOrNull { it.height } ?: 0
            acc + rowHeight + if (idx > 0) vGap else 0
        }.coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(totalWidth, totalHeight) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                val rowHeight = row.maxOfOrNull { it.height } ?: 0
                row.forEach { placeable ->
                    placeable.placeRelative(x, y + (rowHeight - placeable.height) / 2)
                    x += placeable.width + hGap
                }
                y += rowHeight + vGap
            }
        }
    }
}
