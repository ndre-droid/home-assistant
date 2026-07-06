package com.nahuel.homeflow.engine

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.net.InetAddress
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nahuel.homeflow.MainActivity
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.data.TriggerType
import com.nahuel.homeflow.devices.HueClient
import com.nahuel.homeflow.devices.LgTvClient
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
    private var biasWatcher: Job? = null
    private var partnerWatcher: Job? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var wifiConnected = false
    @Volatile private var partnerLastSeen = 0L

    companion object {
        const val CHANNEL_ID = "homeflow_trigger"

        fun hasStateTriggers(): Boolean =
            Store.routines.value.any { it.enabled && it.trigger.type == TriggerType.DEVICE_STATE }

        fun hasWifiTriggers(): Boolean =
            Store.routines.value.any { it.enabled && it.trigger.type == TriggerType.LEAVE_WIFI }

        /** Starts or stops the service depending on whether any background work exists. */
        fun sync(ctx: Context) {
            val intent = Intent(ctx, TriggerService::class.java)
            val needed = hasStateTriggers() || hasWifiTriggers() || Store.config.value.biasEnabled
            if (needed && Store.config.value.hueBridgeIp.isNotEmpty()) {
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
        if (biasWatcher == null) biasWatcher = scope.launch { biasLoop() }
        if (partnerWatcher == null) partnerWatcher = scope.launch { partnerLoop() }
        if (netCallback == null) registerWifiWatch()
        return START_STICKY
    }

    // ---------- "Leave home" trigger: WiFi lost = left the flat ----------

    private fun registerWifiWatch() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        netCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { wifiConnected = true }
            override fun onLost(network: Network) {
                if (wifiConnected) { wifiConnected = false; onWifiLeft() }
            }
        }
        runCatching { cm.registerNetworkCallback(req, netCallback!!) }
    }

    private fun onWifiLeft() {
        val partnerConfigured = Store.config.value.partnerIp.isNotBlank()
        val partnerHome = partnerConfigured &&
                System.currentTimeMillis() - partnerLastSeen < 20 * 60_000
        Store.routines.value
            .filter { it.enabled && it.trigger.type == TriggerType.LEAVE_WIFI }
            .forEach { r ->
                if (r.trigger.partnerAware && partnerHome) return@forEach // partner still home
                RoutineEngine.runAsync(this, r)
            }
    }

    /** Pings the partner phone while on WiFi; remembers when it was last seen. */
    private suspend fun partnerLoop() {
        while (true) {
            val ip = Store.config.value.partnerIp
            if (wifiConnected && ip.isNotBlank()) {
                runCatching {
                    if (InetAddress.getByName(ip).isReachable(2500)) {
                        partnerLastSeen = System.currentTimeMillis()
                    }
                }
            }
            delay(60_000)
        }
    }

    // ---------- TV bias lighting (content-type mood, no sync box) ----------

    /** appId -> (colorHex, brightnessPct). Approximate mood per content type. */
    private fun moodFor(appId: String): Pair<String, Int> = when {
        appId.contains("netflix") || appId.contains("amazon") || appId.contains("disney") ||
                appId.contains("appletv") || appId.contains("hbo") || appId.contains("wow") ->
            "#FF8A3C" to 12                                  // Film/Serie: warmes, dunkles Orange
        appId.contains("hdmi") -> "#FF8A3C" to 12            // Konsole/Player: wie Kino
        appId.contains("youtube") -> "#8B7CF7" to 25         // YouTube: violett, etwas heller
        appId.contains("livetv") -> "#3D8BFD" to 20          // Live-TV: kühles Blau
        else -> "#FFD9A0" to 25                              // Home/unbekannt: sanftes Warmweiß
    }

    private suspend fun biasLoop() {
        var lastApp: String? = null
        var tvWasOn = false
        while (true) {
            val cfg = Store.config.value
            if (cfg.biasEnabled && cfg.biasLights.isNotEmpty() && cfg.tvs.isNotEmpty()) {
                val tv = cfg.tvs.firstOrNull { it.ip == cfg.biasTv } ?: cfg.tvs.first()
                val app = if (tv.clientKey.isNotEmpty())
                    LgTvClient.getForegroundApp(tv.ip, tv.clientKey).getOrNull() else null
                if (app != null) {
                    tvWasOn = true
                    if (app != lastApp) {
                        lastApp = app
                        val (color, bri) = moodFor(app)
                        cfg.biasLights.forEach { id ->
                            HueClient.setLight(id, on = true, brightness = bri, colorHex = color)
                        }
                    }
                } else if (tvWasOn) {
                    // TV ging aus -> Bias-Licht aus
                    tvWasOn = false; lastApp = null
                    cfg.biasLights.forEach { id ->
                        HueClient.setLight(id, on = false, brightness = null, colorHex = null)
                    }
                }
            } else {
                lastApp = null
            }
            delay(5_000)
        }
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
        netCallback?.let {
            runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) }
        }
        scope.cancel()   // beendet Bias- und Partner-Loop
        super.onDestroy()
    }
}
