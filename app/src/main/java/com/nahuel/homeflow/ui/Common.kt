package com.nahuel.homeflow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar

/** Bottom-bar icon, YouTube-style: outline normally, filled + gentle pop when selected. */
@Composable
fun TabIcon(ordinal: Int, selected: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 800f),
        label = "tabScale"
    )
    val icon = when (ordinal) {
        0 -> if (selected) Icons.Filled.PlayArrow else Icons.Outlined.PlayArrow
        1 -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
        else -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
    }
    Icon(icon, contentDescription = null,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
}

/** Big bold screen title (Spotify weight). */
@Composable
fun ScreenTitle(text: String) {
    Text(
        text,
        color = TextPrim,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    )
}

/** Time-of-day greeting, Spotify-home style. */
fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..10 -> "Guten Morgen"
    in 11..17 -> "Guten Tag"
    else -> "Guten Abend"
}

/** Flat borderless card — separation by surface step, not by lines (Spotify/YouTube). */
@Composable
fun GradientCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .animateContentSize(tween(200))
            .padding(16.dp),
        content = content
    )
}

/** Section header — bold sentence case like Spotify/YouTube section rows. */
@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = TextPrim,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
    )
}

@Composable
fun HintText(text: String) {
    Text(text, color = TextSec, fontSize = 13.sp, lineHeight = 19.sp)
}

/** Springy press-scale, like the big apps: 0.94 with a soft spring back. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "pressScale"
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/** One-liner animated click: press-scale without ripple, like Spotify rows. */
@Composable
fun Modifier.bouncyClick(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .pressScale(interaction)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/** Round accent play button (Spotify): filled circle, dark icon, springy press. */
@Composable
fun PlayCircleButton(size: Int = 40, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .bouncyClick(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.PlayArrow, contentDescription = "Jetzt ausführen",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size((size * 0.6).dp)
        )
    }
}

/** Brief "Gespeichert ✓" confirmation; fades in/out on its own. */
@Composable
fun SaveFlash(visible: Boolean, onHide: () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(150)), exit = fadeOut(tween(400))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✓", color = Green, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text("Gespeichert", color = Green, fontSize = 13.sp)
        }
    }
    if (visible) {
        LaunchedEffect(Unit) { delay(1500); onHide() }
    }
}
