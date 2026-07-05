package com.nahuel.homeflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import com.nahuel.homeflow.devices.LgTvClient
import com.nahuel.homeflow.devices.SonosClient
import kotlinx.coroutines.launch

private val colorPresets = listOf(
    "#FFFFFF" to Color.White, "#FFB74D" to Color(0xFFFFB74D), "#F062A6" to Pink,
    "#34D399" to Green, "#3D8BFD" to Blue, "#8B7CF7" to Violet, "#EF4444" to Color(0xFFEF4444)
)

@Composable
fun DevicesScreen(modifier: Modifier = Modifier) {
    val config by Store.config.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var lights by remember { mutableStateOf<List<HueLight>>(emptyList()) }
    var status by remember { mutableStateOf("") }

    fun refreshLights() {
        if (config.hueAppKey.isEmpty()) return
        scope.launch {
            HueClient.lights().onSuccess { lights = it }.onFailure { status = "Hue: ${it.message}" }
        }
    }
    LaunchedEffect(config.hueAppKey, config.hueBridgeIp) { refreshLights() }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Geräte", color = TextPrim, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            // Panic button: everything off, in parallel
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
            Text(status, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        HueSection(config, lights, onStatus = { status = it }, onRefresh = { refreshLights() })
        SonosSection(config, onStatus = { status = it })
        TvSection(config, onStatus = { status = it })
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HueSection(config: Config, lights: List<HueLight>, onStatus: (String) -> Unit, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var bridgeIp by remember(config.hueBridgeIp) { mutableStateOf(config.hueBridgeIp) }

    GradientCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Philips Hue")
            Spacer(Modifier.weight(1f))
            if (config.hueAppKey.isNotEmpty()) {
                IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Aktualisieren", tint = TextSec) }
            }
        }
        if (config.hueAppKey.isEmpty()) {
            OutlinedTextField(
                value = bridgeIp, onValueChange = { bridgeIp = it },
                label = { Text("Bridge-IP (z. B. 192.168.178.30)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            HintText("IP findest du in der Hue-App unter Einstellungen → Meine Hue-Systeme → Bridge. Drücke den runden Knopf auf der Bridge und tippe dann auf „Koppeln\".")
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
        } else {
            lights.forEach { light -> LightRow(light, onRefresh) }
            if (lights.isEmpty()) HintText("Keine Lampen gefunden – Aktualisieren tippen.")
        }
    }
}

@Composable
private fun LightRow(light: HueLight, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var on by remember(light.id, light.on) { mutableStateOf(light.on) }
    var brightness by remember(light.id) { mutableStateOf(70f) }

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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    colorPresets.forEach { (hex, c) ->
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(c)
                                .clickable {
                                    scope.launch {
                                        HueClient.setLight(light.id, on = null, brightness = null, colorHex = hex)
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SonosSection(config: Config, onStatus: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }

    GradientCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Sonos")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                searching = true
                scope.launch {
                    val found = SonosClient.discover()
                    if (found.isEmpty()) onStatus("Sonos: nichts gefunden (gleiches WLAN?)")
                    else Store.updateConfig { cfg ->
                        val known = cfg.sonos.map { it.ip }.toSet()
                        cfg.copy(sonos = cfg.sonos + found.filter { it.ip !in known })
                    }
                    searching = false
                }
            }) { Text(if (searching) "Suche…" else "Suchen", color = Violet) }
        }
        if (config.sonos.isEmpty()) HintText("Tippe auf „Suchen\", um deine Sonos-Lautsprecher (Beam, Era 100 …) zu finden.")
        config.sonos.forEach { sp -> SonosRow(sp) }
    }
}

@Composable
private fun SonosRow(sp: SonosSpeaker) {
    val scope = rememberCoroutineScope()
    var volume by remember(sp.ip) { mutableStateOf(25f) }

    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sp.name, color = TextPrim, modifier = Modifier.weight(1f))
            IconButton(onClick = { scope.launch { SonosClient.play(sp.ip) } }) {
                Icon(Icons.Filled.PlayArrow, "Play", tint = Green)
            }
            IconButton(onClick = { scope.launch { SonosClient.pause(sp.ip) } }) {
                Icon(Icons.Filled.Clear, "Pause", tint = TextSec)
            }
        }
        Slider(
            value = volume, onValueChange = { volume = it },
            onValueChangeFinished = { scope.launch { SonosClient.setVolume(sp.ip, volume.toInt()) } },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue)
        )
    }
}

@Composable
private fun TvSection(config: Config, onStatus: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("Wohnzimmer TV") }
    var ip by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }

    GradientCard {
        SectionTitle("LG TV")
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
        HintText("IP & MAC: TV-Einstellungen → Netzwerk → WLAN-Verbindung → Erweitert. Für Einschalten per App: „Einschalten über WLAN\" am TV aktivieren.")
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
