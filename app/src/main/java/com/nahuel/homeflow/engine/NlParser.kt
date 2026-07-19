package com.nahuel.homeflow.engine

import com.nahuel.homeflow.data.*
import com.nahuel.homeflow.devices.HueLight

/**
 * Offline, free, instant natural-language routine parser (German + English).
 * Rule-based: this domain is small and closed, so a keyword grammar beats a
 * 1-GB on-device LLM in speed, reliability and battery. No cloud, no cost.
 *
 * Understands, in one sentence:
 *  - a trigger  ("wenn das Badlicht angeht", "um 7:00", "bei Sonnenuntergang", "wenn ich gehe", NFC)
 *  - conditions ("tagsüber", "nachts", "nur wenn nichts läuft")
 *  - several actions across several devices, joined by "und / dann / , / and / then".
 */
object NlParser {

    // longest keys first so "warmweiss" wins over "weiss"
    private val COLORS = listOf(
        "warmweiss" to "#FFD9A0", "warmweiß" to "#FFD9A0", "kaltweiss" to "#EAF3FF", "kaltweiß" to "#EAF3FF",
        "türkis" to "#22D3EE", "tuerkis" to "#22D3EE", "violett" to "#8B5CF6",
        "orange" to "#FF8C42", "purple" to "#8B5CF6", "yellow" to "#FFD400", "green" to "#1ED760",
        "white" to "#FFFFFF", "warm" to "#FFD9A0",
        "rot" to "#FF0000", "red" to "#FF0000", "grün" to "#1ED760", "gruen" to "#1ED760",
        "blau" to "#2E6BFF", "blue" to "#2E6BFF", "lila" to "#8B5CF6", "pink" to "#F062A6",
        "rosa" to "#F5A6C8", "gelb" to "#FFD400", "cyan" to "#22D3EE", "magenta" to "#FF00FF",
        "gold" to "#FFC03A", "weiss" to "#FFFFFF", "weiß" to "#FFFFFF"
    )

    private val TV_APPS = listOf(
        listOf("netflix") to "netflix",
        listOf("youtube", "you tube") to "youtube.leanback.v4",
        listOf("prime", "amazon") to "amazon",
        listOf("disney") to "com.disney.disneyplus-prod",
        listOf("spotify") to "spotify",
        listOf("plex") to "cdp-30",
        listOf("apple tv", "appletv") to "com.apple.appletv"
    )

    data class Ctx(val cfg: Config, val lights: List<HueLight>)
    private fun norm(s: String) = s.lowercase().replace("ß", "ss")

    fun parse(input: String, cfg: Config, lights: List<HueLight>): Result<Routine> = runCatching {
        val ctx = Ctx(cfg, lights)
        var body = norm(input)

        // ---------------- trigger ----------------
        var trigger = Trigger(TriggerType.MANUAL)
        fun strip(re: String) { body = body.replace(Regex(re), " ") }

        Regex("um (\\d{1,2})[:.](\\d{2})( uhr)?|at (\\d{1,2})[:.](\\d{2})").find(body)?.let { m ->
            val h = (m.groupValues[1].ifEmpty { m.groupValues[4] }).toInt()
            val mi = m.groupValues[2].ifEmpty { m.groupValues[5] }
            trigger = Trigger(TriggerType.TIME, time = "%02d:%s".format(h, mi))
            body = body.replace(m.value, " ")
        }
        if (Regex("nfc|tag berühr|tag beruehr").containsMatchIn(body)) {
            trigger = Trigger(TriggerType.NFC)
            strip("wenn ich (den |die )?nfc[- ]?tag (berühre?|beruehre?|scanne?)|nfc")
        }
        if (Regex("wenn ich (nach hause|heim|heimkomme|ankomme)|when i (arrive|come home|get home)").containsMatchIn(body)) {
            trigger = Trigger(TriggerType.ARRIVE_HOME)
            strip("wenn ich (nach hause komme|heim ?komme|heimkomme|ankomme)|when i (arrive|come home|get home)")
        } else if (Regex("wenn ich (gehe|rausgehe|das haus verlasse|weg bin)|beim (gehen|verlassen)|when i leave").containsMatchIn(body)) {
            trigger = Trigger(TriggerType.LEAVE_WIFI)
            strip("wenn ich (gehe|rausgehe|das haus verlasse|weg bin)|beim (gehen|verlassen)|when i leave")
        }
        if (Regex("bei sonnenuntergang|wenn es dunkel wird|at sunset|when it gets dark").containsMatchIn(body)) {
            trigger = Trigger(TriggerType.SUN, sunEvent = "SUNSET")
            strip("bei sonnenuntergang|wenn es dunkel wird|at sunset|when it gets dark")
        }
        if (Regex("bei sonnenaufgang|at sunrise").containsMatchIn(body)) {
            trigger = Trigger(TriggerType.SUN, sunEvent = "SUNRISE")
            strip("bei sonnenaufgang|at sunrise")
        }
        Regex("wenn (das |die |der )?([a-zäöü ]+?)-? ?licht (an|aus) ?geht|when the ([a-z ]+?) light turns (on|off)")
            .find(body)?.let { m ->
                val name = (m.groupValues[2].ifBlank { m.groupValues[4] }).trim()
                bestLight(ctx, name)?.let { light ->
                    val on = m.groupValues[3].ifEmpty { m.groupValues[5] }.let { it == "an" || it == "on" }
                    trigger = Trigger(TriggerType.DEVICE_STATE, hueLightId = light.id, toState = on)
                    body = body.replace(m.value, " ")
                }
            }

        // ---------------- conditions ----------------
        val conds = mutableListOf<Cond>()
        if (Regex("tagsüber|tagsueber|am tag|during the day").containsMatchIn(body)) {
            conds += Cond(CondType.DAY); strip("tagsüber|tagsueber|am tag|during the day")
        }
        if (Regex("nachts|in der nacht|abends|at night|in the evening").containsMatchIn(body)) {
            conds += Cond(CondType.NIGHT); strip("nachts|in der nacht|abends|at night|in the evening")
        }
        Regex("(nur )?wenn (gerade )?nichts (läuft|laeuft|spielt|abgespielt wird)|(only )?if nothing (is )?playing").find(body)?.let { m ->
            val sp = ctx.cfg.sonos.firstOrNull { body.contains(norm(it.name)) } ?: ctx.cfg.sonos.firstOrNull()
            if (sp != null) conds += Cond(CondType.SPEAKER_IDLE, sp.ip)
            body = body.replace(m.value, " ")
        }
        if (Regex("wenn (schon )?(etwas|was) (läuft|laeuft|spielt)|if something (is )?(already )?playing").containsMatchIn(body)) {
            val sp = ctx.cfg.sonos.firstOrNull { body.contains(norm(it.name)) } ?: ctx.cfg.sonos.firstOrNull()
            if (sp != null) conds += Cond(CondType.SPEAKER_PLAYING, sp.ip)
            strip("wenn (schon )?(etwas|was) (läuft|laeuft|spielt)|if something (is )?(already )?playing")
        }

        // ---------------- "alles aus" catch-all ----------------
        val actions = mutableListOf<Action>()
        if (Regex("\\balles aus\\b|\\ball off\\b|\\beverything off\\b|schalte alles aus").containsMatchIn(body)) {
            actions += Action(TargetType.HUE, "all", "set", mapOf("on" to "false"))
            ctx.cfg.sonos.forEach { actions += Action(TargetType.SONOS, it.ip, "pause") }
            ctx.cfg.tvs.forEach { actions += Action(TargetType.LG_TV, it.ip, "off") }
        } else {
            // split into segments; run all device detectors per segment (multi-device support)
            val segs = body.split(Regex("[,;]|\\bdann\\b|\\bthen\\b|\\bdanach\\b")).map { it.trim() }.filter { it.length > 1 }
            for (seg in segs) {
                detectTv(seg, ctx, actions)
                detectSonos(seg, ctx, actions)
                detectHue(seg, ctx, actions)
            }
        }

        check(actions.isNotEmpty()) {
            "Nichts Ausführbares erkannt. Beispiele:\n" +
            "• „Wohnzimmer rot und Küche blau“\n" +
            "• „Um 7:00 alle Lampen warmweiss 40% und Radio in der Küche leise“\n" +
            "• „Wenn das Badlicht angeht: Licht grün, Vogelsounds auf Bad – nur wenn nichts läuft“\n" +
            "• „Fernseher an und Netflix“"
        }

        Routine(
            name = input.trim().take(30).replaceFirstChar { it.uppercase() },
            triggers = listOf(trigger),
            variants = listOf(Variant(conditions = conds, actions = actions))
        )
    }

    private fun bestLight(ctx: Ctx, token: String?): HueLight? {
        if (token.isNullOrBlank()) return null
        val t = norm(token).trim()
        return ctx.lights.firstOrNull { norm(it.name) == t }
            ?: ctx.lights.firstOrNull { norm(it.name).contains(t) || t.contains(norm(it.name)) }
    }

    private fun volume(c: String): Int? =
        Regex("(\\d{1,3})\\s*%").find(c)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("(lautstärke|lautstaerke|volume|laut)\\s*(auf\\s*)?(\\d{1,3})").find(c)?.groupValues?.get(3)?.toIntOrNull()
            ?: when {
                Regex("\\bleise\\b|quiet|low|gedämpft|gedaempft").containsMatchIn(c) -> 15
                Regex("\\bmittel\\b|medium").containsMatchIn(c) -> 40
                Regex("\\blaut\\b|loud").containsMatchIn(c) -> 70
                else -> null
            }

    private fun brightness(c: String): Int? =
        Regex("(\\d{1,3})\\s*%").find(c)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 100)
            ?: when {
                Regex("gedimmt|dimm|dunkler|dim\\b|schummrig").containsMatchIn(c) -> 20
                Regex("\\bhalb\\b|half").containsMatchIn(c) -> 50
                Regex("\\bhell\\b|voll|bright|full").containsMatchIn(c) -> 100
                else -> null
            }

    private fun detectTv(c: String, ctx: Ctx, out: MutableList<Action>) {
        val tv = ctx.cfg.tvs.firstOrNull { c.contains(norm(it.name)) } ?: ctx.cfg.tvs.firstOrNull() ?: return
        val app = TV_APPS.firstOrNull { (keys, _) -> keys.any { c.contains(it) } }
        val link = Regex("(https?://\\S*(youtu\\.be|youtube)\\S*)").find(c)?.value
        when {
            app != null -> {
                val params = mutableMapOf("appId" to app.second)
                link?.let { params["contentId"] = it }
                out += Action(TargetType.LG_TV, tv.ip, "app", params)
            }
            Regex("fernseher|\\btv\\b|fernsehen").containsMatchIn(c) -> when {
                Regex("\\baus\\b|ausschalt|\\boff\\b").containsMatchIn(c) -> out += Action(TargetType.LG_TV, tv.ip, "off")
                Regex("stumm|mute").containsMatchIn(c) -> out += Action(TargetType.LG_TV, tv.ip, "mute")
                Regex("\\ban\\b|einschalt|\\bon\\b").containsMatchIn(c) -> out += Action(TargetType.LG_TV, tv.ip, "on")
                volume(c) != null -> out += Action(TargetType.LG_TV, tv.ip, "volume", mapOf("volume" to volume(c).toString()))
            }
        }
    }

    private fun detectSonos(c: String, ctx: Ctx, out: MutableList<Action>) {
        if (ctx.cfg.sonos.isEmpty()) return
        val named = ctx.cfg.sonos.filter { c.contains(norm(it.name)) }
        val soundish = Regex("sound|geräusch|geraeusch|musik|music|radio|spiel|abspiel|play|song|lied|spotify|" +
            "lautstärke|lautstaerke|volume|\\bleise\\b|\\blaut\\b|pause|stopp|\\bstop\\b|stumm|mute|vögel|voegel|vogel|" +
            "wal|regen|meer|natur|white ?noise|rauschen").containsMatchIn(c)
        val hasLight = Regex("licht|lampe|light|leuchte").containsMatchIn(c)
        if (named.isEmpty() && (!soundish || hasLight)) return
        val targets = named.ifEmpty { listOf(ctx.cfg.sonos.first()) }
        val vol = volume(c)
        for (sp in targets) {
            when {
                c.contains("spotify") -> {
                    val q = c.substringAfter("spotify").trim(' ', ':', '-', '.')
                    out += Action(TargetType.SONOS, sp.ip, "spotify",
                        buildMap { put("query", q.ifBlank { "Chill Mix" }); vol?.let { put("volume", it.toString()) } })
                }
                Regex("pause|stopp\\b|\\bstop\\b").containsMatchIn(c) -> out += Action(TargetType.SONOS, sp.ip, "pause")
                Regex("stumm|mute").containsMatchIn(c) -> out += Action(TargetType.SONOS, sp.ip, "mute", mapOf("on" to "true"))
                Regex("weiter|resume|fortsetz").containsMatchIn(c) -> out += Action(TargetType.SONOS, sp.ip, "play")
                Regex("https?://\\S+").containsMatchIn(c) -> out += Action(TargetType.SONOS, sp.ip, "play_uri",
                    buildMap { put("uri", Regex("https?://\\S+").find(c)!!.value); vol?.let { put("volume", it.toString()) } })
                soundish -> out += Action(TargetType.SONOS, sp.ip, "play_uri",
                    buildMap { put("uri", ""); vol?.let { put("volume", it.toString()) } })  // URL fehlt -> Editor markiert ⚠
                vol != null -> out += Action(TargetType.SONOS, sp.ip, "volume", mapOf("volume" to vol.toString()))
            }
        }
    }

    private fun detectHue(c: String, ctx: Ctx, out: MutableList<Action>) {
        val allWords = Regex("alle (lampen|lichter|leuchten)|alles licht|überall|ueberall|all lights|everywhere").containsMatchIn(c)
        val named = ctx.lights.filter { c.contains(norm(it.name)) }
        val lightish = Regex("licht|lampe|light|leuchte|beleuchtung").containsMatchIn(c)
        if (!allWords && named.isEmpty() && !lightish) return
        // ignore if this segment is clearly only about a speaker/tv and mentions no light word
        if (!allWords && named.isEmpty() && !lightish) return

        val base = mutableMapOf<String, String>()
        COLORS.firstOrNull { c.contains(it.first) }?.let { base["color"] = it.second; base["on"] = "true" }
        brightness(c)?.let { base["brightness"] = it.toString(); base["on"] = "true" }
        when {
            Regex("\\baus\\b|ausschalt|\\boff\\b|ausmachen").containsMatchIn(c) -> { base.clear(); base["on"] = "false" }
            base.isEmpty() && Regex("\\ban\\b|einschalt|\\bon\\b|anmachen").containsMatchIn(c) -> base["on"] = "true"
        }
        if (base.isEmpty()) return

        val targets: List<String> = when {
            named.isNotEmpty() && !allWords -> named.map { it.id }
            else -> listOf("all")
        }
        for (id in targets) out += Action(TargetType.HUE, id, "set", HashMap(base))
    }
}
