package com.nahuel.homeflow.engine

import com.nahuel.homeflow.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.ServerSocket
import java.net.URLDecoder

/**
 * Tiny dependency-free HTTP server so a guest (e.g. an iPhone on the same WiFi / Tailscale)
 * can trigger routines from a browser page. No app needed on their device.
 *
 * Routes:
 *   GET /            -> HTML page with a button per enabled routine
 *   GET /run?id=<id> -> runs that routine, returns "ok"
 */
object WebTriggerServer {
    const val PORT = 87 * 1000 + 82   // 8782
    @Volatile private var running = false
    private var job: Job? = null
    private var server: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun isRunning() = running

    /** Best-effort local IPv4 for building the guest URL. */
    fun localUrl(): String {
        val ip = runCatching {
            java.net.NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }?.hostAddress
        }.getOrNull() ?: "<phone-ip>"
        return "http://$ip:$PORT"
    }

    fun start(onRun: (String) -> Unit) {
        if (running) return
        running = true
        job = scope.launch {
            runCatching {
                server = ServerSocket(PORT)
                while (running) {
                    val socket = server?.accept() ?: break
                    launch {
                        runCatching {
                            socket.use { s ->
                                val reader = s.getInputStream().bufferedReader()
                                val line = reader.readLine() ?: return@use
                                // e.g. "GET /run?id=abc HTTP/1.1"
                                val path = line.split(" ").getOrNull(1) ?: "/"
                                val body: String
                                val ctype: String
                                if (path.startsWith("/run")) {
                                    val id = path.substringAfter("id=", "").substringBefore(" ").substringBefore("&")
                                        .let { URLDecoder.decode(it, "UTF-8") }
                                    if (id.isNotEmpty()) onRun(id)
                                    body = "ok"; ctype = "text/plain"
                                } else {
                                    body = pageHtml(); ctype = "text/html; charset=utf-8"
                                }
                                val out = s.getOutputStream()
                                val bytes = body.toByteArray(Charsets.UTF_8)
                                out.write(("HTTP/1.1 200 OK\r\nContent-Type: $ctype\r\n" +
                                        "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
                                out.write(bytes); out.flush()
                            }
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        job?.cancel()
    }

    private fun pageHtml(): String {
        val routines = Store.routines.value.filter { it.enabled }
        val buttons = routines.joinToString("\n") { r ->
            val safe = r.name.replace("<", "&lt;").replace(">", "&gt;")
            """<button onclick="fetch('/run?id=${r.id}').then(()=>flash(this))">$safe</button>"""
        }
        return """<!doctype html><html><head><meta name=viewport content="width=device-width,initial-scale=1">
<title>HomeFlow</title><style>
body{background:#0B0D12;color:#F4F6FB;font-family:-apple-system,system-ui,sans-serif;margin:0;padding:24px}
h1{font-size:22px;font-weight:600;margin:0 0 20px}
button{display:block;width:100%;padding:18px;margin:10px 0;font-size:17px;font-weight:600;
color:#fff;background:#3B6EF5;border:none;border-radius:14px;transition:transform .1s,background .2s}
button:active{transform:scale(.97)}.done{background:#3DD68C!important}
</style></head><body><h1>HomeFlow</h1>$buttons
<script>function flash(b){b.classList.add('done');setTimeout(()=>b.classList.remove('done'),800)}</script>
</body></html>"""
    }
}
