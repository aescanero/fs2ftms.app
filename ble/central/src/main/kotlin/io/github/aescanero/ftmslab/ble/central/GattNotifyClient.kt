package io.github.aescanero.ftmslab.ble.central

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import io.github.aescanero.ftmslab.protocol.FtmsUuids
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Cliente GATT de solo lectura: se suscribe a las notificaciones de un servicio y las entrega.
 * La primera característica es obligatoria; el resto, opcionales. Reconecta con espera creciente.
 */
@SuppressLint("MissingPermission")
class GattNotifyClient(
    private val context: Context,
    private val scope: CoroutineScope,
    private val address: String,
    private val service: UUID,
    private val characteristics: List<UUID>,
    private val listener: Listener,
) {
    interface Listener {
        fun onState(text: String, connected: Boolean)
        fun onData(characteristic: UUID, value: ByteArray)
    }

    private sealed class Ev {
        class Conn(val status: Int, val newState: Int) : Ev()
        class Services(val status: Int) : Ev()
        class DescWrite(val status: Int) : Ev()
    }

    private class LinkLost(val status: Int) : Exception()

    private val manager = context.getSystemService(BluetoothManager::class.java)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var failures = 0
            while (isActive) {
                val subscribed = runSession()
                failures = if (subscribed) 0 else failures + 1
                val wait = BACKOFF_SECONDS[failures.coerceAtMost(BACKOFF_SECONDS.size - 1)]
                listener.onState("Reconnecting in $wait s", false)
                delay(wait * 1000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
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

    private suspend fun subscribe(
        gatt: BluetoothGatt,
        events: Channel<Ev>,
        characteristic: BluetoothGattCharacteristic,
    ): Boolean {
        val cccd = characteristic.getDescriptor(FtmsUuids.CLIENT_CHARACTERISTIC_CONFIG) ?: return false
        gatt.setCharacteristicNotification(characteristic, true)
        val code = gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        if (code != BluetoothStatusCodes.SUCCESS) return false
        return await(events, Ev.DescWrite::class.java, 5000)?.status == BluetoothGatt.GATT_SUCCESS
    }

    /** Devuelve true si llegó a suscribirse a la característica obligatoria. */
    private suspend fun runSession(): Boolean {
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            listener.onState("Bluetooth is off", false)
            return false
        }
        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            listener.onState("Invalid address: $address", false)
            return false
        }
        val events = Channel<Ev>(Channel.UNLIMITED)
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                events.trySend(Ev.Conn(status, newState))
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                events.trySend(Ev.Services(status))
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                events.trySend(Ev.DescWrite(status))
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                listener.onData(characteristic.uuid, value)
            }
        }
        var subscribed = false
        var gatt: BluetoothGatt? = null
        try {
            listener.onState("Connecting…", false)
            val link = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            if (link == null) {
                listener.onState("Could not start the connection", false)
                return false
            }
            gatt = link
            if (await(events, Ev.Conn::class.java, 35000) == null) {
                listener.onState("No response (off, or connected to another app?)", false)
                return false
            }
            if (!link.discoverServices() || await(events, Ev.Services::class.java, 20000) == null) {
                listener.onState("Could not discover services", false)
                return false
            }
            val gattService = link.getService(service)
            val required = gattService?.getCharacteristic(characteristics.first())
            if (required == null) {
                listener.onState("The device does not offer ${FtmsUuids.display(characteristics.first())}", false)
                return false
            }
            if (!subscribe(link, events, required)) {
                listener.onState("Subscription rejected for ${FtmsUuids.display(required.uuid)}", false)
                return false
            }
            subscribed = true
            characteristics.drop(1).forEach { uuid ->
                gattService.getCharacteristic(uuid)?.let { subscribe(link, events, it) }
            }
            listener.onState("Connected: receiving data", true)
            while (true) {
                val event = events.receive()
                if (event is Ev.Conn && event.newState == BluetoothProfile.STATE_DISCONNECTED) {
                    throw LinkLost(event.status)
                }
            }
        } catch (e: LinkLost) {
            listener.onState("Connection lost (status ${e.status})", false)
        } finally {
            runCatching { gatt?.disconnect() }
            runCatching { gatt?.close() }
        }
        return subscribed
    }

    private companion object {
        val BACKOFF_SECONDS = intArrayOf(1, 2, 5, 10, 20)
    }
}
