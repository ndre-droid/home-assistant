package com.nahuel.homeflow.nlp

import com.nahuel.homeflow.devices.HueLight
import com.nahuel.homeflow.data.Routine
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.devices.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns natural language ("Wenn ich den NFC-Tag berühre, alles aus") into a Routine
 * via the Anthropic API. Only used at setup/edit time — execution itself never
 * touches the internet, so routines stay instant.
 */
object ClaudeParser {

    suspend fun parse(userText: String, existing: Routine?, hueLights: List<HueLight>): Result<Routine> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cfg = Store.config.value
                check(cfg.anthropicKey.isNotEmpty()) { "Kein Anthropic API-Key hinterlegt (Einstellungen)" }

                val body = JSONObject()
                    .put("model", cfg.model)
                    .put("max_tokens", 2000)
                    .put("system", systemPrompt(hueLights))
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content",
                        buildString {
                            append(userText)
                            existing?.let {
                                append("\n\nBestehende Routine (bitte entsprechend anpassen):\n")
                                append(it.toJson().toString())
                            }
                        }
                    )))
                    .toString().toRequestBody("application/json".toMediaType())

                val req = Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .header("x-api-key", cfg.anthropicKey)
                    .header("anthropic-version", "2023-06-01")
                    .post(body)
                    .build()

                Http.internet.newCall(req).execute().use { resp ->
                    val text = resp.body?.string() ?: ""
                    check(resp.isSuccessful) {
                        runCatching { JSONObject(text).getJSONObject("error").getString("message") }
                            .getOrDefault("API HTTP ${resp.code}")
                    }
                    val content = JSONObject(text).getJSONArray("content").getJSONObject(0).getString("text")
                    val json = extractJson(content)
                    val parsed = Routine.fromJson(JSONObject(json))
                    // keep identity + enabled state when editing
                    if (existing != null) parsed.copy(id = existing.id, enabled = existing.enabled) else parsed
                }
            }
        }

    private fun extractJson(s: String): String {
        val start = s.indexOf('{'); val end = s.lastIndexOf('}')
        check(start >= 0 && end > start) { "Claude hat kein JSON geliefert" }
        return s.substring(start, end + 1)
    }

    private fun systemPrompt(hueLights: List<HueLight>): String {
        val cfg = Store.config.value
        val lights = hueLights.joinToString("\n") { "- id=\"${it.id}\" name=\"${it.name}\"" }
        val speakers = cfg.sonos.joinToString("\n") { "- ip=\"${it.ip}\" name=\"${it.name}\"" }
        val tvs = cfg.tvs.joinToString("\n") { "- ip=\"${it.ip}\" name=\"${it.name}\"" }
        return """
You convert German or English smart-home automation descriptions into JSON. Reply with ONLY a JSON object, no prose, no code fences.

Schema:
{"name":"short German name","trigger":{"type":"MANUAL|NFC|DEVICE_STATE","hueLightId":"<id>","toState":true|false},"variants":[{"condition":"ALWAYS|DAY|NIGHT","actions":[{"target":"HUE|SONOS|LG_TV","deviceId":"...","command":"...","params":{}}]}]}

Rules:
- trigger DEVICE_STATE only when the user describes "wenn Licht X an/aus geht"; hueLightId must be one of the Hue ids below; toState=true means "turns on".
- NFC when the user mentions an NFC tag/chip. MANUAL for button/widget triggers or when unclear.
- If the user distinguishes day/night (tagsüber/nachts), create one DAY and one NIGHT variant; otherwise a single ALWAYS variant. Day is ${cfg.dayStart}–${cfg.nightStart}.
- HUE: command "set", deviceId = light id or "all"; params subset of {"on":"true|false","color":"#RRGGBB","brightness":"1-100"}.
- SONOS: deviceId = speaker ip; commands: "play","pause","stop","volume"(params.volume 0-100),"play_uri"(params.uri, optional params.volume). For ambience sounds (birds, whales, rain...) use "play_uri" with params.uri="" — the app asks the user for a stream URL; still set a low volume if requested (leise ≈ 15).
- LG_TV: deviceId = tv ip; commands "on","off","volume"(params.volume),"mute".
- "Alles aus" = HUE set on=false deviceId "all" + SONOS pause for every speaker + LG_TV off for every TV.

Devices:
HUE LIGHTS:
$lights
SONOS SPEAKERS:
$speakers
LG TVS:
$tvs
""".trimIndent()
    }
}
