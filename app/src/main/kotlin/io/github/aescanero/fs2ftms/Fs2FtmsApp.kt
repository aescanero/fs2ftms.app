package io.github.aescanero.fs2ftms

import android.app.Application
import android.content.Context
import io.github.aescanero.fs2ftms.ble.central.EventLog
import io.github.aescanero.fs2ftms.ble.central.GattInspector
import io.github.aescanero.fs2ftms.data.AppDatabase
import io.github.aescanero.fs2ftms.data.SampleEntity
import io.github.aescanero.fs2ftms.data.Sessions
import io.github.aescanero.fs2ftms.protocol.CalorieEstimator
import io.github.aescanero.fs2ftms.protocol.HeartRateZones
import io.github.aescanero.fs2ftms.protocol.WorkoutSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class DeviceSettings(
    val machineAddress: String? = null,
    val machineName: String? = null,
    val heartRateAddress: String? = null,
    val heartRateName: String? = null,
    val debug: Boolean = false,
    /** Con la depuración activa, graba una sesión simulada en lugar de conectar con los dispositivos. */
    val demo: Boolean = false,
    val age: Int? = null,
    /** FC máxima medida; si falta, se estima con la edad. */
    val measuredMaxHeartRate: Int? = null,
    val weightKg: Int? = null,
    /** Sexo para la fórmula de calorías: true hombre, false mujer, null sin indicar. */
    val male: Boolean? = null,
) {
    /** Estimador de calorías por pulso, disponible cuando hay edad, peso y sexo. */
    val calorieEstimator: CalorieEstimator?
        get() = if (age != null && weightKg != null && male != null) CalorieEstimator(age, weightKg, male) else null

    fun estimateKcal(samples: List<SampleEntity>): Int? =
        calorieEstimator?.total(samples.map { it.elapsedSeconds to it.heartRate })?.roundToInt()

    val demoActive: Boolean get() = debug && demo

    val maxHeartRate: Int?
        get() = measuredMaxHeartRate ?: age?.let { HeartRateZones.estimateMax(it) }
}

data class RecorderState(
    val recording: Boolean = false,
    val machineConnected: Boolean = false,
    val heartRateConnected: Boolean = false,
    val sample: WorkoutSample? = null,
    val heartRate: Int? = null,
    /** Instante (SystemClock.elapsedRealtime) de la última lectura de pulso. */
    val heartRateAtMs: Long? = null,
    val paused: Boolean = false,
    val error: String? = null,
)

/** Estado compartido entre el servicio y la UI (sustituible por Hilt cuando crezca el grafo). */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = AppDatabase.create(context)
    val dao = database.sessionDao()
    val eventLog = EventLog()
    val inspector = GattInspector(context, scope)
    val recorder = MutableStateFlow(RecorderState())

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val settings = MutableStateFlow(
        DeviceSettings(
            machineAddress = prefs.getString("machineAddress", null) ?: prefs.getString("sourceAddress", null),
            machineName = prefs.getString("machineName", null) ?: prefs.getString("sourceName", null),
            heartRateAddress = prefs.getString("heartRateAddress", null),
            heartRateName = prefs.getString("heartRateName", null),
            debug = prefs.getBoolean("debug", false),
            demo = prefs.getBoolean("demo", false),
            age = prefs.getInt("age", 0).takeIf { it > 0 },
            measuredMaxHeartRate = prefs.getInt("measuredMaxHeartRate", 0).takeIf { it > 0 },
            weightKg = prefs.getInt("weightKg", 0).takeIf { it > 0 },
            male = when (prefs.getInt("sex", 0)) {
                1 -> true
                2 -> false
                else -> null
            },
        ),
    )

    init {
        scope.launch {
            settings.collect {
                prefs.edit()
                    .putString("machineAddress", it.machineAddress)
                    .putString("machineName", it.machineName)
                    .putString("heartRateAddress", it.heartRateAddress)
                    .putString("heartRateName", it.heartRateName)
                    .putBoolean("debug", it.debug)
                    .putBoolean("demo", it.demo)
                    .putInt("age", it.age ?: 0)
                    .putInt("measuredMaxHeartRate", it.measuredMaxHeartRate ?: 0)
                    .putInt("weightKg", it.weightKg ?: 0)
                    .putInt("sex", when (it.male) { true -> 1; false -> 2; null -> 0 })
                    .apply()
            }
        }
        // Sesiones que quedaron abiertas si el sistema cerró la app: se cierran con lo grabado.
        scope.launch(Dispatchers.IO) {
            dao.unfinished().forEach { session ->
                val samples = dao.samples(session.id)
                if (samples.isEmpty()) {
                    dao.deleteSession(session.id)
                } else {
                    dao.updateSession(
                        Sessions.summarize(
                            session,
                            samples,
                            samples.last().timestampMs,
                            settings.value.estimateKcal(samples),
                        ),
                    )
                }
            }
        }
    }
}

class Fs2FtmsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
