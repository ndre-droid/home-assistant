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
import java.net.SocketTimeoutException

/**
 * Sonos local control via UPnP/SOAP on port 1400. Latency on LAN: ~100-300 ms.
 * No cloud account needed; works remotely through Tailscale like everything else.
 */
object SonosClient {
    private val xml = "text/xml; charset=\"utf-8\"".toMediaType()

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
            Http.local.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                check(resp.isSuccessful) { "Sonos HTTP ${resp.code}: ${body.take(200)}" }
                body
            }
        }

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

    /** Sets a stream/file URI (http(s), or x-rincon-mp3radio:// for radio) and starts playback. */
    suspend fun playUri(ip: String, uri: String, volume: Int?): Result<Unit> = runCatching {
        volume?.let { setVolume(ip, it).getOrThrow() }
        val esc = uri.replace("&", "&amp;").replace("<", "&lt;")
        soap(
            ip, "AVTransport", "SetAVTransportURI",
            "<InstanceID>0</InstanceID><CurrentURI>$esc</CurrentURI><CurrentURIMetaData></CurrentURIMetaData>"
        )
        soap(ip, "AVTransport", "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>"); Unit
    }

    /** SSDP discovery: finds all Sonos players in the network within ~3 s. */
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
