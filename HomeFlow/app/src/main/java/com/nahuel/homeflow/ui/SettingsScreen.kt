package com.nahuel.homeflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.devices.HueClient
import com.nahuel.homeflow.devices.HueLight
import com.nahuel.homeflow.engine.TriggerService

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val config by Store.config.collectAsState()
    val ctx = LocalContext.current
    var hueLights by remember { mutableStateOf<List<HueLight>>(emptyList()) }
    LaunchedEffect(config.hueAppKey) {
        if (config.hueAppKey.isNotEmpty()) HueClient.lights().onSuccess { hueLights = it }
    }
    var apiKey by remember(config.anthropicKey) { mutableStateOf(config.anthropicKey) }
    var model by remember(config.model) { mutableStateOf(config.model) }
    var dayStart by remember(config.dayStart) { mutableStateOf(config.dayStart) }
    var nightStart by remember(config.nightStart) { mutableStateOf(config.nightStart) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Einstellungen", color = TextPrim, fontSize = 28.sp, fontWeight = FontWeight.Bold)

        GradientCard {
            SectionTitle("Claude (natürliche Sprache)")
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it },
                label = { Text("Anthropic API-Key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model, onValueChange = { model = it },
                label = { Text("Modell") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            HintText("Key erstellen: console.anthropic.com → API Keys → Create Key. Wird nur beim Erstellen/Bearbeiten von Automationen genutzt, nie beim Ausführen.")
        }

        GradientCard {
            SectionTitle("Tag / Nacht")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dayStart, onValueChange = { dayStart = it },
                    label = { Text("Tag ab (HH:MM)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f), singleLine = true
                )
                OutlinedTextField(
                    value = nightStart, onValueChange = { nightStart = it },
                    label = { Text("Nacht ab (HH:MM)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f), singleLine = true
                )
            }
            Spacer(Modifier.height(4.dp))
            HintText("Automationen mit Tag/Nacht-Varianten nutzen diese Zeiten.")
        }

        Button(
            onClick = {
                Store.updateConfig {
                    it.copy(anthropicKey = apiKey.trim(), model = model.trim(),
                        dayStart = dayStart.trim(), nightStart = nightStart.trim())
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Violet),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Speichern") }

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
                Text("Bias-Lampen (Lightstrips hinterm TV):", color = TextSec)
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
                if (config.tvs.isEmpty()) HintText("Erst LG TV im Geräte-Tab anlegen + koppeln.")
            }
            Spacer(Modifier.height(4.dp))
            HintText("Läuft der TV, färben sich die gewählten Lampen je nach Inhalt: Netflix/Prime/HDMI → warmes dunkles Orange, YouTube → Violett, Live-TV → Blau. TV aus → Lampen aus. Ehrlich: Das ist Stimmung nach Content-Typ (~5 s Reaktion), kein Frame-genaues Ambilight — dafür bräuchte es eine HDMI-Capture-Box, die API des TVs gibt Bildinhalte nicht her.")
        }

        GradientCard {
            SectionTitle("Unterwegs steuern (Tailscale)")
            HintText(
                "1. Tailscale-App auf diesem Handy installieren und anmelden.\n" +
                "2. Tailscale auf einem Gerät zuhause installieren (PC/Laptop/Raspberry Pi, das immer läuft) und dort „Subnet Router\" für dein Heimnetz aktivieren (z. B. 192.168.178.0/24).\n" +
                "3. Im Tailscale-Admin die Subnet-Route freigeben.\n" +
                "Danach funktioniert HomeFlow unterwegs exakt wie im WLAN – gleiche IPs, gleiche Geschwindigkeit."
            )
        }

        GradientCard {
            SectionTitle("Akku-Hinweis")
            HintText("Für Geräte-Trigger („wenn Badlicht angeht…\") läuft ein Hintergrunddienst. Damit Samsung ihn nicht beendet: Einstellungen → Apps → HomeFlow → Akku → „Nicht optimiert\" wählen.")
        }
        Spacer(Modifier.height(24.dp))
    }
}
