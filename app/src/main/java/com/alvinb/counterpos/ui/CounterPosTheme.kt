package com.alvinb.counterpos.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvinb.counterpos.R

val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold)
)

object SerenityTokens {
    val CanvasDark = Color(0xFF0B1210)
    val GlowDark = Color(0xFF14392D)
    val GlowNodeInner = Color(0xFF1F5A48)
    val FloorSurfaceDark = Color(0xFF0F1814)
    val CardSurfaceDark = Color(0xFF121C18)
    val NavSurfaceDark = Color(0xFF13201B)
    val ActiveChipFillDark = Color(0xFF1B3A30)
    val FreePillDark = Color(0xFF1D2A24)
    val MintPrimary = Color(0xFF5EE6B0)
    val OnMintPrimary = Color(0xFF06231A)
    val AmberAccent = Color(0xFFE8A04A)
    val OnAmberAccent = Color(0xFF2B1500)
    val SubtleBorderDark = Color(0xFF22302A)
    val StrongBorderDark = Color(0xFF2E4A3F)
    val DashedFreeBorderDark = Color(0xFF3A5A4C)
    val MutedTextDark = Color(0xFF8FA39A)
    val PrimaryTextDark = Color(0xFFE8F1EC)
    val ThumbnailFrame = Color(0xFFFFFFFF)
}

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
    primary = SerenityTokens.MintPrimary,
    onPrimary = SerenityTokens.OnMintPrimary,
    primaryContainer = SerenityTokens.ActiveChipFillDark,
    onPrimaryContainer = Color(0xFFBFFAE1),
    secondary = SerenityTokens.AmberAccent,
    onSecondary = SerenityTokens.OnAmberAccent,
    secondaryContainer = Color(0xFF3D2912),
    onSecondaryContainer = Color(0xFFFFDDB8),
    tertiary = Color(0xFF8ED8E2),
    onTertiary = Color(0xFF00363C),
    tertiaryContainer = Color(0xFF164E55),
    onTertiaryContainer = Color(0xFFA9EFF5),
    error = Color(0xFFFB7185),
    onError = Color(0xFF3D0611),
    errorContainer = Color(0xFF4C111E),
    onErrorContainer = Color(0xFFFFD5DC),
    background = SerenityTokens.CanvasDark,
    onBackground = SerenityTokens.PrimaryTextDark,
    surface = SerenityTokens.CardSurfaceDark,
    onSurface = SerenityTokens.PrimaryTextDark,
    surfaceVariant = SerenityTokens.FloorSurfaceDark,
    onSurfaceVariant = SerenityTokens.MutedTextDark,
    outline = SerenityTokens.StrongBorderDark,
    outlineVariant = SerenityTokens.SubtleBorderDark
)

private val CounterPosTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        ),
        headlineMedium = headlineMedium.copy(
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp
        ),
        headlineSmall = headlineSmall.copy(
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp
        ),
        titleLarge = titleLarge.copy(
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold
        ),
        titleMedium = titleMedium.copy(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold
        ),
        titleSmall = titleSmall.copy(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold
        ),
        bodyLarge = bodyLarge.copy(
            fontFamily = InterFontFamily,
            lineHeight = 22.sp
        ),
        bodyMedium = bodyMedium.copy(
            fontFamily = InterFontFamily,
            lineHeight = 21.sp
        ),
        bodySmall = bodySmall.copy(
            fontFamily = InterFontFamily
        ),
        labelLarge = labelLarge.copy(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold
        ),
        labelMedium = labelMedium.copy(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold
        ),
        labelSmall = labelSmall.copy(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium
        )
    )
}

private val CounterPosShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun CounterPosTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) CounterPosDarkColors else CounterPosLightColors,
        typography = CounterPosTypography,
        shapes = CounterPosShapes,
        content = content
    )
}
