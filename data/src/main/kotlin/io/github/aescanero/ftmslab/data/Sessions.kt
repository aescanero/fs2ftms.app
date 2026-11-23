package io.github.aescanero.ftmslab.data

import org.json.JSONArray
import org.json.JSONObject

object Sessions {
    /** Calcula los totales de la sesión a partir de sus muestras. */
    fun summarize(
        session: SessionEntity,
        samples: List<SampleEntity>,
        endedAtMs: Long,
        estimatedKcal: Int? = null,
    ): SessionEntity {
        val last = samples.lastOrNull() ?: return session.copy(endedAtMs = endedAtMs, finished = true, updatedAtMs = endedAtMs)
        val moving = samples.filter { it.cadence > 0 }
        val heartRates = samples.mapNotNull { it.heartRate }
        val resistances = samples.mapNotNull { it.resistance }
        return session.copy(
            updatedAtMs = endedAtMs,
            endedAtMs = endedAtMs,
            durationSeconds = last.elapsedSeconds,
            distanceMeters = last.distanceMeters,
            machineKcal = last.energyKcal,
            estimatedKcal = estimatedKcal,
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

    /**
     * Corrige sesiones grabadas antes de detectar los saltos imposibles: si el tiempo de la máquina
     * avanza más que el reloj entre dos muestras, descuenta ese salto de todas las posteriores.
     * Devuelve null si no había nada que corregir.
     */
    fun removeImpossibleJumps(samples: List<SampleEntity>): List<SampleEntity>? {
        var elapsed = 0
        var distance = 0
        var energy = 0
        var strides = 0.0
        var changed = false
        val fixed = ArrayList<SampleEntity>(samples.size)
        samples.forEachIndexed { index, sample ->
            if (index > 0) {
                val previous = samples[index - 1]
                val wallSeconds = (sample.timestampMs - previous.timestampMs) / 1000.0
                if (sample.elapsedSeconds - previous.elapsedSeconds > wallSeconds + 5) {
                    elapsed += sample.elapsedSeconds - previous.elapsedSeconds
                    distance += sample.distanceMeters - previous.distanceMeters
                    energy += sample.energyKcal - previous.energyKcal
                    strides += sample.strideCount - previous.strideCount
                    changed = true
                }
            }
            fixed.add(
                sample.copy(
                    elapsedSeconds = sample.elapsedSeconds - elapsed,
                    distanceMeters = sample.distanceMeters - distance,
                    energyKcal = sample.energyKcal - energy,
                    strideCount = sample.strideCount - strides,
                ),
            )
        }
        return fixed.takeIf { changed }
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)

    fun toJson(session: SessionEntity, samples: List<SampleEntity>): String {
        val root = JSONObject()
            .put("format", "ftmslab-session-v1")
            .put("startedAtMs", session.startedAtMs)
            .put("endedAtMs", session.endedAtMs)
            .put("durationSeconds", session.durationSeconds)
            .put("distanceMeters", session.distanceMeters)
            .put("machineKcal", session.machineKcal)
            .putNullable("manualKcal", session.manualKcal)
            .putNullable("estimatedKcal", session.estimatedKcal)
            .put("averageCadence", session.averageCadence)
            .put("averagePowerWatts", session.averagePowerWatts)
            .put("averageSpeedKmh", session.averageSpeedKmh)
            .putNullable("averageResistance", session.averageResistance)
            .put("strideCount", session.strideCount)
            .putNullable("averageHeartRate", session.averageHeartRate)
            .putNullable("maxHeartRate", session.maxHeartRate)
            .putNullable("machineName", session.machineName)
            .putNullable("machineType", session.machineType)
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
