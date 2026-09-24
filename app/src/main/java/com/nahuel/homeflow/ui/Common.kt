package com.nahuel.homeflow.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Modernist components.
// Flat blocks, 2dp ink rules, zero radius, labels flush left, no motion accents.
// ---------------------------------------------------------------------------

/** The system's only divider: a 2dp ink rule. */
@Composable
fun Rule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(RuleWidth).background(Divider))
}

/** Screen top bar: title left, optional actions right, 2dp rule underneath. */
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(modifier.fillMaxWidth().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Text(
                title,
                color = Ink,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
        Rule()
    }
}

/** Bordered square that holds a glyph: device and trigger icons, action indices. */
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
            .background(fill ?: Bg)
            .border(RuleWidth, Divider),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Uppercase section label: 12sp, .06em tracking, muted. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = Muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.72.sp,
        modifier = modifier
    )
}

@Composable
fun HintText(text: String) {
    Text(text, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
}

/** Caption line under a control. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = modifier)
}

// ---- Buttons ---------------------------------------------------------------
// Labels are flush left in this system, never centred, even when the button is
// wider than its label. Block buttons keep that alignment too.

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
            .background(fill.copy(alpha = fill.alpha * alpha))
            .then(if (border) Modifier.border(RuleWidth, Divider.copy(alpha = alpha)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            label,
            color = textColor.copy(alpha = alpha),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Start
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

/** Outlined block: 2dp ink border on the ground. */
@Composable
fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) = FlatButtonBase(label, Bg, Ink, true, modifier, enabled, onClick)

/** Borderless, accent label: tertiary and destructive actions. */
@Composable
fun GhostButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color? = null,
    onClick: () -> Unit
) = FlatButtonBase(label, Color.Transparent, color ?: AccentText, false, modifier, enabled, onClick)

/** Square icon button: bordered box with a glyph, matching the row controls. */
@Composable
fun IconBoxButton(
    size: Dp = 34.dp,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .size(size)
            .background(Bg)
            .border(RuleWidth, Divider)
            .clickable(onClick = onClick)
            .then(
                if (contentDescription == null) Modifier
                else Modifier.semantics { this.contentDescription = contentDescription }
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

// ---- Toggle ----------------------------------------------------------------

/**
 * Flat switch: 36x18 track with a 2dp border and a 12x12 knob that slides
 * linearly. Accent-filled when on. No radius, no spring.
 */
@Composable
fun FlatToggle(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val knobX by animateDpAsState(
        targetValue = if (checked) 20.dp else 1.dp,
        animationSpec = tween(120, easing = LinearEasing),
        label = "knob"
    )
    Box(
        modifier
            .size(width = 36.dp, height = 18.dp)
            .background(if (checked) Accent else Bg)
            .border(RuleWidth, Divider)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Box(
            Modifier
                .padding(start = knobX, top = 1.dp)
                .size(12.dp)
                .background(if (checked) Bg else Ink)
        )
    }
}

// ---- Tags, fields, segmented control ---------------------------------------

/** Small square status dot: accent when live, neutral when not. */
@Composable
fun StatusDot(on: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).background(if (on) AccentText else Faint))
}

/** Labelled text field: uppercase label over a 2dp-bordered input. */
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
        SectionLabel(label)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(Bg)
                .border(RuleWidth, Divider)
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, color = Faint, fontSize = 13.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                minLines = minLines,
                textStyle = LocalTextStyle.current.copy(color = Ink, fontSize = 13.sp),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 3-way (or n-way) segmented control: one bordered strip, accent-filled选択. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    Row(modifier.fillMaxWidth().border(RuleWidth, Divider)) {
        options.forEachIndexed { i, opt ->
            val selected = i == selectedIndex
            if (i > 0) Box(Modifier.fillMaxHeight().width(RuleWidth).background(Divider))
            Box(
                Modifier
                    .weight(1f)
                    .background(if (selected) Accent else Bg)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    opt,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else Ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ---- Structure blocks ------------------------------------------------------

/** A bordered block: the system's stand-in for a card. Flat, 2dp, no radius. */
@Composable
fun FlatBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Bg)
            .border(RuleWidth, Divider)
            .padding(14.dp),
        content = content
    )
}

/** A screen section: padded body with a rule beneath it. */
@Composable
fun Section(
    label: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth().background(Bg)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (label != null) {
                SectionLabel(label)
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
        Rule()
    }
}

// ---- Dialogs ---------------------------------------------------------------

/**
 * Every dialog in the app: square, ground-filled, 2dp ink border, flush-left
 * title. Buttons are passed as composables so callers can use the flat set.
 */
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
        modifier = modifier.border(RuleWidth, Divider),
        shape = Square,
        containerColor = Bg,
        titleContentColor = Ink,
        textContentColor = Muted,
        tonalElevation = 0.dp,
        title = {
            Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}

// ---- Navigation ------------------------------------------------------------

/**
 * Flat bottom bar: three text tabs, uppercase 10sp, active tab in the accent,
 * 2dp rule on top. No icons, no motion.
 */
@Composable
fun FlatNavBar(labels: List<String>, current: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.background(Bg)) {
        Rule()
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { i, label ->
                val selected = i == current
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label.uppercase(),
                        color = if (selected) AccentText else Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp
                    )
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
