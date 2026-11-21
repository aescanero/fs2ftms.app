package io.github.aescanero.fs2ftms.protocol

/**
 * Decodifica Cross Trainer Data (0x2ACE). Con MTU 23 la máquina reparte los campos en varios
 * paquetes: los que llevan el bit "More Data" se acumulan y la muestra se emite con el último.
 * Validado con tramas reales de la Merach MRK-E27.
 */
class CrossTrainerDataParser : FtmsDataParser {
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
    private val present = HashSet<Metric>()

    /** Devuelve una muestra completa cuando llega el paquete final; null en los intermedios. */
    override fun accept(value: ByteArray): ParsedSample? {
        if (value.size < 3) return null
        val reader = FrameReader(value)
        val flags = reader.u24()
        fun has(bit: Int) = (flags and (1 shl bit)) != 0
        val moreData = has(0)
        try {
            if (!moreData) {
                speed = reader.u16() / 100.0
                present += Metric.SPEED
            }
            if (has(1)) averageSpeed = reader.u16() / 100.0
            if (has(2)) {
                distance = reader.u24()
                present += Metric.DISTANCE
            }
            if (has(3)) {
                stepRate = reader.u16()
                averageStepRate = reader.u16()
                present += Metric.CADENCE
            }
            if (has(4)) {
                strideCount = reader.u16() / 10.0
                present += Metric.STRIDES
            }
            if (has(5)) {
                reader.u16()
                reader.u16()
            }
            if (has(6)) {
                reader.s16()
                reader.s16()
            }
            if (has(7)) {
                resistance = reader.s16() / 10.0
                present += Metric.RESISTANCE
            }
            if (has(8)) {
                power = reader.s16()
                present += Metric.POWER
            }
            if (has(9)) averagePower = reader.s16()
            if (has(10)) {
                val total = reader.u16()
                if (total != 0xFFFF) {
                    energy = total
                    present += Metric.ENERGY
                }
                reader.u16()
                reader.u8()
            }
            if (has(11)) reader.u8()
            if (has(12)) reader.u8()
            if (has(13)) {
                elapsed = reader.u16()
                present += Metric.ELAPSED_TIME
            }
            if (has(14)) reader.u16()
        } catch (e: IndexOutOfBoundsException) {
            return null
        }
        if (moreData) return null
        val sample = WorkoutSample(
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
        return ParsedSample(sample, present.toSet()).also { present.clear() }
    }
}
