package com.nahuel.homeflow.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nahuel.homeflow.R

// ---------------------------------------------------------------------------
// Modernist design system
// Flat, architectural, red-on-white. 2dp rules, zero corner radius, Archivo.
// Ground #f3f2f2 / ink #201e1d / one accent #ec3013, each with a 100-900 ramp.
// ---------------------------------------------------------------------------

// Neutral ramp (light: 100 = near-white ... 900 = ink)
private val N200 = Color(0xFFEBEAEA)
private val N300 = Color(0xFFDCDBDA)
private val N700 = Color(0xFF575453)
private val N900 = Color(0xFF201E1D)

// Accent ramp around #ec3013
private val A100 = Color(0xFFFDE7E3)
private val A200 = Color(0xFFFBCAC2)
private val A400 = Color(0xFFF4715C)
private val A500 = Color(0xFFEC3013)
private val A700 = Color(0xFFAB220D)
private val A900 = Color(0xFF5E1107)

private val Ground = Color(0xFFF3F2F2)

/** Archivo: headings and body both, per the design system. */
private val Archivo = FontFamily(
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_medium, FontWeight.Medium),
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_bold, FontWeight.Bold)
)

private val ModernistType = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Archivo),
        displayMedium = displayMedium.copy(fontFamily = Archivo),
        displaySmall = displaySmall.copy(fontFamily = Archivo),
        headlineLarge = headlineLarge.copy(fontFamily = Archivo),
        headlineMedium = headlineMedium.copy(fontFamily = Archivo),
        headlineSmall = headlineSmall.copy(fontFamily = Archivo),
        titleLarge = titleLarge.copy(fontFamily = Archivo, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Bold),
        titleSmall = titleSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.Bold),
        bodyLarge = bodyLarge.copy(fontFamily = Archivo),
        bodyMedium = bodyMedium.copy(fontFamily = Archivo),
        bodySmall = bodySmall.copy(fontFamily = Archivo),
        labelLarge = labelLarge.copy(fontFamily = Archivo, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.SemiBold)
    )
}

// Light: the canonical Modernist scheme, ink on ground with one red accent.
private val LightScheme = lightColorScheme(
    primary = A500,
    onPrimary = Color.White,
    primaryContainer = A100,
    onPrimaryContainer = A900,
    secondary = A700,
    onSecondary = Color.White,
    tertiary = N900,
    onTertiary = Ground,
    background = Ground,
    onBackground = N900,
    surface = Ground,
    onSurface = N900,
    surfaceVariant = N200,
    onSurfaceVariant = N700,
    outline = N900,          // rules are ink: structure, not decoration
    outlineVariant = N300,
    error = A700,
    onError = Color.White
)

// Dark: the same system inverted. Ground and ink swap, the accent stays red but
// steps one stop lighter so it still reads on a dark ground.
private val DarkScheme = darkColorScheme(
    primary = A500,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1A14),
    onPrimaryContainer = A200,
    secondary = A400,
    onSecondary = N900,
    tertiary = Ground,
    onTertiary = N900,
    background = N900,
    onBackground = Ground,
    surface = N900,
    onSurface = Ground,
    surfaceVariant = Color(0xFF2B2928),
    onSurfaceVariant = Color(0xFFA5A09E),
    outline = Ground,
    outlineVariant = Color(0xFF3A3736),
    error = A400,
    onError = N900
)

/** Theme mode preference. Stored in Config.themeMode. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

// ---- Modernist tokens (every screen references these) ----

/** Page ground. */
val Bg: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background

/** Ink: body text, rules, borders. */
val Ink: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onBackground

/** The single accent. */
val Accent: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

/** Accent text on a tint or an active tab: accent-700 in light, accent-400 in dark. */
val AccentText: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary

/** Accent-100 tint: selected rows, subtle fills. */
val AccentTint: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer

/** Every rule and border in the system. Always RuleWidth, always this colour. */
val Divider: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline

/** Block fill under branch headers and inert bars (neutral-200). */
val Fill: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant

/** Muted copy: captions, meta, section labels (neutral-700). */
val Muted: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** Inactive state dot, disabled glyph (neutral-400). */
val Faint: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant

/** The one rule weight in the system. */
val RuleWidth = 2.dp

/** Zero radius, everywhere. */
val Square = RectangleShape

/** Material's Shapes slots need a CornerBasedShape, so zero radius is spelled out. */
private val NoCorners = RoundedCornerShape(0.dp)

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
    // Custom accent (settings picker) overrides the red when dynamic colour is off.
    val accent = runCatching {
        if (accentHex.length == 7 && accentHex.startsWith("#") && !dynamicColor)
            Color(android.graphics.Color.parseColor(accentHex)) else null
    }.getOrNull()
    val scheme = if (accent != null) base.copy(
        primary = accent,
        secondary = accent,
        onPrimary = if (accentIsLight(accent)) N900 else Color.White
    ) else base
    MaterialTheme(
        colorScheme = scheme,
        typography = ModernistType,
        // Zero radius is a rule of the system, not a preference.
        shapes = Shapes(
            extraSmall = NoCorners,
            small = NoCorners,
            medium = NoCorners,
            large = NoCorners,
            extraLarge = NoCorners
        ),
        content = content
    )
}

private fun accentIsLight(c: Color): Boolean =
    (0.299f * c.red + 0.587f * c.green + 0.114f * c.blue) > 0.6f
