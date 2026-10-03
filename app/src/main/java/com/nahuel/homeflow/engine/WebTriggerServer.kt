package com.nahuel.homeflow.engine

import com.nahuel.homeflow.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.ServerSocket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Tiny dependency-free HTTP server so a guest (e.g. an iPhone on the same WiFi / Tailscale)
 * can trigger routines from a browser page. No app needed on their device.
 *
 * Every request must carry the secret `k` (Config.webToken), which is only shown in the
 * app's QR code / link - so other devices on the network can't run anything.
 *
 * Routes:
 *   GET /?k=<token>              -> HTML page with a button per enabled routine
 *   GET /run?id=<id>&k=<token>   -> runs that routine (enabled ones only), returns "ok"
 */
object WebTriggerServer {
    const val PORT = 87 * 1000 + 82   // 8782
    @Volatile private var running = false
    private var job: Job? = null
    private var server: ServerSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isRunning() = running

    /** Creates the access token on first use. Rotating it invalidates every shared link. */
    fun token(): String {
        Store.config.value.webToken.takeIf { it.isNotBlank() }?.let { return it }
        val bytes = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val t = bytes.joinToString("") { "%02x".format(it) }
        Store.updateConfig { it.copy(webToken = t) }
        return t
    }

    fun rotateToken() {
        Store.updateConfig { it.copy(webToken = "") }
        token()
    }

    /** Best-effort local IPv4 for building the guest URL (includes the access token). */
    fun localUrl(): String {
        val ip = runCatching {
            java.net.NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }?.hostAddress
        }.getOrNull() ?: "<phone-ip>"
        return "http://$ip:$PORT/?k=${token()}"
    }

    fun start(onRun: (String) -> Unit) {
        if (running) return
        running = true
        token()
        job = scope.launch {
            runCatching {
                server = ServerSocket(PORT)
                while (running) {
                    val socket = server?.accept() ?: break
                    launch {
                        runCatching {
                            socket.soTimeout = 5_000   // a silent client can't hold a coroutine forever
                            socket.use { s ->
                                val reader = s.getInputStream().bufferedReader()
                                val line = reader.readLine() ?: return@use
                                // e.g. "GET /run?id=abc&k=xyz HTTP/1.1"
                                val target = line.split(" ").getOrNull(1) ?: "/"
                                val path = target.substringBefore("?")
                                val query = parseQuery(target.substringAfter("?", ""))
                                val (status, ctype, body) = when {
                                    !tokenOk(query["k"]) -> Triple("403 Forbidden", "text/plain", "forbidden")
                                    path == "/run" -> {
                                        val id = query["id"].orEmpty()
                                        val r = Store.routine(id)
                                        if (r != null && r.enabled) {
                                            onRun(id); Triple("200 OK", "text/plain", "ok")
                                        } else Triple("404 Not Found", "text/plain", "not found")
                                    }
                                    path == "/" -> Triple("200 OK", "text/html; charset=utf-8", pageHtml())
                                    else -> Triple("404 Not Found", "text/plain", "not found")
                                }
                                val out = s.getOutputStream()
                                val bytes = body.toByteArray(Charsets.UTF_8)
                                out.write(("HTTP/1.1 $status\r\nContent-Type: $ctype\r\n" +
                                        "Content-Length: ${bytes.size}\r\nCache-Control: no-store\r\n" +
                                        "Referrer-Policy: no-referrer\r\nConnection: close\r\n\r\n").toByteArray())
                                out.write(bytes); out.flush()
                            }
                        }
                    }
                }
            }
            running = false
        }
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        job?.cancel()
    }

    private fun parseQuery(q: String): Map<String, String> =
        q.split("&").filter { "=" in it }.associate {
            val (k, v) = it.split("=", limit = 2)
            k to runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault("")
        }

    private fun tokenOk(given: String?): Boolean {
        val expected = Store.config.value.webToken
        if (given == null || expected.isBlank()) return false
        return MessageDigest.isEqual(given.toByteArray(), expected.toByteArray())   // constant time
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    private fun pageHtml(): String {
        val routines = Store.routines.value.filter { it.enabled }
        val buttons = routines.joinToString("\n") { r ->
            """<button data-id="${esc(r.id)}">${esc(r.icon)} ${esc(r.name)}</button>"""
        }
        return """<!doctype html><html><head><meta name=viewport content="width=device-width,initial-scale=1">
<meta name="theme-color" content="#0C0D0E"><title>SmartFlow</title><style>
:root{--bg:#F4F3F0;--card:#FFFFFF;--ink:#1A1B1C;--muted:#5F5D59;--accent:#A35F0C}
@media(prefers-color-scheme:dark){:root{--bg:#0C0D0E;--card:#16181A;--ink:#EDEBE7;--muted:#A3A19C;--accent:#F0B45E}}
body{background:var(--bg);color:var(--ink);font-family:-apple-system,system-ui,sans-serif;margin:0;padding:24px;max-width:560px;margin:auto}
h1{font-size:24px;font-weight:600;margin:8px 0 4px}p{color:var(--muted);margin:0 0 20px;font-size:14px}
button{display:block;width:100%;padding:18px 20px;margin:0 0 10px;font-size:16px;font-weight:600;text-align:left;
color:var(--ink);background:var(--card);border:0;border-radius:18px;font-family:inherit;transition:background .2s}
.done{background:var(--accent)!important;color:var(--bg)!important}.err{outline:2px solid #FF8A7A}
</style></head><body><h1>SmartFlow</h1><p>Tippen startet die Automation.</p>$buttons
<script>
const k=new URLSearchParams(location.search).get('k')||'';
document.querySelectorAll('button[data-id]').forEach(b=>b.onclick=()=>{
 fetch('/run?id='+encodeURIComponent(b.dataset.id)+'&k='+encodeURIComponent(k))
  .then(r=>{b.classList.add(r.ok?'done':'err');setTimeout(()=>b.classList.remove('done','err'),900)})
  .catch(()=>b.classList.add('err'))});
</script></body></html>"""
    }
}
