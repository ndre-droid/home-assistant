package com.nahuel.homeflow.devices

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** One SSDP answer: sender IP plus its headers (keys lower-cased). */
data class SsdpHit(val ip: String, val headers: Map<String, String>)

/** UPnP discovery: multicast M-SEARCH, collect the unicast replies until [timeoutMs] of silence. */
object Ssdp {
    suspend fun search(st: String, timeoutMs: Int = 3000): List<SsdpHit> = withContext(Dispatchers.IO) {
        val hits = mutableListOf<SsdpHit>()
        runCatching {
            val msg = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\nST: $st\r\n\r\n"
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val data = msg.toByteArray()
                val group = InetAddress.getByName("239.255.255.250")
                // UDP gets lost on busy WiFi: send twice.
                repeat(2) { socket.send(DatagramPacket(data, data.size, group, 1900)) }
                val buf = ByteArray(2048)
                while (true) {
                    val packet = DatagramPacket(buf, buf.size)
                    try { socket.receive(packet) } catch (e: SocketTimeoutException) { break }
                    val ip = packet.address.hostAddress ?: continue
                    val headers = String(packet.data, 0, packet.length, Charsets.UTF_8).lines().drop(1)
                        .mapNotNull { line ->
                            val i = line.indexOf(':')
                            if (i <= 0) null else line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
                        }.toMap()
                    hits += SsdpHit(ip, headers)
                }
            }
        }
        hits
    }

    /** "uuid:RINCON_123::urn:…" -> "RINCON_123". */
    fun uuidOf(usn: String?): String =
        usn?.removePrefix("uuid:")?.substringBefore("::")?.trim().orEmpty()
}
