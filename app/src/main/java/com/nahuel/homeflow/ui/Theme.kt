package com.nahuel.homeflow.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// ---- Brand palette: Spotify/YouTube language ----
// Neutral near-black canvas, ONE saturated accent, gray steps for everything else.
private val AccentGreen  = Color(0xFF1ED760)   // default accent (changeable in settings)
private val BrandPink    = Color(0xFFF2649E)
private val BrandGreenOk = Color(0xFF1ED760)

// Dark: YouTube's pure near-black + Spotify's gray steps. No blue tint, no borders.
private val DarkScheme = darkColorScheme(
    primary = AccentGreen,
    onPrimary = Color(0xFF0D0D0D),         // black-on-green, like Spotify's play button
    secondary = AccentGreen,
    tertiary = BrandPink,
    background = Color(0xFF0F0F0F),
    onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF1B1B1B),           // card step
    onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF272727),    // chip/control step (YouTube chip gray)
    onSurfaceVariant = Color(0xFFAAAAAA),  // muted text
    outline = Color(0xFF2A2A2A),
    outlineVariant = Color(0xFF232323),
    error = Color(0xFFF26D6D)
)

// Light: YouTube light — white canvas, gray chips, near-black text, deep green accent.
private val LightScheme = lightColorScheme(
    primary = Color(0xFF12833B),
    onPrimary = Color.White,
    secondary = Color(0xFF12833B),
    tertiary = Color(0xFFC33C7E),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F0F0F),
    surface = Color(0xFFF2F2F2),
    onSurface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFFE5E5E5),
    onSurfaceVariant = Color(0xFF606060),
    outline = Color(0xFFDDDDDD),
    outlineVariant = Color(0xFFE8E8E8),
    error = Color(0xFFCC3D33)
)

/** Theme mode preference. Stored in Config.themeMode. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

// ---- Theme-aware tokens (all screens reference these) ----
val Bg: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
val Surface1: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
val Surface2: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant
val Violet: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val Blue: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary
val Pink: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary
val Green: Color @Composable @ReadOnlyComposable get() = BrandGreenOk
val TextPrim: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
val TextSec: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Hairline: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline

val CardGradient: Brush
    @Composable @ReadOnlyComposable get() {
        val s = MaterialTheme.colorScheme.surface
        return Brush.verticalGradient(listOf(s, s))
    }
val AccentGradient: Brush
    @Composable @ReadOnlyComposable get() =
        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))

@Composable
fun HomeFlowTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    accentHex: String = "",
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val base = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkScheme
        else -> LightScheme
    }
    // Custom accent (settings picker) overrides primary when dynamic color is off.
    val accent = runCatching {
        if (accentHex.length == 7 && accentHex.startsWith("#") && !dynamicColor)
            Color(android.graphics.Color.parseColor(accentHex)) else null
    }.getOrNull()
    val scheme = if (accent != null) base.copy(
        primary = accent,
        secondary = accent,
        // keep icon/text on the accent readable for light accents
        onPrimary = if (accentIsLight(accent)) Color(0xFF0D0D0D) else Color.White
    ) else base
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp)
        ),
        content = content
    )
}

private fun accentIsLight(c: Color): Boolean =
    (0.299f * c.red + 0.587f * c.green + 0.114f * c.blue) > 0.6f
