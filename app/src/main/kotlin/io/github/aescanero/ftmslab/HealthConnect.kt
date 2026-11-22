package io.github.aescanero.ftmslab

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Velocity
import io.github.aescanero.ftmslab.data.SampleEntity
import io.github.aescanero.ftmslab.data.SessionEntity
import io.github.aescanero.ftmslab.protocol.DemoWorkout
import io.github.aescanero.ftmslab.protocol.MachineType
import java.time.Instant
import java.time.ZoneId
import kotlin.reflect.KClass

enum class HealthConnectStatus {
    /** Health Connect no existe en este dispositivo. */
    UNAVAILABLE,

    /** Hay que instalar o actualizar el proveedor de Health Connect. */
    UPDATE_REQUIRED,

    /** Disponible, pero faltan permisos de escritura. */
    NOT_GRANTED,

    GRANTED,
}

/**
 * Escribe en Health Connect las sesiones terminadas: el ejercicio, la distancia, las calorías y las
 * series de pulso, potencia y velocidad. Cada registro lleva un identificador propio derivado de la
 * sesión, así que reenviar una sesión la actualiza en lugar de duplicarla.
 */
class HealthConnectWriter(private val context: Context) {
    private val types: List<KClass<out Record>> = listOf(
        ExerciseSessionRecord::class,
        DistanceRecord::class,
        ActiveCaloriesBurnedRecord::class,
        HeartRateRecord::class,
        PowerRecord::class,
        SpeedRecord::class,
    )

    val permissions: Set<String> = types.map { HealthPermission.getWritePermission(it) }.toSet()

    private fun client(): HealthConnectClient? =
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }

    suspend fun status(): HealthConnectStatus = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> {
            val granted = runCatching { client()?.permissionController?.getGrantedPermissions() }.getOrNull().orEmpty()
            if (granted.containsAll(permissions)) HealthConnectStatus.GRANTED else HealthConnectStatus.NOT_GRANTED
        }
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectStatus.UPDATE_REQUIRED
        else -> HealthConnectStatus.UNAVAILABLE
    }

    /** Escribe una sesión; devuelve false si no se pudo (sin Health Connect, sin permisos o error). */
    suspend fun write(session: SessionEntity, samples: List<SampleEntity>): Boolean {
        if (session.machineName == DemoWorkout.NAME || samples.isEmpty()) return false
        val client = client() ?: return false
        val granted = runCatching { client.permissionController.getGrantedPermissions() }.getOrNull().orEmpty()
        fun allowed(type: KClass<out Record>) = HealthPermission.getWritePermission(type) in granted
        if (!allowed(ExerciseSessionRecord::class)) return false

        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(session.startedAtMs)
        // Las muestras deben quedar dentro del intervalo: el final nunca antes de la última muestra.
        val end = Instant.ofEpochMilli(maxOf(session.endedAtMs, samples.last().timestampMs + 1000))
        val startOffset = zone.rules.getOffset(start)
        val endOffset = zone.rules.getOffset(end)
        // La versión crece en cada envío: así un reenvío (p. ej. tras corregir calorías) actualiza los datos.
        val version = System.currentTimeMillis()
        fun metadata(kind: String) = Metadata.activelyRecorded(
            device = Device(type = Device.TYPE_PHONE),
            clientRecordId = "ftmslab-${session.startedAtMs}-$kind",
            clientRecordVersion = version,
        )
        // Una muestra cada 5 s basta para las series y mantiene los registros pequeños.
        val series = samples.filterIndexed { index, _ -> index % 6 == 0 }

        val records = buildList<Record> {
            add(
                ExerciseSessionRecord(
                    startTime = start,
                    startZoneOffset = startOffset,
                    endTime = end,
                    endZoneOffset = endOffset,
                    metadata = metadata("session"),
                    exerciseType = when (MachineType.fromName(session.machineType)) {
                        MachineType.INDOOR_BIKE -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY
                        MachineType.CROSS_TRAINER, null -> ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL
                        else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
                    },
                    title = session.machineName,
                ),
            )
            if (allowed(DistanceRecord::class) && session.distanceMeters > 0) {
                add(
                    DistanceRecord(
                        startTime = start,
                        startZoneOffset = startOffset,
                        endTime = end,
                        endZoneOffset = endOffset,
                        distance = Length.meters(session.distanceMeters.toDouble()),
                        metadata = metadata("distance"),
                    ),
                )
            }
            if (allowed(ActiveCaloriesBurnedRecord::class) && session.kcal > 0) {
                add(
                    ActiveCaloriesBurnedRecord(
                        startTime = start,
                        startZoneOffset = startOffset,
                        endTime = end,
                        endZoneOffset = endOffset,
                        energy = Energy.kilocalories(session.kcal.toDouble()),
                        metadata = metadata("calories"),
                    ),
                )
            }
            val heartRates = series.mapNotNull { s ->
                s.heartRate?.takeIf { it in 1..300 }?.let { HeartRateRecord.Sample(Instant.ofEpochMilli(s.timestampMs), it.toLong()) }
            }
            if (allowed(HeartRateRecord::class) && heartRates.isNotEmpty()) {
                add(
                    HeartRateRecord(
                        startTime = start,
                        startZoneOffset = startOffset,
                        endTime = end,
                        endZoneOffset = endOffset,
                        samples = heartRates,
                        metadata = metadata("heart-rate"),
                    ),
                )
            }
            val power = series.filter { it.powerWatts > 0 }
                .map { PowerRecord.Sample(Instant.ofEpochMilli(it.timestampMs), Power.watts(it.powerWatts.toDouble())) }
            if (allowed(PowerRecord::class) && power.isNotEmpty()) {
                add(
                    PowerRecord(
                        startTime = start,
                        startZoneOffset = startOffset,
                        endTime = end,
                        endZoneOffset = endOffset,
                        samples = power,
                        metadata = metadata("power"),
                    ),
                )
            }
            val speed = series.filter { it.speedKmh > 0 }
                .map { SpeedRecord.Sample(Instant.ofEpochMilli(it.timestampMs), Velocity.kilometersPerHour(it.speedKmh)) }
            if (allowed(SpeedRecord::class) && speed.isNotEmpty()) {
                add(
                    SpeedRecord(
                        startTime = start,
                        startZoneOffset = startOffset,
                        endTime = end,
                        endZoneOffset = endOffset,
                        samples = speed,
                        metadata = metadata("speed"),
                    ),
                )
            }
        }
        return runCatching { client.insertRecords(records) }.isSuccess
    }
}
