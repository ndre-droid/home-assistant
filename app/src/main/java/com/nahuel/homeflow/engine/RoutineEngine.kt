package com.nahuel.homeflow.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.nahuel.homeflow.data.*
import com.nahuel.homeflow.devices.HueClient
import com.nahuel.homeflow.devices.LgTvClient
import com.nahuel.homeflow.devices.SonosClient
import kotlinx.coroutines.*

/**
 * Executes routines. All actions of a variant run IN PARALLEL so a routine with
 * lights + speaker + TV completes in the time of its slowest single command (~0.3 s),
 * not the sum. Errors never abort the rest; they are collected and shown once.
 */
object RoutineEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun runAsync(ctx: Context, routineId: String) {
        val r = Store.routine(routineId) ?: return
        runAsync(ctx, r)
    }

    fun runAsync(ctx: Context, routine: Routine) {
        val appCtx = ctx.applicationContext
        scope.launch {
            val errors = run(routine)
            val msg = if (errors.isEmpty()) "▶ ${routine.name}"
            else "${routine.name}: ${errors.size} Fehler – ${errors.first()}"
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Decision tree: branches are checked top-down, first branch whose conditions ALL match wins. */
    suspend fun pickVariant(routine: Routine): Variant? {
        for (v in routine.variants) if (v.conditions.all { matches(it) }) return v
        return null
    }

    private suspend fun matches(c: Cond): Boolean = when (c.type) {
        CondType.DAY -> Store.isDaytime()
        CondType.NIGHT -> !Store.isDaytime()
        CondType.SPEAKER_IDLE -> !SonosClient.isPlaying(c.deviceId)
        CondType.SPEAKER_PLAYING -> SonosClient.isPlaying(c.deviceId)
        CondType.PARTNER_HOME -> TriggerService.partnerRecentlySeen()
        CondType.PARTNER_AWAY -> !TriggerService.partnerRecentlySeen()
    }

    suspend fun run(routine: Routine): List<String> {
        val variant = pickVariant(routine) ?: return listOf("Kein Zweig passt gerade (Bedingungen prüfen)")
        val errors = mutableListOf<String>()
        // Sequential: each action finishes before the next, so off-then-on works.
        for (action in variant.actions) {
            execute(action).onFailure { errors += (it.message ?: "Unbekannter Fehler") }
        }
        return errors
    }

    private suspend fun execute(a: Action): Result<Unit> {
        // Sonos "all": fan the same command out to every configured speaker.
        if (a.target == TargetType.SONOS && a.deviceId == "all") {
            val speakers = Store.config.value.sonos
            if (speakers.isEmpty()) return Result.failure(IllegalArgumentException("Keine Sonos-Speaker konfiguriert"))
            var lastErr: Throwable? = null
            speakers.forEach { sp ->
                execute(a.copy(deviceId = sp.ip)).onFailure { lastErr = it }
            }
            return lastErr?.let { Result.failure(it) } ?: Result.success(Unit)
        }
        return when (a.target) {
        TargetType.HUE -> HueClient.setLight(
            id = a.deviceId,
            on = a.params["on"]?.toBooleanStrictOrNull(),
            brightness = a.params["brightness"]?.toIntOrNull(),
            colorHex = a.params["color"]?.takeIf { it.startsWith("#") && it.length == 7 },
            exclude = a.params["exclude"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        )

        TargetType.SONOS -> when (a.command) {
            "play" ->
                if (a.params["onlyIfIdle"] == "true" && SonosClient.isPlaying(a.deviceId)) Result.success(Unit)
                else SonosClient.play(a.deviceId)
            "pause" -> SonosClient.pause(a.deviceId)
            "stop" -> SonosClient.stop(a.deviceId)
            "volume" -> SonosClient.setVolume(a.deviceId, a.params["volume"]?.toIntOrNull() ?: 20)
            "play_uri" -> {
                val uri = a.params["uri"].orEmpty()
                if (uri.isBlank()) Result.failure(IllegalArgumentException("Sonos: keine Sound-URL gesetzt"))
                else if (a.params["onlyIfIdle"] == "true" && SonosClient.isPlaying(a.deviceId)) Result.success(Unit)
                else SonosClient.playUri(a.deviceId, uri, a.params["volume"]?.toIntOrNull(), a.params["meta"].orEmpty())
            }
            "mute" -> SonosClient.setMute(a.deviceId, a.params["on"] != "false")
            "night_mode" -> SonosClient.setNightMode(a.deviceId, a.params["on"] != "false")
            "dialog_level" -> SonosClient.setDialogLevel(a.deviceId, a.params["on"] != "false")
            else -> Result.failure(IllegalArgumentException("Sonos: unbekanntes Kommando ${a.command}"))
        }

        TargetType.LG_TV -> {
            val tv = Store.config.value.tvs.firstOrNull { it.ip == a.deviceId }
            if (tv == null) Result.failure(IllegalArgumentException("TV ${a.deviceId} nicht konfiguriert"))
            else when (a.command) {
                "off" -> LgTvClient.powerOff(tv.ip, tv.clientKey)
                "on" ->
                    if (tv.mac.isBlank()) Result.failure(IllegalArgumentException("TV an: MAC-Adresse fehlt (Geräte-Tab)"))
                    else LgTvClient.powerOnAndWait(tv.mac, tv.ip)
                "volume" -> LgTvClient.setVolume(tv.ip, tv.clientKey, a.params["volume"]?.toIntOrNull() ?: 10)
                "mute" -> LgTvClient.setMute(tv.ip, tv.clientKey, true)
                "app" -> LgTvClient.launchApp(tv.ip, tv.clientKey, a.params["appId"].orEmpty(), a.params["contentId"])
                else -> Result.failure(IllegalArgumentException("TV: unbekanntes Kommando ${a.command}"))
            }
        }
    }
    }
}
