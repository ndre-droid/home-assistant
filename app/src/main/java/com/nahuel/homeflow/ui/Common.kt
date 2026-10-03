package com.nahuel.homeflow.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Calm components.
// Rounded cards on a near-black ground, filled insets instead of borders,
// pill toggles, 44dp touch targets. Names kept from the previous system so
// every screen picks up the new look without call-site changes.
// ---------------------------------------------------------------------------

/** Hairline separator. */
@Composable
fun Rule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(RuleWidth).background(Divider))
}

/** Screen header: large title left, optional leading slot and actions right. */
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
            .background(Bg)
            .padding(start = if (leading != null) 12.dp else 20.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(
            title,
            color = Ink,
            fontSize = if (leading == null) 28.sp else 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp,
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
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(fill ?: Fill),
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

// ---- Buttons ---------------------------------------------------------------

@Composable
private fun FlatButtonBase(
    label: String,
    fill: Color,
    textColor: Color,
    border: Boolean,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(FieldShape)
            .background(fill.copy(alpha = fill.alpha * alpha))
            .then(if (border) Modifier.border(RuleWidth, Divider, FieldShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = textColor.copy(alpha = alpha),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

/** Accent-filled call to action. */
@Composable
fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) = FlatButtonBase(label, Accent, MaterialTheme.colorScheme.onPrimary, false, modifier, enabled, onClick)

/** Filled inset button (surface 2). */
@Composable
fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) = FlatButtonBase(label, Fill, Ink, false, modifier, enabled, onClick)

/** Borderless text button: tertiary and destructive actions. */
@Composable
fun GhostButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color? = null,
    onClick: () -> Unit
) = FlatButtonBase(label, Color.Transparent, color ?: AccentText, false, modifier, enabled, onClick)

/** Round icon button on an inset fill. Never smaller than 36dp. */
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
    Box(
        modifier
            .size(s)
            .clip(CircleShape)
            .background(fill ?: Fill)
            .clickable(role = Role.Button, onClick = onClick)
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

/** Pill switch: 46x28 track, 20dp knob. Accent track when on. */
@Composable
fun FlatToggle(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val knobX by animateDpAsState(if (checked) 22.dp else 4.dp, tween(160), label = "knob")
    val track by animateColorAsState(if (checked) Accent else Divider, tween(160), label = "track")
    val knob by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.onPrimary else Muted, tween(160), label = "knobColor"
    )
    Box(
        modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(PillShape)
            .background(track.copy(alpha = if (enabled) 1f else 0.4f))
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    ) {
        Box(
            Modifier
                .padding(start = knobX, top = 4.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(knob)
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
            thumbColor = Ink,
            activeTrackColor = color,
            inactiveTrackColor = Fill,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent
        ),
        modifier = modifier.fillMaxWidth()
    )
}

// ---- Tags, fields, segmented control ---------------------------------------

/** Selectable pill chip. */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(PillShape)
            .background(if (selected) Accent else Color.Transparent)
            .border(RuleWidth, if (selected) Accent else Faint, PillShape)
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

/** Small round status dot: accent when live, neutral when not. */
@Composable
fun StatusDot(on: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(if (on) AccentText else Faint))
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

/** n-way segmented control: pill track, selected segment in ink. */
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

/** A rounded card. */
@Composable
fun FlatBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Card)
            .padding(16.dp),
        content = content
    )
}

/** A screen section: heading on the ground, content in a card below. */
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
                .clip(CardShape)
                .background(Card)
                .padding(16.dp),
            content = content
        )
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

/** Bottom bar: icon over label, active item in the accent, hairline on top. */
@Composable
fun FlatNavBar(items: List<NavItem>, current: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.background(Bg)) {
        Rule()
        Row(
            Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { i, item ->
                val selected = i == current
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                        .clip(TileShape)
                        .selectable(selected = selected, role = Role.Tab) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        item.icon, contentDescription = null,
                        tint = if (selected) Accent else Muted,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.label,
                        color = if (selected) Accent else Muted,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** Side rail for wide screens (Fold inner display, tablets). */
@Composable
fun FlatNavRail(items: List<NavItem>, current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxHeight().background(Bg)) {
        Column(
            Modifier
                .width(88.dp)
                .fillMaxHeight()
                .statusBarsPadding()
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEachIndexed { i, item ->
                val selected = i == current
                Column(
                    Modifier
                        .size(width = 72.dp, height = 64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) Card else Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        item.icon, contentDescription = null,
                        tint = if (selected) Accent else Muted,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.label,
                        color = if (selected) Accent else Muted,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Box(Modifier.fillMaxHeight().width(RuleWidth).background(Divider))
    }
}

/** Parses "#RRGGBB" to a Color, null when invalid. */
fun hexColor(hex: String?): Color? = runCatching {
    if (hex.isNullOrBlank()) null else Color(android.graphics.Color.parseColor(hex))
}.getOrNull()
