package com.nahuel.homeflow.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** How a routine is started. NFC and widget both end up calling RoutineEngine directly;
 *  DEVICE_STATE is watched by the foreground TriggerService via the Hue event stream. */
enum class TriggerType { MANUAL, NFC, DEVICE_STATE }

/** Time condition for a variant. DAY/NIGHT boundaries come from Config. */
enum class ConditionType { ALWAYS, DAY, NIGHT }

enum class TargetType { HUE, SONOS, LG_TV }

data class Trigger(
    val type: TriggerType = TriggerType.MANUAL,
    val hueLightId: String = "",
    val toState: Boolean = true // fire when light turns on (true) or off (false)
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", type.name).put("hueLightId", hueLightId).put("toState", toState)

    companion object {
        fun fromJson(o: JSONObject) = Trigger(
            type = runCatching { TriggerType.valueOf(o.optString("type", "MANUAL")) }.getOrDefault(TriggerType.MANUAL),
            hueLightId = o.optString("hueLightId", ""),
            toState = o.optBoolean("toState", true)
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

data class Variant(
    val condition: ConditionType = ConditionType.ALWAYS,
    val actions: List<Action> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("condition", condition.name)
        .put("actions", JSONArray().also { a -> actions.forEach { a.put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject): Variant {
            val acts = mutableListOf<Action>()
            o.optJSONArray("actions")?.let { arr ->
                for (i in 0 until arr.length()) acts += Action.fromJson(arr.getJSONObject(i))
            }
            return Variant(
                condition = runCatching { ConditionType.valueOf(o.optString("condition", "ALWAYS")) }
                    .getOrDefault(ConditionType.ALWAYS),
                actions = acts
            )
        }
    }
}

data class Routine(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val trigger: Trigger = Trigger(),
    val variants: List<Variant> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("enabled", enabled)
        .put("trigger", trigger.toJson())
        .put("variants", JSONArray().also { a -> variants.forEach { a.put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject): Routine {
            val vars = mutableListOf<Variant>()
            o.optJSONArray("variants")?.let { arr ->
                for (i in 0 until arr.length()) vars += Variant.fromJson(arr.getJSONObject(i))
            }
            return Routine(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name", "Unbenannt"),
                enabled = o.optBoolean("enabled", true),
                trigger = o.optJSONObject("trigger")?.let { Trigger.fromJson(it) } ?: Trigger(),
                variants = vars
            )
        }
    }
}

data class SonosSpeaker(val name: String, val ip: String)
data class LgTv(val name: String, val ip: String, val clientKey: String = "", val mac: String = "")

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
    val biasLights: List<String> = emptyList()
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
                } ?: emptyList()
            )
        }
    }
}
