package com.nahuel.homeflow.engine

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nahuel.homeflow.MainActivity
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.data.TriggerType
import com.nahuel.homeflow.devices.HueClient
import kotlinx.coroutines.*
import okhttp3.sse.EventSource

/**
 * Foreground service that keeps a connection to the Hue event stream open and fires
 * routines with DEVICE_STATE triggers. Only needed for those; NFC/widget/manual
 * triggers work without any background service.
 */
class TriggerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var eventSource: EventSource? = null
    private var watcher: Job? = null

    companion object {
        const val CHANNEL_ID = "homeflow_trigger"

        fun hasStateTriggers(): Boolean =
            Store.routines.value.any { it.enabled && it.trigger.type == TriggerType.DEVICE_STATE }

        /** Starts or stops the service depending on whether any state trigger exists. */
        fun sync(ctx: Context) {
            val intent = Intent(ctx, TriggerService::class.java)
            if (hasStateTriggers() && Store.config.value.hueBridgeIp.isNotEmpty()) {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(intent) else ctx.startService(intent)
            } else {
                ctx.stopService(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (watcher == null) watcher = scope.launch { connectLoop() }
        return START_STICKY
    }

    private suspend fun connectLoop() {
        while (scope.isActive) {
            val reconnect = CompletableDeferred<Unit>()
            eventSource?.cancel()
            eventSource = HueClient.openEventStream(
                onLightEvent = { lightId, on -> onLightEvent(lightId, on) },
                onFailure = { if (!reconnect.isCompleted) reconnect.complete(Unit) }
            )
            reconnect.await()          // stream died -> retry with backoff
            delay(5_000)
        }
    }

    private fun onLightEvent(lightId: String, on: Boolean) {
        Store.routines.value
            .filter {
                it.enabled && it.trigger.type == TriggerType.DEVICE_STATE &&
                        it.trigger.hueLightId == lightId && it.trigger.toState == on
            }
            .forEach { RoutineEngine.runAsync(this, it) }
    }

    private fun startInForeground() {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HomeFlow aktiv")
            .setContentText("Wartet auf Geräte-Trigger (z. B. Badlicht)")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Geräte-Trigger", NotificationManager.IMPORTANCE_MIN
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        eventSource?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
