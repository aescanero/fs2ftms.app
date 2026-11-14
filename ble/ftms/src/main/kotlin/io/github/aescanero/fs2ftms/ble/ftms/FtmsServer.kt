package io.github.aescanero.fs2ftms.ble.ftms

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.os.SystemClock
import io.github.aescanero.fs2ftms.protocol.FtmsCodec
import io.github.aescanero.fs2ftms.protocol.FtmsUuids
import io.github.aescanero.fs2ftms.protocol.MachineType
import io.github.aescanero.fs2ftms.protocol.WorkoutSample
import io.github.aescanero.fs2ftms.protocol.toHex
import java.util.UUID

data class FtmsServerConfig(
    val machineType: MachineType = MachineType.CROSS_TRAINER,
    val batteryService: Boolean = false,
)

/**
 * Servidor GATT FTMS (periférico). Requiere BLUETOOTH_CONNECT y BLUETOOTH_ADVERTISE
 * concedidos antes de llamar a [start]. Toda la actividad del cliente se registra en [telemetry].
 */
@SuppressLint("MissingPermission")
class FtmsServer(
    private val context: Context,
    private val config: FtmsServerConfig,
    private val telemetry: TelemetryRecorder,
) {
    private class Outgoing(
        val device: BluetoothDevice,
        val characteristic: BluetoothGattCharacteristic,
        val confirm: Boolean,
        val value: ByteArray,
        val isData: Boolean,
    )

    private val lock = Any()
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private var server: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private val pendingServices = ArrayDeque<BluetoothGattService>()
    private val devices = HashMap<String, BluetoothDevice>()
    private val connectedAt = HashMap<String, Long>()
    private val subscriptions = HashMap<String, MutableSet<UUID>>()
    private val queue = ArrayDeque<Outgoing>()
    private var inFlightSince = 0L
    private var trainingActive = false

    private lateinit var dataCharacteristic: BluetoothGattCharacteristic
    private lateinit var controlPoint: BluetoothGattCharacteristic
    private lateinit var machineStatus: BluetoothGattCharacteristic
    private lateinit var trainingStatus: BluetoothGattCharacteristic

    fun start(): Boolean {
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            telemetry.event(TelemetryType.ERROR, null, "Bluetooth desactivado o no disponible")
            return false
        }
        if (!adapter.isMultipleAdvertisementSupported) {
            telemetry.event(TelemetryType.ERROR, null, "El adaptador no admite anuncios BLE (modo periférico)")
            return false
        }
        val gattServer = try {
            manager.openGattServer(context, gattCallback)
        } catch (e: SecurityException) {
            telemetry.event(TelemetryType.ERROR, null, "Permiso denegado al abrir el servidor GATT: ${e.message}")
            null
        }
        if (gattServer == null) {
            telemetry.event(TelemetryType.ERROR, null, "No se pudo abrir el servidor GATT")
            return false
        }
        synchronized(lock) {
            server = gattServer
            advertiser = adapter.bluetoothLeAdvertiser
            pendingServices.clear()
            pendingServices.add(buildFitnessMachineService())
            pendingServices.add(buildDeviceInformationService())
            if (config.batteryService) pendingServices.add(buildBatteryService())
        }
        telemetry.update { it.copy(running = true, startedAtMs = System.currentTimeMillis()) }
        telemetry.event(
            TelemetryType.SERVER,
            null,
            "Servidor abierto: ${config.machineType.label}, batería simulada=${config.batteryService}",
        )
        val already = runCatching { manager.getConnectedDevices(BluetoothProfile.GATT) }.getOrNull().orEmpty()
        telemetry.event(
            TelemetryType.SERVER,
            null,
            "Enlaces BLE ya activos al iniciar: " +
                already.joinToString { "${label(it)} ${bond(it)}" }.ifEmpty { "ninguno" },
        )
        addNextService()
        return true
    }

    fun stop() {
        val gattServer: BluetoothGattServer?
        val leAdvertiser: BluetoothLeAdvertiser?
        synchronized(lock) {
            gattServer = server
            leAdvertiser = advertiser
            server = null
            advertiser = null
            queue.clear()
            inFlightSince = 0L
            devices.clear()
            subscriptions.clear()
            connectedAt.clear()
        }
        runCatching { leAdvertiser?.stopAdvertising(advertiseCallback) }
        runCatching { gattServer?.close() }
        telemetry.update { it.copy(running = false, advertising = false, connectedDevices = emptySet()) }
        telemetry.event(TelemetryType.SERVER, null, "Servidor detenido")
    }

    /** Envía una muestra a todos los clientes suscritos a la característica de datos. */
    fun publish(sample: WorkoutSample) {
        val value = FtmsCodec.data(config.machineType, sample)
        synchronized(lock) {
            if (server == null) return
            subscriptions.forEach { (address, set) ->
                val device = devices[address]
                if (device != null && dataCharacteristic.uuid in set) {
                    enqueueLocked(Outgoing(device, dataCharacteristic, false, value, true))
                }
            }
        }
        drain()
    }

    // ---- Servicios -------------------------------------------------------------------------

    private fun cccd() = BluetoothGattDescriptor(
        FtmsUuids.CLIENT_CHARACTERISTIC_CONFIG,
        BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
    )

    private fun buildFitnessMachineService(): BluetoothGattService {
        val service = BluetoothGattService(FtmsUuids.FITNESS_MACHINE_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                FtmsUuids.FEATURE,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ),
        )
        dataCharacteristic = BluetoothGattCharacteristic(
            config.machineType.dataCharacteristic,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0,
        ).apply { addDescriptor(cccd()) }
        trainingStatus = BluetoothGattCharacteristic(
            FtmsUuids.TRAINING_STATUS,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        ).apply { addDescriptor(cccd()) }
        controlPoint = BluetoothGattCharacteristic(
            FtmsUuids.CONTROL_POINT,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        ).apply { addDescriptor(cccd()) }
        machineStatus = BluetoothGattCharacteristic(
            FtmsUuids.MACHINE_STATUS,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0,
        ).apply { addDescriptor(cccd()) }
        service.addCharacteristic(dataCharacteristic)
        service.addCharacteristic(trainingStatus)
        service.addCharacteristic(controlPoint)
        service.addCharacteristic(machineStatus)
        return service
    }

    private fun buildDeviceInformationService(): BluetoothGattService {
        val service =
            BluetoothGattService(FtmsUuids.DEVICE_INFORMATION_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        listOf(FtmsUuids.MANUFACTURER_NAME, FtmsUuids.MODEL_NUMBER).forEach { uuid ->
            service.addCharacteristic(
                BluetoothGattCharacteristic(
                    uuid,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ,
                ),
            )
        }
        return service
    }

    private fun buildBatteryService(): BluetoothGattService {
        val service = BluetoothGattService(FtmsUuids.BATTERY_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                FtmsUuids.BATTERY_LEVEL,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ).apply { addDescriptor(cccd()) },
        )
        return service
    }

    /** Android exige esperar a onServiceAdded antes de añadir el siguiente servicio. */
    private fun addNextService() {
        val gattServer: BluetoothGattServer
        val next: BluetoothGattService?
        synchronized(lock) {
            gattServer = server ?: return
            next = pendingServices.removeFirstOrNull()
        }
        if (next == null) {
            startAdvertising(includeName = true)
        } else if (!gattServer.addService(next)) {
            telemetry.event(TelemetryType.ERROR, null, "addService rechazado para ${next.uuid}")
        }
    }

    // ---- Anuncio ---------------------------------------------------------------------------

    private var advertisingWithName = true

    private fun startAdvertising(includeName: Boolean) {
        val leAdvertiser = synchronized(lock) { advertiser }
        if (leAdvertiser == null) {
            telemetry.event(TelemetryType.ERROR, null, "Anunciante BLE no disponible")
            return
        }
        advertisingWithName = includeName
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val serviceUuid = ParcelUuid(FtmsUuids.FITNESS_MACHINE_SERVICE)
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(serviceUuid)
            .addServiceData(serviceUuid, FtmsCodec.advertisingServiceData(config.machineType))
            .build()
        val scanResponse = AdvertiseData.Builder().setIncludeDeviceName(includeName).build()
        leAdvertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            telemetry.update { it.copy(advertising = true) }
            telemetry.event(
                TelemetryType.ADVERTISING,
                null,
                "Anuncio activo (0x1826, nombre en scan response=$advertisingWithName)",
            )
        }

        override fun onStartFailure(errorCode: Int) {
            if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE && advertisingWithName) {
                telemetry.event(TelemetryType.ADVERTISING, null, "Nombre demasiado largo: reintento sin nombre")
                startAdvertising(includeName = false)
                return
            }
            telemetry.update { it.copy(advertising = false) }
            telemetry.event(TelemetryType.ERROR, null, "Fallo al anunciar, código $errorCode")
        }
    }

    // ---- Cola de notificaciones ------------------------------------------------------------

    private fun enqueueLocked(item: Outgoing) {
        if (item.isData && queue.size >= MAX_QUEUE) {
            telemetry.update { it.copy(notificationsDropped = it.notificationsDropped + 1) }
            return
        }
        queue.addLast(item)
    }

    private fun enqueue(item: Outgoing) {
        synchronized(lock) { enqueueLocked(item) }
        drain()
    }

    /** Android solo admite una notificación en vuelo: la siguiente sale en onNotificationSent. */
    private fun drain() {
        val gattServer: BluetoothGattServer
        val item: Outgoing
        synchronized(lock) {
            gattServer = server ?: return
            val now = SystemClock.elapsedRealtime()
            if (inFlightSince != 0L && now - inFlightSince < IN_FLIGHT_TIMEOUT_MS) return
            item = queue.removeFirstOrNull() ?: run {
                inFlightSince = 0L
                return
            }
            inFlightSince = now
        }
        val status = try {
            gattServer.notifyCharacteristicChanged(item.device, item.characteristic, item.confirm, item.value)
        } catch (e: SecurityException) {
            -1
        }
        if (status == BluetoothStatusCodes.SUCCESS) {
            telemetry.update { it.copy(notificationsSent = it.notificationsSent + 1) }
        } else {
            synchronized(lock) { inFlightSince = 0L }
            telemetry.update { it.copy(notificationsFailed = it.notificationsFailed + 1) }
            if (!item.isData) {
                telemetry.event(
                    TelemetryType.ERROR,
                    label(item.device),
                    "Envío rechazado (código $status) en ${FtmsUuids.nameOf(item.characteristic.uuid)}",
                )
            }
            drain()
        }
    }

    // ---- Callbacks GATT --------------------------------------------------------------------

    private fun label(device: BluetoothDevice): String {
        val name = runCatching { device.name }.getOrNull()
        return if (name.isNullOrBlank()) device.address else "$name (${device.address})"
    }

    private fun bond(device: BluetoothDevice): String = when (runCatching { device.bondState }.getOrNull()) {
        BluetoothDevice.BOND_BONDED -> "[emparejado]"
        BluetoothDevice.BOND_BONDING -> "[emparejando]"
        else -> "[sin emparejar]"
    }

    private fun isSubscribed(device: BluetoothDevice, uuid: UUID): Boolean =
        synchronized(lock) { subscriptions[device.address]?.contains(uuid) == true }

    private fun valueFor(uuid: UUID): ByteArray? = when (uuid) {
        FtmsUuids.FEATURE -> FtmsCodec.feature(config.machineType)
        FtmsUuids.TRAINING_STATUS -> FtmsCodec.trainingStatus(trainingActive)
        FtmsUuids.BATTERY_LEVEL -> byteArrayOf(100)
        FtmsUuids.MANUFACTURER_NAME -> "fs2ftms".toByteArray()
        FtmsUuids.MODEL_NUMBER -> "FS2FTMS-1".toByteArray()
        else -> null
    }

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                telemetry.event(TelemetryType.ERROR, null, "Servicio ${service.uuid} no añadido, estado $status")
            } else {
                telemetry.event(TelemetryType.SERVER, null, "Servicio añadido: ${service.uuid}")
            }
            addNextService()
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val name = label(device)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                synchronized(lock) {
                    devices[device.address] = device
                    connectedAt[device.address] = SystemClock.elapsedRealtime()
                }
                telemetry.update {
                    it.copy(connectedDevices = it.connectedDevices + name, connections = it.connections + 1)
                }
                telemetry.event(TelemetryType.CONNECTION, name, "Conectado (estado $status) ${bond(device)}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val duration = synchronized(lock) {
                    devices.remove(device.address)
                    subscriptions.remove(device.address)
                    queue.removeAll { it.device.address == device.address }
                    connectedAt.remove(device.address)?.let { SystemClock.elapsedRealtime() - it }
                }
                telemetry.update {
                    it.copy(
                        connectedDevices = it.connectedDevices - name,
                        disconnections = it.disconnections + 1,
                        subscriptions = it.subscriptions - name,
                        mtu = it.mtu - name,
                    )
                }
                telemetry.event(
                    TelemetryType.CONNECTION,
                    name,
                    "Desconectado (estado $status, duración ${duration?.div(1000) ?: "?"} s)",
                )
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            val name = label(device)
            telemetry.update { it.copy(mtu = it.mtu + (name to mtu)) }
            telemetry.event(TelemetryType.MTU, name, "MTU $mtu")
        }

        override fun onPhyUpdate(device: BluetoothDevice, txPhy: Int, rxPhy: Int, status: Int) {
            telemetry.event(TelemetryType.PHY, label(device), "PHY tx=$txPhy rx=$rxPhy (estado $status)")
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val value = valueFor(characteristic.uuid)
            val gattServer = synchronized(lock) { server } ?: return
            if (value == null || offset > value.size) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
            } else {
                gattServer.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value.copyOfRange(offset, value.size),
                )
            }
            telemetry.update { it.copy(reads = it.reads + 1) }
            telemetry.event(
                TelemetryType.READ,
                label(device),
                "Lee ${FtmsUuids.nameOf(characteristic.uuid)} → ${value?.toHex() ?: "no permitido"}",
            )
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor,
        ) {
            val gattServer = synchronized(lock) { server } ?: return
            val uuid = descriptor.characteristic.uuid
            val value = if (isSubscribed(device, uuid)) {
                if (uuid == FtmsUuids.CONTROL_POINT) byteArrayOf(0x02, 0x00) else byteArrayOf(0x01, 0x00)
            } else {
                byteArrayOf(0x00, 0x00)
            }
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            val gattServer = synchronized(lock) { server } ?: return
            if (descriptor.uuid != FtmsUuids.CLIENT_CHARACTERISTIC_CONFIG) {
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, 0, null)
                }
                return
            }
            val uuid = descriptor.characteristic.uuid
            val enabled = value.isNotEmpty() && value[0].toInt() != 0
            val name = label(device)
            val delay: Long?
            synchronized(lock) {
                val set = subscriptions.getOrPut(device.address) { mutableSetOf() }
                if (enabled) set.add(uuid) else set.remove(uuid)
                delay = connectedAt[device.address]?.let { SystemClock.elapsedRealtime() - it }
            }
            if (responseNeeded) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            val characteristicName = FtmsUuids.nameOf(uuid)
            telemetry.update {
                val current = it.subscriptions[name].orEmpty()
                val updated = if (enabled) current + characteristicName else current - characteristicName
                it.copy(
                    subscriptions = it.subscriptions + (name to updated),
                    firstSubscriptionDelayMs = it.firstSubscriptionDelayMs ?: if (enabled) delay else null,
                )
            }
            telemetry.event(
                TelemetryType.SUBSCRIBE,
                name,
                "${if (enabled) "Suscribe" else "Cancela"} $characteristicName (CCCD ${value.toHex()})",
            )
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            val gattServer = synchronized(lock) { server } ?: return
            val name = label(device)
            if (characteristic.uuid != FtmsUuids.CONTROL_POINT) {
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, 0, null)
                }
                telemetry.event(
                    TelemetryType.ERROR,
                    name,
                    "Escritura inesperada en ${FtmsUuids.nameOf(characteristic.uuid)}: ${value.toHex()}",
                )
                return
            }
            if (responseNeeded) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            telemetry.update { it.copy(controlPointWrites = it.controlPointWrites + 1) }
            val request = FtmsCodec.parseControlPoint(value)
            if (request == null) {
                telemetry.event(TelemetryType.CONTROL_POINT, name, "Escritura vacía")
                return
            }
            val result = FtmsCodec.resultFor(request)
            telemetry.event(
                TelemetryType.CONTROL_POINT,
                name,
                "${request.name} [${value.toHex()}] → resultado 0x%02X".format(result),
            )
            enqueue(
                Outgoing(device, controlPoint, true, FtmsCodec.controlPointResponse(request.opcode, result), false),
            )
            when (request.opcode) {
                FtmsCodec.OP_START -> announceTraining(active = true)
                FtmsCodec.OP_STOP -> announceTraining(active = false)
            }
        }

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            synchronized(lock) { server }?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            synchronized(lock) { inFlightSince = 0L }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                telemetry.update { it.copy(notificationsConfirmed = it.notificationsConfirmed + 1) }
            } else {
                telemetry.update { it.copy(notificationsFailed = it.notificationsFailed + 1) }
            }
            drain()
        }
    }

    private fun announceTraining(active: Boolean) {
        val targets: List<Outgoing>
        synchronized(lock) {
            trainingActive = active
            val status = if (active) FtmsCodec.machineStatusStarted() else FtmsCodec.machineStatusStopped()
            val training = FtmsCodec.trainingStatus(active)
            targets = subscriptions.flatMap { (address, set) ->
                val device = devices[address] ?: return@flatMap emptyList()
                buildList {
                    if (machineStatus.uuid in set) add(Outgoing(device, machineStatus, false, status, false))
                    if (trainingStatus.uuid in set) add(Outgoing(device, trainingStatus, false, training, false))
                }
            }
            targets.forEach { enqueueLocked(it) }
        }
        drain()
    }

    private companion object {
        const val MAX_QUEUE = 20
        const val IN_FLIGHT_TIMEOUT_MS = 2000L
    }
}
