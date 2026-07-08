package com.nahuel.homeflow.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TabIcon(ordinal: Int) {
    val icon = when (ordinal) {
        0 -> Icons.Outlined.PlayArrow
        1 -> Icons.Outlined.Home
        else -> Icons.Outlined.Settings
    }
    Icon(icon, contentDescription = null)
}

/** Bold screen title (direction C: heavier hierarchy, 26/600, tight tracking). */
@Composable
fun ScreenTitle(text: String) {
    Text(
        text,
        color = TextPrim,
        fontSize = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp
    )
}

/** Flat surface card — single fill, thin hairline, 14dp radius. No nesting. */
@Composable
fun GradientCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.5.dp, Hairline, MaterialTheme.shapes.medium)
            .animateContentSize(tween(180))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content
    )
}

/** Quiet caps label — muted, fine tracking. */
@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = TextSec,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
    )
}

@Composable
fun HintText(text: String) {
    Text(text, color = TextSec, fontSize = 13.sp, lineHeight = 19.sp)
}

/**
 * Press-feedback scale (DESIGN.md 5.5): subtle 0.97 on press, ~140ms, no bounce.
 * Pass the same MutableInteractionSource you give to clickable().
 */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "pressScale"
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}


/**
 * Brief "Gespeichert ✓" confirmation chip. Set [visible]=true to show it; it fades out on its own.
 * Usage: hold a `var flash by remember { mutableStateOf(false) }`, set true on save, render SaveFlash(flash){ flash=false }.
 */
@Composable
fun SaveFlash(visible: Boolean, onHide: () -> Unit) {
    if (visible) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1500); onHide()
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("✓", color = Green, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text("Gespeichert", color = Green, fontSize = 13.sp)
        }
    }
}
