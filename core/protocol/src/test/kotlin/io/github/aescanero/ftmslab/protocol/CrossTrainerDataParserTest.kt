package io.github.aescanero.ftmslab.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Tramas reales capturadas de la Merach MRK-E27 (volcado del 14-11-2026). */
class CrossTrainerDataParserTest {
    private fun hex(text: String) = text.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    private val first = hex("9F 00 00 8A 03 44 00 00 25 00 1C 00 3C 00 1E 00")
    private val last = hex("00 2F 00 94 04 29 00 25 00 01 00 00 00 00 00 1B 00")

    @Test
    fun mergesSplitPackets() {
        val parser = CrossTrainerDataParser()
        assertNull(parser.accept(first))
        val sample = parser.accept(last)?.sample
        assertNotNull(sample)
        sample!!
        assertEquals(11.72, sample.speedKmh, 0.001)
        assertEquals(9.06, sample.averageSpeedKmh!!, 0.001)
        assertEquals(68, sample.distanceMeters)
        assertEquals(37, sample.cadencePerMinute)
        assertEquals(28, sample.averageCadencePerMinute)
        assertEquals(6.0, sample.strideCount, 0.001)
        assertEquals(3.0, sample.resistanceLevel!!, 0.001)
        assertEquals(41, sample.powerWatts)
        assertEquals(37, sample.averagePowerWatts)
        assertEquals(1, sample.energyKcal)
        assertEquals(27, sample.elapsedSeconds)
    }

    @Test
    fun truncatedPacketIsIgnored() {
        val parser = CrossTrainerDataParser()
        assertNull(parser.accept(hex("00 2F 00 94")))
        assertNull(parser.accept(hex("9F 00")))
    }
}
