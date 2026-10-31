package com.alvinb.counterpos.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import java.io.FileOutputStream
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
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
    registeredWaiterEmails: Set<String> = emptySet(),
    businessName: String = "Counter POS"
) {
    val context = LocalContext.current
    val selected = tables.firstOrNull { it.id == selectedTableId }

    // Dialog & sheet states
    var showCreateDialog by remember { mutableStateOf(false) }
    var newTableName by remember { mutableStateOf("") }
    var showProductPicker by remember { mutableStateOf(false) }
    var productQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var showSettlementDialog by remember { mutableStateOf(false) }
    var selectedPaymentMethod by remember { mutableStateOf("cash") }
    var printReceiptOnSettlement by remember { mutableStateOf(true) }
    var tableToDelete by remember { mutableStateOf<DiningTableEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var tableSearchQuery by remember { mutableStateOf("") }
    var tableStatusFilter by remember { mutableStateOf("all") } // "all", "open", "available"
    var showReceiptPreviewDialog by remember { mutableStateOf(false) }
    var receiptOrdersToPreview by remember { mutableStateOf<List<TableOrderEntity>>(emptyList()) }
    var receiptPaymentMethod by remember { mutableStateOf<String?>(null) }
    var tableDetailTab by remember { mutableStateOf(0) } // 0 = Active Bill, 1 = Settled Receipts
    var settlementSuccessMessage by remember { mutableStateOf<String?>(null) }

    val currentOrders = orders.filter { it.tableId == selectedTableId && it.settlementId == null }
    val settledOrders = orders.filter { it.tableId == selectedTableId && it.settlementId != null }
    val tableTotal = currentOrders.sumOf { it.totalKsh.toLong() }
    val productsByCode = remember(products) { products.associateBy { it.catalogCode } }

    val categories = remember(products) {
        listOf("All") + products.map { it.category }.filter(String::isNotBlank).distinct().sorted()
    }

    // When table creation finishes and table is selected, dismiss create dialog
    LaunchedEffect(isCreatingTable, selectedTableId) {
        if (!isCreatingTable && selectedTableId != null) showCreateDialog = false
    }

    // -----------------------------------------------------------------
    // DIALOG: RECEIPT PREVIEW (ON-SCREEN THERMAL RECEIPT + PRINT)
    // -----------------------------------------------------------------
    if (showReceiptPreviewDialog && selected != null) {
        val previewOrders = if (receiptOrdersToPreview.isNotEmpty()) receiptOrdersToPreview else currentOrders
        val previewTotal = previewOrders.sumOf { it.totalKsh.toLong() }

        AlertDialog(
            onDismissRequest = { showReceiptPreviewDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Guest Receipt", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { showReceiptPreviewDialog = false }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close preview")
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Thermal Receipt Visual Simulation
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                businessName.uppercase(Locale.ROOT),
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Table: ${selected.name}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                SimpleDateFormat("dd MMM yyyy · h:mm a", Locale.getDefault()).format(Date()),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                            Spacer(Modifier.height(8.dp))

                            // Column headers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("ITEM", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                                Text("QTY", fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(0.6f))
                                Text("AMOUNT", fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                            }

                            Spacer(Modifier.height(6.dp))

                            previewOrders.forEach { order ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1.5f)) {
                                        Text(order.productName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text("${order.unitName} @ ${ksh(order.unitPriceKsh)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("${order.quantity}", fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(0.6f))
                                    Text(ksh(order.totalKsh), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("TOTAL DUE", fontWeight = FontWeight.Black, fontSize = 15.sp)
                                Text(ksh(previewTotal), fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                            }

                            if (!receiptPaymentMethod.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .fillMaxWidth()
                                ) {
                                    Text(
                                        "PAID VIA ${receiptPaymentMethod!!.uppercase(Locale.ROOT)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Thank you for dining with us!",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        printTableReceipt(
                            context = context,
                            businessName = businessName,
                            tableName = selected.name,
                            orders = previewOrders,
                            total = previewTotal,
                            paymentMethod = receiptPaymentMethod
                        )
                        showReceiptPreviewDialog = false
                    },
                    modifier = Modifier.testTag("dialog_print_receipt_action")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Print receipt", modifier = Modifier.padding(start = 6.dp))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReceiptPreviewDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // -----------------------------------------------------------------
    // DIALOG: SETTLEMENT SUCCESS CONFIRMATION
    // -----------------------------------------------------------------
    if (settlementSuccessMessage != null && selected != null) {
        AlertDialog(
            onDismissRequest = { settlementSuccessMessage = null },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            },
            title = { Text("Bill Settled", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(settlementSuccessMessage!!, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Text(
                        "${selected.name} is now available and clear for new guests.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        settlementSuccessMessage = null
                        onBackToTables()
                    }
                ) {
                    Text("Back to Floor")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        val lastSettled = settledOrders.filter { it.settlementId != null }.takeLast(20)
                        if (lastSettled.isNotEmpty()) {
                            receiptOrdersToPreview = lastSettled
                            receiptPaymentMethod = selectedPaymentMethod
                            showReceiptPreviewDialog = true
                        }
                        settlementSuccessMessage = null
                    }
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Print Receipt", modifier = Modifier.padding(start = 4.dp))
                }
            }
        )
    }

    // -----------------------------------------------------------------
    // DIALOG: CLOSE & SETTLE BILL
    // -----------------------------------------------------------------
    if (showSettlementDialog && selected != null) {
        AlertDialog(
            onDismissRequest = { if (!isSettlingTable) showSettlementDialog = false },
            title = {
                Column {
                    Text("Close & Settle Bill", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text(selected.name, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Amount Due", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(ksh(tableTotal), fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    Text("Select Payment Method:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                    listOf(
                        "cash" to "Cash",
                        "mpesa" to "M-Pesa",
                        "card" to "Card",
                        "other" to "Other"
                    ).forEach { (value, label) ->
                        Surface(
                            onClick = { selectedPaymentMethod = value },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedPaymentMethod == value) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (selectedPaymentMethod == value) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedPaymentMethod == value,
                                    onClick = { selectedPaymentMethod = value }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(label, fontSize = 15.sp, fontWeight = if (selectedPaymentMethod == value) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }

                    // Print receipt toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { printReceiptOnSettlement = !printReceiptOnSettlement }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = printReceiptOnSettlement,
                            onCheckedChange = { printReceiptOnSettlement = it }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Print receipt automatically upon settling", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val snapshotOrders = currentOrders.toList()
                        val snapshotTotal = tableTotal
                        val method = selectedPaymentMethod
                        val willPrint = printReceiptOnSettlement

                        onSettleTable(selected.id, method)
                        showSettlementDialog = false

                        if (willPrint) {
                            printTableReceipt(
                                context = context,
                                businessName = businessName,
                                tableName = selected.name,
                                orders = snapshotOrders,
                                total = snapshotTotal,
                                paymentMethod = method
                            )
                        }
                        settlementSuccessMessage = "${selected.name} bill of ${ksh(snapshotTotal)} settled via ${method.uppercase(Locale.ROOT)}."
                    },
                    enabled = !isSettlingTable,
                    modifier = Modifier.testTag("confirm_settle_table_button")
                ) {
                    if (isSettlingTable) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Confirm & Settle ${ksh(tableTotal)}")
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

    // -----------------------------------------------------------------
    // DIALOG: ADD NEW TABLE (WITH ONE-TAP PRESETS)
    // -----------------------------------------------------------------
    if (showCreateDialog) {
        val existingNames = remember(tables) { tables.map { it.name.trim().lowercase(Locale.ROOT) }.toSet() }
        val presets = listOf("Table 1", "Table 2", "Table 3", "Table 4", "Table 5", "Bar 1", "Bar 2", "VIP 1", "VIP 2", "Patio 1")

        AlertDialog(
            onDismissRequest = { if (!isCreatingTable) showCreateDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TableRestaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add New Table", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a unique table number or floor location.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // Quick Preset Chips
                    Text("Quick suggestions:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.filter { it.lowercase(Locale.ROOT) !in existingNames }.take(5).forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { newTableName = preset }
                            ) {
                                Text(
                                    preset,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newTableName,
                        onValueChange = { newTableName = it.take(40) },
                        label = { Text("Table name / number") },
                        placeholder = { Text("e.g. Table 6, Booth 3, VIP Lounge") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .testTag("table_name_input")
                    )

                    if (!actionError.isNullOrBlank()) {
                        Text(actionError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
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
                        Text("Create Table")
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

    // -----------------------------------------------------------------
    // DIALOG: DELETE TABLE CONFIRMATION
    // -----------------------------------------------------------------
    if (showDeleteConfirmDialog && (tableToDelete != null || selected != null)) {
        val target = tableToDelete ?: selected!!
        val targetOrders = orders.filter { it.tableId == target.id && it.settlementId == null }
        val hasOpenOrders = targetOrders.isNotEmpty()
        val openTotal = targetOrders.sumOf { it.totalKsh.toLong() }

        AlertDialog(
            onDismissRequest = { if (!isDeletingTable) showDeleteConfirmDialog = false },
            title = { Text(if (hasOpenOrders) "Cannot delete table" else "Delete ${target.name}?", fontWeight = FontWeight.Bold) },
            text = {
                if (hasOpenOrders) {
                    Text(
                        "${target.name} has ${targetOrders.size} open served item(s) totaling ${ksh(openTotal)}. Settle and close the bill before deleting this table.",
                        fontSize = 14.sp
                    )
                } else {
                    Text(
                        "Are you sure you want to delete ${target.name}? This will permanently remove the table from the floor layout.",
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
                            Text("Delete Table")
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

    // -----------------------------------------------------------------
    // BOTTOM SHEET: ADD PRODUCTS TO TABLE (FAST MULTI-CATEGORY PICKER)
    // -----------------------------------------------------------------
    if (showProductPicker && selected != null) {
        ModalBottomSheet(
            onDismissRequest = { showProductPicker = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Add to ${selected.name}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Running check: ${ksh(tableTotal)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onScanProduct) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                OutlinedTextField(
                    value = productQuery,
                    onValueChange = { productQuery = it },
                    placeholder = { Text("Search drinks, food, cocktails...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("table_product_search_input")
                )

                // Category chips
                if (categories.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryFilter == cat,
                                onClick = { selectedCategoryFilter = cat },
                                label = { Text(cat, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                val matches = products.filter { product ->
                    val inStock = product.inMyStock && product.stockQuantity > 0
                    val matchesQuery = productQuery.isBlank() ||
                        product.name.contains(productQuery, ignoreCase = true) ||
                        product.brand.contains(productQuery, ignoreCase = true)
                    val matchesCat = selectedCategoryFilter == "All" || product.category.equals(selectedCategoryFilter, ignoreCase = true)
                    inStock && matchesQuery && matchesCat
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(matches, key = { it.catalogCode }) { product ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showProductPicker = false
                                    productQuery = ""
                                    onAddProduct(selected.id, product)
                                }
                                .testTag("pick_product_${product.catalogCode}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TableProductThumbnail(product = product, modifier = Modifier.size(46.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${product.stockQuantity} in stock · ${product.category}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(ksh(product.sellingPriceKsh), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text("+ Add", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }

                    if (matches.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("No matching in-stock products found", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------
    // MAIN SCREEN CONTENT
    // -----------------------------------------------------------------
    if (selected == null) {
        // =============================================================
        // VIEW A: SERVICE FLOOR OVERVIEW (TABLE LIST / GRID)
        // =============================================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header: Title, Floor Summary, Add Table
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Service Floor", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    val activeCount = tables.count { t -> orders.any { it.tableId == t.id && it.settlementId == null } }
                    val activeFloorTotal = orders.filter { it.settlementId == null }.sumOf { it.totalKsh.toLong() }
                    Text(
                        if (activeCount > 0) "$activeCount occupied · Floor: ${ksh(activeFloorTotal)}" else "${tables.size} tables · All available",
                        fontSize = 13.sp,
                        color = if (activeCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (activeCount > 0) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp).testTag("tables_refresh_button")) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh floor", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(6.dp))
                if (canManageTables) {
                    Button(
                        onClick = { newTableName = ""; showCreateDialog = true },
                        enabled = !isCreatingTable,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("add_table_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Add table", modifier = Modifier.padding(start = 4.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Search Bar
            if (tables.size > 2) {
                OutlinedTextField(
                    value = tableSearchQuery,
                    onValueChange = { tableSearchQuery = it },
                    placeholder = { Text("Search tables...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("table_search_input")
                )
            }

            // Status Filter Tabs
            if (tables.isNotEmpty()) {
                val openBillsCount = tables.count { t -> orders.any { it.tableId == t.id && it.settlementId == null } }
                val availableCount = tables.size - openBillsCount

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = tableStatusFilter == "all",
                        onClick = { tableStatusFilter = "all" },
                        label = { Text("All (${tables.size})") }
                    )
                    FilterChip(
                        selected = tableStatusFilter == "open",
                        onClick = { tableStatusFilter = "open" },
                        label = { Text("Open Bills ($openBillsCount)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    FilterChip(
                        selected = tableStatusFilter == "available",
                        onClick = { tableStatusFilter = "available" },
                        label = { Text("Available ($availableCount)") }
                    )
                }
            }

            if (!actionError.isNullOrBlank() && !showCreateDialog) {
                Text(actionError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
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
                // Empty Floor State
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(76.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.TableRestaurant,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("No Tables on the Floor", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Create dining, bar, or VIP tables to start taking orders, running guest checks, and printing receipts.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        if (canManageTables) {
                            Button(
                                onClick = { newTableName = ""; showCreateDialog = true },
                                enabled = !isCreatingTable,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("empty_add_table_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("Add First Table", modifier = Modifier.padding(start = 6.dp), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if (filteredTables.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No tables match your filter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // Responsive Grid of Table Cards
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                                containerColor = if (hasOrders) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = if (hasOrders) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                                    // Status pill
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (hasOrders) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            if (hasOrders) "ACTIVE" else "FREE",
                                            color = if (hasOrders) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    if (!hasOrders && canManageTables) {
                                        IconButton(
                                            onClick = {
                                                tableToDelete = table
                                                showDeleteConfirmDialog = true
                                            },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .testTag("delete_table_${table.id}")
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Delete ${table.name}",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                // Table Name
                                Text(
                                    table.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(Modifier.height(4.dp))

                                if (hasOrders) {
                                    // Running Bill Amount
                                    Text(
                                        ksh(total),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "${lines.size} item${if (lines.size == 1) "" else "s"}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        "Ready for guests",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(18.dp))
                                }

                                Spacer(Modifier.height(10.dp))

                                // Action Button
                                Button(
                                    onClick = { onOpenTable(table) },
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = if (hasOrders) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text(
                                        if (hasOrders) "Manage Check" else "+ Open Table",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // =============================================================
        // VIEW B: TABLE CHECK DETAIL (MANAGE BILL & PRINT RECEIPT)
        // =============================================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // Top Nav Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackToTables, modifier = Modifier.testTag("back_to_tables_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to tables")
                }
                Column(Modifier.weight(1f)) {
                    Text(selected.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (currentOrders.isNotEmpty()) "${currentOrders.size} active item${if (currentOrders.size == 1) "" else "s"} · ${ksh(tableTotal)}" else "Table is available",
                        fontSize = 12.sp,
                        color = if (currentOrders.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (currentOrders.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                // Table switcher previous / next
                if (tables.size > 1) {
                    IconButton(onClick = { onNextTable(-1) }, modifier = Modifier.testTag("prev_table_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous table", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onNextTable(1) }, modifier = Modifier.testTag("next_table_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next table", modifier = Modifier.size(18.dp))
                    }
                }

                // Print receipt preview button
                IconButton(
                    onClick = {
                        receiptOrdersToPreview = currentOrders
                        receiptPaymentMethod = null
                        showReceiptPreviewDialog = true
                    },
                    enabled = currentOrders.isNotEmpty(),
                    modifier = Modifier.testTag("print_table_receipt_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = "Print receipt for ${selected.name}",
                        tint = if (currentOrders.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }

                // Delete table button (if empty)
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
                            tint = if (currentOrders.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // Quick Horizontal Table Selector
            Row(
                modifier = Modifier
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
                            Text(
                                if (tOrders.isNotEmpty()) "${table.name} (${tOrders.size})" else table.name,
                                fontSize = 12.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
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
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("New", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                }
            }

            // Active Bill vs Settled Receipts Tabs
            TabRow(
                selectedTabIndex = tableDetailTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Tab(
                    selected = tableDetailTab == 0,
                    onClick = { tableDetailTab = 0 },
                    text = { Text("Active Check (${currentOrders.size})", fontWeight = if (tableDetailTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = tableDetailTab == 1,
                    onClick = { tableDetailTab = 1 },
                    text = { Text("Past Receipts", fontWeight = if (tableDetailTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            if (!actionError.isNullOrBlank()) {
                Text(actionError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
            }

            if (tableDetailTab == 0) {
                // =====================================================
                // ACTIVE RUNNING CHECK TAB
                // =====================================================
                if (currentOrders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(
                                Icons.Default.TableRestaurant,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("No Items on This Table", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Tap 'Add item' or 'Scan barcode' below to start the guest check.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(currentOrders, key = { it.id }) { line ->
                            val product = productsByCode[line.catalogCode]
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TableProductThumbnail(product = product, modifier = Modifier.size(42.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(line.productName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val counterLabel = if (showCounterLabels) line.counterName?.takeIf { it.isNotBlank() } else null
                                        val waiterLabel = line.waiterName?.takeIf { it.isNotBlank() }
                                        val attribution = listOfNotNull(
                                            counterLabel,
                                            waiterLabel?.let { "By $it" },
                                            time(line.servedAt)
                                        ).joinToString(" · ")
                                        Text(
                                            "${line.quantity} × ${line.unitName} · ${ksh(line.unitPriceKsh)}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (attribution.isNotBlank()) {
                                            Text(attribution, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                        }
                                    }
                                    Text(ksh(line.totalKsh), fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                // Unified Compact Footer: Running Total & Actions
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Check Total", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                ksh(tableTotal),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "${currentOrders.size} item${if (currentOrders.size == 1) "" else "s"}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onScanProduct,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("scan_barcode_table_button")
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Scan", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                            }
                            Button(
                                onClick = { productQuery = ""; showProductPicker = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("add_product_to_table_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Add Item", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                            }
                            if (canSettleTables && currentOrders.isNotEmpty()) {
                                Button(
                                    onClick = { selectedPaymentMethod = "cash"; showSettlementDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    enabled = !isSettlingTable,
                                    modifier = Modifier.testTag("settle_bill_button")
                                ) {
                                    if (isSettlingTable) {
                                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text("Settle", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // =====================================================
                // PAST SETTLED RECEIPTS TAB (RE-PRINT HISTORIC RECEIPTS)
                // =====================================================
                val groupedSettled = remember(settledOrders) {
                    settledOrders.groupBy { it.settlementId ?: "settled_${it.servedAt}" }
                }

                if (groupedSettled.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No settled receipts yet today", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Completed bills for this table will appear here for re-printing.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(groupedSettled.entries.toList(), key = { it.key }) { (_, groupOrders) ->
                            val groupTotal = groupOrders.sumOf { it.totalKsh.toLong() }
                            val settleTime = groupOrders.firstOrNull()?.servedAt ?: System.currentTimeMillis()

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Settled Check", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text(SimpleDateFormat("h:mm a · dd MMM", Locale.getDefault()).format(Date(settleTime)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(ksh(groupTotal), fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    // Item summary
                                    groupOrders.take(3).forEach { order ->
                                        Text(
                                            "• ${order.quantity} × ${order.productName} (${ksh(order.totalKsh)})",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (groupOrders.size > 3) {
                                        Text("+ ${groupOrders.size - 3} more items", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }

                                    Spacer(Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                receiptOrdersToPreview = groupOrders
                                                receiptPaymentMethod = "PAID"
                                                showReceiptPreviewDialog = true
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Text("View & Print Receipt", modifier = Modifier.padding(start = 4.dp), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
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

/**
 * Native Android Receipt Printing Adapter using system PrintManager and PrintedPdfDocument (zero WebView/Chromium)
 */
private class TableReceiptPrintAdapter(
    private val context: Context,
    private val businessName: String,
    private val tableName: String,
    private val orders: List<TableOrderEntity>,
    private val total: Long,
    private val paymentMethod: String?
) : PrintDocumentAdapter() {
    private var printedPdfDocument: PrintedPdfDocument? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        printedPdfDocument = PrintedPdfDocument(context, newAttributes)
        val info = PrintDocumentInfo.Builder("Table_${tableName.replace(Regex("[^A-Za-z0-9_-]"), "_")}_Receipt.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(1)
            .build()
        callback.onLayoutFinished(info, newAttributes != oldAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        val pdf = printedPdfDocument ?: return
        val page = pdf.startPage(0)
        if (cancellationSignal?.isCanceled == true) {
            pdf.close()
            printedPdfDocument = null
            callback.onWriteCancelled()
            return
        }

        val canvas = page.canvas
        val width = page.info.pageWidth.toFloat()
        val margin = 28f

        val textPaint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = 10f
            isAntiAlias = true
        }
        val boldPaint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val titlePaint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val centerPaint = Paint().apply {
            color = AndroidColor.DKGRAY
            textSize = 9f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val rightPaint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = 10f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val rightBoldPaint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = AndroidColor.GRAY
            strokeWidth = 1f
        }

        var y = 40f

        // Title: Business Name
        canvas.drawText(businessName.uppercase(Locale.ROOT), width / 2f, y, titlePaint)
        y += 18f

        // Subtitle: Table Name
        boldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Table: $tableName", width / 2f, y, boldPaint)
        boldPaint.textAlign = Paint.Align.LEFT
        y += 14f

        // Date & Time
        val dateStr = SimpleDateFormat("dd MMM yyyy · h:mm a", Locale.getDefault()).format(Date())
        canvas.drawText(dateStr, width / 2f, y, centerPaint)
        y += 16f

        // Payment status banner
        if (!paymentMethod.isNullOrBlank()) {
            val bannerText = "PAID VIA ${paymentMethod.uppercase(Locale.ROOT)}"
            boldPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(bannerText, width / 2f, y, boldPaint)
            boldPaint.textAlign = Paint.Align.LEFT
            y += 16f
        } else {
            canvas.drawText("** GUEST CHECK / UNPAID **", width / 2f, y, centerPaint)
            y += 16f
        }

        // Divider
        canvas.drawLine(margin, y, width - margin, y, linePaint)
        y += 14f

        // Column Headers
        boldPaint.textSize = 9f
        canvas.drawText("ITEM", margin, y, boldPaint)
        val rightHeaderPaint = Paint(rightPaint).apply {
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("TOTAL", width - margin, y, rightHeaderPaint)
        y += 6f
        canvas.drawLine(margin, y, width - margin, y, linePaint)
        y += 14f

        // Items
        boldPaint.textSize = 10f
        for (order in orders) {
            val name = if (order.productName.length > 30) order.productName.take(28) + "…" else order.productName
            canvas.drawText(name, margin, y, boldPaint)
            canvas.drawText(ksh(order.totalKsh), width - margin, y, rightPaint)
            y += 12f
            canvas.drawText("${order.quantity} × ${order.unitName} @ ${ksh(order.unitPriceKsh)}", margin, y, textPaint)
            y += 15f
        }

        // Total Divider
        canvas.drawLine(margin, y, width - margin, y, linePaint)
        y += 18f

        // Total
        val totalLabelPaint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("TOTAL DUE", margin, y, totalLabelPaint)
        canvas.drawText(ksh(total), width - margin, y, rightBoldPaint)
        y += 18f
        canvas.drawLine(margin, y, width - margin, y, linePaint)
        y += 20f

        // Footer
        canvas.drawText("Thank you for dining with us!", width / 2f, y, centerPaint)
        y += 12f
        canvas.drawText("Powered by Counter POS", width / 2f, y, centerPaint)

        pdf.finishPage(page)

        try {
            FileOutputStream(destination.fileDescriptor).use { out ->
                pdf.writeTo(out)
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        } finally {
            pdf.close()
            printedPdfDocument = null
        }
    }
}

private fun String.centerInWidth(width: Int): String {
    if (length >= width) return this
    val padding = (width - length) / 2
    return " ".repeat(padding) + this
}

private fun printTableReceipt(
    context: Context,
    businessName: String,
    tableName: String,
    orders: List<TableOrderEntity>,
    total: Long,
    paymentMethod: String? = null
) {
    if (orders.isEmpty()) return
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager != null) {
            val adapter = TableReceiptPrintAdapter(
                context = context,
                businessName = businessName,
                tableName = tableName,
                orders = orders,
                total = total,
                paymentMethod = paymentMethod
            )
            val printAttributes = PrintAttributes.Builder()
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                .build()
            printManager.print("Table $tableName Receipt", adapter, printAttributes)
            return
        }
    } catch (_: Exception) {
        // Fallback to text sharing below if system print service encounters an issue
    }

    runCatching {
        val dateStr = SimpleDateFormat("dd MMM yyyy · h:mm a", Locale.getDefault()).format(Date())
        val textReceipt = buildString {
            appendLine("================================")
            appendLine(businessName.uppercase(Locale.ROOT).centerInWidth(32))
            appendLine("Table: $tableName".centerInWidth(32))
            appendLine(dateStr.centerInWidth(32))
            if (!paymentMethod.isNullOrBlank()) {
                appendLine("[ PAID VIA ${paymentMethod.uppercase(Locale.ROOT)} ]".centerInWidth(32))
            } else {
                appendLine("[ GUEST CHECK / UNPAID ]".centerInWidth(32))
            }
            appendLine("--------------------------------")
            orders.forEach { o ->
                appendLine(o.productName)
                appendLine("  ${o.quantity} x ${ksh(o.unitPriceKsh)} = ${ksh(o.totalKsh)}")
            }
            appendLine("--------------------------------")
            appendLine("TOTAL DUE: ${ksh(total)}")
            appendLine("================================")
            appendLine("Thank you for dining with us!".centerInWidth(32))
            appendLine("Powered by Counter POS".centerInWidth(32))
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Receipt for Table $tableName")
            putExtra(android.content.Intent.EXTRA_TEXT, textReceipt)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share / Print Receipt"))
    }
}
