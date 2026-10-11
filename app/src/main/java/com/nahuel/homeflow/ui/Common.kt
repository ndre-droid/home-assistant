package com.nahuel.homeflow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.roundToInt

// =====================================================================  Navigation

/** Tab icon: outline normally, filled + gentle pop when selected. 0 Home, 1 Flows, 2 Geräte, 3 Einstellungen. */
@Composable
fun TabIcon(ordinal: Int, selected: Boolean, tint: Color) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "tabScale"
    )
    val icon = when (ordinal) {
        0 -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
        1 -> if (selected) Icons.Filled.PlayArrow else Icons.Outlined.PlayArrow
        2 -> if (selected) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb
        else -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
    }
    Icon(icon, contentDescription = null, tint = tint,
        modifier = Modifier.size(22.dp).graphicsLayer { scaleX = scale; scaleY = scale })
}

@Composable
private fun navPillBg(): Color =
    if (IsDark) Color(0xFF1C1E21).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.96f)

/** Floating pill bottom bar (phone / cover screen portrait). Only the active tab shows its label. */
@Composable
fun SlimNavBar(labels: List<String>, current: Int, onSelect: (Int) -> Unit) {
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp, top = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .height(60.dp)
                .shadow(18.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .background(navPillBg())
                .border(1.dp, Color.White.copy(alpha = if (IsDark) 0.06f else 0.0f), CircleShape)
                .padding(6.dp)
        ) {
            labels.forEachIndexed { i, label ->
                val selected = i == current
                val tint = if (selected) Violet else TextSec
                val w by animateFloatAsState(if (selected) 1.7f else 1f, tween(220), label = "navW")
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(w)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (selected) Violet.copy(alpha = 0.14f) else Color.Transparent)
                        .semantics { contentDescription = label }
                        .bouncyClick { onSelect(i) }
                ) {
                    TabIcon(i, selected, tint)
                    if (selected) {
                        Spacer(Modifier.width(7.dp))
                        Text(label, fontSize = 13.sp, color = tint, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Vertical pill rail for wide windows (cover screen landscape, inner display). */
@Composable
fun NavRailPill(labels: List<String>, current: Int, onSelect: (Int) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .shadow(18.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(navPillBg())
            .border(1.dp, Color.White.copy(alpha = if (IsDark) 0.06f else 0.0f), CircleShape)
            .padding(6.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val selected = i == current
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (selected) Violet.copy(alpha = 0.14f) else Color.Transparent)
                    .semantics { contentDescription = label }
                    .bouncyClick { onSelect(i) }
            ) { TabIcon(i, selected, if (selected) Violet else TextSec) }
        }
    }
}

// =====================================================================  Type & cards

/** Big screen title. */
@Composable
fun ScreenTitle(text: String) {
    Text(text, color = TextPrim, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.9).sp)
}

/** Time-of-day greeting. */
fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..10 -> "Guten Morgen"
    in 11..17 -> "Guten Tag"
    else -> "Guten Abend"
}

/** Softly lit card: faint top highlight + subtle depth instead of borders. */
@Composable
fun GradientCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(if (IsDark) 10.dp else 4.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(CardGradient)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (IsDark) 0.07f else 0.5f), Color.Transparent)), shape)
            .animateContentSize(tween(200))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        content = content
    )
}

/** Section header above a card. */
@Composable
fun SectionTitle(text: String) {
    Text(
        text, color = TextPrim, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp,
        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
    )
}

@Composable
fun HintText(text: String) {
    Text(text, color = TextSec, fontSize = 13.sp, lineHeight = 19.sp)
}

// =====================================================================  Motion helpers

/** Springy press-scale. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 900f),
        label = "pressScale"
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/** One-liner animated click: press-scale without ripple. */
@Composable
fun Modifier.bouncyClick(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .pressScale(interaction)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/** Soft coloured light behind a screen, like a lamp glowing in a dark room. */
fun Modifier.ambientGlow(color: Color, cx: Float = 0.2f, cy: Float = -0.05f, radius: Float = 0.85f, strength: Float = 0.28f): Modifier =
    this.drawBehind {
        drawRect(
            Brush.radialGradient(
                colors = listOf(color.copy(alpha = strength), color.copy(alpha = strength * 0.25f), Color.Transparent),
                center = Offset(size.width * cx, size.height * cy),
                radius = size.maxDimension * radius
            )
        )
    }

// =====================================================================  Controls

/** Round accent play button with a soft glow. */
@Composable
fun PlayCircleButton(size: Int = 40, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(size.dp)
            .shadow(10.dp, CircleShape, ambientColor = accent, spotColor = accent)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(lerp(accent, Color.White, 0.35f), accent)))
            .bouncyClick(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.PlayArrow, contentDescription = "Jetzt ausführen",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size((size * 0.55).dp)
        )
    }
}

/** Glassy round icon button (header actions). */
@Composable
fun GlassIconButton(icon: ImageVector, contentDescription: String, size: Dp = 44.dp, tint: Color = TextPrim, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(TextPrim.copy(alpha = 0.10f), TextPrim.copy(alpha = 0.04f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (IsDark) 0.10f else 0.4f), Color.Transparent)), CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .bouncyClick(onClick)
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.44f)) }
}

/** 3D switch: inset track, white knob with shadow, soft glow when on. onColor = sits on a coloured card. */
@Composable
fun GlowSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, onColor: Boolean = false, label: String = "") {
    val accent = MaterialTheme.colorScheme.primary
    val knobX by animateDpAsState(if (checked) 21.dp else 3.dp, tween(160), label = "knob")
    val track by animateColorAsState(
        when {
            onColor -> Color.White.copy(alpha = if (checked) 0.32f else 0.18f)
            checked -> accent
            else -> if (IsDark) Color(0xFF222629) else Color(0xFFD9D7D1)
        }, tween(160), label = "track"
    )
    Box(
        modifier
            .size(width = 46.dp, height = 28.dp)
            .then(if (checked && !onColor) Modifier.shadow(8.dp, CircleShape, ambientColor = accent, spotColor = accent) else Modifier)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.White.copy(alpha = 0.10f))), CircleShape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { if (label.isNotEmpty()) contentDescription = label }
    ) {
        Box(
            Modifier
                .offset(x = knobX, y = 3.dp)
                .size(22.dp)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    if (checked || onColor) Brush.verticalGradient(listOf(Color.White, Color(0xFFF1EBE2)))
                    else Brush.verticalGradient(listOf(Color(0xFF9A9894), Color(0xFF6C6A66)))
                )
        )
    }
}

// =====================================================================  Light colour helpers

/** Gradient for a lamp/scene: light tint -> base -> warmer, deeper end. */
fun lightGradient(hex: String?): List<Color> {
    val base = hexColor(hex) ?: Color(0xFFFFB86A)
    return listOf(lerp(base, Color.White, 0.45f), base, lerp(base, Color(0xFFD9506A), 0.30f))
}

fun hexColor(hex: String?): Color? = runCatching {
    if (hex.isNullOrBlank()) null else Color(android.graphics.Color.parseColor(hex))
}.getOrNull()

/** Readable text colour on top of a gradient. */
fun inkOn(colors: List<Color>): Color {
    val l = colors.map { it.luminance() }.average()
    return if (l > 0.42) Color(0xFF1A1208) else Color.White
}

private val scenePalettes = listOf(
    listOf(Color(0xFFFFF1C9), Color(0xFFFFC46B), Color(0xFFFF9F4A)),
    listOf(Color(0xFFFFB36B), Color(0xFFF0705A), Color(0xFFB8456E)),
    listOf(Color(0xFFFFFFFF), Color(0xFFDFE9FF), Color(0xFFA9C6FF)),
    listOf(Color(0xFF6B5CFF), Color(0xFFA84BD0), Color(0xFFE0508A)),
    listOf(Color(0xFF4A50A8), Color(0xFF2A2350), Color(0xFF151530)),
    listOf(Color(0xFF9FE6C8), Color(0xFF46C2A0), Color(0xFF1E7F7A))
)

/** Stable fallback palette for anything without a colour of its own. */
fun paletteFor(key: String): List<Color> = scenePalettes[(key.hashCode() and 0x7fffffff) % scenePalettes.size]

// =====================================================================  Scenes & dimmers

/** Glossy scene orb (Hue scene gallery). Emoji sits on top so custom icons survive. */
@Composable
fun SceneOrb(
    colors: List<Color>, label: String, emoji: String = "", selected: Boolean = false,
    size: Dp = 56.dp, dimmed: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit
) {
    val glow = colors.getOrElse(1) { colors.first() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(size + 12.dp).graphicsLayer { alpha = if (dimmed) 0.45f else 1f }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size + 8.dp)
                .then(if (selected) Modifier.border(2.dp, Violet, CircleShape) else Modifier)
                .padding(4.dp)
                .shadow(12.dp, CircleShape, ambientColor = glow, spotColor = glow)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors))
                .drawBehind {
                    drawRect(Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.6f), Color.Transparent),
                        center = Offset(this.size.width * 0.32f, this.size.height * 0.26f),
                        radius = this.size.width * 0.36f
                    ))
                }
                .then(
                    if (onLongClick != null) Modifier.pointerInput(Unit) {
                        detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
                    } else Modifier.bouncyClick(onClick)
                )
        ) { if (emoji.isNotEmpty()) Text(emoji, fontSize = (size.value * 0.36f).sp) }
        Spacer(Modifier.height(7.dp))
        Text(
            label, fontSize = 12.sp, color = if (selected) TextPrim else TextSec,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Hue-style light card: the card IS the dimmer. Filled with the lamp's colour,
 * the unlit part darkens, a glowing handle marks the level. Swipe sideways to dim.
 */
@Composable
fun DimmerCard(
    title: String,
    subtitle: String,
    colors: List<Color>,
    on: Boolean,
    brightness: Int,
    onBrightness: (Int) -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 88.dp,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    var local by remember(brightness) { mutableFloatStateOf(brightness.toFloat()) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val shape = RoundedCornerShape(26.dp)
    val frac = (local / 100f).coerceIn(0.03f, 1f)
    val glow = colors.getOrElse(1) { colors.first() }
    val ink = if (on) inkOn(colors) else TextPrim
    val sub = if (on) ink.copy(alpha = 0.68f) else TextSec
    val onBrightnessNow by rememberUpdatedState(onBrightness)

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .then(if (selected) Modifier.border(2.dp, Violet, RoundedCornerShape(29.dp)).padding(3.dp) else Modifier)
            .shadow(if (on) 16.dp else 6.dp, shape, ambientColor = if (on) glow else Color.Black, spotColor = if (on) glow else Color.Black)
            .clip(shape)
            .background(if (on) Brush.linearGradient(colors) else CardGradient)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (on) 0.5f else 0.07f), Color.Transparent)), shape)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(on, brightness) {
                if (on) detectHorizontalDragGestures(
                    onDragEnd = { onBrightnessNow(local.roundToInt().coerceIn(1, 100)) },
                    onDragCancel = { local = brightness.toFloat() }
                ) { change, dx ->
                    change.consume()
                    local = (local + dx / widthPx * 100f).coerceIn(1f, 100f)
                }
            }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        if (on) {
            // gloss
            Box(Modifier.matchParentSize().drawBehind {
                drawRect(Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.36f), Color.Transparent),
                    center = Offset(size.width * 0.1f, 0f), radius = size.maxDimension * 0.6f
                ))
            })
            // unlit part, soft edge at the level
            Box(Modifier.matchParentSize().drawBehind {
                val x = size.width * frac
                val soft = 20.dp.toPx()
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color(0x00140A0C), 1f to Color(0x70140A0C),
                        startX = x - soft / 2, endX = x + soft / 2
                    ),
                    topLeft = Offset(x - soft / 2, 0f),
                    size = androidx.compose.ui.geometry.Size(size.width - x + soft / 2, size.height)
                )
            })
            // glowing handle
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((widthPx * frac).roundToInt() - 4, 0) }
                    .size(width = 3.dp, height = 26.dp)
                    .shadow(6.dp, CircleShape, ambientColor = Color.White, spotColor = Color.White)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 16.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (on) "$subtitle${if (subtitle.isNotEmpty()) " · " else ""}${local.roundToInt()} %" else "Aus",
                    color = sub, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            GlowSwitch(checked = on, onCheckedChange = onToggle, onColor = on, label = title)
        }
    }
}

/** Big brightness slider: inset track, glowing gradient fill, pearl knob. Tap or drag. */
@Composable
fun BigBrightnessSlider(value: Float, onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit, colors: List<Color>, modifier: Modifier = Modifier) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val frac = (value / 100f).coerceIn(0f, 1f)
    val glow = colors.getOrElse(1) { colors.first() }
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(TextPrim.copy(alpha = 0.05f))
            .border(1.dp, Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))), CircleShape)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { p -> change((p.x / widthPx * 100f).coerceIn(1f, 100f)); finished() }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = { finished() }) { pc, _ ->
                    pc.consume()
                    change((pc.position.x / widthPx * 100f).coerceIn(1f, 100f))
                }
            }
            .semantics { contentDescription = "Helligkeit ${value.roundToInt()} Prozent" }
    ) {
        Box(
            Modifier
                .padding(4.dp)
                .fillMaxHeight()
                .fillMaxWidth(frac.coerceAtLeast(0.16f))
                .shadow(14.dp, CircleShape, ambientColor = glow, spotColor = glow)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(colors))
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.Transparent), endY = size.height * 0.6f))
                }
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .size(40.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White, Color(0xFFF6F0E8), Color(0xFFE3DBD0))))
            )
        }
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
