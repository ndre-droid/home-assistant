package com.nahuel.homeflow.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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

/** All fields auto-save on change — no separate save button to miss. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val config by Store.config.collectAsState()
    val ctx = LocalContext.current
    var hueLights by remember { mutableStateOf<List<HueLight>>(emptyList()) }
    var editBias by remember { mutableStateOf(false) }

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
}
