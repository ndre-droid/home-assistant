package com.nahuel.homeflow.devices

import com.nahuel.homeflow.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject

data class HueLight(
    val id: String, val name: String, val on: Boolean, val supportsColor: Boolean,
    val brightness: Int = 100,          // 1..100
    val colorHex: String? = null        // current color, if the light has one
)

/** Philips Hue bridge, local CLIP v2 API. Latency on LAN: typically < 100 ms. */
/** Remembers which lights WE just commanded, so the event stream can tell
 *  our own echoes apart from real user actions (no self-retriggering loops). */
object HueEcho {
    private val m = java.util.concurrent.ConcurrentHashMap<String, Long>()
    fun mark(id: String) { m[id] = System.currentTimeMillis() }
    fun recent(id: String, windowMs: Long = 2500): Boolean =
        System.currentTimeMillis() - (m[id] ?: 0L) < windowMs
}

object HueClient {
    private val json = "application/json".toMediaType()

    private fun ip() = Store.config.value.hueBridgeIp
    private fun key() = Store.config.value.hueAppKey

    private fun v2(path: String) = Request.Builder()
        .url("https://${ip()}/clip/v2/$path")
        .header("hue-application-key", key())

    /** Press the bridge link button first, then call this. Returns the app key. */
    suspend fun pair(bridgeIp: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("devicetype", "homeflow#android").toString().toRequestBody(json)
            val req = Request.Builder().url("http://$bridgeIp/api").post(body).build()
            Http.local.newCall(req).execute().use { resp ->
                val arr = JSONArray(resp.body!!.string())
                val first = arr.getJSONObject(0)
                first.optJSONObject("success")?.optString("username")?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalStateException(
                        first.optJSONObject("error")?.optString("description") ?: "Pairing fehlgeschlagen"
                    )
            }
        }
    }

    suspend fun lights(): Result<List<HueLight>> = withContext(Dispatchers.IO) {
        runCatching {
            Http.local.newCall(v2("resource/light").get().build()).execute().use { resp ->
                check(resp.isSuccessful) { "Bridge HTTP ${resp.code}" }
                val data = JSONObject(resp.body!!.string()).getJSONArray("data")
                (0 until data.length()).map { i ->
                    val l = data.getJSONObject(i)
                    val xy = l.optJSONObject("color")?.optJSONObject("xy")
                    HueLight(
                        id = l.getString("id"),
                        name = l.optJSONObject("metadata")?.optString("name") ?: "Lampe",
                        on = l.optJSONObject("on")?.optBoolean("on") ?: false,
                        supportsColor = l.has("color"),
                        brightness = (l.optJSONObject("dimming")?.optDouble("brightness", 100.0) ?: 100.0).toInt().coerceIn(1, 100),
                        colorHex = xy?.let { c -> xyToHex(c.optDouble("x", 0.3127), c.optDouble("y", 0.3290)) }
                    )
                }
            }
        }
    }

    /** Any of the params may be null = leave unchanged. deviceId "all" fans out to every light. */
    suspend fun setLight(id: String, on: Boolean?, brightness: Int?, colorHex: String?, exclude: List<String> = emptyList()): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val targets = (if (id == "all") lights().getOrThrow().map { it.id } else listOf(id))
                    .filter { it !in exclude }
                val body = JSONObject().apply {
                    on?.let { put("on", JSONObject().put("on", it)) }
                    brightness?.let {
                        put("dimming", JSONObject().put("brightness", it.coerceIn(1, 100).toDouble()))
                    }
                    colorHex?.let {
                        val (x, y) = hexToXY(it)
                        put("color", JSONObject().put("xy", JSONObject().put("x", x).put("y", y)))
                    }
                }.toString().toRequestBody(json)
                targets.forEach { t ->
                    HueEcho.mark(t)
                    Http.local.newCall(v2("resource/light/$t").put(body).build()).execute().use { resp ->
                        check(resp.isSuccessful) { "Hue HTTP ${resp.code}" }
                    }
                }
            }
        }

    /** Server-sent events; onLightEvent fires on every on/off change. Blocking until cancelled. */
    fun openEventStream(onLightEvent: (lightId: String, on: Boolean) -> Unit, onFailure: () -> Unit): EventSource {
        val req = v2("").url("https://${ip()}/eventstream/clip/v2")
            .header("Accept", "text/event-stream").build()
        return EventSources.createFactory(Http.localStream).newEventSource(req, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                runCatching {
                    val arr = JSONArray(data)
                    for (i in 0 until arr.length()) {
                        val update = arr.getJSONObject(i)
                        if (update.optString("type") != "update") continue
                        val items = update.optJSONArray("data") ?: continue
                        for (j in 0 until items.length()) {
                            val item = items.getJSONObject(j)
                            if (item.optString("type") == "light" && item.has("on")) {
                                onLightEvent(item.getString("id"), item.getJSONObject("on").getBoolean("on"))
                            }
                        }
                    }
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                onFailure()
            }
        })
    }

    /** sRGB hex -> CIE 1931 xy (standard Philips conversion incl. gamma correction). */
    fun hexToXY(hex: String): Pair<Double, Double> {
        val clean = hex.removePrefix("#")
        val r = Integer.parseInt(clean.substring(0, 2), 16) / 255.0
        val g = Integer.parseInt(clean.substring(2, 4), 16) / 255.0
        val b = Integer.parseInt(clean.substring(4, 6), 16) / 255.0
        fun gamma(c: Double) = if (c > 0.04045) Math.pow((c + 0.055) / 1.055, 2.4) else c / 12.92
        val rl = gamma(r); val gl = gamma(g); val bl = gamma(b)
        val x = rl * 0.4124 + gl * 0.3576 + bl * 0.1805
        val y = rl * 0.2126 + gl * 0.7152 + bl * 0.0722
        val z = rl * 0.0193 + gl * 0.1192 + bl * 0.9505
        val sum = x + y + z
        return if (sum == 0.0) 0.3127 to 0.3290 else (x / sum) to (y / sum)
    }

    /** CIE xy -> sRGB hex (inverse of hexToXY, brightness-normalized). Used for scene capture. */
    fun xyToHex(x: Double, y: Double): String {
        if (y <= 0.0) return "#FFFFFF"
        val yy = 1.0
        val xx = (yy / y) * x
        val zz = (yy / y) * (1.0 - x - y)
        var r = xx * 3.2406 + yy * -1.5372 + zz * -0.4986
        var g = xx * -0.9689 + yy * 1.8758 + zz * 0.0415
        var b = xx * 0.0557 + yy * -0.2040 + zz * 1.0570
        val max = maxOf(r, g, b)
        if (max > 0) { r /= max; g /= max; b /= max }
        fun deGamma(c: Double): Double {
            val v = c.coerceIn(0.0, 1.0)
            return if (v <= 0.0031308) 12.92 * v else 1.055 * Math.pow(v, 1.0 / 2.4) - 0.055
        }
        fun hex(c: Double) = String.format("%02X", (deGamma(c) * 255).toInt().coerceIn(0, 255))
        return "#" + hex(r) + hex(g) + hex(b)
    }
}
