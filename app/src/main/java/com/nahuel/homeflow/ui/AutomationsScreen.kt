package com.nahuel.homeflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import com.nahuel.homeflow.data.Action
import com.nahuel.homeflow.data.Routine
import com.nahuel.homeflow.data.RunLog
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.data.TargetType
import com.nahuel.homeflow.data.Trigger
import com.nahuel.homeflow.data.TriggerType
import com.nahuel.homeflow.engine.AlarmScheduler
import com.nahuel.homeflow.engine.NlParser
import com.nahuel.homeflow.engine.RoutineEngine
import com.nahuel.homeflow.engine.TriggerService
import java.util.Calendar

private enum class RoutineFilter(val label: String) { ALL("Alle"), TIME("Zeit"), PLACE("Ort"), DEVICE("Gerät"), MANUAL("Manuell") }

private fun RoutineFilter.matches(r: Routine): Boolean = when (this) {
    RoutineFilter.ALL -> true
    RoutineFilter.TIME -> r.triggers.any { it.type == TriggerType.TIME || it.type == TriggerType.SUN }
    RoutineFilter.PLACE -> r.triggers.any {
        it.type == TriggerType.LEAVE_WIFI || it.type == TriggerType.ARRIVE_HOME || it.type == TriggerType.LEAVE_HOME
    }
    RoutineFilter.DEVICE -> r.triggers.any { it.type == TriggerType.DEVICE_STATE }
    RoutineFilter.MANUAL -> r.triggers.any { it.type == TriggerType.MANUAL || it.type == TriggerType.NFC }
}

/**
 * Automations tab: one-sentence creation on top (offline NlParser), filter
 * pills, then one card per routine with trigger → action summary, live status
 * (next run, last run, last error), run button and enable switch.
 */
@Composable
fun AutomationsScreen(modifier: Modifier = Modifier, onEdit: (String) -> Unit, onCaptureScene: () -> Unit) {
    var showTemplates by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val routines by Store.routines.collectAsState()
    val history by Store.history.collectAsState()
    val config by Store.config.collectAsState()
    val ctx = LocalContext.current
    var sortMode by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(RoutineFilter.ALL) }
    var nlText by remember { mutableStateOf("") }
    var nlError by remember { mutableStateOf("") }
    // Live reorder: work on a local copy that visibly reshuffles while dragging.
    var order by remember { mutableStateOf(routines) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowStepPx = with(LocalDensity.current) { 66.dp.toPx() }
    LaunchedEffect(routines, sortMode) { if (dragId == null) order = routines }

    val shown = routines.filter { filter.matches(it) }

    Column(modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        TopBar("Automationen") {
            if (routines.size > 1) {
                GhostButton(if (sortMode) "Fertig" else "Sortieren") { sortMode = !sortMode }
                Spacer(Modifier.width(4.dp))
            }
            RoundIconButton(Icons.Outlined.History, "Verlauf") { showHistory = true }
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!sortMode) {
                // ---- Create ----
                item {
                    FlatBlock {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AutoAwesome, null, tint = AccentText, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Neue Automation in einem Satz", color = AccentText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            FlatField(
                                label = "",
                                value = nlText,
                                placeholder = "Wenn ich gehe, alles aus",
                                singleLine = false,
                                modifier = Modifier.weight(1f)
                            ) { nlText = it; nlError = "" }
                            Spacer(Modifier.width(8.dp))
                            RoundIconButton(
                                Icons.Outlined.ArrowForward, "Automation erstellen",
                                size = 48.dp, fill = Accent, tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                            ) {
                                if (nlText.isBlank()) return@RoundIconButton
                                NlParser.parse(nlText, config, HomeRepo.lights.value)
                                    .onSuccess { parsed ->
                                        Store.saveRoutine(parsed)
                                        TriggerService.sync(ctx)
                                        nlText = ""
                                        onEdit(parsed.id)   // open the builder to review
                                    }
                                    .onFailure { nlError = it.message ?: "Nicht verstanden" }
                            }
                        }
                        if (nlError.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(nlError, color = Danger, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallPill("Vorlage") { showTemplates = true }
                            SmallPill("Szene aufnehmen") { onCaptureScene() }
                            SmallPill("Manuell bauen") { onEdit("") }
                        }
                    }
                }

                // ---- Filter ----
                if (routines.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RoutineFilter.entries.forEach { f ->
                                val n = routines.count { f.matches(it) }
                                if (f == RoutineFilter.ALL || n > 0) {
                                    ChoiceChip(
                                        label = if (f == RoutineFilter.ALL) "${f.label} $n" else f.label,
                                        selected = filter == f
                                    ) { filter = f }
                                }
                            }
                        }
                    }
                }
            }

            if (routines.isEmpty()) {
                item {
                    FlatBlock {
                        Text("Noch keine Automationen", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        HintText("Beschreib oben in einem Satz, was passieren soll, oder nimm den aktuellen Zustand als Szene auf.")
                    }
                }
            }

            if (sortMode) {
                item { Caption("Halte ☰ und ziehe an die neue Position.", Modifier.padding(horizontal = 4.dp)) }
                itemsIndexed(order, key = { _, r -> r.id }) { _, r ->
                    val dragged = r.id == dragId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .zIndex(if (dragged) 1f else 0f)
                            .graphicsLayer { translationY = if (dragged) dragOffset else 0f }
                            .clip(TileShape)
                            .background(if (dragged) AccentTint else Card)
                            .padding(horizontal = 16.dp)
                            .animateItem()
                    ) {
                        Icon(
                            Icons.Outlined.DragHandle, contentDescription = "Verschieben",
                            tint = if (dragged) AccentText else Muted,
                            modifier = Modifier.size(24.dp).pointerInput(r.id) {
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
                        Text(
                            r.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                items(shown, key = { it.id }) { r ->
                    RoutineCard(
                        r = r,
                        lastRun = history.firstOrNull { it.routineName == r.name },
                        nextFire = nextFireFor(r, config.latitude, config.longitude),
                        onOpen = { onEdit(r.id) },
                        onRun = { RoutineEngine.runAsync(ctx, r) },
                        onToggle = {
                            Store.setEnabled(r.id, !r.enabled)
                            TriggerService.sync(ctx)
                        }
                    )
                }
            }
        }
    }

    if (showTemplates) {
        FlatDialog(
            onDismissRequest = { showTemplates = false },
            title = "Vorlage wählen",
            dismissButton = { GhostButton("Schließen", color = Muted) { showTemplates = false } },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(com.nahuel.homeflow.data.Templates.all) { tpl ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(TileShape)
                                .clickable {
                                    Store.saveRoutine(tpl.build())
                                    showTemplates = false
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Text(tpl.title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Caption(tpl.description)
                        }
                    }
                }
            }
        )
    }

    if (showHistory) HistoryDialog { showHistory = false }
}

@Composable
private fun SmallPill(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(36.dp)
            .clip(PillShape)
            .background(Fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

/** Run history: newest first, failures in the error colour. */
@Composable
fun HistoryDialog(onDismiss: () -> Unit) {
    val history by Store.history.collectAsState()
    FlatDialog(
        onDismissRequest = onDismiss,
        title = "Verlauf",
        confirmButton = { GhostButton("Leeren") { Store.clearHistory() } },
        dismissButton = { GhostButton("Schließen", color = Muted, onClick = onDismiss) },
        text = {
            if (history.isEmpty()) Caption("Noch nichts ausgeführt.")
            else LazyColumn(Modifier.height(340.dp)) {
                items(history, key = { it.timestamp }) { h ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (h.ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                            contentDescription = if (h.ok) "Erfolgreich" else "Fehler",
                            tint = if (h.ok) Muted else Danger,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.routineName, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Caption(
                                android.text.format.DateFormat.format("dd.MM. HH:mm", h.timestamp).toString() +
                                    (if (h.detail.isNotEmpty()) "  ·  ${h.detail}" else "")
                            )
                        }
                    }
                }
            }
        }
    )
}

/** One routine card: icon tile, name, summary, status; run button and switch. */
@Composable
private fun RoutineCard(
    r: Routine,
    lastRun: RunLog?,
    nextFire: Long?,
    onOpen: () -> Unit,
    onRun: () -> Unit,
    onToggle: () -> Unit
) {
    var runFlash by remember { mutableStateOf(false) }
    LaunchedEffect(runFlash) { if (runFlash) { delay(1500); runFlash = false } }

    val (statusText, statusColor) = when {
        runFlash -> "Gestartet …" to AccentText
        !r.enabled -> "Pausiert" to Muted
        lastRun != null && !lastRun.ok -> "Fehler: ${lastRun.detail.ifEmpty { "unbekannt" }}" to Danger
        nextFire != null -> "Nächste: ${formatWhen(nextFire)}" to AccentText
        lastRun != null -> "Zuletzt ${formatWhen(lastRun.timestamp)}" to Muted
        else -> "" to Muted
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(if (runFlash) AccentTint else Card)
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
    ) {
        if (r.icon.isNotEmpty()) {
            IconBox(40.dp) { Text(r.icon, fontSize = 18.sp) }
        } else {
            IconTile(routineVector(r), size = 40.dp, tint = if (r.enabled) AccentText else Muted)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                r.name.ifEmpty { "Unbenannt" },
                color = if (r.enabled) Ink else Muted,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                routineSummary(r),
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (statusText.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        RoundIconButton(Icons.Outlined.PlayArrow, "${r.name} ausführen", size = 40.dp, tint = AccentText) {
            runFlash = true; onRun()
        }
        Spacer(Modifier.width(10.dp))
        FlatToggle(r.enabled) { onToggle() }
    }
}

// ---- Shared labels & icons -------------------------------------------------

/** Vector icon for a routine: name keywords first, then its first trigger. */
fun routineVector(r: Routine): ImageVector {
    val n = r.name.lowercase()
    fun has(vararg k: String) = k.any { it in n }
    return when {
        has("film", "kino", "movie", "netflix", "tv") -> Icons.Outlined.Movie
        has("schlaf", "nacht", "bett") -> Icons.Outlined.Bedtime
        has("aufwach", "morgen", "wecker") -> Icons.Outlined.WbSunny
        has("party", "rave", "feier") -> Icons.Outlined.Celebration
        has("musik", "radio", "sound") -> Icons.Outlined.MusicNote
        has("alles aus", "aus") -> Icons.Outlined.PowerSettingsNew
        has("lesen", "buch") -> Icons.Outlined.MenuBook
        else -> triggerVector(r.triggers.firstOrNull()?.type)
    }
}

fun triggerVector(t: TriggerType?): ImageVector = when (t) {
    TriggerType.NFC -> Icons.Outlined.Nfc
    TriggerType.DEVICE_STATE -> Icons.Outlined.Lightbulb
    TriggerType.LEAVE_WIFI -> Icons.Outlined.WifiOff
    TriggerType.TIME -> Icons.Outlined.Schedule
    TriggerType.SUN -> Icons.Outlined.WbTwilight
    TriggerType.ARRIVE_HOME -> Icons.Outlined.Home
    TriggerType.LEAVE_HOME -> Icons.Outlined.DirectionsWalk
    else -> Icons.Outlined.TouchApp
}

/** "Sonnenuntergang −15 Min → Licht 40 %" */
fun routineSummary(r: Routine): String {
    val trig = r.triggers.joinToString(" · ") { triggerDetail(it) }
    val acts = when {
        r.variants.size > 1 -> "${r.variants.size} Varianten"
        else -> {
            val list = r.variants.firstOrNull()?.actions.orEmpty()
            if (list.isEmpty()) "keine Aktion"
            else actionLabel(list.first()) + if (list.size > 1) " +${list.size - 1}" else ""
        }
    }
    return "$trig → $acts"
}

fun triggerDetail(t: Trigger): String = when (t.type) {
    TriggerType.MANUAL -> "Manuell"
    TriggerType.NFC -> "NFC-Tag"
    TriggerType.DEVICE_STATE -> if (t.toState) "Lampe geht an" else "Lampe geht aus"
    TriggerType.LEAVE_WIFI -> "WLAN verlassen" + if (t.partnerAware) ", außer Partner da" else ""
    TriggerType.TIME -> t.time
    TriggerType.SUN -> {
        val ev = if (t.sunEvent == "SUNRISE") "Sonnenaufgang" else "Sonnenuntergang"
        when {
            t.sunOffsetMin < 0 -> "$ev −${-t.sunOffsetMin} Min"
            t.sunOffsetMin > 0 -> "$ev +${t.sunOffsetMin} Min"
            else -> ev
        }
    }
    TriggerType.ARRIVE_HOME -> "Ankommen"
    TriggerType.LEAVE_HOME -> "Haus verlassen"
}

private fun actionLabel(a: Action): String = when (a.target) {
    TargetType.HUE -> when {
        a.command == "wakeup" -> "Lichtwecker"
        a.params["on"] == "false" -> "Licht aus"
        a.params["brightness"] != null -> "Licht ${a.params["brightness"]} %"
        else -> "Licht"
    }
    TargetType.SONOS -> when (a.command) {
        "play", "play_uri" -> "Sonos spielt"
        "pause", "stop" -> "Sonos stopp"
        "volume" -> "Sonos Lautstärke"
        else -> "Sonos"
    }
    TargetType.LG_TV -> when (a.command) {
        "on" -> "TV an"
        "off" -> "TV aus"
        else -> "TV"
    }
    TargetType.GENERIC -> "Webhook"
}

/** Next scheduled run for time/sun routines, null otherwise. */
fun nextFireFor(r: Routine, lat: Double, lon: Double): Long? =
    if (!r.enabled) null
    else r.triggers
        .filter { it.type == TriggerType.TIME || it.type == TriggerType.SUN }
        .mapNotNull { AlarmScheduler.nextFireMillis(it.type, it, lat, lon) }
        .minOrNull()

/** "Heute 18:45", "Morgen 07:00", "Gestern 22:41", else "Do 21:03". */
fun formatWhen(millis: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    fun dayKey(x: Calendar) = x.get(Calendar.YEAR) * 1000 + x.get(Calendar.DAY_OF_YEAR)
    val diff = dayKey(c) - dayKey(today)
    val time = android.text.format.DateFormat.format("HH:mm", millis).toString()
    return when (diff) {
        0 -> "heute $time"
        1 -> "morgen $time"
        -1 -> "gestern $time"
        else -> android.text.format.DateFormat.format("EE HH:mm", millis).toString()
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
