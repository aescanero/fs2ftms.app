package io.github.aescanero.fs2ftms.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aescanero.fs2ftms.EmulatorSettings
import io.github.aescanero.fs2ftms.MainViewModel
import io.github.aescanero.fs2ftms.ble.ftms.TelemetryEvent
import io.github.aescanero.fs2ftms.ble.ftms.TelemetrySnapshot
import io.github.aescanero.fs2ftms.ble.ftms.TelemetryType
import io.github.aescanero.fs2ftms.protocol.MachineType
import io.github.aescanero.fs2ftms.protocol.WorkoutSample
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val tabs = listOf("Emulador", "Telemetría", "Privacidad")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    vm: MainViewModel,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onShare: () -> Unit,
) {
    val telemetry by vm.telemetry.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sample by vm.lastSample.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(topBar = { TopAppBar(title = { Text("fs2ftms ${vm.versionName}") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                }
            }
            when (tab) {
                0 -> EmulatorTab(
                    telemetry = telemetry,
                    settings = settings,
                    sample = sample,
                    hasPermissions = hasPermissions,
                    onRequestPermissions = onRequestPermissions,
                    onStart = vm::start,
                    onStop = vm::stop,
                    onMachineType = vm::setMachineType,
                    onBattery = vm::setBatteryService,
                    onCadence = vm::setCadence,
                )
                1 -> TelemetryTab(telemetry, onShare)
                else -> PrivacyTab()
            }
        }
    }
}

@Composable
private fun EmulatorTab(
    telemetry: TelemetrySnapshot,
    settings: EmulatorSettings,
    sample: WorkoutSample?,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onMachineType: (MachineType) -> Unit,
    onBattery: (Boolean) -> Unit,
    onCadence: (Int) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val status = when {
            !telemetry.running -> "Detenido"
            telemetry.connectedDevices.isNotEmpty() -> "Conectado: ${telemetry.connectedDevices.joinToString()}"
            telemetry.advertising -> "Anunciando FTMS, esperando al reloj"
            else -> "Arrancando…"
        }
        Text(status, style = MaterialTheme.typography.titleMedium)

        telemetry.events.lastOrNull { it.type == TelemetryType.ERROR }?.let {
            Text("Último error: ${it.detail}", color = MaterialTheme.colorScheme.error)
        }

        if (!hasPermissions) {
            Text("Faltan los permisos de Bluetooth (dispositivos cercanos).")
            Button(onClick = onRequestPermissions) { Text("Conceder permisos") }
        } else if (telemetry.running) {
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Detener emulador") }
        } else {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Iniciar emulador") }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Perfil anunciado", fontWeight = FontWeight.Bold)
                Text(
                    "Se aplica al iniciar. Para cambiarlo, detén el emulador.",
                    style = MaterialTheme.typography.bodySmall,
                )
                MachineType.entries.forEach { type ->
                    FilterChip(
                        selected = settings.machineType == type,
                        enabled = !telemetry.running,
                        onClick = { onMachineType(type) },
                        label = { Text(type.label) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Battery Service simulado", Modifier.weight(1f))
                    Switch(
                        checked = settings.batteryService,
                        enabled = !telemetry.running,
                        onCheckedChange = onBattery,
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Datos simulados", fontWeight = FontWeight.Bold)
                Text("Cadencia: ${settings.cadencePerMinute} pasos/min")
                Slider(
                    value = settings.cadencePerMinute.toFloat(),
                    onValueChange = { onCadence(it.toInt()) },
                    valueRange = 0f..180f,
                )
                if (sample != null) {
                    Text("Tiempo: ${sample.elapsedSeconds} s")
                    Text("Velocidad: ${"%.2f".format(sample.speedKmh)} km/h")
                    Text("Distancia: ${sample.distanceMeters} m")
                    Text("Zancadas: ${"%.1f".format(sample.strideCount)}")
                    Text("Potencia: ${sample.powerWatts} W · Energía: ${sample.energyKcal} kcal")
                } else {
                    Text("Sin emisión.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TelemetryTab(telemetry: TelemetrySnapshot, onShare: () -> Unit) {
    val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Resumen de la sesión", fontWeight = FontWeight.Bold)
                Text("Conexiones: ${telemetry.connections} · Desconexiones: ${telemetry.disconnections}")
                Text("MTU: ${telemetry.mtu.entries.joinToString { "${it.key}=${it.value}" }.ifEmpty { "—" }}")
                Text("Lecturas: ${telemetry.reads} · Escrituras Control Point: ${telemetry.controlPointWrites}")
                Text(
                    "Primera suscripción: " +
                        (telemetry.firstSubscriptionDelayMs?.let { "$it ms tras conectar" } ?: "—"),
                )
                Text(
                    "Notificaciones: ${telemetry.notificationsSent} enviadas, " +
                        "${telemetry.notificationsConfirmed} entregadas, " +
                        "${telemetry.notificationsFailed} fallidas, ${telemetry.notificationsDropped} descartadas",
                )
                if (telemetry.subscriptions.isEmpty()) {
                    Text("Suscripciones: —")
                } else {
                    telemetry.subscriptions.forEach { (device, set) ->
                        Text("Suscripciones de $device: ${set.joinToString().ifEmpty { "ninguna" }}")
                    }
                }
                Button(onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text("Exportar telemetría (JSON)") }
                Text("Eventos (${telemetry.events.size}), más recientes primero", fontWeight = FontWeight.Bold)
            }
        }
        items(telemetry.events.asReversed()) { event ->
            EventRow(event, format)
            HorizontalDivider()
        }
    }
}

@Composable
private fun EventRow(event: TelemetryEvent, format: SimpleDateFormat) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(
            "${format.format(Date(event.timestampMs))}  ${event.type}" + (event.device?.let { "  $it" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = if (event.type == TelemetryType.ERROR) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(event.detail, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PrivacyTab() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Política de privacidad", style = MaterialTheme.typography.titleMedium)
        Text(
            "fs2ftms funciona íntegramente en este dispositivo. No tiene acceso a Internet, " +
                "no crea cuentas y no envía datos a ningún servidor.",
        )
        Text(
            "Usa Bluetooth para publicar un servicio Fitness Machine (FTMS) al que se conecta el reloj. " +
                "Registra en memoria los eventos de esa conexión (direcciones Bluetooth, lecturas, " +
                "suscripciones y escrituras) con fines de diagnóstico.",
        )
        Text(
            "Esos registros solo salen del dispositivo cuando pulsas «Exportar telemetría» y eliges " +
                "con qué aplicación compartir el fichero. Se borran al iniciar una nueva sesión.",
        )
    }
}
