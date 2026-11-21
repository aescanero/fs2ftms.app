package io.github.aescanero.fs2ftms.protocol

/**
 * Decodifica Indoor Bike Data (0x2AD2) según la especificación FTMS: flags de 16 bits y campos en
 * orden. Pendiente de validar con tramas reales (bicicleta Toputure); en particular la escala de la
 * resistencia, que aquí se toma en décimas como en la elíptica.
 */
class IndoorBikeDataParser : FtmsDataParser {
    private var speed: Double? = null
    private var averageSpeed: Double? = null
    private var cadence: Double? = null
    private var averageCadence: Double? = null
    private var distance: Int? = null
    private var resistance: Double? = null
    private var power: Int? = null
    private var averagePower: Int? = null
    private var energy: Int? = null
    private var elapsed: Int? = null
    private var heartRate: Int? = null
    private val present = HashSet<Metric>()

    override fun accept(value: ByteArray): ParsedSample? {
        if (value.size < 2) return null
        val reader = FrameReader(value)
        val flags = reader.u16()
        fun has(bit: Int) = (flags and (1 shl bit)) != 0
        val moreData = has(0)
        try {
            if (!moreData) {
                speed = reader.u16() / 100.0
                present += Metric.SPEED
            }
            if (has(1)) averageSpeed = reader.u16() / 100.0
            if (has(2)) {
                cadence = reader.u16() / 2.0
                present += Metric.CADENCE
            }
            if (has(3)) averageCadence = reader.u16() / 2.0
            if (has(4)) {
                distance = reader.u24()
                present += Metric.DISTANCE
            }
            if (has(5)) {
                resistance = reader.s16() / 10.0
                present += Metric.RESISTANCE
            }
            if (has(6)) {
                power = reader.s16()
                present += Metric.POWER
            }
            if (has(7)) averagePower = reader.s16()
            if (has(8)) {
                val total = reader.u16()
                if (total != 0xFFFF) {
                    energy = total
                    present += Metric.ENERGY
                }
                reader.u16()
                reader.u8()
            }
            if (has(9)) {
                // La Merach incluye el campo siempre a 0: solo cuenta como métrica si hay lectura real.
                heartRate = reader.u8().takeIf { it > 0 }
                if (heartRate != null) present += Metric.HEART_RATE
            }
            if (has(10)) reader.u8()
            if (has(11)) {
                elapsed = reader.u16()
                present += Metric.ELAPSED_TIME
            }
            if (has(12)) reader.u16()
        } catch (e: IndexOutOfBoundsException) {
            return null
        }
        if (moreData) return null
        val sample = WorkoutSample(
            elapsedSeconds = elapsed ?: 0,
            speedKmh = speed ?: 0.0,
            cadencePerMinute = cadence?.toInt() ?: 0,
            averageCadencePerMinute = averageCadence?.toInt() ?: 0,
            distanceMeters = distance ?: 0,
            strideCount = 0.0,
            powerWatts = power ?: 0,
            energyKcal = energy ?: 0,
            averageSpeedKmh = averageSpeed,
            resistanceLevel = resistance,
            averagePowerWatts = averagePower,
            heartRate = heartRate,
        )
        return ParsedSample(sample, present.toSet()).also { present.clear() }
    }
}
