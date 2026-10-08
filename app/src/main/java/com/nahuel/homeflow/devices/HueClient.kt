package com.nahuel.homeflow.devices

import com.nahuel.homeflow.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val colorHex: String? = null,       // current color, if the light has one
    val ownerId: String = ""            // Hue device id that owns this light (room membership)
)

/** A Hue room: name plus the device ids it contains (lights link to it via ownerId). */
data class HueRoom(val id: String, val name: String, val deviceIds: Set<String>)

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

    // ---------- Discovery (finding the bridge again after an IP change) ----------

    /** A bridge on the LAN. [id] is the lower-case bridge id, e.g. "001788fffe123456". */
    data class Bridge(val id: String, val ip: String)

    /** SSDP first (local only); Philips' cloud lookup as fallback when multicast is blocked. */
    suspend fun discoverBridges(): List<Bridge> = withContext(Dispatchers.IO) {
        val local = Ssdp.search("upnp:rootdevice").mapNotNull { hit ->
            hit.headers["hue-bridgeid"]?.takeIf { it.isNotBlank() }?.let { Bridge(it.lowercase(), hit.ip) }
        }
        local.ifEmpty {
            runCatching {
                val req = Request.Builder().url("https://discovery.meethue.com/").get().build()
                Http.internet.newCall(req).execute().use { resp ->
                    val arr = JSONArray(resp.body!!.string())
                    (0 until arr.length()).map { arr.getJSONObject(it) }
                        .map { Bridge(it.optString("id").lowercase(), it.optString("internalipaddress")) }
                        // never point the app at a public host just because the cloud said so
                        .filter { it.id.isNotEmpty() && Http.isPrivateHost(it.ip) }
                }
            }.getOrDefault(emptyList())
        }.distinctBy { it.id }
    }

    /** Bridge id at [ip] if our app key is accepted there, else null. Proves "same bridge, still paired". */
    suspend fun verifiedBridgeId(ip: String, key: String): String? = withContext(Dispatchers.IO) {
        if (ip.isBlank() || key.isBlank()) return@withContext null
        runCatching {
            val req = Request.Builder().url("https://$ip/clip/v2/resource/bridge")
                .header("hue-application-key", key).get().build()
            Http.local.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                JSONObject(resp.body!!.string()).getJSONArray("data").getJSONObject(0)
                    .optString("bridge_id").lowercase().ifEmpty { null }
            }
        }.getOrNull()
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
                        colorHex = xy?.let { c -> xyToHex(c.optDouble("x", 0.3127), c.optDouble("y", 0.3290)) },
                        ownerId = l.optJSONObject("owner")?.optString("rid") ?: ""
                    )
                }
            }
        }
    }

    /** Rooms as configured in the Hue app. Children are device ids (lights point to them via owner). */
    suspend fun rooms(): Result<List<HueRoom>> = withContext(Dispatchers.IO) {
        runCatching {
            Http.local.newCall(v2("resource/room").get().build()).execute().use { resp ->
                check(resp.isSuccessful) { "Bridge HTTP ${resp.code}" }
                val data = JSONObject(resp.body!!.string()).getJSONArray("data")
                (0 until data.length()).map { i ->
                    val r = data.getJSONObject(i)
                    val kids = r.optJSONArray("children") ?: JSONArray()
                    HueRoom(
                        id = r.getString("id"),
                        name = r.optJSONObject("metadata")?.optString("name") ?: "Raum",
                        deviceIds = (0 until kids.length()).map { kids.getJSONObject(it).optString("rid") }.toSet()
                    )
                }
            }
        }
    }

    // The bridge drops commands beyond ~10/s for lights (1/s for groups). All light PUTs go
    // through one gate so party/strobe/fan-out never flood it.
    private const val MIN_GAP_MS = 100L
    private val sendGate = Mutex()
    @Volatile private var lastSend = 0L

    private suspend fun <T> throttled(block: () -> T): T = sendGate.withLock {
        val wait = lastSend + MIN_GAP_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try { block() } finally { lastSend = System.currentTimeMillis() }
    }

    /** grouped_light owned by bridge_home = "every light" in ONE command. Cached per bridge. */
    @Volatile private var allGroup: Pair<String, String>? = null   // bridge ip -> grouped_light id

    private fun allLightsGroupId(): String? {
        allGroup?.takeIf { it.first == ip() }?.let { return it.second }
        return runCatching {
            Http.local.newCall(v2("resource/grouped_light").get().build()).execute().use { resp ->
                check(resp.isSuccessful)
                val data = JSONObject(resp.body!!.string()).getJSONArray("data")
                (0 until data.length()).map { data.getJSONObject(it) }
                    .firstOrNull { it.optJSONObject("owner")?.optString("rtype") == "bridge_home" }
                    ?.getString("id")
            }
        }.getOrNull()?.also { allGroup = ip() to it }
    }

    /** Any of the params may be null = leave unchanged. deviceId "all" fans out to every light. */
    suspend fun setLight(id: String, on: Boolean?, brightness: Int?, colorHex: String?, exclude: List<String> = emptyList()): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val targets = (if (id == "all") lights().getOrThrow().map { it.id } else listOf(id))
                    .filter { it !in exclude }
                // Plain on/off/dim of every light: one group command instead of N light commands.
                if (id == "all" && exclude.isEmpty() && colorHex == null && (on != null || brightness != null)) {
                    val group = allLightsGroupId()
                    if (group != null) {
                        val gBody = JSONObject().apply {
                            on?.let { put("on", JSONObject().put("on", it)) }
                            brightness?.let { put("dimming", JSONObject().put("brightness", it.coerceIn(1, 100).toDouble())) }
                        }.toString().toRequestBody(json)
                        targets.forEach { HueEcho.mark(it) }
                        throttled {
                            Http.local.newCall(v2("resource/grouped_light/$group").put(gBody).build()).execute().use { resp ->
                                check(resp.isSuccessful) { "Hue HTTP ${resp.code}" }
                            }
                        }
                        return@runCatching
                    }
                }
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
                    throttled {
                        Http.local.newCall(v2("resource/light/$t").put(body).build()).execute().use { resp ->
                            check(resp.isSuccessful) { "Hue HTTP ${resp.code}" }
                        }
                    }
                }
            }
        }

    /** Server-sent events; onLightEvent fires on every on/off change. onFailure also fires on a clean close. */
    fun openEventStream(
        onLightEvent: (lightId: String, on: Boolean) -> Unit,
        onFailure: () -> Unit,
        onOpen: () -> Unit = {}
    ): EventSource {
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

            override fun onOpen(eventSource: EventSource, response: Response) {
                onOpen()
            }

            override fun onClosed(eventSource: EventSource) {
                onFailure()   // bridge closed the stream (e.g. reboot) - reconnect like on errors
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
