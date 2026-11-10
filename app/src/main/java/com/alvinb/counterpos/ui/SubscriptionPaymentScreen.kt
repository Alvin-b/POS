package com.alvinb.counterpos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvinb.counterpos.data.SubscriptionEntitlement
import com.alvinb.counterpos.data.SubscriptionManager
import com.alvinb.counterpos.data.SubscriptionStatus
import java.text.NumberFormat
import java.util.Locale

// Safaricom M-Pesa Brand Colors
private val MpesaGreen = Color(0xFF00A349)
private val MpesaGreenDark = Color(0xFF007A34)
private val MpesaGreenLight = Color(0xFFE8F8EE)

@Composable
fun SubscriptionPaymentScreen(
    viewModel: CounterPosViewModel,
    onContinueToPos: () -> Unit = {},
    onExitToAdmin: (() -> Unit)? = null,
    onSignOut: () -> Unit = {}
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activeShop = session.activeBusiness

    val entitlement = remember(activeShop, state.isOnline, state.subscriptionEntitlement) {
        state.subscriptionEntitlement ?: if (activeShop != null) {
            SubscriptionManager.evaluate(activeShop, isOnline = state.isOnline)
        } else {
            SubscriptionEntitlement(
                status = SubscriptionStatus.EXPIRED,
                isEntitled = false,
                daysRemaining = 0,
                hoursGraceRemaining = 0,
                expiryTimestamp = 0L,
                expiryFormatted = "Expired",
                amountDueKsh = 2500,
                planDays = 30,
                isGracePeriod = false,
                statusLabel = "Subscription Expired",
                message = "Your subscription has ended. Pay to continue using Counter POS."
            )
        }
    }

    var phoneNumber by remember(activeShop?.lastPaymentPhone, activeShop?.ownerPhone) {
        mutableStateOf(activeShop?.lastPaymentPhone ?: activeShop?.ownerPhone ?: "")
    }

    val focusManager = LocalFocusManager.current
    val formattedAmount = NumberFormat.getNumberInstance(Locale.US).format(entitlement.amountDueKsh)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Brand Header Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MpesaGreenLight)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MpesaGreen)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Lipa na M-Pesa • Daraja Online",
                    color = MpesaGreenDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            // Main Lock Icon / Status Icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.isSubscriptionPaymentSuccess) MpesaGreenLight
                        else MaterialTheme.colorScheme.errorContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (state.isSubscriptionPaymentSuccess) Icons.Default.CheckCircle
                    else Icons.Default.Lock,
                    contentDescription = "Subscription Status",
                    tint = if (state.isSubscriptionPaymentSuccess) MpesaGreen
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Title & Subtitle
            Text(
                text = if (state.isSubscriptionPaymentSuccess) "Subscription Activated!"
                else if (entitlement.status == SubscriptionStatus.TRIAL) "Free Trial Ended"
                else "Subscription Renewal Required",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = activeShop?.shopName?.let { "Store: $it" } ?: "Counter POS SaaS",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            // Pricing & Plan Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Standard Store Plan",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "All counters, staff & devices included",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "KSh $formattedAmount",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MpesaGreen
                            )
                            Text(
                                text = "for ${entitlement.planDays} days",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Safe & Offline Resilient",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Safe & Offline Tolerant: Queued sales sync once renewed",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Dynamic State: Payment In-Progress vs Success vs Input Form
            if (state.isSubscriptionPaymentSuccess) {
                // Payment Success Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MpesaGreenLight)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = MpesaGreen,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "M-Pesa Payment Received",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MpesaGreenDark
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Normal store operations have been completely restored.",
                            fontSize = 13.sp,
                            color = MpesaGreenDark,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = onContinueToPos,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("continue_to_pos_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MpesaGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Enter POS Workspace",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            } else if (state.isPollingStkPayment) {
                // STK Push Waiting / Polling State
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MpesaGreen,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "STK Push Sent to Your Phone",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = state.stkCustomerMessage
                                ?: "Please check your phone and enter your 4-digit M-Pesa PIN to complete payment of KSh $formattedAmount.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = viewModel::verifySubscriptionPayment,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("verify_payment_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Check Now", fontSize = 13.sp)
                            }

                            // Instant confirmation helper for demo / sandbox environments
                            Button(
                                onClick = viewModel::simulateConfirmSubscriptionPayment,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("confirm_test_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MpesaGreen),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Confirm (Demo)", fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                // Standard Payment Form
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "M-Pesa Phone Number",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "The phone will receive an STK Push prompt for your PIN.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mpesa_phone_input"),
                            placeholder = { Text("0712 345 678 or 2547...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Phone",
                                    tint = MpesaGreen
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    viewModel.initiateSubscriptionPayment(phoneNumber)
                                }
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (!state.subscriptionPaymentError.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = state.subscriptionPaymentError.orEmpty(),
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.initiateSubscriptionPayment(phoneNumber)
                            },
                            enabled = !state.isInitiatingStkPush && phoneNumber.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("pay_mpesa_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MpesaGreen,
                                disabledContainerColor = MpesaGreen.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (state.isInitiatingStkPush) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Initiating STK Push...",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                text = "Pay Now • KSh $formattedAmount",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Superadmin return shortcut if user is platform admin
            if (session.isPlatformAdmin && onExitToAdmin != null) {
                OutlinedButton(
                    onClick = onExitToAdmin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .height(48.dp)
                        .testTag("return_superadmin_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Return to Superadmin Portal", fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(12.dp))
            }

            // Sign out / switch store button
            TextButton(
                onClick = onSignOut,
                modifier = Modifier.testTag("payment_sign_out_button")
            ) {
                Text(
                    text = "Sign out or switch business account",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
