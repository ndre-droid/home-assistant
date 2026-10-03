package com.nahuel.homeflow.ui

import com.nahuel.homeflow.data.LgTv
import com.nahuel.homeflow.data.SonosSpeaker
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.devices.HueClient
import com.nahuel.homeflow.devices.HueLight
import com.nahuel.homeflow.devices.HueRoom
import com.nahuel.homeflow.devices.LgTvClient
import com.nahuel.homeflow.devices.SonosClient
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

/** One room as the Home tab shows it: Hue lights plus the speakers/TVs assigned to it. */
data class RoomInfo(
    val id: String,
    val name: String,
    val lights: List<HueLight>,
    val speakers: List<SonosSpeaker>,
    val tvs: List<LgTv>
) {
    val onLights: List<HueLight> get() = lights.filter { it.on }
    val anyOn: Boolean get() = onLights.isNotEmpty()
    val avgBrightness: Int get() = onLights.map { it.brightness }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
    val hasMedia: Boolean get() = speakers.isNotEmpty() || tvs.isNotEmpty()
}

data class SpeakerState(val playing: Boolean, val volume: Int?, val title: String)

/**
 * Live device state shared by Home and Room screens. Rooms come from the Hue
 * bridge; Sonos and TVs are mapped to rooms via Config.deviceRooms. Anything
 * without a room lands in a catch-all "Weitere" room.
 */
object HomeRepo {
    const val OTHER_ROOM = "_other"

    private val _rooms = MutableStateFlow<List<RoomInfo>>(emptyList())
    val rooms: StateFlow<List<RoomInfo>> = _rooms

    private val _lights = MutableStateFlow<List<HueLight>>(emptyList())
    val lights: StateFlow<List<HueLight>> = _lights

    private val _speakers = MutableStateFlow<Map<String, SpeakerState>>(emptyMap())
    val speakers: StateFlow<Map<String, SpeakerState>> = _speakers

    /** TV ip -> reachable (on). Missing = unknown. */
    private val _tvOn = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val tvOn: StateFlow<Map<String, Boolean>> = _tvOn

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    @Volatile private var hueRooms: List<HueRoom> = emptyList()

    fun clearError() { _error.value = null }
    fun reportError(msg: String) { _error.value = msg }

    /** Pull fresh state from all devices. TVs are slow to probe, so only on request. */
    suspend fun refresh(includeTv: Boolean = false) = coroutineScope {
        val cfg = Store.config.value
        val hue = async<Unit> {
            if (cfg.hueAppKey.isEmpty() || cfg.hueBridgeIp.isEmpty()) return@async
            HueClient.lights()
                .onSuccess { _lights.value = it; if (_error.value?.startsWith("Hue") == true) _error.value = null }
                .onFailure { _error.value = "Hue nicht erreichbar: ${it.message}" }
            HueClient.rooms().onSuccess { hueRooms = it }
        }
        val sonos = async {
            cfg.sonos.map { s ->
                async {
                    s.ip to SpeakerState(
                        playing = SonosClient.isPlaying(s.ip),
                        volume = SonosClient.getVolume(s.ip).getOrNull(),
                        title = SonosClient.getMedia(s.ip).getOrNull()?.let { mediaTitle(it.first, it.second) } ?: ""
                    )
                }
            }.awaitAll().toMap()
        }
        val tvs = if (includeTv) async {
            cfg.tvs.filter { it.clientKey.isNotEmpty() }.map { t ->
                async {
                    t.ip to (withTimeoutOrNull(4_000) { LgTvClient.getForegroundApp(t.ip, t.clientKey).isSuccess } ?: false)
                }
            }.awaitAll().toMap()
        } else null
        hue.await()
        _speakers.value = sonos.await()
        tvs?.await()?.let { _tvOn.value = it }
        _loaded.value = true
        rebuild()
    }

    /** Recompute rooms from the cached state (cheap; call after config changes). */
    fun rebuild() {
        val cfg = Store.config.value
        val all = orderLights(_lights.value, cfg.lightOrder)
        val built = hueRooms.map { r ->
            RoomInfo(
                id = r.id,
                name = r.name,
                lights = all.filter { it.ownerId in r.deviceIds },
                speakers = cfg.sonos.filter { cfg.deviceRooms[it.ip] == r.id },
                tvs = cfg.tvs.filter { cfg.deviceRooms[it.ip] == r.id }
            )
        }.filter { it.lights.isNotEmpty() || it.hasMedia }
        val usedLights = built.flatMap { it.lights }.map { it.id }.toSet()
        val roomIds = hueRooms.map { it.id }.toSet()
        val rest = RoomInfo(
            id = OTHER_ROOM,
            name = if (built.isEmpty()) "Zuhause" else "Weitere",
            lights = all.filter { it.id !in usedLights },
            speakers = cfg.sonos.filter { cfg.deviceRooms[it.ip] !in roomIds },
            tvs = cfg.tvs.filter { cfg.deviceRooms[it.ip] !in roomIds }
        )
        // Rooms with media first (usually the living room), otherwise Hue order.
        val ordered = built.sortedByDescending { it.hasMedia }
        _rooms.value = if (rest.lights.isNotEmpty() || rest.hasMedia) ordered + rest else ordered
    }

    /** Optimistic light update, then the bridge call. brightness implies on. */
    suspend fun setLights(ids: List<String>, on: Boolean?, brightness: Int? = null, colorHex: String? = null) {
        if (ids.isEmpty()) return
        val turnOn = on ?: if (brightness != null) true else null
        _lights.value = _lights.value.map { l ->
            if (l.id !in ids) l else l.copy(
                on = turnOn ?: l.on,
                brightness = brightness?.coerceIn(1, 100) ?: l.brightness,
                colorHex = if (l.supportsColor && colorHex != null) colorHex else l.colorHex
            )
        }
        rebuild()
        coroutineScope {
            ids.map { id ->
                async {
                    val light = _lights.value.firstOrNull { it.id == id }
                    HueClient.setLight(
                        id, turnOn, brightness?.coerceIn(1, 100),
                        if (light?.supportsColor == true) colorHex else null
                    ).onFailure { _error.value = "Hue: ${it.message}" }
                }
            }.awaitAll()
        }
    }

    suspend fun togglePlay(ip: String) {
        val playing = _speakers.value[ip]?.playing ?: false
        _speakers.value = _speakers.value + (ip to (_speakers.value[ip] ?: SpeakerState(false, null, "")).copy(playing = !playing))
        val r = if (playing) SonosClient.pause(ip) else SonosClient.play(ip)
        r.onFailure { _error.value = "Sonos: ${it.message}" }
    }

    suspend fun setVolume(ip: String, volume: Int) {
        _speakers.value[ip]?.let { _speakers.value = _speakers.value + (ip to it.copy(volume = volume)) }
        SonosClient.setVolume(ip, volume).onFailure { _error.value = "Sonos: ${it.message}" }
    }

    fun setTvOn(ip: String, on: Boolean) { _tvOn.value = _tvOn.value + (ip to on) }

    /** Everything off: lights, speakers paused, TVs off. */
    suspend fun allOff() = coroutineScope {
        val cfg = Store.config.value
        val jobs = mutableListOf<Deferred<Any?>>()
        jobs += async {
            if (_lights.value.isEmpty()) HueClient.setLight("all", on = false, brightness = null, colorHex = null)
            else setLights(_lights.value.map { it.id }, on = false)
        }
        cfg.sonos.forEach { s -> jobs += async { SonosClient.pause(s.ip) } }
        cfg.tvs.filter { it.clientKey.isNotEmpty() }.forEach { t ->
            jobs += async { LgTvClient.powerOff(t.ip, t.clientKey); setTvOn(t.ip, false) }
        }
        jobs.awaitAll()
        _speakers.value = _speakers.value.mapValues { it.value.copy(playing = false) }
    }

    private fun mediaTitle(uri: String, meta: String): String {
        fun tag(name: String) = Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL)
            .find(meta)?.groupValues?.get(1)?.trim().orEmpty()
        val stream = tag("r:streamContent")
        val title = tag("dc:title")
        return when {
            stream.isNotEmpty() -> stream
            title.isNotEmpty() && !title.startsWith("x-") -> title
            uri.startsWith("x-sonos-htastream") -> "TV-Ton"
            uri.isNotEmpty() -> "Stream"
            else -> ""
        }
    }
}

