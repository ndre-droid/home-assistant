package com.nahuel.homeflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nahuel.homeflow.R
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.engine.RoutineEngine

/** One widget = one routine. Tap -> routine runs immediately, app does not open. */
class RoutineWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_RUN = "com.nahuel.homeflow.WIDGET_RUN"
        private const val PREFS = "widget_prefs"

        fun saveMapping(ctx: Context, widgetId: Int, routineId: String) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString("routine_$widgetId", routineId).apply()
        }

        fun routineFor(ctx: Context, widgetId: Int): String? =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("routine_$widgetId", null)

        fun update(ctx: Context, manager: AppWidgetManager, widgetId: Int) {
            Store.init(ctx.applicationContext)
            val routineId = routineFor(ctx, widgetId)
            val name = routineId?.let { Store.routine(it)?.name } ?: "HomeFlow"
            val views = RemoteViews(ctx.packageName, R.layout.routine_widget)
            views.setTextViewText(R.id.widget_name, name)
            val intent = Intent(ctx, RoutineWidget::class.java).apply {
                action = ACTION_RUN
                putExtra("routineId", routineId)
                data = android.net.Uri.parse("homeflow://widget/$widgetId") // unique per widget
            }
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getBroadcast(ctx, widgetId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            manager.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, appWidgetManager, it) }
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
        appWidgetIds.forEach { prefs.remove("routine_$it") }
        prefs.apply()
    }
}
