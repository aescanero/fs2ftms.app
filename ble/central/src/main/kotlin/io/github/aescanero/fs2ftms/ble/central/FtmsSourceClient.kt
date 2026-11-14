package io.github.aescanero.fs2ftms.ble.central

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
import io.github.aescanero.fs2ftms.protocol.CrossTrainerDataParser
import io.github.aescanero.fs2ftms.protocol.FtmsUuids
import io.github.aescanero.fs2ftms.protocol.WorkoutSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Cliente de la máquina real: se suscribe a su Cross Trainer Data (0x2ACE) y entrega muestras.
 * Solo lectura (no escribe en el Control Point). Reconecta con espera creciente.
 */
@SuppressLint("MissingPermission")
class FtmsSourceClient(
    private val context: Context,
    private val scope: CoroutineScope,
    private val address: String,
    private val listener: Listener,
) {
    interface Listener {
        fun onState(text: String, connected: Boolean)
        fun onSample(sample: WorkoutSample)
        fun onMachineStatus(value: ByteArray)
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
                listener.onState("Reintento de conexión con la máquina en $wait s", false)
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

    /** Devuelve true si llegó a suscribirse a los datos (la sesión fue útil). */
    private suspend fun runSession(): Boolean {
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            listener.onState("Bluetooth desactivado", false)
            return false
        }
        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            listener.onState("Dirección de máquina no válida: $address", false)
            return false
        }
        val events = Channel<Ev>(Channel.UNLIMITED)
        val parser = CrossTrainerDataParser()
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
                when (characteristic.uuid) {
                    FtmsUuids.CROSS_TRAINER_DATA -> parser.accept(value)?.let(listener::onSample)
                    FtmsUuids.MACHINE_STATUS -> listener.onMachineStatus(value)
                }
            }
        }
        var subscribed = false
        var gatt: BluetoothGatt? = null
        try {
            listener.onState("Conectando con la máquina…", false)
            val link = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            if (link == null) {
                listener.onState("No se pudo iniciar la conexión con la máquina", false)
                return false
            }
            gatt = link
            if (await(events, Ev.Conn::class.java, 35000) == null) {
                listener.onState("La máquina no responde (¿apagada o conectada a otra app?)", false)
                return false
            }
            if (!link.discoverServices() || await(events, Ev.Services::class.java, 20000) == null) {
                listener.onState("No se pudieron descubrir los servicios de la máquina", false)
                return false
            }
            val service = link.getService(FtmsUuids.FITNESS_MACHINE_SERVICE)
            val data = service?.getCharacteristic(FtmsUuids.CROSS_TRAINER_DATA)
            if (data == null) {
                listener.onState("La máquina no ofrece Cross Trainer Data (0x2ACE)", false)
                return false
            }
            if (!subscribe(link, events, data)) {
                listener.onState("La máquina rechazó la suscripción a 0x2ACE", false)
                return false
            }
            subscribed = true
            service.getCharacteristic(FtmsUuids.MACHINE_STATUS)?.let { subscribe(link, events, it) }
            listener.onState("Máquina conectada: recibiendo datos", true)
            while (true) {
                val event = events.receive()
                if (event is Ev.Conn && event.newState == BluetoothProfile.STATE_DISCONNECTED) {
                    throw LinkLost(event.status)
                }
            }
        } catch (e: LinkLost) {
            listener.onState("Conexión con la máquina perdida (estado ${e.status})", false)
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
