package com.nahuel.homeflow.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** Flat surface card — finer hairline, single clean fill (M3). */
@Composable
fun GradientCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.5.dp, Hairline, MaterialTheme.shapes.medium)
            .animateContentSize(tween(200))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content
    )
}

/** Quiet, uppercase section label — fine tracking, secondary color. */
@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = TextSec,
        fontSize = 11.sp,
        letterSpacing = 1.4.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
    )
}

@Composable
fun HintText(text: String) {
    Text(text, color = TextSec, fontSize = 13.sp, lineHeight = 19.sp)
}
