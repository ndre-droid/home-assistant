package com.nahuel.homeflow.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Glow components.
// Softly lit cards on a near-black ground, colour comes from the lights,
// 3D switches and pearl buttons, 44dp touch targets. Names and signatures
// are kept from the calm system so every screen picks up the new look
// without call-site changes.
// ---------------------------------------------------------------------------

/** Hairline separator. */
@Composable
fun Rule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(RuleWidth).background(Divider))
}

/** Screen header: large title left, optional leading slot and actions right. Transparent so screen glow shows. */
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = if (leading != null) 12.dp else 20.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(
            title,
            color = Ink,
            fontSize = if (leading == null) 32.sp else 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = if (leading == null) (-0.9).sp else (-0.4).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

/** Rounded inset tile that holds a glyph or icon. */
@Composable
fun IconBox(
    size: Dp = 36.dp,
    modifier: Modifier = Modifier,
    fill: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(fill ?: Fill)
            .border(RuleWidth, EdgeLight, shape),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Vector icon in a rounded tile. */
@Composable
fun IconTile(icon: ImageVector, size: Dp = 44.dp, tint: Color = Ink, fill: Color? = null) {
    IconBox(size, fill = fill) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

/** Small label above fields and groups. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = modifier)
}

/** Section heading on the ground (Szenen, Räume, Lichter …). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

@Composable
fun HintText(text: String) {
    Text(text, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
}

/** Caption line under a control. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = modifier)
}

// ---- Motion & light helpers -------------------------------------------------

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

/** Soft coloured light behind a screen, like a lamp in a dark room. Put it after background(). */
fun Modifier.ambientGlow(color: Color, cx: Float = 0.2f, cy: Float = -0.05f, radius: Float = 0.85f, strength: Float = 0.26f): Modifier =
    this.drawBehind {
        drawRect(
            Brush.radialGradient(
                colors = listOf(color.copy(alpha = strength), color.copy(alpha = strength * 0.25f), Color.Transparent),
                center = Offset(size.width * cx, size.height * cy),
                radius = size.maxDimension * radius
            )
        )
    }

/** Lit card surface: soft shadow, gradient fill, edge of light on top. */
@Composable
fun Modifier.litCard(shape: androidx.compose.ui.graphics.Shape = CardShape, elevation: Dp = 10.dp): Modifier =
    this
        .shadow(if (IsDark) elevation else elevation / 2, shape, ambientColor = Color.Black, spotColor = Color.Black)
        .clip(shape)
        .background(CardBrush)
        .border(RuleWidth, EdgeLight, shape)

/** Gradient for a lamp/scene colour: light tint -> base -> warmer, deeper end. */
fun lightGradient(hex: String?): List<Color> {
    val base = hexColor(hex) ?: Color(0xFFFFB86A)
    return listOf(lerp(base, Color.White, 0.45f), base, lerp(base, Color(0xFFD9506A), 0.30f))
}

/** Gradient through several real light colours (a room with different lamps). */
fun lightsGradient(colors: List<Color>): List<Color> = when {
    colors.isEmpty() -> lightGradient(null)
    colors.size == 1 -> listOf(lerp(colors[0], Color.White, 0.45f), colors[0], lerp(colors[0], Color(0xFFD9506A), 0.30f))
    else -> listOf(lerp(colors[0], Color.White, 0.35f), colors[0], colors[1])
}

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

// ---- Buttons ---------------------------------------------------------------

@Composable
private fun FlatButtonBase(
    label: String,
    fill: Brush?,
    textColor: Color,
    glow: Color?,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .graphicsLayer { this.alpha = alpha }
            .pressScale(interaction)
            .then(if (glow != null && enabled) Modifier.shadow(10.dp, PillShape, ambientColor = glow, spotColor = glow) else Modifier)
            .clip(PillShape)
            .then(if (fill != null) Modifier.background(fill).border(RuleWidth, EdgeLight, PillShape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** Accent call to action: glowing pill. */
@Composable
fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val a = Accent
    FlatButtonBase(
        label, Brush.radialGradient(listOf(lerp(a, Color.White, 0.3f), a), radius = 420f),
        MaterialTheme.colorScheme.onPrimary, a, modifier, enabled, onClick
    )
}

/** Glassy secondary pill. */
@Composable
fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) = FlatButtonBase(
    label, Brush.verticalGradient(listOf(Ink.copy(alpha = 0.10f), Ink.copy(alpha = 0.05f))),
    Ink, null, modifier, enabled, onClick
)

/** Borderless text button: tertiary and destructive actions. */
@Composable
fun GhostButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color? = null,
    onClick: () -> Unit
) = FlatButtonBase(label, null, color ?: AccentText, null, modifier, enabled, onClick)

/** Round glassy icon button. Never smaller than 36dp. A [fill] makes it solid (e.g. accent play). */
@Composable
fun IconBoxButton(
    size: Dp = 40.dp,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    fill: Color? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val s = if (size < 36.dp) 36.dp else size
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(s)
            .pressScale(interaction)
            .then(if (fill != null) Modifier.shadow(10.dp, CircleShape, ambientColor = fill, spotColor = fill) else Modifier)
            .clip(CircleShape)
            .background(
                if (fill != null) Brush.radialGradient(listOf(lerp(fill, Color.White, 0.3f), fill))
                else Brush.verticalGradient(listOf(Ink.copy(alpha = 0.10f), Ink.copy(alpha = 0.04f)))
            )
            .border(RuleWidth, EdgeLight, CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .then(
                if (contentDescription == null) Modifier
                else Modifier.semantics { this.contentDescription = contentDescription }
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Round icon button with a vector icon. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp = 44.dp,
    fill: Color? = null,
    tint: Color = Ink,
    onClick: () -> Unit
) {
    IconBoxButton(size, contentDescription, fill = fill, onClick = onClick) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

// ---- Toggle ----------------------------------------------------------------

/** 3D pill switch: inset track, white knob with shadow, soft glow when on. */
@Composable
fun FlatToggle(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onColor: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    val accent = Accent
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
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .then(if (checked && !onColor && enabled) Modifier.shadow(8.dp, PillShape, ambientColor = accent, spotColor = accent) else Modifier)
            .clip(PillShape)
            .background(track)
            .border(RuleWidth, Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.White.copy(alpha = 0.10f))), PillShape)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
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

// ---- Slider ----------------------------------------------------------------

/**
 * 0..100 level slider (brightness, volume). Moves locally while dragging and
 * reports the final value once via [onCommit], so devices aren't flooded.
 */
@Composable
fun LevelSlider(
    value: Int,
    modifier: Modifier = Modifier,
    color: Color = Ink,
    enabled: Boolean = true,
    onCommit: (Int) -> Unit
) {
    var local by remember { mutableFloatStateOf(value.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!dragging) local = value.toFloat() }
    Slider(
        value = local,
        onValueChange = { local = it; dragging = true },
        onValueChangeFinished = { dragging = false; onCommit(local.toInt()) },
        valueRange = 0f..100f,
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = color,
            inactiveTrackColor = Fill,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** Big brightness slider: inset track, glowing gradient fill, pearl knob. Tap or drag; commits on release. */
@Composable
fun BigBrightnessSlider(value: Int, colors: List<Color>, modifier: Modifier = Modifier, onCommit: (Int) -> Unit) {
    var local by remember { mutableFloatStateOf(value.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!dragging) local = value.toFloat() }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val commit by rememberUpdatedState(onCommit)
    val frac = (local / 100f).coerceIn(0f, 1f)
    val glow = colors.getOrElse(1) { colors.first() }
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(PillShape)
            .background(Ink.copy(alpha = 0.05f))
            .border(RuleWidth, Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))), PillShape)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    local = (p.x / widthPx * 100f).coerceIn(1f, 100f); commit(local.roundToInt())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false; commit(local.roundToInt()) },
                    onDragCancel = { dragging = false }
                ) { pc, _ ->
                    pc.consume()
                    local = (pc.position.x / widthPx * 100f).coerceIn(1f, 100f)
                }
            }
            .semantics { contentDescription = "Helligkeit ${local.roundToInt()} Prozent" }
    ) {
        Box(
            Modifier
                .padding(4.dp)
                .fillMaxHeight()
                .fillMaxWidth(frac.coerceAtLeast(0.16f))
                .shadow(14.dp, PillShape, ambientColor = glow, spotColor = glow)
                .clip(PillShape)
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

// ---- Tags, fields, segmented control ---------------------------------------

/** Selectable pill chip. Selected glows softly in the accent. */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onClick: () -> Unit
) {
    val a = Accent
    Row(
        modifier
            .defaultMinSize(minHeight = 36.dp)
            .then(if (selected) Modifier.shadow(8.dp, PillShape, ambientColor = a, spotColor = a) else Modifier)
            .clip(PillShape)
            .background(if (selected) Brush.radialGradient(listOf(lerp(a, Color.White, 0.25f), a), radius = 300f) else SolidColor(Color.Transparent))
            .border(RuleWidth, if (selected) a else Faint, PillShape)
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else Ink,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                trailing,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else Muted,
                fontSize = 13.sp
            )
        }
    }
}

/** Small round status dot: glowing accent when live, neutral when not. */
@Composable
fun StatusDot(on: Boolean, modifier: Modifier = Modifier) {
    val a = AccentText
    Box(
        modifier.size(8.dp)
            .then(if (on) Modifier.shadow(4.dp, CircleShape, ambientColor = a, spotColor = a) else Modifier)
            .clip(CircleShape).background(if (on) a else Faint)
    )
}

/** Labelled text field on an inset fill. */
@Composable
fun FlatField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    onValueChange: (String) -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            SectionLabel(label)
            Spacer(Modifier.height(6.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(FieldShape)
                .background(Fill)
                .border(RuleWidth, Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.White.copy(alpha = 0.04f))), FieldShape)
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, color = Muted, fontSize = 15.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                minLines = minLines,
                textStyle = LocalTextStyle.current.copy(color = Ink, fontSize = 15.sp),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** n-way segmented control: inset pill track, selected segment raised in ink. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(Fill)
            .padding(4.dp)
    ) {
        options.forEachIndexed { i, opt ->
            val selected = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .then(if (selected) Modifier.shadow(4.dp, PillShape) else Modifier)
                    .clip(PillShape)
                    .background(if (selected) Ink else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab) { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    opt,
                    color = if (selected) Bg else Ink,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

// ---- Structure blocks ------------------------------------------------------

/** A softly lit rounded card. */
@Composable
fun FlatBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .litCard()
            .padding(16.dp),
        content = content
    )
}

/** A screen section: heading on the ground, content in a lit card below. */
@Composable
fun Section(
    label: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (label != null) {
            SectionTitle(label, Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(10.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .litCard()
                .padding(16.dp),
            content = content
        )
    }
}

// ---- Scenes & lights --------------------------------------------------------

/** Glossy scene orb (Hue scene gallery). Icon or emoji sits on top. */
@Composable
fun SceneOrb(
    colors: List<Color>,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    emoji: String = "",
    selected: Boolean = false,
    size: Dp = 56.dp,
    onClick: () -> Unit
) {
    val glow = colors.getOrElse(1) { colors.first() }
    val ink = inkOn(colors)
    val interaction = remember { MutableInteractionSource() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.width(size + 16.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size + 8.dp)
                .pressScale(interaction)
                .then(if (selected) Modifier.border(2.dp, Accent, CircleShape) else Modifier)
                .padding(4.dp)
                .shadow(12.dp, CircleShape, ambientColor = glow, spotColor = glow)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors))
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(this.size.width * 0.32f, this.size.height * 0.26f),
                            radius = this.size.width * 0.38f
                        )
                    )
                }
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label }
        ) {
            when {
                emoji.isNotEmpty() -> Text(emoji, fontSize = (size.value * 0.34f).sp)
                icon != null -> Icon(icon, null, tint = ink.copy(alpha = 0.85f), modifier = Modifier.size(size * 0.38f))
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            label, fontSize = 12.sp, color = if (selected) Ink else Muted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/** Glowing colour dot for a lamp (off = hollow ring). */
@Composable
fun LightOrb(color: Color, on: Boolean, size: Dp = 26.dp) {
    if (on) {
        Box(
            Modifier
                .size(size)
                .shadow(10.dp, CircleShape, ambientColor = color, spotColor = color)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(lerp(color, Color.White, 0.75f), color)))
        )
    } else {
        Box(Modifier.size(size).clip(CircleShape).border(1.5.dp, Faint, CircleShape))
    }
}

/**
 * Hue-style card that is its own dimmer: filled with the lights' colours, the unlit
 * part darkens, a glowing handle marks the level. Swipe sideways to dim, tap to open.
 * [extra] adds lines (media status) under the title.
 */
@Composable
fun DimmerCard(
    title: String,
    on: Boolean,
    brightness: Int,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    selected: Boolean = false,
    canToggle: Boolean = true,
    minHeight: Dp = 88.dp,
    onBrightness: ((Int) -> Unit)? = null,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    extra: @Composable ColumnScope.(ink: Color) -> Unit = {}
) {
    var local by remember { mutableFloatStateOf(brightness.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(brightness) { if (!dragging) local = brightness.toFloat() }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val commit by rememberUpdatedState(onBrightness)
    val shape = RoundedCornerShape(26.dp)
    val frac = (local / 100f).coerceIn(0.03f, 1f)
    val glow = colors.getOrElse(1) { colors.first() }
    val ink = if (on) inkOn(colors) else Ink
    val sub = if (on) ink.copy(alpha = 0.7f) else Muted

    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .then(if (selected) Modifier.border(2.dp, Accent, RoundedCornerShape(29.dp)).padding(3.dp) else Modifier)
            .shadow(if (on) 16.dp else 8.dp, shape, ambientColor = if (on) glow else Color.Black, spotColor = if (on) glow else Color.Black)
            .clip(shape)
            .background(if (on) Brush.linearGradient(colors) else CardBrush)
            .border(RuleWidth, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (on) 0.5f else 0.08f), Color.Transparent)), shape)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(on, onBrightness != null) {
                if (on && commit != null) detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false; commit?.invoke(local.roundToInt().coerceIn(1, 100)) },
                    onDragCancel = { dragging = false }
                ) { change, dx ->
                    change.consume()
                    local = (local + dx / widthPx * 100f).coerceIn(1f, 100f)
                }
            }
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        if (on) {
            Box(Modifier.matchParentSize().drawBehind {
                drawRect(Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.34f), Color.Transparent),
                    center = Offset(size.width * 0.1f, 0f), radius = size.maxDimension * 0.6f
                ))
                // unlit part with a soft edge at the level
                val x = size.width * frac
                val soft = 20.dp.toPx()
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color(0x00140A0C), 1f to Color(0x70140A0C),
                        startX = x - soft / 2, endX = x + soft / 2
                    ),
                    topLeft = Offset(x - soft / 2, 0f),
                    size = Size(size.width - x + soft / 2, size.height)
                )
            })
            if (onBrightness != null) {
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
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = minHeight).padding(start = 20.dp, end = 16.dp)
        ) {
            Column(Modifier.weight(1f).padding(vertical = 14.dp)) {
                Text(title, color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotEmpty() || on) {
                    Text(
                        if (on && onBrightness != null) listOf(subtitle, "${local.roundToInt()} %").filter { it.isNotEmpty() }.joinToString(" · ")
                        else subtitle,
                        color = sub, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                extra(sub)
            }
            Spacer(Modifier.width(10.dp))
            FlatToggle(on, enabled = canToggle, onColor = on) { onToggle(it) }
        }
    }
}

// ---- Dialogs ---------------------------------------------------------------

/** Every dialog in the app: rounded card surface, no border. */
@Composable
fun FlatDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: (@Composable () -> Unit)? = null,
    text: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        containerColor = Card,
        titleContentColor = Ink,
        textContentColor = Muted,
        tonalElevation = 0.dp,
        title = {
            Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        },
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}

// ---- Navigation ------------------------------------------------------------

data class NavItem(val label: String, val icon: ImageVector)

@Composable
private fun navPillBg(): Color = if (IsDark) Color(0xFF1C1E21).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.97f)

/** Floating pill bottom bar. Only the active tab shows its label. */
@Composable
fun FlatNavBar(items: List<NavItem>, current: Int, onSelect: (Int) -> Unit) {
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 6.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(60.dp)
                .shadow(18.dp, PillShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(PillShape)
                .background(navPillBg())
                .border(RuleWidth, EdgeLight, PillShape)
                .padding(6.dp)
        ) {
            items.forEachIndexed { i, item ->
                val selected = i == current
                val w by animateFloatAsState(if (selected) 1.9f else 1f, tween(220), label = "navW")
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(w)
                        .fillMaxHeight()
                        .clip(PillShape)
                        .background(if (selected) Accent.copy(alpha = 0.15f) else Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab) { onSelect(i) }
                        .semantics { contentDescription = item.label }
                ) {
                    Icon(item.icon, contentDescription = null, tint = if (selected) Accent else Muted, modifier = Modifier.size(22.dp))
                    if (selected) {
                        Spacer(Modifier.width(7.dp))
                        Text(item.label, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Vertical pill rail for wide windows (cover screen landscape, Fold inner display). */
@Composable
fun FlatNavRail(items: List<NavItem>, current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, centered: Boolean = false) {
    Box(
        modifier.fillMaxHeight().width(84.dp).systemBarsPadding().padding(vertical = 16.dp),
        contentAlignment = if (centered) Alignment.Center else Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .shadow(18.dp, PillShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(PillShape)
                .background(navPillBg())
                .border(RuleWidth, EdgeLight, PillShape)
                .padding(6.dp)
        ) {
            items.forEachIndexed { i, item ->
                val selected = i == current
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (selected) Accent.copy(alpha = 0.15f) else Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab) { onSelect(i) }
                        .semantics { contentDescription = item.label }
                ) {
                    Icon(item.icon, contentDescription = null, tint = if (selected) Accent else Muted, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/** Parses "#RRGGBB" to a Color, null when invalid. */
fun hexColor(hex: String?): Color? = runCatching {
    if (hex.isNullOrBlank()) null else Color(android.graphics.Color.parseColor(hex))
}.getOrNull()
