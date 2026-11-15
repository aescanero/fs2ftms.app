package io.github.aescanero.fs2ftms.ble.central

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

data class LogEntry(val timestampMs: Long, val source: String, val text: String)

/** Registro de eventos de conexión para la capa de depuración. */
class EventLog(private val maxEntries: Int = 1000) {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun add(source: String, text: String) {
        val entry = LogEntry(System.currentTimeMillis(), source, text)
        _entries.update { (it + entry).takeLast(maxEntries) }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun toJson(extra: Map<String, Any?> = emptyMap()): String {
        val root = JSONObject()
        extra.forEach { (k, v) -> root.put(k, v ?: JSONObject.NULL) }
        val array = JSONArray()
        _entries.value.forEach { e ->
            array.put(JSONObject().put("t", e.timestampMs).put("source", e.source).put("text", e.text))
        }
        root.put("events", array)
        return root.toString(2)
    }
}
