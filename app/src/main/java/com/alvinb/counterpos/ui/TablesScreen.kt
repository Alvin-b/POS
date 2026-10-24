package com.alvinb.counterpos.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedPaymentMethod by remember { mutableStateOf("cash") }
    val currentOrders = orders.filter { it.tableId == selectedTableId && it.settlementId == null }
    val tableTotal = currentOrders.sumOf { it.totalKsh.toLong() }
    val productsByCode = remember(products) { products.associateBy { it.catalogCode } }

    if (showSettlementDialog && selected != null) {
        AlertDialog(
            onDismissRequest = { if (!isSettlingTable) showSettlementDialog = false },
            title = { Text("Mark bill paid and close?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${selected.name} · ${ksh(tableTotal)}")
                    Text("Choose how the customer paid. This records the method; it does not initiate a payment.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf("cash" to "Cash", "mpesa" to "M-Pesa", "card" to "Card", "other" to "Other").forEach { (value, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { selectedPaymentMethod = value }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedPaymentMethod == value, onClick = { selectedPaymentMethod = value })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSettleTable(selected.id, selectedPaymentMethod)
                        showSettlementDialog = false
                    },
                    enabled = !isSettlingTable
                ) {
                    if (isSettlingTable) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Mark paid & close")
                }
            },
            dismissButton = { TextButton(onClick = { showSettlementDialog = false }, enabled = !isSettlingTable) { Text("Cancel") } }
        )
    }

    if (showDeleteDialog && selected != null) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingTable) showDeleteDialog = false },
            title = { Text("Delete ${selected.name}?") },
            text = { Text("This removes the table from the business. A table with an open bill cannot be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTable(selected.id)
                        showDeleteDialog = false
                    },
                    enabled = !isDeletingTable
                ) {
                    if (isDeletingTable) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }, enabled = !isDeletingTable) { Text("Cancel") } }
        )
    }

    LaunchedEffect(isCreatingTable, selectedTableId) {
        if (!isCreatingTable && selectedTableId != null) showCreateDialog = false
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCreatingTable) showCreateDialog = false },
            title = { Text("Add a table") },
            text = {
                Column {
                    Text("Name it so orders and the printed receipt are easy to identify.", fontSize = 13.sp)
                    OutlinedTextField(
                        value = newTableName,
                        onValueChange = { newTableName = it.take(40) },
                        label = { Text("Table name") },
                        placeholder = { Text("e.g. Patio A, Window 2") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    if (!actionError.isNullOrBlank()) Text(actionError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onCreateTable(newTableName) },
                    enabled = newTableName.isNotBlank() && !isCreatingTable
                ) {
                    if (isCreatingTable) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Create table")
                }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } }
        )
    }

    if (showProductPicker && selected != null) {
        ModalBottomSheet(onDismissRequest = { showProductPicker = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("Add to ${selected.name}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = productQuery,
                    onValueChange = { productQuery = it },
                    label = { Text("Search in-stock products") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
                )
                val matches = products.filter {
                    it.inMyStock && it.stockQuantity > 0 &&
                        (productQuery.isBlank() || it.name.contains(productQuery, true) || it.brand.contains(productQuery, true))
                }
                LazyColumn(Modifier.fillMaxWidth().height(420.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(matches, key = { it.catalogCode }) { product ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                showProductPicker = false
                                productQuery = ""
                                onAddProduct(selected.id, product)
                            }.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TableProductThumbnail(product = product, modifier = Modifier.size(44.dp))
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(product.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${product.stockQuantity} in stock · ${product.category}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.Add, contentDescription = "Add ${product.name} to table")
                        }
                    }
                    if (matches.isEmpty()) item { Text("No matching products in stock.", modifier = Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }

    if (selected == null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tables", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Manage open bills across all tables", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (canManageTables) {
                    FilledIconButton(
                        onClick = { newTableName = ""; showCreateDialog = true },
                        enabled = !isCreatingTable
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add table", modifier = Modifier.size(22.dp))
                    }
                }
            }
            if (!actionError.isNullOrBlank() && !showCreateDialog) {
                Text(actionError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            if (tables.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No tables yet", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        Text(if (canManageTables) "Tap Add table to create one." else "Ask an owner or manager to create a table.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(tables, key = { it.id }) { table ->
                        val lines = orders.filter { it.tableId == table.id && it.settlementId == null }
                        val total = lines.sumOf { it.totalKsh.toLong() }
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenTable(table) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(18.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.TableRestaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                    }
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(table.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                    Text("${lines.size} served item${if (lines.size == 1) "" else "s"} · ${ksh(total)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("Open", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackToTables) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to tables") }
                Column(Modifier.weight(1f)) {
                    Text(selected.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${currentOrders.size} item line${if (currentOrders.size == 1) "" else "s"} served", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (canManageTables) {
                    IconButton(onClick = { newTableName = ""; showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add table")
                    }
                }
                if (canManageTables) {
                    IconButton(onClick = { showDeleteDialog = true }, enabled = !isDeletingTable) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${selected.name}", tint = MaterialTheme.colorScheme.error)
                    }
                }
                IconButton(onClick = { printTableReceipt(context, selected.name, currentOrders, tableTotal) }, enabled = currentOrders.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Print receipt for ${selected.name}", tint = if (currentOrders.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tables.forEach { table ->
                    FilterChip(
                        selected = table.id == selected.id,
                        onClick = { onOpenTable(table) },
                        label = { Text(table.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
                if (canManageTables) {
                    IconButton(
                        onClick = { newTableName = ""; showCreateDialog = true },
                        enabled = !isCreatingTable
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add table")
                    }
                }
            }
            if (!actionError.isNullOrBlank()) Text(actionError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            if (currentOrders.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No items served yet", fontWeight = FontWeight.SemiBold)
                        Text("Add ordered products to build this table’s check.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
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
                                Text(ksh(line.totalKsh), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Running total", fontWeight = FontWeight.Medium)
                    Text(ksh(tableTotal), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (canSettleTables) {
                Button(
                    onClick = { selectedPaymentMethod = "cash"; showSettlementDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    enabled = currentOrders.isNotEmpty() && !isSettlingTable
                ) {
                    if (isSettlingTable) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Mark paid & close bill")
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { productQuery = ""; showProductPicker = true },
                    modifier = Modifier.weight(0.45f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add products or drinks", modifier = Modifier.size(22.dp))
                }
                OutlinedButton(onClick = onScanProduct, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Scan barcode", modifier = Modifier.padding(start = 5.dp))
                }
                OutlinedButton(onClick = { printTableReceipt(context, selected.name, currentOrders, tableTotal) }, enabled = currentOrders.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Print", modifier = Modifier.padding(start = 4.dp))
                }
            }
            Text(
                "Add sale opens in-stock products. Choose a product, sale unit and quantity; stock and this table’s bill update together. Print opens Android’s printer picker for available Wi-Fi or installed print services.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
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
    val rows = orders.joinToString("") { item ->
        "<tr><td class='item'><strong>${htmlEncode(item.productName)}</strong><div class='detail'>${item.quantity} × ${htmlEncode(item.unitName)} @ ${ksh(item.unitPriceKsh)}</div><div class='detail'>Served ${time(item.servedAt)}</div></td><td class='amount'>${ksh(item.totalKsh)}</td></tr>"
    }
    val html = """
        <!doctype html><html><head><meta charset='utf-8'><style>
        *{box-sizing:border-box}body{font-family:Arial,Helvetica,sans-serif;color:#17211c;margin:28px auto;max-width:680px;font-size:14px;line-height:1.4}.brand{text-align:center;border-bottom:2px solid #183d2d;padding:4px 0 16px}.brand h1{font-size:26px;letter-spacing:1px;margin:0;color:#183d2d}.brand p{margin:4px 0 0;color:#607168;font-size:11px;letter-spacing:2px;text-transform:uppercase}.title{font-weight:700;font-size:17px;text-align:center;margin:18px 0 3px}.meta{text-align:center;color:#5e6a63;font-size:12px}.table-name{text-align:center;font-size:15px;font-weight:700;margin:3px 0 14px}table{width:100%;border-collapse:collapse;margin-top:14px;border-top:1px solid #cbd5ce}td{padding:10px 3px;border-bottom:1px solid #dce3de;vertical-align:top}.item{width:74%}.detail{font-size:11px;color:#647168;margin-top:2px}.amount{text-align:right;white-space:nowrap;font-weight:700}.total{border-top:2px solid #183d2d;margin-top:8px;padding-top:12px;display:flex;justify-content:space-between;font-size:18px;font-weight:700}.foot{text-align:center;margin-top:22px;padding-top:10px;border-top:1px dashed #aab7ae;color:#607168;font-size:11px}@media print{body{margin:0 auto;padding:10px;max-width:100%}.brand h1{font-size:20px}.title{margin-top:12px}}
        </style></head><body><header class='brand'><h1>COUNTERPOS</h1><p>Thank you for choosing us</p></header><div class='title'>TABLE BILL</div><div class='table-name'>${htmlEncode(tableName)}</div><div class='meta'>${SimpleDateFormat("EEE, dd MMM yyyy · h:mm a", Locale.getDefault()).format(Date())}</div><table>$rows</table><div class='total'><span>TOTAL DUE</span><span>${ksh(total)}</span></div><div class='foot'>Please confirm your order before payment.<br/>We appreciate your visit.</div></body></html>
    """.trimIndent()
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val adapter = view.createPrintDocumentAdapter("${tableName.replace(Regex("[^A-Za-z0-9_-]"), "_")}_receipt")
            printManager.print("${tableName} receipt", adapter, PrintAttributes.Builder().build())
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
}
