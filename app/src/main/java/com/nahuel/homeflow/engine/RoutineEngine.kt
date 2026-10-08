package com.nahuel.homeflow.engine

import android.content.Context
import com.nahuel.homeflow.data.*
import com.nahuel.homeflow.devices.HueClient
import com.nahuel.homeflow.devices.LgTvClient
import com.nahuel.homeflow.devices.SonosClient
import com.nahuel.homeflow.devices.SpotifyClient
import com.nahuel.homeflow.devices.GenericClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Executes routines. Actions of the chosen branch run SEQUENTIALLY (each finishes before the
 * next starts, so "off, then on" works). Errors never abort the rest; they are collected and
 * reported once.
 */
object RoutineEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // One running instance per routine.
    private val running = java.util.concurrent.ConcurrentHashMap<String, Job>()

    /** Light states captured right before the last run, so it can be reverted with one tap. */
    data class UndoPoint(val routineName: String, val at: Long, val actions: List<Action>)

    private val _undo = MutableStateFlow<UndoPoint?>(null)
    val undo: StateFlow<UndoPoint?> = _undo
    const val UNDO_WINDOW_MS = 15 * 60_000L

    fun runAsync(ctx: Context, routineId: String, toggle: Boolean = true) {
        val r = Store.routine(routineId) ?: return
        runAsync(ctx, r, toggle)
    }

    /**
     * @param toggle true for user-started runs (tap, widget, NFC, tile, shake, web):
     *   starting a routine that is still running (wake-up fade, party, ...) STOPS it.
     *   Automatic triggers pass false, so a repeated event never cancels a running routine.
     */
    fun runAsync(ctx: Context, routine: Routine, toggle: Boolean = true) {
        val appCtx = ctx.applicationContext
        running[routine.id]?.let { job ->
            if (job.isActive) {
                if (!toggle) return
                job.cancel()
                running.remove(routine.id)
                Store.logRun(routine.name, true, "gestoppt")
                Notifier.result(appCtx, "⏹ ${routine.name} gestoppt", isError = false)
                return
            }
        }
        val job = scope.launch {
            val errors = run(routine)
            Store.logRun(routine.name, errors.isEmpty(), errors.firstOrNull() ?: "")
            if (errors.isEmpty()) Notifier.result(appCtx, "▶ ${routine.name}", isError = false)
            else Notifier.result(appCtx, "${routine.name}: ${errors.size} Fehler, ${errors.first()}", isError = true)
        }
        running[routine.id] = job
        job.invokeOnCompletion { running.remove(routine.id) }
    }

    /** Restores the lights to how they were before the last routine. */
    fun undoLast(ctx: Context) {
        val point = _undo.value ?: return
        _undo.value = null
        val appCtx = ctx.applicationContext
        scope.launch {
            val errors = point.actions.mapNotNull { a -> execute(a).exceptionOrNull()?.message }
            Store.logRun("Rückgängig: ${point.routineName}", errors.isEmpty(), errors.firstOrNull() ?: "")
            Notifier.result(appCtx, if (errors.isEmpty()) "↩ ${point.routineName} rückgängig" else "Rückgängig: ${errors.first()}", errors.isNotEmpty())
        }
    }

    fun undoAvailable(now: Long = System.currentTimeMillis()): UndoPoint? =
        _undo.value?.takeIf { now - it.at < UNDO_WINDOW_MS }

    private suspend fun snapshot(routine: Routine, variant: Variant) {
        val hueIds = variant.actions.filter { it.target == TargetType.HUE }.map { it.deviceId }.toSet()
        if (hueIds.isEmpty()) return
        val lights = HueClient.lights().getOrNull() ?: return
        val affected = if ("all" in hueIds) lights else lights.filter { it.id in hueIds }
        val actions = SceneCapture.lightStateActions(affected)
        if (actions.isNotEmpty()) _undo.value = UndoPoint(routine.name, System.currentTimeMillis(), actions)
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
        runCatching { snapshot(routine, variant) }
        val errors = mutableListOf<String>()
        val moved = mutableMapOf<String, String>()   // devices re-found mid-run: old IP -> new IP
        // Sequential: each action finishes before the next, so off-then-on works.
        for (action in variant.actions) {
            currentCoroutineContext().ensureActive()
            executeHealing(action, moved).onFailure {
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

    /**
     * Runs [a]; if its device isn't reachable at the stored IP, re-finds moved devices once
     * (DeviceRelocator.heal) and retries. [moved] carries the new IPs to later actions of this run.
     */
    private suspend fun executeHealing(a: Action, moved: MutableMap<String, String>): Result<Unit> {
        fun relocated(x: Action) =
            if (x.target == TargetType.SONOS || x.target == TargetType.LG_TV) x.copy(deviceId = moved[x.deviceId] ?: x.deviceId) else x
        val first = execute(relocated(a))
        if (first.isSuccess || a.target == TargetType.GENERIC || !DeviceRelocator.isUnreachable(first.exceptionOrNull())) return first
        val healed = DeviceRelocator.heal() ?: return first
        moved += healed
        return execute(relocated(a))
    }

    private suspend fun execute(a: Action): Result<Unit> {
        // Sonos "all": fan the same command out to every configured speaker.
        if (a.target == TargetType.SONOS && a.deviceId == "all") {
            val speakers = Store.config.value.sonos
            if (speakers.isEmpty()) return Result.failure(IllegalArgumentException("Keine Sonos-Speaker konfiguriert"))
            // Parallel: one unreachable speaker (retry + timeouts ~16 s) must not hold up the others
            // or the routine's later steps.
            val results = coroutineScope { speakers.map { sp -> async { execute(a.copy(deviceId = sp.ip)) } }.awaitAll() }
            return results.lastOrNull { it.isFailure } ?: Result.success(Unit)
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
            "spotify" -> SpotifyClient.play(
                a.params["query"].orEmpty(),
                Store.config.value.sonos.firstOrNull { it.ip == a.deviceId }?.name ?: ""
            )
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
