package com.alvinb.counterpos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvinb.counterpos.data.remote.AdminBusiness

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminClientBillingSheet(
    business: AdminBusiness,
    isUpdating: Boolean,
    isUpdatingPhone: Boolean = false,
    isUpdatingBusinessType: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (
        price: Int?,
        endsAt: String?,
        status: String?,
        planDays: Int?,
        trialEndsAt: String?,
        note: String?
    ) -> Unit,
    onSetCustomTrial: (days: Int) -> Unit,
    onExtendTrial: (days: Int) -> Unit,
    onActivatePaid: (days: Int) -> Unit,
    onSetPaymentLock: (locked: Boolean, amountKsh: Int?, reason: String?) -> Unit,
    onUpdatePhone: (phone: String) -> Unit = {},
    onElevateToClub: () -> Unit = {},
    onRequestDeleteBusiness: () -> Unit = {}
) {
    var priceText by remember(business.shopId, business.subscriptionPriceKsh) {
        mutableStateOf(business.subscriptionPriceKsh.toString())
    }
    var planDaysText by remember(business.shopId, business.subscriptionPlanDays) {
        mutableStateOf(business.subscriptionPlanDays.toString())
    }
    var selectedStatus by remember(business.shopId, business.subscriptionStatus) {
        mutableStateOf(
            business.subscriptionStatus.lowercase().takeIf { it in setOf("trial", "active", "expired") }
                ?: "trial"
        )
    }
    var customTrialDaysText by remember(business.shopId) {
        mutableStateOf("14")
    }
    var trialEndsAtText by remember(business.shopId, business.trialEndsAt) {
        mutableStateOf(business.trialEndsAt.orEmpty())
    }
    var customEndsAt by remember(business.shopId, business.subscriptionEndsAt) {
        mutableStateOf(business.subscriptionEndsAt.orEmpty())
    }
    var noteText by remember(business.shopId, business.customBillingNote) {
        mutableStateOf(business.customBillingNote.orEmpty())
    }
    var lockAmountText by remember(business.shopId, business.paymentLockAmountKsh, business.subscriptionPriceKsh) {
        mutableStateOf(
            (if (business.paymentLockAmountKsh > 0) business.paymentLockAmountKsh else business.subscriptionPriceKsh).toString()
        )
    }
    var lockReasonText by remember(business.shopId, business.paymentLockReason) {
        mutableStateOf(business.paymentLockReason.orEmpty())
    }
    var phoneText by remember(business.shopId, business.ownerPhone, business.lastPaymentPhone) {
        mutableStateOf(business.ownerPhone ?: business.lastPaymentPhone.orEmpty())
    }
    val phoneValid = com.alvinb.counterpos.data.SubscriptionManager.normalizeKenyanPhone(phoneText) != null

    val billingState = business.billingState()
    val stateAccent = adminStateColor(billingState)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val parsedCustomTrialDays = customTrialDaysText.toIntOrNull()?.coerceIn(1, 365)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = business.shopName,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Individual client billing • changes apply only to this store",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Live authoritative summary card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = stateAccent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, stateAccent.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AdminStatusPill(billingState.label, stateAccent)
                        AdminStatusPill(
                            label = business.businessTypeLabel(),
                            color = if (business.isLiquorStoreBusiness()) Color(0xFFFBBF24) else Color(0xFF38BDF8)
                        )
                        Text(
                            text = "KSh ${business.subscriptionPriceKsh} / ${business.subscriptionPlanDays}d",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = business.billingExpiryLabel(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Client phone",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = business.displayPhoneLabel(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Client Contact Phone Editor
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Client Contact Phone",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = phoneText,
                            onValueChange = { phoneText = it },
                            label = { Text("Owner phone (e.g. 0712 345 678)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_client_phone_input")
                        )
                        Button(
                            onClick = { onUpdatePhone(phoneText) },
                            enabled = phoneValid && !isUpdatingPhone && !isUpdating,
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("admin_save_client_phone_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isUpdatingPhone) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Save", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (business.isLiquorStoreBusiness()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0E2920),
                    border = BorderStroke(1.dp, Color(0xFF5EE6B0).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TableRestaurant,
                                contentDescription = null,
                                tint = Color(0xFF5EE6B0),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Store Tier · Liquor Store",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Elevate ${business.shopName} to a Club to unlock Floor Tables, Multi-Counter Management, Working Hours, and Sales Analytics.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onElevateToClub,
                            enabled = !isUpdating && !isUpdatingBusinessType,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5EE6B0),
                                contentColor = Color(0xFF022619)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("admin_sheet_elevate_to_club_button")
                        ) {
                            if (isUpdatingBusinessType) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF022619))
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Default.Upgrade, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Elevate to Club", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Section 1: Instant Payment Lock / Unlock
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    1.dp,
                    if (business.paymentLockActive) MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (business.paymentLockActive) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (business.paymentLockActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (business.paymentLockActive) "Payment Lock Active" else "Instant Account Payment Lock",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (business.paymentLockActive) {
                            "POS is currently blocked on every device for ${business.shopName}. Clearing unlocks the store immediately."
                        } else {
                            "Immediately block POS access for this client only and require M-Pesa payment of the specified amount."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!business.paymentLockActive) {
                        OutlinedTextField(
                            value = lockAmountText,
                            onValueChange = { lockAmountText = it.filter(Char::isDigit).take(6) },
                            label = { Text("Amount due (KES)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = lockReasonText,
                            onValueChange = { lockReasonText = it.take(240) },
                            label = { Text("Lock reason / instruction (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Button(
                            onClick = {
                                onSetPaymentLock(true, lockAmountText.toIntOrNull(), lockReasonText.trim().ifBlank { null })
                            },
                            enabled = !isUpdating && (lockAmountText.toIntOrNull() ?: 0) > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("admin_lock_client_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Lock This Client Immediately", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { onSetPaymentLock(false, null, null) },
                            enabled = !isUpdating,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("admin_unlock_client_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Unlock Client Access Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Section 2: Per-Client Free Trial Control (Strictly Individual)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Individual Free Trial (${business.shopName})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Assign or extend a free trial strictly for this client without affecting other tenants.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(7, 14, 21, 30).forEach { days ->
                            FilterChip(
                                selected = customTrialDaysText == days.toString(),
                                onClick = { customTrialDaysText = days.toString() },
                                label = { Text("${days}d", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customTrialDaysText,
                            onValueChange = { customTrialDaysText = it.filter(Char::isDigit).take(3) },
                            label = { Text("Trial Days") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { parsedCustomTrialDays?.let(onSetCustomTrial) },
                            enabled = !isUpdating && parsedCustomTrialDays != null,
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFBBF24),
                                contentColor = Color(0xFF1E1400)
                            )
                        ) {
                            Text("Set Trial", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { onExtendTrial(parsedCustomTrialDays ?: 14) },
                            enabled = !isUpdating,
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("+${parsedCustomTrialDays ?: 14}d", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                    OutlinedTextField(
                        value = trialEndsAtText,
                        onValueChange = { trialEndsAtText = it },
                        label = { Text("Trial Ends At (ISO UTC)") },
                        placeholder = { Text("2026-12-31T23:59:59Z") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Section 3: Paid Term, Price & Manual Override
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Paid Term & Custom Pricing",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onActivatePaid(30) },
                            enabled = !isUpdating,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("+30d Paid", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { onActivatePaid(90) },
                            enabled = !isUpdating,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("+90d Paid", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { priceText = it.filter(Char::isDigit).take(6) },
                            label = { Text("Price (KES)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = planDaysText,
                            onValueChange = { planDaysText = it.filter(Char::isDigit).take(3) },
                            label = { Text("Term (Days)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    Text(
                        text = "Client Subscription Status",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("trial", "active", "expired").forEach { st ->
                            FilterChip(
                                selected = selectedStatus == st,
                                onClick = { selectedStatus = st },
                                label = { Text(st.replaceFirstChar { it.uppercase() }) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customEndsAt,
                        onValueChange = { customEndsAt = it },
                        label = { Text("Paid Term Expiry (ISO UTC)") },
                        placeholder = { Text("2026-12-31T23:59:59Z") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Client Billing Note") },
                        placeholder = { Text("e.g. Partner rate or agreed renewal date") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            if (!business.lastPaymentReceipt.isNullOrBlank() || !business.lastPaymentAt.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Last Verified M-Pesa Payment", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                "Receipt: ${business.lastPaymentReceipt ?: "N/A"} • Phone: ${business.lastPaymentPhone ?: "N/A"}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!business.lastPaymentAt.isNullOrBlank()) {
                                Text(
                                    "Date: ${business.lastPaymentAt.take(19).replace('T', ' ')}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val priceInt = priceText.toIntOrNull()
                        val daysInt = planDaysText.toIntOrNull()
                        val endsIso = customEndsAt.trim().takeIf(String::isNotBlank)
                            ?: if (selectedStatus == "active" && daysInt != null && daysInt > 0) {
                                com.alvinb.counterpos.data.SubscriptionManager.formatUtcIso(System.currentTimeMillis() + daysInt * 86_400_000L)
                            } else {
                                null
                            }
                        val trialIso = trialEndsAtText.trim().takeIf(String::isNotBlank)
                            ?: if (selectedStatus == "trial") {
                                val trialDays = (parsedCustomTrialDays ?: 14)
                                com.alvinb.counterpos.data.SubscriptionManager.formatUtcIso(System.currentTimeMillis() + trialDays * 86_400_000L)
                            } else {
                                null
                            }
                        val note = noteText.trim().takeIf(String::isNotBlank)
                        onSave(priceInt, endsIso, selectedStatus, daysInt, trialIso, note)
                    },
                    enabled = !isUpdating,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("admin_save_client_billing_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save Client Billing", fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider()

            OutlinedButton(
                onClick = onRequestDeleteBusiness,
                enabled = !isUpdating && !isUpdatingPhone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("admin_sheet_delete_business_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Delete Business Permanently",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
