package com.kye.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kye.app.R

val Violet = Color(0xFF6C5CE7)
val VioletSoft = Color(0xFFA29BFE)
val Mint = Color(0xFF00B894)
val Coral = Color(0xFFFF7675)
val Amber = Color(0xFFFDCB6E)

val HeroBrush = Brush.linearGradient(listOf(Color(0xFF5B4BDB), Color(0xFF8E7CFF), Color(0xFFE47BB4)))

/** The big gradient card on Home and Recurring; follows the chosen colours. */
val LocalHeroBrush = staticCompositionLocalOf { HeroBrush }

/**
 * A colour set picked in Settings → Appearance. Green (saved, good) and coral (over, warning)
 * stay the same in every palette so they always mean the same thing.
 */
data class Palette(
    val key: String,
    val label: String,
    val primary: Long,
    val primaryDark: Long,
    val container: Long,
    val containerDark: Long,
    val onContainer: Long,
    val onContainerDark: Long,
    val onPrimaryDark: Long,
    val hero: List<Long>
)

val Palettes = listOf(
    Palette(
        "violet", "Violet", 0xFF6C5CE7, 0xFFA29BFE, 0xFFE9E5FF, 0xFF3B2F9E, 0xFF2A1F7A, 0xFFE9E5FF, 0xFF1E1466,
        listOf(0xFF5B4BDB, 0xFF8E7CFF, 0xFFE47BB4)
    ),
    Palette(
        "ocean", "Ocean", 0xFF1E6FD9, 0xFF8AB8FF, 0xFFDCEBFF, 0xFF123E73, 0xFF00224D, 0xFFDCEBFF, 0xFF002B5C,
        listOf(0xFF0B4FB3, 0xFF1E88E5, 0xFF22B8CF)
    ),
    Palette(
        "rose", "Rose", 0xFFD63384, 0xFFF78FB3, 0xFFFFE0EF, 0xFF6A1B45, 0xFF4A0D2C, 0xFFFFE0EF, 0xFF4A0D2C,
        listOf(0xFFB0124F, 0xFFE84393, 0xFFF77FAE)
    ),
    Palette(
        "gold", "Gold", 0xFFA86A12, 0xFFF6C667, 0xFFFFF0D1, 0xFF5C3D07, 0xFF3D2800, 0xFFFFF0D1, 0xFF3D2800,
        listOf(0xFF7A4A08, 0xFFB7791F, 0xFFDDA83E)
    ),
    Palette(
        "slate", "Slate", 0xFF455A64, 0xFFB0BEC5, 0xFFE3E9EC, 0xFF2E3A40, 0xFF1A2327, 0xFFE3E9EC, 0xFF1A2327,
        listOf(0xFF1E272E, 0xFF3D4C57, 0xFF6C7A86)
    )
)

/** Settings key for "use the phone's wallpaper colours" (Android 12 and newer). */
const val WALLPAPER = "wallpaper"

private fun lightFor(p: Palette) = lightColorScheme(
    primary = Color(p.primary),
    onPrimary = Color.White,
    primaryContainer = Color(p.container),
    onPrimaryContainer = Color(p.onContainer),
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

private fun darkFor(p: Palette) = darkColorScheme(
    primary = Color(p.primaryDark),
    onPrimary = Color(p.onPrimaryDark),
    primaryContainer = Color(p.containerDark),
    onPrimaryContainer = Color(p.onContainerDark),
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

/** Wallpaper colours, but keep KYE's own green and coral for "good" and "warning". */
private fun ColorScheme.withKyeMeanings(dark: Boolean): ColorScheme {
    val base = if (dark) darkFor(Palettes[0]) else lightFor(Palettes[0])
    return copy(
        secondary = base.secondary,
        onSecondary = base.onSecondary,
        secondaryContainer = base.secondaryContainer,
        onSecondaryContainer = base.onSecondaryContainer,
        tertiary = base.tertiary,
        onTertiary = base.onTertiary,
        tertiaryContainer = base.tertiaryContainer,
        onTertiaryContainer = base.onTertiaryContainer
    )
}

/** True black for OLED screens: saves battery and looks deep. Cards stay a shade lighter. */
private fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color(0xFF111114),
    surfaceContainerLow = Color(0xFF141418),
    surfaceContainer = Color(0xFF18181D),
    surfaceContainerHigh = Color(0xFF1F1F25),
    surfaceContainerHighest = Color(0xFF29292F),
    surfaceVariant = Color(0xFF1F1F25)
)

/** Poppins: rounded, clear digits and the ₹ sign, used for headings and amounts. */
val Poppins = FontFamily(
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

private val KyeTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = Poppins, fontWeight = FontWeight.Bold),
        displayMedium = t.displayMedium.copy(fontFamily = Poppins, fontWeight = FontWeight.Bold),
        displaySmall = t.displaySmall.copy(fontFamily = Poppins, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontFamily = Poppins, fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontFamily = Poppins, fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontFamily = Poppins, fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold)
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
fun KyeTheme(theme: String, palette: String = "violet", pureBlack: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val wallpaper = palette == WALLPAPER
    val chosen = Palettes.firstOrNull { it.key == palette } ?: Palettes[0]
    val scheme = remember(dark, palette, pureBlack) {
        val base = when {
            wallpaper && dark -> dynamicDarkColorScheme(context).withKyeMeanings(true)
            wallpaper -> dynamicLightColorScheme(context).withKyeMeanings(false)
            dark -> darkFor(chosen)
            else -> lightFor(chosen)
        }
        if (dark && pureBlack) base.pureBlack() else base
    }
    val hero = remember(palette, dark) {
        if (wallpaper) {
            // The light scheme's deep tones keep white text readable in both modes.
            val l = dynamicLightColorScheme(context)
            Brush.linearGradient(listOf(l.primary, l.primary.copy(alpha = 0.85f).compositeOver(l.tertiary), l.tertiary))
        } else {
            Brush.linearGradient(chosen.hero.map { Color(it) })
        }
    }
    CompositionLocalProvider(LocalHeroBrush provides hero) {
        MaterialTheme(
            colorScheme = scheme,
            typography = KyeTypography,
            shapes = KyeShapes,
            content = content
        )
    }
}

/** Colours for payment modes in charts. */
fun paymentColor(index: Int): Color = listOf(
    Violet, Mint, Color(0xFFFDCB6E), Color(0xFFFF7675), Color(0xFF74B9FF), Color(0xFFE84393)
)[index % 6]
