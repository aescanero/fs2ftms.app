package io.github.aescanero.fs2ftms.protocol

import java.util.UUID

/** Métricas que una máquina puede aportar a una sesión. El tiempo se muestra siempre. */
enum class Metric { SPEED, CADENCE, DISTANCE, STRIDES, RESISTANCE, POWER, ENERGY, ELAPSED_TIME }

/**
 * Tipos de máquina del perfil FTMS, con su característica de datos y su bit en el Service Data del
 * anuncio. Solo los que tienen decodificador se pueden grabar; el resto se identifica y se marca.
 */
enum class MachineType(val dataCharacteristic: UUID, val advertisingBit: Int) {
    TREADMILL(FtmsUuids.short(0x2ACD), 0),
    CROSS_TRAINER(FtmsUuids.short(0x2ACE), 1),
    STEP_CLIMBER(FtmsUuids.short(0x2ACF), 2),
    STAIR_CLIMBER(FtmsUuids.short(0x2AD0), 3),
    ROWER(FtmsUuids.short(0x2AD1), 4),
    INDOOR_BIKE(FtmsUuids.short(0x2AD2), 5),
    ;

    val supported: Boolean get() = parserFor(this) != null

    companion object {
        fun fromName(name: String?): MachineType? = entries.firstOrNull { it.name == name }

        /** Tipo anunciado en el Service Data de 0x1826: flags (1 byte) + tipos (2 bytes). */
        fun fromServiceData(data: ByteArray?): MachineType? {
            if (data == null || data.size < 3) return null
            val types = (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
            return entries.firstOrNull { (types and (1 shl it.advertisingBit)) != 0 }
        }
    }
}

/** Muestra decodificada junto con las métricas que la trama traía realmente. */
data class ParsedSample(val sample: WorkoutSample, val present: Set<Metric>)

/** Decodificador de la característica de datos de un tipo de máquina. Con estado: une paquetes. */
interface FtmsDataParser {
    fun accept(value: ByteArray): ParsedSample?
}

fun parserFor(type: MachineType): FtmsDataParser? = when (type) {
    MachineType.CROSS_TRAINER -> CrossTrainerDataParser()
    MachineType.INDOOR_BIKE -> IndoorBikeDataParser()
    else -> null
}

internal class FrameReader(private val data: ByteArray) {
    private var position = 0
    fun u8(): Int = data[position++].toInt() and 0xFF
    fun u16(): Int = u8() or (u8() shl 8)
    fun u24(): Int = u16() or (u8() shl 16)
    fun s16(): Int = u16().toShort().toInt()
}
