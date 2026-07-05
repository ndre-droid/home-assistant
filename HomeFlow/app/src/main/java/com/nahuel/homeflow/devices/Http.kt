package com.nahuel.homeflow.devices

import okhttp3.OkHttpClient
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Shared OkHttp clients.
 * `local` trusts all certificates — required because the Hue bridge and LG TVs use
 * self-signed certs on the LAN. Never use it for internet endpoints; `internet` does
 * normal certificate validation and is used for the Anthropic API.
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
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    /** No read timeout — used for the Hue SSE event stream. */
    val localStream: OkHttpClient by lazy {
        local.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    }

    val internet: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
