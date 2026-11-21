package io.github.aescanero.fs2ftms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.aescanero.fs2ftms.ble.central.GattNotifyClient
import io.github.aescanero.fs2ftms.data.SampleEntity
import io.github.aescanero.fs2ftms.data.SessionEntity
import io.github.aescanero.fs2ftms.data.Sessions
import io.github.aescanero.fs2ftms.data.StatParam
import io.github.aescanero.fs2ftms.protocol.DemoWorkout
import io.github.aescanero.fs2ftms.protocol.FtmsUuids
import io.github.aescanero.fs2ftms.protocol.HeartRateParser
import io.github.aescanero.fs2ftms.protocol.Metric
import io.github.aescanero.fs2ftms.protocol.SessionAccumulator
import io.github.aescanero.fs2ftms.protocol.WorkoutSample
import io.github.aescanero.fs2ftms.protocol.parserFor
import io.github.aescanero.fs2ftms.protocol.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/** Servicio en primer plano que graba la sesión: datos de la elíptica y, si hay, pulso del reloj. */
class RecorderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var machine: GattNotifyClient? = null
    private var heartRateClient: GattNotifyClient? = null

    /** Las escrituras en la base de datos se procesan en orden por un único consumidor. */
    private var writes: Channel<suspend () -> Unit>? = null
    private var writer: Job? = null
    private var sessionId: Long? = null
    private var running = false
    private var accumulator: SessionAccumulator? = null

    @Volatile private var paused = false
    private var demoJob: Job? = null

    @Volatile private var lastHeartRate: Int? = null
    @Volatile private var lastHeartRateAt = 0L

    private val container: AppContainer
        get() = (application as Fs2FtmsApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> finish()
            ACTION_PAUSE -> setPaused(true)
            ACTION_RESUME -> setPaused(false)
            else -> begin()
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        if (running) return
        val settings = container.settings.value
        val demo = settings.demoActive
        val machineAddress = settings.machineAddress
        val profile = container.selectedProfile()
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } catch (e: Exception) {
            container.recorder.value = RecorderState(error = getString(R.string.error_start_recording, e.message.orEmpty()))
            stopSelf()
            return
        }
        val dataParser = profile?.type?.let { parserFor(it) }
        val error = when {
            profile == null -> getString(R.string.error_no_machine)
            !profile.supported || dataParser == null -> getString(R.string.error_profile_unsupported)
            else -> null
        }
        if (error != null || profile == null || dataParser == null) {
            container.recorder.value = RecorderState(error = error)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val dataCharacteristic = profile.type!!.dataCharacteristic
        // Si la máquina no envía su propio tiempo, se cuenta el tiempo activo entre muestras.
        val machineCountsTime = profile.has(Metric.ELAPSED_TIME)
        var ownElapsed = 0.0
        var lastSampleAt = 0L
        val log = container.eventLog
        val dao = container.dao
        val machineName = settings.machineName ?: machineAddress ?: DemoWorkout.NAME
        val sessionMachineName = if (demo) DemoWorkout.NAME else settings.machineName
        running = true
        container.recorder.value = RecorderState(recording = true)
        sessionId = null
        lastHeartRate = null

        val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
        writes = queue
        writer = container.scope.launch(Dispatchers.IO) {
            for (write in queue) runCatching { write() }.onFailure { log.add("Recorder", "Save error: ${it.message}") }
        }

        val totals = SessionAccumulator()
        // Sumas para las medias en vivo (mismo criterio que el resumen: muestras en movimiento).
        var moving = 0
        var cadenceSum = 0.0
        var speedSum = 0.0
        var powerSum = 0.0
        var resistanceSum = 0.0
        var resistanceCount = 0
        var heartRateSum = 0.0
        var heartRateCount = 0
        accumulator = totals
        paused = false
        fun record(raw: WorkoutSample) {
            if (heartRateClient == null) {
                raw.heartRate?.let { bpm ->
                    lastHeartRate = bpm
                    lastHeartRateAt = SystemClock.elapsedRealtime()
                    container.recorder.update {
                        it.copy(heartRate = bpm, heartRateAtMs = lastHeartRateAt, heartRateConnected = true)
                    }
                }
            }
            // En pausa se descartan las muestras; al reanudar, el acumulador continúa donde estaba.
            if (paused) {
                lastSampleAt = 0L
                return
            }
            val now = System.currentTimeMillis()
            val timed = if (machineCountsTime) {
                raw
            } else {
                if (lastSampleAt > 0) ownElapsed += ((now - lastSampleAt) / 1000.0).coerceAtMost(5.0)
                lastSampleAt = now
                raw.copy(elapsedSeconds = ownElapsed.toInt())
            }
            val sample = totals.add(timed, now)
            val heartRate = lastHeartRate?.takeIf { SystemClock.elapsedRealtime() - lastHeartRateAt < HEART_RATE_MAX_AGE_MS }
            if (sample.cadencePerMinute > 0) {
                moving++
                cadenceSum += sample.cadencePerMinute
                speedSum += sample.speedKmh
                powerSum += sample.powerWatts
            }
            sample.resistanceLevel?.let {
                resistanceSum += it
                resistanceCount++
            }
            heartRate?.let {
                heartRateSum += it
                heartRateCount++
            }
            val averages = buildMap {
                if (moving > 0) {
                    put(StatParam.CADENCE, cadenceSum / moving)
                    if (speedSum > 0) put(StatParam.SPEED, speedSum / moving)
                    if (powerSum > 0) put(StatParam.POWER, powerSum / moving)
                }
                if (resistanceCount > 0 && resistanceSum > 0) put(StatParam.RESISTANCE, resistanceSum / resistanceCount)
                if (heartRateCount > 0) put(StatParam.HEART_RATE, heartRateSum / heartRateCount)
            }
            container.recorder.update { it.copy(sample = sample, averages = averages) }
            queue.trySend {
                val id = sessionId ?: dao.insertSession(
                    SessionEntity(
                        startedAtMs = now,
                        endedAtMs = now,
                        machineName = sessionMachineName,
                        machineType = profile.type?.name,
                    ),
                ).also { sessionId = it }
                dao.insertSample(
                    SampleEntity(
                        sessionId = id,
                        timestampMs = now,
                        elapsedSeconds = sample.elapsedSeconds,
                        speedKmh = sample.speedKmh,
                        cadence = sample.cadencePerMinute,
                        distanceMeters = sample.distanceMeters,
                        strideCount = sample.strideCount,
                        powerWatts = sample.powerWatts,
                        energyKcal = sample.energyKcal,
                        resistance = sample.resistanceLevel,
                        heartRate = heartRate,
                    ),
                )
            }
        }

        if (demo || machineAddress == null) {
            log.add(DemoWorkout.NAME, "Simulated session started")
            container.recorder.update { it.copy(machineConnected = true, heartRateConnected = true) }
            val generator = DemoWorkout(settings.maxHeartRate ?: 180)
            demoJob = scope.launch {
                while (isActive) {
                    delay(1000)
                    val point = generator.next()
                    lastHeartRate = point.heartRate
                    lastHeartRateAt = SystemClock.elapsedRealtime()
                    container.recorder.update {
                        it.copy(heartRate = point.heartRate, heartRateAtMs = lastHeartRateAt)
                    }
                    record(point.sample)
                }
            }
            return
        }

        machine = GattNotifyClient(
            applicationContext,
            scope,
            machineAddress,
            FtmsUuids.FITNESS_MACHINE_SERVICE,
            listOf(dataCharacteristic, FtmsUuids.MACHINE_STATUS),
            object : GattNotifyClient.Listener {
                override fun onState(text: String, connected: Boolean) {
                    container.recorder.update { it.copy(machineConnected = connected) }
                    log.add(machineName, text)
                }

                override fun onData(characteristic: UUID, value: ByteArray) {
                    when (characteristic) {
                        dataCharacteristic -> dataParser.accept(value)?.let { record(it.sample) }
                        FtmsUuids.MACHINE_STATUS -> log.add(machineName, "Machine Status: ${value.toHex()}")
                    }
                }
            },
        ).also { it.start() }

        val heartRateAddress = settings.heartRateAddress
        if (heartRateAddress != null) {
            val heartRateName = settings.heartRateName ?: heartRateAddress
            heartRateClient = GattNotifyClient(
                applicationContext,
                scope,
                heartRateAddress,
                FtmsUuids.HEART_RATE_SERVICE,
                listOf(FtmsUuids.HEART_RATE_MEASUREMENT),
                object : GattNotifyClient.Listener {
                    override fun onState(text: String, connected: Boolean) {
                        container.recorder.update { it.copy(heartRateConnected = connected) }
                        log.add(heartRateName, text)
                    }

                    override fun onData(characteristic: UUID, value: ByteArray) {
                        val bpm = HeartRateParser.parse(value) ?: return
                        lastHeartRate = bpm
                        lastHeartRateAt = SystemClock.elapsedRealtime()
                        container.recorder.update { it.copy(heartRate = bpm, heartRateAtMs = lastHeartRateAt) }
                    }
                },
            ).also { it.start() }
        }
    }

    /** Detiene las conexiones, cierra la sesión con sus totales y para el servicio. */
    private fun finish() {
        release()
        val queue = writes
        val dao = container.dao
        writes = null
        if (queue != null) {
            queue.trySend {
                val id = sessionId
                if (id != null) {
                    val session = dao.session(id)
                    val samples = dao.samples(id)
                    if (session != null) {
                        if (samples.isEmpty()) {
                            dao.deleteSession(id)
                        } else {
                            val summary = Sessions.summarize(
                                session,
                                samples,
                                System.currentTimeMillis(),
                                container.settings.value.estimateKcal(samples),
                            )
                            dao.updateSession(summary)
                            if (container.settings.value.healthConnect && container.healthConnect.write(summary, samples)) {
                                dao.setHealthConnectAt(id, System.currentTimeMillis())
                            }
                        }
                    }
                }
            }
            queue.close()
        }
        container.recorder.value = RecorderState()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun setPaused(value: Boolean) {
        if (!running || paused == value) return
        paused = value
        if (!value) accumulator?.rebase()
        container.recorder.update { it.copy(paused = value) }
        container.eventLog.add("Recorder", if (value) "Session paused" else "Session resumed")
    }

    private fun release() {
        running = false
        paused = false
        accumulator = null
        demoJob?.cancel()
        demoJob = null
        machine?.stop()
        machine = null
        heartRateClient?.stop()
        heartRateClient = null
    }

    override fun onDestroy() {
        release()
        writes?.close()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, RecorderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, getString(R.string.notification_stop), stop)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "recorder"
        private const val NOTIFICATION_ID = 1
        private const val HEART_RATE_MAX_AGE_MS = 5000L
        const val ACTION_START = "io.github.aescanero.fs2ftms.START"
        const val ACTION_STOP = "io.github.aescanero.fs2ftms.STOP"
        const val ACTION_PAUSE = "io.github.aescanero.fs2ftms.PAUSE"
        const val ACTION_RESUME = "io.github.aescanero.fs2ftms.RESUME"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RecorderService::class.java).setAction(ACTION_START),
            )
        }

        fun setPaused(context: Context, paused: Boolean) {
            context.startService(
                Intent(context, RecorderService::class.java).setAction(if (paused) ACTION_PAUSE else ACTION_RESUME),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RecorderService::class.java).setAction(ACTION_STOP))
        }
    }
}
