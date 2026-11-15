package io.github.aescanero.fs2ftms.protocol

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
)

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
