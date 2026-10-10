package com.alvinb.counterpos.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CounterPosLight = lightColorScheme(
    primary = Color(0xFF059669),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFFD97706),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    error = Color(0xFFDC2626),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1)
)

private val CounterPosDark = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color(0xFF022C22),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color(0xFF451A03),
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = Color(0xFFFDE68A),
    error = Color(0xFFF87171),
    background = Color(0xFF0B0F17),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF131B26),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155)
)

@Composable
fun CounterPosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) CounterPosDark else CounterPosLight,
        content = content
    )
}
