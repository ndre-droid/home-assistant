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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// ---- HomeFlow "Glow" palette ----
// Warm near-black ground, ONE accent (the colour of light), warm neutrals.
// Colour comes from the lights themselves (gradients on cards), not from chrome.
private val Amber = Color(0xFFF0B45E)
private val Coral = Color(0xFFF0705A)

private val DarkScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF17130A),
    secondary = Amber,
    onSecondary = Color(0xFF17130A),
    tertiary = Coral,
    background = Color(0xFF0B0C0D),
    onBackground = Color(0xFFEDEBE7),
    surface = Color(0xFF17191C),          // card
    onSurface = Color(0xFFEDEBE7),
    surfaceVariant = Color(0xFF22252A),   // inset fill / chips / tracks
    onSurfaceVariant = Color(0xFFA3A19C), // muted text
    outline = Color(0xFF26292D),
    outlineVariant = Color(0xFF1F2225),
    error = Color(0xFFFF8A7A)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFA35F0C),
    onPrimary = Color.White,
    secondary = Color(0xFFA35F0C),
    onSecondary = Color.White,
    tertiary = Color(0xFFC2453E),
    background = Color(0xFFF4F3F0),
    onBackground = Color(0xFF1A1B1C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1B1C),
    surfaceVariant = Color(0xFFECEBE6),
    onSurfaceVariant = Color(0xFF5F5D59),
    outline = Color(0xFFE2E0DB),
    outlineVariant = Color(0xFFE8E6E1),
    error = Color(0xFFB3261E)
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
val Green: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val TextPrim: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
val TextSec: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Hairline: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline

/** True when the current scheme is dark (works for dynamic colour too). */
val IsDark: Boolean @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Card fill: a hair lighter at the top so cards read as softly lit, not flat. */
val CardGradient: Brush
    @Composable @ReadOnlyComposable get() {
        val s = MaterialTheme.colorScheme.surface
        val top = if (IsDark) Color.White.copy(alpha = 0.035f).compositeOver(s) else s
        return Brush.verticalGradient(listOf(top, s))
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
        onPrimary = if (accentIsLight(accent)) Color(0xFF17130A) else Color.White,
        onSecondary = if (accentIsLight(accent)) Color(0xFF17130A) else Color.White
    ) else base
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp)
        ),
        content = content
    )
}

private fun accentIsLight(c: Color): Boolean =
    (0.299f * c.red + 0.587f * c.green + 0.114f * c.blue) > 0.6f
