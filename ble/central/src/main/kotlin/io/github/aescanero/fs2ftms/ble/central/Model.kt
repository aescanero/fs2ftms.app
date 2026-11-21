package io.github.aescanero.fs2ftms.ble.central

import org.json.JSONArray
import org.json.JSONObject

data class ScanEntry(
    val address: String,
    val name: String?,
    val rssi: Int,
    val connectable: Boolean,
    val serviceUuids: List<String>,
    val serviceData: Map<String, String>,
    val manufacturerData: Map<String, String>,
    val rawHex: String,
) {
    val hasFtms: Boolean get() = "0x1826" in serviceUuids || "0x1826" in serviceData
    val hasFitShow: Boolean get() = "0xFFF0" in serviceUuids
}

data class ScanState(
    val scanning: Boolean = false,
    val entries: List<ScanEntry> = emptyList(),
    val error: String? = null,
)

data class DescriptorDump(val uuid: String, val value: String?)

data class CharacteristicDump(
    val uuid: String,
    val properties: List<String>,
    val value: String?,
    val error: String?,
    val descriptors: List<DescriptorDump>,
)

data class ServiceDump(val uuid: String, val primary: Boolean, val characteristics: List<CharacteristicDump>)

data class NotificationDump(val offsetMs: Long, val uuid: String, val value: String)

/** Volcado completo de un periférico: anuncio, árbol GATT, valores legibles y notificaciones. */
data class GattDump(
    val scan: ScanEntry?,
    val address: String,
    val mtu: Int?,
    val services: List<ServiceDump>,
    val notifications: List<NotificationDump>,
    val log: List<String>,
    val error: String?,
) {
    fun toText(): String = buildString {
        appendLine("${scan?.name ?: "(unnamed)"}  $address")
        scan?.let {
            appendLine("Advertisement: ${it.rawHex}")
            if (it.serviceUuids.isNotEmpty()) appendLine("Advertised services: ${it.serviceUuids.joinToString()}")
            it.serviceData.forEach { (k, v) -> appendLine("Service data $k: $v") }
            it.manufacturerData.forEach { (k, v) -> appendLine("Manufacturer $k: $v") }
        }
        appendLine("MTU: ${mtu ?: "not negotiated"}")
        error?.let { appendLine("ERROR: $it") }
        services.forEach { service ->
            appendLine("■ ${service.uuid}")
            service.characteristics.forEach { c ->
                appendLine("  ├ ${c.uuid} [${c.properties.joinToString(",")}]")
                c.value?.let { appendLine("  │   value: ${it.ifEmpty { "(empty)" }}") }
                c.error?.let { appendLine("  │   read: $it") }
                c.descriptors.forEach { d -> appendLine("  │   desc ${d.uuid}: ${d.value ?: "—"}") }
            }
        }
        appendLine("Notifications received: ${notifications.size}")
        notifications.groupBy { it.uuid }.forEach { (uuid, list) ->
            appendLine("  $uuid ×${list.size}, last: ${list.last().value}")
        }
    }

    fun toJson(extra: Map<String, Any?> = emptyMap()): String {
        val root = JSONObject()
        extra.forEach { (k, v) -> root.put(k, v ?: JSONObject.NULL) }
        root.put("address", address)
        scan?.let {
            root.put("name", it.name ?: JSONObject.NULL)
            root.put("rssi", it.rssi)
            root.put("connectable", it.connectable)
            root.put("advertisement", it.rawHex)
            root.put("advertisedServices", JSONArray(it.serviceUuids))
            root.put("serviceData", JSONObject(it.serviceData as Map<*, *>))
            root.put("manufacturerData", JSONObject(it.manufacturerData as Map<*, *>))
        }
        root.put("mtu", mtu ?: JSONObject.NULL)
        root.put("error", error ?: JSONObject.NULL)
        val servicesJson = JSONArray()
        services.forEach { service ->
            val chars = JSONArray()
            service.characteristics.forEach { c ->
                val descriptors = JSONArray()
                c.descriptors.forEach { d ->
                    descriptors.put(JSONObject().put("uuid", d.uuid).put("value", d.value ?: JSONObject.NULL))
                }
                chars.put(
                    JSONObject()
                        .put("uuid", c.uuid)
                        .put("properties", JSONArray(c.properties))
                        .put("value", c.value ?: JSONObject.NULL)
                        .put("error", c.error ?: JSONObject.NULL)
                        .put("descriptors", descriptors),
                )
            }
            servicesJson.put(
                JSONObject().put("uuid", service.uuid).put("primary", service.primary).put("characteristics", chars),
            )
        }
        root.put("services", servicesJson)
        val notificationsJson = JSONArray()
        notifications.forEach { n ->
            notificationsJson.put(JSONObject().put("t", n.offsetMs).put("uuid", n.uuid).put("value", n.value))
        }
        root.put("notifications", notificationsJson)
        root.put("log", JSONArray(log))
        return root.toString(2)
    }
}

data class DumpState(
    val running: Boolean = false,
    val status: String = "",
    val result: GattDump? = null,
)
