package com.nahuel.homeflow.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nahuel.homeflow.R
import com.nahuel.homeflow.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while a routine started without the UI (NFC tag) runs, so a long
 * routine (wake-up fade, ...) isn't killed once the app is in the background. Lives exactly as
 * long as its runs; the routine itself still runs in RoutineEngine's own scope.
 */
class RoutineRunService : Service() {
    companion object {
        private const val CHANNEL_ID = "smartflow_running"
        private const val NOTIFICATION_ID = 3
        private const val EXTRA_ROUTINE_ID = "routineId"

        /** Starts the routine (toggle semantics). Falls back to a plain run if the service can't start. */
        fun start(ctx: Context, routineId: String) {
            val intent = Intent(ctx, RoutineRunService::class.java).putExtra(EXTRA_ROUTINE_ID, routineId)
            runCatching { ctx.startForegroundService(intent) }
                .onFailure { RoutineEngine.runAsync(ctx, routineId) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var active = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_ROUTINE_ID)
        startInForeground(id?.let { Store.routine(it)?.name })
        val job = id?.let { RoutineEngine.runAsync(this, it) }
        if (job == null) {
            // Nothing new started (stopped via toggle, unknown id): only stay for other runs.
            if (active == 0) stopSelf()
            return START_NOT_STICKY
        }
        active++
        scope.launch {
            job.join()
            if (--active == 0) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(routineName: String?) {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Laufende Automationen", NotificationManager.IMPORTANCE_LOW)
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_smartflow)
            .setContentTitle(routineName?.let { "▶ $it" } ?: "SmartFlow")
            .setContentText("Automation läuft")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
