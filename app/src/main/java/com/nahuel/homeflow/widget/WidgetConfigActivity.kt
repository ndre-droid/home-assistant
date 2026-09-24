package com.nahuel.homeflow.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

                Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
                    TopBar("Automationen wählen")
                    Caption(
                        "Maximal 8. Reihenfolge = Button-Reihenfolge. Tippe zum Hinzufügen/Entfernen.",
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                    Rule()

                    LazyColumn(Modifier.weight(1f)) {
                        items(routines, key = { it.id }) { r ->
                            val idx = selected.indexOf(r.id)
                            val isSel = idx >= 0
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isSel) AccentTint else Bg)
                                        .clickable {
                                            selected = when {
                                                isSel -> selected - r.id
                                                selected.size < 8 -> selected + r.id
                                                else -> selected
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    IconBox(30.dp) { Text(routineIcon(r), fontSize = 15.sp) }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        r.name,
                                        color = if (isSel) AccentText else Ink,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSel) {
                                        IconBox(22.dp) {
                                            Text(
                                                "${idx + 1}", color = AccentText,
                                                fontSize = 11.sp, fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Rule()
                            }
                        }
                    }

                    Section {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Icons statt Namen", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Caption("Buttons zeigen das Automations-Icon")
                            }
                            FlatToggle(iconMode) { iconMode = it }
                        }
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(
                            "Widget erstellen (${selected.size})",
                            Modifier.fillMaxWidth(),
                            enabled = selected.isNotEmpty()
                        ) {
                            RoutineWidget.saveMapping(this@WidgetConfigActivity, widgetId, selected)
                            RoutineWidget.saveIconMode(this@WidgetConfigActivity, widgetId, iconMode)
                            RoutineWidget.update(
                                this@WidgetConfigActivity,
                                AppWidgetManager.getInstance(this@WidgetConfigActivity), widgetId
                            )
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                            finish()
                        }
                    }
                }
            }
        }
    }
}
