package io.github.aescanero.fs2ftms.protocol

/** Zonas de entrenamiento por porcentaje de la frecuencia cardíaca máxima. */
enum class HeartRateZone(val number: Int, val label: String, val lowerPercent: Int, val upperPercent: Int) {
    RECOVERY(1, "Recuperación", 50, 60),
    LIGHT_AEROBIC(2, "Aeróbica ligera", 60, 70),
    AEROBIC(3, "Aeróbica", 70, 80),
    ANAEROBIC(4, "Anaeróbica", 80, 90),
    MAXIMUM(5, "Máxima", 90, 100),
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

    companion object {
        /** Estimación clásica cuando no se conoce la máxima medida. */
        fun estimateMax(age: Int): Int = 220 - age
    }
}
