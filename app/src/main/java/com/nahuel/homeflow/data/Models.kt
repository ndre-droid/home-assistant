package com.nahuel.homeflow.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import com.nahuel.homeflow.ui.ThemeMode

/** How a routine is started. NFC and widget both end up calling RoutineEngine directly;
 *  DEVICE_STATE is watched by the foreground TriggerService via the Hue event stream. */
enum class TriggerType { MANUAL, NFC, DEVICE_STATE, LEAVE_WIFI, TIME, SUN, ARRIVE_HOME, LEAVE_HOME }

/** A single IF-condition on a branch. All conditions of a branch must match. */
enum class CondType { DAY, NIGHT, SPEAKER_IDLE, SPEAKER_PLAYING, PARTNER_HOME, PARTNER_AWAY }

data class Cond(val type: CondType, val deviceId: String = "") {
    fun toJson(): JSONObject = JSONObject().put("type", type.name).put("deviceId", deviceId)

    companion object {
        fun fromJson(o: JSONObject) = Cond(
            runCatching { CondType.valueOf(o.optString("type")) }.getOrDefault(CondType.DAY),
            o.optString("deviceId", "")
        )
    }
}

enum class TargetType { HUE, SONOS, LG_TV, GENERIC }

data class Trigger(
    val type: TriggerType = TriggerType.MANUAL,
    val hueLightId: String = "",
    val toState: Boolean = true, // fire when light turns on (true) or off (false)
    val partnerAware: Boolean = true, // LEAVE_WIFI: skip if partner device recently seen at home
    val time: String = "07:00",       // TIME trigger: HH:MM
    val sunEvent: String = "SUNSET",  // SUN trigger: SUNRISE | SUNSET
    val sunOffsetMin: Int = 0         // SUN: minutes before(-)/after(+) the event
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", type.name).put("hueLightId", hueLightId).put("toState", toState)
        .put("partnerAware", partnerAware)
        .put("time", time).put("sunEvent", sunEvent).put("sunOffsetMin", sunOffsetMin)

    companion object {
        fun fromJson(o: JSONObject) = Trigger(
            type = runCatching { TriggerType.valueOf(o.optString("type", "MANUAL")) }.getOrDefault(TriggerType.MANUAL),
            hueLightId = o.optString("hueLightId", ""),
            toState = o.optBoolean("toState", true),
            partnerAware = o.optBoolean("partnerAware", true),
            time = o.optString("time", "07:00"),
            sunEvent = o.optString("sunEvent", "SUNSET"),
            sunOffsetMin = o.optInt("sunOffsetMin", 0)
        )
    }
}

/**
 * One device command.
 * HUE:    command "set", deviceId = light id or "all"; params: on="true|false", color="#RRGGBB", brightness="0..100"
 * SONOS:  commands "play" | "pause" | "stop" | "volume" | "play_uri"; deviceId = speaker IP; params: volume, uri
 * LG_TV:  commands "on" | "off" | "volume" | "mute"; deviceId = TV IP; params: volume
 */
data class Action(
    val target: TargetType,
    val deviceId: String,
    val command: String,
    val params: Map<String, String> = emptyMap()
) {
    fun toJson(): JSONObject {
        val p = JSONObject(); params.forEach { (k, v) -> p.put(k, v) }
        return JSONObject().put("target", target.name).put("deviceId", deviceId)
            .put("command", command).put("params", p)
    }

    companion object {
        fun fromJson(o: JSONObject): Action {
            val p = mutableMapOf<String, String>()
            o.optJSONObject("params")?.let { po -> po.keys().forEach { k -> p[k] = po.optString(k) } }
            return Action(
                target = runCatching { TargetType.valueOf(o.getString("target")) }.getOrDefault(TargetType.HUE),
                deviceId = o.optString("deviceId", ""),
                command = o.optString("command", ""),
                params = p
            )
        }
    }
}

/** One decision branch: first branch whose conditions ALL match wins (if / else-if / else). */
data class Variant(
    val conditions: List<Cond> = emptyList(),   // empty = always matches ("Sonst")
    val actions: List<Action> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("conditions", JSONArray().also { a -> conditions.forEach { a.put(it.toJson()) } })
        .put("actions", JSONArray().also { a -> actions.forEach { a.put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject): Variant {
            val acts = mutableListOf<Action>()
            o.optJSONArray("actions")?.let { arr ->
                for (i in 0 until arr.length()) acts += Action.fromJson(arr.getJSONObject(i))
            }
            val conds = mutableListOf<Cond>()
            val arr = o.optJSONArray("conditions")
            if (arr != null) {
                for (i in 0 until arr.length()) conds += Cond.fromJson(arr.getJSONObject(i))
            } else when (o.optString("condition")) {   // migrate old routines
                "DAY" -> conds += Cond(CondType.DAY)
                "NIGHT" -> conds += Cond(CondType.NIGHT)
            }
            return Variant(conditions = conds, actions = acts)
        }
    }
}

data class Routine(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val triggers: List<Trigger> = listOf(Trigger()),
    val variants: List<Variant> = emptyList(),
    val icon: String = ""            // chosen emoji; empty = auto-suggest
) {
    /** Backward-compat: first trigger. Old call sites keep working. */
    val trigger: Trigger get() = triggers.firstOrNull() ?: Trigger()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("enabled", enabled)
        .put("triggers", JSONArray().also { a -> triggers.forEach { a.put(it.toJson()) } })
        .put("variants", JSONArray().also { a -> variants.forEach { a.put(it.toJson()) } })
        .put("icon", icon)

    companion object {
        fun fromJson(o: JSONObject): Routine {
            val vars = mutableListOf<Variant>()
            o.optJSONArray("variants")?.let { arr ->
                for (i in 0 until arr.length()) vars += Variant.fromJson(arr.getJSONObject(i))
            }
            val trigs = mutableListOf<Trigger>()
            o.optJSONArray("triggers")?.let { arr ->
                for (i in 0 until arr.length()) trigs += Trigger.fromJson(arr.getJSONObject(i))
            } ?: o.optJSONObject("trigger")?.let { trigs += Trigger.fromJson(it) }  // migrate old single trigger
            if (trigs.isEmpty()) trigs += Trigger()
            return Routine(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name", "Unbenannt"),
                enabled = o.optBoolean("enabled", true),
                triggers = trigs,
                variants = vars,
                icon = o.optString("icon", "")
            )
        }
    }
}

data class SonosSpeaker(val name: String, val ip: String)
data class LgTv(val name: String, val ip: String, val clientKey: String = "", val mac: String = "")
/** A user-defined HTTP endpoint: any webhook/URL device (Shelly, Tasmota, HA, IFTTT...). */
data class GenericDevice(val name: String, val url: String, val method: String = "GET", val body: String = "")
/** One entry in the run history. */
data class RunLog(val routineName: String, val timestamp: Long, val ok: Boolean, val detail: String = "")

data class Config(
    val hueBridgeIp: String = "",
    val hueAppKey: String = "",
    val sonos: List<SonosSpeaker> = emptyList(),
    val tvs: List<LgTv> = emptyList(),
    val anthropicKey: String = "",
    val model: String = "claude-sonnet-5",
    val dayStart: String = "07:00",
    val nightStart: String = "22:00",
    // TV bias lighting: content-type mood on selected lights while the TV runs
    val biasEnabled: Boolean = false,
    val biasTv: String = "",
    val biasLights: List<String> = emptyList(),
    val lightOrder: List<String> = emptyList(),  // custom drag&drop order of Hue lights
    val partnerIp: String = "",                  // partner phone IP for presence check
    val myPhoneIp: String = "",                  // your phone IP for leave-wifi detection
    val themeMode: ThemeMode = ThemeMode.SYSTEM, // appearance: follow system / light / dark
    val dynamicColor: Boolean = false,           // Material You wallpaper colors (Android 12+)
    val accentColor: String = "",                // custom accent hex (#RRGGBB); empty = default blue
    val generics: List<GenericDevice> = emptyList(), // user HTTP/webhook devices
    val latitude: Double = 52.52,                // for sunrise/sunset (default Berlin)
    val longitude: Double = 13.405,
    val webServerEnabled: Boolean = false,       // guest web-trigger server for iPhone access
    val shakeRoutineId: String = "",             // routine fired on hard phone shake ("" = off)
    val homeLat: Double = 0.0,                   // geofence home center (0 = not set)
    val homeLon: Double = 0.0,
    val geofenceRadius: Int = 150,               // meters
    val spotifyClientId: String = "",            // user's own Spotify dev app
    val spotifyRefresh: String = "",             // OAuth refresh token ("" = not connected)
    val spotifyVerifier: String = ""             // transient PKCE verifier during login
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("hueBridgeIp", hueBridgeIp); put("hueAppKey", hueAppKey)
        put("sonos", JSONArray().also { a ->
            sonos.forEach { a.put(JSONObject().put("name", it.name).put("ip", it.ip)) }
        })
        put("tvs", JSONArray().also { a ->
            tvs.forEach {
                a.put(JSONObject().put("name", it.name).put("ip", it.ip)
                    .put("clientKey", it.clientKey).put("mac", it.mac))
            }
        })
        put("anthropicKey", anthropicKey); put("model", model)
        put("dayStart", dayStart); put("nightStart", nightStart)
        put("biasEnabled", biasEnabled); put("biasTv", biasTv)
        put("biasLights", JSONArray().also { a -> biasLights.forEach { a.put(it) } })
        put("lightOrder", JSONArray().also { a -> lightOrder.forEach { a.put(it) } })
        put("partnerIp", partnerIp); put("myPhoneIp", myPhoneIp)
        put("generics", JSONArray().also { a ->
            generics.forEach { a.put(JSONObject().put("name", it.name).put("url", it.url).put("method", it.method).put("body", it.body)) }
        })
        put("latitude", latitude); put("longitude", longitude); put("webServerEnabled", webServerEnabled); put("shakeRoutineId", shakeRoutineId)
        put("homeLat", homeLat); put("homeLon", homeLon); put("geofenceRadius", geofenceRadius)
        put("spotifyClientId", spotifyClientId); put("spotifyRefresh", spotifyRefresh); put("spotifyVerifier", spotifyVerifier)
        put("themeMode", themeMode.name); put("dynamicColor", dynamicColor); put("accentColor", accentColor)
    }

    companion object {
        fun fromJson(o: JSONObject): Config {
            val sp = mutableListOf<SonosSpeaker>()
            o.optJSONArray("sonos")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i)
                    sp += SonosSpeaker(s.optString("name"), s.optString("ip"))
                }
            }
            val tvs = mutableListOf<LgTv>()
            o.optJSONArray("tvs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val t = arr.getJSONObject(i)
                    tvs += LgTv(t.optString("name"), t.optString("ip"),
                        t.optString("clientKey"), t.optString("mac"))
                }
            }
            return Config(
                hueBridgeIp = o.optString("hueBridgeIp"),
                hueAppKey = o.optString("hueAppKey"),
                sonos = sp, tvs = tvs,
                anthropicKey = o.optString("anthropicKey"),
                model = o.optString("model", "claude-sonnet-5"),
                dayStart = o.optString("dayStart", "07:00"),
                nightStart = o.optString("nightStart", "22:00"),
                biasEnabled = o.optBoolean("biasEnabled", false),
                biasTv = o.optString("biasTv", ""),
                biasLights = o.optJSONArray("biasLights")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                lightOrder = o.optJSONArray("lightOrder")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                partnerIp = o.optString("partnerIp", ""),
                myPhoneIp = o.optString("myPhoneIp", ""),
                themeMode = runCatching { ThemeMode.valueOf(o.optString("themeMode", "SYSTEM")) }.getOrDefault(ThemeMode.SYSTEM),
                dynamicColor = o.optBoolean("dynamicColor", false),
                accentColor = o.optString("accentColor", ""),
                generics = o.optJSONArray("generics")?.let { arr ->
                    (0 until arr.length()).map {
                        val g = arr.getJSONObject(it)
                        GenericDevice(g.optString("name"), g.optString("url"), g.optString("method", "GET"), g.optString("body"))
                    }
                } ?: emptyList(),
                latitude = o.optDouble("latitude", 52.52),
                longitude = o.optDouble("longitude", 13.405),
                webServerEnabled = o.optBoolean("webServerEnabled", false),
                shakeRoutineId = o.optString("shakeRoutineId", ""),
                homeLat = o.optDouble("homeLat", 0.0),
                homeLon = o.optDouble("homeLon", 0.0),
                geofenceRadius = o.optInt("geofenceRadius", 150),
                spotifyClientId = o.optString("spotifyClientId", ""),
                spotifyRefresh = o.optString("spotifyRefresh", ""),
                spotifyVerifier = o.optString("spotifyVerifier", "")
            )
        }
    }
}
