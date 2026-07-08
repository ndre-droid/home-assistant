package com.nahuel.homeflow.engine

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlin.math.sqrt
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
    private var sensorManager: SensorManager? = null
    private var shakeListener: SensorEventListener? = null
    private var lastShake = 0L
    private var geoWatcher: Job? = null
    @Volatile private var wasHome: Boolean? = null

    companion object {
        const val CHANNEL_ID = "homeflow_trigger"

        @Volatile var partnerLastSeen = 0L

        /** True if the partner phone answered a ping within the last 20 minutes. */
        fun partnerRecentlySeen(): Boolean =
            System.currentTimeMillis() - partnerLastSeen < 20 * 60_000L

        fun hasStateTriggers(): Boolean =
            Store.routines.value.any { it.enabled && it.triggers.any { t -> t.type == TriggerType.DEVICE_STATE } }

        fun hasWifiTriggers(): Boolean =
            Store.routines.value.any { it.enabled && it.triggers.any { t -> t.type == TriggerType.LEAVE_WIFI } }

        /** Starts or stops the service depending on whether any background work exists. */
        fun sync(ctx: Context) {
            AlarmScheduler.rescheduleAll(ctx)   // (re)arm TIME/SUN triggers
            val intent = Intent(ctx, TriggerService::class.java)
            val needed = hasStateTriggers() || hasWifiTriggers() || Store.config.value.biasEnabled || Store.config.value.webServerEnabled || Store.config.value.shakeRoutineId.isNotBlank()
            if (needed && (Store.config.value.hueBridgeIp.isNotEmpty() || Store.config.value.webServerEnabled || Store.config.value.shakeRoutineId.isNotBlank())) {
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
        if (Store.config.value.webServerEnabled && !WebTriggerServer.isRunning()) {
            WebTriggerServer.start { id -> RoutineEngine.runAsync(applicationContext, id) }
        } else if (!Store.config.value.webServerEnabled && WebTriggerServer.isRunning()) {
            WebTriggerServer.stop()
        }
        registerShake()
        if (geoWatcher == null) geoWatcher = scope.launch { geofenceLoop() }
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
                // Debounce flaky wifi: wait, then only fire if STILL disconnected.
                if (!wifiConnected) return
                wifiConnected = false
                scope.launch {
                    kotlinx.coroutines.delay(8000)          // grace period for brief dropouts
                    if (!wifiConnected) runCatching { onWifiLeft() }
                }
            }
        }
        runCatching { cm.registerNetworkCallback(req, netCallback!!) }
    }

    private fun onWifiLeft() {
        val partnerHome = Store.config.value.partnerIp.isNotBlank() && partnerRecentlySeen()
        Store.routines.value
            .filter { r -> r.enabled && r.triggers.any { it.type == TriggerType.LEAVE_WIFI } }
            .forEach { r ->
                val wifiTrig = r.triggers.first { it.type == TriggerType.LEAVE_WIFI }
                if (wifiTrig.partnerAware && partnerHome) return@forEach // partner still home
                runCatching { RoutineEngine.runAsync(this, r) }
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
                it.enabled && it.triggers.any { t ->
                    t.type == TriggerType.DEVICE_STATE && t.hueLightId == lightId && t.toState == on
                }
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

    /** Polls location every ~90s; fires ARRIVE_HOME/LEAVE_HOME when crossing the home radius. */
    private suspend fun geofenceLoop() {
        while (true) {
            val cfg = Store.config.value
            val hasGeoRoutine = Store.routines.value.any { r ->
                r.enabled && r.triggers.any { it.type == TriggerType.ARRIVE_HOME || it.type == TriggerType.LEAVE_HOME }
            }
            if (hasGeoRoutine && cfg.homeLat != 0.0 && cfg.homeLon != 0.0 && hasLocationPermission()) {
                val loc = lastKnownLocation()
                if (loc != null) {
                    val d = FloatArray(1)
                    Location.distanceBetween(loc.latitude, loc.longitude, cfg.homeLat, cfg.homeLon, d)
                    val inside = d[0] <= cfg.geofenceRadius
                    val prev = wasHome
                    if (prev != null && prev != inside) {
                        val want = if (inside) TriggerType.ARRIVE_HOME else TriggerType.LEAVE_HOME
                        Store.routines.value.filter { r ->
                            r.enabled && r.triggers.any { it.type == want }
                        }.forEach { runCatching { RoutineEngine.runAsync(this, it) } }
                    }
                    wasHome = inside
                }
            }
            kotlinx.coroutines.delay(90_000)
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun lastKnownLocation(): Location? = runCatching {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }.getOrNull()

    private fun registerShake() {
        if (Store.config.value.shakeRoutineId.isBlank()) { unregisterShake(); return }
        if (shakeListener != null) return
        val sm = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager = sm
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        shakeListener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val g = sqrt(e.values[0]*e.values[0] + e.values[1]*e.values[1] + e.values[2]*e.values[2]) / 9.81f
                if (g > 2.7f) {   // hard shake threshold
                    val now = System.currentTimeMillis()
                    if (now - lastShake > 2000) {   // debounce
                        lastShake = now
                        val id = Store.config.value.shakeRoutineId
                        if (id.isNotBlank()) RoutineEngine.runAsync(applicationContext, id)
                    }
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(shakeListener, accel, SensorManager.SENSOR_DELAY_UI)
    }

    private fun unregisterShake() {
        shakeListener?.let { sensorManager?.unregisterListener(it) }
        shakeListener = null
    }

    override fun onDestroy() {
        unregisterShake()
        WebTriggerServer.stop()
        eventSource?.cancel()
        netCallback?.let {
            runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) }
        }
        scope.cancel()   // beendet Bias- und Partner-Loop
        super.onDestroy()
    }
}
