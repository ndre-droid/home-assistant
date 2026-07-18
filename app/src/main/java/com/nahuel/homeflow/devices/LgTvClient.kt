package com.nahuel.homeflow.devices

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import java.net.Socket
import java.net.InetSocketAddress
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * LG webOS TV: WebSocket SSAP protocol (wss on 3001, self-signed cert).
 * First connection shows a pairing prompt on the TV; the returned client-key is stored.
 * Power ON is only possible via Wake-on-LAN and requires the TV setting
 * "Einschalten über WLAN / LG Connect Apps" to be active — and even then it's the
 * one command that is NOT instant (TV boot takes a few seconds).
 */
object LgTvClient {

    private val permissions = JSONArray(
        listOf(
            "LAUNCH", "LAUNCH_WEBAPP", "CONTROL_AUDIO", "CONTROL_POWER", "CONTROL_DISPLAY",
            "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TV", "READ_INSTALLED_APPS",
            "READ_INPUT_DEVICE_LIST", "READ_NETWORK_STATE", "READ_TV_CHANNEL_LIST",
            "READ_CURRENT_CHANNEL", "READ_RUNNING_APPS", "WRITE_NOTIFICATION_TOAST",
            "CONTROL_MOUSE_AND_KEYBOARD", "CONTROL_INPUT_TEXT", "CONTROL_INPUT_JOYSTICK"
        )
    )

    private fun registerPayload(clientKey: String?): JSONObject {
        val payload = JSONObject()
            .put("forcePairing", false)
            .put("pairingType", "PROMPT")
            .put("manifest", JSONObject().put("manifestVersion", 1).put("permissions", permissions))
        clientKey?.takeIf { it.isNotEmpty() }?.let { payload.put("client-key", it) }
        return JSONObject().put("type", "register").put("id", "reg0").put("payload", payload)
    }

    /**
     * Opens a socket, registers (pairing prompt if no key yet), optionally sends one request,
     * returns Pair(clientKey, responsePayload). Timeout: 30 s to leave time to accept the prompt.
     */
    private suspend fun session(
        ip: String, clientKey: String?, uri: String?, payload: JSONObject?
    ): Pair<String, JSONObject?> = withContext(Dispatchers.IO) {
        val keyResult = CompletableDeferred<String>()
        val cmdResult = CompletableDeferred<JSONObject?>()
        var ws: WebSocket? = null
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(registerPayload(clientKey).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (msg.optString("type")) {
                    "registered" -> {
                        val key = msg.optJSONObject("payload")?.optString("client-key") ?: (clientKey ?: "")
                        keyResult.complete(key)
                        if (uri == null) cmdResult.complete(null)
                        else webSocket.send(
                            JSONObject().put("id", "cmd0").put("type", "request").put("uri", uri)
                                .apply { payload?.let { put("payload", it) } }.toString()
                        )
                    }
                    "response" -> if (msg.optString("id") == "cmd0") cmdResult.complete(msg.optJSONObject("payload"))
                    "error" -> {
                        val err = IllegalStateException("TV: ${msg.optString("error")}")
                        if (!keyResult.isCompleted) keyResult.completeExceptionally(err)
                        if (!cmdResult.isCompleted) cmdResult.completeExceptionally(err)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!keyResult.isCompleted) keyResult.completeExceptionally(t)
                if (!cmdResult.isCompleted) cmdResult.completeExceptionally(t)
            }
        }
        try {
            ws = Http.local.newWebSocket(Request.Builder().url("wss://$ip:3001").build(), listener)
            withTimeout(30_000) {
                val key = keyResult.await()
                val resp = cmdResult.await()
                key to resp
            }
        } finally {
            ws?.close(1000, null)
        }
    }

    /** Pair with the TV (accept the prompt on screen). Returns the client-key to store. */
    suspend fun pair(ip: String): Result<String> = runCatching { session(ip, null, null, null).first }

    suspend fun powerOff(ip: String, key: String): Result<Unit> =
        runCatching { session(ip, key, "ssap://system/turnOff", null); Unit }

    suspend fun setVolume(ip: String, key: String, volume: Int): Result<Unit> = runCatching {
        session(ip, key, "ssap://audio/setVolume", JSONObject().put("volume", volume.coerceIn(0, 100))); Unit
    }

    suspend fun setMute(ip: String, key: String, mute: Boolean): Result<Unit> = runCatching {
        session(ip, key, "ssap://audio/setMute", JSONObject().put("mute", mute)); Unit
    }

    /** Opens an app on the TV; contentId deep-links into it (YouTube video id, Netflix title id). */
    suspend fun launchApp(ip: String, key: String, appId: String, contentId: String?): Result<Unit> = runCatching {
        require(appId.isNotBlank()) { "Keine App gewählt" }
        val payload = JSONObject().put("id", appId)
        contentId?.takeIf { it.isNotBlank() }?.let { payload.put("contentId", it) }
        // Up to 4 attempts: right after a cold boot the launcher service lags behind the socket.
        var last: Throwable? = null
        repeat(4) { attempt ->
            val r = runCatching { session(ip, key, "ssap://system.launcher/launch", payload) }
            if (r.isSuccess) return@runCatching
            last = r.exceptionOrNull()
            delay(1200L + attempt * 800L)
        }
        throw IllegalStateException("App-Start fehlgeschlagen: ${last?.message}")
    }

    /**
     * TRUE readiness: polls getForegroundAppInfo (the launcher itself), not just the socket.
     * If the TV is off and a MAC is known, sends WoL first. Waits up to [maxWaitMs].
     */
    suspend fun ensureAwake(ip: String, key: String, mac: String, maxWaitMs: Long = 45_000): Result<Unit> {
        // Fire WoL FIRST (harmless if the TV is already on) - saves the initial probe round-trip.
        if (mac.isNotBlank()) powerOn(mac)
        if (getForegroundApp(ip, key).isSuccess) return Result.success(Unit)   // already awake
        if (mac.isBlank()) return Result.failure(IllegalStateException("TV ist aus und keine MAC-Adresse hinterlegt (Geräte-Tab)"))
        val deadline = System.currentTimeMillis() + maxWaitMs
        var i = 0
        while (System.currentTimeMillis() < deadline) {
            delay(1000)                       // tight polling: react the moment SSAP answers
            if (i++ % 3 == 2) powerOn(mac)    // re-send WoL occasionally (packets get lost)
            if (getForegroundApp(ip, key).isSuccess) {
                delay(500)                    // brief settle, launcher is proven alive already
                return Result.success(Unit)
            }
        }
        return Result.failure(IllegalStateException("TV nicht aufgewacht (45 s). Netzwerk-Standby am TV aktiv?"))
    }

    /** One command = wake TV if needed, then open the app (Netflix, YouTube-Video, ...). */
    suspend fun openApp(ip: String, key: String, mac: String, appId: String, contentId: String?): Result<Unit> {
        ensureAwake(ip, key, mac).onFailure { return Result.failure(it) }
        return launchApp(ip, key, appId, contentId)
    }

    /** Foreground app id (netflix, youtube.leanback.v4, com.webos.app.hdmi2, ...). Fails fast if TV is off. */
    suspend fun getForegroundApp(ip: String, key: String): Result<String> = runCatching {
        val (_, payload) = session(ip, key, "ssap://com.webos.applicationManager/getForegroundAppInfo", null)
        payload?.optString("appId").orEmpty().ifEmpty { error("Keine App-Info") }
    }

    /** Wake-on-LAN magic packet. Requires MAC address + TV network standby enabled. */
    suspend fun powerOn(mac: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanMac = mac.replace(":", "").replace("-", "")
            require(cleanMac.length == 12) { "Ungültige MAC-Adresse" }
            val macBytes = ByteArray(6) { i -> cleanMac.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
            val packet = ByteArray(102)
            for (i in 0 until 6) packet[i] = 0xFF.toByte()
            for (i in 1..16) System.arraycopy(macBytes, 0, packet, i * 6, 6)
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName("255.255.255.255"), 9))
            }
        }
    }

    /**
     * Wake-on-LAN, then wait until the TV actually accepts connections (webOS needs
     * ~10-30s to boot). Polls the SSAP port so the next action only runs once the TV
     * is really awake. Returns success as soon as it's reachable, or after maxWaitMs.
     */
    suspend fun powerOnAndWait(mac: String, ip: String, maxWaitMs: Long = 35_000): Result<Unit> =
        withContext(Dispatchers.IO) {
            val wol = powerOn(mac)
            if (wol.isFailure) return@withContext wol
            val deadline = System.currentTimeMillis() + maxWaitMs
            while (System.currentTimeMillis() < deadline) {
                val reachable = runCatching {
                    Socket().use { it.connect(InetSocketAddress(ip, 3001), 1500) }
                    true
                }.getOrElse {
                    runCatching {
                        Socket().use { it.connect(InetSocketAddress(ip, 3000), 1500) }
                        true
                    }.getOrDefault(false)
                }
                if (reachable) {
                    delay(1500) // small settle so SSAP is ready to register, not just the socket
                    return@withContext Result.success(Unit)
                }
                delay(1500)
            }
            // Reachability never confirmed; still report success so the WoL itself isn't
            // treated as an error - the TV may just be slow, and the next action can retry.
            Result.success(Unit)
        }
}
