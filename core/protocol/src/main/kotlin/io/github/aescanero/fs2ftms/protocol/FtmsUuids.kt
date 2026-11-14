package io.github.aescanero.fs2ftms.protocol

import java.util.UUID

/** UUID de 16 bits del perfil Fitness Machine y servicios auxiliares. */
object FtmsUuids {
    fun short(value: Int): UUID =
        UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", value))

    val FITNESS_MACHINE_SERVICE: UUID = short(0x1826)
    val FEATURE: UUID = short(0x2ACC)
    val CROSS_TRAINER_DATA: UUID = short(0x2ACE)
    val INDOOR_BIKE_DATA: UUID = short(0x2AD2)
    val TRAINING_STATUS: UUID = short(0x2AD3)
    val CONTROL_POINT: UUID = short(0x2AD9)
    val MACHINE_STATUS: UUID = short(0x2ADA)

    val BATTERY_SERVICE: UUID = short(0x180F)
    val BATTERY_LEVEL: UUID = short(0x2A19)

    val DEVICE_INFORMATION_SERVICE: UUID = short(0x180A)
    val MANUFACTURER_NAME: UUID = short(0x2A29)
    val MODEL_NUMBER: UUID = short(0x2A24)

    val CLIENT_CHARACTERISTIC_CONFIG: UUID = short(0x2902)

    private val names = mapOf(
        FEATURE to "Feature (2ACC)",
        CROSS_TRAINER_DATA to "Cross Trainer Data (2ACE)",
        INDOOR_BIKE_DATA to "Indoor Bike Data (2AD2)",
        TRAINING_STATUS to "Training Status (2AD3)",
        CONTROL_POINT to "Control Point (2AD9)",
        MACHINE_STATUS to "Machine Status (2ADA)",
        BATTERY_LEVEL to "Battery Level (2A19)",
        MANUFACTURER_NAME to "Manufacturer (2A29)",
        MODEL_NUMBER to "Model (2A24)",
    )

    fun nameOf(uuid: UUID): String = names[uuid] ?: uuid.toString()
}
