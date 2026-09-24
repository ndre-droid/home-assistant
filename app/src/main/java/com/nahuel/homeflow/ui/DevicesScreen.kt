package com.nahuel.homeflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.nahuel.homeflow.data.*
import com.nahuel.homeflow.devices.HueClient
import com.nahuel.homeflow.devices.HueLight
import com.nahuel.homeflow.devices.LgTvClient
import com.nahuel.homeflow.devices.SonosClient
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val colorPresets = listOf(
    "#FFFFFF" to Color.White, "#FFB74D" to Color(0xFFFFB74D), "#F062A6" to Color(0xFFF062A6),
    "#34D399" to Color(0xFF34D399), "#3D8BFD" to Color(0xFF3D8BFD), "#8B7CF7" to Color(0xFF8B7CF7), "#EF4444" to Color(0xFFEF4444)
)

/** Applies the user's custom drag&drop order; unknown lights go to the end. */
fun orderLights(lights: List<HueLight>, order: List<String>): List<HueLight> {
    val idx = order.withIndex().associate { it.value to it.index }
    return lights.sortedBy { idx[it.id] ?: Int.MAX_VALUE }
}

/**
 * Integration group: uppercase header with its actions, 2dp rule, then the rows.
 * Tapping the header collapses the group; that state survives tab switches.
 */
@Composable
private fun DeviceGroup(
    key: String,
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(Bg)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 10.dp)
        ) {
            SectionLabel(title, Modifier.weight(1f))
            if (expanded) actions()
            Text(if (expanded) "−" else "+", color = Muted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Rule()
        if (expanded) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
            Rule()
        }
    }
}

@Composable
fun DevicesScreen(modifier: Modifier = Modifier) {
    val config by Store.config.collectAsState()
    val scope = rememberCoroutineScope()
    var lights by remember { mutableStateOf<List<HueLight>>(emptyList()) }
    var status by remember { mutableStateOf("") }

    fun refreshLights() {
        if (config.hueAppKey.isEmpty()) return
        scope.launch {
            HueClient.lights()
                .onSuccess { lights = orderLights(it, Store.config.value.lightOrder) }
                .onFailure { status = "Hue: ${it.message}" }
        }
    }
    LaunchedEffect(config.hueAppKey, config.hueBridgeIp) { refreshLights() }
    LaunchedEffect(config.lightOrder) { lights = orderLights(lights, config.lightOrder) }

    Column(modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        TopBar("Geräte") {
            SecondaryButton("Alles aus") {
                scope.launch {
                    HueClient.setLight("all", on = false, brightness = null, colorHex = null)
                    config.sonos.forEach { s -> scope.launch { SonosClient.pause(s.ip) } }
                    config.tvs.forEach { t -> scope.launch { LgTvClient.powerOff(t.ip, t.clientKey) } }
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (status.isNotEmpty()) {
                Column {
                    Text(
                        status,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AccentTint)
                            .clickable { status = "" }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                    Rule()
                }
            }

            HueSection(config, lights, onStatus = { status = it }, onRefresh = { refreshLights() })
            SonosSection(config, onStatus = { status = it })
            TvSection(config, onStatus = { status = it })
            GenericSection(config, onStatus = { status = it })
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Device line: square status dot, name, meta caption, trailing controls. */
@Composable
private fun DeviceRow(
    name: String,
    meta: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        if (leading != null) leading() else StatusDot(on)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (meta.isNotEmpty()) Caption(meta)
        }
        trailing()
    }
}

// ---------------------------------------------------------------- HUE

@Composable
private fun HueSection(config: Config, lights: List<HueLight>, onStatus: (String) -> Unit, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var bridgeIp by remember(config.hueBridgeIp) { mutableStateOf(config.hueBridgeIp) }
    var sortMode by remember { mutableStateOf(false) }

    DeviceGroup(
        key = "hue", title = "Philips Hue",
        actions = {
            if (config.hueAppKey.isNotEmpty()) {
                GhostButton(if (sortMode) "Fertig" else "Sortieren") { sortMode = !sortMode }
                GhostButton("Aktualisieren", onClick = onRefresh)
            }
        }
    ) {
        if (config.hueAppKey.isEmpty()) {
            FlatField("Bridge-IP", bridgeIp, placeholder = "192.168.178.30") { bridgeIp = it }
            Spacer(Modifier.height(8.dp))
            HintText("IP: Hue-App → Einstellungen → Meine Hue-Systeme. Knopf auf der Bridge drücken, dann Koppeln.")
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Koppeln") {
                scope.launch {
                    HueClient.pair(bridgeIp.trim())
                        .onSuccess { key ->
                            Store.updateConfig { it.copy(hueBridgeIp = bridgeIp.trim(), hueAppKey = key) }
                            onStatus("Hue verbunden ✓")
                        }
                        .onFailure { onStatus("Hue: ${it.message}") }
                }
            }
        } else if (sortMode) {
            SortableLightList(lights)
        } else {
            lights.forEach { light -> LightRow(light) }
            if (lights.isEmpty()) HintText("Keine Lampen gefunden, Aktualisieren tippen.")
        }
    }
}

/** Drag&drop: hold the ☰ handle, pull the row to its new spot, release. */
@Composable
private fun SortableLightList(lights: List<HueLight>) {
    val rowHeight = 48.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    var dragIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }

    HintText("Halte ☰ gedrückt und ziehe die Lampe an ihre Position.")
    Spacer(Modifier.height(6.dp))
    Column {
        lights.forEachIndexed { i, light ->
            val dragged = i == dragIndex
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(if (dragged) 1f else 0f)
                    .graphicsLayer { translationY = if (dragged) dragOffset else 0f }
                    .background(if (dragged) AccentTint else Color.Transparent)
            ) {
                Text(
                    "☰",
                    color = if (dragged) AccentText else Muted,
                    fontSize = 15.sp,
                    modifier = Modifier.pointerInput(light.id, lights.size) {
                        detectDragGestures(
                            onDragStart = { dragIndex = i; dragOffset = 0f },
                            onDrag = { change, amount -> change.consume(); dragOffset += amount.y },
                            onDragEnd = {
                                val target = (i + (dragOffset / rowHeightPx).roundToInt())
                                    .coerceIn(0, lights.lastIndex)
                                if (target != i) {
                                    val newOrder = lights.map { it.id }.toMutableList()
                                    val moved = newOrder.removeAt(i)
                                    newOrder.add(target, moved)
                                    Store.updateConfig { it.copy(lightOrder = newOrder) }
                                }
                                dragIndex = -1; dragOffset = 0f
                            },
                            onDragCancel = { dragIndex = -1; dragOffset = 0f }
                        )
                    }
                )
                Spacer(Modifier.width(14.dp))
                Text(light.name, color = Ink, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun LightRow(light: HueLight) {
    val scope = rememberCoroutineScope()
    var on by remember(light.id, light.on) { mutableStateOf(light.on) }
    var brightness by remember(light.id) { mutableStateOf(light.brightness.toFloat()) }
    var showWheel by remember { mutableStateOf(false) }

    Column {
        DeviceRow(
            name = light.name,
            meta = if (on) "An · ${brightness.toInt()} %" else "Aus",
            on = on
        ) {
            FlatToggle(on) { v ->
                on = v
                scope.launch { HueClient.setLight(light.id, on = v, brightness = null, colorHex = null) }
            }
        }
        if (on) {
            Slider(
                value = brightness, onValueChange = { brightness = it },
                onValueChangeFinished = {
                    scope.launch {
                        HueClient.setLight(light.id, on = null, brightness = brightness.toInt(), colorHex = null)
                    }
                },
                valueRange = 1f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = Fill
                )
            )
            if (light.supportsColor) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    colorPresets.forEach { (hex, c) ->
                        Box(
                            Modifier
                                .size(24.dp)
                                .background(c)
                                .clickable {
                                    scope.launch {
                                        HueClient.setLight(light.id, on = null, brightness = null, colorHex = hex)
                                    }
                                }
                        )
                    }
                    Box(
                        Modifier
                            .size(24.dp)
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                                        Color.Blue, Color.Magenta, Color.Red
                                    )
                                )
                            )
                            .clickable { showWheel = true }
                    )
                }
            }
            if (showWheel) {
                ColorWheelDialog(
                    initialHex = light.colorHex,
                    onDismiss = { showWheel = false },
                    onPick = { hex ->
                        scope.launch { HueClient.setLight(light.id, on = null, brightness = null, colorHex = hex) }
                    }
                )
            }
        }
        Rule()
    }
}

// ---------------------------------------------------------------- SONOS

@Composable
private fun SonosSection(config: Config, onStatus: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }

    DeviceGroup(
        key = "sonos", title = "Sonos",
        actions = {
            GhostButton(if (searching) "Suche…" else "+ Suchen") {
                searching = true
                scope.launch {
                    val found = SonosClient.discover()
                    if (found.isEmpty()) onStatus("Sonos: nichts gefunden (gleiches WLAN?)")
                    else Store.updateConfig { cfg ->
                        // merge by room name: refreshes stale IPs instead of duplicating entries
                        val byName = cfg.sonos.associateBy { it.name }.toMutableMap()
                        found.forEach { f -> byName[f.name] = f }
                        cfg.copy(sonos = byName.values.toList())
                    }.also { onStatus("Sonos aktualisiert: ${found.joinToString { it.name }}") }
                    searching = false
                }
            }
        }
    ) {
        if (config.sonos.isEmpty()) HintText("Tippe auf „Suchen\", um Beam & Era 100 zu finden.")
        config.sonos.forEachIndexed { i, sp -> SonosRow(sp, i, config.sonos.size, onStatus) }
    }
}

@Composable
private fun SonosRow(sp: SonosSpeaker, index: Int, total: Int, onStatus: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var volume by remember(sp.ip) { mutableStateOf(25f) }

    Column {
        DeviceRow(
            name = sp.name,
            meta = sp.ip,
            on = true,
            leading = { DragHandle(index, total) { from, to ->
                Store.updateConfig { it.copy(sonos = it.sonos.moveItem(from, to)) }
            } }
        ) {
            IconBoxButton(26.dp, "Play", onClick = {
                scope.launch {
                    SonosClient.setMute(sp.ip, false)
                    SonosClient.play(sp.ip).onFailure { onStatus("${sp.name}: ${it.message}") }
                }
            }) { Text("▶", color = Ink, fontSize = 10.sp) }
            Spacer(Modifier.width(6.dp))
            IconBoxButton(26.dp, "Pause", onClick = {
                scope.launch { SonosClient.pause(sp.ip).onFailure { onStatus("${sp.name}: ${it.message}") } }
            }) { Text("⏸", color = Ink, fontSize = 10.sp) }
            Spacer(Modifier.width(6.dp))
            IconBoxButton(26.dp, "Stumm", onClick = {
                scope.launch {
                    SonosClient.setMute(sp.ip, true)
                        .onSuccess { onStatus("${sp.name}: stumm ✓ (Play = wieder laut)") }
                        .onFailure { onStatus("${sp.name}: ${it.message}") }
                }
            }) { Text("✕", color = Ink, fontSize = 10.sp) }
            Spacer(Modifier.width(6.dp))
            IconBoxButton(26.dp, "Test", onClick = {
                scope.launch {
                    SonosClient.getVolume(sp.ip)
                        .onSuccess { onStatus("${sp.name}: erreichbar ✓ (Lautstärke $it %)") }
                        .onFailure { onStatus("${sp.name}: ${it.message}") }
                }
            }) { Text("?", color = Ink, fontSize = 11.sp) }
            Spacer(Modifier.width(6.dp))
            IconBoxButton(26.dp, "Entfernen", onClick = {
                Store.updateConfig { it.copy(sonos = it.sonos.filterNot { s -> s.ip == sp.ip }) }
            }) { Text("−", color = Ink, fontSize = 12.sp) }
        }
        Slider(
            value = volume, onValueChange = { volume = it },
            onValueChangeFinished = { scope.launch { SonosClient.setVolume(sp.ip, volume.toInt()) } },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Fill
            )
        )
        Rule()
    }
}

// ---------------------------------------------------------------- LG TV

@Composable
private fun TvSection(config: Config, onStatus: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("Wohnzimmer TV") }
    var ip by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }

    DeviceGroup(key = "tv", title = "LG TV") {
        config.tvs.forEachIndexed { ti, tv ->
            var editing by remember(tv.ip) { mutableStateOf(false) }
            var macEdit by remember(tv.ip) { mutableStateOf(tv.mac) }
            val paired = tv.clientKey.isNotEmpty() && tv.mac.isNotBlank()
            DeviceRow(
                name = tv.name,
                meta = buildString {
                    append(if (tv.clientKey.isEmpty()) "Nicht gekoppelt" else "Gekoppelt ✓")
                    append(if (tv.mac.isBlank()) "  ·  MAC fehlt" else "  ·  MAC ✓")
                    append("  ·  ${tv.ip}")
                },
                on = paired,
                leading = { DragHandle(ti, config.tvs.size) { from, to ->
                    Store.updateConfig { it.copy(tvs = it.tvs.moveItem(from, to)) }
                } }
            ) {
                GhostButton("An") {
                    scope.launch {
                        if (tv.mac.isBlank()) onStatus("TV an: MAC fehlt, Einstellungen antippen und MAC eintragen.")
                        else LgTvClient.powerOn(tv.mac)
                            .onSuccess { onStatus("${tv.name}: Einschalt-Signal gesendet (dauert ein paar Sek.)") }
                            .onFailure { onStatus("TV: ${it.message}") }
                    }
                }
                GhostButton("Aus", color = Muted) {
                    scope.launch {
                        LgTvClient.powerOff(tv.ip, tv.clientKey).onFailure {
                            onStatus("TV nicht erreichbar, ist er an? Bei ausgeschaltetem TV geht nur Einschalten.")
                        }
                    }
                }
                IconBoxButton(26.dp, "Bearbeiten", onClick = { editing = !editing }) {
                    Text("⚙", color = Ink, fontSize = 11.sp)
                }
            }
            if (editing) {
                Column(Modifier.padding(start = 20.dp, bottom = 10.dp)) {
                    FlatField("MAC (für Einschalten)", macEdit) { macEdit = it }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("Speichern") {
                            Store.updateConfig { cfg ->
                                cfg.copy(tvs = cfg.tvs.map { if (it.ip == tv.ip) it.copy(mac = macEdit.trim()) else it })
                            }
                            editing = false
                            onStatus("${tv.name}: MAC gespeichert ✓")
                        }
                        if (tv.clientKey.isEmpty()) {
                            SecondaryButton("Koppeln") {
                                onStatus("Kopplungs-Anfrage am TV bestätigen…")
                                scope.launch {
                                    LgTvClient.pair(tv.ip)
                                        .onSuccess { key ->
                                            Store.updateConfig { cfg ->
                                                cfg.copy(tvs = cfg.tvs.map { if (it.ip == tv.ip) it.copy(clientKey = key) else it })
                                            }
                                            onStatus("TV gekoppelt ✓")
                                        }
                                        .onFailure { onStatus("TV: ${it.message} (TV an? IP korrekt?)") }
                                }
                            }
                        }
                        GhostButton("TV entfernen", color = MaterialTheme.colorScheme.error) {
                            Store.updateConfig { it.copy(tvs = it.tvs.filterNot { t -> t.ip == tv.ip }) }
                        }
                    }
                }
            }
            Rule()
        }
        Spacer(Modifier.height(12.dp))
        FlatField("Name", name) { name = it }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { FlatField("IP", ip) { ip = it } }
            Box(Modifier.weight(1f)) { FlatField("MAC", mac) { mac = it } }
        }
        Spacer(Modifier.height(8.dp))
        HintText("IP & MAC: TV → Einstellungen → Netzwerk → WLAN → Erweitert. „Einschalten über WLAN\" am TV aktivieren.")
        Spacer(Modifier.height(10.dp))
        PrimaryButton("TV speichern", Modifier.fillMaxWidth()) {
            if (ip.isNotBlank()) {
                Store.updateConfig { it.copy(tvs = it.tvs + LgTv(name.trim(), ip.trim(), "", mac.trim())) }
                onStatus("TV \"${name.trim()}\" gespeichert. Jetzt koppeln (Zahnrad).")
                ip = ""; mac = ""
            } else onStatus("Bitte zuerst die IP eingeben.")
        }
    }
}

/**
 * Small drag handle. Drag it up/down; every ~44dp of travel moves the row one step.
 * Reliable inside a scrolling column (no LazyColumn reorder gymnastics).
 */
@Composable
private fun DragHandle(index: Int, total: Int, onMove: (from: Int, to: Int) -> Unit) {
    val density = LocalDensity.current
    val stepPx = with(density) { 44.dp.toPx() }
    var accum by remember(index) { mutableStateOf(0f) }
    var curIndex by remember(index) { mutableStateOf(index) }
    Text(
        "☰",
        color = Muted,
        fontSize = 14.sp,
        modifier = Modifier
            .size(24.dp)
            .pointerInput(index, total) {
                detectDragGestures(
                    onDragStart = { accum = 0f; curIndex = index },
                    onDragEnd = { accum = 0f },
                    onDrag = { change, drag ->
                        change.consume()
                        accum += drag.y
                        while (accum <= -stepPx && curIndex > 0) {
                            onMove(curIndex, curIndex - 1); curIndex--; accum += stepPx
                        }
                        while (accum >= stepPx && curIndex < total - 1) {
                            onMove(curIndex, curIndex + 1); curIndex++; accum -= stepPx
                        }
                    }
                )
            }
    )
}

private fun <T> List<T>.moveItem(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    val m = toMutableList(); val item = m.removeAt(from); m.add(to, item); return m
}

/** User-defined HTTP/webhook devices (Shelly, Tasmota, Home Assistant, IFTTT...). */
@Composable
private fun GenericSection(config: Config, onStatus: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var post by remember { mutableStateOf(false) }
    var body by remember { mutableStateOf("") }

    DeviceGroup(key = "generic", title = "Weitere Geräte (HTTP / Webhook)") {
        config.generics.forEach { g ->
            Column {
                DeviceRow(name = g.name, meta = "${g.method}  ${g.url}", on = true) {
                    GhostButton("Test") {
                        scope.launch {
                            com.nahuel.homeflow.devices.GenericClient.fire(g.url, g.method, g.body)
                                .onSuccess { onStatus("${g.name}: OK") }
                                .onFailure { onStatus("${g.name}: ${it.message}") }
                        }
                    }
                    IconBoxButton(26.dp, "Entfernen", onClick = {
                        Store.updateConfig { it.copy(generics = it.generics.filterNot { d -> d.name == g.name }) }
                    }) { Text("−", color = Ink, fontSize = 12.sp) }
                }
                Rule()
            }
        }
        if (config.generics.isEmpty())
            HintText("Beliebige Geräte per URL steuern: Shelly, Tasmota, Home Assistant, IFTTT. Trage Name und URL ein.")
        Spacer(Modifier.height(12.dp))
        FlatField("Name", name) { name = it }
        Spacer(Modifier.height(8.dp))
        FlatField("URL", url, placeholder = "http://…") { url = it }
        Spacer(Modifier.height(10.dp))
        SegmentedControl(listOf("GET", "POST"), if (post) 1 else 0) { post = it == 1 }
        if (post) {
            Spacer(Modifier.height(8.dp))
            FlatField("Body (JSON, optional)", body) { body = it }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Gerät speichern", Modifier.fillMaxWidth()) {
            if (name.isNotBlank() && url.isNotBlank()) {
                Store.updateConfig {
                    it.copy(
                        generics = it.generics +
                            GenericDevice(name.trim(), url.trim(), if (post) "POST" else "GET", body.trim())
                    )
                }
                onStatus("${name.trim()} gespeichert")
                name = ""; url = ""; body = ""
            } else onStatus("Name und URL eingeben.")
        }
    }
}
