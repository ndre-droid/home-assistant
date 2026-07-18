package com.nahuel.homeflow.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Single source of truth, persisted as JSON in app-private storage.
 * Deliberately no DB: the data set is tiny and JSON keeps the build dependency-free.
 */
object Store {
    private lateinit var routinesFile: File
    private lateinit var configFile: File

    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    val routines: StateFlow<List<Routine>> = _routines

    private val _config = MutableStateFlow(Config())
    val config: StateFlow<Config> = _config

    private val _history = MutableStateFlow<List<RunLog>>(emptyList())
    val history: StateFlow<List<RunLog>> = _history
    private lateinit var historyFile: File

    fun init(ctx: Context) {
        routinesFile = File(ctx.filesDir, "routines.json")
        configFile = File(ctx.filesDir, "config.json")
        historyFile = File(ctx.filesDir, "history.json")
        runCatching {
            if (historyFile.exists()) {
                val arr = JSONArray(historyFile.readText())
                _history.value = (0 until arr.length()).map { val o = arr.getJSONObject(it)
                    RunLog(o.optString("routineName"), o.optLong("timestamp"), o.optBoolean("ok"), o.optString("detail")) }
            }
        }
        runCatching {
            if (configFile.exists()) _config.value = Config.fromJson(JSONObject(configFile.readText()))
        }
        runCatching {
            if (routinesFile.exists()) {
                val arr = JSONArray(routinesFile.readText())
                _routines.value = (0 until arr.length()).map { Routine.fromJson(arr.getJSONObject(it)) }
            }
        }
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

    fun routine(id: String): Routine? = _routines.value.firstOrNull { it.id == id }

    @Synchronized
    fun updateConfig(block: (Config) -> Config) {
        _config.value = block(_config.value)
        runCatching { configFile.writeText(_config.value.toJson().toString()) }
    }

    private fun persistRoutines() {
        runCatching {
            val arr = JSONArray(); _routines.value.forEach { arr.put(it.toJson()) }
            routinesFile.writeText(arr.toString())
        }
    }

    /** True while local time is inside [dayStart, nightStart). */
    fun logRun(name: String, ok: Boolean, detail: String = "") {
        val entry = RunLog(name, System.currentTimeMillis(), ok, detail)
        _history.value = (listOf(entry) + _history.value).take(100)  // keep last 100
        runCatching {
            val arr = JSONArray()
            _history.value.forEach { arr.put(JSONObject()
                .put("routineName", it.routineName).put("timestamp", it.timestamp)
                .put("ok", it.ok).put("detail", it.detail)) }
            historyFile.writeText(arr.toString())
        }
    }

    fun clearHistory() { _history.value = emptyList(); runCatching { historyFile.writeText("[]") } }

    fun isDaytime(): Boolean {
        val c = _config.value
        val now = java.util.Calendar.getInstance()
        val minutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        fun parse(s: String): Int {
            val p = s.split(":"); return (p.getOrNull(0)?.toIntOrNull() ?: 7) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
        }
        return minutes >= parse(c.dayStart) && minutes < parse(c.nightStart)
    }
}
