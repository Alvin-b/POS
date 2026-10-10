package com.alvinb.counterpos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.alvinb.counterpos.data.ShopAccess

@Composable
fun BackendLoadingScreen(message: String = "Connecting to Counter POS…") {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(message, modifier = Modifier.padding(top = 14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AuthenticationScreen(
    state: CounterPosUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleMode: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Counter POS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Sign in to your shop", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (state.isCreatingAccount) "Create your account" else "Welcome back",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = state.authEmail,
                        onValueChange = onEmailChange,
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    OutlinedTextField(
                        value = state.authPassword,
                        onValueChange = onPasswordChange,
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    state.authError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    state.authNotice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    Button(onClick = onSubmit, enabled = !state.authBusy, modifier = Modifier.fillMaxWidth()) {
                        if (state.authBusy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Please wait…", modifier = Modifier.padding(start = 8.dp))
                        } else {
                            Text(if (state.isCreatingAccount) "Create account" else "Sign in")
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (state.isCreatingAccount) "Already have an account?" else "New to Counter POS?",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onToggleMode, enabled = !state.authBusy) {
                            Text(if (state.isCreatingAccount) "Sign in" else "Create account")
                        }
                    }
                    Text(
                        "Your shop data is private to its members. Email confirmation may be required for new accounts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ShopSelectionScreen(
    state: CounterPosUiState,
    onNameChange: (String) -> Unit,
    onCreateShop: () -> Unit,
    onSelectShop: (ShopAccess) -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Counter POS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Signed in as ${state.userEmail}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
            if (state.membershipLoadFailed) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Could not load your shops", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(state.shopError.orEmpty(), color = MaterialTheme.colorScheme.error)
                        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
                    }
                }
            } else if (state.shops.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Set up your shop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Create a shop to start adding beverages from the shared library.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = state.shopNameInput,
                            onValueChange = onNameChange,
                            label = { Text("Shop name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        state.shopError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(onClick = onCreateShop, enabled = !state.shopBusy, modifier = Modifier.fillMaxWidth()) {
                            if (state.shopBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("Create shop")
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Choose a shop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        state.shops.forEach { shop ->
                            Button(onClick = { onSelectShop(shop) }, modifier = Modifier.fillMaxWidth()) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(shop.name, fontWeight = FontWeight.SemiBold)
                                    Text("${shop.role.replaceFirstChar(Char::uppercase)} · ${shop.currency}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                state.shopError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp)) }
            }
            TextButton(onClick = onSignOut, modifier = Modifier.padding(top = 10.dp)) { Text("Sign out") }
        }
    }
}
