package com.loudwhisper.rangedtimer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.loudwhisper.rangedtimer.core.Theme

// Dark is true OLED black with neutral greys; light is plain off-white. Sand is the only accent.
private val Dark = darkColorScheme(
    primary = Color(0xFFD9C7A7),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFFB0B0B0),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFFA8A8A8),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    outline = Color(0xFF3A3A3A),
    error = Color(0xFFF2B8A8),
)

private val Light = lightColorScheme(
    primary = Color(0xFF2B2B2B),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF8A7556),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFEAEAEA),
    onSurfaceVariant = Color(0xFF555555),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFE6E6E6),
    outline = Color(0xFFA0A0A0),
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
