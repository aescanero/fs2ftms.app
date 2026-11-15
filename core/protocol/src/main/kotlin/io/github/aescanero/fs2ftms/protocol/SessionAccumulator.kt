package io.github.aescanero.fs2ftms.protocol

/**
 * Convierte los contadores de la máquina en contadores de la sesión grabada: descuenta lo que
 * ya marcaba al empezar y sigue sumando si la máquina se reinicia a mitad de sesión.
 */
class SessionAccumulator {
    private var baseElapsed = 0
    private var baseDistance = 0
    private var baseEnergy = 0
    private var baseStrides = 0.0
    private var last: WorkoutSample? = null

    fun add(raw: WorkoutSample): WorkoutSample {
        val previous = last
        if (previous == null) {
            baseElapsed = -raw.elapsedSeconds
            baseDistance = -raw.distanceMeters
            baseEnergy = -raw.energyKcal
            baseStrides = -raw.strideCount
        } else if (raw.elapsedSeconds < previous.elapsedSeconds || raw.distanceMeters < previous.distanceMeters) {
            baseElapsed += previous.elapsedSeconds
            baseDistance += previous.distanceMeters
            baseEnergy += previous.energyKcal
            baseStrides += previous.strideCount
        }
        last = raw
        return raw.copy(
            elapsedSeconds = baseElapsed + raw.elapsedSeconds,
            distanceMeters = baseDistance + raw.distanceMeters,
            energyKcal = baseEnergy + raw.energyKcal,
            strideCount = baseStrides + raw.strideCount,
        )
    }
}
