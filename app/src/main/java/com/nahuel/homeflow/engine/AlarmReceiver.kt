package com.nahuel.homeflow.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nahuel.homeflow.data.Store

/** Fired by AlarmManager for TIME/SUN triggers. Runs the routine, then re-arms all alarms. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
        Store.init(context.applicationContext)
        intent.getStringExtra("routineId")?.let { RoutineEngine.runAsync(context, it) }
        // Re-arm for the next occurrence (alarms are one-shot).
        AlarmScheduler.rescheduleAll(context.applicationContext)
    }
}
