package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ClubColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF00363D),
    onPrimaryContainer = NeonCyan,
    secondary = NeonPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3B185F),
    onSecondaryContainer = Color(0xFFE9D5FF),
    tertiary = NeonPink,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF4C0519),
    onTertiaryContainer = Color(0xFFFFD1DC),
    background = NightClubDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = NeonRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Enforce nightclub neon branding
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ClubColorScheme,
        typography = Typography,
        content = content
    )
}
