package com.alvinb.counterpos.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CounterPosLightColors = lightColorScheme(
    primary = Color(0xFF087F5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F3E8),
    onPrimaryContainer = Color(0xFF07553F),
    secondary = Color(0xFFE07A22),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE8D2),
    onSecondaryContainer = Color(0xFF7A3B0A),
    tertiary = Color(0xFF356859),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDEDE6),
    onTertiaryContainer = Color(0xFF183B31),
    error = Color(0xFFB42318),
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A271A),
    background = Color(0xFFF5F7F6),
    onBackground = Color(0xFF17211D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17211D),
    surfaceVariant = Color(0xFFEAF0ED),
    onSurfaceVariant = Color(0xFF65736C),
    outline = Color(0xFFD2DDD7),
    outlineVariant = Color(0xFFE3EBE6)
)

private val CounterPosDarkColors = darkColorScheme(
    primary = Color(0xFF70D6A8),
    onPrimary = Color(0xFF063D2B),
    primaryContainer = Color(0xFF1F4B38),
    onPrimaryContainer = Color(0xFFB9F2D4),
    secondary = Color(0xFFFFB86B),
    onSecondary = Color(0xFF482300),
    secondaryContainer = Color(0xFF5A3517),
    onSecondaryContainer = Color(0xFFFFDDB8),
    tertiary = Color(0xFF9FD3BD),
    onTertiary = Color(0xFF153B2C),
    tertiaryContainer = Color(0xFF2C5140),
    onTertiaryContainer = Color(0xFFBCEBD5),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101612),
    onBackground = Color(0xFFE8F0EA),
    surface = Color(0xFF18211B),
    onSurface = Color(0xFFE8F0EA),
    surfaceVariant = Color(0xFF25312A),
    onSurfaceVariant = Color(0xFFB2C0B7),
    outline = Color(0xFF536258),
    outlineVariant = Color(0xFF39483E)
)

private val CounterPosTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 21.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Bold)
    )
}

private val CounterPosShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CounterPosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) CounterPosDarkColors else CounterPosLightColors,
        typography = CounterPosTypography,
        shapes = CounterPosShapes,
        content = content
    )
}
