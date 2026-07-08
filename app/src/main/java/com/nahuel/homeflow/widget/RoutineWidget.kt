package com.nahuel.homeflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.nahuel.homeflow.R
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.engine.RoutineEngine

/**
 * Resizable multi-button widget. Holds up to 8 automations (ordered); shows as many
 * buttons as the current widget size allows. Each tap runs its automation, app stays closed.
 */
class RoutineWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_RUN = "com.nahuel.homeflow.WIDGET_RUN"
        private const val PREFS = "widget_prefs"
        private val CELL_IDS = intArrayOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3,
            R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7
        )
        private val ROW_IDS = intArrayOf(R.id.widget_row0, R.id.widget_row1, R.id.widget_row2, R.id.widget_row3)

        /** Save the ordered list of routine ids for this widget (comma-separated). */
        fun saveMapping(ctx: Context, widgetId: Int, routineIds: List<String>) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString("routines_$widgetId", routineIds.joinToString(",")).apply()
        }

        fun routinesFor(ctx: Context, widgetId: Int): List<String> =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("routines_$widgetId", null)
                ?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

        /** How many buttons fit, from the widget's current size (rows x 2 columns). */
        private fun visibleCount(ctx: Context, manager: AppWidgetManager, widgetId: Int, total: Int): Int {
            val opts: Bundle? = runCatching { manager.getAppWidgetOptions(widgetId) }.getOrNull()
            val minW = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110) ?: 110
            val minH = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40) ?: 40
            val cols = if (minW >= 180) 2 else 1
            val rows = when {
                minH >= 250 -> 4
                minH >= 180 -> 3
                minH >= 110 -> 2
                else -> 1
            }
            return (cols * rows).coerceIn(1, 8).coerceAtMost(if (total == 0) 1 else total)
        }

        fun update(ctx: Context, manager: AppWidgetManager, widgetId: Int) {
            Store.init(ctx.applicationContext)
            val ids = routinesFor(ctx, widgetId)
            val views = RemoteViews(ctx.packageName, R.layout.routine_widget)
            val count = visibleCount(ctx, manager, widgetId, ids.size)

            // Fill / hide the 8 cells.
            CELL_IDS.forEachIndexed { i, cellId ->
                if (i < count && i < ids.size) {
                    val name = Store.routine(ids[i])?.name ?: "?"
                    views.setTextViewText(cellId, name)
                    views.setViewVisibility(cellId, View.VISIBLE)
                    val intent = Intent(ctx, RoutineWidget::class.java).apply {
                        action = ACTION_RUN
                        putExtra("routineId", ids[i])
                        data = android.net.Uri.parse("homeflow://widget/$widgetId/$i")
                    }
                    views.setOnClickPendingIntent(
                        cellId,
                        PendingIntent.getBroadcast(ctx, widgetId * 10 + i, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    )
                } else if (i < count) {
                    // slot exists but no routine assigned -> empty placeholder
                    views.setTextViewText(cellId, "+")
                    views.setViewVisibility(cellId, View.VISIBLE)
                } else {
                    views.setViewVisibility(cellId, View.GONE)
                }
            }
            // Hide empty rows (both cells gone).
            ROW_IDS.forEachIndexed { r, rowId ->
                val firstVisible = r * 2 < count
                views.setViewVisibility(rowId, if (firstVisible) View.VISIBLE else View.GONE)
            }
            manager.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        update(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_RUN) {
            Store.init(context.applicationContext)
            intent.getStringExtra("routineId")?.let { RoutineEngine.runAsync(context, it) }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { prefs.remove("routines_$it") }
        prefs.apply()
    }
}
