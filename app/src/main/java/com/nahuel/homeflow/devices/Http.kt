package com.nahuel.homeflow.devices

import okhttp3.OkHttpClient
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Shared OkHttp clients.
 * `local` accepts self-signed certificates - required because the Hue bridge and LG TVs use
 * them on the LAN. Its hostname verifier only lets that through for private/LAN hosts, so a
 * misrouted request to a public host fails instead of silently skipping TLS checks.
 * `internet` does normal certificate validation. Use [forUrl] when the host is user-supplied.
 */
object Http {
    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    val local: OkHttpClient by lazy {
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), java.security.SecureRandom()) }
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .sslSocketFactory(ssl.socketFactory, trustAll)
            .hostnameVerifier { host, _ -> isPrivateHost(host) }
            .build()
    }

    /** No read timeout - used for the Hue SSE event stream. */
    val localStream: OkHttpClient by lazy {
        local.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    }

    val internet: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    /** Lenient client for LAN hosts, strict one for everything else. */
    fun forUrl(url: String): OkHttpClient {
        val host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
        return if (isPrivateHost(host)) local else internet
    }

    /**
     * LAN / VPN hosts, decided without DNS: private IPv4 ranges, Tailscale's 100.64/10,
     * link-local, loopback, IPv6 ULA/link-local, and local-only names (.local, .lan, .ts.net, ...).
     */
    fun isPrivateHost(rawHost: String): Boolean {
        val host = rawHost.trim().removePrefix("[").removeSuffix("]").lowercase()
        if (host.isEmpty()) return false
        val parts = host.split(".")
        if (parts.size == 4 && parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }) {
            val (a, b) = parts.take(2).map { it.toInt() }
            return a == 10 || a == 127 ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) ||
                (a == 169 && b == 254) ||
                (a == 100 && b in 64..127)
        }
        if (':' in host) {
            return host == "::1" || host.startsWith("fe80") || host.startsWith("fc") || host.startsWith("fd")
        }
        if ('.' !in host) return true   // bare hostname, e.g. "hue-bridge"
        return listOf(".local", ".lan", ".home", ".internal", ".home.arpa", ".ts.net", ".fritz.box")
            .any { host.endsWith(it) }
    }
}
