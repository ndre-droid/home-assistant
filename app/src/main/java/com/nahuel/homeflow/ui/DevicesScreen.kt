package com.nahuel.homeflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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

/** Card with a tap-to-collapse header. Expansion survives tab switches. */
@Composable
private fun CollapsibleSection(
    key: String,
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(key) { mutableStateOf(true) }
    GradientCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
        ) {
            Text(title, color = TextPrim, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).padding(vertical = 6.dp))
            if (expanded) actions()
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Einklappen" else "Ausklappen",
                tint = TextSec
            )
        }
        if (expanded) content()
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

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Geräte", color = TextPrim, fontSize = 24.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    scope.launch {
                        HueClient.setLight("all", on = false, brightness = null, colorHex = null)
                        config.sonos.forEach { s -> scope.launch { SonosClient.pause(s.ip) } }
                        config.tvs.forEach { t -> scope.launch { LgTvClient.powerOff(t.ip, t.clientKey) } }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Surface2)
            ) { Text("Alles aus", color = TextPrim) }
        }
        if (status.isNotEmpty()) {
            Text(status, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                modifier = Modifier.clickable { status = "" })
        }

        HueSection(config, lights, onStatus = { status = it }, onRefresh = { refreshLights() })
        SonosSection(config, onStatus = { status = it })
        TvSection(config, onStatus = { status = it })
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- HUE

@Composable
private fun HueSection(config: Config, lights: List<HueLight>, onStatus: (String) -> Unit, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var bridgeIp by remember(config.hueBridgeIp) { mutableStateOf(config.hueBridgeIp) }
    var sortMode by remember { mutableStateOf(false) }

    CollapsibleSection(
        key = "hue", title = "Philips Hue",
        actions = {
            if (config.hueAppKey.isNotEmpty()) {
                TextButton(onClick = { sortMode = !sortMode }) {
                    Text(if (sortMode) "Fertig" else "Sortieren", color = Violet, fontSize = 13.sp)
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Aktualisieren", tint = TextSec) }
            }
        }
    ) {
        if (config.hueAppKey.isEmpty()) {
            OutlinedTextField(
                value = bridgeIp, onValueChange = { bridgeIp = it },
                label = { Text("Bridge-IP (z. B. 192.168.178.30)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            HintText("IP: Hue-App → Einstellungen → Meine Hue-Systeme. Knopf auf der Bridge drücken, dann Koppeln.")
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        HueClient.pair(bridgeIp.trim())
                            .onSuccess { key ->
                                Store.updateConfig { it.copy(hueBridgeIp = bridgeIp.trim(), hueAppKey = key) }
                                onStatus("Hue verbunden ✓")
                            }
                            .onFailure { onStatus("Hue: ${it.message}") }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Violet)
            ) { Text("Koppeln") }
        } else if (sortMode) {
            SortableLightList(lights)
        } else {
            lights.forEach { light -> LightRow(light) }
            if (lights.isEmpty()) HintText("Keine Lampen gefunden – Aktualisieren tippen.")
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
    Spacer(Modifier.height(4.dp))
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
                    .background(
                        if (dragged) Surface2 else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 6.dp)
            ) {
                Icon(
                    Icons.Filled.Menu, "Verschieben", tint = if (dragged) Violet else TextSec,
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
                Spacer(Modifier.width(12.dp))
                Text(light.name, color = TextPrim)
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

    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(light.name, color = TextPrim, modifier = Modifier.weight(1f))
            Switch(
                checked = on,
                onCheckedChange = { v ->
                    on = v
                    scope.launch { HueClient.setLight(light.id, on = v, brightness = null, colorHex = null) }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Violet, uncheckedTrackColor = Surface2)
            )
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
                colors = SliderDefaults.colors(thumbColor = Violet, activeTrackColor = Violet)
            )
            if (light.supportsColor) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    colorPresets.forEach { (hex, c) ->
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(c)
                                .clickable {
                                    scope.launch {
                                        HueClient.setLight(light.id, on = null, brightness = null, colorHex = hex)
                                    }
                                }
                        )
                    }
                    Box(
                        Modifier.size(26.dp).clip(CircleShape)
                            .background(Brush.sweepGradient(listOf(
                                Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                                Color.Blue, Color.Magenta, Color.Red
                            )))
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
    }
}

// ---------------------------------------------------------------- SONOS

@Composable
private fun SonosSection(config: Config, onStatus: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }

    CollapsibleSection(
        key = "sonos", title = "Sonos",
        actions = {
            TextButton(onClick = {
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
            }) { Text(if (searching) "Suche…" else "Suchen", color = Violet, fontSize = 13.sp) }
        }
    ) {
        if (config.sonos.isEmpty()) HintText("Tippe auf „Suchen\", um Beam & Era 100 zu finden.")
        config.sonos.forEach { sp -> SonosRow(sp, onStatus) }
    }
}

@Composable
private fun SonosRow(sp: SonosSpeaker, onStatus: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var volume by remember(sp.ip) { mutableStateOf(25f) }

    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sp.name, color = TextPrim, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                scope.launch {
                    SonosClient.setMute(sp.ip, true)
                        .onSuccess { onStatus("${sp.name}: stumm ✓ (Play = wieder laut)") }
                        .onFailure { onStatus("${sp.name}: ${it.message}") }
                }
            }) { Text("Stumm", color = Pink, fontSize = 13.sp) }
            TextButton(onClick = {
                scope.launch {
                    SonosClient.getVolume(sp.ip)
                        .onSuccess { onStatus("${sp.name}: erreichbar ✓ (Lautstärke $it %)") }
                        .onFailure { onStatus("${sp.name}: ${it.message}") }
                }
            }) { Text("Test", color = Green, fontSize = 13.sp) }
            IconButton(onClick = {
                scope.launch {
                    SonosClient.setMute(sp.ip, false)
                    SonosClient.play(sp.ip).onFailure { onStatus("${sp.name}: ${it.message}") }
                }
            }) { Icon(Icons.Filled.PlayArrow, "Play", tint = Green) }
            IconButton(onClick = {
                scope.launch { SonosClient.pause(sp.ip).onFailure { onStatus("${sp.name}: ${it.message}") } }
            }) { Icon(Icons.Filled.Clear, "Pause", tint = TextSec) }
            IconButton(onClick = {
                Store.updateConfig { it.copy(sonos = it.sonos.filterNot { s -> s.ip == sp.ip }) }
            }) { Icon(Icons.Filled.Delete, "Entfernen", tint = TextSec) }
        }
        Slider(
            value = volume, onValueChange = { volume = it },
            onValueChangeFinished = { scope.launch { SonosClient.setVolume(sp.ip, volume.toInt()) } },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue)
        )
    }
}

// ---------------------------------------------------------------- LG TV

@Composable
private fun TvSection(config: Config, onStatus: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("Wohnzimmer TV") }
    var ip by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }

    CollapsibleSection(key = "tv", title = "LG TV") {
        config.tvs.forEach { tv ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(tv.name, color = TextPrim)
                    Text(
                        if (tv.clientKey.isEmpty()) "Nicht gekoppelt" else "Gekoppelt ✓",
                        color = if (tv.clientKey.isEmpty()) MaterialTheme.colorScheme.error else Green,
                        fontSize = 12.sp
                    )
                }
                TextButton(onClick = {
                    scope.launch {
                        if (tv.mac.isBlank()) onStatus("TV an: MAC fehlt")
                        else LgTvClient.powerOn(tv.mac).onFailure { onStatus("TV: ${it.message}") }
                    }
                }) { Text("An", color = Green) }
                TextButton(onClick = {
                    scope.launch {
                        LgTvClient.powerOff(tv.ip, tv.clientKey).onFailure { onStatus("TV: ${it.message}") }
                    }
                }) { Text("Aus", color = TextSec) }
                if (tv.clientKey.isEmpty()) {
                    TextButton(onClick = {
                        onStatus("Kopplungs-Anfrage am TV bestätigen…")
                        scope.launch {
                            LgTvClient.pair(tv.ip)
                                .onSuccess { key ->
                                    Store.updateConfig { cfg ->
                                        cfg.copy(tvs = cfg.tvs.map {
                                            if (it.ip == tv.ip) it.copy(clientKey = key) else it
                                        })
                                    }
                                    onStatus("TV gekoppelt ✓")
                                }
                                .onFailure { onStatus("TV: ${it.message} (TV an? IP korrekt?)") }
                        }
                    }) { Text("Koppeln", color = Violet) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = name, onValueChange = { name = it },
            label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = ip, onValueChange = { ip = it },
                label = { Text("IP") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = mac, onValueChange = { mac = it },
                label = { Text("MAC (für Einschalten)") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Spacer(Modifier.height(6.dp))
        HintText("IP & MAC: TV → Einstellungen → Netzwerk → WLAN → Erweitert. „Einschalten über WLAN\" am TV aktivieren.")
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = {
                if (ip.isNotBlank()) {
                    Store.updateConfig { it.copy(tvs = it.tvs + LgTv(name.trim(), ip.trim(), "", mac.trim())) }
                    ip = ""; mac = ""
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Violet)
        ) { Text("TV hinzufügen") }
    }
}
