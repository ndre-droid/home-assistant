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
        Template("Party", "Lichter pulsieren in Farbe, 60 Sek") {
            Routine(
                name = "Party",
                triggers = listOf(Trigger(TriggerType.MANUAL)),
                variants = listOf(Variant(actions = listOf(
                    Action(TargetType.HUE, "all", "party", mapOf("seconds" to "60"))
                )))
            )
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
}
