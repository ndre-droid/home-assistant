package com.nahuel.homeflow

import com.nahuel.homeflow.data.*
import com.nahuel.homeflow.engine.Candidate
import com.nahuel.homeflow.engine.DeviceKind
import com.nahuel.homeflow.engine.DeviceRelocator
import com.nahuel.homeflow.engine.Match
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelocatorTest {
    private fun match(configured: List<Triple<String, String, String>>, found: List<Candidate>) =
        DeviceRelocator.match(DeviceKind.SONOS, configured, found)

    @Test fun storedIdFindsDeviceAtNewIp() {
        val r = match(listOf(Triple("Beam", "192.168.1.41", "RINCON_A")), listOf(Candidate("192.168.0.23", "RINCON_A", "Wohnzimmer")))
        assertEquals(Match.EXACT, r.single().match)
        assertEquals("192.168.0.23", r.single().target?.ip)
    }

    @Test fun sameIdSameIpIsUnchanged() {
        val r = match(listOf(Triple("Beam", "192.168.1.41", "RINCON_A")), listOf(Candidate("192.168.1.41", "RINCON_A", "Beam")))
        assertEquals(Match.SAME, r.single().match)
        assertEquals(false, r.single().changes)
    }

    @Test fun noIdYetMatchesByNameAsGuess() {
        val r = match(
            listOf(Triple("Beam", "192.168.1.41", ""), Triple("Era 100", "192.168.1.42", "")),
            listOf(Candidate("192.168.0.30", "RINCON_E", "Era 100"), Candidate("192.168.0.23", "RINCON_B", "Beam"))
        )
        assertEquals(listOf(Match.GUESS, Match.GUESS), r.map { it.match })
        assertEquals(listOf("192.168.0.23", "192.168.0.30"), r.map { it.target?.ip })
    }

    @Test fun differentDeviceAtOldIpIsNotTakenWhenIdKnown() {
        // Our speaker is gone; a stranger now holds its old IP under another id and name.
        val r = match(
            listOf(Triple("Beam", "192.168.1.41", "RINCON_A"), Triple("Era 100", "192.168.1.42", "RINCON_E")),
            listOf(Candidate("192.168.1.41", "RINCON_X", "Bad"), Candidate("192.168.1.50", "RINCON_E", "Era 100"))
        )
        assertEquals(Match.MISSING, r[0].match)
        assertNull(r[0].target)
        assertEquals(Match.EXACT, r[1].match)
    }

    @Test fun singleLeftoverIsGuessed() {
        val r = DeviceRelocator.match(DeviceKind.TV, listOf(Triple("Wohnzimmer TV", "192.168.1.50", "")),
            listOf(Candidate("192.168.0.31", "uuid-1", "[LG] webOS TV OLED55")))
        assertEquals(Match.GUESS, r.single().match)
    }

    @Test fun candidateIsClaimedOnlyOnce() {
        val r = match(
            listOf(Triple("Beam", "192.168.1.41", ""), Triple("Beam", "192.168.1.43", "")),
            listOf(Candidate("192.168.0.23", "RINCON_B", "Beam"))
        )
        assertEquals(1, r.count { it.target != null })
    }

    @Test fun ipRemapRewritesRoutinesAndConfig() {
        val map = mapOf("192.168.1.41" to "192.168.0.23", "192.168.1.50" to "192.168.0.31")
        val routine = Routine(variants = listOf(Variant(
            listOf(Cond(CondType.SPEAKER_IDLE, "192.168.1.41"), Cond(CondType.DAY)),
            listOf(
                Action(TargetType.SONOS, "192.168.1.41", "pause"),
                Action(TargetType.LG_TV, "192.168.1.50", "off"),
                Action(TargetType.SONOS, "all", "pause"),
                Action(TargetType.HUE, "light-1", "set")
            )
        )))
        val v = routine.remapDeviceIps(map).variants.single()
        assertEquals("192.168.0.23", v.conditions[0].deviceId)
        assertEquals(listOf("192.168.0.23", "192.168.0.31", "all", "light-1"), v.actions.map { it.deviceId })

        val cfg = Config(
            sonos = listOf(SonosSpeaker("Beam", "192.168.1.41")),
            tvs = listOf(LgTv("TV", "192.168.1.50", "key", "aa:bb")),
            biasTv = "192.168.1.50",
            deviceRooms = mapOf("192.168.1.41" to "room1")
        ).remapDeviceIps(map)
        assertEquals("192.168.0.23", cfg.sonos.single().ip)
        assertEquals("192.168.0.31", cfg.tvs.single().ip)
        assertEquals("key", cfg.tvs.single().clientKey)
        assertEquals("192.168.0.31", cfg.biasTv)
        assertEquals(mapOf("192.168.0.23" to "room1"), cfg.deviceRooms)
    }

    @Test fun httpDeviceFollowsMovedHost() {
        val cfg = Config(generics = listOf(
            GenericDevice("Bridge", "https://192.168.178.26/api/x/groups/0/action", "PUT"),
            GenericDevice("Shelly", "http://192.168.178.90/relay/0?turn=off")
        )).remapDeviceIps(mapOf("192.168.178.26" to "192.168.2.40"))
        assertEquals("https://192.168.2.40/api/x/groups/0/action", cfg.generics[0].url)
        assertEquals("http://192.168.178.90/relay/0?turn=off", cfg.generics[1].url)
    }

    @Test fun swappedIpsStayCorrect() {
        val map = mapOf("10.0.0.5" to "10.0.0.6", "10.0.0.6" to "10.0.0.5")
        val cfg = Config(sonos = listOf(SonosSpeaker("A", "10.0.0.5"), SonosSpeaker("B", "10.0.0.6"))).remapDeviceIps(map)
        assertEquals(listOf("10.0.0.6", "10.0.0.5"), cfg.sonos.map { it.ip })
    }

    @Test fun stableIdsRoundTrip() {
        val c = Config(hueBridgeId = "001788fffe123456",
            sonos = listOf(SonosSpeaker("Beam", "192.168.1.41", "RINCON_A")),
            tvs = listOf(LgTv("TV", "192.168.1.50", "k", "m", "uuid-1")))
        assertEquals(c, Config.fromJson(org.json.JSONObject(c.toJson().toString())))
    }
}
