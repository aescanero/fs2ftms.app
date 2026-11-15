package io.github.aescanero.fs2ftms.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Resumen de una sesión grabada. Los totales se recalculan al finalizarla. */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val durationSeconds: Int = 0,
    val distanceMeters: Int = 0,
    val machineKcal: Int = 0,
    val manualKcal: Int? = null,
    val averageCadence: Int = 0,
    val averagePowerWatts: Int = 0,
    val averageSpeedKmh: Double = 0.0,
    val averageResistance: Double? = null,
    val strideCount: Double = 0.0,
    val averageHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val machineName: String? = null,
    val finished: Boolean = false,
) {
    /** Calorías a mostrar: la corrección manual manda sobre la cifra de la máquina. */
    val kcal: Int get() = manualKcal ?: machineKcal
}

@Entity(
    tableName = "samples",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMs: Long,
    val elapsedSeconds: Int,
    val speedKmh: Double,
    val cadence: Int,
    val distanceMeters: Int,
    val strideCount: Double,
    val powerWatts: Int,
    val energyKcal: Int,
    val resistance: Double?,
    val heartRate: Int?,
)
