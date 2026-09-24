package com.nahuel.homeflow.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuel.homeflow.ManualActivity
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.devices.HueClient
import com.nahuel.homeflow.devices.HueLight
import com.nahuel.homeflow.engine.TriggerService
import kotlinx.coroutines.launch

/**
 * Settings, Modernist layout: stacked sections separated by 2dp rules, each with
 * an uppercase label. All fields auto-save on change — no save button to miss.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val config by Store.config.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var hueLights by remember { mutableStateOf<List<HueLight>>(emptyList()) }
    var editBias by remember { mutableStateOf(false) }
    var showAccentWheel by remember { mutableStateOf(false) }
    var showWidgetHelp by remember { mutableStateOf(false) }

    LaunchedEffect(config.hueAppKey) {
        if (config.hueAppKey.isNotEmpty())
            HueClient.lights().onSuccess { hueLights = orderLights(it, Store.config.value.lightOrder) }
    }

    Column(modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        TopBar("Einstellungen")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            // ---- Darstellung ----
            Section("Darstellung") {
                val modes = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Hell", ThemeMode.DARK to "Dunkel")
                SegmentedControl(
                    options = modes.map { it.second },
                    selectedIndex = modes.indexOfFirst { it.first == config.themeMode }.coerceAtLeast(0)
                ) { i -> Store.updateConfig { it.copy(themeMode = modes[i].first) } }

                Spacer(Modifier.height(16.dp))
                SectionLabel("Akzentfarbe")
                Spacer(Modifier.height(8.dp))
                val presets = listOf(
                    "" to "Standard", "#3B6EF5" to "Blau", "#7C74E8" to "Violett",
                    "#1D9E75" to "Teal", "#E0A558" to "Amber", "#EC5F9E" to "Pink"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { (hex, _) ->
                        val selected = config.accentColor == hex
                        val swatch =
                            if (hex.isEmpty()) Color(0xFFEC3013) else Color(android.graphics.Color.parseColor(hex))
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(swatch)
                                .border(if (selected) RuleWidth else 1.dp, if (selected) Ink else Faint)
                                .clickable { Store.updateConfig { it.copy(accentColor = hex) } }
                        )
                    }
                    IconBoxButton(30.dp, "Eigene Farbe", onClick = { showAccentWheel = true }) {
                        Text("+", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(16.dp))
                ToggleRow(
                    label = "Dynamische Farben",
                    caption = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                        "Nutzt die Farben deines Hintergrundbilds (Material You)"
                    else "Erst ab Android 12 verfügbar, nutzt sonst das App-Design",
                    checked = config.dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S,
                    enabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                ) { v -> Store.updateConfig { it.copy(dynamicColor = v) } }
            }

            // ---- Tag / Nacht ----
            Section("Tag / Nacht") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        FlatField("Tag ab (HH:MM)", config.dayStart) { v ->
                            Store.updateConfig { it.copy(dayStart = v.trim()) }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        FlatField("Nacht ab (HH:MM)", config.nightStart) { v ->
                            Store.updateConfig { it.copy(nightStart = v.trim()) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HintText("Bestimmt, wann Tag- bzw. Nacht-Varianten von Automationen greifen. Wird sofort gespeichert.")
            }

            // ---- Schütteln ----
            Section("Bei Schütteln ausführen") {
                val routines by Store.routines.collectAsState()
                var shakeOpen by remember { mutableStateOf(false) }
                val shakeName = routines.firstOrNull { it.id == config.shakeRoutineId }?.name ?: "Aus"
                Box {
                    SecondaryButton(shakeName, Modifier.fillMaxWidth()) { shakeOpen = true }
                    DropdownMenu(
                        expanded = shakeOpen,
                        onDismissRequest = { shakeOpen = false },
                        modifier = Modifier.background(Bg).border(RuleWidth, Divider)
                    ) {
                        DropdownMenuItem(text = { Text("Aus", color = Ink, fontSize = 13.sp) }, onClick = {
                            Store.updateConfig { it.copy(shakeRoutineId = "") }
                            TriggerService.sync(ctx); shakeOpen = false
                        })
                        routines.forEach { r ->
                            DropdownMenuItem(text = { Text(r.name, color = Ink, fontSize = 13.sp) }, onClick = {
                                Store.updateConfig { it.copy(shakeRoutineId = r.id) }
                                TriggerService.sync(ctx); shakeOpen = false
                            })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HintText("Handy kräftig schütteln, um diese Automation auszulösen (z. B. Panik-Aus). Der Hintergrunddienst muss laufen.")
            }

            // ---- Zuhause-Adresse ----
            Section("Zuhause-Adresse (GPS-Trigger)") {
                var addr by remember { mutableStateOf("") }
                var geoBusy by remember { mutableStateOf(false) }
                var geoMsg by remember { mutableStateOf("") }

                FlatField("Adresse (Straße, Ort)", addr) { addr = it }
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    if (geoBusy) "Suche…" else "Adresse suchen & speichern",
                    Modifier.fillMaxWidth(),
                    enabled = !geoBusy && addr.isNotBlank()
                ) {
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
                }
                if (geoMsg.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        geoMsg,
                        color = if (geoMsg.startsWith("✓")) AccentText else MaterialTheme.colorScheme.error,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                if (config.homeLat != 0.0) {
                    Spacer(Modifier.height(6.dp))
                    Caption("Aktuell gespeichert: %.5f, %.5f".format(config.homeLat, config.homeLon))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        FlatField(
                            "Breite (lat)",
                            if (config.homeLat == 0.0) "" else config.homeLat.toString()
                        ) { v -> Store.updateConfig { it.copy(homeLat = v.toDoubleOrNull() ?: 0.0) } }
                    }
                    Box(Modifier.weight(1f)) {
                        FlatField(
                            "Länge (lon)",
                            if (config.homeLon == 0.0) "" else config.homeLon.toString()
                        ) { v -> Store.updateConfig { it.copy(homeLon = v.toDoubleOrNull() ?: 0.0) } }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HintText("Danach Automationen mit Auslöser Ankunft/Weggehen (GPS) anlegen. Standort-Berechtigung auf immer erlauben stellen.")
            }

            // ---- Gäste-Zugriff ----
            Section("Gäste-Zugriff (iPhone / Browser)") {
                ToggleRow(
                    label = "Web-Steuerung",
                    caption = "Startet einen kleinen Server. Gäste öffnen die URL im Browser und lösen Automationen aus, ganz ohne App.",
                    checked = config.webServerEnabled
                ) { v ->
                    Store.updateConfig { it.copy(webServerEnabled = v) }
                    TriggerService.sync(ctx)
                }
                if (config.webServerEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Caption("Diese Adresse im Browser öffnen (gleiches WLAN oder Tailscale):")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        com.nahuel.homeflow.engine.WebTriggerServer.localUrl(),
                        color = AccentText, fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.fillMaxWidth().background(Bg).border(RuleWidth, Divider).padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        QrCode(
                            com.nahuel.homeflow.engine.WebTriggerServer.localUrl(),
                            sizePx = 480,
                            modifier = Modifier.size(200.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    HintText("iPhone-Kamera auf den QR-Code halten, in Safari öffnen, zum Home-Bildschirm hinzufügen. Jede aktive Automation wird zum Button.")
                    Spacer(Modifier.height(10.dp))
                    Text("Nur im Heim-WLAN?", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    HintText("Diese Adresse funktioniert nur, wenn beide im selben WLAN sind und dein Handy an ist. Für dauerhaften Zugriff von überall: Tailscale (kostenlos) auf beiden Handys installieren, mit demselben Konto anmelden, dann diese Adresse durch die Tailscale-IP deines Handys ersetzen. Anleitung im Handbuch.")
                }
            }

            // ---- Home-screen widget ----
            Section("Homescreen-Widget") {
                HintText("Bis zu 8 Automationen als Ein-Tipp-Buttons auf dem Homescreen.")
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Widget einrichten") { showWidgetHelp = true }
            }

            // ---- TV Bias-Light ----
            Section("TV Bias-Light (ohne Sync-Box)") {
                ToggleRow(label = "Aktiv", caption = "", checked = config.biasEnabled) { v ->
                    Store.updateConfig {
                        it.copy(biasEnabled = v, biasTv = it.biasTv.ifEmpty { it.tvs.firstOrNull()?.ip ?: "" })
                    }
                    TriggerService.sync(ctx)
                }
                if (config.biasEnabled) {
                    Spacer(Modifier.height(10.dp))
                    if (!editBias) {
                        val chosen = hueLights.filter { it.id in config.biasLights }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (chosen.isEmpty()) "Keine Lampen gewählt"
                                else chosen.joinToString(", ") { it.name },
                                color = if (chosen.isEmpty()) MaterialTheme.colorScheme.error else Ink,
                                fontSize = 13.sp, modifier = Modifier.weight(1f)
                            )
                            GhostButton("Ändern") { editBias = true }
                        }
                    } else {
                        SectionLabel("Bias-Lampen wählen")
                        Spacer(Modifier.height(8.dp))
                        hueLights.forEach { l ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                            ) {
                                val on = l.id in config.biasLights
                                IconBoxButton(18.dp, onClick = {
                                    Store.updateConfig {
                                        it.copy(biasLights = if (!on) it.biasLights + l.id else it.biasLights - l.id)
                                    }
                                }) { if (on) Text("✓", color = Ink, fontSize = 10.sp) }
                                Spacer(Modifier.width(10.dp))
                                Text(l.name, color = Ink, fontSize = 13.sp)
                            }
                        }
                        if (hueLights.isEmpty()) HintText("Erst Hue im Geräte-Tab koppeln.")
                        Spacer(Modifier.height(8.dp))
                        GhostButton("Fertig") { editBias = false }
                    }
                    if (config.tvs.isEmpty()) HintText("Erst LG TV im Geräte-Tab anlegen + koppeln.")
                }
                Spacer(Modifier.height(8.dp))
                HintText("Läuft der TV, färben sich die gewählten Lampen nach Inhalt (Netflix/HDMI → warm-orange, YouTube → violett, Live-TV → blau). TV aus → Lampen aus. Stimmung nach Content-Typ, ~5 s Reaktion — frame-genaues Ambilight ist ohne HDMI-Capture-Hardware technisch nicht möglich.")
            }

            // ---- Anwesenheit ----
            Section("Anwesenheit (Partnerin)") {
                FlatField("iPhone-IP im WLAN", config.partnerIp) { v ->
                    Store.updateConfig { it.copy(partnerIp = v.trim()) }
                }
                Spacer(Modifier.height(8.dp))
                HintText("Für den Auslöser „WLAN verlassen“: Die Routine wird übersprungen, wenn dieses Gerät in den letzten 20 Min im WLAN gesehen wurde. IP im Router fest vergeben (DHCP-Reservierung) und am iPhone die private WLAN-Adresse für dein Netz ausschalten, sonst wechselt die Adresse. iPhones schlafen — die Erkennung ist gut, aber nicht 100 %.")
            }

            // ---- Spotify ----
            Section("Spotify") {
                FlatField("Client-ID (developer.spotify.com)", config.spotifyClientId) { v ->
                    Store.updateConfig { it.copy(spotifyClientId = v.trim()) }
                }
                Spacer(Modifier.height(10.dp))
                if (config.spotifyRefresh.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Verbunden ✓", color = AccentText, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                        )
                        GhostButton("Trennen", color = MaterialTheme.colorScheme.error) {
                            Store.updateConfig { it.copy(spotifyRefresh = "") }
                        }
                    }
                } else {
                    PrimaryButton("Mit Spotify verbinden", enabled = config.spotifyClientId.isNotBlank()) {
                        val verifier = com.nahuel.homeflow.devices.SpotifyClient.newVerifier()
                        Store.updateConfig { it.copy(spotifyVerifier = verifier) }
                        val url = com.nahuel.homeflow.devices.SpotifyClient.authUrl(config.spotifyClientId, verifier)
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                    }
                }
                Spacer(Modifier.height(8.dp))
                HintText("Einmalig: developer.spotify.com → Create App → Redirect-URI exakt „homeflow://spotify\" eintragen → Client-ID hier einfügen → verbinden. Braucht Spotify Premium. Danach gibt es die Sonos-Aktion „Spotify\" mit Song-/Playlist-Suche.")
            }

            // ---- Anleitung ----
            Section("Anleitung") {
                SecondaryButton("Anleitung öffnen", Modifier.fillMaxWidth()) {
                    ctx.startActivity(Intent(ctx, ManualActivity::class.java))
                }
            }

            Section("Unterwegs steuern (Tailscale)") {
                HintText(
                    "1. Tailscale-App auf diesem Handy installieren und anmelden.\n" +
                        "2. Tailscale auf einem Gerät zuhause installieren, das durchläuft, und Subnet-Router aktivieren (z. B. 192.168.178.0/24).\n" +
                        "3. Route im Tailscale-Admin freigeben.\n" +
                        "Danach funktioniert HomeFlow unterwegs exakt wie im WLAN."
                )
            }

            Section("Akku-Hinweis") {
                HintText("Für Geräte-Trigger und Bias-Light läuft ein Hintergrunddienst. Damit Samsung ihn nicht beendet: Einstellungen → Apps → HomeFlow → Akku → „Nicht optimiert\".")
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAccentWheel) {
        ColorWheelDialog(
            initialHex = config.accentColor.ifEmpty { null },
            onDismiss = { showAccentWheel = false },
            onPick = { hex -> Store.updateConfig { it.copy(accentColor = hex) } }
        )
    }

    if (showWidgetHelp) {
        FlatDialog(
            onDismissRequest = { showWidgetHelp = false },
            title = "Widget einrichten",
            confirmButton = { GhostButton("Verstanden") { showWidgetHelp = false } },
            text = {
                Caption(
                    "Homescreen lange drücken → Widgets → HomeFlow. Beim Ablegen öffnet sich die Auswahl, " +
                        "in der du bis zu 8 Automationen in Tipp-Reihenfolge wählst."
                )
            }
        )
    }
}

/** Label + caption on the left, flat switch on the right. */
@Composable
private fun ToggleRow(
    label: String,
    caption: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (caption.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Caption(caption)
            }
        }
        FlatToggle(checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}
