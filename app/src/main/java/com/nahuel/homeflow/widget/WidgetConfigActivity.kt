package com.nahuel.homeflow.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.ui.*

/** Shown when the user drops the widget on the home screen: pick which routine it runs. */
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
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text("Welche Automation soll das Widget starten?",
                            color = TextPrim, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    items(routines, key = { it.id }) { r ->
                        GradientCard(Modifier.clickable {
                            RoutineWidget.saveMapping(this@WidgetConfigActivity, widgetId, r.id)
                            RoutineWidget.update(
                                this@WidgetConfigActivity,
                                AppWidgetManager.getInstance(this@WidgetConfigActivity), widgetId
                            )
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                            finish()
                        }) {
                            Text(r.name, color = TextPrim, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
