package com.nahuel.homeflow.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuel.homeflow.ManualActivity
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.devices.HueClient
import com.nahuel.homeflow.devices.HueLight
import com.nahuel.homeflow.engine.TriggerService
import kotlinx.coroutines.launch

/** All fields auto-save on change — no separate save button to miss. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val config by Store.config.collectAsState()
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var hueLights by remember { mutableStateOf<List<HueLight>>(emptyList()) }
    var editBias by remember { mutableStateOf(false) }
    var showAccentWheel by remember { mutableStateOf(false) }

    LaunchedEffect(config.hueAppKey) {
        if (config.hueAppKey.isNotEmpty())
            HueClient.lights().onSuccess { hueLights = orderLights(it, Store.config.value.lightOrder) }
    }

    Column(
        modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitle("Einstellungen")

        GradientCard {
            SectionTitle("Darstellung")
            Text("Design", color = TextSec, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val modes = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Hell", ThemeMode.DARK to "Dunkel")
                modes.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = config.themeMode == mode,
                        onClick = { Store.updateConfig { it.copy(themeMode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = Surface2,
                            activeContentColor = Violet,
                            inactiveContainerColor = Surface1,
                            inactiveContentColor = TextSec
                        )
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Akzentfarbe", color = TextSec, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            val presets = listOf(
                "" to "Standard", "#3B6EF5" to "Blau", "#7C74E8" to "Violett",
                "#1D9E75" to "Teal", "#E0A558" to "Amber", "#EC5F9E" to "Pink"
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.forEach { (hex, name) ->
                    val selected = config.accentColor == hex
                    val swatch = if (hex.isEmpty()) Color(0xFF3B6EF5) else Color(android.graphics.Color.parseColor(hex))
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(swatch)
                            .border(
                                width = if (selected) 3.dp else 0.5.dp,
                                color = if (selected) TextPrim else Hairline,
                                shape = CircleShape
                            )
                            .clickable { Store.updateConfig { it.copy(accentColor = hex) } }
                    )
                }
                // Wheel option
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .background(Surface2)
                        .border(0.5.dp, Hairline, CircleShape)
                        .clickable { showAccentWheel = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Add, "Eigene Farbe", tint = TextSec, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dynamische Farben", color = TextPrim, fontSize = 15.sp)
                    Text(
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                            "Nutzt die Farben deines Hintergrundbilds (Material You)"
                        else "Erst ab Android 12 verfügbar, nutzt sonst das App-Design",
                        color = TextSec, fontSize = 12.sp
                    )
                }
                Switch(
                    checked = config.dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S,
                    enabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S,
                    onCheckedChange = { v -> Store.updateConfig { it.copy(dynamicColor = v) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = Violet, uncheckedTrackColor = Surface2)
                )
            }
        }

        GradientCard {
            SectionTitle("Tag / Nacht")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config.dayStart,
                    onValueChange = { v -> Store.updateConfig { it.copy(dayStart = v.trim()) } },
                    label = { Text("Tag ab (HH:MM)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f), singleLine = true
                )
                OutlinedTextField(
                    value = config.nightStart,
                    onValueChange = { v -> Store.updateConfig { it.copy(nightStart = v.trim()) } },
                    label = { Text("Nacht ab (HH:MM)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f), singleLine = true
                )
            }
            Spacer(Modifier.height(4.dp))
            HintText("Bestimmt, wann Tag- bzw. Nacht-Varianten von Automationen greifen. Wird sofort gespeichert.")
        }

        GradientCard {
            SectionTitle("TV Bias-Light (ohne Sync-Box)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aktiv", color = TextPrim, modifier = Modifier.weight(1f))
                Switch(
                    checked = config.biasEnabled,
                    onCheckedChange = { v ->
                        Store.updateConfig {
                            it.copy(biasEnabled = v, biasTv = it.biasTv.ifEmpty { it.tvs.firstOrNull()?.ip ?: "" })
                        }
                        TriggerService.sync(ctx)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Violet, uncheckedTrackColor = Surface2)
                )
            }
            if (config.biasEnabled) {
                Spacer(Modifier.height(4.dp))
                if (!editBias) {
                    // Compact view: only the chosen lights, rest hidden
                    val chosen = hueLights.filter { it.id in config.biasLights }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (chosen.isEmpty()) "Keine Lampen gewählt"
                            else chosen.joinToString(", ") { it.name },
                            color = if (chosen.isEmpty()) Pink else TextPrim,
                            fontSize = 14.sp, modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { editBias = true }) { Text("Ändern", color = Violet) }
                    }
                } else {
                    Text("Bias-Lampen wählen:", color = TextSec)
                    hueLights.forEach { l ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = l.id in config.biasLights,
                                onCheckedChange = { c ->
                                    Store.updateConfig {
                                        it.copy(biasLights = if (c) it.biasLights + l.id else it.biasLights - l.id)
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = Violet)
                            )
                            Text(l.name, color = TextPrim)
                        }
                    }
                    if (hueLights.isEmpty()) HintText("Erst Hue im Geräte-Tab koppeln.")
                    TextButton(onClick = { editBias = false }) { Text("Fertig", color = Green) }
                }
                if (config.tvs.isEmpty()) HintText("Erst LG TV im Geräte-Tab anlegen + koppeln.")
            }
            Spacer(Modifier.height(4.dp))
            HintText("Läuft der TV, färben sich die gewählten Lampen nach Inhalt (Netflix/HDMI → warm-orange, YouTube → violett, Live-TV → blau). TV aus → Lampen aus. Stimmung nach Content-Typ, ~5 s Reaktion — frame-genaues Ambilight ist ohne HDMI-Capture-Hardware technisch nicht möglich.")
        }

        GradientCard {
            SectionTitle("Anwesenheit (Partnerin)")
            OutlinedTextField(
                value = config.partnerIp,
                onValueChange = { v -> Store.updateConfig { it.copy(partnerIp = v.trim()) } },
                label = { Text("iPhone-IP im WLAN") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(4.dp))
            HintText("Für den Auslöser „WLAN verlassen“: Die Routine wird übersprungen, wenn dieses Gerät in den letzten 20 Min im WLAN gesehen wurde. IP im Router fest vergeben (DHCP-Reservierung) und am iPhone die private WLAN-Adresse für dein Netz ausschalten, sonst wechselt die Adresse. iPhones schlafen — die Erkennung ist gut, aber nicht 100 %.")
        }

        GradientCard {
            SectionTitle("Gäste-Zugriff (iPhone / Browser)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Web-Steuerung", color = TextPrim, fontSize = 15.sp)
                    Text("Startet einen kleinen Server. Gäste öffnen die URL im Browser und lösen Automationen aus, ganz ohne App.",
                        color = TextSec, fontSize = 12.sp)
                }
                Switch(
                    checked = config.webServerEnabled,
                    onCheckedChange = { v ->
                        Store.updateConfig { it.copy(webServerEnabled = v) }
                        TriggerService.sync(ctx)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Violet, uncheckedTrackColor = Surface2)
                )
            }
            if (config.webServerEnabled) {
                Spacer(Modifier.height(8.dp))
                Text("Diese Adresse im Browser öffnen (gleiches WLAN oder Tailscale):", color = TextSec, fontSize = 12.sp)
                Text(com.nahuel.homeflow.engine.WebTriggerServer.localUrl(),
                    color = Violet, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    QrCode(com.nahuel.homeflow.engine.WebTriggerServer.localUrl(), sizePx = 480,
                        modifier = Modifier.size(200.dp))
                }
                Spacer(Modifier.height(6.dp))
                HintText("iPhone-Kamera auf den QR-Code halten, in Safari öffnen, zum Home-Bildschirm hinzufügen. Jede aktive Automation wird zum Button.")
                Spacer(Modifier.height(8.dp))
                Text("Nur im Heim-WLAN?", color = TextPrim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                HintText("Diese Adresse funktioniert nur, wenn beide im selben WLAN sind und dein Handy an ist. Für dauerhaften Zugriff von überall: Tailscale (kostenlos) auf beiden Handys installieren, mit demselben Konto anmelden, dann diese Adresse durch die Tailscale-IP deines Handys ersetzen. Anleitung im Handbuch.")
            }
        }

        GradientCard {
            SectionTitle("Schütteln & Standort")
            val routines by Store.routines.collectAsState()
            Text("Bei Schütteln ausführen", color = TextPrim, fontSize = 15.sp)
            var shakeOpen by remember { mutableStateOf(false) }
            val shakeName = routines.firstOrNull { it.id == config.shakeRoutineId }?.name ?: "Aus"
            Box {
                OutlinedButton(onClick = { shakeOpen = true }) { Text(shakeName, color = TextPrim) }
                DropdownMenu(expanded = shakeOpen, onDismissRequest = { shakeOpen = false }) {
                    DropdownMenuItem(text = { Text("Aus") }, onClick = {
                        Store.updateConfig { it.copy(shakeRoutineId = "") }; TriggerService.sync(ctx); shakeOpen = false
                    })
                    routines.forEach { r ->
                        DropdownMenuItem(text = { Text(r.name) }, onClick = {
                            Store.updateConfig { it.copy(shakeRoutineId = r.id) }; TriggerService.sync(ctx); shakeOpen = false
                        })
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            HintText("Handy kräftig schütteln, um diese Automation auszulösen (z. B. Panik-Aus). Der Hintergrunddienst muss laufen.")

            Spacer(Modifier.height(12.dp))
            Text("Zuhause-Adresse (für GPS-Trigger)", color = TextPrim, fontSize = 15.sp)
            var addr by remember { mutableStateOf("") }
            var geoBusy by remember { mutableStateOf(false) }
            var geoMsg by remember { mutableStateOf("") }
            OutlinedTextField(
                value = addr, onValueChange = { addr = it },
                label = { Text("Adresse (Straße, Ort)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    geoBusy = true; geoMsg = ""
                    scope.launch {
                        com.nahuel.homeflow.devices.Geocode.addressToCoords(ctx, addr)
                            .onSuccess { (lat, lon) ->
                                Store.updateConfig { it.copy(homeLat = lat, homeLon = lon) }
                                geoMsg = "✓ Gespeichert: %.5f, %.5f".format(lat, lon)
                            }
                            .onFailure { geoMsg = "Nicht gefunden: ${it.message}. Nutze unten Koordinaten." }
                        geoBusy = false
                    }
                },
                enabled = !geoBusy && addr.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Violet),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (geoBusy) "Suche…" else "Adresse suchen & speichern", fontWeight = FontWeight.Medium) }
            if (geoMsg.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(geoMsg, color = if (geoMsg.startsWith("✓")) Green else Pink, fontSize = 12.sp)
            }
            if (config.homeLat != 0.0) {
                Spacer(Modifier.height(4.dp))
                Text("Aktuell gespeichert: %.5f, %.5f".format(config.homeLat, config.homeLon),
                    color = TextSec, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text("Oder Koordinaten direkt (aus Google Maps):", color = TextSec, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = if (config.homeLat == 0.0) "" else config.homeLat.toString(),
                    onValueChange = { v -> Store.updateConfig { it.copy(homeLat = v.toDoubleOrNull() ?: 0.0) } },
                    label = { Text("Breite (lat)") }, modifier = Modifier.weight(1f), singleLine = true
                )
                OutlinedTextField(
                    value = if (config.homeLon == 0.0) "" else config.homeLon.toString(),
                    onValueChange = { v -> Store.updateConfig { it.copy(homeLon = v.toDoubleOrNull() ?: 0.0) } },
                    label = { Text("Länge (lon)") }, modifier = Modifier.weight(1f), singleLine = true
                )
            }
            Spacer(Modifier.height(4.dp))
            HintText("Danach Automationen mit Auslöser Ankunft/Weggehen (GPS) anlegen. Standort-Berechtigung auf immer erlauben stellen.")
        }

        GradientCard {
            SectionTitle("Anleitung")
            Button(
                onClick = { ctx.startActivity(Intent(ctx, ManualActivity::class.java)) },
                colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                modifier = Modifier.fillMaxWidth()
            ) { Text("📖 Anleitung öffnen", color = TextPrim) }
        }

        GradientCard {
            SectionTitle("Unterwegs steuern (Tailscale)")
            HintText(
                "1. Tailscale-App auf diesem Handy installieren und anmelden.\n" +
                "2. Tailscale auf einem Gerät zuhause installieren, das durchläuft, und Subnet-Router aktivieren (z. B. 192.168.178.0/24).\n" +
                "3. Route im Tailscale-Admin freigeben.\n" +
                "Danach funktioniert HomeFlow unterwegs exakt wie im WLAN."
            )
        }

        GradientCard {
            SectionTitle("Akku-Hinweis")
            HintText("Für Geräte-Trigger und Bias-Light läuft ein Hintergrunddienst. Damit Samsung ihn nicht beendet: Einstellungen → Apps → HomeFlow → Akku → „Nicht optimiert\".")
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showAccentWheel) {
        ColorWheelDialog(
            initialHex = config.accentColor.ifEmpty { null },
            onDismiss = { showAccentWheel = false },
            onPick = { hex -> Store.updateConfig { it.copy(accentColor = hex) } }
        )
    }
}
