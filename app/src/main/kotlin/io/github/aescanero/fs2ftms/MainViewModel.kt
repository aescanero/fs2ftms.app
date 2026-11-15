package io.github.aescanero.fs2ftms

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import io.github.aescanero.fs2ftms.ble.central.DumpState
import io.github.aescanero.fs2ftms.ble.central.ScanState
import io.github.aescanero.fs2ftms.ble.ftms.TelemetrySnapshot
import io.github.aescanero.fs2ftms.protocol.MachineType
import io.github.aescanero.fs2ftms.protocol.WorkoutSample
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as Fs2FtmsApp).container

    val telemetry: StateFlow<TelemetrySnapshot> = container.telemetry.state
    val settings: StateFlow<EmulatorSettings> = container.settings
    val lastSample: StateFlow<WorkoutSample?> = container.lastSample
    val scan: StateFlow<ScanState> = container.inspector.scan
    val dump: StateFlow<DumpState> = container.inspector.dump

    val versionName: String = runCatching {
        application.packageManager.getPackageInfo(application.packageName, 0).versionName
    }.getOrNull() ?: "?"

    fun start() = BridgeService.start(getApplication())

    fun stop() = BridgeService.stop(getApplication())

    fun setMachineType(type: MachineType) = container.settings.update { it.copy(machineType = type) }

    fun setBatteryService(enabled: Boolean) = container.settings.update { it.copy(batteryService = enabled) }

    fun setPauseAdvertising(enabled: Boolean) =
        container.settings.update { it.copy(pauseAdvertisingWhileConnected = enabled) }

    fun setRequestBonding(enabled: Boolean) = container.settings.update { it.copy(requestBonding = enabled) }

    fun setUseBridge(enabled: Boolean) = container.settings.update { it.copy(useBridge = enabled) }

    /** Guarda como origen del puente la máquina del último volcado. */
    fun useDumpAsSource() {
        val result = container.inspector.dump.value.result ?: return
        container.settings.update {
            it.copy(sourceAddress = result.address, sourceName = result.scan?.name, useBridge = true)
        }
        container.inspector.clearDump()
    }

    fun setCadence(cadence: Int) = container.settings.update { it.copy(cadencePerMinute = cadence) }

    fun startScan() = container.inspector.startScan()

    fun stopScan() = container.inspector.stopScan()

    fun inspect(address: String) = container.inspector.inspect(address)

    fun cancelDump() = container.inspector.cancelDump()

    fun clearDump() = container.inspector.clearDump()

    /** Escribe el último volcado GATT en un fichero JSON y devuelve el intent para compartirlo. */
    fun exportDumpIntent(): Intent? {
        val result = container.inspector.dump.value.result ?: return null
        val json = result.toJson(
            mapOf(
                "app" to "fs2ftms $versionName",
                "phone" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "exportedAtMs" to System.currentTimeMillis(),
            ),
        )
        val label = (result.scan?.name ?: result.address).replace(Regex("[^A-Za-z0-9_-]"), "_")
        return shareJson("fs2ftms-volcado-$label", json, "Exportar volcado GATT")
    }

    private fun shareJson(prefix: String, json: String, title: String): Intent {
        val app = getApplication<Application>()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = File(app.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "$prefix-$stamp.json")
        file.writeText(json)
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, title)
    }

    /** Escribe la telemetría en un fichero JSON y devuelve el intent para compartirlo. */
    fun exportIntent(): Intent {
        val current = container.settings.value
        val json = container.telemetry.toJson(
            mapOf(
                "app" to "fs2ftms $versionName",
                "phone" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "androidSdk" to Build.VERSION.SDK_INT,
                "machineType" to current.machineType.name,
                "batteryService" to current.batteryService,
                "bridge" to current.useBridge,
                "requestBonding" to current.requestBonding,
                "source" to current.sourceName,
                "pauseAdvertisingWhileConnected" to current.pauseAdvertisingWhileConnected,
                "exportedAtMs" to System.currentTimeMillis(),
            ),
        )
        return shareJson("fs2ftms-telemetria", json, "Exportar telemetría")
    }
}
