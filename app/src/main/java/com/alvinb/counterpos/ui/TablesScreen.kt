package com.alvinb.counterpos.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvinb.counterpos.data.local.entity.DiningTableEntity
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.TableOrderEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TablesScreen(
    tables: List<DiningTableEntity>,
    orders: List<TableOrderEntity>,
    products: List<ProductEntity>,
    selectedTableId: String?,
    actionError: String?,
    isCreatingTable: Boolean,
    canManageTables: Boolean,
    canSettleTables: Boolean,
    isSettlingTable: Boolean,
    isDeletingTable: Boolean,
    onCreateTable: (String) -> Unit,
    onDeleteTable: (String) -> Unit,
    onSettleTable: (String, String) -> Unit,
    onOpenTable: (DiningTableEntity) -> Unit,
    onBackToTables: () -> Unit,
    onNextTable: (Int) -> Unit = {},
    onRefresh: () -> Unit = {},
    onAddProduct: (String, ProductEntity) -> Unit,
    onScanProduct: () -> Unit,
    showCounterLabels: Boolean = false,
    registeredWaiterEmails: Set<String> = emptySet()
) {
    val context = LocalContext.current
    val selected = tables.firstOrNull { it.id == selectedTableId }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newTableName by remember { mutableStateOf("") }
    var showProductPicker by remember { mutableStateOf(false) }
    var productQuery by remember { mutableStateOf("") }
    var showSettlementDialog by remember { mutableStateOf(false) }
    var selectedPaymentMethod by remember { mutableStateOf("cash") }
    var tableToDelete by remember { mutableStateOf<DiningTableEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var tableSearchQuery by remember { mutableStateOf("") }
    var tableStatusFilter by remember { mutableStateOf("all") } // "all", "open", "available"

    val currentOrders = orders.filter { it.tableId == selectedTableId && it.settlementId == null }
    val tableTotal = currentOrders.sumOf { it.totalKsh.toLong() }
    val productsByCode = remember(products) { products.associateBy { it.catalogCode } }

    // Settlement dialog
    if (showSettlementDialog && selected != null) {
        AlertDialog(
            onDismissRequest = { if (!isSettlingTable) showSettlementDialog = false },
            title = { Text("Close bill · ${selected.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${selected.name} · ${ksh(tableTotal)}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Choose payment method received. This records the settlement and clears the running bill.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf("cash" to "Cash", "mpesa" to "M-Pesa", "card" to "Card", "other" to "Other").forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPaymentMethod = value }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedPaymentMethod == value, onClick = { selectedPaymentMethod = value })
                            Spacer(Modifier.width(8.dp))
                            Text(label, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSettleTable(selected.id, selectedPaymentMethod)
                        showSettlementDialog = false
                    },
                    enabled = !isSettlingTable,
                    modifier = Modifier.testTag("confirm_settle_table_button")
                ) {
                    if (isSettlingTable) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Mark paid & close")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettlementDialog = false }, enabled = !isSettlingTable) {
                    Text("Cancel")
                }
            }
        )
    }

    LaunchedEffect(isCreatingTable, selectedTableId) {
        if (!isCreatingTable && selectedTableId != null) showCreateDialog = false
    }

    // Add Table Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCreatingTable) showCreateDialog = false },
            title = { Text("Add new table") },
            text = {
                Column {
                    Text("Enter a unique name or number for the table (e.g. Table 1, Patio B, VIP Booth).", fontSize = 13.sp)
                    OutlinedTextField(
                        value = newTableName,
                        onValueChange = { newTableName = it.take(40) },
                        label = { Text("Table name") },
                        placeholder = { Text("e.g. Table 5, Patio 2") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("table_name_input")
                    )
                    if (!actionError.isNullOrBlank()) {
                        Text(actionError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newTableName.trim()
                        if (clean.isNotBlank()) onCreateTable(clean)
                    },
                    enabled = newTableName.isNotBlank() && !isCreatingTable,
                    modifier = Modifier.testTag("confirm_create_table_button")
                ) {
                    if (isCreatingTable) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Create table")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }, enabled = !isCreatingTable) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Table Dialog
    if (showDeleteConfirmDialog && (tableToDelete != null || selected != null)) {
        val target = tableToDelete ?: selected!!
        val targetOrders = orders.filter { it.tableId == target.id && it.settlementId == null }
        val hasOpenOrders = targetOrders.isNotEmpty()
        val openTotal = targetOrders.sumOf { it.totalKsh.toLong() }

        AlertDialog(
            onDismissRequest = { if (!isDeletingTable) showDeleteConfirmDialog = false },
            title = { Text(if (hasOpenOrders) "Cannot delete table" else "Delete ${target.name}?") },
            text = {
                if (hasOpenOrders) {
                    Text(
                        "${target.name} has ${targetOrders.size} open served item(s) totaling ${ksh(openTotal)}. Settle and close the bill before deleting this table.",
                        fontSize = 14.sp
                    )
                } else {
                    Text(
                        "Are you sure you want to delete ${target.name}? This will permanently remove the table from the service floor.",
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                if (hasOpenOrders) {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Dismiss")
                    }
                } else {
                    Button(
                        onClick = {
                            onDeleteTable(target.id)
                            showDeleteConfirmDialog = false
                            tableToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = !isDeletingTable,
                        modifier = Modifier.testTag("confirm_delete_table_button")
                    ) {
                        if (isDeletingTable) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Delete table")
                        }
                    }
                }
            },
            dismissButton = {
                if (!hasOpenOrders) {
                    TextButton(onClick = { showDeleteConfirmDialog = false }, enabled = !isDeletingTable) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Product Picker Bottom Sheet
    if (showProductPicker && selected != null) {
        ModalBottomSheet(onDismissRequest = { showProductPicker = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("Add to ${selected.name}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = productQuery,
                    onValueChange = { productQuery = it },
                    label = { Text("Search in-stock products") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp)
                        .testTag("table_product_search_input")
                )
                val matches = products.filter {
                    it.inMyStock && it.stockQuantity > 0 &&
                        (productQuery.isBlank() || it.name.contains(productQuery, true) || it.brand.contains(productQuery, true))
                }
                LazyColumn(Modifier.fillMaxWidth().height(420.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(matches, key = { it.catalogCode }) { product ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showProductPicker = false
                                    productQuery = ""
                                    onAddProduct(selected.id, product)
                                }
                                .padding(vertical = 12.dp)
                                .testTag("pick_product_${product.catalogCode}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TableProductThumbnail(product = product, modifier = Modifier.size(44.dp))
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(product.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${product.stockQuantity} in stock · ${product.category} · ${ksh(product.sellingPriceKsh)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.Add, contentDescription = "Add ${product.name} to table", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (matches.isEmpty()) {
                        item {
                            Text("No matching products in stock.", modifier = Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // MAIN VIEW: Table List OR Table Detail
    if (selected == null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Header Row
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tables", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    val activeCount = tables.count { t -> orders.any { it.tableId == t.id && it.settlementId == null } }
                    Text(
                        "${tables.size} total · $activeCount with open bill${if (activeCount == 1) "" else "s"}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp).testTag("tables_refresh_button")) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh tables", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(6.dp))
                if (canManageTables) {
                    Button(
                        onClick = { newTableName = ""; showCreateDialog = true },
                        enabled = !isCreatingTable,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("add_table_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Add table", modifier = Modifier.padding(start = 6.dp), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Search bar for tables
            if (tables.size > 2) {
                OutlinedTextField(
                    value = tableSearchQuery,
                    onValueChange = { tableSearchQuery = it },
                    placeholder = { Text("Search tables...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("table_search_input")
                )
            }

            // Filter chips
            if (tables.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val openBillsCount = tables.count { t -> orders.any { it.tableId == t.id && it.settlementId == null } }
                    val emptyCount = tables.size - openBillsCount

                    FilterChip(
                        selected = tableStatusFilter == "all",
                        onClick = { tableStatusFilter = "all" },
                        label = { Text("All (${tables.size})") }
                    )
                    FilterChip(
                        selected = tableStatusFilter == "open",
                        onClick = { tableStatusFilter = "open" },
                        label = { Text("Open bills ($openBillsCount)") }
                    )
                    FilterChip(
                        selected = tableStatusFilter == "available",
                        onClick = { tableStatusFilter = "available" },
                        label = { Text("Available ($emptyCount)") }
                    )
                }
            }

            if (!actionError.isNullOrBlank() && !showCreateDialog) {
                Text(actionError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }

            val filteredTables = remember(tables, orders, tableSearchQuery, tableStatusFilter) {
                tables.filter { table ->
                    val matchesQuery = tableSearchQuery.isBlank() || table.name.contains(tableSearchQuery, ignoreCase = true)
                    val hasOpen = orders.any { it.tableId == table.id && it.settlementId == null }
                    val matchesStatus = when (tableStatusFilter) {
                        "open" -> hasOpen
                        "available" -> !hasOpen
                        else -> true
                    }
                    matchesQuery && matchesStatus
                }
            }

            if (tables.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(68.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.TableRestaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("No tables yet", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Add your dining, bar, patio, or VIP tables to start serving orders and tracking checks.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        if (canManageTables) {
                            Button(
                                onClick = { newTableName = ""; showCreateDialog = true },
                                enabled = !isCreatingTable,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.testTag("empty_add_table_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(19.dp))
                                Text("Add first table", modifier = Modifier.padding(start = 6.dp), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else if (filteredTables.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No tables match your filter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredTables, key = { it.id }) { table ->
                        val lines = orders.filter { it.tableId == table.id && it.settlementId == null }
                        val total = lines.sumOf { it.totalKsh.toLong() }
                        val hasOrders = lines.isNotEmpty()

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenTable(table) }
                                .testTag("table_card_${table.id}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (hasOrders) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(18.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (hasOrders) 2.dp else 0.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (hasOrders) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.TableRestaurant,
                                            contentDescription = null,
                                            tint = if (hasOrders) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text(table.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                    if (hasOrders) {
                                        Text(
                                            "${lines.size} item line${if (lines.size == 1) "" else "s"} · ${ksh(total)}",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        Text(
                                            "Available · Ready for guests",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (hasOrders) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Text(
                                            "Open bill",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                } else if (canManageTables) {
                                    IconButton(
                                        onClick = {
                                            tableToDelete = table
                                            showDeleteConfirmDialog = true
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("delete_table_${table.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete ${table.name}",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Table detail / Order management view
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 6.dp)) {
            // Top Action Row
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackToTables, modifier = Modifier.testTag("back_to_tables_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to tables")
                }
                Column(Modifier.weight(1f)) {
                    Text(selected.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${currentOrders.size} served item${if (currentOrders.size == 1) "" else "s"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Table switcher previous / next
                if (tables.size > 1) {
                    IconButton(onClick = { onNextTable(-1) }, modifier = Modifier.testTag("prev_table_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous table", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onNextTable(1) }, modifier = Modifier.testTag("next_table_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next table", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Delete table button
                if (canManageTables) {
                    IconButton(
                        onClick = {
                            tableToDelete = selected
                            showDeleteConfirmDialog = true
                        },
                        modifier = Modifier.testTag("delete_current_table_button")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete table",
                            tint = if (currentOrders.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                // Add table button
                if (canManageTables) {
                    IconButton(
                        onClick = { newTableName = ""; showCreateDialog = true },
                        modifier = Modifier.testTag("detail_add_table_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add table", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Print receipt button
                IconButton(
                    onClick = { printTableReceipt(context, selected.name, currentOrders, tableTotal) },
                    enabled = currentOrders.isNotEmpty(),
                    modifier = Modifier.testTag("print_table_receipt_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = "Print receipt for ${selected.name}",
                        tint = if (currentOrders.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }

            // Horizontal Table Navigator (Chips)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tables.forEach { table ->
                    val tOrders = orders.filter { it.tableId == table.id && it.settlementId == null }
                    val isCurrent = table.id == selected.id
                    FilterChip(
                        selected = isCurrent,
                        onClick = { onOpenTable(table) },
                        label = {
                            val labelText = if (tOrders.isNotEmpty()) {
                                "${table.name} (${tOrders.size})"
                            } else {
                                table.name
                            }
                            Text(labelText, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                        },
                        modifier = Modifier.testTag("chip_table_${table.id}")
                    )
                }

                if (canManageTables) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clickable { newTableName = ""; showCreateDialog = true }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .testTag("add_table_chip")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("New table", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }

            if (!actionError.isNullOrBlank()) {
                Text(actionError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }

            // Order Lines
            if (currentOrders.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No items served yet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tap 'Add item' or 'Scan barcode' below to add items to ${selected.name}'s check.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(currentOrders, key = { it.id }) { line ->
                        val product = productsByCode[line.catalogCode]
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                TableProductThumbnail(product = product, modifier = Modifier.size(42.dp))
                                Spacer(Modifier.size(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(line.productName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val counterLabel = if (showCounterLabels) line.counterName?.takeIf { it.isNotBlank() } else null
                                    val waiterLabel = line.waiterName?.takeIf { it.isNotBlank() }
                                    val attribution = listOfNotNull(
                                        counterLabel,
                                        waiterLabel?.let { "Recorded by $it" },
                                        time(line.servedAt)
                                    ).joinToString(" · ")
                                    Text(
                                        "${line.quantity} × ${line.unitName} · ${ksh(line.unitPriceKsh)} · $attribution",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(ksh(line.totalKsh), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }

            // Running Total Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Running total (${currentOrders.size} items)", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(ksh(tableTotal), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Mark Paid & Close Bill Button
            if (canSettleTables) {
                Button(
                    onClick = { selectedPaymentMethod = "cash"; showSettlementDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).testTag("settle_bill_button"),
                    enabled = currentOrders.isNotEmpty() && !isSettlingTable,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSettlingTable) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Mark bill paid & close", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Bottom Actions: Add Manually, Scan Barcode, Print
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { productQuery = ""; showProductPicker = true },
                    modifier = Modifier.weight(1f).testTag("add_product_to_table_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Add item", modifier = Modifier.padding(start = 5.dp))
                }
                OutlinedButton(
                    onClick = onScanProduct,
                    modifier = Modifier.weight(1f).testTag("scan_barcode_table_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Scan barcode", modifier = Modifier.padding(start = 5.dp))
                }
                OutlinedButton(
                    onClick = { printTableReceipt(context, selected.name, currentOrders, tableTotal) },
                    enabled = currentOrders.isNotEmpty(),
                    modifier = Modifier.testTag("print_table_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Print", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun TableProductThumbnail(product: ProductEntity?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(Modifier.fillMaxSize().padding(3.dp), contentAlignment = Alignment.Center) {
            if (product != null) {
                ProductThumbnail(product, Modifier.fillMaxSize())
            } else {
                Icon(
                    Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun ksh(amount: Long): String = "KSh ${String.format(Locale.US, "%,d", amount)}"
private fun ksh(amount: Int): String = ksh(amount.toLong())
private fun time(epoch: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epoch))

private fun printTableReceipt(context: Context, tableName: String, orders: List<TableOrderEntity>, total: Long) {
    if (orders.isEmpty()) return
    val htmlEncode: (String) -> String = { value ->
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    }
    val rows = orders.joinToString("") { order ->
        """
        <tr>
            <td style="padding: 4px 0;">${htmlEncode(order.productName)}</td>
            <td style="text-align: center; padding: 4px 0;">${order.quantity}</td>
            <td style="text-align: right; padding: 4px 0;">${ksh(order.totalKsh)}</td>
        </tr>
        """.trimIndent()
    }
    val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <style>
                body { font-family: monospace; font-size: 12px; margin: 0; padding: 12px; }
                .center { text-align: center; }
                .bold { font-weight: bold; }
                table { width: 100%; border-collapse: collapse; margin-top: 8px; }
                hr { border: none; border-top: 1px dashed #000; margin: 8px 0; }
            </style>
        </head>
        <body>
            <div class="center bold" style="font-size: 16px;">COUNTER POS</div>
            <div class="center">Table: ${htmlEncode(tableName)}</div>
            <div class="center">${SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(Date())}</div>
            <hr>
            <table>
                <thead>
                    <tr style="border-bottom: 1px dashed #000;">
                        <th style="text-align: left; padding-bottom: 4px;">Item</th>
                        <th style="text-align: center; padding-bottom: 4px;">Qty</th>
                        <th style="text-align: right; padding-bottom: 4px;">Total</th>
                    </tr>
                </thead>
                <tbody>
                    $rows
                </tbody>
            </table>
            <hr>
            <div style="display: flex; justify-content: space-between; font-size: 14px; font-weight: bold;">
                <span>TOTAL</span>
                <span style="float: right;">${ksh(total)}</span>
            </div>
            <div style="clear: both;"></div>
            <hr>
            <div class="center" style="font-size: 10px; margin-top: 12px;">Thank you! Please pay at the counter.</div>
        </body>
        </html>
    """.trimIndent()

    val webView = WebView(context).apply {
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                val adapter = view?.createPrintDocumentAdapter("Table-$tableName-Receipt")
                if (printManager != null && adapter != null) {
                    printManager.print("Table $tableName Receipt", adapter, PrintAttributes.Builder().build())
                }
            }
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
}
