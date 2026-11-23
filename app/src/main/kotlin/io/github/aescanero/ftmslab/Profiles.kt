package io.github.aescanero.ftmslab

import android.content.SharedPreferences
import io.github.aescanero.ftmslab.ble.central.GattDump
import io.github.aescanero.ftmslab.data.ProfileRow
import io.github.aescanero.ftmslab.protocol.CaptureInput
import io.github.aescanero.ftmslab.protocol.DemoWorkout
import io.github.aescanero.ftmslab.protocol.FtmsUuids
import io.github.aescanero.ftmslab.protocol.MachineProfile
import io.github.aescanero.ftmslab.protocol.MachineType
import io.github.aescanero.ftmslab.protocol.Metric
import io.github.aescanero.ftmslab.protocol.ProfileBuilder
import io.github.aescanero.ftmslab.protocol.ProfileStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Perfil fijo de la sesión simulada. */
val DEMO_PROFILE = MachineProfile(
    address = "demo",
    name = DemoWorkout.NAME,
    type = MachineType.CROSS_TRAINER,
    status = ProfileStatus.SUPPORTED,
    metrics = Metric.entries.toSet(),
    declaredMetrics = Metric.entries.toSet(),
)

/** Perfiles de máquina guardados, persistidos como JSON en las preferencias. */
class ProfileStore(private val prefs: SharedPreferences) {
    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<MachineProfile>> = _profiles.asStateFlow()

    fun find(address: String?): MachineProfile? = _profiles.value.firstOrNull { it.address == address }

    fun save(profile: MachineProfile) {
        _profiles.update { list -> list.filterNot { it.address == profile.address } + profile }
        persist()
    }

    fun delete(address: String) {
        _profiles.update { list -> list.filterNot { it.address == address } }
        persist()
    }

    /** Perfiles en el formato del fichero de sincronización; la fecha de captura marca la versión. */
    fun rows(): List<ProfileRow> = _profiles.value.map { ProfileRow(it.address, toJson(it).toString(), it.createdAtMs) }

    fun replaceAll(rows: List<ProfileRow>) {
        _profiles.value = rows.mapNotNull { runCatching { fromJson(JSONObject(it.json)) }.getOrNull() }
        persist()
    }

    private fun persist() {
        val array = JSONArray()
        _profiles.value.forEach { array.put(toJson(it)) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun load(): List<MachineProfile> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        List(array.length()) { fromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY = "machineProfiles"

        fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)

        fun JSONObject.optDoubleOrNull(key: String): Double? = if (isNull(key)) null else optDouble(key)

        fun metrics(array: JSONArray?): Set<Metric> =
            (0 until (array?.length() ?: 0)).mapNotNull { i -> Metric.entries.firstOrNull { it.name == array!!.getString(i) } }.toSet()

        fun toJson(p: MachineProfile): JSONObject = JSONObject()
            .put("address", p.address)
            .put("name", p.name ?: JSONObject.NULL)
            .put("type", p.type?.name ?: JSONObject.NULL)
            .put("status", p.status.name)
            .put("metrics", JSONArray(p.metrics.map { it.name }))
            .put("declared", JSONArray(p.declaredMetrics.map { it.name }))
            .put("manufacturer", p.manufacturer ?: JSONObject.NULL)
            .put("model", p.model ?: JSONObject.NULL)
            .put("firmware", p.firmware ?: JSONObject.NULL)
            .put("resistanceMin", p.resistanceMin ?: JSONObject.NULL)
            .put("resistanceMax", p.resistanceMax ?: JSONObject.NULL)
            .put("createdAtMs", p.createdAtMs)

        fun fromJson(o: JSONObject) = MachineProfile(
            address = o.getString("address"),
            name = o.optStringOrNull("name"),
            type = MachineType.fromName(o.optStringOrNull("type")),
            status = ProfileStatus.entries.firstOrNull { it.name == o.optString("status") } ?: ProfileStatus.NO_DATA,
            metrics = metrics(o.optJSONArray("metrics")),
            declaredMetrics = metrics(o.optJSONArray("declared")),
            manufacturer = o.optStringOrNull("manufacturer"),
            model = o.optStringOrNull("model"),
            firmware = o.optStringOrNull("firmware"),
            resistanceMin = o.optDoubleOrNull("resistanceMin"),
            resistanceMax = o.optDoubleOrNull("resistanceMax"),
            createdAtMs = o.optLong("createdAtMs"),
        )
    }
}

private fun hexBytes(text: String?): ByteArray? =
    text?.trim()?.takeIf { it.isNotEmpty() }?.split(" ")?.map { it.toInt(16).toByte() }?.toByteArray()

/** Convierte la forma de mostrar un UUID del volcado ("0x2ACE" o completo) en UUID. */
private fun uuidOf(display: String): UUID? = runCatching {
    if (display.startsWith("0x")) FtmsUuids.short(display.substring(2).toInt(16)) else UUID.fromString(display)
}.getOrNull()

/** Extrae del volcado GATT lo que necesita el generador de perfiles. */
fun GattDump.toCaptureInput(capturedAtMs: Long): CaptureInput {
    val ftms = services.firstOrNull { it.uuid == "0x1826" }
    val info = services.firstOrNull { it.uuid == "0x180A" }
    fun ftmsValue(uuid: String) = hexBytes(ftms?.characteristics?.firstOrNull { it.uuid == uuid }?.value)
    fun infoText(uuid: String) = ProfileBuilder.ascii(hexBytes(info?.characteristics?.firstOrNull { it.uuid == uuid }?.value))
    return CaptureInput(
        address = address,
        name = scan?.name,
        serviceData = hexBytes(scan?.serviceData?.get("0x1826")),
        ftmsCharacteristics = ftms?.characteristics?.mapNotNull { uuidOf(it.uuid) }?.toSet().orEmpty(),
        feature = ftmsValue("0x2ACC"),
        resistanceRange = ftmsValue("0x2AD6"),
        manufacturer = infoText("0x2A29"),
        model = infoText("0x2A24"),
        firmware = infoText("0x2A26"),
        notifications = notifications.mapNotNull { n ->
            val uuid = uuidOf(n.uuid) ?: return@mapNotNull null
            val value = hexBytes(n.value) ?: return@mapNotNull null
            uuid to value
        },
        capturedAtMs = capturedAtMs,
    )
}
