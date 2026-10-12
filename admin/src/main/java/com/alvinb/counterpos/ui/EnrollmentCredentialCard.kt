package com.alvinb.counterpos.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Bitmap

@Composable
fun EnrollmentCredentialCard(
    credential: EnrollmentCredential,
    onDismiss: () -> Unit
) {
    val bitmap = remember(credential.qrPayload) { qrBitmap(credential.qrPayload) }
    val clipboard = LocalClipboardManager.current
    var copied by remember(credential.passkey) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Access pass ready", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(credential.businessName, fontWeight = FontWeight.SemiBold)
            Text("Role: ${credential.role}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Secure access QR code", modifier = Modifier.size(210.dp))
            Text("Scan this QR code on the staff app", fontSize = 12.sp, textAlign = TextAlign.Center)
            Text("Passkey", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
            SelectionContainer {
                Text(credential.passkey, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, textAlign = TextAlign.Center)
            }
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(credential.passkey))
                    copied = true
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (copied) "Passkey copied" else "Copy passkey") }
            Text("Show or send this passkey only to the intended person. It is displayed once in this session.", fontSize = 11.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Hide") }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Done") }
            }
        }
    }
}

private fun qrBitmap(payload: String): Bitmap {
    val size = 720
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}
