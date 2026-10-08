package com.nahuel.homeflow.devices

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * Last-resort discovery: TCP-probe every host of the phone's own WiFi /24 on the given ports.
 * Works where multicast (SSDP/mDNS) is filtered by the router or dropped by newer firmware.
 */
object LanSweep {
    private const val CONNECT_TIMEOUT_MS = 350
    private const val PARALLEL = 48

    /** port -> IPs that accepted a connection. Empty when not on WiFi/LAN. */
    suspend fun openPorts(ports: Set<Int>): Map<Int, List<String>> = coroutineScope {
        if (ports.isEmpty()) return@coroutineScope emptyMap()
        val own = localIpv4s()
        val hosts = own.map { it.substringBeforeLast('.') }.distinct()
            .flatMap { prefix -> (1..254).map { "$prefix.$it" } }
            .filter { it !in own }
        val gate = Semaphore(PARALLEL)
        hosts.flatMap { ip -> ports.map { port -> ip to port } }
            .map { (ip, port) ->
                async(Dispatchers.IO) { gate.withPermit { if (isOpen(ip, port)) ip to port else null } }
            }
            .awaitAll().filterNotNull()
            .groupBy({ it.second }, { it.first })
    }

    private fun isOpen(ip: String, port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS) }
        true
    }.getOrDefault(false)

    /** Private IPv4s of WiFi/Ethernet interfaces only - never mobile data (rmnet) or VPN (tun). */
    private fun localIpv4s(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && (it.name.startsWith("wlan") || it.name.startsWith("eth")) }
            .flatMap { it.interfaceAddresses }
            .mapNotNull { (it.address as? Inet4Address)?.takeIf { a -> a.isSiteLocalAddress }?.hostAddress }
            .distinct()
    }.getOrDefault(emptyList())
}
