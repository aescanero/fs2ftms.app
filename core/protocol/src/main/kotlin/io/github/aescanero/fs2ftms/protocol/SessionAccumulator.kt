package io.github.aescanero.fs2ftms.protocol

/**
 * Convierte los contadores de la máquina en contadores de la sesión grabada: descuenta lo que
 * ya marcaba al empezar, sigue sumando si la máquina se reinicia y, tras una pausa, ignora lo
 * que la máquina haya contado mientras tanto.
 */
class SessionAccumulator {
    private var baseElapsed = 0
    private var baseDistance = 0
    private var baseEnergy = 0
    private var baseStrides = 0.0
    private var last: WorkoutSample? = null
    private var lastOutput: WorkoutSample? = null

    @Volatile
    private var pendingRebase = false

    /** Tras una pausa: la siguiente muestra continúa desde los totales ya grabados. */
    fun rebase() {
        pendingRebase = true
    }

    fun add(raw: WorkoutSample): WorkoutSample {
        val previous = last
        val output = lastOutput
        if (previous == null || output == null) {
            baseElapsed = -raw.elapsedSeconds
            baseDistance = -raw.distanceMeters
            baseEnergy = -raw.energyKcal
            baseStrides = -raw.strideCount
        } else if (pendingRebase) {
            baseElapsed = output.elapsedSeconds - raw.elapsedSeconds
            baseDistance = output.distanceMeters - raw.distanceMeters
            baseEnergy = output.energyKcal - raw.energyKcal
            baseStrides = output.strideCount - raw.strideCount
        } else if (raw.elapsedSeconds < previous.elapsedSeconds || raw.distanceMeters < previous.distanceMeters) {
            baseElapsed += previous.elapsedSeconds
            baseDistance += previous.distanceMeters
            baseEnergy += previous.energyKcal
            baseStrides += previous.strideCount
        }
        pendingRebase = false
        last = raw
        return raw.copy(
            elapsedSeconds = baseElapsed + raw.elapsedSeconds,
            distanceMeters = baseDistance + raw.distanceMeters,
            energyKcal = baseEnergy + raw.energyKcal,
            strideCount = baseStrides + raw.strideCount,
        ).also { lastOutput = it }
    }
}
