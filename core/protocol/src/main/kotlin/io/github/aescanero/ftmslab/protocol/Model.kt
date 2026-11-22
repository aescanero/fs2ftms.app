package io.github.aescanero.ftmslab.protocol

/** Muestra de entrenamiento de la máquina, ya decodificada. */
data class WorkoutSample(
    val elapsedSeconds: Int,
    val speedKmh: Double,
    val cadencePerMinute: Int,
    val averageCadencePerMinute: Int,
    val distanceMeters: Int,
    val strideCount: Double,
    val powerWatts: Int,
    val energyKcal: Int,
    val averageSpeedKmh: Double? = null,
    val resistanceLevel: Double? = null,
    val averagePowerWatts: Int? = null,
    /** Pulso medido por la propia máquina (sensores de agarre o banda emparejada); null si no lo envía. */
    val heartRate: Int? = null,
)

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
