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
import kotlinx.coroutines.launch

data class EmulatorSettings(
    val machineType: MachineType = MachineType.INDOOR_BIKE,
    val batteryService: Boolean = false,
    val pauseAdvertisingWhileConnected: Boolean = false,
    val cadencePerMinute: Int = 110,
    /** Máquina real elegida en el escáner; con [useBridge] sustituye a los datos simulados. */
    val sourceAddress: String? = null,
    val sourceName: String? = null,
    val useBridge: Boolean = false,
)

/** Estado compartido entre el servicio y la UI (sustituible por Hilt cuando crezca el grafo). */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val telemetry = TelemetryRecorder()
    val inspector = GattInspector(context, scope)
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val settings = MutableStateFlow(
        EmulatorSettings(
            sourceAddress = prefs.getString("sourceAddress", null),
            sourceName = prefs.getString("sourceName", null),
            useBridge = prefs.getBoolean("useBridge", false),
        ),
    )

    init {
        scope.launch {
            settings.collect {
                prefs.edit()
                    .putString("sourceAddress", it.sourceAddress)
                    .putString("sourceName", it.sourceName)
                    .putBoolean("useBridge", it.useBridge)
                    .apply()
            }
        }
    }

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
