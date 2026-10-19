package com.alvinb.counterpos.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CounterPosLight = lightColorScheme(
    primary = Color(0xFF087F5B), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F3E8), onPrimaryContainer = Color(0xFF07553F),
    secondary = Color(0xFFE07A22), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE8D2), onSecondaryContainer = Color(0xFF7A3B0A),
    tertiary = Color(0xFF356859), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDEDE6), onTertiaryContainer = Color(0xFF183B31),
    error = Color(0xFFB42318), errorContainer = Color(0xFFFEE4E2), onErrorContainer = Color(0xFF7A271A),
    background = Color(0xFFF5F7F6), onBackground = Color(0xFF17211D),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF17211D),
    surfaceVariant = Color(0xFFEAF0ED), onSurfaceVariant = Color(0xFF65736C),
    outline = Color(0xFFD2DDD7), outlineVariant = Color(0xFFE3EBE6)
)

private val CounterPosDark = darkColorScheme(
    primary = Color(0xFF53D39F), onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF07553F), onPrimaryContainer = Color(0xFFB4F2D7),
    secondary = Color(0xFFFFB36C), onSecondary = Color(0xFF4A2000),
    secondaryContainer = Color(0xFF713B13), onSecondaryContainer = Color(0xFFFFDCC0),
    tertiary = Color(0xFFA8D5C4), onTertiary = Color(0xFF11372C),
    tertiaryContainer = Color(0xFF275447), onTertiaryContainer = Color(0xFFC8EBDD),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF690005), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111815), onBackground = Color(0xFFE5EEE9),
    surface = Color(0xFF19221E), onSurface = Color(0xFFE5EEE9),
    surfaceVariant = Color(0xFF25322C), onSurfaceVariant = Color(0xFFB4C4BC),
    outline = Color(0xFF52635A), outlineVariant = Color(0xFF34433C)
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
fun CounterPosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) CounterPosDark else CounterPosLight,
        typography = CounterPosTypography,
        shapes = CounterPosShapes,
        content = content
    )
}
