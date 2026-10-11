package com.nahuel.homeflow.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AutomationsScreen(modifier: Modifier = Modifier, onEdit: (String) -> Unit, onCaptureScene: () -> Unit) {
    val showAddChooser = remember { androidx.compose.runtime.mutableStateOf(false) }
    val showTemplates = remember { androidx.compose.runtime.mutableStateOf(false) }
    val showHistory = remember { androidx.compose.runtime.mutableStateOf(false) }
    val routines by Store.routines.collectAsState()
    val ctx = LocalContext.current
    var sortMode by remember { mutableStateOf(false) }
    // Live reorder: work on a local copy that visibly reshuffles while dragging.
    var order by remember { mutableStateOf(routines) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowStepPx = with(LocalDensity.current) { 66.dp.toPx() }
    // keep local order in sync whenever the store changes and we're not mid-drag
    LaunchedEffect(routines, sortMode) { if (dragId == null) order = routines }

    Box(modifier.fillMaxSize().statusBarsPadding()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 360.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, top = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${routines.count { it.enabled }} aktiv · ${routines.count { !it.enabled }} aus",
                                color = TextSec, fontSize = 13.sp
                            )
                            ScreenTitle("Flows")
                        }
                        if (routines.size > 1) {
                            TextButton(onClick = { sortMode = !sortMode }) {
                                Text(if (sortMode) "Fertig" else "Sortieren", color = Violet, fontSize = 13.sp)
                            }
                        }
                        GlassIconButton(Icons.Outlined.History, "Verlauf", tint = TextSec) { showHistory.value = true }
                        Spacer(Modifier.width(8.dp))
                        AddButton { showAddChooser.value = true }
                    }
                    val next = nextScheduled(routines, Store.config.value.latitude, Store.config.value.longitude)
                    if (next != null && !sortMode) {
                        Spacer(Modifier.height(16.dp))
                        NextFlowHero(next.first, next.second,
                            onRun = { RoutineEngine.runAsync(ctx, next.first) },
                            onEdit = { onEdit(next.first.id) })
                    }
                    if (routines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (sortMode) "Halte ☰ und ziehe an die neue Position."
                            else "Tippen = ausführen · Schalter oder Halten = an/aus · Stift = bearbeiten",
                            color = TextSec, fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            if (routines.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    GradientCard {
                        Text("Noch keine Automationen", color = TextPrim, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        HintText("Tippe auf +, um eine Automation mit Auslöser, Bedingungen und Aktionen zu bauen, oder nimm den aktuellen Zustand als Szene auf.")
                    }
                }
            }
            if (sortMode) {
                itemsIndexed(order, key = { _, r -> r.id }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { i, r ->
                    val dragged = r.id == dragId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .zIndex(if (dragged) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (dragged) dragOffset else 0f
                                scaleX = if (dragged) 1.03f else 1f
                                scaleY = if (dragged) 1.03f else 1f
                                shadowElevation = if (dragged) 16f else 0f
                                alpha = if (dragged) 0.97f else 1f
                            }
                            .clip(MaterialTheme.shapes.small)
                            .background(if (dragged) Surface2 else Surface1)
                            .padding(horizontal = 12.dp)
                            .animateItem()
                    ) {
                        Icon(
                            Icons.Filled.Menu, "Verschieben",
                            tint = if (dragged) Violet else TextSec,
                            modifier = Modifier.pointerInput(r.id) {
                                detectDragGestures(
                                    onDragStart = { dragId = r.id; dragOffset = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        // Live swap: once dragged past half a row, reorder the list now.
                                        val cur = order.indexOfFirst { it.id == dragId }
                                        if (cur < 0) return@detectDragGestures
                                        if (dragOffset > rowStepPx / 2 && cur < order.lastIndex) {
                                            order = order.toMutableList().also { it.add(cur + 1, it.removeAt(cur)) }
                                            dragOffset -= rowStepPx
                                        } else if (dragOffset < -rowStepPx / 2 && cur > 0) {
                                            order = order.toMutableList().also { it.add(cur - 1, it.removeAt(cur)) }
                                            dragOffset += rowStepPx
                                        }
                                    },
                                    onDragEnd = {
                                        Store.setRoutineOrder(order.map { it.id })
                                        dragId = null; dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        order = routines; dragId = null; dragOffset = 0f
                                    }
                                )
                            }
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(routineIcon(r), fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            r.name, color = TextPrim, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
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
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
        }

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
        targetValue = if (runFlash) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else Color.Transparent,
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
            .height(80.dp)
            .graphicsLayer { alpha = if (r.enabled) 1f else 0.5f }
            .shadow(8.dp, MaterialTheme.shapes.medium, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(MaterialTheme.shapes.medium)
            .background(CardGradient)
            .background(tileBg)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (IsDark) 0.07f else 0.5f), Color.Transparent)), MaterialTheme.shapes.medium)
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
            .padding(horizontal = 14.dp)
    ) {
        // glowing scene orb in the routine's own light colours
        val cols = routineColors(r)
        Box(
            Modifier
                .size(52.dp)
                .shadow(12.dp, CircleShape, ambientColor = cols[1], spotColor = cols[1])
                .clip(CircleShape)
                .background(Brush.linearGradient(cols)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                routineIcon(r), fontSize = 22.sp,
                modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale }
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                r.name,
                color = TextPrim,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                if (runFlash) "▶ Gestartet …"
                else r.triggers.joinToString(" · ") { triggerLabel(it.type) },
                color = if (runFlash) Violet else TextSec,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        GlassIconButton(Icons.Outlined.Edit, "Bearbeiten", size = 40.dp, tint = TextSec, onClick = onEdit)
        Spacer(Modifier.width(10.dp))
        GlowSwitch(checked = r.enabled, onCheckedChange = { onToggle() }, label = r.name)
        Spacer(Modifier.width(6.dp))
    }
}

/** Accent "+" with a soft glow. */
@Composable
private fun AddButton(onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .shadow(12.dp, CircleShape, ambientColor = accent, spotColor = accent)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(androidx.compose.ui.graphics.lerp(accent, Color.White, 0.35f), accent)))
            .semantics { contentDescription = "Neu anlegen" }
            .bouncyClick(onClick)
    ) { Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onPrimary) }
}

/** Big gradient card for the next scheduled flow. */
@Composable
private fun NextFlowHero(r: Routine, at: Long, onRun: () -> Unit, onEdit: () -> Unit) {
    val cols = routineColors(r)
    val ink = inkOn(cols)
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(22.dp, shape, ambientColor = cols[1], spotColor = cols[1])
            .clip(shape)
            .background(Brush.linearGradient(cols))
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.32f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, 0f), radius = size.maxDimension * 0.7f))
            }
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent)), shape)
            .padding(22.dp)
    ) {
        Text("Als Nächstes · ${hhmm(at)}", color = ink.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(r.name, color = ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(r.triggers.joinToString(" · ") { triggerLabel(it.type) }, color = ink.copy(alpha = 0.85f), fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(44.dp).shadow(6.dp, CircleShape).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF6F0E8))))
                    .bouncyClick(onRun).padding(horizontal = 18.dp)
            ) { Text("Jetzt starten", color = Color(0xFF2A1410), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(44.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .bouncyClick(onEdit).padding(horizontal = 18.dp)
            ) { Text("Bearbeiten", color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
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
