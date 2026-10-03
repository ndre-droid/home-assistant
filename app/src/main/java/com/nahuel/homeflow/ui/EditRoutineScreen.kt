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

/**
 * Routine builder, Modernist layout: a top bar carrying the routine icon, name
 * and the Run button, then stacked sections. Each branch is a bordered block
 * with a filled header bar over numbered action rows.
 */
@Composable
fun EditRoutineScreen(routineId: String?, onClose: () -> Unit, onRequestNfcWrite: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = remember(routineId) { routineId?.takeIf { it.isNotEmpty() }?.let { Store.routine(it) } }

    val draftId by remember { mutableStateOf(existing?.id ?: UUID.randomUUID().toString()) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var icon by remember { mutableStateOf(existing?.icon ?: "") }
    var showIconPicker by remember { mutableStateOf(false) }
    val enabled by remember { mutableStateOf(existing?.enabled ?: true) }
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

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {

        // ---- Top bar: back, icon, title, Run ----
        Column(Modifier.fillMaxWidth().background(Bg)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBoxButton(30.dp, "Zurück", onClick = onClose) {
                    Text("←", color = Ink, fontSize = 14.sp)
                }
                Spacer(Modifier.width(12.dp))
                IconBox(36.dp, Modifier.clickable { showIconPicker = true }) {
                    Text(icon.ifEmpty { suggestIcon(name) ?: "▶️" }, fontSize = 18.sp)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    name.ifBlank { if (existing == null) "Neue Automation" else "Bearbeiten" },
                    color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp, maxLines = 1, modifier = Modifier.weight(1f)
                )
                PrimaryButton("Testen") { RoutineEngine.runAsync(ctx, save()) }
            }
            Rule()
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            if (existing == null) {
                Section("Mit Worten beschreiben  ·  offline") {
                    var nlText by remember { mutableStateOf("") }
                    var nlError by remember { mutableStateOf("") }
                    FlatField(
                        label = "Beschreibung",
                        value = nlText,
                        placeholder = "z. B. Wenn das Badlicht angeht: Licht grün und Vogelsounds auf Bad",
                        singleLine = false,
                        minLines = 2
                    ) { nlText = it }
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Erstellen", enabled = nlText.isNotBlank()) {
                        nlError = ""
                        com.nahuel.homeflow.engine.NlParser.parse(nlText, Store.config.value, hueLights)
                            .onSuccess { parsed ->
                                if (name.isBlank()) name = parsed.name
                                triggers = parsed.triggers
                                branches = parsed.variants
                            }
                            .onFailure { nlError = it.message ?: "Nicht verstanden" }
                    }
                    if (nlError.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(nlError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    HintText("Versteht Deutsch & Englisch, läuft komplett auf dem Gerät. Ergebnis unten prüfen & anpassen.")
                }
            }

            // ---- Name & Icon ----
            Section("Name & Icon") {
                FlatField("Name", name, placeholder = "z. B. Badlicht-Sound") { name = it }
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Icon ändern") { showIconPicker = true }
            }

            // ---- Trigger ----
            Section("Auslöser  ·  einer davon genügt") {
                triggers.forEachIndexed { ti, trg ->
                    if (ti > 0) {
                        Spacer(Modifier.height(12.dp))
                        Rule()
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Auslöser ${ti + 1}", Modifier.weight(1f))
                        if (triggers.size > 1) {
                            IconBoxButton(22.dp, "Auslöser entfernen", onClick = {
                                triggers = triggers.filterIndexed { k, _ -> k != ti }
                            }) { Text("−", color = Ink, fontSize = 11.sp) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ChipFlow {
                        TriggerType.entries.forEach { t ->
                            ChoiceChip(
                                label = when (t) {
                                    TriggerType.MANUAL -> "Button"
                                    TriggerType.NFC -> "NFC"
                                    TriggerType.DEVICE_STATE -> "Hue-Licht"
                                    TriggerType.LEAVE_WIFI -> "WLAN weg"
                                    TriggerType.TIME -> "Uhrzeit"
                                    TriggerType.SUN -> "Sonne"
                                    TriggerType.ARRIVE_HOME -> "Ankunft"
                                    TriggerType.LEAVE_HOME -> "Weggehen"
                                },
                                selected = trg.type == t
                            ) { triggers = triggers.mapIndexed { k, x -> if (k == ti) x.copy(type = t) else x } }
                        }
                    }

                    fun upd(block: (Trigger) -> Trigger) {
                        triggers = triggers.mapIndexed { k, x -> if (k == ti) block(x) else x }
                    }
                    when (trg.type) {
                        TriggerType.DEVICE_STATE -> {
                            Spacer(Modifier.height(10.dp))
                            LightPicker(hueLights, trg.hueLightId) { picked -> upd { it.copy(hueLightId = picked) } }
                            Spacer(Modifier.height(8.dp))
                            ChipFlow {
                                ChoiceChip("wird eingeschaltet", trg.toState) { upd { it.copy(toState = true) } }
                                ChoiceChip("wird ausgeschaltet", !trg.toState) { upd { it.copy(toState = false) } }
                            }
                        }
                        TriggerType.NFC -> {
                            Spacer(Modifier.height(10.dp))
                            SecondaryButton("NFC-Tag beschreiben") { save(); onRequestNfcWrite(draftId) }
                        }
                        TriggerType.LEAVE_WIFI -> {
                            Spacer(Modifier.height(10.dp))
                            ChoiceChip("Nur wenn Partnerin nicht zuhause", trg.partnerAware) {
                                upd { it.copy(partnerAware = !it.partnerAware) }
                            }
                            Spacer(Modifier.height(8.dp))
                            HintText("Löst aus, wenn dein Handy das Heim-WLAN verliert. Für die Ausführung danach muss Tailscale aktiv sein.")
                        }
                        TriggerType.TIME -> {
                            Spacer(Modifier.height(10.dp))
                            FlatField("Uhrzeit (HH:MM)", trg.time) { v -> upd { it.copy(time = v) } }
                            Spacer(Modifier.height(8.dp))
                            HintText("Läuft täglich zu dieser Uhrzeit.")
                        }
                        TriggerType.SUN -> {
                            Spacer(Modifier.height(10.dp))
                            ChipFlow {
                                ChoiceChip("Sonnenaufgang", trg.sunEvent == "SUNRISE") { upd { it.copy(sunEvent = "SUNRISE") } }
                                ChoiceChip("Sonnenuntergang", trg.sunEvent == "SUNSET") { upd { it.copy(sunEvent = "SUNSET") } }
                            }
                            Spacer(Modifier.height(10.dp))
                            FlatField("Minuten davor (-) / danach (+)", trg.sunOffsetMin.toString()) { v ->
                                upd { it.copy(sunOffsetMin = v.toIntOrNull() ?: 0) }
                            }
                        }
                        else -> {}
                    }
                }
                Spacer(Modifier.height(12.dp))
                GhostButton("+ Auslöser hinzufügen") { triggers = triggers + Trigger() }
            }

            // ---- Flow: start node + branches ----
            Section("Dann  ·  erster passender Zweig gewinnt") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(Accent))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        SectionLabel("Start")
                        Spacer(Modifier.height(2.dp))
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
                            color = Ink, fontSize = 13.sp
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                branches.forEachIndexed { bi, br ->
                    Column(Modifier.fillMaxWidth().border(RuleWidth, Divider)) {
                        // Branch header bar
                        Row(
                            Modifier.fillMaxWidth().background(Fill).padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                branchTitle(bi, br, branches.size),
                                color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (bi > 0) IconBoxButton(20.dp, "Hoch", onClick = { branches = branches.swap(bi, bi - 1) }) {
                                Text("↑", color = Ink, fontSize = 10.sp)
                            }
                            if (bi < branches.lastIndex) {
                                Spacer(Modifier.width(4.dp))
                                IconBoxButton(20.dp, "Runter", onClick = { branches = branches.swap(bi, bi + 1) }) {
                                    Text("↓", color = Ink, fontSize = 10.sp)
                                }
                            }
                            if (branches.size > 1) {
                                Spacer(Modifier.width(4.dp))
                                IconBoxButton(20.dp, "Zweig löschen", onClick = {
                                    branches = branches.filterIndexed { i, _ -> i != bi }
                                }) { Text("−", color = Ink, fontSize = 11.sp) }
                            }
                        }
                        Rule()

                        // Conditions
                        Column(Modifier.padding(10.dp)) {
                            if (br.conditions.isNotEmpty()) {
                                ChipFlow {
                                    br.conditions.forEachIndexed { ci, c ->
                                        ChoiceChip(condLabel(c), selected = true, trailing = "×") {
                                            branches = branches.mapIndexed { i, v ->
                                                if (i == bi) v.copy(conditions = v.conditions.filterIndexed { j, _ -> j != ci }) else v
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            GhostButton("+ Bedingung") { condDialogFor = bi }
                        }
                        Rule()

                        // Actions
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionLabel("Aktionen", Modifier.weight(1f))
                            if (br.actions.isNotEmpty()) {
                                GhostButton("Kopieren", color = Muted) { actionClipboard = br.actions }
                            }
                            if (actionClipboard.isNotEmpty()) {
                                GhostButton("Einfügen (${actionClipboard.size})") {
                                    branches = branches.mapIndexed { i, v ->
                                        if (i == bi) v.copy(actions = v.actions + actionClipboard) else v
                                    }
                                }
                            }
                        }

                        br.actions.forEachIndexed { ai, action ->
                            Rule()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                IconBox(18.dp) {
                                    Text("${ai + 1}", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f).clickable { actionDialog = bi to action }) {
                                    Text(describeAction(action, hueLights), color = Ink, fontSize = 13.sp)
                                    if (action.command == "play_uri" && action.params["uri"].isNullOrBlank()) {
                                        Text(
                                            "⚠ Sound-URL fehlt, antippen",
                                            color = MaterialTheme.colorScheme.error, fontSize = 11.sp
                                        )
                                    }
                                }
                                ActionDragHandle(ai, br.actions.size) { from, to ->
                                    branches = branches.mapIndexed { i, v ->
                                        if (i == bi) v.copy(actions = v.actions.moveAt(from, to)) else v
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                IconBoxButton(22.dp, "Aktion löschen", onClick = {
                                    branches = branches.mapIndexed { i, v ->
                                        if (i == bi) v.copy(actions = v.actions.filterIndexed { j, _ -> j != ai }) else v
                                    }
                                }) { Text("−", color = Ink, fontSize = 11.sp) }
                            }
                        }
                        Rule()
                        Box(Modifier.padding(10.dp)) {
                            GhostButton("+ Aktion") { actionDialog = bi to null }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                GhostButton("+ Zweig hinzufügen (Sonst wenn … / Sonst)") { branches = branches + Variant() }
            }

            // ---- Save / delete ----
            Section {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        if (justSaved) "Gespeichert ✓" else "Speichern",
                        Modifier.weight(1f)
                    ) {
                        save()
                        justSaved = true
                        scope.launch { kotlinx.coroutines.delay(650); onClose() }
                    }
                    SecondaryButton("Abbrechen", onClick = onClose)
                    if (existing != null) {
                        GhostButton("Löschen", color = MaterialTheme.colorScheme.error) {
                            Store.deleteRoutine(draftId); TriggerService.sync(ctx); onClose()
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
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

/** Wrapping row of chips — the builder offers more choices than fit one line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipFlow(content: @Composable () -> Unit) {
    // Content takes no receiver on purpose: FlowRowScope is experimental and
    // would otherwise leak the opt-in requirement to every call site.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
private fun ActionDragHandle(index: Int, total: Int, onMove: (from: Int, to: Int) -> Unit) {
    val stepPx = with(LocalDensity.current) { 44.dp.toPx() }
    var accum by remember(index) { mutableStateOf(0f) }
    var cur by remember(index) { mutableStateOf(index) }
    Text(
        "☰",
        color = Muted,
        fontSize = 13.sp,
        modifier = Modifier
            .size(22.dp)
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

