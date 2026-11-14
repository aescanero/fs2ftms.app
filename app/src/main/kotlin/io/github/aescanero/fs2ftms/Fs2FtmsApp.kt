package io.github.aescanero.fs2ftms

import android.app.Application
import io.github.aescanero.fs2ftms.ble.ftms.TelemetryRecorder
import io.github.aescanero.fs2ftms.protocol.MachineType
import io.github.aescanero.fs2ftms.protocol.WorkoutSample
import kotlinx.coroutines.flow.MutableStateFlow

data class EmulatorSettings(
    val machineType: MachineType = MachineType.CROSS_TRAINER,
    val batteryService: Boolean = false,
    val cadencePerMinute: Int = 110,
)

/** Estado compartido entre el servicio y la UI (sustituible por Hilt cuando crezca el grafo). */
class AppContainer {
    val telemetry = TelemetryRecorder()
    val settings = MutableStateFlow(EmulatorSettings())
    val lastSample = MutableStateFlow<WorkoutSample?>(null)
}

class Fs2FtmsApp : Application() {
    val container = AppContainer()
}
