package io.github.aescanero.fs2ftms.protocol

import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** Codificación little-endian de las características FTMS (GATT Specification Supplement). */
object FtmsCodec {
    const val RESULT_SUCCESS = 0x01
    const val RESULT_NOT_SUPPORTED = 0x02
    const val RESULT_CONTROL_NOT_PERMITTED = 0x05

    const val OP_REQUEST_CONTROL = 0x00
    const val OP_RESET = 0x01
    const val OP_START = 0x07
    const val OP_STOP = 0x08
    private const val OP_RESPONSE = 0x80

    private class Writer {
        private val out = ByteArrayOutputStream()
        fun u8(v: Int) = apply { out.write(v and 0xFF) }
        fun u16(v: Int) = apply { u8(v); u8(v shr 8) }
        fun u24(v: Int) = apply { u16(v); u8(v shr 16) }
        fun u32(v: Long) = apply { u16(v.toInt()); u16((v shr 16).toInt()) }
        fun bytes(): ByteArray = out.toByteArray()
    }

    /** Cross Trainer Data (0x2ACE): flags de 24 bits. */
    fun crossTrainerData(s: WorkoutSample): ByteArray {
        // bit2 distancia, bit3 pasos, bit4 zancadas, bit8 potencia, bit10 energía, bit13 tiempo
        val flags = (1 shl 2) or (1 shl 3) or (1 shl 4) or (1 shl 8) or (1 shl 10) or (1 shl 13)
        return Writer()
            .u24(flags)
            .u16((s.speedKmh * 100).roundToInt().coerceIn(0, 0xFFFF))
            .u24(s.distanceMeters.coerceIn(0, 0xFFFFFF))
            .u16(s.cadencePerMinute.coerceIn(0, 0xFFFF))
            .u16(s.averageCadencePerMinute.coerceIn(0, 0xFFFF))
            .u16((s.strideCount * 10).roundToInt().coerceIn(0, 0xFFFF))
            .u16(s.powerWatts.coerceIn(-32768, 32767))
            .u16(s.energyKcal.coerceIn(0, 0xFFFF)).u16(0xFFFF).u8(0xFF)
            .u16(s.elapsedSeconds.coerceIn(0, 0xFFFF))
            .bytes()
    }

    /** Indoor Bike Data (0x2AD2): flags de 16 bits. */
    fun indoorBikeData(s: WorkoutSample): ByteArray {
        // bit2 cadencia, bit4 distancia, bit5 resistencia (opcional), bit6 potencia, bit8 energía,
        // bit11 tiempo. Máximo 20 bytes: el reloj no negocia MTU y solo caben 20 por notificación.
        val resistance = s.resistanceLevel
        var flags = (1 shl 2) or (1 shl 4) or (1 shl 6) or (1 shl 8) or (1 shl 11)
        if (resistance != null) flags = flags or (1 shl 5)
        val writer = Writer()
            .u16(flags)
            .u16((s.speedKmh * 100).roundToInt().coerceIn(0, 0xFFFF))
            .u16((s.cadencePerMinute * 2).coerceIn(0, 0xFFFF))
            .u24(s.distanceMeters.coerceIn(0, 0xFFFFFF))
        if (resistance != null) writer.u16(resistance.roundToInt().coerceIn(-32768, 32767))
        return writer
            .u16(s.powerWatts.coerceIn(-32768, 32767))
            .u16(s.energyKcal.coerceIn(0, 0xFFFF)).u16(0xFFFF).u8(0xFF)
            .u16(s.elapsedSeconds.coerceIn(0, 0xFFFF))
            .bytes()
    }

    fun data(type: MachineType, sample: WorkoutSample): ByteArray = when (type) {
        MachineType.CROSS_TRAINER -> crossTrainerData(sample)
        MachineType.INDOOR_BIKE -> indoorBikeData(sample)
    }

    /** Fitness Machine Feature (0x2ACC): solo se declaran las métricas que se envían. */
    fun feature(type: MachineType): ByteArray {
        val common = (1L shl 2) or (1L shl 9) or (1L shl 12) or (1L shl 14)
        val machine = when (type) {
            MachineType.CROSS_TRAINER -> common or (1L shl 6) or (1L shl 8)
            MachineType.INDOOR_BIKE -> common or (1L shl 1) or (1L shl 7)
        }
        return Writer().u32(machine).u32(0L).bytes()
    }

    /** Service Data del anuncio: máquina disponible + tipo. */
    fun advertisingServiceData(type: MachineType): ByteArray =
        Writer().u8(0x01).u16(type.advertisingBit).bytes()

    fun parseControlPoint(value: ByteArray): ControlPointRequest? =
        if (value.isEmpty()) null
        else ControlPointRequest(value[0].toInt() and 0xFF, value.copyOfRange(1, value.size))

    /** El puente es de solo lectura: acepta control, inicio y parada; rechaza los objetivos. */
    fun resultFor(request: ControlPointRequest): Int = when (request.opcode) {
        OP_REQUEST_CONTROL, OP_RESET, OP_START, OP_STOP -> RESULT_SUCCESS
        else -> RESULT_NOT_SUPPORTED
    }

    fun controlPointResponse(requestOpcode: Int, result: Int): ByteArray =
        Writer().u8(OP_RESPONSE).u8(requestOpcode).u8(result).bytes()

    /** Training Status (0x2AD3): 0x0D = modo manual, 0x01 = en reposo. */
    fun trainingStatus(active: Boolean): ByteArray = Writer().u8(0x00).u8(if (active) 0x0D else 0x01).bytes()

    fun machineStatusStarted(): ByteArray = byteArrayOf(0x04)

    fun machineStatusStopped(): ByteArray = byteArrayOf(0x02, 0x01)
}
