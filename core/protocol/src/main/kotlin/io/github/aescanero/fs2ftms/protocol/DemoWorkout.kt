package io.github.aescanero.fs2ftms.protocol

import kotlin.math.sin

/**
 * Sesión simulada para revisar la interfaz sin la máquina: calentamiento y series alternas, con un
 * pulso que responde con retardo a la intensidad. Determinista: una muestra por segundo.
 */
class DemoWorkout {
    data class Point(val sample: WorkoutSample, val heartRate: Int)

    private var elapsed = 0
    private var distance = 0.0
    private var strides = 0.0
    private var energy = 0.0
    private var cadenceSum = 0L
    private var heartRate = 82.0

    fun next(): Point {
        elapsed += 1
        val minute = elapsed / 60
        val (baseCadence, resistance) = when {
            minute < 3 -> 38 to 2
            (minute - 3) % 4 < 2 -> 58 to 9
            else -> 44 to 4
        }
        val cadence = (baseCadence + sin(elapsed / 7.0) * 2).toInt()
        val speed = cadence * 0.3
        val power = (cadence * resistance * 0.25).toInt()
        cadenceSum += cadence
        distance += speed / 3.6
        strides += cadence / 60.0
        energy += power * 4 / 4184.0 + 0.02
        val intensity = (cadence / 60.0) * (0.5 + resistance / 16.0)
        val target = 90 + intensity * 85
        heartRate += (target - heartRate) * 0.03
        val sample = WorkoutSample(
            elapsedSeconds = elapsed,
            speedKmh = speed,
            cadencePerMinute = cadence,
            averageCadencePerMinute = (cadenceSum / elapsed).toInt(),
            distanceMeters = distance.toInt(),
            strideCount = strides,
            powerWatts = power,
            energyKcal = energy.toInt(),
            resistanceLevel = resistance.toDouble(),
        )
        return Point(sample, heartRate.toInt())
    }

    companion object {
        const val NAME = "Demostración"
    }
}
