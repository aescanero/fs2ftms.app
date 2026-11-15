package io.github.aescanero.fs2ftms.data

import org.json.JSONArray
import org.json.JSONObject

object Sessions {
    /** Calcula los totales de la sesión a partir de sus muestras. */
    fun summarize(session: SessionEntity, samples: List<SampleEntity>, endedAtMs: Long): SessionEntity {
        val last = samples.lastOrNull() ?: return session.copy(endedAtMs = endedAtMs, finished = true)
        val moving = samples.filter { it.cadence > 0 }
        val heartRates = samples.mapNotNull { it.heartRate }
        val resistances = samples.mapNotNull { it.resistance }
        return session.copy(
            endedAtMs = endedAtMs,
            durationSeconds = last.elapsedSeconds,
            distanceMeters = last.distanceMeters,
            machineKcal = last.energyKcal,
            strideCount = last.strideCount,
            averageCadence = if (moving.isEmpty()) 0 else moving.map { it.cadence }.average().toInt(),
            averagePowerWatts = if (moving.isEmpty()) 0 else moving.map { it.powerWatts }.average().toInt(),
            averageSpeedKmh = if (moving.isEmpty()) 0.0 else moving.map { it.speedKmh }.average(),
            averageResistance = if (resistances.isEmpty()) null else resistances.average(),
            averageHeartRate = if (heartRates.isEmpty()) null else heartRates.average().toInt(),
            maxHeartRate = heartRates.maxOrNull(),
            finished = true,
        )
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)

    fun toJson(session: SessionEntity, samples: List<SampleEntity>): String {
        val root = JSONObject()
            .put("format", "fs2ftms-session-v1")
            .put("startedAtMs", session.startedAtMs)
            .put("endedAtMs", session.endedAtMs)
            .put("durationSeconds", session.durationSeconds)
            .put("distanceMeters", session.distanceMeters)
            .put("machineKcal", session.machineKcal)
            .putNullable("manualKcal", session.manualKcal)
            .put("averageCadence", session.averageCadence)
            .put("averagePowerWatts", session.averagePowerWatts)
            .put("averageSpeedKmh", session.averageSpeedKmh)
            .putNullable("averageResistance", session.averageResistance)
            .put("strideCount", session.strideCount)
            .putNullable("averageHeartRate", session.averageHeartRate)
            .putNullable("maxHeartRate", session.maxHeartRate)
            .putNullable("machineName", session.machineName)
        val array = JSONArray()
        samples.forEach { s ->
            array.put(
                JSONObject()
                    .put("t", s.timestampMs)
                    .put("elapsedSeconds", s.elapsedSeconds)
                    .put("speedKmh", s.speedKmh)
                    .put("cadence", s.cadence)
                    .put("distanceMeters", s.distanceMeters)
                    .put("strideCount", s.strideCount)
                    .put("powerWatts", s.powerWatts)
                    .put("energyKcal", s.energyKcal)
                    .putNullable("resistance", s.resistance)
                    .putNullable("heartRate", s.heartRate),
            )
        }
        root.put("samples", array)
        return root.toString(2)
    }
}
