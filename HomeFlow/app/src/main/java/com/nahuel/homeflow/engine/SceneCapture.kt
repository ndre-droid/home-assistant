package com.nahuel.homeflow.engine

import com.nahuel.homeflow.data.*
import com.nahuel.homeflow.devices.HueClient
import com.nahuel.homeflow.devices.SonosClient

/**
 * "Scene Sync": snapshots the CURRENT state of selected devices into a Routine.
 * Lights: on/off, brightness, color. Sonos: volume + whatever is playing right now.
 * The result is a plain Routine, so widgets, NFC tags, toggles and Claude editing
 * all work on scenes automatically.
 */
object SceneCapture {

    suspend fun capture(
        name: String,
        lightIds: Set<String>,
        speakerIps: Set<String>,
        nightModeFor: Set<String>   // speakers that should get night mode + speech enhancement ON
    ): Result<Routine> = runCatching {
        val actions = mutableListOf<Action>()

        if (lightIds.isNotEmpty()) {
            val lights = HueClient.lights().getOrThrow().filter { it.id in lightIds }
            lights.forEach { l ->
                val p = mutableMapOf("on" to l.on.toString())
                if (l.on) {
                    p["brightness"] = l.brightness.toString()
                    if (l.supportsColor && l.colorHex != null) p["color"] = l.colorHex
                }
                actions += Action(TargetType.HUE, l.id, "set", p)
            }
        }

        speakerIps.forEach { ip ->
            val volume = SonosClient.getVolume(ip).getOrNull()
            val playing = SonosClient.isPlaying(ip)
            val media = SonosClient.getMedia(ip).getOrNull()
            if (playing && media != null && media.first.isNotBlank()) {
                val p = mutableMapOf("uri" to media.first)
                if (media.second.isNotBlank()) p["meta"] = media.second
                volume?.let { p["volume"] = it.toString() }
                actions += Action(TargetType.SONOS, ip, "play_uri", p)
            } else if (volume != null) {
                actions += Action(TargetType.SONOS, ip, "volume", mapOf("volume" to volume.toString()))
            }
            if (ip in nightModeFor) {
                actions += Action(TargetType.SONOS, ip, "night_mode", mapOf("on" to "true"))
                actions += Action(TargetType.SONOS, ip, "dialog_level", mapOf("on" to "true"))
            }
        }

        check(actions.isNotEmpty()) { "Keine Geräte ausgewählt oder nichts lesbar" }
        Routine(
            name = name.ifBlank { "Szene" },
            trigger = Trigger(TriggerType.MANUAL),
            variants = listOf(Variant(ConditionType.ALWAYS, actions))
        )
    }
}
