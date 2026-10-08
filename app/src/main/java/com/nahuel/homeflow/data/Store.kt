package com.nahuel.homeflow.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

/**
 * Single source of truth, persisted as JSON in app-private storage.
 * Deliberately no DB: the data set is tiny and JSON keeps the build dependency-free.
 *
 * Writes go through AtomicFile (write-to-temp + rename) so a crash mid-save can never
 * leave a half-written file. A file that fails to parse is copied aside as
 * `<name>.corrupt-<time>` before anything can overwrite it, so data is recoverable.
 */
object Store {
    private lateinit var routinesFile: File
    private lateinit var configFile: File
    private lateinit var historyFile: File

    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    val routines: StateFlow<List<Routine>> = _routines

    private val _config = MutableStateFlow(Config())
    val config: StateFlow<Config> = _config

    private val _history = MutableStateFlow<List<RunLog>>(emptyList())
    val history: StateFlow<List<RunLog>> = _history

    /** Idempotent: receivers call this too, but only the first call reads from disk. */
    @Synchronized
    fun init(ctx: Context) {
        if (::routinesFile.isInitialized) return
        routinesFile = File(ctx.filesDir, "routines.json")
        configFile = File(ctx.filesDir, "config.json")
        historyFile = File(ctx.filesDir, "history.json")
        load(historyFile) { text ->
            val arr = JSONArray(text)
            _history.value = (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val o = arr.getJSONObject(i)
                    RunLog(o.optString("routineName"), o.optLong("timestamp"), o.optBoolean("ok"), o.optString("detail"))
                }.getOrNull()
            }
        }
        load(configFile) { _config.value = Config.fromJson(JSONObject(it)) }
        load(routinesFile) { _routines.value = parseRoutines(JSONArray(it)) }
    }

    @Synchronized
    fun saveRoutine(r: Routine) {
        val list = _routines.value.toMutableList()
        val idx = list.indexOfFirst { it.id == r.id }
        if (idx >= 0) list[idx] = r else list.add(r)
        _routines.value = list
        persistRoutines()
    }

    @Synchronized
    fun deleteRoutine(id: String) {
        _routines.value = _routines.value.filterNot { it.id == id }
        persistRoutines()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        _routines.value.firstOrNull { it.id == id }?.let { saveRoutine(it.copy(enabled = enabled)) }
    }

    @Synchronized
    fun moveRoutine(from: Int, to: Int) {
        val l = _routines.value.toMutableList()
        if (from !in l.indices || to !in l.indices || from == to) return
        val item = l.removeAt(from); l.add(to, item)
        _routines.value = l
        persistRoutines()
    }

    @Synchronized
    fun setRoutineOrder(orderedIds: List<String>) {
        val byId = _routines.value.associateBy { it.id }
        val reordered = orderedIds.mapNotNull { byId[it] } +
            _routines.value.filter { it.id !in orderedIds }
        _routines.value = reordered
        persistRoutines()
    }

    fun routine(id: String): Routine? = _routines.value.firstOrNull { it.id == id }

    @Synchronized
    fun updateConfig(block: (Config) -> Config) {
        _config.value = block(_config.value)
        write(configFile, _config.value.toJson().toString())
    }

    /** Device IPs changed (new router, DHCP): config + every routine reference in one go. */
    @Synchronized
    fun relocateDevices(ipMap: Map<String, String>, block: (Config) -> Config) {
        updateConfig { block(it.remapDeviceIps(ipMap)) }
        if (ipMap.isEmpty()) return
        _routines.value = _routines.value.map { it.remapDeviceIps(ipMap) }
        persistRoutines()
    }

    private fun persistRoutines() {
        val arr = JSONArray(); _routines.value.forEach { arr.put(it.toJson()) }
        write(routinesFile, arr.toString())
    }

    @Synchronized
    fun logRun(name: String, ok: Boolean, detail: String = "") {
        val entry = RunLog(name, System.currentTimeMillis(), ok, detail)
        _history.value = (listOf(entry) + _history.value).take(100)  // keep last 100
        val arr = JSONArray()
        _history.value.forEach { arr.put(JSONObject()
            .put("routineName", it.routineName).put("timestamp", it.timestamp)
            .put("ok", it.ok).put("detail", it.detail)) }
        write(historyFile, arr.toString())
    }

    @Synchronized
    fun clearHistory() { _history.value = emptyList(); write(historyFile, "[]") }

    /** True while local time is inside [dayStart, nightStart). */
    fun isDaytime(): Boolean {
        val c = _config.value
        val now = java.util.Calendar.getInstance()
        val minutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        fun parse(s: String): Int {
            val p = s.split(":"); return (p.getOrNull(0)?.toIntOrNull() ?: 7) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
        }
        return minutes >= parse(c.dayStart) && minutes < parse(c.nightStart)
    }

    // ---------- Backup ----------

    private const val BACKUP_FORMAT = "smartflow-backup"

    /** Everything needed to restore the app on a new install. Contains device keys/tokens. */
    fun exportJson(): String = JSONObject()
        .put("format", BACKUP_FORMAT)
        .put("version", 1)
        .put("exportedAt", System.currentTimeMillis())
        .put("config", _config.value.toJson())
        .put("routines", JSONArray().also { a -> _routines.value.forEach { a.put(it.toJson()) } })
        .toString(2)

    /** Replaces routines + config with the backup's. Validates fully before touching anything. */
    @Synchronized
    fun importJson(text: String): Result<Int> = runCatching {
        val o = JSONObject(text)
        require(o.optString("format") == BACKUP_FORMAT) { "Keine SmartFlow-Sicherung" }
        val cfg = Config.fromJson(o.getJSONObject("config"))
        val list = parseRoutines(o.getJSONArray("routines"))
        _config.value = cfg
        write(configFile, cfg.toJson().toString())
        _routines.value = list
        persistRoutines()
        list.size
    }

    // ---------- File helpers ----------

    /** One broken routine must not take all others down with it. */
    private fun parseRoutines(arr: JSONArray): List<Routine> =
        (0 until arr.length()).mapNotNull { i -> runCatching { Routine.fromJson(arr.getJSONObject(i)) }.getOrNull() }

    private fun load(f: File, parse: (String) -> Unit) {
        val text = try {
            String(AtomicFile(f).readFully(), Charsets.UTF_8)
        } catch (_: FileNotFoundException) {
            return   // first start
        } catch (e: Exception) {
            quarantine(f); return
        }
        runCatching { parse(text) }.onFailure { quarantine(f) }
    }

    private fun quarantine(f: File) {
        runCatching { f.copyTo(File(f.parentFile, "${f.name}.corrupt-${System.currentTimeMillis()}"), overwrite = true) }
    }

    private fun write(f: File, text: String) {
        val af = AtomicFile(f)
        val out = runCatching { af.startWrite() }.getOrNull() ?: return
        try {
            out.write(text.toByteArray(Charsets.UTF_8))
            af.finishWrite(out)
        } catch (e: Exception) {
            af.failWrite(out)
        }
    }
}
