package com.nahuel.homeflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import com.nahuel.homeflow.data.Routine
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.data.TriggerType
import com.nahuel.homeflow.engine.RoutineEngine
import com.nahuel.homeflow.engine.TriggerService

/**
 * Automations tab, Modernist layout: top bar with a rule, one routine per row
 * separated by 2dp rules, and the new-automation block pinned at the end of the
 * list. Row controls are explicit: the square button runs, the flat switch
 * enables, tapping the row opens the builder.
 */
@Composable
fun AutomationsScreen(modifier: Modifier = Modifier, onEdit: (String) -> Unit, onCaptureScene: () -> Unit) {
    var showAddChooser by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val routines by Store.routines.collectAsState()
    val ctx = LocalContext.current
    var sortMode by remember { mutableStateOf(false) }
    // Live reorder: work on a local copy that visibly reshuffles while dragging.
    var order by remember { mutableStateOf(routines) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowStepPx = with(LocalDensity.current) { 66.dp.toPx() }
    LaunchedEffect(routines, sortMode) { if (dragId == null) order = routines }

    Column(modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        TopBar("Homeflow") {
            if (routines.size > 1) {
                GhostButton(if (sortMode) "Fertig" else "Sortieren") { sortMode = !sortMode }
                Spacer(Modifier.width(4.dp))
            }
            IconBoxButton(contentDescription = "Verlauf", onClick = { showHistory = true }) {
                Text("↻", color = Ink, fontSize = 15.sp)
            }
        }

        LazyColumn(Modifier.weight(1f)) {
            if (routines.isEmpty()) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        FlatBlock {
                            Text(
                                "Noch keine Automationen",
                                color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            HintText(
                                "Lege eine Automation mit Auslöser, Bedingungen und Aktionen an, " +
                                    "oder nimm den aktuellen Zustand als Szene auf."
                            )
                        }
                    }
                }
            } else {
                item {
                    Caption(
                        if (sortMode) "Halte ☰ und ziehe an die neue Position."
                        else "Zeile tippen = bearbeiten  ·  ▶ = ausführen  ·  Schalter = an/aus",
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                item { Rule() }
            }

            if (sortMode) {
                itemsIndexed(order, key = { _, r -> r.id }) { _, r ->
                    val dragged = r.id == dragId
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .zIndex(if (dragged) 1f else 0f)
                                .graphicsLayer { translationY = if (dragged) dragOffset else 0f }
                                .background(if (dragged) AccentTint else Bg)
                                .padding(horizontal = 16.dp)
                                .animateItem()
                        ) {
                            Text(
                                "☰",
                                color = if (dragged) AccentText else Muted,
                                fontSize = 16.sp,
                                modifier = Modifier.pointerInput(r.id) {
                                    detectDragGestures(
                                        onDragStart = { dragId = r.id; dragOffset = 0f },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
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
                            Spacer(Modifier.width(14.dp))
                            IconBox(28.dp) { Text(routineIcon(r), fontSize = 14.sp) }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                r.name, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Rule()
                    }
                }
            } else {
                items(routines, key = { it.id }) { r ->
                    RoutineRow(
                        r = r,
                        onOpen = { onEdit(r.id) },
                        onRun = { RoutineEngine.runAsync(ctx, r) },
                        onToggle = {
                            Store.setEnabled(r.id, !r.enabled)
                            TriggerService.sync(ctx)
                        }
                    )
                }
            }

            item {
                Column(Modifier.padding(16.dp)) {
                    SecondaryButton("+ Neue Automation", Modifier.fillMaxWidth()) { showAddChooser = true }
                }
            }
        }
    }

    if (showAddChooser) {
        FlatDialog(
            onDismissRequest = { showAddChooser = false },
            title = "Was möchtest du anlegen?",
            dismissButton = { GhostButton("Abbrechen", color = Muted) { showAddChooser = false } },
            text = {
                Column {
                    SecondaryButton("Neue Automation", Modifier.fillMaxWidth()) {
                        showAddChooser = false; onEdit("")
                    }
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Szene aus aktuellem Zustand", Modifier.fillMaxWidth()) {
                        showAddChooser = false; onCaptureScene()
                    }
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Vorlage verwenden", Modifier.fillMaxWidth()) {
                        showAddChooser = false; showTemplates = true
                    }
                }
            }
        )
    }

    if (showTemplates) {
        FlatDialog(
            onDismissRequest = { showTemplates = false },
            title = "Vorlage wählen",
            dismissButton = { GhostButton("Schließen", color = Muted) { showTemplates = false } },
            text = {
                Column {
                    com.nahuel.homeflow.data.Templates.all.forEach { tpl ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Store.saveRoutine(tpl.build())
                                    showTemplates = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(tpl.title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Caption(tpl.description)
                        }
                        Rule()
                    }
                }
            }
        )
    }

    if (showHistory) {
        val history by Store.history.collectAsState()
        FlatDialog(
            onDismissRequest = { showHistory = false },
            title = "Verlauf",
            confirmButton = { GhostButton("Leeren") { Store.clearHistory() } },
            dismissButton = { GhostButton("Schließen", color = Muted) { showHistory = false } },
            text = {
                if (history.isEmpty()) Caption("Noch nichts ausgeführt.")
                else LazyColumn(Modifier.height(320.dp)) {
                    items(history, key = { it.timestamp }) { h ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconBox(18.dp) {
                                Text(
                                    if (h.ok) "✓" else "✕",
                                    color = if (h.ok) Ink else AccentText,
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.routineName, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Caption(
                                    android.text.format.DateFormat.format("dd.MM. HH:mm", h.timestamp).toString() +
                                        (if (h.detail.isNotEmpty()) "  ·  ${h.detail}" else "")
                                )
                            }
                        }
                        Rule()
                    }
                }
            }
        )
    }
}

/**
 * One routine row: 36dp icon box, name over trigger caption, square run button,
 * flat switch, 2dp rule beneath. Tapping the row opens the builder.
 */
@Composable
private fun RoutineRow(r: Routine, onOpen: () -> Unit, onRun: () -> Unit, onToggle: () -> Unit) {
    var runFlash by remember { mutableStateOf(false) }
    LaunchedEffect(runFlash) { if (runFlash) { delay(1200); runFlash = false } }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (runFlash) AccentTint else Bg)
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            IconBox(36.dp) { Text(routineIcon(r), fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.name,
                    color = if (r.enabled) Ink else Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (runFlash) "▶ Gestartet …"
                    else r.triggers.joinToString(" · ") { triggerLabel(it.type) },
                    color = if (runFlash) AccentText else Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            IconBoxButton(28.dp, contentDescription = "Jetzt ausführen", onClick = { runFlash = true; onRun() }) {
                Text("▶", color = Ink, fontSize = 11.sp)
            }
            Spacer(Modifier.width(12.dp))
            FlatToggle(r.enabled) { onToggle() }
        }
        Rule()
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
