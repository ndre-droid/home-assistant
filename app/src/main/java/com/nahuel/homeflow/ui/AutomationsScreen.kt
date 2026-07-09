package com.nahuel.homeflow.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuel.homeflow.data.Routine
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.data.TriggerType
import com.nahuel.homeflow.engine.RoutineEngine
import com.nahuel.homeflow.engine.TriggerService

@Composable
fun AutomationsScreen(modifier: Modifier = Modifier, onEdit: (String) -> Unit, onCaptureScene: () -> Unit) {
    val showAddChooser = remember { androidx.compose.runtime.mutableStateOf(false) }
    val showTemplates = remember { androidx.compose.runtime.mutableStateOf(false) }
    val showHistory = remember { androidx.compose.runtime.mutableStateOf(false) }
    val routines by Store.routines.collectAsState()
    val ctx = LocalContext.current

    Box(modifier.fillMaxSize().statusBarsPadding()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(1) }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            ScreenTitle(greeting())
                            Text("Deine Automationen", color = TextSec, fontSize = 13.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { showHistory.value = true }) {
                            Icon(Icons.Outlined.History, "Verlauf", tint = TextSec)
                        }
                    }
                    if (routines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Tippen = ausführen · Halten = an/aus · ✎ = bearbeiten",
                            color = TextSec, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            if (routines.isEmpty()) {
                item(span = { GridItemSpan(1) }) {
                    GradientCard {
                        Text("Noch keine Automationen", color = TextPrim, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        HintText("Tippe auf +, um eine Automation mit Auslöser, Bedingungen und Aktionen zu bauen, oder nimm den aktuellen Zustand als Szene auf.")
                    }
                }
            }
            items(routines, key = { it.id }) { r ->
                RoutineTile(
                    r = r,
                    onRun = { RoutineEngine.runAsync(ctx, r) },
                    onEdit = { onEdit(r.id) },
                    onToggle = {
                        Store.setEnabled(r.id, !r.enabled)
                        TriggerService.sync(ctx)
                    }
                )
            }
            item(span = { GridItemSpan(1) }) { Spacer(Modifier.height(88.dp)) }
        }

        ExtendedFloatingActionButton(
            onClick = { showAddChooser.value = true },
            containerColor = Violet,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Neu") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        )

        if (showAddChooser.value) {
            AlertDialog(
                onDismissRequest = { showAddChooser.value = false },
                containerColor = Surface1,
                title = { Text("Was möchtest du anlegen?", color = TextPrim) },
                text = {
                    Column {
                        TextButton(onClick = { showAddChooser.value = false; onEdit("") }) {
                            Text("Neue Automation", color = Violet)
                        }
                        TextButton(onClick = { showAddChooser.value = false; onCaptureScene() }) {
                            Text("Szene aus aktuellem Zustand aufnehmen", color = Blue)
                        }
                        TextButton(onClick = { showAddChooser.value = false; showTemplates.value = true }) {
                            Text("Vorlage verwenden (Filmabend, Aufwachen, ...)", color = Green)
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showAddChooser.value = false }) { Text("Abbrechen", color = TextSec) } }
            )
        }

        if (showTemplates.value) {
            AlertDialog(
                onDismissRequest = { showTemplates.value = false },
                containerColor = Surface1,
                title = { Text("Vorlage wählen", color = TextPrim) },
                text = {
                    Column {
                        com.nahuel.homeflow.data.Templates.all.forEach { tpl ->
                            TextButton(onClick = {
                                Store.saveRoutine(tpl.build())
                                showTemplates.value = false
                            }) {
                                Column {
                                    Text(tpl.title, color = Violet, fontWeight = FontWeight.SemiBold)
                                    Text(tpl.description, color = TextSec, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showTemplates.value = false }) { Text("Schließen", color = TextSec) } }
            )
        }

        if (showHistory.value) {
            val history by Store.history.collectAsState()
            AlertDialog(
                onDismissRequest = { showHistory.value = false },
                containerColor = Surface1,
                title = { Text("Verlauf", color = TextPrim) },
                text = {
                    if (history.isEmpty()) Text("Noch nichts ausgeführt.", color = TextSec)
                    else LazyColumn(Modifier.height(320.dp)) {
                        items(history, key = { it.timestamp }) { h ->
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (h.ok) "✓" else "✕", color = if (h.ok) Green else Pink, modifier = Modifier.width(20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(h.routineName, color = TextPrim, fontSize = 14.sp)
                                    Text(
                                        android.text.format.DateFormat.format("dd.MM. HH:mm", h.timestamp).toString() +
                                            (if (h.detail.isNotEmpty()) "  ·  ${h.detail}" else ""),
                                        color = TextSec, fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { Store.clearHistory() }) { Text("Leeren", color = Pink) } },
                dismissButton = { TextButton(onClick = { showHistory.value = false }) { Text("Schließen", color = TextSec) } }
            )
        }
    }
}

/**
 * Spotify-style tile: leading icon block, bold two-line name, edit pencil.
 * Tap = run (like Spotify tap = play), long-press = enable/disable (dims when off).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoutineTile(r: Routine, onRun: () -> Unit, onEdit: () -> Unit, onToggle: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }

    // Start confirmation: tile flashes in the accent, icon pops, subtitle says it's running.
    var runFlash by remember { mutableStateOf(false) }
    val tileBg by animateColorAsState(
        targetValue = if (runFlash) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else Surface1,
        animationSpec = tween(220), label = "tileBg"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (runFlash) 1.3f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f), label = "iconPop"
    )
    LaunchedEffect(runFlash) { if (runFlash) { delay(1200); runFlash = false } }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .graphicsLayer { alpha = if (r.enabled) 1f else 0.45f }
            .clip(MaterialTheme.shapes.small)
            .background(tileBg)
            .pressScale(interaction)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { runFlash = true; onRun() },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                }
            )
    ) {
        // leading block, Spotify album-art slot
        Box(
            Modifier.size(72.dp).background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Text(
                triggerEmoji(r.triggers.firstOrNull()?.type), fontSize = 28.sp,
                modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale }
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                r.name,
                color = TextPrim,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (runFlash) "▶ Gestartet …"
                else r.triggers.joinToString(" · ") { triggerLabel(it.type) },
                color = if (runFlash) Violet else TextSec,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "✎",
            color = TextSec,
            fontSize = 16.sp,
            modifier = Modifier
                .padding(end = 8.dp)
                .clip(MaterialTheme.shapes.small)
                .bouncyClick(onEdit)
                .padding(8.dp)
        )
    }
}

fun triggerEmoji(t: TriggerType?): String = when (t) {
    TriggerType.NFC -> "🏷️"
    TriggerType.DEVICE_STATE -> "💡"
    TriggerType.LEAVE_WIFI -> "📡"
    TriggerType.TIME -> "⏰"
    TriggerType.SUN -> "🌅"
    TriggerType.ARRIVE_HOME -> "🏠"
    TriggerType.LEAVE_HOME -> "🚪"
    else -> "▶️"
}

fun triggerLabel(t: TriggerType): String = when (t) {
    TriggerType.MANUAL -> "Manuell · Button/Widget"
    TriggerType.NFC -> "NFC-Tag"
    TriggerType.DEVICE_STATE -> "Geräte-Trigger (Hue)"
    TriggerType.LEAVE_WIFI -> "Beim Verlassen des WLANs"
    TriggerType.TIME -> "Zu einer Uhrzeit"
    TriggerType.SUN -> "Sonnenauf-/untergang"
    TriggerType.ARRIVE_HOME -> "Beim Nachhausekommen"
    TriggerType.LEAVE_HOME -> "Beim Verlassen (GPS)"
}
