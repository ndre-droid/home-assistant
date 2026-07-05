package com.nahuel.homeflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
        0 -> Icons.Filled.PlayArrow
        1 -> Icons.Filled.Home
        else -> Icons.Filled.Settings
    }
    Icon(icon, contentDescription = null)
}

/** Gradient card container used across screens (Hue-style). */
@Composable
fun GradientCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(CardGradient)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = TextPrim,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
    )
}

@Composable
fun HintText(text: String) {
    Text(text, color = TextSec, fontSize = 13.sp, lineHeight = 18.sp)
}
