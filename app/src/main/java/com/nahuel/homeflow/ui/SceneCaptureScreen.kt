package com.nahuel.homeflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    Column(
        Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, "Zurück", tint = TextPrim) }
            Text("Szene aufnehmen", color = TextPrim, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        GradientCard {
            HintText("Stelle Licht und Musik JETZT so ein, wie die Szene sein soll (z. B. für „Movie Night\": Bias-Light dimmen, Beam-Lautstärke setzen). Dann Geräte anhaken und speichern — der aktuelle Zustand wird eingefroren.")
        }

        GradientCard {
            SectionTitle("Name")
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                placeholder = { Text("z. B. Movie Night") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
        }

        if (lights.isNotEmpty()) {
            GradientCard {
                SectionTitle("Hue-Lampen (Zustand wird übernommen)")
                lights.forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = l.id in selLights,
                            onCheckedChange = { c ->
                                selLights = if (c) selLights + l.id else selLights - l.id
                            },
                            colors = CheckboxDefaults.colors(checkedColor = Violet)
                        )
                        Text(l.name, color = TextPrim, modifier = Modifier.weight(1f))
                        Text(
                            if (l.on) "an · ${l.brightness} %" else "aus",
                            color = TextSec, fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (config.sonos.isNotEmpty()) {
            GradientCard {
                SectionTitle("Sonos (Lautstärke + laufende Wiedergabe)")
                config.sonos.forEach { sp ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = sp.ip in selSpeakers,
                            onCheckedChange = { c ->
                                selSpeakers = if (c) selSpeakers + sp.ip else selSpeakers - sp.ip
                                if (!c) nightMode = nightMode - sp.ip
                            },
                            colors = CheckboxDefaults.colors(checkedColor = Blue)
                        )
                        Text(sp.name, color = TextPrim, modifier = Modifier.weight(1f))
                        if (sp.ip in selSpeakers) {
                            FilterChip(
                                selected = sp.ip in nightMode,
                                onClick = {
                                    nightMode = if (sp.ip in nightMode) nightMode - sp.ip else nightMode + sp.ip
                                },
                                label = { Text("Night-Mode", fontSize = 12.sp) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                HintText("Night-Mode + Sprachverbesserung: nur Beam/Arc. Wird beim Abspielen der Szene aktiviert.")
            }
        }

        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)

        Button(
            onClick = {
                busy = true; error = ""
                scope.launch {
                    SceneCapture.capture(name, selLights, selSpeakers, nightMode)
                        .onSuccess { Store.saveRoutine(it); onClose() }
                        .onFailure { error = it.message ?: "Fehler beim Aufnehmen" }
                    busy = false
                }
            },
            enabled = !busy && (selLights.isNotEmpty() || selSpeakers.isNotEmpty()),
            colors = ButtonDefaults.buttonColors(containerColor = Violet),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("Szene speichern")
        }
        Spacer(Modifier.height(24.dp))
    }
}
