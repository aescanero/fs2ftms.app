package io.github.aescanero.ftmslab.protocol

/** Zonas de entrenamiento por porcentaje de la frecuencia cardíaca máxima. `label` es solo para registros; la interfaz usa recursos traducidos. */
enum class HeartRateZone(val number: Int, val label: String, val lowerPercent: Int, val upperPercent: Int) {
    RECOVERY(1, "Recovery", 50, 60),
    LIGHT_AEROBIC(2, "Light aerobic", 60, 70),
    AEROBIC(3, "Aerobic", 70, 80),
    ANAEROBIC(4, "Anaerobic", 80, 90),
    MAXIMUM(5, "Maximum", 90, 100),
}

class HeartRateZones(val maxHeartRate: Int) {
    /** Pulso mínimo representado: el inicio de la zona 1. */
    val floor: Int get() = lowerBound(HeartRateZone.RECOVERY)

    fun lowerBound(zone: HeartRateZone): Int = maxHeartRate * zone.lowerPercent / 100

    fun upperBound(zone: HeartRateZone): Int = maxHeartRate * zone.upperPercent / 100

    /** Zona del pulso dado; null por debajo de la zona 1. Por encima del máximo cuenta como zona 5. */
    fun zoneOf(bpm: Int): HeartRateZone? {
        if (bpm < floor) return null
        return HeartRateZone.entries.lastOrNull { bpm >= lowerBound(it) }
    }

    /** Posición del pulso entre el inicio de la zona 1 (0) y el máximo (1). */
    fun fraction(bpm: Int): Float {
        val span = (maxHeartRate - floor).coerceAtLeast(1)
        return ((bpm - floor).toFloat() / span).coerceIn(0f, 1f)
    }

    /**
     * Segundos en cada zona a partir de muestras (tiempo activo acumulado, pulso). La clave null
     * agrupa el tiempo por debajo de la zona 1; las muestras sin pulso no cuentan.
     */
    fun timeInZones(points: List<Pair<Int, Int?>>): Map<HeartRateZone?, Int> {
        val totals = LinkedHashMap<HeartRateZone?, Int>()
        var previous: Int? = null
        for ((elapsed, bpm) in points) {
            val step = previous?.let { (elapsed - it).coerceIn(0, MAX_STEP_SECONDS) } ?: 1
            previous = elapsed
            if (bpm != null && step > 0) {
                val zone = zoneOf(bpm)
                totals[zone] = (totals[zone] ?: 0) + step
            }
        }
        return totals
    }

    companion object {
        private const val MAX_STEP_SECONDS = 5

        /** Estimación clásica cuando no se conoce la máxima medida. */
        fun estimateMax(age: Int): Int = 220 - age
    }
}
