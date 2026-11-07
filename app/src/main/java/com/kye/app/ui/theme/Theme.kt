package com.kye.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Violet = Color(0xFF6C5CE7)
val VioletSoft = Color(0xFFA29BFE)
val Mint = Color(0xFF00B894)
val Coral = Color(0xFFFF7675)
val Amber = Color(0xFFFDCB6E)

val HeroBrush = Brush.linearGradient(listOf(Color(0xFF5B4BDB), Color(0xFF8E7CFF), Color(0xFFE47BB4)))

private val Light = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E5FF),
    onPrimaryContainer = Color(0xFF2A1F7A),
    secondary = Mint,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5F7EE),
    onSecondaryContainer = Color(0xFF00382B),
    tertiary = Coral,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE3E2),
    onTertiaryContainer = Color(0xFF5C1110),
    background = Color(0xFFF6F5FB),
    onBackground = Color(0xFF1B1A23),
    surface = Color(0xFFF6F5FB),
    onSurface = Color(0xFF1B1A23),
    surfaceVariant = Color(0xFFEDEBF6),
    onSurfaceVariant = Color(0xFF5E5A72),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1EFF8),
    surfaceContainerHighest = Color(0xFFE8E5F3),
    outline = Color(0xFFD3CFE3),
    outlineVariant = Color(0xFFE6E3EF)
)

private val Dark = darkColorScheme(
    primary = VioletSoft,
    onPrimary = Color(0xFF1E1466),
    primaryContainer = Color(0xFF3B2F9E),
    onPrimaryContainer = Color(0xFFE9E5FF),
    secondary = Color(0xFF55EFC4),
    onSecondary = Color(0xFF00382B),
    secondaryContainer = Color(0xFF00513F),
    onSecondaryContainer = Color(0xFFD5F7EE),
    tertiary = Color(0xFFFF9F9E),
    onTertiary = Color(0xFF5C1110),
    tertiaryContainer = Color(0xFF6E2321),
    onTertiaryContainer = Color(0xFFFFE3E2),
    background = Color(0xFF0F0E17),
    onBackground = Color(0xFFEDEBF7),
    surface = Color(0xFF0F0E17),
    onSurface = Color(0xFFEDEBF7),
    surfaceVariant = Color(0xFF262435),
    onSurfaceVariant = Color(0xFFB9B5CC),
    surfaceContainerLowest = Color(0xFF0C0B12),
    surfaceContainerLow = Color(0xFF17161F),
    surfaceContainer = Color(0xFF1B1A25),
    surfaceContainerHigh = Color(0xFF23222E),
    surfaceContainerHighest = Color(0xFF2D2B3A),
    outline = Color(0xFF3A3850),
    outlineVariant = Color(0xFF2C2A3D)
)

private val KyeTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    )
}

private val KyeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun KyeTheme(theme: String, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = KyeTypography,
        shapes = KyeShapes,
        content = content
    )
}

/** Colours for payment modes in charts. */
fun paymentColor(index: Int): Color = listOf(
    Violet, Mint, Color(0xFFFDCB6E), Color(0xFFFF7675), Color(0xFF74B9FF), Color(0xFFE84393)
)[index % 6]
