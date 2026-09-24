package com.nahuel.homeflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.devices.HueClient
import com.nahuel.homeflow.devices.HueLight
import com.nahuel.homeflow.engine.SceneCapture
import kotlinx.coroutines.launch

/**
 * "Scene Sync": stelle Licht & Musik so ein, wie es sein soll — dann hier
 * Geräte anhaken und speichern. Der aktuelle Zustand wird als Szene eingefroren.
 */
@Composable
fun SceneCaptureScreen(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val config by Store.config.collectAsState()
    var name by remember { mutableStateOf("") }
    var lights by remember { mutableStateOf<List<HueLight>>(emptyList()) }
    var selLights by remember { mutableStateOf(setOf<String>()) }
    var selSpeakers by remember { mutableStateOf(setOf<String>()) }
    var nightMode by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (Store.config.value.hueAppKey.isNotEmpty())
            HueClient.lights().onSuccess { lights = orderLights(it, Store.config.value.lightOrder) }
    }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        TopBar(
            "Szene aufnehmen",
            leading = {
                IconBoxButton(30.dp, "Zurück", onClick = onClose) { Text("←", color = Ink, fontSize = 14.sp) }
            }
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            Section {
                HintText("Stelle Licht und Musik JETZT so ein, wie die Szene sein soll (z. B. für „Movie Night\": Bias-Light dimmen, Beam-Lautstärke setzen). Dann Geräte anhaken und speichern — der aktuelle Zustand wird eingefroren.")
            }

            Section("Name") {
                FlatField("Name", name, placeholder = "z. B. Movie Night") { name = it }
            }

            if (lights.isNotEmpty()) {
                Section("Hue-Lampen (Zustand wird übernommen)") {
                    lights.forEach { l ->
                        CheckRow(
                            label = l.name,
                            meta = if (l.on) "an · ${l.brightness} %" else "aus",
                            checked = l.id in selLights,
                            onToggle = { c -> selLights = if (c) selLights + l.id else selLights - l.id }
                        )
                    }
                }
            }

            if (config.sonos.isNotEmpty()) {
                Section("Sonos (Lautstärke + laufende Wiedergabe)") {
                    config.sonos.forEach { sp ->
                        CheckRow(
                            label = sp.name,
                            meta = "",
                            checked = sp.ip in selSpeakers,
                            onToggle = { c ->
                                selSpeakers = if (c) selSpeakers + sp.ip else selSpeakers - sp.ip
                                if (!c) nightMode = nightMode - sp.ip
                            }
                        ) {
                            if (sp.ip in selSpeakers) {
                                ChoiceChip("Night-Mode", sp.ip in nightMode) {
                                    nightMode = if (sp.ip in nightMode) nightMode - sp.ip else nightMode + sp.ip
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HintText("Night-Mode + Sprachverbesserung: nur Beam/Arc. Wird beim Abspielen der Szene aktiviert.")
                }
            }

            Section {
                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                }
                PrimaryButton(
                    if (busy) "Nimmt auf…" else "Szene speichern",
                    Modifier.fillMaxWidth(),
                    enabled = !busy && (selLights.isNotEmpty() || selSpeakers.isNotEmpty())
                ) {
                    busy = true; error = ""
                    scope.launch {
                        SceneCapture.capture(name, selLights, selSpeakers, nightMode)
                            .onSuccess { Store.saveRoutine(it); onClose() }
                            .onFailure { error = it.message ?: "Fehler beim Aufnehmen" }
                        busy = false
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Square checkbox row: bordered box, ticked when selected. */
@Composable
private fun CheckRow(
    label: String,
    meta: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        IconBoxButton(18.dp, onClick = { onToggle(!checked) }) {
            if (checked) Text("✓", color = Ink, fontSize = 10.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(label, color = Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (meta.isNotEmpty()) {
            Caption(meta)
            Spacer(Modifier.width(8.dp))
        }
        trailing()
    }
}
