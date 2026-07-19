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
        Station("🐦 Vogelgezwitscher (Natur)", "http://prem2.di.fm/nature", "nature birds"),
        Station("🌧️ Regen & Gewitter", "https://streams.calmradio.com/api/39/128/stream", "rain thunder"),
        Station("🌊 Meeresrauschen", "https://streams.calmradio.com/api/103/128/stream", "ocean waves"),
        Station("🌲 Wald-Ambience", "https://streams.calmradio.com/api/230/128/stream", "forest nature"),
        Station("🧘 Ruhe / Meditation", "https://streams.calmradio.com/api/204/128/stream", "meditation calm"),
        Station("🎹 SomaFM: Groove Salad (Chill)", "http://ice1.somafm.com/groovesalad-128-mp3", "chill downtempo"),
        Station("🚀 SomaFM: Drone Zone (Ambient)", "http://ice1.somafm.com/dronezone-128-mp3", "ambient space"),
        Station("🌌 SomaFM: Deep Space One", "http://ice1.somafm.com/deepspaceone-128-mp3", "ambient space"),
        Station("💧 SomaFM: Fluid (Chill-Hop)", "http://ice1.somafm.com/fluid-128-mp3", "chillhop"),
        Station("☕ SomaFM: Coffeehouse", "http://ice1.somafm.com/coffeehouse-128-mp3", "acoustic folk"),
        Station("🎷 SomaFM: Sonic Universe (Jazz)", "http://ice1.somafm.com/sonicuniverse-128-mp3", "jazz"),
        Station("📻 SomaFM: Lush (Vocal)", "http://ice1.somafm.com/lush-128-mp3", "vocal chill"),
        Station("🎉 SomaFM: Beat Blender (House)", "http://ice1.somafm.com/beatblender-128-mp3", "house electronic"),
        Station("🔥 SomaFM: Indie Pop Rocks", "http://ice1.somafm.com/indiepop-128-mp3", "indie pop")
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
