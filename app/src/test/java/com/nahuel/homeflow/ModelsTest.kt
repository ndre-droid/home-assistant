package com.nahuel.homeflow

import com.nahuel.homeflow.data.*
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {
    @Test fun routineRoundTrips() {
        val r = Routine(
            name = "Abend",
            triggers = listOf(Trigger(TriggerType.SUN, sunEvent = "SUNSET", sunOffsetMin = -15)),
            variants = listOf(
                Variant(listOf(Cond(CondType.NIGHT)), listOf(Action(TargetType.HUE, "all", "set", mapOf("on" to "true", "brightness" to "40")))),
                Variant(emptyList(), listOf(Action(TargetType.SONOS, "192.168.1.5", "pause")))
            ),
            icon = "🌙"
        )
        assertEquals(r, Routine.fromJson(JSONObject(r.toJson().toString())))
    }

    @Test fun configRoundTripsNewFields() {
        val c = Config(hueBridgeIp = "192.168.1.2", webToken = "abc", homeWifiSsid = "Heim", tileRoutineId = "r1",
            sonos = listOf(SonosSpeaker("Küche", "192.168.1.5")), deviceRooms = mapOf("192.168.1.5" to "room1"))
        assertEquals(c, Config.fromJson(JSONObject(c.toJson().toString())))
    }

    @Test fun oldSingleTriggerFormatMigrates() {
        val old = JSONObject("""{"id":"x","name":"Alt","trigger":{"type":"NFC"},"variants":[{"condition":"DAY","actions":[]}]}""")
        val r = Routine.fromJson(old)
        assertEquals(TriggerType.NFC, r.trigger.type)
        assertEquals(listOf(Cond(CondType.DAY)), r.variants.single().conditions)
    }

    @Test fun unknownEnumsFallBackInsteadOfCrashing() {
        val r = Routine.fromJson(JSONObject("""{"id":"y","triggers":[{"type":"TELEPATHY"}],"variants":[]}"""))
        assertEquals(TriggerType.MANUAL, r.trigger.type)
    }
}
