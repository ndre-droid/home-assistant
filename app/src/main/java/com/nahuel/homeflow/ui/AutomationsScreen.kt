package com.nahuel.homeflow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
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
    var showAddChooser = remember { androidx.compose.runtime.mutableStateOf(false) }
    val routines by Store.routines.collectAsState()
    val ctx = LocalContext.current

    Box(modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScreenTitle("Automationen")
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
                        IconButton(onClick = { RoutineEngine.runAsync(ctx, r) }) {
                            Icon(Icons.Filled.PlayArrow, "Jetzt ausführen", tint = Violet)
                        }
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
            contentColor = androidx.compose.ui.graphics.Color.White,
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
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showAddChooser.value = false }) { Text("Abbrechen", color = TextSec) } }
            )
        }
    }
}

fun triggerLabel(t: TriggerType): String = when (t) {
    TriggerType.MANUAL -> "Manuell · Button/Widget"
    TriggerType.NFC -> "NFC-Tag"
    TriggerType.DEVICE_STATE -> "Geräte-Trigger (Hue)"
    TriggerType.LEAVE_WIFI -> "Beim Verlassen des WLANs"
}
