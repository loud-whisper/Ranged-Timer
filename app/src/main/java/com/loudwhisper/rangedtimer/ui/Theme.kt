package com.loudwhisper.rangedtimer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.loudwhisper.rangedtimer.core.Theme

// Calm, low-contrast palette: deep moss for dark, warm paper for light, sand as the accent.
private val Dark = darkColorScheme(
    primary = Color(0xFFD9C7A7),
    onPrimary = Color(0xFF2A2419),
    secondary = Color(0xFF9DB3A4),
    onSecondary = Color(0xFF14201A),
    background = Color(0xFF161D1A),
    onBackground = Color(0xFFE6E1D8),
    surface = Color(0xFF161D1A),
    onSurface = Color(0xFFE6E1D8),
    surfaceVariant = Color(0xFF24302B),
    onSurfaceVariant = Color(0xFFBEC7C1),
    surfaceContainer = Color(0xFF1E2723),
    surfaceContainerHigh = Color(0xFF24302B),
    outline = Color(0xFF5C6A64),
    error = Color(0xFFF2B8A8),
)

private val Light = lightColorScheme(
    primary = Color(0xFF5E6F5F),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF8A7556),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F2EA),
    onBackground = Color(0xFF26302C),
    surface = Color(0xFFF6F2EA),
    onSurface = Color(0xFF26302C),
    surfaceVariant = Color(0xFFE8E2D6),
    onSurfaceVariant = Color(0xFF4F5953),
    surfaceContainer = Color(0xFFEFEAE0),
    surfaceContainerHigh = Color(0xFFE8E2D6),
    outline = Color(0xFF9AA19B),
    error = Color(0xFFA64B36),
)

@Composable
fun RangedTimerTheme(theme: Theme, content: @Composable () -> Unit) {
    val dark = when (theme) {
        Theme.SYSTEM -> isSystemInDarkTheme()
        Theme.LIGHT -> false
        Theme.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
