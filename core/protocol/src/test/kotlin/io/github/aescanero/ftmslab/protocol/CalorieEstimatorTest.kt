package io.github.aescanero.ftmslab.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalorieEstimatorTest {
    @Test
    fun keytelFormula() {
        assertEquals(14.25, CalorieEstimator(52, 80, male = true).kcalPerMinute(140), 0.01)
        assertEquals(8.83, CalorieEstimator(40, 65, male = false).kcalPerMinute(140), 0.01)
        assertEquals(0.0, CalorieEstimator(30, 60, male = true).kcalPerMinute(40), 0.0001)
    }

    @Test
    fun totalOverSession() {
        val estimator = CalorieEstimator(52, 80, male = true)
        val points = List(600) { (it + 1) to 140 }
        assertEquals(142.5, estimator.total(points)!!, 0.2)
        assertNull(estimator.total(listOf(1 to null, 2 to null)))
    }
}
