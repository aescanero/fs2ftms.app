package io.github.aescanero.fs2ftms.protocol

/** Genera una muestra por segundo a partir de una cadencia fija (pasos por minuto). */
class WorkoutSimulator(private val metersPerStep: Double = 0.6) {
    private var elapsed = 0
    private var distance = 0.0
    private var steps = 0.0
    private var energy = 0.0
    private var cadenceSum = 0L

    fun next(cadencePerMinute: Int): WorkoutSample {
        val cadence = cadencePerMinute.coerceAtLeast(0)
        elapsed += 1
        cadenceSum += cadence
        val stepsThisSecond = cadence / 60.0
        steps += stepsThisSecond
        distance += stepsThisSecond * metersPerStep
        val power = (cadence * 0.8).toInt()
        energy += power / 1046.0
        return WorkoutSample(
            elapsedSeconds = elapsed,
            speedKmh = cadence * metersPerStep * 60.0 / 1000.0,
            cadencePerMinute = cadence,
            averageCadencePerMinute = (cadenceSum / elapsed).toInt(),
            distanceMeters = distance.toInt(),
            strideCount = steps / 2.0,
            powerWatts = power,
            energyKcal = energy.toInt(),
        )
    }
}
