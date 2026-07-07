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

// ---- Brand palette (used when dynamic color is off) ----
private val BrandViolet = Color(0xFF8B7CF7)
private val BrandBlue   = Color(0xFF3D8BFD)
private val BrandPink   = Color(0xFFF062A6)
private val BrandGreen  = Color(0xFF34D399)

// Dark scheme (the app's original navy look, mapped to M3 roles)
private val DarkScheme = darkColorScheme(
    primary = BrandViolet,
    onPrimary = Color.White,
    secondary = BrandBlue,
    tertiary = BrandPink,
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFF2F3F7),
    surface = Color(0xFF161B26),
    onSurface = Color(0xFFF2F3F7),
    surfaceVariant = Color(0xFF1E2433),
    onSurfaceVariant = Color(0xFF8A93A6),
    outline = Color(0xFF222836),
    outlineVariant = Color(0xFF222836),
    error = Color(0xFFF87171)
)

// Light scheme
private val LightScheme = lightColorScheme(
    primary = Color(0xFF6650E0),
    onPrimary = Color.White,
    secondary = Color(0xFF2C6FD6),
    tertiary = Color(0xFFC33C7E),
    background = Color(0xFFF7F7FB),
    onBackground = Color(0xFF1A1C22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C22),
    surfaceVariant = Color(0xFFEDEEF3),
    onSurfaceVariant = Color(0xFF5A6072),
    outline = Color(0xFFE0E2EA),
    outlineVariant = Color(0xFFE8EAF1),
    error = Color(0xFFD1443B)
)

/** Theme mode preference. Stored in Config.themeMode. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

// ---- Backward-compatible color tokens: now theme-aware ----
// Every screen still writes `Violet`, `TextPrim`, etc. — these now resolve
// from the active MaterialTheme colorScheme, so light/dark + dynamic all work.
val Bg: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
val Surface1: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
val Surface2: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant
val Violet: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val Blue: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary
val Pink: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary
val Green: Color @Composable @ReadOnlyComposable get() = BrandGreen
val TextPrim: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
val TextSec: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Hairline: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline

// Finer, flatter card fill — a subtle two-stop that reads as one clean surface.
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
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}
