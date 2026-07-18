package com.nahuel.homeflow.engine

import com.nahuel.homeflow.data.*
import com.nahuel.homeflow.devices.HueLight

/**
 * Fully offline, free, instant natural-language routine parser (German + English).
 * Rule-based on purpose: this domain is small and closed, so keyword grammar beats
 * a 1-GB on-device LLM in speed, reliability and battery. No cloud, no cost.
 */
object NlParser {

    private val colors = mapOf(
        "rot" to "#FF0000", "red" to "#FF0000",
        "grün" to "#1ED760", "gruen" to "#1ED760", "green" to "#1ED760",
        "blau" to "#2E6BFF", "blue" to "#2E6BFF",
        "lila" to "#8B5CF6", "violett" to "#8B5CF6", "purple" to "#8B5CF6",
        "pink" to "#F062A6", "rosa" to "#F5A6C8",
        "orange" to "#FF8C42", "gelb" to "#FFD400", "yellow" to "#FFD400",
        "türkis" to "#22D3EE", "tuerkis" to "#22D3EE", "cyan" to "#22D3EE",
        "weiß" to "#FFFFFF", "weiss" to "#FFFFFF", "white" to "#FFFFFF",
        "warmweiß" to "#FFD9A0", "warmweiss" to "#FFD9A0", "warm" to "#FFD9A0"
    )

    data class Ctx(val cfg: Config, val lights: List<HueLight>)

    /** Returns a Routine draft, or null with [error] describing what was not understood. */
    fun parse(input: String, cfg: Config, lights: List<HueLight>): Result<Routine> = runCatching {
        val text = input.lowercase().replace("ß", "ss")
        val ctx = Ctx(cfg, lights)

        // ---------- trigger ----------
        var trigger = Trigger(TriggerType.MANUAL)
        var body = text
        Regex("um (\\d{1,2})[:.](\\d{2})( uhr)?").find(text)?.let { m ->
            trigger = Trigger(TriggerType.TIME, time = "%02d:%s".format(m.groupValues[1].toInt(), m.groupValues[2]))
            body = text.replace(m.value, " ")
        }
        if (Regex("nfc|tag berühr|tag beruehr").containsMatchIn(text)) {
            trigger = Trigger(TriggerType.NFC); body = body.replace(Regex("wenn ich (den )?nfc[- ]?tag berühre?"), " ")
        }
        if (Regex("wenn ich (gehe|das haus verlasse|weg bin)|beim (gehen|verlassen)|when i leave").containsMatchIn(text)) {
            trigger = Trigger(TriggerType.LEAVE_WIFI)
            body = body.replace(Regex("wenn ich (gehe|das haus verlasse|weg bin)|beim (gehen|verlassen)|when i leave"), " ")
        }
        if (Regex("wenn ich (nach hause|heim|ankomme)|when i (arrive|come home)").containsMatchIn(text)) {
            trigger = Trigger(TriggerType.ARRIVE_HOME)
            body = body.replace(Regex("wenn ich (nach hause komme|heim ?komme|ankomme)|when i (arrive|come home)"), " ")
        }
        Regex("bei sonnenuntergang|at sunset").find(text)?.let {
            trigger = Trigger(TriggerType.SUN, sunEvent = "SUNSET"); body = body.replace(it.value, " ")
        }
        Regex("bei sonnenaufgang|at sunrise").find(text)?.let {
            trigger = Trigger(TriggerType.SUN, sunEvent = "SUNRISE"); body = body.replace(it.value, " ")
        }
        // "wenn das badlicht angeht"
        Regex("wenn (das |die )?([a-zäöü]+)?-?licht (an|aus)geht").find(text)?.let { m ->
            val lightName = m.groupValues[2]
            val light = bestLight(ctx, lightName)
            if (light != null) {
                trigger = Trigger(TriggerType.DEVICE_STATE, hueLightId = light.id, toState = m.groupValues[3] == "an")
                body = body.replace(m.value, " ")
            }
        }

        // ---------- conditions ----------
        val conds = mutableListOf<Cond>()
        var b2 = body
        if (Regex("tagsüber|tagsueber|am tag|during the day").containsMatchIn(b2)) {
            conds += Cond(CondType.DAY); b2 = b2.replace(Regex("tagsüber|tagsueber|am tag|during the day"), " ")
        }
        if (Regex("nachts|in der nacht|at night").containsMatchIn(b2)) {
            conds += Cond(CondType.NIGHT); b2 = b2.replace(Regex("nachts|in der nacht|at night"), " ")
        }
        Regex("wenn (gerade )?nichts (läuft|laeuft|spielt)|if nothing is playing").find(b2)?.let { m ->
            val sp = ctx.cfg.sonos.firstOrNull { b2.contains(it.name.lowercase()) } ?: ctx.cfg.sonos.firstOrNull()
            if (sp != null) conds += Cond(CondType.SPEAKER_IDLE, sp.ip)
            b2 = b2.replace(m.value, " ")
        }

        // ---------- actions ----------
        val actions = mutableListOf<Action>()
        val clauses = b2.split(Regex("\\bund\\b|\\bdann\\b|,|\\band\\b|\\bthen\\b")).map { it.trim() }.filter { it.length > 2 }
        for (clause in clauses) parseClause(clause, ctx, actions)
        check(actions.isNotEmpty()) {
            "Keine Aktion verstanden. Beispiele: „Badlicht grün und Vogelsounds auf Sonos Bad, aber nur wenn nichts läuft“ · „Um 7:00 alle Lampen warmweiss 40%“"
        }

        Routine(
            name = input.trim().take(28).replaceFirstChar { it.uppercase() },
            triggers = listOf(trigger),
            variants = listOf(Variant(conditions = conds, actions = actions))
        )
    }

    private fun bestLight(ctx: Ctx, token: String?): HueLight? {
        if (token.isNullOrBlank()) return null
        return ctx.lights.firstOrNull { it.name.lowercase().replace("ß", "ss").contains(token) }
    }

    private fun parseClause(clause: String, ctx: Ctx, out: MutableList<Action>) {
        val c = clause

        // --- TV ---
        val tvApps = listOf("netflix" to "netflix", "youtube" to "youtube.leanback.v4",
            "prime" to "amazon", "disney" to "com.disney.disneyplus-prod")
        tvApps.firstOrNull { c.contains(it.first) }?.let { (word, appId) ->
            val tv = ctx.cfg.tvs.firstOrNull() ?: return@let
            out += Action(TargetType.LG_TV, tv.ip, "app", mapOf("appId" to appId))
            return
        }
        if (Regex("fernseher|tv").containsMatchIn(c)) {
            val tv = ctx.cfg.tvs.firstOrNull()
            if (tv != null) {
                if (Regex("\\ban\\b|einschalt|\\bon\\b").containsMatchIn(c)) { out += Action(TargetType.LG_TV, tv.ip, "on"); return }
                if (Regex("\\baus\\b|ausschalt|\\boff\\b").containsMatchIn(c)) { out += Action(TargetType.LG_TV, tv.ip, "off"); return }
                if (Regex("stumm|mute").containsMatchIn(c)) { out += Action(TargetType.LG_TV, tv.ip, "mute"); return }
            }
        }

        // --- Sonos ---
        val speaker = ctx.cfg.sonos.firstOrNull { c.contains(it.name.lowercase().replace("ß", "ss")) }
        val soundish = Regex("sound|musik|music|geräusch|geraeusch|radio|spiel|play|lautstärke|lautstaerke|volume|pause|stopp|stop|stumm|mute|spotify")
            .containsMatchIn(c)
        if (speaker != null || (soundish && ctx.cfg.sonos.isNotEmpty() && !Regex("licht|lampe|light").containsMatchIn(c))) {
            val sp = speaker ?: ctx.cfg.sonos.first()
            val vol = Regex("(\\d{1,3})\\s*%").find(c)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("lautstärke (\\d{1,3})|lautstaerke (\\d{1,3})|volume (\\d{1,3})").find(c)
                    ?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull()
                ?: if (Regex("leise|quiet|low").containsMatchIn(c)) 15 else null
            when {
                c.contains("spotify") -> {
                    val q = clause.substringAfter("spotify").trim(' ', ':', '-')
                    out += Action(TargetType.SONOS, sp.ip, "spotify",
                        buildMap { put("query", q.ifBlank { "Chill Mix" }); vol?.let { put("volume", it.toString()) } })
                }
                Regex("pause|stopp\\b|stop\\b").containsMatchIn(c) -> out += Action(TargetType.SONOS, sp.ip, "pause")
                Regex("stumm|mute").containsMatchIn(c) -> out += Action(TargetType.SONOS, sp.ip, "mute", mapOf("on" to "true"))
                Regex("http\\S+").containsMatchIn(c) -> out += Action(TargetType.SONOS, sp.ip, "play_uri",
                    buildMap { put("uri", Regex("http\\S+").find(clause)!!.value); vol?.let { put("volume", it.toString()) } })
                soundish -> out += Action(TargetType.SONOS, sp.ip, "play_uri",
                    buildMap { put("uri", ""); vol?.let { put("volume", it.toString()) } })  // URL fehlt -> Editor markiert ⚠
                vol != null -> out += Action(TargetType.SONOS, sp.ip, "volume", mapOf("volume" to vol.toString()))
            }
            if (speaker != null || soundish) return
        }

        // --- Hue ---
        val allLights = Regex("alle (lampen|lichter)|alles|überall|ueberall|all lights").containsMatchIn(c)
        val light = ctx.lights.firstOrNull { c.contains(it.name.lowercase().replace("ß", "ss")) }
        val lightish = Regex("licht|lampe|light").containsMatchIn(c)
        if (allLights || light != null || lightish) {
            val id = if (allLights || light == null) "all" else light.id
            val params = mutableMapOf<String, String>()
            colors.entries.firstOrNull { c.contains(it.key) }?.let { params["color"] = it.value; params["on"] = "true" }
            Regex("(\\d{1,3})\\s*%").find(c)?.groupValues?.get(1)?.toIntOrNull()?.let {
                params["brightness"] = it.coerceIn(1, 100).toString(); params["on"] = "true"
            }
            if (Regex("dimm|gedimmt|dunkler|dim\\b").containsMatchIn(c)) { params["brightness"] = "20"; params["on"] = "true" }
            if (Regex("\\bhell\\b|bright").containsMatchIn(c)) { params["brightness"] = "100"; params["on"] = "true" }
            if (Regex("\\baus\\b|ausschalt|\\boff\\b").containsMatchIn(c)) { params.clear(); params["on"] = "false" }
            else if (params.isEmpty() && Regex("\\ban\\b|einschalt|\\bon\\b").containsMatchIn(c)) params["on"] = "true"
            if (params.isNotEmpty()) out += Action(TargetType.HUE, id, "set", params)
        }
    }
}
