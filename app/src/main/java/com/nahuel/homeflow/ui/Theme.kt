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
private val BrandBlue   = Color(0xFF3B6EF5)   // committed primary accent (direction C)
private val BrandViolet = Color(0xFF7C74E8)   // secondary
private val BrandPink   = Color(0xFFEC5F9E)
private val BrandGreen  = Color(0xFF3DD68C)

// Dark scheme (the app's original navy look, mapped to M3 roles)
private val DarkScheme = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandViolet,
    tertiary = BrandPink,
    background = Color(0xFF0B0D12),        // blue-tinted near-black, not pure
    onBackground = Color(0xFFF4F6FB),
    surface = Color(0xFF12151C),           // card surface, one step up
    onSurface = Color(0xFFF4F6FB),
    surfaceVariant = Color(0xFF171B24),    // raised controls, two steps up
    onSurfaceVariant = Color(0xFF98A0B4),  // muted labels (passes contrast on tinted dark)
    outline = Color(0xFF20242F),           // thin hairline
    outlineVariant = Color(0xFF191D26),
    error = Color(0xFFF26D6D)
)

// Light scheme
private val LightScheme = lightColorScheme(
    primary = Color(0xFF2C5FE0),
    onPrimary = Color.White,
    secondary = Color(0xFF6650E0),
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
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp)
        ),
        content = content
    )
}
