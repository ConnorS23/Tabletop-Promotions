package com.tableads.app.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.tableads.app.BuildConfig
import com.tableads.app.data.DeviceState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PairingScreen(state: DeviceState, deviceId: String, onOpenSettings: () -> Unit) {
    val code = state.payload?.pairCode
    val linkUrl = code?.let { pairingUrl(it) }

    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxSize().padding(48.dp),
            horizontalArrangement = Arrangement.spacedBy(64.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(360.dp).clip(RoundedCornerShape(24.dp)).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (linkUrl != null) {
                    val bitmap = remember(linkUrl) { qrBitmap(linkUrl, 800) }
                    Image(bitmap.asImageBitmap(), contentDescription = "Pairing QR code", modifier = Modifier.size(330.dp))
                } else {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Link this display", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(
                    "Scan the QR code with your phone and sign in to your\nmanager account, or enter this code on the website:",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.75f),
                )
                Text(
                    code ?: if (state.online) "…" else "Connecting…",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 8.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("Device ID: $deviceId", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.5f))
                if (state.lastError != null) {
                    Text("Offline: ${state.lastError}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF8A80))
                }
            }
        }
        // Hidden admin access: long-press the top-right corner.
        Box(
            Modifier.align(Alignment.TopEnd).size(96.dp).combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = onOpenSettings,
            ),
        )
    }
}

fun pairingUrl(code: String): String {
    val base = BuildConfig.LINK_BASE_URL.trimEnd('/')
    return if (base.isEmpty()) code else "$base/link?code=$code"
}

private fun qrBitmap(text: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i ->
        if (matrix[i % size, i / size]) AndroidColor.BLACK else AndroidColor.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
}
