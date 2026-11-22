package io.github.aescanero.ftmslab.protocol

import java.util.UUID

enum class ProfileStatus {
    /** FTMS legible con un tipo que sabemos decodificar: se puede grabar. */
    SUPPORTED,

    /** No publica el servicio Fitness Machine (0x1826). */
    NO_FTMS,

    /** Publica FTMS pero no se puede determinar qué tipo de máquina es. */
    UNKNOWN_TYPE,

    /** Tipo identificado pero sin decodificador todavía (cinta, remo, escaladora…). */
    TYPE_NOT_SUPPORTED,

    /** Tipo soportado, pero no llegó ninguna trama válida durante la captura. */
    NO_DATA,
}

/** Perfil de una máquina, generado a partir de una captura FTMS real. */
data class MachineProfile(
    val address: String,
    val name: String?,
    val type: MachineType?,
    val status: ProfileStatus,
    /** Métricas observadas en las tramas capturadas: las que la pantalla de entreno muestra. */
    val metrics: Set<Metric>,
    /** Métricas que la máquina declara en Fitness Machine Feature (0x2ACC), a título informativo. */
    val declaredMetrics: Set<Metric>,
    val manufacturer: String? = null,
    val model: String? = null,
    val firmware: String? = null,
    val resistanceMin: Double? = null,
    val resistanceMax: Double? = null,
    val createdAtMs: Long = 0,
) {
    val supported: Boolean get() = status == ProfileStatus.SUPPORTED

    fun has(metric: Metric): Boolean = metric in metrics

    companion object {
        /** Perfil supuesto para máquinas elegidas antes de que existieran los perfiles. */
        fun legacy(address: String, name: String?) = MachineProfile(
            address = address,
            name = name,
            type = MachineType.CROSS_TRAINER,
            status = ProfileStatus.SUPPORTED,
            metrics = Metric.entries.toSet() - Metric.HEART_RATE,
            declaredMetrics = emptySet(),
        )
    }
}

/** Datos de una captura, ya extraídos del volcado GATT y convertidos a bytes. */
class CaptureInput(
    val address: String,
    val name: String?,
    /** Service Data del anuncio para 0x1826, si lo había. */
    val serviceData: ByteArray?,
    /** Características presentes en el servicio 0x1826; vacío si el servicio no existe. */
    val ftmsCharacteristics: Set<UUID>,
    val feature: ByteArray?,
    val resistanceRange: ByteArray?,
    val manufacturer: String?,
    val model: String?,
    val firmware: String?,
    /** Notificaciones recibidas, en orden: característica y valor. */
    val notifications: List<Pair<UUID, ByteArray>>,
    val capturedAtMs: Long,
)

object ProfileBuilder {
    val RESISTANCE_RANGE = FtmsUuids.short(0x2AD6)

    fun build(input: CaptureInput): MachineProfile {
        fun profile(status: ProfileStatus, type: MachineType? = null, metrics: Set<Metric> = emptySet()) =
            MachineProfile(
                address = input.address,
                name = input.name,
                type = type,
                status = status,
                metrics = metrics,
                declaredMetrics = declaredMetrics(input.feature),
                manufacturer = input.manufacturer?.takeIf { it.isNotBlank() },
                model = input.model?.takeIf { it.isNotBlank() },
                firmware = input.firmware?.takeIf { it.isNotBlank() },
                resistanceMin = resistanceRange(input.resistanceRange)?.first,
                resistanceMax = resistanceRange(input.resistanceRange)?.second,
                createdAtMs = input.capturedAtMs,
            )

        if (input.ftmsCharacteristics.isEmpty() && input.serviceData == null) return profile(ProfileStatus.NO_FTMS)

        val offered = MachineType.entries.filter { it.dataCharacteristic in input.ftmsCharacteristics }
        val notifiedUuids = input.notifications.map { it.first }.toSet()
        val type = offered.firstOrNull { it.dataCharacteristic in notifiedUuids }
            ?: offered.singleOrNull()
            ?: MachineType.fromServiceData(input.serviceData)
            ?: offered.firstOrNull()
            ?: return profile(ProfileStatus.UNKNOWN_TYPE)

        val parser = parserFor(type) ?: return profile(ProfileStatus.TYPE_NOT_SUPPORTED, type)
        val observed = HashSet<Metric>()
        var samples = 0
        input.notifications
            .filter { it.first == type.dataCharacteristic }
            .forEach { (_, value) ->
                parser.accept(value)?.let {
                    samples++
                    observed += it.present
                }
            }
        if (samples == 0) return profile(ProfileStatus.NO_DATA, type)
        return profile(ProfileStatus.SUPPORTED, type, observed)
    }

    /** Fitness Machine Features (primeros 32 bits de 0x2ACC) traducidos a métricas. */
    fun declaredMetrics(feature: ByteArray?): Set<Metric> {
        if (feature == null || feature.size < 4) return emptySet()
        val bits = (0 until 4).fold(0L) { acc, i -> acc or ((feature[i].toLong() and 0xFF) shl (8 * i)) }
        fun has(bit: Int) = (bits and (1L shl bit)) != 0L
        return buildSet {
            add(Metric.SPEED)
            if (has(1) || has(6)) add(Metric.CADENCE)
            if (has(2)) add(Metric.DISTANCE)
            if (has(7)) add(Metric.RESISTANCE)
            if (has(8)) add(Metric.STRIDES)
            if (has(9)) add(Metric.ENERGY)
            if (has(10)) add(Metric.HEART_RATE)
            if (has(12)) add(Metric.ELAPSED_TIME)
            if (has(14)) add(Metric.POWER)
        }
    }

    /** Supported Resistance Level Range (0x2AD6): mínimo y máximo en décimas. */
    fun resistanceRange(value: ByteArray?): Pair<Double, Double>? {
        if (value == null || value.size < 4) return null
        val reader = FrameReader(value)
        return reader.s16() / 10.0 to reader.s16() / 10.0
    }

    /** Texto de una característica de Device Information, sin los ceros de relleno. */
    fun ascii(value: ByteArray?): String? =
        value?.takeWhile { it != 0.toByte() }?.toByteArray()?.toString(Charsets.UTF_8)?.trim()?.takeIf { it.isNotEmpty() }
}
