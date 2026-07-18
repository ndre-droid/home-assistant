package com.nahuel.homeflow.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.ui.*

/** Pick up to 8 automations (in tap order); they fill the widget's buttons. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        Store.init(applicationContext)

        setContent {
            HomeFlowTheme {
                val routines by Store.routines.collectAsState()
                var selected by remember { mutableStateOf(listOf<String>()) }
                var iconMode by remember { mutableStateOf(false) }

                Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                    Text("Automationen wählen (max. 8)",
                        color = TextPrim, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Reihenfolge = Button-Reihenfolge. Tippe zum Hinzufügen/Entfernen.",
                        color = TextSec, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(routines, key = { it.id }) { r ->
                            val idx = selected.indexOf(r.id)
                            val isSel = idx >= 0
                            GradientCard(Modifier.clickable {
                                selected = when {
                                    isSel -> selected - r.id
                                    selected.size < 8 -> selected + r.id
                                    else -> selected
                                }
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(r.name, color = if (isSel) Violet else TextPrim,
                                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    if (isSel) Text("${idx + 1}", color = Violet, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Icons statt Namen", color = TextPrim, fontWeight = FontWeight.SemiBold)
                            Text("Buttons zeigen das Automations-Icon", color = TextSec, fontSize = 12.sp)
                        }
                        Switch(
                            checked = iconMode, onCheckedChange = { iconMode = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Violet)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            RoutineWidget.saveMapping(this@WidgetConfigActivity, widgetId, selected)
                            RoutineWidget.saveIconMode(this@WidgetConfigActivity, widgetId, iconMode)
                            RoutineWidget.update(
                                this@WidgetConfigActivity,
                                AppWidgetManager.getInstance(this@WidgetConfigActivity), widgetId
                            )
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                            finish()
                        },
                        enabled = selected.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Violet),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Widget erstellen (${selected.size})") }
                }
            }
        }
    }
}
