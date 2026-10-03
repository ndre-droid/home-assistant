package com.nahuel.homeflow

import com.nahuel.homeflow.data.Trigger
import com.nahuel.homeflow.data.TriggerType
import com.nahuel.homeflow.engine.AlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AlarmSchedulerTest {
    private val berlin = 52.52 to 13.405

    @Test fun timeTriggerIsNextOccurrenceWithin24h() {
        val now = System.currentTimeMillis()
        val at = AlarmScheduler.nextFireMillis(TriggerType.TIME, Trigger(TriggerType.TIME, time = "06:45"), berlin.first, berlin.second)!!
        assertTrue(at > now && at <= now + 24 * 3600_000L)
        val c = Calendar.getInstance().apply { timeInMillis = at }
        assertEquals(6, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, c.get(Calendar.MINUTE))
    }

    @Test fun sunTriggersAreInTheFutureForBerlin() {
        val now = System.currentTimeMillis()
        listOf("SUNRISE", "SUNSET").forEach { ev ->
            val at = AlarmScheduler.nextFireMillis(TriggerType.SUN, Trigger(TriggerType.SUN, sunEvent = ev), berlin.first, berlin.second)
            assertNotNull(ev, at)
            assertTrue(ev, at!! > now && at <= now + 48 * 3600_000L)
        }
    }

    @Test fun polarNightHasNoSunrise() {
        // Longyearbyen in deep winter: no sunrise -> null instead of a bogus time. Only meaningful Nov-Jan.
        val month = Calendar.getInstance().get(Calendar.MONTH)
        if (month != Calendar.DECEMBER) return
        val at = AlarmScheduler.nextFireMillis(TriggerType.SUN, Trigger(TriggerType.SUN, sunEvent = "SUNRISE"), 78.22, 15.65)
        assertEquals(null, at)
    }
}
