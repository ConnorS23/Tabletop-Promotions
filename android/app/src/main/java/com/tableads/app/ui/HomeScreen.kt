package com.tableads.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tableads.app.data.DeviceState

@Composable
fun HomeScreen(state: DeviceState, onNavigate: (Screen) -> Unit) {
    Column(Modifier.fillMaxSize().padding(40.dp)) {
        Text(
            state.payload?.restaurant?.name ?: "Welcome",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text("What would you like to do?", style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Tile("View Menu", Icons.AutoMirrored.Filled.MenuBook, Color(0xFFFF6B35), Modifier.weight(1f)) { onNavigate(Screen.Menu) }
            Tile("Play Games", Icons.Filled.SportsEsports, Color(0xFF4ECDC4), Modifier.weight(1f)) { onNavigate(Screen.Games) }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Tile("Back to Ads", Icons.Filled.PlayCircle, Color(0xFF5C6BC0), Modifier.weight(3f), small = true) { onNavigate(Screen.Ads) }
            Tile("Settings", Icons.Filled.Lock, Color(0xFF424852), Modifier.weight(1f), small = true) { onNavigate(Screen.PinLock) }
        }
    }
}

@Composable
private fun Tile(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        if (small) {
            Row(Modifier.fillMaxSize().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(icon, null, Modifier.size(48.dp), tint = Color.White)
                Spacer(Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, Modifier.size(120.dp), tint = Color.White)
                Spacer(Modifier.height(16.dp))
                Text(label, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
