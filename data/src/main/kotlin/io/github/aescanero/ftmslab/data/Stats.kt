package io.github.aescanero.ftmslab.data

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Parámetros que se comparan entre la sesión actual y los periodos anteriores. */
enum class StatParam {
    CADENCE,
    SPEED,
    POWER,
    RESISTANCE,
    HEART_RATE,
    ;

    /** Valor medio del parámetro en una sesión; null si la sesión no lo tiene. */
    fun of(session: SessionEntity): Double? = when (this) {
        CADENCE -> session.averageCadence.toDouble().takeIf { it > 0 }
        SPEED -> session.averageSpeedKmh.takeIf { it > 0 }
        POWER -> session.averagePowerWatts.toDouble().takeIf { it > 0 }
        RESISTANCE -> session.averageResistance?.takeIf { it > 0 }
        HEART_RATE -> session.averageHeartRate?.toDouble()
    }
}

/** Totales y medias de un conjunto de sesiones. Las medias se ponderan por la duración. */
data class PeriodStats(
    val sessions: Int,
    val seconds: Int,
    val meters: Int,
    val kcal: Int,
    val averages: Map<StatParam, Double>,
) {
    companion object {
        val EMPTY = PeriodStats(0, 0, 0, 0, emptyMap())
    }
}

object Stats {
    const val DAY_MS = 24L * 60 * 60 * 1000

    fun of(sessions: List<SessionEntity>): PeriodStats {
        if (sessions.isEmpty()) return PeriodStats.EMPTY
        val averages = StatParam.entries.mapNotNull { param ->
            val weighted = sessions.mapNotNull { s -> param.of(s)?.let { it to s.durationSeconds.coerceAtLeast(1) } }
            val weight = weighted.sumOf { it.second }
            if (weight == 0) null else param to weighted.sumOf { it.first * it.second } / weight
        }.toMap()
        return PeriodStats(
            sessions = sessions.size,
            seconds = sessions.sumOf { it.durationSeconds },
            meters = sessions.sumOf { it.distanceMeters },
            kcal = sessions.sumOf { it.kcal },
            averages = averages,
        )
    }

    /** Sesiones iniciadas en los últimos [days] días respecto a [nowMs]. */
    fun lastDays(sessions: List<SessionEntity>, days: Int, nowMs: Long): PeriodStats =
        of(sessions.filter { it.startedAtMs >= nowMs - days * DAY_MS && it.startedAtMs <= nowMs })

    /** Agregado por mes natural, del más reciente al más antiguo. */
    fun monthly(sessions: List<SessionEntity>, zone: ZoneId): List<Pair<YearMonth, PeriodStats>> =
        sessions
            .groupBy { YearMonth.from(Instant.ofEpochMilli(it.startedAtMs).atZone(zone)) }
            .map { (month, list) -> month to of(list) }
            .sortedByDescending { it.first }
}
