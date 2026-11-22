package io.github.aescanero.ftmslab.protocol

/**
 * Gasto energético estimado a partir del pulso (Keytel et al., 2005), con edad, peso y sexo.
 * Es una aproximación poblacional: sirve para comparar sesiones propias, no como medida clínica.
 */
class CalorieEstimator(private val age: Int, private val weightKg: Int, private val male: Boolean) {
    fun kcalPerMinute(heartRate: Int): Double {
        val joules = if (male) {
            -55.0969 + 0.6309 * heartRate + 0.1988 * weightKg + 0.2017 * age
        } else {
            -20.4022 + 0.4472 * heartRate - 0.1263 * weightKg + 0.074 * age
        }
        return (joules / 4.184).coerceAtLeast(0.0)
    }

    /** Total de una sesión a partir de (tiempo activo acumulado, pulso); null si no hay pulso. */
    fun total(points: List<Pair<Int, Int?>>): Double? {
        var total = 0.0
        var counted = false
        var previous: Int? = null
        for ((elapsed, bpm) in points) {
            val step = previous?.let { (elapsed - it).coerceIn(0, MAX_STEP_SECONDS) } ?: 1
            previous = elapsed
            if (bpm != null && step > 0) {
                total += kcalPerMinute(bpm) * step / 60.0
                counted = true
            }
        }
        return total.takeIf { counted }
    }

    private companion object {
        const val MAX_STEP_SECONDS = 5
    }
}
