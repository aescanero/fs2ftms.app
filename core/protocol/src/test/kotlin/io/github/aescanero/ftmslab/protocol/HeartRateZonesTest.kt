package io.github.aescanero.ftmslab.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateZonesTest {
    private val zones = HeartRateZones(180)

    @Test
    fun boundaries() {
        assertNull(zones.zoneOf(89))
        assertEquals(HeartRateZone.RECOVERY, zones.zoneOf(90))
        assertEquals(HeartRateZone.RECOVERY, zones.zoneOf(107))
        assertEquals(HeartRateZone.LIGHT_AEROBIC, zones.zoneOf(108))
        assertEquals(HeartRateZone.AEROBIC, zones.zoneOf(126))
        assertEquals(HeartRateZone.ANAEROBIC, zones.zoneOf(144))
        assertEquals(HeartRateZone.MAXIMUM, zones.zoneOf(162))
        assertEquals(HeartRateZone.MAXIMUM, zones.zoneOf(200))
    }

    @Test
    fun fractionIsClamped() {
        assertEquals(0f, zones.fraction(60), 0.0001f)
        assertEquals(0.5f, zones.fraction(135), 0.0001f)
        assertEquals(1f, zones.fraction(190), 0.0001f)
    }

    @Test
    fun timeInZonesIgnoresGapsAndMissingPulse() {
        val totals = zones.timeInZones(
            listOf(1 to 100, 2 to 100, 3 to 130, 4 to null, 5 to 130, 60 to 150, 61 to 80),
        )
        assertEquals(2, totals[HeartRateZone.RECOVERY])
        assertEquals(2, totals[HeartRateZone.AEROBIC])
        // El salto de 55 s (pausa) se limita a 5 s.
        assertEquals(5, totals[HeartRateZone.ANAEROBIC])
        assertEquals(1, totals[null])
    }

    @Test
    fun estimate() {
        assertEquals(180, HeartRateZones.estimateMax(40))
    }
}
