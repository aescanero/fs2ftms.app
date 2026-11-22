package io.github.aescanero.ftmslab.protocol

/**
 * Convierte los contadores de la máquina en contadores de la sesión grabada: descuenta lo que
 * ya marcaba al empezar, sigue sumando si la máquina se reinicia y, tras una pausa, ignora lo
 * que la máquina haya contado mientras tanto.
 *
 * La Merach envía contadores a cero en los primeros paquetes tras conectar y después salta a sus
 * valores reales (visto en una sesión real: de 0 a 142 s en 0,8 s). Un avance del tiempo de la
 * máquina mayor que el tiempo real transcurrido es imposible, así que se trata como cambio de
 * referencia y no se suma a la sesión.
 */
class SessionAccumulator {
    private var baseElapsed = 0
    private var baseDistance = 0
    private var baseEnergy = 0
    private var baseStrides = 0.0
    private var last: WorkoutSample? = null
    private var lastOutput: WorkoutSample? = null
    private var lastTimestampMs = 0L

    @Volatile
    private var pendingRebase = false

    /** Tras una pausa: la siguiente muestra continúa desde los totales ya grabados. */
    fun rebase() {
        pendingRebase = true
    }

    fun add(raw: WorkoutSample, timestampMs: Long): WorkoutSample {
        val previous = last
        val output = lastOutput
        val wallSeconds = (timestampMs - lastTimestampMs) / 1000.0
        val impossibleJump = previous != null &&
            raw.elapsedSeconds - previous.elapsedSeconds > wallSeconds + JUMP_TOLERANCE_SECONDS
        lastTimestampMs = timestampMs
        if (previous == null || output == null) {
            baseElapsed = -raw.elapsedSeconds
            baseDistance = -raw.distanceMeters
            baseEnergy = -raw.energyKcal
            baseStrides = -raw.strideCount
        } else if (pendingRebase || impossibleJump) {
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

    private companion object {
        const val JUMP_TOLERANCE_SECONDS = 5
    }
}
