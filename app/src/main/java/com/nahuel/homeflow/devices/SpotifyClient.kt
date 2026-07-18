package com.nahuel.homeflow.devices

import android.util.Base64
import com.nahuel.homeflow.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Spotify via the official Connect Web API (OAuth PKCE - no secret, user's own Client-ID).
 * Playback lands on the Sonos speaker, because Sonos players ARE Spotify-Connect devices.
 * Needs: Spotify Premium + a free developer app (Client-ID) + one-time login.
 */
object SpotifyClient {
    private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
    const val REDIRECT = "homeflow://spotify"

    @Volatile private var cachedToken: String = ""
    @Volatile private var cachedUntil: Long = 0

    fun newVerifier(): String {
        val bytes = ByteArray(48); SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun authUrl(clientId: String, verifier: String): String =
        "https://accounts.spotify.com/authorize?response_type=code" +
            "&client_id=" + clientId +
            "&redirect_uri=" + java.net.URLEncoder.encode(REDIRECT, "UTF-8") +
            "&scope=" + java.net.URLEncoder.encode(
                "user-modify-playback-state user-read-playback-state", "UTF-8") +
            "&code_challenge_method=S256&code_challenge=" + challenge(verifier)

    /** Called from the homeflow://spotify?code=... callback. Stores the refresh token. */
    suspend fun exchangeCode(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cfg = Store.config.value
            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT)
                .add("client_id", cfg.spotifyClientId)
                .add("code_verifier", cfg.spotifyVerifier)
                .build()
            Http.internet.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute().use { r ->
                val o = JSONObject(r.body!!.string())
                check(r.isSuccessful) { o.optString("error_description", "Login fehlgeschlagen") }
                Store.updateConfig { it.copy(spotifyRefresh = o.getString("refresh_token")) }
                cachedToken = o.optString("access_token")
                cachedUntil = System.currentTimeMillis() + (o.optLong("expires_in", 3600) - 60) * 1000
            }
        }
    }

    private suspend fun accessToken(): String = withContext(Dispatchers.IO) {
        if (cachedToken.isNotEmpty() && System.currentTimeMillis() < cachedUntil) return@withContext cachedToken
        val cfg = Store.config.value
        check(cfg.spotifyRefresh.isNotEmpty()) { "Spotify nicht verbunden (Einstellungen)" }
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", cfg.spotifyRefresh)
            .add("client_id", cfg.spotifyClientId)
            .build()
        Http.internet.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute().use { r ->
            val o = JSONObject(r.body!!.string())
            check(r.isSuccessful) { "Spotify-Login abgelaufen - in den Einstellungen neu verbinden" }
            o.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { newRt ->
                Store.updateConfig { it.copy(spotifyRefresh = newRt) }
            }
            cachedToken = o.getString("access_token")
            cachedUntil = System.currentTimeMillis() + (o.optLong("expires_in", 3600) - 60) * 1000
            cachedToken
        }
    }

    private suspend fun api(method: String, path: String, body: JSONObject? = null): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val b = Request.Builder().url("https://api.spotify.com/v1$path")
                .header("Authorization", "Bearer ${accessToken()}")
            when (method) {
                "GET" -> b.get()
                "PUT" -> b.put((body?.toString() ?: "").toRequestBody("application/json".toMediaType()))
                else -> error("method")
            }
            Http.internet.newCall(b.build()).execute().use { r -> r.code to (r.body?.string() ?: "") }
        }

    /** spotify:track:.., open.spotify.com links, or a free-text search - plays on the Sonos. */
    suspend fun play(queryOrUri: String, deviceHint: String): Result<Unit> = runCatching {
        require(queryOrUri.isNotBlank()) { "Kein Song/Playlist angegeben" }
        val uri = resolveUri(queryOrUri)
        val deviceId = findDevice(deviceHint)
        val payload = if (uri.startsWith("spotify:track:"))
            JSONObject().put("uris", JSONArray().put(uri))
        else JSONObject().put("context_uri", uri)
        val (code, resp) = api("PUT", "/me/player/play?device_id=$deviceId", payload)
        when {
            code in 200..299 -> Unit
            code == 403 -> error("Spotify: Premium erforderlich oder Wiedergabe verweigert")
            else -> error("Spotify-Fehler $code: ${JSONObject(resp).optJSONObject("error")?.optString("message") ?: ""}")
        }
    }

    private suspend fun resolveUri(q: String): String {
        val t = q.trim()
        if (t.startsWith("spotify:")) return t
        Regex("open\\.spotify\\.com/(track|playlist|album|artist)/([A-Za-z0-9]+)").find(t)?.let {
            return "spotify:${it.groupValues[1]}:${it.groupValues[2]}"
        }
        val (code, resp) = api("GET", "/search?limit=1&type=playlist,track&q=" +
                java.net.URLEncoder.encode(t, "UTF-8"))
        check(code in 200..299) { "Spotify-Suche fehlgeschlagen ($code)" }
        val o = JSONObject(resp)
        o.optJSONObject("tracks")?.optJSONArray("items")?.optJSONObject(0)?.optString("uri")
            ?.takeIf { it.isNotEmpty() }?.let { return it }
        o.optJSONObject("playlists")?.optJSONArray("items")?.optJSONObject(0)?.optString("uri")
            ?.takeIf { it.isNotEmpty() }?.let { return it }
        error("Nichts gefunden für \"$t\"")
    }

    /** Sonos players appear as Spotify-Connect devices under their room name. */
    private suspend fun findDevice(hint: String): String {
        val (code, resp) = api("GET", "/me/player/devices")
        check(code in 200..299) { "Geräteliste fehlgeschlagen ($code)" }
        val arr = JSONObject(resp).optJSONArray("devices") ?: JSONArray()
        val devices = (0 until arr.length()).map { arr.getJSONObject(it) }
        val match = devices.firstOrNull { it.optString("name").contains(hint, ignoreCase = true) && hint.isNotBlank() }
            ?: devices.firstOrNull { it.optString("type") == "Speaker" }
            ?: devices.firstOrNull()
        checkNotNull(match) {
            "Kein Spotify-Connect-Gerät sichtbar. Öffne einmal Spotify und spiele kurz auf dem Sonos, dann taucht er auf."
        }
        return match.getString("id")
    }
}
