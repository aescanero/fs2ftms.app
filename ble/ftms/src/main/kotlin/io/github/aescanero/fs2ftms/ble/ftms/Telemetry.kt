package io.github.aescanero.fs2ftms.ble.ftms

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

enum class TelemetryType { SERVER, ADVERTISING, CONNECTION, MTU, PHY, READ, SUBSCRIBE, CONTROL_POINT, NOTIFY, ERROR }

data class TelemetryEvent(
    val timestampMs: Long,
    val type: TelemetryType,
    val device: String?,
    val detail: String,
)

/** Lo que el servidor GATT observa del cliente (el reloj) durante una sesión. */
data class TelemetrySnapshot(
    val running: Boolean = false,
    val advertising: Boolean = false,
    val startedAtMs: Long = 0,
    val connectedDevices: Set<String> = emptySet(),
    val connections: Int = 0,
    val disconnections: Int = 0,
    val mtu: Map<String, Int> = emptyMap(),
    val subscriptions: Map<String, Set<String>> = emptyMap(),
    val firstSubscriptionDelayMs: Long? = null,
    val reads: Int = 0,
    val controlPointWrites: Int = 0,
    val notificationsSent: Long = 0,
    val notificationsConfirmed: Long = 0,
    val notificationsFailed: Long = 0,
    val notificationsDropped: Long = 0,
    val events: List<TelemetryEvent> = emptyList(),
)

class TelemetryRecorder(private val maxEvents: Int = 2000) {
    private val _state = MutableStateFlow(TelemetrySnapshot())
    val state: StateFlow<TelemetrySnapshot> = _state.asStateFlow()

    fun reset() {
        _state.value = TelemetrySnapshot()
    }

    fun update(transform: (TelemetrySnapshot) -> TelemetrySnapshot) {
        _state.update(transform)
    }

    fun event(type: TelemetryType, device: String?, detail: String) {
        val event = TelemetryEvent(System.currentTimeMillis(), type, device, detail)
        _state.update { it.copy(events = (it.events + event).takeLast(maxEvents)) }
    }

    fun toJson(extra: Map<String, Any?> = emptyMap()): String {
        val s = _state.value
        val root = JSONObject()
        extra.forEach { (k, v) -> root.put(k, v ?: JSONObject.NULL) }
        root.put("running", s.running)
        root.put("advertising", s.advertising)
        root.put("startedAtMs", s.startedAtMs)
        root.put("connections", s.connections)
        root.put("disconnections", s.disconnections)
        root.put("connectedDevices", JSONArray(s.connectedDevices.toList()))
        root.put("mtu", JSONObject(s.mtu as Map<*, *>))
        val subscriptions = JSONObject()
        s.subscriptions.forEach { (device, set) -> subscriptions.put(device, JSONArray(set.toList())) }
        root.put("subscriptions", subscriptions)
        root.put("firstSubscriptionDelayMs", s.firstSubscriptionDelayMs ?: JSONObject.NULL)
        root.put("reads", s.reads)
        root.put("controlPointWrites", s.controlPointWrites)
        root.put("notificationsSent", s.notificationsSent)
        root.put("notificationsConfirmed", s.notificationsConfirmed)
        root.put("notificationsFailed", s.notificationsFailed)
        root.put("notificationsDropped", s.notificationsDropped)
        val events = JSONArray()
        s.events.forEach { e ->
            events.put(
                JSONObject()
                    .put("t", e.timestampMs)
                    .put("type", e.type.name)
                    .put("device", e.device ?: JSONObject.NULL)
                    .put("detail", e.detail),
            )
        }
        root.put("events", events)
        return root.toString(2)
    }
}
