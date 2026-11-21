package io.github.aescanero.fs2ftms.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneOffset

class StatsTest {
    private val day = Stats.DAY_MS
    private val now = 1_795_000_000_000L

    private fun session(daysAgo: Int, seconds: Int, cadence: Int, heartRate: Int?) = SessionEntity(
        startedAtMs = now - daysAgo * day,
        endedAtMs = now - daysAgo * day + seconds * 1000L,
        durationSeconds = seconds,
        distanceMeters = seconds * 4,
        machineKcal = seconds / 10,
        averageCadence = cadence,
        averageHeartRate = heartRate,
        finished = true,
    )

    @Test
    fun averagesAreWeightedByDuration() {
        val stats = Stats.of(listOf(session(1, 600, 40, 120), session(2, 1800, 60, null)))
        assertEquals(2, stats.sessions)
        assertEquals(2400, stats.seconds)
        assertEquals(9600, stats.meters)
        assertEquals(240, stats.kcal)
        assertEquals(55.0, stats.averages[StatParam.CADENCE]!!, 0.001)
        // El pulso solo cuenta en la sesión que lo tiene.
        assertEquals(120.0, stats.averages[StatParam.HEART_RATE]!!, 0.001)
        assertNull(stats.averages[StatParam.POWER])
    }

    @Test
    fun periodsAndMonths() {
        val sessions = listOf(session(1, 600, 40, null), session(10, 600, 50, null), session(40, 600, 60, null))
        assertEquals(1, Stats.lastDays(sessions, 7, now).sessions)
        assertEquals(2, Stats.lastDays(sessions, 30, now).sessions)
        val months = Stats.monthly(sessions, ZoneOffset.UTC)
        assertEquals(sessions.size, months.sumOf { it.second.sessions })
        assertEquals(months.map { it.first }.sortedDescending(), months.map { it.first })
        assertEquals(YearMonth.of(2026, 11), months.first().first)
    }

    @Test
    fun emptyPeriod() {
        assertEquals(PeriodStats.EMPTY, Stats.of(emptyList()))
    }
}
