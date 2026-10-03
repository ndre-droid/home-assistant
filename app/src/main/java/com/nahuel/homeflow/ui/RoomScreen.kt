package com.nahuel.homeflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuel.homeflow.data.LgTv
import com.nahuel.homeflow.data.SonosSpeaker
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.devices.HueLight
import com.nahuel.homeflow.devices.LgTvClient
import com.nahuel.homeflow.engine.TriggerService
import kotlinx.coroutines.launch

private data class Mood(val name: String, val hex: String, val brightness: Int)

private val moods = listOf(
    Mood("Warm", "#FFB46B", 60),
    Mood("Lesen", "#F4EEE2", 90),
    Mood("Kino", "#8E7CFF", 20),
    Mood("Nacht", "#B4582E", 10)
)

/**
 * Room detail: room brightness, mood presets, every light with its own
 * switch (tap to expand brightness + colour), and the room's Sonos/TV.
 * [onBack] null = shown as the right pane on wide screens (no back button).
 */
@Composable
fun RoomScreen(roomId: String, modifier: Modifier = Modifier, onBack: (() -> Unit)?) {
    val rooms by HomeRepo.rooms.collectAsState()
    val speakers by HomeRepo.speakers.collectAsState()
    val tvOn by HomeRepo.tvOn.collectAsState()
    val config by Store.config.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val room = rooms.firstOrNull { it.id == roomId }
    var expanded by remember { mutableStateOf<String?>(null) }
    var wheelFor by remember { mutableStateOf<List<String>?>(null) }   // light ids to colour
    var wheelInitial by remember { mutableStateOf<String?>(null) }
    var showAssign by remember { mutableStateOf(false) }
    var activeMood by remember(roomId) { mutableStateOf<String?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                RoundIconButton(Icons.Outlined.ArrowBack, "Zurück", onClick = onBack)
                Spacer(Modifier.width(12.dp))
            }
            Text(
                room?.name ?: "Raum", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (room != null && room.lights.isNotEmpty()) {
                FlatToggle(room.anyOn) { on -> scope.launch { HomeRepo.setLights(room.lights.map { it.id }, on) } }
            }
        }

        if (room == null) {
            Spacer(Modifier.height(24.dp))
            Caption("Dieser Raum ist nicht mehr verfügbar.")
        } else {
        val allIds = room.lights.map { it.id }

        // ---- Brightness + moods ----
        if (room.lights.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            FlatBlock {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (room.anyOn) "Helligkeit" else "Licht aus",
                        color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${room.avgBrightness}", color = Ink, fontSize = 44.sp, fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-1).sp, lineHeight = 44.sp
                    )
                    Text(" %", color = Muted, fontSize = 20.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(Modifier.height(6.dp))
                LevelSlider(
                    room.avgBrightness,
                    color = room.onLights.firstOrNull()?.let { lightColor(it) } ?: Faint
                ) { b -> scope.launch { HomeRepo.setLights(allIds, true, b) } }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    moods.forEach { m ->
                        MoodButton(m.name, hexColor(m.hex) ?: Color.White, selected = activeMood == m.name) {
                            activeMood = m.name
                            scope.launch { HomeRepo.setLights(allIds, true, m.brightness, m.hex) }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconBoxButton(44.dp, "Eigene Farbe", onClick = {
                            wheelInitial = room.onLights.firstOrNull { it.supportsColor }?.colorHex
                            wheelFor = allIds
                        }) { Icon(Icons.Outlined.Palette, null, tint = Ink, modifier = Modifier.size(20.dp)) }
                        Spacer(Modifier.height(6.dp))
                        Text("Eigene", color = Muted, fontSize = 11.sp)
                    }
                }
            }

            // ---- Lights ----
            Spacer(Modifier.height(20.dp))
            SectionTitle("Lichter")
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clip(CardShape).background(Card)) {
                room.lights.forEachIndexed { i, l ->
                    if (i > 0) Rule(Modifier.padding(horizontal = 16.dp))
                    RoomLightRow(
                        l,
                        expanded = expanded == l.id,
                        onExpand = { expanded = if (expanded == l.id) null else l.id },
                        onToggle = { on -> scope.launch { HomeRepo.setLights(listOf(l.id), on) } },
                        onBrightness = { b -> scope.launch { HomeRepo.setLights(listOf(l.id), true, b) } },
                        onColor = { wheelInitial = l.colorHex; wheelFor = listOf(l.id) }
                    )
                }
            }
        }

        // ---- Media ----
        if (room.hasMedia || room.id != HomeRepo.OTHER_ROOM) {
            Spacer(Modifier.height(20.dp))
            SectionTitle("Medien")
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                room.speakers.forEach { s -> SpeakerCard(s, speakers[s.ip]) }
                room.tvs.forEach { t ->
                    TvCard(
                        t, tvOn[t.ip],
                        biasOn = config.biasEnabled && (config.biasTv == t.ip || config.biasTv.isEmpty()),
                        onBias = { v ->
                            Store.updateConfig { it.copy(biasEnabled = v, biasTv = t.ip) }
                            TriggerService.sync(ctx)
                        }
                    )
                }
                if (room.id != HomeRepo.OTHER_ROOM && (config.sonos.isNotEmpty() || config.tvs.isNotEmpty())) {
                    SecondaryButton(
                        if (room.hasMedia) "Lautsprecher & TV zuordnen" else "Lautsprecher oder TV hinzufügen",
                        Modifier.fillMaxWidth()
                    ) { showAssign = true }
                } else if (!room.hasMedia) {
                    Caption("Im Geräte-Tab Sonos oder LG TV hinzufügen.")
                }
            }
        }
        }
        Spacer(Modifier.height(28.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }

    wheelFor?.let { ids ->
        ColorWheelDialog(
            initialHex = wheelInitial,
            onDismiss = { wheelFor = null },
            onPick = { hex ->
                wheelFor = null
                activeMood = null
                scope.launch { HomeRepo.setLights(ids, true, null, hex) }
            }
        )
    }

    if (showAssign && room != null) {
        FlatDialog(
            onDismissRequest = { showAssign = false },
            title = "Geräte in „${room.name}“",
            confirmButton = { GhostButton("Fertig") { showAssign = false } },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val items = config.sonos.map { it.ip to it.name } + config.tvs.map { it.ip to it.name }
                    items.forEach { (ip, name) ->
                        val here = config.deviceRooms[ip] == room.id
                        val elsewhere = rooms.firstOrNull { it.id == config.deviceRooms[ip] && it.id != room.id }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(TileShape)
                                .clickable {
                                    Store.updateConfig { c ->
                                        val m = c.deviceRooms.toMutableMap()
                                        if (here) m.remove(ip) else m[ip] = room.id
                                        c.copy(deviceRooms = m)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                if (elsewhere != null) Caption("Aktuell: ${elsewhere.name}")
                            }
                            Icon(
                                if (here) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = if (here) "zugeordnet" else "nicht zugeordnet",
                                tint = if (here) Accent else Faint
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun MoodButton(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(44.dp)
                .then(if (selected) Modifier.border(2.dp, Accent, CircleShape).padding(4.dp) else Modifier)
                .clip(CircleShape)
                .background(color)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "Stimmung $name" }
        )
        Spacer(Modifier.height(6.dp))
        Text(name, color = if (selected) Ink else Muted, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun RoomLightRow(
    l: HueLight,
    expanded: Boolean,
    onExpand: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onBrightness: (Int) -> Unit,
    onColor: () -> Unit
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onExpand).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 44.dp)) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .then(
                        if (l.on) Modifier.background(lightColor(l))
                        else Modifier.border(1.5.dp, Faint, CircleShape)
                    )
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(l.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(if (l.on) "${l.brightness} %" else "Aus", color = Muted, fontSize = 13.sp)
            }
            FlatToggle(l.on) { onToggle(it) }
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelSlider(l.brightness, Modifier.weight(1f), color = if (l.on) lightColor(l) else Faint) { onBrightness(it) }
                if (l.supportsColor) {
                    Spacer(Modifier.width(10.dp))
                    RoundIconButton(Icons.Outlined.Palette, "Farbe von ${l.name}", size = 40.dp, onClick = onColor)
                }
            }
        }
    }
}

@Composable
private fun SpeakerCard(s: SonosSpeaker, state: SpeakerState?) {
    val scope = rememberCoroutineScope()
    FlatBlock {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.Speaker)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        state == null -> "Status wird geladen …"
                        state.playing && state.title.isNotEmpty() -> state.title
                        state.playing -> "Spielt"
                        else -> "Pausiert"
                    },
                    color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            val playing = state?.playing == true
            RoundIconButton(
                if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                if (playing) "Pause" else "Abspielen",
                size = 48.dp, fill = Accent, tint = MaterialTheme.colorScheme.onPrimary
            ) { scope.launch { HomeRepo.togglePlay(s.ip) } }
        }
        state?.volume?.let { vol ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.VolumeUp, null, tint = Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                LevelSlider(vol, Modifier.weight(1f)) { v -> scope.launch { HomeRepo.setVolume(s.ip, v) } }
                Spacer(Modifier.width(10.dp))
                Text("$vol", color = Ink, fontSize = 13.sp, modifier = Modifier.width(28.dp))
            }
        }
    }
}

@Composable
private fun TvCard(t: LgTv, on: Boolean?, biasOn: Boolean, onBias: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var muted by remember { mutableStateOf(false) }
    val paired = t.clientKey.isNotEmpty()
    FlatBlock {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.Tv)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        !paired -> "Nicht gekoppelt (Geräte-Tab)"
                        on == true -> if (muted) "An · stumm" else "An"
                        on == false -> "Aus"
                        else -> "Status wird geladen …"
                    },
                    color = Muted, fontSize = 13.sp
                )
            }
        }
        if (paired) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (on == false) {
                    SecondaryButton("Einschalten", Modifier.weight(1f)) {
                        scope.launch {
                            if (t.mac.isBlank()) HomeRepo.reportError("TV: MAC fehlt – im Geräte-Tab eintragen.")
                            else LgTvClient.powerOn(t.mac)
                                .onSuccess { HomeRepo.setTvOn(t.ip, true) }
                                .onFailure { HomeRepo.reportError("TV: ${it.message}") }
                        }
                    }
                } else {
                    SecondaryButton(if (muted) "Ton an" else "Stumm", Modifier.weight(1f)) {
                        val target = !muted
                        scope.launch {
                            LgTvClient.setMute(t.ip, t.clientKey, target)
                                .onSuccess { muted = target }
                                .onFailure { HomeRepo.reportError("TV: ${it.message}") }
                        }
                    }
                    SecondaryButton("Ausschalten", Modifier.weight(1f)) {
                        scope.launch {
                            LgTvClient.powerOff(t.ip, t.clientKey)
                                .onSuccess { HomeRepo.setTvOn(t.ip, false) }
                                .onFailure { HomeRepo.reportError("TV: ${it.message}") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Rule()
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bias-Licht", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Caption("Lichter folgen dem Bildinhalt (Auswahl in Einstellungen)")
                }
                Spacer(Modifier.width(10.dp))
                FlatToggle(biasOn) { onBias(it) }
            }
        }
    }
}
