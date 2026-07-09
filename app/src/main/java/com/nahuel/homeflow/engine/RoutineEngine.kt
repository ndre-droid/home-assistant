package com.nahuel.homeflow.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.nahuel.homeflow.data.*
import com.nahuel.homeflow.devices.HueClient
import com.nahuel.homeflow.devices.LgTvClient
import com.nahuel.homeflow.devices.SonosClient
import com.nahuel.homeflow.devices.GenericClient
import kotlinx.coroutines.*

/**
 * Executes routines. All actions of a variant run IN PARALLEL so a routine with
 * lights + speaker + TV completes in the time of its slowest single command (~0.3 s),
 * not the sum. Errors never abort the rest; they are collected and shown once.
 */
object RoutineEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // One running instance per routine. Starting again while running = STOP (toggle).
    private val running = java.util.concurrent.ConcurrentHashMap<String, Job>()

    private fun toast(ctx: Context, msg: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    }

    fun runAsync(ctx: Context, routineId: String) {
        val r = Store.routine(routineId) ?: return
        runAsync(ctx, r)
    }

    fun runAsync(ctx: Context, routine: Routine) {
        val appCtx = ctx.applicationContext
        // Already running (wake-up fade, party, ...)? Second start stops it instead of stacking.
        running[routine.id]?.let { job ->
            if (job.isActive) {
                job.cancel()
                running.remove(routine.id)
                Store.logRun(routine.name, true, "gestoppt")
                toast(appCtx, "⏹ ${routine.name} gestoppt")
                return
            }
        }
        val job = scope.launch {
            val errors = run(routine)
            Store.logRun(routine.name, errors.isEmpty(), errors.firstOrNull() ?: "")
            val msg = if (errors.isEmpty()) "▶ ${routine.name}"
            else "${routine.name}: ${errors.size} Fehler, ${errors.first()}"
            toast(appCtx, msg)
        }
        running[routine.id] = job
        job.invokeOnCompletion { running.remove(routine.id) }
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
            currentCoroutineContext().ensureActive()
            execute(action).onFailure {
                if (it is CancellationException) throw it
                errors += (it.message ?: "Unbekannter Fehler")
            }
        }
        return errors
    }

    /** True if a specific light was switched off from outside (user intervened) - long-runners then stop. */
    private suspend fun externallyOff(lightId: String): Boolean {
        if (lightId == "all") return false
        return HueClient.lights().getOrNull()?.firstOrNull { it.id == lightId }?.on == false
    }

    /** Sunrise-style fade: deep red -> warm -> bright white over `minutes`.
     *  Aborts silently if cancelled or if the light gets turned off manually. */
    private suspend fun wakeUp(a: Action): Result<Unit> = runCatching {
        val minutes = a.params["minutes"]?.toIntOrNull()?.coerceIn(1, 60) ?: 20
        val steps = 20
        val stepMs = (minutes * 60_000L) / steps
        val colors = listOf("#3A0A0A", "#5A1A0A", "#8A3A10", "#B5651D", "#D9963C", "#F0C270", "#FFE8C0", "#FFFFFF")
        for (i in 0 until steps) {
            currentCoroutineContext().ensureActive()
            if (i > 0 && externallyOff(a.deviceId)) return@runCatching   // user turned it off - respect that
            val t = i.toFloat() / (steps - 1)
            val bri = (5 + t * 95).toInt()
            val color = colors[(t * (colors.size - 1)).toInt().coerceIn(0, colors.size - 1)]
            HueClient.setLight(a.deviceId, on = true, brightness = bri, colorHex = color)
            kotlinx.coroutines.delay(stepMs)
        }
    }

    /** Party: cycle vivid colors on the lights for `seconds`. */
    private suspend fun party(a: Action): Result<Unit> = runCatching {
        val seconds = a.params["seconds"]?.toIntOrNull()?.coerceIn(5, 600) ?: 60
        val mode = a.params["mode"] ?: "rave"
        // Each preset: palette, step delay (ms), brightness (or -1 = alternate flash).
        val (colors, stepMs, bri) = when (mode) {
            "chill"  -> Triple(listOf("#FF8C42", "#FF6B6B", "#C44FD4", "#6B5BE8"), 2500L, 45)
            "strobe" -> Triple(listOf("#FFFFFF", "#000010"), 120L, 100)
            "sunset" -> Triple(listOf("#FFB347", "#FF7F50", "#FF6B6B", "#C71585", "#4B2E83"), 3000L, 55)
            "ocean"  -> Triple(listOf("#00CED1", "#1E90FF", "#20B2AA", "#4169E1", "#00FFFF"), 2000L, 50)
            else     -> Triple(listOf("#FF0000", "#FF7F00", "#FFFF00", "#00FF00", "#00FFFF", "#0000FF", "#FF00FF"), 600L, 100)
        }
        val endAt = System.currentTimeMillis() + seconds * 1000L
        var i = 0
        var lastExternCheck = 0L
        while (System.currentTimeMillis() < endAt) {
            currentCoroutineContext().ensureActive()
            val now = System.currentTimeMillis()
            if (now - lastExternCheck > 2000) {
                lastExternCheck = now
                if (externallyOff(a.deviceId)) break   // user turned the light off - stop the party
            }
            val c = colors[i % colors.size]
            // strobe: flash on/off by alternating a near-black "off" color
            val b = if (mode == "strobe" && c == "#000010") 1 else bri
            HueClient.setLight(a.deviceId, on = true, brightness = b, colorHex = c)
            i++
            kotlinx.coroutines.delay(stepMs)
        }
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
        TargetType.HUE -> when (a.command) {
            "wakeup" -> wakeUp(a)   // fade deep-red -> bright white over N minutes
            "party"  -> party(a)    // cycle colors for N seconds
            "countdown_off" -> runCatching {
                val min = a.params["minutes"]?.toIntOrNull()?.coerceIn(1, 240) ?: 10
                kotlinx.coroutines.delay(min * 60_000L)
                HueClient.setLight(a.deviceId, on = false, brightness = null, colorHex = null).getOrThrow()
            }
            else -> HueClient.setLight(
                id = a.deviceId,
                on = a.params["on"]?.toBooleanStrictOrNull(),
                brightness = a.params["brightness"]?.toIntOrNull(),
                colorHex = a.params["color"]?.takeIf { it.startsWith("#") && it.length == 7 },
                exclude = a.params["exclude"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            )
        }

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
                "app" -> LgTvClient.openApp(tv.ip, tv.clientKey, tv.mac, a.params["appId"].orEmpty(), a.params["contentId"])
                else -> Result.failure(IllegalArgumentException("TV: unbekanntes Kommando ${a.command}"))
            }
        }

        TargetType.GENERIC -> {
            val dev = Store.config.value.generics.firstOrNull { it.name == a.deviceId }
            if (dev == null) Result.failure(IllegalArgumentException("Gerät ${a.deviceId} nicht konfiguriert"))
            else GenericClient.fire(dev.url, dev.method, dev.body)
        }
    }
    }
}
