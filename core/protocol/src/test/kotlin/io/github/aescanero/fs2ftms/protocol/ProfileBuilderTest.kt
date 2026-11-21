package io.github.aescanero.fs2ftms.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** Casos construidos a partir del volcado real de la Merach MRK-E27 (14-11-2026). */
class ProfileBuilderTest {
    private fun hex(text: String) = text.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    private val crossTrainer = MachineType.CROSS_TRAINER.dataCharacteristic
    private val merachCharacteristics = setOf(
        FtmsUuids.FEATURE,
        FtmsUuids.TRAINING_STATUS,
        FtmsUuids.short(0x2AD4),
        FtmsUuids.short(0x2AD6),
        FtmsUuids.CONTROL_POINT,
        FtmsUuids.MACHINE_STATUS,
        crossTrainer,
    )
    private val merachFrames = listOf(
        "9F 00 00 AF 02 15 00 00 19 00 15 00 14 00 0A 00",
        "00 2F 00 18 03 0E 00 16 00 00 00 00 00 00 00 0B 00",
        "9F 00 00 0C 03 1A 00 00 1D 00 19 00 14 00 0A 00",
        "00 2F 00 97 03 10 00 1B 00 00 00 00 00 00 00 0C 00",
    ).map { crossTrainer to hex(it) }

    private fun input(
        characteristics: Set<UUID> = merachCharacteristics,
        serviceData: ByteArray? = hex("01 02 00"),
        notifications: List<Pair<UUID, ByteArray>> = merachFrames,
    ) = CaptureInput(
        address = "A4:C1:38:7F:25:49",
        name = "MRK-E27-B78B",
        serviceData = serviceData,
        ftmsCharacteristics = characteristics,
        feature = hex("C7 52 00 00 04 00 00 00"),
        resistanceRange = hex("0A 00 A0 00 0A 00"),
        manufacturer = ProfileBuilder.ascii(hex("4D 52 4B 00 00")),
        model = ProfileBuilder.ascii(hex("4D 52 2D 45 32 37 00 00")),
        firmware = "3.4.2.4",
        notifications = notifications,
        capturedAtMs = 1,
    )

    @Test
    fun merachIsASupportedCrossTrainer() {
        val profile = ProfileBuilder.build(input())
        assertEquals(ProfileStatus.SUPPORTED, profile.status)
        assertEquals(MachineType.CROSS_TRAINER, profile.type)
        assertEquals(
            setOf(
                Metric.SPEED,
                Metric.DISTANCE,
                Metric.CADENCE,
                Metric.STRIDES,
                Metric.RESISTANCE,
                Metric.POWER,
                Metric.ENERGY,
                Metric.ELAPSED_TIME,
            ),
            profile.metrics,
        )
        assertEquals("MRK", profile.manufacturer)
        assertEquals("MR-E27", profile.model)
        assertEquals(1.0, profile.resistanceMin!!, 0.001)
        assertEquals(16.0, profile.resistanceMax!!, 0.001)
        assertTrue(Metric.POWER in profile.declaredMetrics)
    }

    @Test
    fun withoutNotificationsThereIsNoData() {
        val profile = ProfileBuilder.build(input(notifications = emptyList()))
        assertEquals(ProfileStatus.NO_DATA, profile.status)
        assertEquals(MachineType.CROSS_TRAINER, profile.type)
    }

    @Test
    fun withoutFtmsItIsNotSupported() {
        val profile = ProfileBuilder.build(input(characteristics = emptySet(), serviceData = null, notifications = emptyList()))
        assertEquals(ProfileStatus.NO_FTMS, profile.status)
    }

    @Test
    fun rowerIsIdentifiedButNotSupported() {
        val rower = MachineType.ROWER.dataCharacteristic
        val profile = ProfileBuilder.build(
            input(characteristics = setOf(FtmsUuids.FEATURE, rower), serviceData = hex("01 10 00"), notifications = emptyList()),
        )
        assertEquals(ProfileStatus.TYPE_NOT_SUPPORTED, profile.status)
        assertEquals(MachineType.ROWER, profile.type)
    }

    @Test
    fun typeFromAdvertisingWhenNoDataCharacteristicIsVisible() {
        val profile = ProfileBuilder.build(
            input(characteristics = setOf(FtmsUuids.FEATURE), serviceData = hex("01 20 00"), notifications = emptyList()),
        )
        assertEquals(MachineType.INDOOR_BIKE, profile.type)
        assertEquals(ProfileStatus.NO_DATA, profile.status)
    }

    @Test
    fun indoorBikeFrameFromTheSpecification() {
        // Flags 0x0874: cadencia, distancia, resistencia, potencia, tiempo; velocidad presente.
        val frame = hex("74 08 B0 04 B4 00 2C 01 00 32 00 78 00 3C 00")
        val parsed = IndoorBikeDataParser().accept(frame)!!
        assertEquals(12.0, parsed.sample.speedKmh, 0.001)
        assertEquals(90, parsed.sample.cadencePerMinute)
        assertEquals(300, parsed.sample.distanceMeters)
        assertEquals(5.0, parsed.sample.resistanceLevel!!, 0.001)
        assertEquals(120, parsed.sample.powerWatts)
        assertEquals(60, parsed.sample.elapsedSeconds)
        assertEquals(
            setOf(Metric.SPEED, Metric.CADENCE, Metric.DISTANCE, Metric.RESISTANCE, Metric.POWER, Metric.ELAPSED_TIME),
            parsed.present,
        )
    }

    @Test
    fun serviceDataType() {
        assertEquals(MachineType.CROSS_TRAINER, MachineType.fromServiceData(hex("01 02 00")))
        assertEquals(MachineType.INDOOR_BIKE, MachineType.fromServiceData(hex("01 20 00")))
        assertEquals(null, MachineType.fromServiceData(hex("01")))
    }
}
