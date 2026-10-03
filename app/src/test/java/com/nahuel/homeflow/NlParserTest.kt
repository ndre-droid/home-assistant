package com.nahuel.homeflow

import com.nahuel.homeflow.data.Config
import com.nahuel.homeflow.data.TargetType
import com.nahuel.homeflow.data.TriggerType
import com.nahuel.homeflow.devices.HueLight
import com.nahuel.homeflow.engine.NlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NlParserTest {
    private val lights = listOf(HueLight("l1", "Wohnzimmer", on = false, supportsColor = true))
    private fun parse(s: String) = NlParser.parse(s, Config(), lights)

    @Test fun timeTriggerAndAllOff() {
        val r = parse("Um 7:30 alles aus").getOrThrow()
        assertEquals(TriggerType.TIME, r.trigger.type)
        assertEquals("07:30", r.trigger.time)
        val a = r.variants.single().actions.first()
        assertEquals(TargetType.HUE, a.target)
        assertEquals("all", a.deviceId)
        assertEquals("false", a.params["on"])
    }

    @Test fun sunsetTrigger() {
        assertEquals("SUNSET", parse("bei Sonnenuntergang alles aus").getOrThrow().trigger.sunEvent)
    }

    @Test fun leaveTrigger() {
        assertEquals(TriggerType.LEAVE_WIFI, parse("wenn ich gehe alles aus").getOrThrow().trigger.type)
    }

    @Test fun namedLightGetsColor() {
        val a = parse("Wohnzimmer rot").getOrThrow().variants.single().actions.single()
        assertEquals("l1", a.deviceId)
        assertEquals("#FF0000", a.params["color"])
    }

    @Test fun gibberishFails() {
        assertTrue(parse("qwertz uiop").isFailure)
    }
}
