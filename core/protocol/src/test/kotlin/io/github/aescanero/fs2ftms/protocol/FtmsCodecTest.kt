package io.github.aescanero.fs2ftms.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtmsCodecTest {
    private val sample = WorkoutSample(
        elapsedSeconds = 65,
        speedKmh = 4.32,
        cadencePerMinute = 120,
        averageCadencePerMinute = 118,
        distanceMeters = 78,
        strideCount = 65.0,
        powerWatts = 96,
        energyKcal = 6,
    )

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun crossTrainerDataLayout() {
        val expected = bytes(
            0x1C, 0x25, 0x00, // flags
            0xB0, 0x01, // 4,32 km/h
            0x4E, 0x00, 0x00, // 78 m
            0x78, 0x00, 0x76, 0x00, // 120 y 118 pasos/min
            0x8A, 0x02, // 65,0 zancadas
            0x60, 0x00, // 96 W
            0x06, 0x00, 0xFF, 0xFF, 0xFF, // 6 kcal, resto no disponible
            0x41, 0x00, // 65 s
        )
        assertArrayEquals(expected, FtmsCodec.crossTrainerData(sample))
    }

    @Test
    fun indoorBikeDataLayout() {
        val expected = bytes(
            0x54, 0x09,
            0xB0, 0x01,
            0xF0, 0x00,
            0x4E, 0x00, 0x00,
            0x60, 0x00,
            0x06, 0x00, 0xFF, 0xFF, 0xFF,
            0x41, 0x00,
        )
        assertArrayEquals(expected, FtmsCodec.indoorBikeData(sample))
    }

    @Test
    fun featureDeclaresOnlySentMetrics() {
        assertArrayEquals(bytes(0x44, 0x53, 0, 0, 0, 0, 0, 0), FtmsCodec.feature(MachineType.CROSS_TRAINER))
        assertArrayEquals(bytes(0x86, 0x52, 0, 0, 0, 0, 0, 0), FtmsCodec.feature(MachineType.INDOOR_BIKE))
    }

    @Test
    fun controlPoint() {
        val request = FtmsCodec.parseControlPoint(bytes(0x00))!!
        assertEquals("Request Control", request.name)
        assertEquals(FtmsCodec.RESULT_SUCCESS, FtmsCodec.resultFor(request))
        val target = FtmsCodec.parseControlPoint(bytes(0x04, 0x0A))!!
        assertEquals(FtmsCodec.RESULT_NOT_SUPPORTED, FtmsCodec.resultFor(target))
        assertArrayEquals(bytes(0x80, 0x07, 0x01), FtmsCodec.controlPointResponse(0x07, FtmsCodec.RESULT_SUCCESS))
        assertNull(FtmsCodec.parseControlPoint(ByteArray(0)))
    }

    @Test
    fun advertisingServiceData() {
        assertArrayEquals(bytes(0x01, 0x02, 0x00), FtmsCodec.advertisingServiceData(MachineType.CROSS_TRAINER))
        assertArrayEquals(bytes(0x01, 0x20, 0x00), FtmsCodec.advertisingServiceData(MachineType.INDOOR_BIKE))
    }

    @Test
    fun simulatorAccumulates() {
        val simulator = WorkoutSimulator()
        var last = simulator.next(120)
        repeat(59) { last = simulator.next(120) }
        assertEquals(60, last.elapsedSeconds)
        assertEquals(72, last.distanceMeters)
        assertEquals(60.0, last.strideCount, 0.001)
        assertEquals(120, last.averageCadencePerMinute)
    }
}
