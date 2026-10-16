package com.alvinb.counterpos.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

@Composable
fun EnrollmentCredentialCard(
    credential: EnrollmentCredential,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(credential.passkey) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (credential.role == "owner") "Owner access pass ready" else "Staff access pass ready",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Text(credential.businessName, fontWeight = FontWeight.SemiBold)
            Text(
                if (credential.role == "owner") "Business owner" else "Cashier / staff member",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "Use this one-time credential to open the business. It expires after 24 hours or after redemption.",
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Text("Scan or copy this passkey", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            QrCodeImage(payload = credential.qrPayload)
            SelectionContainer {
                Text(
                    credential.passkey,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )
            }
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(credential.passkey))
                    copied = true
                },
                modifier = Modifier.fillMaxWidth().testTag("copy_passkey")
            ) { Text(if (copied) "Passkey copied" else "Copy passkey") }
            Text(
                "Send it only to the intended person.",
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Hide") }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Done") }
            }
        }
    }
}

@Composable
private fun QrCodeImage(payload: String) {
    val bitmap = remember(payload) {
        runCatching {
            val size = 280
            val matrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { image ->
                for (x in 0 until size) for (y in 0 until size) {
                    image.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR code for passkey",
            modifier = Modifier.padding(4.dp)
        )
    } else {
        Text("QR preview unavailable", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
}
