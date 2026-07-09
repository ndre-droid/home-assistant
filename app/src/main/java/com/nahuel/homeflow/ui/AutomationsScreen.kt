package com.nahuel.homeflow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.data.TriggerType
import com.nahuel.homeflow.engine.RoutineEngine
import com.nahuel.homeflow.engine.TriggerService

@Composable
fun AutomationsScreen(modifier: Modifier = Modifier, onEdit: (String) -> Unit, onCaptureScene: () -> Unit) {
    val showAddChooser = remember { androidx.compose.runtime.mutableStateOf(false) }
    val showTemplates = remember { androidx.compose.runtime.mutableStateOf(false) }
    val showHistory = remember { androidx.compose.runtime.mutableStateOf(false) }
    val routines by Store.routines.collectAsState()
    val ctx = LocalContext.current

    Box(modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        ScreenTitle(greeting())
                        Text("Deine Automationen", color = TextSec, fontSize = 13.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showHistory.value = true }) {
                        Icon(Icons.Outlined.History, "Verlauf", tint = TextSec)
                    }
                }
            }
            if (routines.isEmpty()) {
                item {
                    GradientCard {
                        Text("Noch keine Automationen", color = TextPrim, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        HintText("Tippe auf +, um eine Automation mit Auslöser, Bedingungen und Aktionen zu bauen, oder nimm den aktuellen Zustand als Szene auf.")
                    }
                }
            }
            items(routines, key = { it.id }) { r ->
                val interaction = remember { MutableInteractionSource() }
                GradientCard(
                    Modifier
                        .pressScale(interaction)
                        .clickable(interaction, indication = null) { onEdit(r.id) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, color = TextPrim, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(2.dp))
                            Text(r.triggers.joinToString(" · ") { triggerLabel(it.type) }, color = TextSec, fontSize = 12.sp)
                        }
                        // Run now — works regardless of enabled state
                        PlayCircleButton(size = 38) { RoutineEngine.runAsync(ctx, r) }
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = r.enabled,
                            onCheckedChange = {
                                Store.setEnabled(r.id, it)
                                TriggerService.sync(ctx)
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = Violet,
                                uncheckedTrackColor = Surface2
                            )
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }

        ExtendedFloatingActionButton(
            onClick = { showAddChooser.value = true },
            containerColor = Violet,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Neu") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        )

        if (showAddChooser.value) {
            AlertDialog(
                onDismissRequest = { showAddChooser.value = false },
                containerColor = Surface1,
                title = { Text("Was möchtest du anlegen?", color = TextPrim) },
                text = {
                    Column {
                        TextButton(onClick = { showAddChooser.value = false; onEdit("") }) {
                            Text("Neue Automation", color = Violet)
                        }
                        TextButton(onClick = { showAddChooser.value = false; onCaptureScene() }) {
                            Text("Szene aus aktuellem Zustand aufnehmen", color = Blue)
                        }
                        TextButton(onClick = { showAddChooser.value = false; showTemplates.value = true }) {
                            Text("Vorlage verwenden (Filmabend, Aufwachen, ...)", color = Green)
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showAddChooser.value = false }) { Text("Abbrechen", color = TextSec) } }
            )
        }

        if (showTemplates.value) {
            AlertDialog(
                onDismissRequest = { showTemplates.value = false },
                containerColor = Surface1,
                title = { Text("Vorlage wählen", color = TextPrim) },
                text = {
                    Column {
                        com.nahuel.homeflow.data.Templates.all.forEach { tpl ->
                            TextButton(onClick = {
                                Store.saveRoutine(tpl.build())
                                showTemplates.value = false
                            }) {
                                Column {
                                    Text(tpl.title, color = Violet, fontWeight = FontWeight.SemiBold)
                                    Text(tpl.description, color = TextSec, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showTemplates.value = false }) { Text("Schließen", color = TextSec) } }
            )
        }

        if (showHistory.value) {
            val history by Store.history.collectAsState()
            AlertDialog(
                onDismissRequest = { showHistory.value = false },
                containerColor = Surface1,
                title = { Text("Verlauf", color = TextPrim) },
                text = {
                    if (history.isEmpty()) Text("Noch nichts ausgeführt.", color = TextSec)
                    else LazyColumn(Modifier.height(320.dp)) {
                        items(history, key = { it.timestamp }) { h ->
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (h.ok) "✓" else "✕", color = if (h.ok) Green else Pink, modifier = Modifier.width(20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(h.routineName, color = TextPrim, fontSize = 14.sp)
                                    Text(
                                        android.text.format.DateFormat.format("dd.MM. HH:mm", h.timestamp).toString() +
                                            (if (h.detail.isNotEmpty()) "  ,  ${h.detail}" else ""),
                                        color = TextSec, fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { Store.clearHistory() }) { Text("Leeren", color = Pink) } },
                dismissButton = { TextButton(onClick = { showHistory.value = false }) { Text("Schließen", color = TextSec) } }
            )
        }
    }
}

fun triggerLabel(t: TriggerType): String = when (t) {
    TriggerType.MANUAL -> "Manuell · Button/Widget"
    TriggerType.NFC -> "NFC-Tag"
    TriggerType.DEVICE_STATE -> "Geräte-Trigger (Hue)"
    TriggerType.LEAVE_WIFI -> "Beim Verlassen des WLANs"
    TriggerType.TIME -> "Zu einer Uhrzeit"
    TriggerType.SUN -> "Sonnenauf-/untergang"
    TriggerType.ARRIVE_HOME -> "Beim Nachhausekommen"
    TriggerType.LEAVE_HOME -> "Beim Verlassen (GPS)"
}
