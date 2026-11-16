package io.github.aescanero.fs2ftms.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionAccumulatorTest {
    private fun sample(elapsed: Int, distance: Int, energy: Int, strides: Double) = WorkoutSample(
        elapsedSeconds = elapsed,
        speedKmh = 10.0,
        cadencePerMinute = 40,
        averageCadencePerMinute = 40,
        distanceMeters = distance,
        strideCount = strides,
        powerWatts = 30,
        energyKcal = energy,
    )

    @Test
    fun startsFromZeroAndSurvivesMachineReset() {
        val accumulator = SessionAccumulator()
        assertEquals(0, accumulator.add(sample(100, 300, 5, 40.0)).distanceMeters)
        val second = accumulator.add(sample(110, 330, 6, 44.0))
        assertEquals(10, second.elapsedSeconds)
        assertEquals(30, second.distanceMeters)
        assertEquals(1, second.energyKcal)
        assertEquals(4.0, second.strideCount, 0.001)
        // La máquina se reinicia: sus contadores vuelven a empezar.
        val third = accumulator.add(sample(2, 5, 0, 1.0))
        assertEquals(12, third.elapsedSeconds)
        assertEquals(35, third.distanceMeters)
        assertEquals(1, third.energyKcal)
        assertEquals(5.0, third.strideCount, 0.001)
    }

    @Test
    fun pauseDoesNotCountWhatTheMachineKeptCounting() {
        val accumulator = SessionAccumulator()
        accumulator.add(sample(100, 300, 5, 40.0))
        accumulator.add(sample(110, 330, 6, 44.0))
        accumulator.rebase()
        val resumed = accumulator.add(sample(150, 500, 9, 60.0))
        assertEquals(10, resumed.elapsedSeconds)
        assertEquals(30, resumed.distanceMeters)
        assertEquals(1, resumed.energyKcal)
        val next = accumulator.add(sample(160, 530, 10, 64.0))
        assertEquals(20, next.elapsedSeconds)
        assertEquals(60, next.distanceMeters)
        assertEquals(2, next.energyKcal)
        assertEquals(8.0, next.strideCount, 0.001)
    }

    @Test
    fun heartRateFormats() {
        assertEquals(128, HeartRateParser.parse(byteArrayOf(0x00, 0x80.toByte())))
        assertEquals(200, HeartRateParser.parse(byteArrayOf(0x01, 0xC8.toByte(), 0x00)))
        assertNull(HeartRateParser.parse(byteArrayOf(0x00)))
        assertNull(HeartRateParser.parse(byteArrayOf(0x00, 0x00)))
    }
}
