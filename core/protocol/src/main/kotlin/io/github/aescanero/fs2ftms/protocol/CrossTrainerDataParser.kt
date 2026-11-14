package io.github.aescanero.fs2ftms.protocol

/**
 * Decodifica Cross Trainer Data (0x2ACE). Con MTU 23 la máquina reparte los campos en varios
 * paquetes: los que llevan el bit "More Data" se acumulan y la muestra se emite con el último.
 */
class CrossTrainerDataParser {
    private class Reader(private val data: ByteArray) {
        private var position = 0
        fun u8(): Int = data[position++].toInt() and 0xFF
        fun u16(): Int = u8() or (u8() shl 8)
        fun u24(): Int = u16() or (u8() shl 16)
        fun s16(): Int = u16().toShort().toInt()
    }

    private var speed: Double? = null
    private var averageSpeed: Double? = null
    private var distance: Int? = null
    private var stepRate: Int? = null
    private var averageStepRate: Int? = null
    private var strideCount: Double? = null
    private var resistance: Double? = null
    private var power: Int? = null
    private var averagePower: Int? = null
    private var energy: Int? = null
    private var elapsed: Int? = null

    /** Devuelve una muestra completa cuando llega el paquete final; null en los intermedios. */
    fun accept(value: ByteArray): WorkoutSample? {
        if (value.size < 3) return null
        val reader = Reader(value)
        val flags = reader.u24()
        fun has(bit: Int) = (flags and (1 shl bit)) != 0
        val moreData = has(0)
        try {
            if (!moreData) speed = reader.u16() / 100.0
            if (has(1)) averageSpeed = reader.u16() / 100.0
            if (has(2)) distance = reader.u24()
            if (has(3)) {
                stepRate = reader.u16()
                averageStepRate = reader.u16()
            }
            if (has(4)) strideCount = reader.u16() / 10.0
            if (has(5)) {
                reader.u16()
                reader.u16()
            }
            if (has(6)) {
                reader.s16()
                reader.s16()
            }
            if (has(7)) resistance = reader.s16() / 10.0
            if (has(8)) power = reader.s16()
            if (has(9)) averagePower = reader.s16()
            if (has(10)) {
                val total = reader.u16()
                if (total != 0xFFFF) energy = total
                reader.u16()
                reader.u8()
            }
            if (has(11)) reader.u8()
            if (has(12)) reader.u8()
            if (has(13)) elapsed = reader.u16()
            if (has(14)) reader.u16()
        } catch (e: IndexOutOfBoundsException) {
            return null
        }
        if (moreData) return null
        return WorkoutSample(
            elapsedSeconds = elapsed ?: 0,
            speedKmh = speed ?: 0.0,
            cadencePerMinute = stepRate ?: 0,
            averageCadencePerMinute = averageStepRate ?: 0,
            distanceMeters = distance ?: 0,
            strideCount = strideCount ?: 0.0,
            powerWatts = power ?: 0,
            energyKcal = energy ?: 0,
            averageSpeedKmh = averageSpeed,
            resistanceLevel = resistance,
            averagePowerWatts = averagePower,
        )
    }
}
