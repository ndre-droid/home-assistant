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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nahuel.homeflow.R

// ---------------------------------------------------------------------------
// Calm dark design system
// Near-black ground (OLED friendly), two layered surfaces, rounded cards,
// one warm accent (the colour of light). Archivo throughout.
// A light variant with the same structure exists for "Hell" in settings.
// ---------------------------------------------------------------------------

// Dark
private val Ground = Color(0xFF0C0D0E)
private val Surface1 = Color(0xFF16181A)
private val Surface2 = Color(0xFF1F2225)
private val Line = Color(0xFF23272A)
private val InkD = Color(0xFFEDEBE7)
private val MutedD = Color(0xFFA3A19C)
private val FaintD = Color(0xFF5C5F62)
private val Amber = Color(0xFFF0B45E)
private val OnAmber = Color(0xFF17130A)
private val ErrorD = Color(0xFFFF8A7A)

// Light
private val GroundL = Color(0xFFF4F3F0)
private val Surface1L = Color(0xFFFFFFFF)
private val Surface2L = Color(0xFFECEAE6)
private val LineL = Color(0xFFE2E0DB)
private val InkL = Color(0xFF1A1B1C)
private val MutedL = Color(0xFF5F5D59)
private val FaintL = Color(0xFFB5B2AC)
private val AmberL = Color(0xFFA35F0C)
private val ErrorL = Color(0xFFB3261E)

/** Archivo: headings and body both. */
private val Archivo = FontFamily(
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_medium, FontWeight.Medium),
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_bold, FontWeight.Bold)
)

private val CalmType = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Archivo),
        displayMedium = displayMedium.copy(fontFamily = Archivo),
        displaySmall = displaySmall.copy(fontFamily = Archivo),
        headlineLarge = headlineLarge.copy(fontFamily = Archivo, fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = Archivo, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontFamily = Archivo),
        bodyMedium = bodyMedium.copy(fontFamily = Archivo),
        bodySmall = bodySmall.copy(fontFamily = Archivo),
        labelLarge = labelLarge.copy(fontFamily = Archivo, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.Medium)
    )
}

private val DarkScheme = darkColorScheme(
    primary = Amber,
    onPrimary = OnAmber,
    primaryContainer = Color(0xFF2B2216),
    onPrimaryContainer = Amber,
    secondary = Amber,
    onSecondary = OnAmber,
    tertiary = InkD,
    onTertiary = Ground,
    background = Ground,
    onBackground = InkD,
    surface = Surface1,
    onSurface = InkD,
    surfaceVariant = Surface2,
    onSurfaceVariant = MutedD,
    surfaceContainerLowest = Ground,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface2,
    outline = Line,
    outlineVariant = FaintD,
    error = ErrorD,
    onError = Ground
)

private val LightScheme = lightColorScheme(
    primary = AmberL,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6E7D2),
    onPrimaryContainer = Color(0xFF5A3405),
    secondary = AmberL,
    onSecondary = Color.White,
    tertiary = InkL,
    onTertiary = GroundL,
    background = GroundL,
    onBackground = InkL,
    surface = Surface1L,
    onSurface = InkL,
    surfaceVariant = Surface2L,
    onSurfaceVariant = MutedL,
    surfaceContainerLowest = GroundL,
    surfaceContainerLow = Surface1L,
    surfaceContainer = Surface1L,
    surfaceContainerHigh = Surface2L,
    surfaceContainerHighest = Surface2L,
    outline = LineL,
    outlineVariant = FaintL,
    error = ErrorL,
    onError = Color.White
)

/** Theme mode preference. Stored in Config.themeMode. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

// ---- Tokens (every screen references these) ----

/** Page ground. */
val Bg: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background

/** Card surface, one step above the ground. */
val Card: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface

/** Inset fill inside cards: fields, icon tiles, secondary buttons (surface 2). */
val Fill: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant

/** Primary text. */
val Ink: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onBackground

/** The single accent. */
val Accent: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

/** Accent used as text on ground or cards. */
val AccentText: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary

/** Accent tint: selected rows, banners. */
val AccentTint: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer

/** Hairline separators. */
val Divider: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline

/** Muted copy: captions, meta. */
val Muted: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** Inactive dots, disabled glyphs, placeholder rings. */
val Faint: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant

/** Errors and failed runs. */
val Danger: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error

/** Hairline weight. */
val RuleWidth = 1.dp

/** Kept for call sites that need a hard edge. */
val Square = RectangleShape

val CardShape = RoundedCornerShape(22.dp)
val TileShape = RoundedCornerShape(16.dp)
val FieldShape = RoundedCornerShape(14.dp)
val PillShape = RoundedCornerShape(percent = 50)

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
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Wallpaper accent, but keep our calm grounds and surfaces.
            val dyn = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            val b = if (dark) DarkScheme else LightScheme
            b.copy(
                primary = dyn.primary, onPrimary = dyn.onPrimary,
                secondary = dyn.primary, onSecondary = dyn.onPrimary,
                primaryContainer = dyn.primary.copy(alpha = 0.16f).compositeOver(b.background),
                onPrimaryContainer = dyn.primary
            )
        }
        dark -> DarkScheme
        else -> LightScheme
    }
    // Custom accent (settings picker) overrides the amber when dynamic colour is off.
    val accent = runCatching {
        if (accentHex.length == 7 && accentHex.startsWith("#") && !dynamicColor)
            Color(android.graphics.Color.parseColor(accentHex)) else null
    }.getOrNull()
    val scheme = if (accent != null) {
        val on = if (accentIsLight(accent)) OnAmber else Color.White
        base.copy(
            primary = accent, onPrimary = on,
            secondary = accent, onSecondary = on,
            primaryContainer = accent.copy(alpha = 0.16f).compositeOver(base.background),
            onPrimaryContainer = accent
        )
    } else base
    MaterialTheme(
        colorScheme = scheme,
        typography = CalmType,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}

private fun accentIsLight(c: Color): Boolean =
    (0.299f * c.red + 0.587f * c.green + 0.114f * c.blue) > 0.6f
