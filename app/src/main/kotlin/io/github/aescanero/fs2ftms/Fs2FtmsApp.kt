package io.github.aescanero.fs2ftms

import android.app.Application
import android.content.Context
import io.github.aescanero.fs2ftms.ble.central.GattInspector
import io.github.aescanero.fs2ftms.ble.ftms.TelemetryRecorder
import io.github.aescanero.fs2ftms.protocol.MachineType
import io.github.aescanero.fs2ftms.protocol.WorkoutSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

data class EmulatorSettings(
    val machineType: MachineType = MachineType.CROSS_TRAINER,
    val batteryService: Boolean = false,
    val pauseAdvertisingWhileConnected: Boolean = false,
    val cadencePerMinute: Int = 110,
)

/** Estado compartido entre el servicio y la UI (sustituible por Hilt cuando crezca el grafo). */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val telemetry = TelemetryRecorder()
    val inspector = GattInspector(context, scope)
    val settings = MutableStateFlow(EmulatorSettings())
    val lastSample = MutableStateFlow<WorkoutSample?>(null)
}

class Fs2FtmsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
