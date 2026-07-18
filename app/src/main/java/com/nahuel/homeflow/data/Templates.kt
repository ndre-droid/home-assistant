package com.nahuel.homeflow.data

/**
 * Pre-built routine templates. Each builds a Routine the user can then tweak.
 * Device ids are placeholders ("all" for Hue) so they work before specific devices are picked.
 */
object Templates {
    data class Template(val title: String, val description: String, val build: () -> Routine)

    val all: List<Template> = listOf(
        Template("Filmabend", "Licht dimmt warm, TV an") {
            Routine(
                name = "Filmabend",
                triggers = listOf(Trigger(TriggerType.MANUAL)),
                variants = listOf(Variant(actions = listOf(
                    Action(TargetType.HUE, "all", "set", mapOf("on" to "true", "brightness" to "20", "color" to "#B5651D"))
                )))
            )
        },
        Template("Aufwachen", "Licht wird über 20 Min heller (Lichtwecker)") {
            Routine(
                name = "Aufwachen",
                triggers = listOf(Trigger(TriggerType.MANUAL)),
                variants = listOf(Variant(actions = listOf(
                    Action(TargetType.HUE, "all", "wakeup", mapOf("minutes" to "20"))
                )))
            )
        },
        Template("Gute Nacht / Alles aus", "Schaltet alles aus") {
            Routine(
                name = "Gute Nacht",
                triggers = listOf(Trigger(TriggerType.MANUAL)),
                variants = listOf(Variant(actions = listOf(
                    Action(TargetType.HUE, "all", "set", mapOf("on" to "false"))
                )))
            )
        },
        Template("Party: Rave", "Schnelles Farb-Feuerwerk, 60 Sek") {
            partyRoutine("Rave", "rave", 60)
        },
        Template("Party: Chill", "Langsame warme Fades") {
            partyRoutine("Chill", "chill", 120)
        },
        Template("Party: Strobe", "Weißes Blitzlicht, 30 Sek") {
            partyRoutine("Strobe", "strobe", 30)
        },
        Template("Party: Sunset", "Warmer Farbverlauf") {
            partyRoutine("Sunset", "sunset", 120)
        },
        Template("Party: Ocean", "Blau-Teal Wellen") {
            partyRoutine("Ocean", "ocean", 120)
        },
        Template("Rage Quit", "Alles aus, Licht rot") {
            Routine(
                name = "Rage Quit",
                triggers = listOf(Trigger(TriggerType.MANUAL)),
                variants = listOf(Variant(actions = listOf(
                    Action(TargetType.HUE, "all", "set", mapOf("on" to "true", "brightness" to "60", "color" to "#FF0000"))
                )))
            )
        },
        Template("Fokus", "Helles Neutralweiß zum Arbeiten") {
            Routine(
                name = "Fokus",
                triggers = listOf(Trigger(TriggerType.MANUAL)),
                variants = listOf(Variant(actions = listOf(
                    Action(TargetType.HUE, "all", "set", mapOf("on" to "true", "brightness" to "100", "color" to "#FFE8C0"))
                )))
            )
        },
        Template("Romantik", "Warmes, gedimmtes Licht") {
            Routine(
                name = "Romantik",
                triggers = listOf(Trigger(TriggerType.MANUAL)),
                variants = listOf(Variant(actions = listOf(
                    Action(TargetType.HUE, "all", "set", mapOf("on" to "true", "brightness" to "22", "color" to "#FF6B6B"))
                )))
            )
        },
        Template("Gäste kommen", "Freundlich hell, warmweiß") {
            Routine(
                name = "Gäste kommen",
                triggers = listOf(Trigger(TriggerType.MANUAL)),
                variants = listOf(Variant(actions = listOf(
                    Action(TargetType.HUE, "all", "set", mapOf("on" to "true", "brightness" to "80", "color" to "#FFD9A0"))
                )))
            )
        },
        Template("Schlummerlicht", "Licht geht nach 15 Min von selbst aus") {
            Routine(
                name = "Schlummerlicht",
                triggers = listOf(Trigger(TriggerType.MANUAL)),
                variants = listOf(Variant(actions = listOf(
                    Action(TargetType.HUE, "all", "set", mapOf("on" to "true", "brightness" to "15", "color" to "#FF8C42")),
                    Action(TargetType.HUE, "all", "countdown_off", mapOf("minutes" to "15"))
                )))
            )
        },
        Template("Verlassen", "Alles aus beim Gehen (WLAN verlassen)") {
            Routine(
                name = "Verlassen",
                triggers = listOf(Trigger(TriggerType.LEAVE_WIFI)),
                variants = listOf(Variant(actions = listOf(
                    Action(TargetType.HUE, "all", "set", mapOf("on" to "false"))
                )))
            )
        }
    )

    private fun partyRoutine(name: String, mode: String, seconds: Int) = Routine(
        name = name,
        triggers = listOf(Trigger(TriggerType.MANUAL)),
        variants = listOf(Variant(actions = listOf(
            Action(TargetType.HUE, "all", "party", mapOf("mode" to mode, "seconds" to seconds.toString()))
        )))
    )
}
