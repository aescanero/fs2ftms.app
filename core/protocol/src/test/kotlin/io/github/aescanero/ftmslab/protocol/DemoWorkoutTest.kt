package io.github.aescanero.ftmslab.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoWorkoutTest {
    @Test
    fun producesPlausibleSession() {
        val demo = DemoWorkout()
        val points = List(1500) { demo.next() }
        val last = points.last()
        assertEquals(1500, last.sample.elapsedSeconds)
        assertTrue(last.sample.distanceMeters in 4000..9000)
        assertTrue(last.sample.energyKcal in 100..400)
        assertTrue(points.all { it.heartRate in 70..185 })
        // Debe recorrer varias zonas para poder revisar el indicador.
        val zones = HeartRateZones(180)
        assertTrue(points.mapNotNull { zones.zoneOf(it.heartRate) }.toSet().size >= 3)
    }
}
