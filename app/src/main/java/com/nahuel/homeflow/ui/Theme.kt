package com.nahuel.homeflow.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Hue-inspired dark palette: deep navy surfaces, violet/blue/pink accents.
val Bg = Color(0xFF0E1116)
val Surface1 = Color(0xFF161B26)
val Surface2 = Color(0xFF1E2433)
val Violet = Color(0xFF8B7CF7)
val Blue = Color(0xFF3D8BFD)
val Pink = Color(0xFFF062A6)
val Green = Color(0xFF34D399)
val TextPrim = Color(0xFFF2F3F7)
val TextSec = Color(0xFF8A93A6)

val CardGradient = Brush.linearGradient(listOf(Color(0xFF232A3D), Color(0xFF161B26)))
val AccentGradient = Brush.linearGradient(listOf(Violet, Pink))

private val scheme = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    secondary = Blue,
    background = Bg,
    surface = Surface1,
    surfaceVariant = Surface2,
    onBackground = TextPrim,
    onSurface = TextPrim,
    onSurfaceVariant = TextSec,
    outline = Color(0xFF2A3040),
    error = Color(0xFFF87171)
)

@Composable
fun HomeFlowTheme(content: @Composable () -> Unit) {
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
