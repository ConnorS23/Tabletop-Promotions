package com.tableads.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFFFF6B35)
val Surface = Color(0xFF16181D)
val SurfaceHigh = Color(0xFF22252C)

private val colors = darkColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    secondary = Color(0xFF4ECDC4),
    background = Color(0xFF0E0F12),
    surface = Surface,
    surfaceVariant = SurfaceHigh,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun TableAdsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
