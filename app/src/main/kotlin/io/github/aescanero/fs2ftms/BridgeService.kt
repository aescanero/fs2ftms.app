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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.aescanero.fs2ftms.ble.central.FtmsSourceClient
import io.github.aescanero.fs2ftms.ble.ftms.FtmsServer
import io.github.aescanero.fs2ftms.ble.ftms.FtmsServerConfig
import io.github.aescanero.fs2ftms.ble.ftms.TelemetryType
import io.github.aescanero.fs2ftms.protocol.MachineType
import io.github.aescanero.fs2ftms.protocol.WorkoutSample
import io.github.aescanero.fs2ftms.protocol.WorkoutSimulator
import io.github.aescanero.fs2ftms.protocol.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Servicio en primer plano que mantiene el servidor FTMS con la pantalla apagada. */
class BridgeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var server: FtmsServer? = null
    private var job: Job? = null
    private var source: FtmsSourceClient? = null

    private val container: AppContainer
        get() = (application as Fs2FtmsApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            stopSelf()
        } else {
            startEmulator()
        }
        return START_NOT_STICKY
    }

    private fun startEmulator() {
        if (server != null) return
        val telemetry = container.telemetry
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } catch (e: Exception) {
            telemetry.event(TelemetryType.ERROR, null, "No se pudo iniciar el servicio en primer plano: ${e.message}")
            stopSelf()
            return
        }
        telemetry.newSession()
        val settings = container.settings.value
        val sourceAddress = settings.sourceAddress
        val bridge = settings.useBridge && sourceAddress != null
        val ftms = FtmsServer(
            applicationContext,
            FtmsServerConfig(
                // El reloj solo acepta bicicleta: en modo puente la elíptica se presenta así.
                if (bridge) MachineType.INDOOR_BIKE else settings.machineType,
                settings.batteryService,
                settings.pauseAdvertisingWhileConnected,
                settings.requestBonding,
            ),
            telemetry,
        )
        if (!ftms.start()) {
            ftms.stop()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        server = ftms
        if (bridge && sourceAddress != null) {
            val name = settings.sourceName ?: sourceAddress
            telemetry.event(TelemetryType.SOURCE, name, "Modo puente: origen $sourceAddress")
            val client = FtmsSourceClient(
                applicationContext,
                scope,
                sourceAddress,
                object : FtmsSourceClient.Listener {
                    override fun onState(text: String, connected: Boolean) {
                        telemetry.update { it.copy(sourceConnected = connected) }
                        telemetry.event(TelemetryType.SOURCE, name, text)
                    }

                    override fun onSample(sample: WorkoutSample) {
                        container.lastSample.value = sample
                        telemetry.update { it.copy(sourceSamples = it.sourceSamples + 1) }
                        ftms.publish(sample)
                    }

                    override fun onMachineStatus(value: ByteArray) {
                        telemetry.event(TelemetryType.SOURCE, name, "Machine Status: ${value.toHex()}")
                    }
                },
            )
            source = client
            client.start()
            return
        }
        val simulator = WorkoutSimulator()
        job = scope.launch {
            while (isActive) {
                delay(1000)
                val sample = simulator.next(container.settings.value.cadencePerMinute)
                container.lastSample.value = sample
                ftms.publish(sample)
            }
        }
    }

    private fun shutdown() {
        job?.cancel()
        job = null
        source?.stop()
        source = null
        server?.stop()
        server = null
        container.lastSample.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        shutdown()
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
            Intent(this, BridgeService::class.java).setAction(ACTION_STOP),
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
        private const val CHANNEL_ID = "bridge"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "io.github.aescanero.fs2ftms.START"
        const val ACTION_STOP = "io.github.aescanero.fs2ftms.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BridgeService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, BridgeService::class.java).setAction(ACTION_STOP))
        }
    }
}
