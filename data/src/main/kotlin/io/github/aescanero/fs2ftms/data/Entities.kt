package io.github.aescanero.fs2ftms.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Resumen de una sesión grabada. Los totales se recalculan al finalizarla. */
enum class KcalSource { MANUAL, HEART_RATE, MACHINE }

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val durationSeconds: Int = 0,
    val distanceMeters: Int = 0,
    val machineKcal: Int = 0,
    val manualKcal: Int? = null,
    /** Estimación por pulso (edad, peso y sexo); null si faltan datos o no hubo pulso. */
    val estimatedKcal: Int? = null,
    val averageCadence: Int = 0,
    val averagePowerWatts: Int = 0,
    val averageSpeedKmh: Double = 0.0,
    val averageResistance: Double? = null,
    val strideCount: Double = 0.0,
    val averageHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val machineName: String? = null,
    /** Tipo FTMS de la máquina (MachineType.name); null en sesiones anteriores a los perfiles. */
    val machineType: String? = null,
    val finished: Boolean = false,
    /** Momento en que se escribió en Health Connect; null si aún no se ha enviado. */
    val healthConnectAtMs: Long? = null,
) {
    /** Calorías a mostrar: corrección manual, si no la estimación por pulso, si no la máquina. */
    val kcal: Int get() = manualKcal ?: estimatedKcal ?: machineKcal

    val kcalSource: KcalSource
        get() = when {
            manualKcal != null -> KcalSource.MANUAL
            estimatedKcal != null -> KcalSource.HEART_RATE
            else -> KcalSource.MACHINE
        }
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
