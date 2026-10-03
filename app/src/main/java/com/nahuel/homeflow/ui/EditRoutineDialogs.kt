package com.nahuel.homeflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuel.homeflow.data.*
import com.nahuel.homeflow.devices.HueClient
import com.nahuel.homeflow.devices.HueLight
import com.nahuel.homeflow.engine.RoutineEngine
import com.nahuel.homeflow.engine.TriggerService
import kotlinx.coroutines.launch
import java.util.UUID

// Dialogs and pickers of the routine editor (split out of EditRoutineScreen.kt).

@Composable
internal fun CondDialog(onDismiss: () -> Unit, onConfirm: (Cond) -> Unit) {
    val cfg = Store.config.value
    var type by remember { mutableStateOf(CondType.DAY) }
    var speakerIp by remember { mutableStateOf(cfg.sonos.firstOrNull()?.ip ?: "") }
    val needsSpeaker = type == CondType.SPEAKER_IDLE || type == CondType.SPEAKER_PLAYING

    FlatDialog(
        onDismissRequest = onDismiss,
        title = "Bedingung hinzufügen",
        confirmButton = {
            GhostButton("Hinzufügen") { onConfirm(Cond(type, if (needsSpeaker) speakerIp else "")) }
        },
        dismissButton = { GhostButton("Abbrechen", color = Muted, onClick = onDismiss) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    CondType.DAY to "Tagsüber", CondType.NIGHT to "Nachts",
                    CondType.SPEAKER_IDLE to "Auf Speaker läuft nichts",
                    CondType.SPEAKER_PLAYING to "Auf Speaker läuft etwas",
                    CondType.PARTNER_HOME to "Partnerin zuhause",
                    CondType.PARTNER_AWAY to "Partnerin unterwegs"
                ).forEach { (t, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { type = t }
                    ) {
                        IconBox(16.dp, fill = if (type == t) Accent else Bg) {}
                        Spacer(Modifier.width(10.dp))
                        Text(label, color = Ink, fontSize = 13.sp)
                    }
                }
                if (needsSpeaker) {
                    var open by remember { mutableStateOf(false) }
                    val label = cfg.sonos.firstOrNull { it.ip == speakerIp }?.name ?: "Speaker wählen…"
                    Box {
                        SecondaryButton(label) { open = true }
                        DropdownMenu(
                            expanded = open,
                            onDismissRequest = { open = false },
                            modifier = Modifier.background(Bg).border(RuleWidth, Divider)
                        ) {
                            cfg.sonos.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.name, color = Ink, fontSize = 13.sp) },
                                    onClick = { speakerIp = s.ip; open = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
internal fun LightPicker(lights: List<HueLight>, selectedId: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = lights.firstOrNull { it.id == selectedId }?.name ?: "Lampe wählen…"
    Box {
        SecondaryButton(label) { open = true }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(Bg).border(RuleWidth, Divider)
        ) {
            lights.forEach { l ->
                DropdownMenuItem(
                    text = { Text(l.name, color = Ink, fontSize = 13.sp) },
                    onClick = { onSelect(l.id); open = false }
                )
            }
        }
    }
}

private val tvApps = listOf(
    "netflix" to "Netflix",
    "youtube.leanback.v4" to "YouTube",
    "amazon" to "Prime Video",
    "com.disney.disneyplus-prod" to "Disney+"
)

fun describeAction(a: Action, lights: List<HueLight>): String {
    val cfg = Store.config.value
    return when (a.target) {
        TargetType.HUE -> {
            val dev = if (a.deviceId == "all") {
                val ex = a.params["exclude"]?.split(",")?.filter { it.isNotEmpty() }.orEmpty()
                if (ex.isEmpty()) "Alle Lampen"
                else "Alle Lampen (außer ${ex.mapNotNull { id -> lights.firstOrNull { it.id == id }?.name }.joinToString(", ")})"
            } else lights.firstOrNull { it.id == a.deviceId }?.name ?: "Lampe"
            val parts = mutableListOf<String>()
            a.params["on"]?.let { parts += if (it == "true") "an" else "aus" }
            a.params["color"]?.let { parts += it }
            a.params["brightness"]?.let { parts += "$it %" }
            "💡 $dev: ${parts.joinToString(", ").ifEmpty { "setzen" }}"
        }
        TargetType.SONOS -> {
            val dev = if (a.deviceId == "all") "Alle Speaker" else cfg.sonos.firstOrNull { it.ip == a.deviceId }?.name ?: a.deviceId
            val label = when (a.command) {
                "play" -> "Play"; "pause" -> "Pause"; "stop" -> "Stopp"
                "volume" -> "Lautstärke ${a.params["volume"]} %"
                "play_uri" -> "Wiedergabe starten" + (a.params["volume"]?.let { " ($it %)" } ?: "")
                "spotify" -> "Spotify: ${a.params["query"]?.take(24) ?: ""}"
                "mute" -> "Stumm " + (if (a.params["on"] == "false") "aus" else "ein")
                "night_mode" -> "Night-Mode " + (if (a.params["on"] == "false") "aus" else "ein")
                "dialog_level" -> "Sprachverbesserung " + (if (a.params["on"] == "false") "aus" else "ein")
                else -> a.command
            }
            val idle = if (a.params["onlyIfIdle"] == "true") " · nur wenn frei" else ""
            "🔊 $dev: $label$idle"
        }
        TargetType.LG_TV -> {
            val dev = cfg.tvs.firstOrNull { it.ip == a.deviceId }?.name ?: a.deviceId
            val label = when (a.command) {
                "on" -> "einschalten"; "off" -> "ausschalten"; "mute" -> "stumm"
                "volume" -> "Lautstärke ${a.params["volume"]}"
                "app" -> (tvApps.firstOrNull { it.first == a.params["appId"] }?.second ?: "App") + " öffnen"
                else -> a.command
            }
            "📺 $dev: $label"
        }
        TargetType.GENERIC -> {
            val dev = cfg.generics.firstOrNull { it.name == a.deviceId }?.name ?: a.deviceId
            "🔗 $dev"
        }
    }
}

private val presetColors = listOf(
    "#FFFFFF", "#FFB74D", "#F062A6", "#34D399", "#3D8BFD", "#8B7CF7", "#EF4444", "#22D3EE"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActionDialog(
    initial: Action?,
    hueLights: List<HueLight>,
    onDismiss: () -> Unit,
    onConfirm: (Action) -> Unit
) {
    val cfg = Store.config.value
    var target by remember { mutableStateOf(initial?.target ?: TargetType.HUE) }
    var deviceId by remember { mutableStateOf(initial?.deviceId ?: "all") }
    var command by remember { mutableStateOf(initial?.command ?: "set") }
    var onState by remember { mutableStateOf(initial?.params?.get("on") ?: "") }
    var color by remember { mutableStateOf(initial?.params?.get("color") ?: "") }
    var brightness by remember { mutableStateOf(initial?.params?.get("brightness") ?: "") }
    var excluded by remember { mutableStateOf(initial?.params?.get("exclude")?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()) }
    var volume by remember { mutableStateOf(initial?.params?.get("volume") ?: "") }
    var uri by remember { mutableStateOf(initial?.params?.get("uri") ?: initial?.params?.get("query") ?: "") }
    var appId by remember { mutableStateOf(initial?.params?.get("appId") ?: "netflix") }
    var contentId by remember { mutableStateOf(initial?.params?.get("contentId") ?: "") }
    var onlyIfIdle by remember { mutableStateOf(initial?.params?.get("onlyIfIdle") == "true") }
    var showWheel by remember { mutableStateOf(false) }
    var showRadio by remember { mutableStateOf(false) }

    FlatDialog(
        onDismissRequest = onDismiss,
        title = if (initial == null) "Aktion hinzufügen" else "Aktion bearbeiten",
        confirmButton = {
            GhostButton("OK") {
                val params = mutableMapOf<String, String>()
                when (target) {
                    TargetType.HUE -> {
                        if (onState.isNotEmpty()) params["on"] = onState
                        if (color.isNotEmpty()) params["color"] = color
                        brightness.toIntOrNull()?.let { params["brightness"] = it.coerceIn(1, 100).toString() }
                        if (deviceId == "all" && excluded.isNotEmpty()) params["exclude"] = excluded.joinToString(",")
                    }
                    TargetType.SONOS -> {
                        volume.toIntOrNull()?.let { params["volume"] = it.coerceIn(0, 100).toString() }
                        if (command == "play_uri") params["uri"] = uri.trim()
                        if (command == "spotify") params["query"] = uri.trim()
                        if (command == "mute" || command == "night_mode" || command == "dialog_level")
                            params["on"] = if (onState == "false") "false" else "true"
                        if ((command == "play" || command == "play_uri") && onlyIfIdle) params["onlyIfIdle"] = "true"
                    }
                    TargetType.LG_TV -> {
                        volume.toIntOrNull()?.let { params["volume"] = it.coerceIn(0, 100).toString() }
                        if (command == "app") {
                            params["appId"] = appId
                            val cid = extractContentId(appId, contentId.trim())
                            if (cid.isNotEmpty()) params["contentId"] = cid
                        }
                    }
                    TargetType.GENERIC -> { /* URL lives on the device; no per-action params */ }
                }
                val cmd = when (target) {
                    TargetType.HUE -> "set"
                    TargetType.GENERIC -> "fire"
                    else -> command
                }
                onConfirm(Action(target, deviceId, cmd, params))
            }
        },
        dismissButton = { GhostButton("Abbrechen", color = Muted, onClick = onDismiss) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChipFlow {
                    TargetType.entries.forEach { t ->
                        ChoiceChip(
                            label = when (t) {
                                TargetType.HUE -> "Hue"; TargetType.SONOS -> "Sonos"
                                TargetType.LG_TV -> "TV"; TargetType.GENERIC -> "HTTP"
                            },
                            selected = target == t
                        ) {
                            target = t
                            deviceId = when (t) {
                                TargetType.HUE -> "all"
                                TargetType.SONOS -> cfg.sonos.firstOrNull()?.ip ?: ""
                                TargetType.LG_TV -> cfg.tvs.firstOrNull()?.ip ?: ""
                                TargetType.GENERIC -> cfg.generics.firstOrNull()?.name ?: ""
                            }
                            command = when (t) {
                                TargetType.HUE -> "set"; TargetType.SONOS -> "play"
                                TargetType.LG_TV -> "off"; TargetType.GENERIC -> "fire"
                            }
                        }
                    }
                }

                val devices: List<Pair<String, String>> = when (target) {
                    TargetType.HUE -> listOf("all" to "💡 Alle Lampen") + hueLights.map { it.id to "💡 ${it.name}" }
                    TargetType.SONOS -> listOf("all" to "🔊 Alle Speaker") + cfg.sonos.map { it.ip to "🔊 ${it.name}" }
                    TargetType.LG_TV -> cfg.tvs.map { it.ip to "📺 ${it.name}" }
                    TargetType.GENERIC -> cfg.generics.map { it.name to "🔌 ${it.name}" }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(devices) { (id, label) ->
                        ChoiceChip(label, selected = deviceId == id) { deviceId = id }
                    }
                }

                when (target) {
                    TargetType.HUE -> {
                        ChipFlow {
                            ChoiceChip("An", onState == "true") { onState = if (onState == "true") "" else "true" }
                            ChoiceChip("Aus", onState == "false") { onState = if (onState == "false") "" else "false" }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            presetColors.forEach { hex ->
                                val c = Color(android.graphics.Color.parseColor(hex))
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .background(c)
                                        .border(if (color == hex) RuleWidth else 1.dp, if (color == hex) Ink else Faint)
                                        .clickable { color = if (color == hex) "" else hex }
                                )
                            }
                        }
                        GhostButton("Farbrad öffnen") { showWheel = true }
                        if (color.isNotEmpty()) Caption("Farbe: $color")
                        FlatField("Helligkeit 1-100 (leer = unverändert)", brightness) { brightness = it }
                        if (deviceId == "all" && hueLights.isNotEmpty()) {
                            SectionLabel("Ausnehmen (bleiben unverändert)")
                            ChipFlow {
                                hueLights.forEach { l ->
                                    ChoiceChip(l.name, l.id in excluded) {
                                        excluded = if (l.id in excluded) excluded - l.id else excluded + l.id
                                    }
                                }
                            }
                        }
                    }
                    TargetType.SONOS -> {
                        ChipFlow {
                            listOf(
                                "play" to "Play", "pause" to "Pause", "stop" to "Stopp",
                                "volume" to "Lautstärke", "play_uri" to "Sound-URL",
                                "spotify" to "Spotify", "mute" to "Stumm",
                                "night_mode" to "Night-Mode", "dialog_level" to "Sprache+"
                            ).forEach { (c, l) ->
                                ChoiceChip(l, command == c) { command = c }
                            }
                        }
                        if (command == "mute" || command == "night_mode" || command == "dialog_level") {
                            ChipFlow {
                                ChoiceChip("Ein", onState != "false") { onState = "true" }
                                ChoiceChip("Aus", onState == "false") { onState = "false" }
                            }
                        }
                        if (command == "play" || command == "play_uri") {
                            ChoiceChip("Nur wenn gerade nichts läuft", onlyIfIdle) { onlyIfIdle = !onlyIfIdle }
                        }
                        if (command == "volume" || command == "play_uri") {
                            FlatField("Lautstärke 0-100", volume) { volume = it }
                        }
                        if (command == "spotify") {
                            FlatField("Song/Playlist-Suche oder Spotify-Link", uri) { uri = it }
                            HintText("Braucht Spotify-Verbindung (Einstellungen). Spielt auf dem gewählten Sonos.")
                        }
                        if (command == "play_uri") {
                            FlatField("Audio-URL (MP3/Stream, kein YouTube)", uri) { uri = it }
                            GhostButton("Sounds & Sender suchen") { showRadio = true }
                        }
                    }
                    TargetType.LG_TV -> {
                        ChipFlow {
                            listOf(
                                "on" to "An", "off" to "Aus", "mute" to "Stumm",
                                "volume" to "Lautstärke", "app" to "App öffnen"
                            ).forEach { (c, l) -> ChoiceChip(l, command == c) { command = c } }
                        }
                        if (command == "volume") FlatField("Lautstärke 0-100", volume) { volume = it }
                        if (command == "app") {
                            var appOpen by remember { mutableStateOf(false) }
                            val appLabel = tvApps.firstOrNull { it.first == appId }?.second ?: "App wählen…"
                            Box {
                                SecondaryButton(appLabel) { appOpen = true }
                                DropdownMenu(
                                    expanded = appOpen,
                                    onDismissRequest = { appOpen = false },
                                    modifier = Modifier.background(Bg).border(RuleWidth, Divider)
                                ) {
                                    tvApps.forEach { (id, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label, color = Ink, fontSize = 13.sp) },
                                            onClick = { appId = id; appOpen = false }
                                        )
                                    }
                                }
                            }
                            FlatField("Optional: YouTube-Link/-ID oder Netflix-Titel-ID", contentId) { contentId = it }
                            HintText("Leer = App öffnet normal. Mit YouTube-Link startet direkt das Video.")
                        }
                    }
                    TargetType.GENERIC -> {
                        HintText("Dieses Gerät sendet seine HTTP-Anfrage. Bearbeite URL/Methode im Geräte-Tab.")
                    }
                }
            }
        }
    )

    if (showWheel) {
        ColorWheelDialog(
            initialHex = color.ifEmpty { null },
            onDismiss = { showWheel = false },
            onPick = { color = it }
        )
    }
    if (showRadio) {
        RadioSearchDialog(onDismiss = { showRadio = false }, onPick = { uri = it })
    }
}

/** Pulls the video id out of pasted YouTube links; passes anything else through. */
private fun extractContentId(appId: String, raw: String): String {
    if (raw.isEmpty()) return ""
    if (appId.startsWith("youtube")) {
        Regex("[?&]v=([A-Za-z0-9_-]{6,})").find(raw)?.let { return it.groupValues[1] }
        Regex("youtu\\.be/([A-Za-z0-9_-]{6,})").find(raw)?.let { return it.groupValues[1] }
    }
    return raw
}
