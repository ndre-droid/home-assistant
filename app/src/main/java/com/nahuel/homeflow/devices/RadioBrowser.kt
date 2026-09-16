package com.nahuel.homeflow.devices

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder

/**
 * radio-browser.info: open, key-free directory of ~50k radio stations and ambience
 * streams. Search "birds", "nature", "rain", genres, station names - pick one and
 * its stream URL goes straight into the Sonos action.
 */
object RadioBrowser {
    data class Station(val name: String, val url: String, val tags: String)

    /** Curated, known plain-MP3 streams that work on Sonos out of the box (no search needed). */
    val CURATED: List<Station> = listOf(
        // SomaFM - all streams are free & open, no login (reliable on Sonos)
        Station("🎹 Groove Salad (Chill/Ambient)", "http://ice1.somafm.com/groovesalad-128-mp3", "chill ambient"),
        Station("🚀 Drone Zone (Space/Ambient)", "http://ice1.somafm.com/dronezone-128-mp3", "ambient space"),
        Station("🌌 Deep Space One (Ambient)", "http://ice1.somafm.com/deepspaceone-128-mp3", "ambient space"),
        Station("💧 Fluid (Chillhop)", "http://ice1.somafm.com/fluid-128-mp3", "chillhop"),
        Station("☕ Coffeehouse (Acoustic)", "http://ice1.somafm.com/coffeehouse-128-mp3", "acoustic folk"),
        Station("🎷 Sonic Universe (Jazz)", "http://ice1.somafm.com/sonicuniverse-128-mp3", "jazz"),
        Station("📻 Lush (Vocal Chill)", "http://ice1.somafm.com/lush-128-mp3", "vocal chill"),
        Station("🎉 Beat Blender (Deep House)", "http://ice1.somafm.com/beatblender-128-mp3", "house"),
        Station("🔥 Indie Pop Rocks", "http://ice1.somafm.com/indiepop-128-mp3", "indie pop"),
        Station("🌃 Secret Agent (Downtempo)", "http://ice1.somafm.com/secretagent-128-mp3", "downtempo lounge"),
        Station("🎄 Christmas Lounge", "http://ice1.somafm.com/christmas-128-mp3", "christmas"),
        Station("🧘 Dubstep Beyond", "http://ice1.somafm.com/dubstep-128-mp3", "dubstep"),
        // Public radio (open Icecast)
        Station("🎼 Radio Paradise (Main Mix)", "http://stream.radioparadise.com/mp3-128", "eclectic rock"),
        Station("🎵 Radio Paradise (Mellow)", "http://stream.radioparadise.com/mellow-128", "mellow"),
        Station("🎸 Radio Paradise (Rock)", "http://stream.radioparadise.com/rock-128", "rock")
    )

    suspend fun search(query: String): Result<List<Station>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://all.api.radio-browser.info/json/stations/search" +
                    "?limit=25&hidebroken=true&order=votes&reverse=true&name=" +
                    URLEncoder.encode(query.trim(), "UTF-8")
            val req = Request.Builder().url(url)
                .header("User-Agent", "HomeFlow/1.0").get().build()
            Http.internet.newCall(req).execute().use { resp ->
                check(resp.isSuccessful) { "Suche fehlgeschlagen (HTTP ${resp.code})" }
                val arr = JSONArray(resp.body!!.string())
                (0 until arr.length()).map { i ->
                    val s = arr.getJSONObject(i)
                    if (s.optInt("hls", 0) == 1) return@map Station("", "", "")
                    Station(
                        name = s.optString("name").trim(),
                        url = s.optString("url_resolved").ifEmpty { s.optString("url") },
                        tags = s.optString("tags")
                    )
                }.filter {
                    it.url.isNotBlank() && it.name.isNotBlank() &&
                            !it.url.substringBefore('?').lowercase().endsWith(".m3u8")
                }
            }
        }
    }
}
