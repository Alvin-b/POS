package com.alvinb.counterpos.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CounterPosLight = lightColorScheme(
    primary = Color(0xFF0F7B5F),
    onPrimary = Color.White,
    secondary = Color(0xFFB26A00),
    onSecondary = Color.White,
    error = Color(0xFFB3261E),
    background = Color(0xFFE9ECEF),
    onBackground = Color(0xFF14181F),
    surface = Color.White,
    onSurface = Color(0xFF14181F),
    surfaceVariant = Color(0xFFF3F5F7),
    onSurfaceVariant = Color(0xFF5D6673),
    outline = Color(0xFFD9DDE2)
)

private val CounterPosDark = darkColorScheme(
    primary = Color(0xFF2BB38B),
    onPrimary = Color(0xFF06201A),
    secondary = Color(0xFFF0B04A),
    onSecondary = Color(0xFF2B1A00),
    error = Color(0xFFFF8A80),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFEEF1F5),
    surface = Color(0xFF181C23),
    onSurface = Color(0xFFEEF1F5),
    surfaceVariant = Color(0xFF20252E),
    onSurfaceVariant = Color(0xFF9AA3AF),
    outline = Color(0xFF2B313B)
)

@Composable
fun CounterPosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) CounterPosDark else CounterPosLight,
        content = content
    )
}
