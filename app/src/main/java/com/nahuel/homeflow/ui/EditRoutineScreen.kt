package com.nahuel.homeflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuel.homeflow.data.*
import com.nahuel.homeflow.devices.HueClient
import com.nahuel.homeflow.devices.HueLight
import com.nahuel.homeflow.engine.RoutineEngine
import com.nahuel.homeflow.engine.TriggerService
import com.nahuel.homeflow.nlp.ClaudeParser
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun EditRoutineScreen(routineId: String?, onClose: () -> Unit, onRequestNfcWrite: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = remember(routineId) { routineId?.takeIf { it.isNotEmpty() }?.let { Store.routine(it) } }

    var draftId by remember { mutableStateOf(existing?.id ?: UUID.randomUUID().toString()) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var trigger by remember { mutableStateOf(existing?.trigger ?: Trigger()) }
    var variants by remember { mutableStateOf(existing?.variants ?: listOf(Variant())) }

    var nlText by remember { mutableStateOf("") }
    var nlBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var hueLights by remember { mutableStateOf<List<HueLight>>(emptyList()) }
    var actionDialog by remember { mutableStateOf<Pair<Int, Action?>?>(null) } // variantIndex to action-being-edited

    LaunchedEffect(Unit) {
        if (Store.config.value.hueAppKey.isNotEmpty()) HueClient.lights().onSuccess { hueLights = it }
    }

    fun applyParsed(r: Routine) {
        draftId = r.id; name = r.name; trigger = r.trigger; variants = r.variants
    }

    fun save(): Routine {
        val r = Routine(draftId, name.ifBlank { "Unbenannt" }, enabled, trigger, variants)
        Store.saveRoutine(r)
        TriggerService.sync(ctx)
        return r
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, "Zurück", tint = TextPrim) }
            Text(
                if (existing == null) "Neue Automation" else "Bearbeiten",
                color = TextPrim, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (existing != null) {
                IconButton(onClick = { Store.deleteRoutine(draftId); TriggerService.sync(ctx); onClose() }) {
                    Icon(Icons.Filled.Delete, "Löschen", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        // ---- Natural language via Claude ----
        GradientCard {
            SectionTitle(if (existing == null) "Mit Claude erstellen" else "Mit Claude ändern")
            OutlinedTextField(
                value = nlText, onValueChange = { nlText = it },
                placeholder = { Text("z. B. Wenn das Badlicht angeht: tagsüber grün + Vogelgeräusche leise auf Sonos Bad, nachts blau gedimmt + Walgeräusche.") },
                modifier = Modifier.fillMaxWidth(), minLines = 3
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    nlBusy = true; error = ""
                    scope.launch {
                        ClaudeParser.parse(nlText, if (existing == null) null else
                            Routine(draftId, name, enabled, trigger, variants), hueLights)
                            .onSuccess { applyParsed(it) }
                            .onFailure { error = it.message ?: "Fehler" }
                        nlBusy = false
                    }
                },
                enabled = !nlBusy && nlText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Violet)
            ) {
                if (nlBusy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Los")
            }
            if (error.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }

        // ---- Manual editing ----
        GradientCard {
            SectionTitle("Name")
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
        }

        GradientCard {
            SectionTitle("Auslöser")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TriggerType.entries.forEach { t ->
                    FilterChip(
                        selected = trigger.type == t,
                        onClick = { trigger = trigger.copy(type = t) },
                        label = {
                            Text(when (t) {
                                TriggerType.MANUAL -> "Button"
                                TriggerType.NFC -> "NFC"
                                TriggerType.DEVICE_STATE -> "Hue-Licht"
                            })
                        }
                    )
                }
            }
            when (trigger.type) {
                TriggerType.DEVICE_STATE -> {
                    Spacer(Modifier.height(8.dp))
                    LightPicker(hueLights, trigger.hueLightId) { trigger = trigger.copy(hueLightId = it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = trigger.toState, onClick = { trigger = trigger.copy(toState = true) },
                            label = { Text("wird eingeschaltet") })
                        FilterChip(selected = !trigger.toState, onClick = { trigger = trigger.copy(toState = false) },
                            label = { Text("wird ausgeschaltet") })
                    }
                }
                TriggerType.NFC -> {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { save(); onRequestNfcWrite(draftId) },
                        colors = ButtonDefaults.buttonColors(containerColor = Surface2)
                    ) { Text("NFC-Tag beschreiben", color = TextPrim) }
                }
                else -> {}
            }
        }

        variants.forEachIndexed { vi, variant ->
            GradientCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Aktionen")
                    Spacer(Modifier.weight(1f))
                    if (variants.size > 1) {
                        IconButton(onClick = { variants = variants.filterIndexed { i, _ -> i != vi } }) {
                            Icon(Icons.Filled.Delete, "Variante löschen", tint = TextSec)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConditionType.entries.forEach { c ->
                        FilterChip(
                            selected = variant.condition == c,
                            onClick = {
                                variants = variants.mapIndexed { i, v ->
                                    if (i == vi) v.copy(condition = c) else v
                                }
                            },
                            label = { Text(when (c) {
                                ConditionType.ALWAYS -> "Immer"
                                ConditionType.DAY -> "Tag"
                                ConditionType.NIGHT -> "Nacht"
                            }) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                variant.actions.forEachIndexed { ai, action ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { actionDialog = vi to action }
                            .padding(vertical = 4.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(describeAction(action, hueLights), color = TextPrim, fontSize = 14.sp)
                            if (action.command == "play_uri" && action.params["uri"].isNullOrBlank()) {
                                Text("⚠ Sound-URL fehlt – antippen und eintragen", color = Pink, fontSize = 12.sp)
                            }
                        }
                        IconButton(onClick = {
                            variants = variants.mapIndexed { i, v ->
                                if (i == vi) v.copy(actions = v.actions.filterIndexed { j, _ -> j != ai }) else v
                            }
                        }) { Icon(Icons.Filled.Delete, "Aktion löschen", tint = TextSec) }
                    }
                }
                TextButton(onClick = { actionDialog = vi to null }) { Text("+ Aktion hinzufügen", color = Violet) }
            }
        }
        TextButton(onClick = { variants = variants + Variant(ConditionType.NIGHT) }) {
            Text("+ Tag/Nacht-Variante hinzufügen", color = Blue)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { save(); onClose() },
                colors = ButtonDefaults.buttonColors(containerColor = Violet),
                modifier = Modifier.weight(1f)
            ) { Text("Speichern") }
            OutlinedButton(
                onClick = { RoutineEngine.runAsync(ctx, save()) },
                modifier = Modifier.weight(1f)
            ) { Text("Testen ▶", color = TextPrim) }
        }
        Spacer(Modifier.height(24.dp))
    }

    actionDialog?.let { (vi, editing) ->
        ActionDialog(
            initial = editing,
            hueLights = hueLights,
            onDismiss = { actionDialog = null },
            onConfirm = { newAction ->
                variants = variants.mapIndexed { i, v ->
                    if (i != vi) v
                    else if (editing == null) v.copy(actions = v.actions + newAction)
                    else v.copy(actions = v.actions.map { if (it === editing || it == editing) newAction else it })
                }
                actionDialog = null
            }
        )
    }
}

@Composable
private fun LightPicker(lights: List<HueLight>, selectedId: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = lights.firstOrNull { it.id == selectedId }?.name ?: "Lampe wählen…"
    Box {
        OutlinedButton(onClick = { open = true }) { Text(label, color = TextPrim) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            lights.forEach { l ->
                DropdownMenuItem(text = { Text(l.name) }, onClick = { onSelect(l.id); open = false })
            }
        }
    }
}

fun describeAction(a: Action, lights: List<HueLight>): String {
    val cfg = Store.config.value
    return when (a.target) {
        TargetType.HUE -> {
            val dev = if (a.deviceId == "all") "Alle Lampen" else lights.firstOrNull { it.id == a.deviceId }?.name ?: "Lampe"
            val parts = mutableListOf<String>()
            a.params["on"]?.let { parts += if (it == "true") "an" else "aus" }
            a.params["color"]?.let { parts += it }
            a.params["brightness"]?.let { parts += "$it %" }
            "💡 $dev: ${parts.joinToString(", ").ifEmpty { "setzen" }}"
        }
        TargetType.SONOS -> {
            val dev = cfg.sonos.firstOrNull { it.ip == a.deviceId }?.name ?: a.deviceId
            val label = when (a.command) {
                "play" -> "Play"; "pause" -> "Pause"; "stop" -> "Stopp"
                "volume" -> "Lautstärke ${a.params["volume"]} %"
                "play_uri" -> "Wiedergabe starten" + (a.params["volume"]?.let { " ($it %)" } ?: "")
                "mute" -> "Stumm " + (if (a.params["on"] == "false") "aus" else "ein")
                "night_mode" -> "Night-Mode " + (if (a.params["on"] == "false") "aus" else "ein")
                "dialog_level" -> "Sprachverbesserung " + (if (a.params["on"] == "false") "aus" else "ein")
                else -> a.command
            }
            "🔊 $dev: $label"
        }
        TargetType.LG_TV -> {
            val dev = cfg.tvs.firstOrNull { it.ip == a.deviceId }?.name ?: a.deviceId
            val label = when (a.command) {
                "on" -> "einschalten"; "off" -> "ausschalten"; "mute" -> "stumm"
                "volume" -> "Lautstärke ${a.params["volume"]}"
                else -> a.command
            }
            "📺 $dev: $label"
        }
    }
}

private val presetColors = listOf(
    "#FFFFFF", "#FFB74D", "#F062A6", "#34D399", "#3D8BFD", "#8B7CF7", "#EF4444", "#22D3EE"
)

@Composable
private fun ActionDialog(
    initial: Action?,
    hueLights: List<HueLight>,
    onDismiss: () -> Unit,
    onConfirm: (Action) -> Unit
) {
    val cfg = Store.config.value
    var target by remember { mutableStateOf(initial?.target ?: TargetType.HUE) }
    var deviceId by remember { mutableStateOf(initial?.deviceId ?: "all") }
    var command by remember { mutableStateOf(initial?.command ?: "set") }
    var onState by remember { mutableStateOf(initial?.params?.get("on") ?: "") } // "", "true", "false"
    var color by remember { mutableStateOf(initial?.params?.get("color") ?: "") }
    var brightness by remember { mutableStateOf(initial?.params?.get("brightness") ?: "") }
    var volume by remember { mutableStateOf(initial?.params?.get("volume") ?: "") }
    var uri by remember { mutableStateOf(initial?.params?.get("uri") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Text(if (initial == null) "Aktion hinzufügen" else "Aktion bearbeiten", color = TextPrim) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetType.entries.forEach { t ->
                        FilterChip(
                            selected = target == t,
                            onClick = {
                                target = t
                                deviceId = when (t) {
                                    TargetType.HUE -> "all"
                                    TargetType.SONOS -> cfg.sonos.firstOrNull()?.ip ?: ""
                                    TargetType.LG_TV -> cfg.tvs.firstOrNull()?.ip ?: ""
                                }
                                command = when (t) {
                                    TargetType.HUE -> "set"; TargetType.SONOS -> "play"; TargetType.LG_TV -> "off"
                                }
                            },
                            label = { Text(when (t) {
                                TargetType.HUE -> "Hue"; TargetType.SONOS -> "Sonos"; TargetType.LG_TV -> "TV"
                            }) }
                        )
                    }
                }

                // Device picker
                var devOpen by remember { mutableStateOf(false) }
                val devLabel = when (target) {
                    TargetType.HUE -> if (deviceId == "all") "Alle Lampen"
                        else hueLights.firstOrNull { it.id == deviceId }?.name ?: "wählen…"
                    TargetType.SONOS -> cfg.sonos.firstOrNull { it.ip == deviceId }?.name ?: "wählen…"
                    TargetType.LG_TV -> cfg.tvs.firstOrNull { it.ip == deviceId }?.name ?: "wählen…"
                }
                Box {
                    OutlinedButton(onClick = { devOpen = true }) { Text(devLabel, color = TextPrim) }
                    DropdownMenu(expanded = devOpen, onDismissRequest = { devOpen = false }) {
                        when (target) {
                            TargetType.HUE -> {
                                DropdownMenuItem(text = { Text("Alle Lampen") },
                                    onClick = { deviceId = "all"; devOpen = false })
                                hueLights.forEach { l ->
                                    DropdownMenuItem(text = { Text(l.name) },
                                        onClick = { deviceId = l.id; devOpen = false })
                                }
                            }
                            TargetType.SONOS -> cfg.sonos.forEach { s ->
                                DropdownMenuItem(text = { Text(s.name) },
                                    onClick = { deviceId = s.ip; devOpen = false })
                            }
                            TargetType.LG_TV -> cfg.tvs.forEach { t ->
                                DropdownMenuItem(text = { Text(t.name) },
                                    onClick = { deviceId = t.ip; devOpen = false })
                            }
                        }
                    }
                }

                when (target) {
                    TargetType.HUE -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = onState == "true", onClick = { onState = if (onState == "true") "" else "true" }, label = { Text("An") })
                            FilterChip(selected = onState == "false", onClick = { onState = if (onState == "false") "" else "false" }, label = { Text("Aus") })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            presetColors.forEach { hex ->
                                val c = Color(android.graphics.Color.parseColor(hex))
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .clickable { color = if (color == hex) "" else hex }
                                )
                            }
                        }
                        if (color.isNotEmpty()) Text("Farbe: $color", color = TextSec, fontSize = 12.sp)
                        OutlinedTextField(value = brightness, onValueChange = { brightness = it },
                            label = { Text("Helligkeit 1–100 (leer = unverändert)") }, singleLine = true)
                    }
                    TargetType.SONOS -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("play" to "Play", "pause" to "Pause", "stop" to "Stopp")
                                .forEach { (c, l) ->
                                    FilterChip(selected = command == c, onClick = { command = c }, label = { Text(l) })
                                }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("volume" to "Lautstärke", "play_uri" to "Sound-URL").forEach { (c, l) ->
                                FilterChip(selected = command == c, onClick = { command = c }, label = { Text(l) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("mute" to "Stumm", "night_mode" to "Night-Mode", "dialog_level" to "Sprache+").forEach { (c, l) ->
                                FilterChip(selected = command == c, onClick = { command = c }, label = { Text(l) })
                            }
                        }
                        if (command == "mute" || command == "night_mode" || command == "dialog_level") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = onState != "false", onClick = { onState = "true" }, label = { Text("Ein") })
                                FilterChip(selected = onState == "false", onClick = { onState = "false" }, label = { Text("Aus") })
                            }
                        }
                        if (command == "volume" || command == "play_uri") {
                            OutlinedTextField(value = volume, onValueChange = { volume = it },
                                label = { Text("Lautstärke 0–100") }, singleLine = true)
                        }
                        if (command == "play_uri") {
                            OutlinedTextField(value = uri, onValueChange = { uri = it },
                                label = { Text("Audio-URL (MP3/Radio-Stream – keine YouTube-Links)") }, singleLine = true)
                        }
                    }
                    TargetType.LG_TV -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("on" to "An", "off" to "Aus", "mute" to "Stumm", "volume" to "Lautstärke")
                                .forEach { (c, l) ->
                                    FilterChip(selected = command == c, onClick = { command = c }, label = { Text(l) })
                                }
                        }
                        if (command == "volume") {
                            OutlinedTextField(value = volume, onValueChange = { volume = it },
                                label = { Text("Lautstärke 0–100") }, singleLine = true)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val params = mutableMapOf<String, String>()
                when (target) {
                    TargetType.HUE -> {
                        if (onState.isNotEmpty()) params["on"] = onState
                        if (color.isNotEmpty()) params["color"] = color
                        brightness.toIntOrNull()?.let { params["brightness"] = it.coerceIn(1, 100).toString() }
                    }
                    TargetType.SONOS -> {
                        volume.toIntOrNull()?.let { params["volume"] = it.coerceIn(0, 100).toString() }
                        if (command == "play_uri") params["uri"] = uri.trim()
                        if (command == "mute" || command == "night_mode" || command == "dialog_level")
                            params["on"] = if (onState == "false") "false" else "true"
                    }
                    TargetType.LG_TV -> volume.toIntOrNull()?.let { params["volume"] = it.coerceIn(0, 100).toString() }
                }
                val cmd = if (target == TargetType.HUE) "set" else command
                onConfirm(Action(target, deviceId, cmd, params))
            }) { Text("OK", color = Violet) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = TextSec) } }
    )
}
