package io.github.aescanero.fs2ftms.protocol

import java.util.UUID

/** Tipo de máquina que anuncia el servidor FTMS. */
enum class MachineType(val advertisingBit: Int, val dataCharacteristic: UUID, val label: String) {
    CROSS_TRAINER(0x0002, FtmsUuids.CROSS_TRAINER_DATA, "Elíptica (Cross Trainer)"),
    INDOOR_BIKE(0x0020, FtmsUuids.INDOOR_BIKE_DATA, "Bicicleta (Indoor Bike)"),
}

/** Muestra de entrenamiento independiente del protocolo de origen. */
data class WorkoutSample(
    val elapsedSeconds: Int,
    val speedKmh: Double,
    val cadencePerMinute: Int,
    val averageCadencePerMinute: Int,
    val distanceMeters: Int,
    val strideCount: Double,
    val powerWatts: Int,
    val energyKcal: Int,
)

/** Petición recibida en el Fitness Machine Control Point. */
data class ControlPointRequest(val opcode: Int, val parameters: ByteArray) {
    val name: String
        get() = when (opcode) {
            0x00 -> "Request Control"
            0x01 -> "Reset"
            0x02 -> "Set Target Speed"
            0x03 -> "Set Target Inclination"
            0x04 -> "Set Target Resistance"
            0x05 -> "Set Target Power"
            0x06 -> "Set Target Heart Rate"
            0x07 -> "Start/Resume"
            0x08 -> "Stop/Pause"
            else -> "Opcode 0x%02X".format(opcode)
        }

    override fun equals(other: Any?): Boolean =
        other is ControlPointRequest && opcode == other.opcode && parameters.contentEquals(other.parameters)

    override fun hashCode(): Int = 31 * opcode + parameters.contentHashCode()
}

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
