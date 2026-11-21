package io.github.aescanero.fs2ftms

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.aescanero.fs2ftms.ble.central.DumpState
import io.github.aescanero.fs2ftms.ble.central.LogEntry
import io.github.aescanero.fs2ftms.ble.central.ScanState
import io.github.aescanero.fs2ftms.data.SampleEntity
import io.github.aescanero.fs2ftms.data.SessionEntity
import io.github.aescanero.fs2ftms.data.Sessions
import io.github.aescanero.fs2ftms.protocol.DemoWorkout
import io.github.aescanero.fs2ftms.protocol.MachineProfile
import io.github.aescanero.fs2ftms.protocol.MachineType
import io.github.aescanero.fs2ftms.protocol.ProfileBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SessionDetail(val session: SessionEntity, val samples: List<SampleEntity>)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as Fs2FtmsApp).container

    val recorder: StateFlow<RecorderState> = container.recorder
    val settings: StateFlow<DeviceSettings> = container.settings
    val scan: StateFlow<ScanState> = container.inspector.scan
    val dump: StateFlow<DumpState> = container.inspector.dump
    val events: StateFlow<List<LogEntry>> = container.eventLog.entries
    val sessions: StateFlow<List<SessionEntity>> =
        container.dao.sessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profiles: StateFlow<List<MachineProfile>> = container.profiles.profiles

    /** Perfil de la máquina con la que se grabará: el guardado, el heredado o el de demostración. */
    val selectedProfile: StateFlow<MachineProfile?> = combine(container.settings, container.profiles.profiles) { _, _ ->
        container.selectedProfile()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, container.selectedProfile())

    /** Perfil generado a partir del último volcado, antes de guardarlo. */
    val dumpProfile: StateFlow<MachineProfile?> = container.inspector.dump.map { state ->
        state.result?.takeIf { it.error == null || it.services.isNotEmpty() }
            ?.let { ProfileBuilder.build(it.toCaptureInput(System.currentTimeMillis())) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _detail = MutableStateFlow<SessionDetail?>(null)
    val detail: StateFlow<SessionDetail?> = _detail.asStateFlow()

    val versionName: String = runCatching {
        application.packageManager.getPackageInfo(application.packageName, 0).versionName
    }.getOrNull() ?: "?"

    fun startRecording() = RecorderService.start(getApplication())

    fun stopRecording() = RecorderService.stop(getApplication())

    fun pauseRecording() = RecorderService.setPaused(getApplication(), true)

    fun resumeRecording() = RecorderService.setPaused(getApplication(), false)

    fun setWeight(kg: Int?) = container.settings.update { it.copy(weightKg = kg?.takeIf { v -> v in 30..250 }) }

    fun setMale(male: Boolean?) = container.settings.update { it.copy(male = male) }

    /** Guarda (o borra, con null) la corrección manual de calorías de la sesión abierta. */
    fun setManualKcal(kcal: Int?) {
        val id = _detail.value?.session?.id ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.dao.setManualKcal(id, kcal?.takeIf { it in 0..20000 }) }
            openSession(id)
        }
    }

    fun setDemo(enabled: Boolean) = container.settings.update { it.copy(demo = enabled) }

    /** Inserta de golpe una sesión simulada terminada, para revisar historial y detalle. */
    fun createDemoSession(minutes: Int = 25) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = container.dao
            val seconds = minutes * 60
            val end = System.currentTimeMillis()
            val start = end - seconds * 1000L
            val session = SessionEntity(
                startedAtMs = start,
                endedAtMs = end,
                machineName = DemoWorkout.NAME,
                machineType = MachineType.CROSS_TRAINER.name,
            )
            val id = dao.insertSession(session)
            val generator = DemoWorkout(container.settings.value.maxHeartRate ?: 180)
            val samples = List(seconds) { index ->
                val point = generator.next()
                SampleEntity(
                    sessionId = id,
                    timestampMs = start + (index + 1) * 1000L,
                    elapsedSeconds = point.sample.elapsedSeconds,
                    speedKmh = point.sample.speedKmh,
                    cadence = point.sample.cadencePerMinute,
                    distanceMeters = point.sample.distanceMeters,
                    strideCount = point.sample.strideCount,
                    powerWatts = point.sample.powerWatts,
                    energyKcal = point.sample.energyKcal,
                    resistance = point.sample.resistanceLevel,
                    heartRate = point.heartRate,
                )
            }
            dao.insertSamples(samples)
            dao.updateSession(
                Sessions.summarize(session.copy(id = id), samples, end, container.settings.value.estimateKcal(samples)),
            )
        }
    }

    fun setDebug(enabled: Boolean) = container.settings.update { it.copy(debug = enabled) }

    fun setAge(age: Int?) = container.settings.update { it.copy(age = age?.takeIf { v -> v in 10..100 }) }

    fun setMeasuredMaxHeartRate(bpm: Int?) =
        container.settings.update { it.copy(measuredMaxHeartRate = bpm?.takeIf { v -> v in 100..230 }) }

    fun clearMachine() = container.settings.update { it.copy(machineAddress = null, machineName = null) }

    fun clearHeartRate() = container.settings.update { it.copy(heartRateAddress = null, heartRateName = null) }

    fun startScan() = container.inspector.startScan()

    fun stopScan() = container.inspector.stopScan()

    /** La escucha dura lo suficiente para que la máquina envíe varias tramas y generar su perfil. */
    fun inspect(address: String) =
        container.inspector.inspect(address, if (container.settings.value.debug) 20 else 12)

    fun cancelDump() = container.inspector.cancelDump()

    fun clearDump() = container.inspector.clearDump()

    /** Guarda el perfil generado con el último volcado y, si se puede grabar, lo deja como máquina activa. */
    fun saveProfileFromDump() {
        val profile = dumpProfile.value ?: return
        container.profiles.save(profile)
        if (profile.supported) selectProfile(profile)
        container.inspector.clearDump()
    }

    fun selectProfile(profile: MachineProfile) =
        container.settings.update { it.copy(machineAddress = profile.address, machineName = profile.name) }

    fun deleteProfile(profile: MachineProfile) {
        container.profiles.delete(profile.address)
        if (container.settings.value.machineAddress == profile.address) clearMachine()
    }

    fun useDumpAsHeartRate() {
        val result = container.inspector.dump.value.result ?: return
        container.settings.update { it.copy(heartRateAddress = result.address, heartRateName = result.scan?.name) }
        container.inspector.clearDump()
    }

    fun openSession(id: Long) {
        viewModelScope.launch {
            _detail.value = withContext(Dispatchers.IO) {
                container.dao.session(id)?.let { SessionDetail(it, container.dao.samples(id)) }
            }
        }
    }

    fun closeSession() {
        _detail.value = null
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.dao.deleteSession(id) }
            _detail.value = null
        }
    }

    private fun stamp(timeMs: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timeMs))

    private fun shareJson(name: String, json: String, title: String): Intent {
        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "$name.json")
        file.writeText(json)
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, title)
    }

    private fun device() = mapOf(
        "app" to "fs2ftms $versionName",
        "phone" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "exportedAtMs" to System.currentTimeMillis(),
    )

    fun exportSessionIntent(): Intent? {
        val current = _detail.value ?: return null
        val json = Sessions.toJson(current.session, current.samples)
        return shareJson("fs2ftms-sesion-${stamp(current.session.startedAtMs)}", json, getApplication<Application>().getString(R.string.share_session))
    }

    fun exportDumpIntent(): Intent? {
        val result = container.inspector.dump.value.result ?: return null
        val label = (result.scan?.name ?: result.address).replace(Regex("[^A-Za-z0-9_-]"), "_")
        return shareJson("fs2ftms-volcado-$label-${stamp()}", result.toJson(device()), getApplication<Application>().getString(R.string.share_dump))
    }

    fun exportEventsIntent(): Intent =
        shareJson("fs2ftms-eventos-${stamp()}", container.eventLog.toJson(device()), getApplication<Application>().getString(R.string.share_events))
}
