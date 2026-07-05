package com.nahuel.homeflow.devices

import com.nahuel.homeflow.data.SonosSpeaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

/**
 * Sonos local control via UPnP/SOAP on port 1400. Latency on LAN: ~100-300 ms.
 * No cloud account needed. Night mode / dialog level are plain EQ calls and work
 * on home-theater players (Beam, Arc); other players ignore them with an error.
 */
object SonosClient {
    private val xml = "text/xml; charset=\"utf-8\"".toMediaType()

    // Sonos players occasionally hold the first connect (power save) - be patient + retry once.
    private val client by lazy {
        Http.local.newBuilder().connectTimeout(8, TimeUnit.SECONDS).build()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun unesc(s: String) = s.replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    private suspend fun soap(ip: String, service: String, action: String, args: String): String =
        withContext(Dispatchers.IO) {
            val envelope = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body><u:$action xmlns:u="urn:schemas-upnp-org:service:$service:1">$args</u:$action></s:Body>
</s:Envelope>"""
            val req = Request.Builder()
                .url("http://$ip:1400/MediaRenderer/$service/Control")
                .header("SOAPACTION", "\"urn:schemas-upnp-org:service:$service:1#$action\"")
                .post(envelope.toRequestBody(xml))
                .build()
            suspend fun once(): String = client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                check(resp.isSuccessful) { "Sonos HTTP ${resp.code}: ${body.take(200)}" }
                body
            }
            try {
                once()
            } catch (e: IOException) {
                delay(400)                      // one retry: player may just be waking up
                try { once() } catch (e2: IOException) {
                    throw IllegalStateException(
                        "Sonos $ip nicht erreichbar. Handy im Heim-WLAN (nicht Mobilfunk/Gäste-WLAN)? " +
                        "Falls unterwegs: Tailscale-VPN aktiv?", e2
                    )
                }
            }
        }

    // ---------- Playback ----------

    suspend fun play(ip: String): Result<Unit> = runCatching {
        soap(ip, "AVTransport", "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>"); Unit
    }

    suspend fun pause(ip: String): Result<Unit> = runCatching {
        soap(ip, "AVTransport", "Pause", "<InstanceID>0</InstanceID>"); Unit
    }

    suspend fun stop(ip: String): Result<Unit> = runCatching {
        soap(ip, "AVTransport", "Stop", "<InstanceID>0</InstanceID>"); Unit
    }

    suspend fun setVolume(ip: String, volume: Int): Result<Unit> = runCatching {
        soap(
            ip, "RenderingControl", "SetVolume",
            "<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>${volume.coerceIn(0, 100)}</DesiredVolume>"
        ); Unit
    }

    /** Sets a stream/file URI and starts playback. meta = plain DIDL-Lite XML (optional, from capture). */
    suspend fun playUri(ip: String, uri: String, volume: Int?, meta: String = ""): Result<Unit> = runCatching {
        volume?.let { setVolume(ip, it).getOrThrow() }
        soap(
            ip, "AVTransport", "SetAVTransportURI",
            "<InstanceID>0</InstanceID><CurrentURI>${esc(uri)}</CurrentURI><CurrentURIMetaData>${esc(meta)}</CurrentURIMetaData>"
        )
        soap(ip, "AVTransport", "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>"); Unit
    }

    // ---------- EQ (Beam/Arc home-theater features, local API) ----------

    /** Night mode: compresses dynamics for quiet evenings. */
    suspend fun setNightMode(ip: String, on: Boolean): Result<Unit> = runCatching {
        soap(
            ip, "RenderingControl", "SetEQ",
            "<InstanceID>0</InstanceID><EQType>NightMode</EQType><DesiredValue>${if (on) 1 else 0}</DesiredValue>"
        ); Unit
    }

    /** Speech enhancement ("Sprachverbesserung"). */
    suspend fun setDialogLevel(ip: String, on: Boolean): Result<Unit> = runCatching {
        soap(
            ip, "RenderingControl", "SetEQ",
            "<InstanceID>0</InstanceID><EQType>DialogLevel</EQType><DesiredValue>${if (on) 1 else 0}</DesiredValue>"
        ); Unit
    }

    // ---------- State readout (for scene capture) ----------

    suspend fun getVolume(ip: String): Result<Int> = runCatching {
        val r = soap(ip, "RenderingControl", "GetVolume", "<InstanceID>0</InstanceID><Channel>Master</Channel>")
        Regex("<CurrentVolume>(\\d+)</CurrentVolume>").find(r)?.groupValues?.get(1)?.toInt()
            ?: error("Volume nicht lesbar")
    }

    suspend fun isPlaying(ip: String): Boolean = runCatching {
        val r = soap(ip, "AVTransport", "GetTransportInfo", "<InstanceID>0</InstanceID>")
        Regex("<CurrentTransportState>([^<]+)</CurrentTransportState>").find(r)?.groupValues?.get(1) == "PLAYING"
    }.getOrDefault(false)

    /** Returns (uri, didlMeta) of the current source, both unescaped to plain form. */
    suspend fun getMedia(ip: String): Result<Pair<String, String>> = runCatching {
        val r = soap(ip, "AVTransport", "GetMediaInfo", "<InstanceID>0</InstanceID>")
        val uri = Regex("<CurrentURI>(.*?)</CurrentURI>", RegexOption.DOT_MATCHES_ALL)
            .find(r)?.groupValues?.get(1) ?: ""
        val meta = Regex("<CurrentURIMetaData>(.*?)</CurrentURIMetaData>", RegexOption.DOT_MATCHES_ALL)
            .find(r)?.groupValues?.get(1) ?: ""
        unesc(uri) to (if (meta == "NOT_IMPLEMENTED") "" else unesc(meta))
    }

    // ---------- Discovery ----------

    suspend fun discover(): List<SonosSpeaker> = withContext(Dispatchers.IO) {
        val found = mutableMapOf<String, SonosSpeaker>()
        runCatching {
            val msg = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\nST: urn:schemas-upnp-org:device:ZonePlayer:1\r\n\r\n"
            DatagramSocket().use { socket ->
                socket.soTimeout = 3000
                val data = msg.toByteArray()
                socket.send(DatagramPacket(data, data.size, InetAddress.getByName("239.255.255.250"), 1900))
                val buf = ByteArray(2048)
                while (true) {
                    val packet = DatagramPacket(buf, buf.size)
                    try { socket.receive(packet) } catch (e: SocketTimeoutException) { break }
                    val ip = packet.address.hostAddress ?: continue
                    if (ip !in found) {
                        val name = fetchRoomName(ip) ?: "Sonos $ip"
                        found[ip] = SonosSpeaker(name, ip)
                    }
                }
            }
        }
        found.values.toList()
    }

    private fun fetchRoomName(ip: String): String? = runCatching {
        val req = Request.Builder().url("http://$ip:1400/xml/device_description.xml").get().build()
        Http.local.newCall(req).execute().use { resp ->
            Regex("<roomName>([^<]*)</roomName>").find(resp.body?.string() ?: "")?.groupValues?.get(1)
        }
    }.getOrNull()
}
