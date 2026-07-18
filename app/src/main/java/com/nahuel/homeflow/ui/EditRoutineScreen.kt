package com.nahuel.homeflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
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
import java.util.UUID

@Composable
fun EditRoutineScreen(routineId: String?, onClose: () -> Unit, onRequestNfcWrite: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = remember(routineId) { routineId?.takeIf { it.isNotEmpty() }?.let { Store.routine(it) } }

    var draftId by remember { mutableStateOf(existing?.id ?: UUID.randomUUID().toString()) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var icon by remember { mutableStateOf(existing?.icon ?: "") }
    var showIconPicker by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var triggers by remember { mutableStateOf(existing?.triggers ?: listOf(Trigger())) }
    var branches by remember { mutableStateOf(existing?.variants ?: listOf(Variant())) }

    var hueLights by remember { mutableStateOf<List<HueLight>>(emptyList()) }
    var actionDialog by remember { mutableStateOf<Pair<Int, Action?>?>(null) }
    var condDialogFor by remember { mutableStateOf<Int?>(null) }
    var actionClipboard by remember { mutableStateOf<List<Action>>(emptyList()) }
    var justSaved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (Store.config.value.hueAppKey.isNotEmpty())
            HueClient.lights().onSuccess { hueLights = orderLights(it, Store.config.value.lightOrder) }
    }

    fun save(): Routine {
        val r = Routine(draftId, name.ifBlank { "Unbenannt" }, enabled, triggers, branches, icon)
        Store.saveRoutine(r)
        TriggerService.sync(ctx)
        return r
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, "Zurück", tint = TextPrim) }
            Text(
                if (existing == null) "Neue Automation" else "Bearbeiten",
                color = TextPrim, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
                modifier = Modifier.weight(1f)
            )
            if (existing != null) {
                IconButton(onClick = { Store.deleteRoutine(draftId); TriggerService.sync(ctx); onClose() }) {
                    Icon(Icons.Filled.Delete, "Löschen", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        GradientCard {
            SectionTitle("Name & Icon")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Surface2)
                        .bouncyClick { showIconPicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(icon.ifEmpty { suggestIcon(name) ?: "▶️" }, fontSize = 22.sp)
                }
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    placeholder = { Text("z. B. Badlicht-Sound") },
                    modifier = Modifier.weight(1f), singleLine = true
                )
            }
            TextButton(onClick = { showIconPicker = true }, contentPadding = PaddingValues(0.dp)) {
                Text("Icon ändern", color = Blue, fontSize = 13.sp)
            }
        }

        GradientCard {
            SectionTitle("Auslöser  ·  einer davon genügt")
            triggers.forEachIndexed { ti, trg ->
                if (ti > 0) Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auslöser ${ti + 1}", color = TextSec, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (triggers.size > 1) IconButton(
                        onClick = { triggers = triggers.filterIndexed { k, _ -> k != ti } },
                        modifier = Modifier.size(28.dp)
                    ) { Icon(Icons.Filled.Delete, "Auslöser entfernen", tint = TextSec) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TriggerType.entries.forEach { t ->
                        FilterChip(
                            selected = trg.type == t,
                            onClick = { triggers = triggers.mapIndexed { k, x -> if (k == ti) x.copy(type = t) else x } },
                            label = {
                                Text(when (t) {
                                    TriggerType.MANUAL -> "Button"
                                    TriggerType.NFC -> "NFC"
                                    TriggerType.DEVICE_STATE -> "Hue-Licht"
                                    TriggerType.LEAVE_WIFI -> "WLAN weg"
                                    TriggerType.TIME -> "Uhrzeit"
                                    TriggerType.SUN -> "Sonne"
                                    TriggerType.ARRIVE_HOME -> "Ankunft"
                                    TriggerType.LEAVE_HOME -> "Weggehen"
                                })
                            }
                        )
                    }
                }
                fun upd(block: (Trigger) -> Trigger) {
                    triggers = triggers.mapIndexed { k, x -> if (k == ti) block(x) else x }
                }
                when (trg.type) {
                    TriggerType.DEVICE_STATE -> {
                        Spacer(Modifier.height(8.dp))
                        LightPicker(hueLights, trg.hueLightId) { picked -> upd { it.copy(hueLightId = picked) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = trg.toState, onClick = { upd { it.copy(toState = true) } },
                                label = { Text("wird eingeschaltet") })
                            FilterChip(selected = !trg.toState, onClick = { upd { it.copy(toState = false) } },
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
                    TriggerType.LEAVE_WIFI -> {
                        Spacer(Modifier.height(8.dp))
                        FilterChip(
                            selected = trg.partnerAware,
                            onClick = { upd { it.copy(partnerAware = !it.partnerAware) } },
                            label = { Text("Nur wenn Partnerin nicht zuhause") }
                        )
                        Spacer(Modifier.height(4.dp))
                        HintText("Löst aus, wenn dein Handy das Heim-WLAN verliert. Für die Ausführung danach muss Tailscale aktiv sein.")
                    }
                    TriggerType.TIME -> {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = trg.time, onValueChange = { v -> upd { it.copy(time = v) } },
                            label = { Text("Uhrzeit (HH:MM)") }, singleLine = true
                        )
                        HintText("Läuft täglich zu dieser Uhrzeit. (Hintergrund-Planung folgt; Auslöser wird gespeichert.)")
                    }
                    TriggerType.SUN -> {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = trg.sunEvent == "SUNRISE",
                                onClick = { upd { it.copy(sunEvent = "SUNRISE") } }, label = { Text("Sonnenaufgang") })
                            FilterChip(selected = trg.sunEvent == "SUNSET",
                                onClick = { upd { it.copy(sunEvent = "SUNSET") } }, label = { Text("Sonnenuntergang") })
                        }
                        OutlinedTextField(
                            value = trg.sunOffsetMin.toString(),
                            onValueChange = { v -> upd { it.copy(sunOffsetMin = v.toIntOrNull() ?: 0) } },
                            label = { Text("Minuten davor (-) / danach (+)") }, singleLine = true
                        )
                    }
                    else -> {}
                }
                if (ti < triggers.lastIndex) HorizontalDivider(color = Hairline, modifier = Modifier.padding(top = 10.dp))
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { triggers = triggers + Trigger() }, contentPadding = PaddingValues(0.dp)) {
                Text("+ Auslöser hinzufügen", color = Blue, fontSize = 13.sp)
            }
        }

        // ================= Flowchart / Entscheidungsbaum =================
        SectionTitle("Flowchart  ·  Auslöser fließt von oben nach unten, erster passender Zweig gewinnt")
        // Start node: the triggers feed into the branch flow below.
        Row(Modifier.height(IntrinsicSize.Min)) {
            Column(Modifier.width(20.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.padding(top = 16.dp).size(10.dp).clip(CircleShape).background(Violet))
                Box(Modifier.padding(top = 4.dp).width(2.dp).weight(1f).background(Violet.copy(alpha = 0.5f)))
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text("START", color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp)
                Text(
                    triggers.joinToString("  oder  ") { t ->
                        when (t.type) {
                            TriggerType.MANUAL -> "Button/Widget"
                            TriggerType.NFC -> "NFC-Tag"
                            TriggerType.DEVICE_STATE -> "Hue-Licht"
                            TriggerType.LEAVE_WIFI -> "WLAN verlassen"
                            TriggerType.TIME -> "Uhrzeit ${t.time}"
                            TriggerType.SUN -> if (t.sunEvent == "SUNRISE") "Sonnenaufgang" else "Sonnenuntergang"
                            TriggerType.ARRIVE_HOME -> "Ankunft zuhause"
                            TriggerType.LEAVE_HOME -> "Verlassen (GPS)"
                        }
                    },
                    color = TextPrim, fontSize = 13.sp
                )
            }
        }
        branches.forEachIndexed { bi, br ->
            Row(Modifier.height(IntrinsicSize.Min)) {
                // Baum-Schiene: Knoten + Linie
                Column(
                    Modifier.width(20.dp).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.padding(top = 18.dp).size(8.dp).clip(CircleShape)
                            .background(if (br.conditions.isEmpty()) TextSec else Violet)
                    )
                    if (bi < branches.lastIndex) {
                        Box(
                            Modifier.padding(top = 4.dp).width(1.dp).weight(1f)
                                .background(Hairline)
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                GradientCard(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            branchTitle(bi, br, branches.size),
                            color = if (br.conditions.isEmpty()) TextSec else Violet,
                            fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        if (bi > 0) IconButton(onClick = { branches = branches.swap(bi, bi - 1) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.KeyboardArrowUp, "Hoch", tint = TextSec)
                        }
                        if (bi < branches.lastIndex) IconButton(onClick = { branches = branches.swap(bi, bi + 1) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.KeyboardArrowDown, "Runter", tint = TextSec)
                        }
                        if (branches.size > 1) IconButton(onClick = {
                            branches = branches.filterIndexed { i, _ -> i != bi }
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, "Zweig löschen", tint = TextSec)
                        }
                    }

                    // Bedingungen
                    if (br.conditions.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            br.conditions.forEachIndexed { ci, c ->
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        branches = branches.mapIndexed { i, v ->
                                            if (i == bi) v.copy(conditions = v.conditions.filterIndexed { j, _ -> j != ci }) else v
                                        }
                                    },
                                    label = { Text(condLabel(c), fontSize = 12.sp) },
                                    trailingIcon = { Text("×", color = TextSec) }
                                )
                            }
                        }
                    }
                    TextButton(onClick = { condDialogFor = bi }, contentPadding = PaddingValues(0.dp)) {
                        Text("+ Bedingung", color = Blue, fontSize = 13.sp)
                    }

                    HorizontalDivider(color = Hairline)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("dann:", color = TextSec, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        if (br.actions.isNotEmpty()) TextButton(
                            onClick = { actionClipboard = br.actions },
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) { Text("Kopieren", color = TextSec, fontSize = 12.sp) }
                        if (actionClipboard.isNotEmpty()) TextButton(
                            onClick = {
                                branches = branches.mapIndexed { i, v ->
                                    if (i == bi) v.copy(actions = v.actions + actionClipboard) else v
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) { Text("Einfügen (${actionClipboard.size})", color = Blue, fontSize = 12.sp) }
                    }

                    br.actions.forEachIndexed { ai, action ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            // Drag handle: drag up/down to reorder actions within the branch.
                            ActionDragHandle(ai, br.actions.size) { from, to ->
                                branches = branches.mapIndexed { i, v ->
                                    if (i == bi) v.copy(actions = v.actions.moveAt(from, to)) else v
                                }
                            }
                            Column(
                                Modifier.weight(1f).clickable { actionDialog = bi to action }.padding(vertical = 4.dp)
                            ) {
                                Text(describeAction(action, hueLights), color = TextPrim, fontSize = 14.sp)
                                if (action.command == "play_uri" && action.params["uri"].isNullOrBlank()) {
                                    Text("⚠ Sound-URL fehlt, antippen", color = Pink, fontSize = 12.sp)
                                }
                            }
                            IconButton(onClick = {
                                branches = branches.mapIndexed { i, v ->
                                    if (i == bi) v.copy(actions = v.actions.filterIndexed { j, _ -> j != ai }) else v
                                }
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Delete, "Aktion löschen", tint = TextSec)
                            }
                        }
                    }
                    TextButton(onClick = { actionDialog = bi to null }, contentPadding = PaddingValues(0.dp)) {
                        Text("+ Aktion", color = Violet, fontSize = 13.sp)
                    }
                }
            }
        }
        TextButton(onClick = { branches = branches + Variant() }) {
            Text("+ Zweig hinzufügen (Sonst wenn … / Sonst)", color = Blue)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    save()
                    justSaved = true
                    scope.launch { kotlinx.coroutines.delay(650); onClose() }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (justSaved) Green else Violet
                ),
                modifier = Modifier.weight(1f)
            ) {
                AnimatedContent(targetState = justSaved, label = "saveLabel") { saved ->
                    if (saved) Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Gespeichert")
                    } else Text("Speichern")
                }
            }
            OutlinedButton(
                onClick = { RoutineEngine.runAsync(ctx, save()) },
                modifier = Modifier.weight(1f)
            ) { Text("Testen ▶", color = TextPrim) }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showIconPicker) {
        IconPickerDialog(
            suggestion = suggestIcon(name),
            onDismiss = { showIconPicker = false },
            onPick = { icon = it }
        )
    }

    condDialogFor?.let { bi ->
        CondDialog(
            onDismiss = { condDialogFor = null },
            onConfirm = { c ->
                branches = branches.mapIndexed { i, v -> if (i == bi) v.copy(conditions = v.conditions + c) else v }
                condDialogFor = null
            }
        )
    }

    actionDialog?.let { (bi, editing) ->
        ActionDialog(
            initial = editing,
            hueLights = hueLights,
            onDismiss = { actionDialog = null },
            onConfirm = { newAction ->
                branches = branches.mapIndexed { i, v ->
                    if (i != bi) v
                    else if (editing == null) v.copy(actions = v.actions + newAction)
                    else v.copy(actions = v.actions.map { if (it == editing) newAction else it })
                }
                actionDialog = null
            }
        )
    }
}

@Composable
private fun ActionDragHandle(index: Int, total: Int, onMove: (from: Int, to: Int) -> Unit) {
    val stepPx = with(androidx.compose.ui.platform.LocalDensity.current) { 44.dp.toPx() }
    var accum by remember(index) { mutableStateOf(0f) }
    var cur by remember(index) { mutableStateOf(index) }
    Icon(
        Icons.Filled.Menu, contentDescription = "Ziehen zum Sortieren", tint = TextSec,
        modifier = Modifier
            .size(26.dp)
            .pointerInput(index, total) {
                detectDragGestures(
                    onDragStart = { accum = 0f; cur = index },
                    onDrag = { change, drag ->
                        change.consume()
                        accum += drag.y
                        while (accum <= -stepPx && cur > 0) { onMove(cur, cur - 1); cur--; accum += stepPx }
                        while (accum >= stepPx && cur < total - 1) { onMove(cur, cur + 1); cur++; accum -= stepPx }
                    }
                )
            }
    )
}

private fun <T> List<T>.moveAt(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    val m = toMutableList(); val item = m.removeAt(from); m.add(to, item); return m
}


private fun List<Variant>.swap(a: Int, b: Int): List<Variant> {
    val m = toMutableList(); val t = m[a]; m[a] = m[b]; m[b] = t; return m
}

private fun branchTitle(bi: Int, br: Variant, total: Int): String = when {
    br.conditions.isNotEmpty() && bi == 0 -> "Wenn"
    br.conditions.isNotEmpty() -> "Sonst wenn"
    total == 1 -> "Immer"
    bi == total - 1 -> "Sonst"
    else -> "Immer (fängt alles ab, nach unten schieben?)"
}

fun condLabel(c: Cond): String {
    val cfg = Store.config.value
    val sp = cfg.sonos.firstOrNull { it.ip == c.deviceId }?.name ?: c.deviceId
    return when (c.type) {
        CondType.DAY -> "Tagsüber"
        CondType.NIGHT -> "Nachts"
        CondType.SPEAKER_IDLE -> "$sp: nichts läuft"
        CondType.SPEAKER_PLAYING -> "$sp: spielt gerade"
        CondType.PARTNER_HOME -> "Partnerin zuhause"
        CondType.PARTNER_AWAY -> "Partnerin unterwegs"
    }
}

@Composable
private fun CondDialog(onDismiss: () -> Unit, onConfirm: (Cond) -> Unit) {
    val cfg = Store.config.value
    var type by remember { mutableStateOf(CondType.DAY) }
    var speakerIp by remember { mutableStateOf(cfg.sonos.firstOrNull()?.ip ?: "") }
    val needsSpeaker = type == CondType.SPEAKER_IDLE || type == CondType.SPEAKER_PLAYING

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Text("Bedingung hinzufügen", color = TextPrim) },
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
                        RadioButton(selected = type == t, onClick = { type = t },
                            colors = RadioButtonDefaults.colors(selectedColor = Violet))
                        Text(label, color = TextPrim, fontSize = 14.sp)
                    }
                }
                if (needsSpeaker) {
                    var open by remember { mutableStateOf(false) }
                    val label = cfg.sonos.firstOrNull { it.ip == speakerIp }?.name ?: "Speaker wählen…"
                    Box {
                        OutlinedButton(onClick = { open = true }) { Text(label, color = TextPrim) }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            cfg.sonos.forEach { s ->
                                DropdownMenuItem(text = { Text(s.name) },
                                    onClick = { speakerIp = s.ip; open = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(Cond(type, if (needsSpeaker) speakerIp else ""))
            }) { Text("Hinzufügen", color = Violet) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = TextSec) } }
    )
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
    var onState by remember { mutableStateOf(initial?.params?.get("on") ?: "") }
    var color by remember { mutableStateOf(initial?.params?.get("color") ?: "") }
    var brightness by remember { mutableStateOf(initial?.params?.get("brightness") ?: "") }
    var excluded by remember { mutableStateOf(initial?.params?.get("exclude")?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()) }
    var volume by remember { mutableStateOf(initial?.params?.get("volume") ?: "") }
    var uri by remember { mutableStateOf(initial?.params?.get("uri") ?: "") }
    var appId by remember { mutableStateOf(initial?.params?.get("appId") ?: "netflix") }
    var contentId by remember { mutableStateOf(initial?.params?.get("contentId") ?: "") }
    var onlyIfIdle by remember { mutableStateOf(initial?.params?.get("onlyIfIdle") == "true") }
    var showWheel by remember { mutableStateOf(false) }
    var showRadio by remember { mutableStateOf(false) }

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
                                    TargetType.GENERIC -> cfg.generics.firstOrNull()?.name ?: ""
                                }
                                command = when (t) {
                                    TargetType.HUE -> "set"; TargetType.SONOS -> "play"; TargetType.LG_TV -> "off"; TargetType.GENERIC -> "fire"
                                }
                            },
                            label = { Text(when (t) {
                                TargetType.HUE -> "Hue"; TargetType.SONOS -> "Sonos"; TargetType.LG_TV -> "TV"; TargetType.GENERIC -> "HTTP"
                            }) }
                        )
                    }
                }
                // Device picker: horizontal chip rail (Spotify device-picker feel)
                val devices: List<Pair<String, String>> = when (target) {
                    TargetType.HUE -> listOf("all" to "💡 Alle Lampen") + hueLights.map { it.id to "💡 ${it.name}" }
                    TargetType.SONOS -> listOf("all" to "🔊 Alle Speaker") + cfg.sonos.map { it.ip to "🔊 ${it.name}" }
                    TargetType.LG_TV -> cfg.tvs.map { it.ip to "📺 ${it.name}" }
                    TargetType.GENERIC -> cfg.generics.map { it.name to "🔌 ${it.name}" }
                }
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(devices.size) { di ->
                        val (id, label) = devices[di]
                        FilterChip(
                            selected = deviceId == id,
                            onClick = { deviceId = id },
                            label = { Text(label) },
                            shape = CircleShape
                        )
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
                                    Modifier.size(24.dp).clip(CircleShape).background(c)
                                        .clickable { color = if (color == hex) "" else hex }
                                )
                            }
                        }
                        TextButton(onClick = { showWheel = true }, contentPadding = PaddingValues(0.dp)) {
                            Text("🎨 Farbrad öffnen", color = Violet)
                        }
                        if (color.isNotEmpty()) Text("Farbe: $color", color = TextSec, fontSize = 12.sp)
                        OutlinedTextField(value = brightness, onValueChange = { brightness = it },
                            label = { Text("Helligkeit 1-100 (leer = unverändert)") }, singleLine = true)
                        if (deviceId == "all" && hueLights.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("Ausnehmen (bleiben unverändert):", color = TextSec, fontSize = 12.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                hueLights.forEach { l ->
                                    FilterChip(
                                        selected = l.id in excluded,
                                        onClick = { excluded = if (l.id in excluded) excluded - l.id else excluded + l.id },
                                        label = { Text(l.name, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
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
                        if (command == "play" || command == "play_uri") {
                            FilterChip(
                                selected = onlyIfIdle,
                                onClick = { onlyIfIdle = !onlyIfIdle },
                                label = { Text("Nur wenn gerade nichts läuft") }
                            )
                        }
                        if (command == "volume" || command == "play_uri") {
                            OutlinedTextField(value = volume, onValueChange = { volume = it },
                                label = { Text("Lautstärke 0-100") }, singleLine = true)
                        }
                        if (command == "play_uri") {
                            OutlinedTextField(value = uri, onValueChange = { uri = it },
                                label = { Text("Audio-URL (MP3/Stream, kein YouTube)") }, singleLine = true)
                            TextButton(onClick = { showRadio = true }, contentPadding = PaddingValues(0.dp)) {
                                Text("🔍 Sounds & Sender suchen", color = Blue)
                            }
                        }
                    }
                    TargetType.LG_TV -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("on" to "An", "off" to "Aus", "mute" to "Stumm").forEach { (c, l) ->
                                FilterChip(selected = command == c, onClick = { command = c }, label = { Text(l) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("volume" to "Lautstärke", "app" to "App öffnen").forEach { (c, l) ->
                                FilterChip(selected = command == c, onClick = { command = c }, label = { Text(l) })
                            }
                        }
                        if (command == "volume") {
                            OutlinedTextField(value = volume, onValueChange = { volume = it },
                                label = { Text("Lautstärke 0-100") }, singleLine = true)
                        }
                        if (command == "app") {
                            var appOpen by remember { mutableStateOf(false) }
                            val appLabel = tvApps.firstOrNull { it.first == appId }?.second ?: "App wählen…"
                            Box {
                                OutlinedButton(onClick = { appOpen = true }) { Text(appLabel, color = TextPrim) }
                                DropdownMenu(expanded = appOpen, onDismissRequest = { appOpen = false }) {
                                    tvApps.forEach { (id, label) ->
                                        DropdownMenuItem(text = { Text(label) },
                                            onClick = { appId = id; appOpen = false })
                                    }
                                }
                            }
                            OutlinedTextField(value = contentId, onValueChange = { contentId = it },
                                label = { Text("Optional: YouTube-Link/-ID oder Netflix-Titel-ID") }, singleLine = true)
                            HintText("Leer = App öffnet normal. Mit YouTube-Link startet direkt das Video.")
                        }
                    }
                    TargetType.GENERIC -> {
                        HintText("Dieses Gerät sendet seine HTTP-Anfrage. Bearbeite URL/Methode im Geräte-Tab.")
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
                        if (deviceId == "all" && excluded.isNotEmpty()) params["exclude"] = excluded.joinToString(",")
                    }
                    TargetType.SONOS -> {
                        volume.toIntOrNull()?.let { params["volume"] = it.coerceIn(0, 100).toString() }
                        if (command == "play_uri") params["uri"] = uri.trim()
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
            }) { Text("OK", color = Violet) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = TextSec) } }
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
