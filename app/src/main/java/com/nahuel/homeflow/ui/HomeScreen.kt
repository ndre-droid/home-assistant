package com.nahuel.homeflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuel.homeflow.data.Routine
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.data.TriggerType
import com.nahuel.homeflow.devices.HueLight
import com.nahuel.homeflow.engine.RoutineEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Colour a light shows as in dots and sliders. White-only lights read as warm white. */
fun lightColor(l: HueLight): Color = hexColor(l.colorHex) ?: Color(0xFFF4EEE2)

/**
 * Home tab: greeting + live summary, scene tiles (manual/NFC routines plus
 * "Alles aus"), room cards from the Hue bridge, and the next scheduled run.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    selectedRoomId: String? = null,
    onOpenRoom: (String) -> Unit,
    onOpenAutomations: () -> Unit,
    onOpenDevices: () -> Unit
) {
    val rooms by HomeRepo.rooms.collectAsState()
    val lights by HomeRepo.lights.collectAsState()
    val speakers by HomeRepo.speakers.collectAsState()
    val tvOn by HomeRepo.tvOn.collectAsState()
    val error by HomeRepo.error.collectAsState()
    val loaded by HomeRepo.loaded.collectAsState()
    val routines by Store.routines.collectAsState()
    val config by Store.config.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showHistory by remember { mutableStateOf(false) }

    val onCount = lights.count { it.on }
    val playingCount = speakers.values.count { it.playing }
    val nothingSetUp = config.hueAppKey.isEmpty() && config.sonos.isEmpty() && config.tvs.isEmpty()

    Column(
        modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        // ---- Header ----
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                val date = SimpleDateFormat("EE, d. MMMM", Locale.GERMAN).format(Date())
                val summary = buildList {
                    add(date)
                    if (lights.isNotEmpty()) add(if (onCount == 0) "Alle Lichter aus" else "$onCount ${if (onCount == 1) "Licht" else "Lichter"} an")
                    if (playingCount > 0) add("$playingCount spielt")
                }.joinToString(" · ")
                Text(summary, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(greeting(), color = Ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp)
            }
            RoundIconButton(Icons.Outlined.History, "Verlauf") { showHistory = true }
        }

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(TileShape)
                    .background(AccentTint)
                    .clickable { HomeRepo.clearError() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = Danger, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(error ?: "", color = Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
            }
        }

        if (nothingSetUp) {
            Spacer(Modifier.height(24.dp))
            FlatBlock {
                Text("Noch keine Geräte", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                HintText("Kopple deine Hue Bridge und such nach Sonos & LG TV. Danach erscheinen hier deine Räume.")
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Geräte einrichten", onClick = onOpenDevices)
            }
        }

        // ---- Scenes ----
        val scenes = routines.filter { r -> r.triggers.any { it.type == TriggerType.MANUAL || it.type == TriggerType.NFC } }
        Spacer(Modifier.height(24.dp))
        SectionTitle("Szenen")
        Spacer(Modifier.height(10.dp))
        var activeId by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(activeId) { if (activeId != null) { delay(2500); activeId = null } }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            scenes.forEach { r ->
                SceneTile(r.name, routineVector(r), active = activeId == r.id) {
                    activeId = r.id
                    RoutineEngine.runAsync(ctx, r)
                }
            }
            SceneTile("Alles aus", Icons.Outlined.PowerSettingsNew, active = activeId == "_off") {
                activeId = "_off"
                scope.launch { HomeRepo.allOff() }
            }
        }
        UndoBar()

        // ---- Rooms ----
        if (rooms.isNotEmpty() || (!loaded && !nothingSetUp)) {
            Spacer(Modifier.height(24.dp))
            SectionTitle("Räume")
            Spacer(Modifier.height(10.dp))
        }
        if (rooms.isEmpty() && !loaded && !nothingSetUp) {
            Caption("Lade Geräte …")
        }
        val (big, rest) = rooms.firstOrNull() to rooms.drop(1)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            big?.let { room ->
                RoomCard(
                    room, big = true, selected = room.id == selectedRoomId,
                    speakers = speakers, tvOn = tvOn,
                    onOpen = { onOpenRoom(room.id) },
                    onToggle = { on -> scope.launch { HomeRepo.setLights(room.lights.map { it.id }, on) } },
                    onBrightness = { b -> scope.launch { HomeRepo.setLights(room.lights.map { it.id }, true, b) } }
                )
            }
            rest.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { room ->
                        RoomCard(
                            room, big = false, selected = room.id == selectedRoomId,
                            speakers = speakers, tvOn = tvOn,
                            modifier = Modifier.weight(1f),
                            onOpen = { onOpenRoom(room.id) },
                            onToggle = { on -> scope.launch { HomeRepo.setLights(room.lights.map { it.id }, on) } },
                            onBrightness = {}
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // ---- Next scheduled run ----
        val next = routines
            .mapNotNull { r -> nextFireFor(r, config.latitude, config.longitude)?.let { r to it } }
            .minByOrNull { it.second }
        if (next != null) {
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(Card)
                    .clickable(onClick = onOpenAutomations)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconTile(triggerVector(next.first.triggers.firstOrNull { it.type == TriggerType.SUN || it.type == TriggerType.TIME }?.type), tint = AccentText)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Als Nächstes · ${formatWhen(next.second)}", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text(next.first.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    if (showHistory) HistoryDialog { showHistory = false }
}

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 5 -> "Gute Nacht"
        h < 11 -> "Guten Morgen"
        h < 18 -> "Guten Tag"
        h < 22 -> "Guten Abend"
        else -> "Gute Nacht"
    }
}

@Composable
private fun SceneTile(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(80.dp)
            .height(84.dp)
            .clip(TileShape)
            .background(Card)
            .then(if (active) Modifier.border(1.5.dp, Accent, TileShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (active) AccentText else Ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            label, color = if (active) AccentText else Ink, fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LightDots(lights: List<HueLight>) {
    Row {
        lights.take(3).forEachIndexed { i, l ->
            Box(
                Modifier
                    .padding(start = if (i == 0) 0.dp else 0.dp)
                    .offset(x = (-4 * i).dp)
                    .size(16.dp)
                    .border(2.dp, Card, CircleShape)
                    .clip(CircleShape)
                    .background(lightColor(l))
            )
        }
    }
}

@Composable
private fun RoomCard(
    room: RoomInfo,
    big: Boolean,
    selected: Boolean,
    speakers: Map<String, SpeakerState>,
    tvOn: Map<String, Boolean>,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onBrightness: (Int) -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(if (selected) Fill else Card)
            .clickable(onClick = onOpen)
            .padding(if (big) 18.dp else 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    room.name, color = Ink, fontSize = if (big) 17.sp else 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                if (big) Icon(Icons.Outlined.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            FlatToggle(room.anyOn, enabled = room.lights.isNotEmpty()) { onToggle(it) }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (room.anyOn) {
                LightDots(room.onLights)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                when {
                    room.lights.isEmpty() -> "Keine Lichter"
                    room.anyOn -> "${room.onLights.size} von ${room.lights.size} · ${room.avgBrightness} %"
                    else -> "Licht aus"
                },
                color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (big && room.anyOn) {
            Spacer(Modifier.height(4.dp))
            LevelSlider(room.avgBrightness, color = lightColor(room.onLights.first())) { onBrightness(it) }
        }
        val media = room.speakers.map { s ->
            Triple(Icons.Outlined.Speaker, s.name, when (speakers[s.ip]?.playing) { true -> "spielt"; false -> "pausiert"; null -> "…" })
        } + room.tvs.map { t ->
            Triple(Icons.Outlined.Tv, t.name, when (tvOn[t.ip]) { true -> "an"; false -> "aus"; null -> "…" })
        }
        if (media.isNotEmpty()) {
            Spacer(Modifier.height(if (big) 6.dp else 8.dp))
            media.forEach { (icon, name, state) ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = Muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$name · $state",
                        color = if (state == "spielt" || state == "an") Ink else Muted,
                        fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** "Undo <routine>" chip: restores the lights to how they were before the last run (15 min window). */
@Composable
private fun UndoBar() {
    val ctx = LocalContext.current
    val point by RoutineEngine.undo.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(point) { while (point != null) { now = System.currentTimeMillis(); delay(30_000) } }
    val p = point?.takeIf { now - it.at < RoutineEngine.UNDO_WINDOW_MS } ?: return
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier
            .clip(CircleShape)
            .background(Fill)
            .clickable(role = Role.Button, onClickLabel = "Rückgängig") { RoutineEngine.undoLast(ctx) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null, tint = AccentText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "„${p.routineName}“ rückgängig",
            color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}
