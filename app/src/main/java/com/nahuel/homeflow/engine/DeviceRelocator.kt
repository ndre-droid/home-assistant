package com.nahuel.homeflow.engine

import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.devices.HueClient
import com.nahuel.homeflow.devices.LanSweep
import com.nahuel.homeflow.devices.LgTvClient
import com.nahuel.homeflow.devices.SonosClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import java.io.IOException

enum class DeviceKind { HUE, SONOS, TV }

/**
 * How sure a match is. SAME/EXACT are proven (stable hardware id or accepted Hue key) and may be
 * applied silently; GUESS (matched by name) needs the user's OK; MISSING found nothing.
 */
enum class Match { SAME, EXACT, GUESS, MISSING }

/** A device seen on the LAN right now. */
data class Candidate(val ip: String, val id: String, val name: String)

/** One configured device and where it is now. [target] null = leave unchanged. */
data class Relocation(
    val kind: DeviceKind,
    val name: String,
    val oldIp: String,
    val oldId: String,
    val target: Candidate?,
    val match: Match,
    val options: List<Candidate> = emptyList()   // same-kind devices on the LAN, for a manual pick
) {
    val moves: Boolean get() = target != null && target.ip != oldIp
    /** Something worth saving: new IP, or a stable id we didn't have yet. */
    val changes: Boolean get() = target != null && (moves || (target.id.isNotEmpty() && target.id != oldId))
}

/**
 * Finds configured devices again after their IPs changed (new router, move, DHCP reshuffle).
 * Devices are re-identified by stable ids, never by IP, and every IP reference in config and
 * routines is rewritten in one transaction (see Store.relocateDevices).
 */
object DeviceRelocator {

    /**
     * Scans the LAN and matches every configured device. Read-only.
     * Fast pass: SSDP (+ Hue cloud). Anything still missing -> TCP sweep of the phone's /24,
     * which also finds devices whose multicast answers are filtered or switched off.
     */
    suspend fun scan(): List<Relocation> = coroutineScope {
        val cfg = Store.config.value
        val sonosConf = cfg.sonos.map { Triple(it.name, it.ip, it.id) }
        val tvConf = cfg.tvs.map { Triple(it.name, it.ip, it.id) }
        val hueA = async { if (cfg.hueAppKey.isNotEmpty()) locateHue(cfg.hueBridgeIp, cfg.hueBridgeId, cfg.hueAppKey) else null }
        val sonosA = async { if (sonosConf.isEmpty()) emptyList() else SonosClient.discover().map { Candidate(it.ip, it.id, it.name) } }
        val tvA = async { if (tvConf.isEmpty()) emptyList() else LgTvClient.discover().map { Candidate(it.ip, it.id, it.name) } }
        var hue = hueA.await()
        val sonosFound = sonosA.await().toMutableList()
        val tvFound = tvA.await().toMutableList()
        var sonos = match(DeviceKind.SONOS, sonosConf, sonosFound)
        var tvs = match(DeviceKind.TV, tvConf, tvFound)

        val ports = buildSet {
            if (hue?.match == Match.MISSING) add(443)
            if (sonos.any { it.match == Match.MISSING }) add(1400)
            if (tvs.any { it.match == Match.MISSING }) add(3001)
        }
        if (ports.isNotEmpty()) {
            val open = LanSweep.openPorts(ports)
            val missingHue = hue?.takeIf { it.match == Match.MISSING }
            if (missingHue != null) {
                for (ip in open[443].orEmpty()) {
                    val id = HueClient.verifiedBridgeId(ip, cfg.hueAppKey) ?: continue
                    hue = missingHue.copy(target = Candidate(ip, id, "Hue Bridge"), match = if (ip == missingHue.oldIp) Match.SAME else Match.EXACT)
                    break
                }
            }
            open[1400].orEmpty().filter { ip -> sonosFound.none { it.ip == ip } }
                .mapNotNull { SonosClient.describe(it) }
                .forEach { sonosFound += Candidate(it.ip, it.id, it.name) }
            // No id without SSDP: a TV found this way is only ever a GUESS for the user to confirm.
            open[3001].orEmpty().filter { ip -> tvFound.none { it.ip == ip } }
                .forEach { tvFound += Candidate(it, "", "LG TV $it") }
            sonos = match(DeviceKind.SONOS, sonosConf, sonosFound)
            tvs = match(DeviceKind.TV, tvConf, tvFound)
        }
        listOfNotNull(hue) + sonos + tvs
    }

    /** The Hue app key itself proves identity: a bridge that accepts it is ours. */
    private suspend fun locateHue(ip: String, id: String, key: String): Relocation {
        fun rel(target: Candidate?, m: Match) = Relocation(DeviceKind.HUE, "Hue Bridge", ip, id, target, m)
        HueClient.verifiedBridgeId(ip, key)?.let { found ->
            if (id.isEmpty() || found == id) return rel(Candidate(ip, found, "Hue Bridge"), Match.SAME)
        }
        val bridges = HueClient.discoverBridges().sortedByDescending { it.id == id }
        for (b in bridges) {
            if (b.ip == ip) continue
            HueClient.verifiedBridgeId(b.ip, key)?.let { return rel(Candidate(b.ip, it, "Hue Bridge"), Match.EXACT) }
        }
        return rel(null, Match.MISSING)
    }

    /**
     * Pure matcher. [configured] = (name, ip, storedId). Order of evidence:
     * stored id -> same IP (only when no id is stored yet) -> same name (also catches a replaced
     * device in the same room) -> the single leftover (only when no id is stored yet).
     * Each LAN device is claimed at most once.
     */
    fun match(kind: DeviceKind, configured: List<Triple<String, String, String>>, found: List<Candidate>): List<Relocation> {
        val used = mutableSetOf<String>()                  // claimed candidate ips
        val result = arrayOfNulls<Relocation>(configured.size)
        fun claim(i: Int, c: Candidate, m: Match) {
            val (name, ip, id) = configured[i]
            used += c.ip
            result[i] = Relocation(kind, name, ip, id, c, if (m == Match.EXACT && c.ip == ip) Match.SAME else m, found)
        }
        fun free() = found.filter { it.ip !in used }

        configured.forEachIndexed { i, (_, _, id) ->
            if (id.isNotEmpty()) found.firstOrNull { it.id == id }?.let { claim(i, it, Match.EXACT) }
        }
        configured.forEachIndexed { i, (_, ip, id) ->
            if (result[i] == null && id.isEmpty()) free().firstOrNull { it.ip == ip }?.let { claim(i, it, Match.SAME) }
        }
        configured.forEachIndexed { i, (name, _, _) ->
            if (result[i] == null) free().firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { claim(i, it, Match.GUESS) }
        }
        // Leftover guess only without a stored id: with one, an unknown id means a different device.
        val left = configured.indices.filter { result[it] == null && configured[it].third.isEmpty() }
        if (left.size == 1 && free().size == 1) claim(left.single(), free().single(), Match.GUESS)

        return configured.mapIndexed { i, (name, ip, id) ->
            result[i] ?: Relocation(kind, name, ip, id, null, Match.MISSING, found)
        }
    }

    /** Saves the given relocations (target set = apply). Returns old IP -> new IP. */
    fun apply(items: List<Relocation>): Map<String, String> {
        val chosen = items.filter { it.changes }
        if (chosen.isEmpty()) return emptyMap()
        val ipMap = chosen.filter { it.kind != DeviceKind.HUE && it.moves }.associate { it.oldIp to it.target!!.ip }
        fun idsByNewIp(kind: DeviceKind) = chosen.filter { it.kind == kind && it.target!!.id.isNotEmpty() }
            .associate { it.target!!.ip to it.target.id }
        val sonosIds = idsByNewIp(DeviceKind.SONOS)
        val tvIds = idsByNewIp(DeviceKind.TV)
        val hue = chosen.firstOrNull { it.kind == DeviceKind.HUE }?.target
        Store.relocateDevices(ipMap) { c ->
            c.copy(
                hueBridgeIp = hue?.ip ?: c.hueBridgeIp,
                hueBridgeId = hue?.id?.ifEmpty { null } ?: c.hueBridgeId,
                sonos = c.sonos.map { s -> sonosIds[s.ip]?.let { s.copy(id = it) } ?: s },
                tvs = c.tvs.map { t -> tvIds[t.ip]?.let { t.copy(id = it) } ?: t }
            )
        }
        val hueMove = chosen.firstOrNull { it.kind == DeviceKind.HUE && it.moves }
        return if (hueMove != null) ipMap + (hueMove.oldIp to hueMove.target!!.ip) else ipMap
    }

    // ---------- Auto-heal ----------

    private val healLock = Mutex()
    @Volatile private var lastHealAt = 0L
    private const val HEAL_GAP_MS = 60_000L

    /**
     * Silent repair: applies only proven matches (SAME/EXACT), never guesses. Rate-limited and
     * single-flight. Returns old IP -> new IP if a device moved, else null.
     */
    suspend fun heal(): Map<String, String>? {
        val cfg = Store.config.value
        if (cfg.hueAppKey.isEmpty() && cfg.sonos.isEmpty() && cfg.tvs.isEmpty()) return null
        if (!healLock.tryLock()) return null
        try {
            val now = System.currentTimeMillis()
            if (now - lastHealAt < HEAL_GAP_MS) return null
            lastHealAt = now
            val proven = scan().filter { it.match == Match.SAME || it.match == Match.EXACT }
            return apply(proven).ifEmpty { null }
        } finally {
            healLock.unlock()
        }
    }

    /** Network-level failure (device not at that address) - as opposed to a device-side error. */
    fun isUnreachable(t: Throwable?): Boolean =
        generateSequence(t) { it.cause }.take(5).any { it is IOException }
}
