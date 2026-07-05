package com.nahuel.homeflow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
fun AutomationsScreen(modifier: Modifier = Modifier, onEdit: (String) -> Unit) {
    val routines by Store.routines.collectAsState()
    val ctx = LocalContext.current

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Automationen",
                    color = TextPrim, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            if (routines.isEmpty()) {
                item {
                    GradientCard {
                        Text("Noch keine Automationen", color = TextPrim, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        HintText("Tippe auf +, beschreibe deine Routine in normaler Sprache und Claude baut sie für dich. Beispiel: „Wenn ich den NFC-Tag berühre: alle Lichter, Sonos und TV aus.“")
                    }
                }
            }
            items(routines, key = { it.id }) { r ->
                GradientCard(Modifier.clickable { onEdit(r.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, color = TextPrim, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(2.dp))
                            Text(triggerLabel(r.trigger.type), color = TextSec, fontSize = 12.sp)
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

        FloatingActionButton(
            onClick = { onEdit("") },
            containerColor = Violet,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) { Icon(Icons.Filled.Add, "Neue Automation") }
    }
}

fun triggerLabel(t: TriggerType): String = when (t) {
    TriggerType.MANUAL -> "Manuell · Button/Widget"
    TriggerType.NFC -> "NFC-Tag"
    TriggerType.DEVICE_STATE -> "Geräte-Trigger (Hue)"
}
