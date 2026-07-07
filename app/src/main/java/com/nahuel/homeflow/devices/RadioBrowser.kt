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

    suspend fun search(query: String): Result<List<Station>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://de1.api.radio-browser.info/json/stations/search" +
                    "?limit=25&hidebroken=true&order=votes&reverse=true&name=" +
                    URLEncoder.encode(query.trim(), "UTF-8")
            val req = Request.Builder().url(url)
                .header("User-Agent", "HomeFlow/1.0").get().build()
            Http.internet.newCall(req).execute().use { resp ->
                check(resp.isSuccessful) { "Suche fehlgeschlagen (HTTP ${resp.code})" }
                val arr = JSONArray(resp.body!!.string())
                (0 until arr.length()).map { i ->
                    val s = arr.getJSONObject(i)
                    Station(
                        name = s.optString("name").trim(),
                        url = s.optString("url_resolved").ifEmpty { s.optString("url") },
                        tags = s.optString("tags")
                    )
                }.filter { it.url.isNotBlank() && it.name.isNotBlank() }
            }
        }
    }
}
