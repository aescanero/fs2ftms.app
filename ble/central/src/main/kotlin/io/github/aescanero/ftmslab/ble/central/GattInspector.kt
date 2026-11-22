package io.github.aescanero.ftmslab.ble.central

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.SystemClock
import io.github.aescanero.ftmslab.protocol.FtmsUuids
import io.github.aescanero.ftmslab.protocol.toHex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections

/**
 * Escáner BLE y volcado GATT de solo consulta: lee lo legible y se suscribe a las
 * notificaciones durante unos segundos. No escribe en ninguna característica.
 * Requiere BLUETOOTH_SCAN y BLUETOOTH_CONNECT concedidos.
 */
@SuppressLint("MissingPermission")
class GattInspector(private val context: Context, private val scope: CoroutineScope) {
    private sealed class Ev {
        class Conn(val status: Int, val newState: Int) : Ev()
        class Mtu(val mtu: Int, val status: Int) : Ev()
        class Services(val status: Int) : Ev()
        class CharRead(val value: ByteArray, val status: Int) : Ev()
        class DescRead(val value: ByteArray, val status: Int) : Ev()
        class DescWrite(val status: Int) : Ev()
    }

    private class LinkLost(val status: Int) : Exception()

    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val lock = Any()
    private val found = LinkedHashMap<String, ScanEntry>()
    private val foundDevices = HashMap<String, BluetoothDevice>()
    private var scanJob: Job? = null
    private var dumpJob: Job? = null

    private val _scan = MutableStateFlow(ScanState())
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    private val _dump = MutableStateFlow(DumpState())
    val dump: StateFlow<DumpState> = _dump.asStateFlow()

    // ---- Escaneo ---------------------------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord
            val manufacturer = LinkedHashMap<String, String>()
            record?.manufacturerSpecificData?.let { data ->
                for (i in 0 until data.size()) {
                    manufacturer["0x%04X".format(data.keyAt(i))] = data.valueAt(i).toHex()
                }
            }
            val entry = ScanEntry(
                address = result.device.address,
                name = record?.deviceName ?: runCatching { result.device.name }.getOrNull(),
                rssi = result.rssi,
                connectable = result.isConnectable,
                serviceUuids = record?.serviceUuids.orEmpty().map { FtmsUuids.display(it.uuid) },
                serviceData = record?.serviceData.orEmpty()
                    .map { (k, v) -> FtmsUuids.display(k.uuid) to v.toHex() }.toMap(),
                manufacturerData = manufacturer,
                rawHex = record?.bytes?.toHex().orEmpty(),
            )
            synchronized(lock) {
                // El scan response puede llegar sin nombre: se conserva el ya visto.
                val previous = found[entry.address]
                found[entry.address] = if (entry.name == null && previous?.name != null) {
                    entry.copy(name = previous.name)
                } else {
                    entry
                }
                foundDevices[entry.address] = result.device
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _scan.update { it.copy(scanning = false, error = "Scan failed (code $errorCode)") }
        }
    }

    private fun publishScan(scanning: Boolean) {
        val entries = synchronized(lock) { found.values.toList() }
            .sortedWith(
                compareByDescending<ScanEntry> { it.hasFtms || it.hasFitShow }
                    .thenByDescending { it.name != null }
                    .thenByDescending { it.rssi },
            )
        _scan.update { it.copy(scanning = scanning, entries = entries) }
    }

    fun startScan() {
        if (scanJob?.isActive == true) return
        val scanner = manager?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            _scan.value = ScanState(error = "Bluetooth is off or unavailable")
            return
        }
        synchronized(lock) {
            found.clear()
            foundDevices.clear()
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(null, settings, scanCallback)
        } catch (e: SecurityException) {
            _scan.value = ScanState(error = "Scan permission denied")
            return
        }
        _scan.value = ScanState(scanning = true)
        scanJob = scope.launch {
            try {
                repeat(SCAN_SECONDS * 2) {
                    delay(500)
                    publishScan(scanning = true)
                }
            } finally {
                runCatching { scanner.stopScan(scanCallback) }
                publishScan(scanning = false)
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
    }

    // ---- Volcado GATT ----------------------------------------------------------------------

    fun inspect(address: String, listenSeconds: Int = 20) {
        if (dumpJob?.isActive == true) return
        val device = synchronized(lock) { foundDevices[address] } ?: return
        val entry = synchronized(lock) { found[address] }
        stopScan()
        dumpJob = scope.launch { runDump(device, entry, listenSeconds) }
    }

    fun cancelDump() {
        dumpJob?.cancel()
    }

    fun clearDump() {
        if (dumpJob?.isActive != true) _dump.value = DumpState()
    }

    private suspend fun <T : Ev> await(events: Channel<Ev>, type: Class<T>, timeoutMs: Long): T? =
        withTimeoutOrNull(timeoutMs) {
            var match: T? = null
            while (match == null) {
                val event = events.receive()
                if (event is Ev.Conn && event.newState == BluetoothProfile.STATE_DISCONNECTED) {
                    throw LinkLost(event.status)
                }
                if (type.isInstance(event)) match = type.cast(event)
            }
            match
        }

    private fun properties(c: BluetoothGattCharacteristic): List<String> = buildList {
        val p = c.properties
        if ((p and BluetoothGattCharacteristic.PROPERTY_READ) != 0) add("READ")
        if ((p and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) add("WRITE")
        if ((p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) add("WRITE_NO_RESPONSE")
        if ((p and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) add("NOTIFY")
        if ((p and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) add("INDICATE")
    }

    private suspend fun dumpCharacteristic(
        gatt: BluetoothGatt,
        events: Channel<Ev>,
        c: BluetoothGattCharacteristic,
    ): CharacteristicDump {
        var value: String? = null
        var error: String? = null
        if ((c.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
            if (gatt.readCharacteristic(c)) {
                val read = await(events, Ev.CharRead::class.java, 5000)
                when {
                    read == null -> error = "no response"
                    read.status == BluetoothGatt.GATT_SUCCESS -> value = read.value.toHex()
                    else -> error = "status ${read.status}"
                }
            } else {
                error = "read rejected"
            }
        }
        val descriptors = ArrayList<DescriptorDump>()
        for (d in c.descriptors) {
            var descriptorValue: String? = null
            if (gatt.readDescriptor(d)) {
                val read = await(events, Ev.DescRead::class.java, 3000)
                descriptorValue = when {
                    read == null -> "no response"
                    read.status == BluetoothGatt.GATT_SUCCESS -> read.value.toHex()
                    else -> "status ${read.status}"
                }
            }
            descriptors.add(DescriptorDump(FtmsUuids.display(d.uuid), descriptorValue))
        }
        return CharacteristicDump(FtmsUuids.display(c.uuid), properties(c), value, error, descriptors)
    }

    private suspend fun runDump(device: BluetoothDevice, entry: ScanEntry?, listenSeconds: Int) {
        val events = Channel<Ev>(Channel.UNLIMITED)
        val notifications = Collections.synchronizedList(ArrayList<NotificationDump>())
        val log = ArrayList<String>()
        val started = SystemClock.elapsedRealtime()

        fun note(text: String) {
            log.add("+${SystemClock.elapsedRealtime() - started} ms  $text")
            _dump.update { it.copy(status = text) }
        }

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                events.trySend(Ev.Conn(status, newState))
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                events.trySend(Ev.Mtu(mtu, status))
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                events.trySend(Ev.Services(status))
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                events.trySend(Ev.CharRead(value, status))
            }

            override fun onDescriptorRead(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
                value: ByteArray,
            ) {
                events.trySend(Ev.DescRead(value, status))
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                events.trySend(Ev.DescWrite(status))
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                if (notifications.size < MAX_NOTIFICATIONS) {
                    notifications.add(
                        NotificationDump(
                            SystemClock.elapsedRealtime() - started,
                            FtmsUuids.display(characteristic.uuid),
                            value.toHex(),
                        ),
                    )
                }
            }
        }

        _dump.value = DumpState(running = true, status = "Connecting…")
        var mtu: Int? = null
        var services: List<ServiceDump> = emptyList()
        var error: String? = null
        var gatt: BluetoothGatt? = null
        try {
            val link = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                ?: throw IllegalStateException("Could not start the connection")
            gatt = link
            val connected = await(events, Ev.Conn::class.java, 15000)
                ?: throw IllegalStateException("Timed out while connecting")
            note("Connected (status ${connected.status})")

            if (link.requestMtu(247)) {
                await(events, Ev.Mtu::class.java, 4000)?.let {
                    mtu = it.mtu
                    note("MTU ${it.mtu}")
                }
            }

            if (!link.discoverServices()) throw IllegalStateException("Service discovery rejected")
            val discovered = await(events, Ev.Services::class.java, 20000)
                ?: throw IllegalStateException("Timed out while discovering services")
            note("Services discovered: ${link.services.size} (status ${discovered.status})")

            val collected = ArrayList<ServiceDump>()
            for (service in link.services) {
                note("Reading ${FtmsUuids.display(service.uuid)}…")
                val characteristics = ArrayList<CharacteristicDump>()
                for (c in service.characteristics) characteristics.add(dumpCharacteristic(link, events, c))
                collected.add(
                    ServiceDump(
                        FtmsUuids.display(service.uuid),
                        service.type == BluetoothGattService.SERVICE_TYPE_PRIMARY,
                        characteristics,
                    ),
                )
                services = collected.toList()
            }

            var subscribed = 0
            for (service in link.services) {
                for (c in service.characteristics) {
                    val notify = (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                    val indicate = (c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                    if (!notify && !indicate) continue
                    val cccd = c.getDescriptor(FtmsUuids.CLIENT_CHARACTERISTIC_CONFIG) ?: continue
                    link.setCharacteristicNotification(c, true)
                    val enable = if (notify) {
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    } else {
                        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    }
                    val code = link.writeDescriptor(cccd, enable)
                    val name = FtmsUuids.display(c.uuid)
                    if (code == BluetoothStatusCodes.SUCCESS) {
                        val written = await(events, Ev.DescWrite::class.java, 4000)
                        note("Subscription to $name: ${written?.let { "status ${it.status}" } ?: "no response"}")
                        if (written?.status == BluetoothGatt.GATT_SUCCESS) subscribed++
                    } else {
                        note("Subscription to $name rejected (code $code)")
                    }
                }
            }

            if (subscribed > 0) {
                for (remaining in listenSeconds downTo 1) {
                    _dump.update {
                        it.copy(status = "Listening to $subscribed characteristics… $remaining s (use the machine)")
                    }
                    delay(1000)
                    val event = events.tryReceive().getOrNull()
                    if (event is Ev.Conn && event.newState == BluetoothProfile.STATE_DISCONNECTED) {
                        throw LinkLost(event.status)
                    }
                }
            }
            note("Dump complete: ${notifications.size} notifications")
        } catch (e: CancellationException) {
            error = "Cancelled"
            throw e
        } catch (e: LinkLost) {
            error = "Link lost (status ${e.status})"
            note("Link lost (status ${e.status})")
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            note("Error: $error")
        } finally {
            runCatching { gatt?.disconnect() }
            runCatching { gatt?.close() }
            val result = GattDump(
                scan = entry,
                address = device.address,
                mtu = mtu,
                services = services,
                notifications = synchronized(notifications) { notifications.toList() },
                log = log.toList(),
                error = error,
            )
            _dump.value = DumpState(running = false, status = error ?: "Dump complete", result = result)
        }
    }

    private companion object {
        const val SCAN_SECONDS = 30
        const val MAX_NOTIFICATIONS = 2000
    }
}
