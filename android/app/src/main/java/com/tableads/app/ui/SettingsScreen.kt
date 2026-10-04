package com.tableads.app.ui

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tableads.app.BuildConfig
import com.tableads.app.kiosk.Kiosk
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun PinLockScreen(verify: (String) -> Boolean, onUnlocked: () -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        if (error) {
            delay(900)
            error = false
            pin = ""
        }
    }

    ScreenScaffold(title = "Staff settings", onBack = onCancel) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(if (error) "Incorrect PIN" else "Enter staff PIN", style = MaterialTheme.typography.headlineSmall, color = if (error) Color(0xFFFF8A80) else Color.White)
            Spacer(Modifier.height(16.dp))
            Text(
                if (pin.isEmpty()) " " else "•".repeat(pin.length),
                fontSize = 48.sp,
                letterSpacing = 12.sp,
            )
            Spacer(Modifier.height(24.dp))
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    row.forEach { key ->
                        FilledTonalButton(
                            onClick = {
                                when (key) {
                                    "⌫" -> pin = pin.dropLast(1)
                                    "OK" -> if (verify(pin)) onUnlocked() else error = true
                                    else -> if (pin.length < 8) pin += key
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier.size(88.dp),
                            colors = if (key == "OK") ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.filledTonalButtonColors(),
                        ) {
                            if (key == "⌫") Icon(Icons.AutoMirrored.Filled.Backspace, "Delete") else Text(key, fontSize = 26.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as Activity
    val payload = state.payload
    var confirmRelease by remember { mutableStateOf(false) }
    var brightness by remember {
        mutableFloatStateOf(activity.window.attributes.screenBrightness.takeIf { it >= 0 } ?: 0.8f)
    }

    ScreenScaffold(title = "Staff settings", onBack = onClose) {
        Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Device", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Info("Restaurant", payload?.restaurant?.name ?: "Not linked")
                    Info("Display name", payload?.name?.ifBlank { "—" } ?: "—")
                    Info("Pairing code", payload?.pairCode ?: "—")
                    Info("Device ID", vm.repository.identity.deviceId)
                    Info("Status", if (state.online) "Online" else "Offline${state.lastError?.let { " ($it)" } ?: ""}")
                    Info("Last sync", if (state.lastSyncMillis == 0L) "Never" else DateFormat.getDateTimeInstance().format(Date(state.lastSyncMillis)))
                    Info("Ads cached", "${state.adFiles.size} / ${payload?.ads?.size ?: 0}")
                    Info("Menu", if (state.menuFile != null) "Downloaded" else "None")
                    Info("Kiosk mode", when {
                        Kiosk.isLocked(context) -> "Locked"
                        Kiosk.isDeviceOwner(context) -> "Device owner (unlocked)"
                        else -> "Not device owner"
                    })
                    Info("App version", BuildConfig.VERSION_NAME)
                }
            }
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Actions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Button(onClick = { vm.repository.requestSync() }, Modifier.fillMaxWidth()) { Text("Sync now") }
                    FilledTonalButton(onClick = { vm.repository.clearCache() }, Modifier.fillMaxWidth()) { Text("Clear cache & re-download") }
                    FilledTonalButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, Modifier.fillMaxWidth()) { Text("Wi-Fi settings") }
                    Text("Screen brightness")
                    Slider(value = brightness, onValueChange = {
                        brightness = it
                        activity.window.attributes = activity.window.attributes.apply {
                            screenBrightness = it.coerceIn(0.05f, 1f)
                        }
                    })
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { Kiosk.exit(activity) }, Modifier.fillMaxWidth()) { Text("Unlock kiosk until next restart") }
                    OutlinedButton(onClick = { activity.moveTaskToBack(true); Kiosk.exit(activity) }, Modifier.fillMaxWidth()) { Text("Exit to Android") }
                    if (Kiosk.isDeviceOwner(context)) {
                        TextButton(onClick = { confirmRelease = true }, Modifier.fillMaxWidth()) {
                            Text("Remove kiosk device owner…", color = Color(0xFFFF8A80))
                        }
                    }
                }
            }
        }
    }

    if (confirmRelease) {
        AlertDialog(
            onDismissRequest = { confirmRelease = false },
            title = { Text("Remove kiosk control?") },
            text = { Text("The tablet will no longer be locked to this app. You will need ADB to re-enable kiosk mode.") },
            confirmButton = { TextButton(onClick = { confirmRelease = false; Kiosk.releaseDeviceOwner(activity) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRelease = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row {
        Text(label, Modifier.width(150.dp), color = Color.White.copy(alpha = 0.6f))
        Box(Modifier.weight(1f)) { Text(value) }
    }
}
