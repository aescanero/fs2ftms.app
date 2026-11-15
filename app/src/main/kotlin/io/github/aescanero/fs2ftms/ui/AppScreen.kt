package io.github.aescanero.fs2ftms.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.aescanero.fs2ftms.DeviceSettings
import io.github.aescanero.fs2ftms.MainViewModel
import io.github.aescanero.fs2ftms.RecorderState
import io.github.aescanero.fs2ftms.ble.central.DumpState
import io.github.aescanero.fs2ftms.ble.central.LogEntry
import io.github.aescanero.fs2ftms.ble.central.ScanEntry
import io.github.aescanero.fs2ftms.ble.central.ScanState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val tabs = listOf("Entreno", "Historial", "Dispositivos", "Ajustes")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    vm: MainViewModel,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onShare: (Intent?) -> Unit,
) {
    val recorder by vm.recorder.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val dump by vm.dump.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    BackHandler(enabled = tab == 1 && detail != null) { vm.closeSession() }

    Scaffold(topBar = { TopAppBar(title = { Text("fs2ftms") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title, maxLines = 1) })
                }
            }
            when (tab) {
                0 -> WorkoutTab(
                    recorder = recorder,
                    settings = settings,
                    hasPermissions = hasPermissions,
                    onRequestPermissions = onRequestPermissions,
                    onStart = { vm.startRecording() },
                    onStop = { vm.stopRecording() },
                )
                1 -> {
                    val current = detail
                    if (current != null) {
                        SessionDetailScreen(
                            detail = current,
                            onBack = { vm.closeSession() },
                            onExport = { onShare(vm.exportSessionIntent()) },
                            onDelete = { vm.deleteSession(current.session.id) },
                        )
                    } else {
                        HistoryList(sessions) { vm.openSession(it) }
                    }
                }
                2 -> if (hasPermissions) {
                    DevicesTab(
                        settings = settings,
                        scan = scan,
                        dump = dump,
                        events = events,
                        onStartScan = { vm.startScan() },
                        onStopScan = { vm.stopScan() },
                        onInspect = { vm.inspect(it) },
                        onCancelDump = { vm.cancelDump() },
                        onClearDump = { vm.clearDump() },
                        onUseAsMachine = { vm.useDumpAsMachine() },
                        onUseAsHeartRate = { vm.useDumpAsHeartRate() },
                        onClearMachine = { vm.clearMachine() },
                        onClearHeartRate = { vm.clearHeartRate() },
                        onShareDump = { onShare(vm.exportDumpIntent()) },
                        onShareEvents = { onShare(vm.exportEventsIntent()) },
                    )
                } else {
                    PermissionRequest(onRequestPermissions)
                }
                else -> SettingsTab(settings, vm.versionName) { vm.setDebug(it) }
            }
        }
    }
}

@Composable
private fun PermissionRequest(onRequestPermissions: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Faltan los permisos de Bluetooth (dispositivos cercanos).")
        Button(onClick = onRequestPermissions) { Text("Conceder permisos") }
    }
}

@Composable
private fun WorkoutTab(
    recorder: RecorderState,
    settings: DeviceSettings,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val machine = settings.machineName ?: settings.machineAddress
        val heartRate = settings.heartRateName ?: settings.heartRateAddress
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Elíptica: " + deviceStatus(machine, recorder.recording, recorder.machineConnected))
                Text("Pulso: " + deviceStatus(heartRate, recorder.recording, recorder.heartRateConnected))
            }
        }
        recorder.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        when {
            !hasPermissions -> PermissionRequest(onRequestPermissions)
            recorder.recording ->
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Finalizar y guardar") }
            machine == null -> Text("Elige la elíptica en la pestaña Dispositivos para poder grabar.")
            else -> Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Iniciar sesión") }
        }

        val sample = recorder.sample
        MetricRow(
            "Tiempo",
            sample?.let { formatDuration(it.elapsedSeconds) } ?: "—",
            "Distancia",
            sample?.let { formatDistance(it.distanceMeters) } ?: "—",
        )
        MetricRow(
            "Cadencia",
            sample?.let { "${it.cadencePerMinute} /min" } ?: "—",
            "Velocidad",
            sample?.let { "%.1f km/h".format(it.speedKmh) } ?: "—",
        )
        MetricRow(
            "Potencia",
            sample?.let { "${it.powerWatts} W" } ?: "—",
            "Resistencia",
            sample?.resistanceLevel?.let { "%.0f".format(it) } ?: "—",
        )
        MetricRow(
            "Pulso",
            recorder.heartRate?.let { "$it ppm" } ?: "—",
            "Calorías",
            sample?.let { "${it.energyKcal} kcal" } ?: "—",
        )

        if (recorder.recording && sample == null) {
            Text("Esperando datos: mueve la elíptica para empezar.", style = MaterialTheme.typography.bodySmall)
        }
        if (recorder.recording && heartRate != null && !recorder.heartRateConnected) {
            Text(
                "Para recibir el pulso, inicia el entrenamiento en el reloj con la emisión de pulso activada.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun deviceStatus(name: String?, recording: Boolean, connected: Boolean): String = when {
    name == null -> "sin elegir"
    !recording -> name
    connected -> "$name · conectado"
    else -> "$name · buscando…"
}

@Composable
private fun MetricRow(labelA: String, valueA: String, labelB: String, valueB: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Metric(labelA, valueA, Modifier.weight(1f))
        Metric(labelB, valueB, Modifier.weight(1f))
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun DevicesTab(
    settings: DeviceSettings,
    scan: ScanState,
    dump: DumpState,
    events: List<LogEntry>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onInspect: (String) -> Unit,
    onCancelDump: () -> Unit,
    onClearDump: () -> Unit,
    onUseAsMachine: () -> Unit,
    onUseAsHeartRate: () -> Unit,
    onClearMachine: () -> Unit,
    onClearHeartRate: () -> Unit,
    onShareDump: () -> Unit,
    onShareEvents: () -> Unit,
) {
    val result = dump.result
    if (dump.running || result != null) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (dump.running) "Consultando dispositivo…" else (result?.scan?.name ?: result?.address ?: ""),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(dump.status)
            if (dump.running) {
                OutlinedButton(onClick = onCancelDump, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
            } else if (result != null) {
                val isMachine = result.services.any { service ->
                    service.uuid == "0x1826" && service.characteristics.any { it.uuid == "0x2ACE" }
                }
                val isHeartRate = result.services.any { it.uuid == "0x180D" }
                if (isMachine) {
                    Button(onClick = onUseAsMachine, modifier = Modifier.fillMaxWidth()) { Text("Usar como elíptica") }
                }
                if (isHeartRate) {
                    Button(onClick = onUseAsHeartRate, modifier = Modifier.fillMaxWidth()) {
                        Text("Usar como fuente de pulso")
                    }
                }
                if (!isMachine && !isHeartRate) {
                    Text("Este dispositivo no ofrece datos de elíptica (0x2ACE) ni de pulso (0x180D).")
                }
                if (settings.debug) {
                    OutlinedButton(onClick = onShareDump, modifier = Modifier.fillMaxWidth()) {
                        Text("Exportar volcado (JSON)")
                    }
                }
                OutlinedButton(onClick = onClearDump, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
                if (settings.debug) {
                    Text(
                        result.toText(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        return
    }
    val format = SimpleDateFormat("HH:mm:ss", Locale.US)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectedDevice("Elíptica", settings.machineName ?: settings.machineAddress, onClearMachine)
                SelectedDevice("Pulso", settings.heartRateName ?: settings.heartRateAddress, onClearHeartRate)
                Text(
                    "Busca y pulsa un dispositivo para asignarlo. Para el reloj, inicia antes un " +
                        "entrenamiento con la emisión de pulso activada. Cierra FitShow mientras buscas la elíptica.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (scan.scanning) {
                    OutlinedButton(onClick = onStopScan, modifier = Modifier.fillMaxWidth()) {
                        Text("Detener búsqueda (${scan.entries.size})")
                    }
                } else {
                    Button(onClick = onStartScan, modifier = Modifier.fillMaxWidth()) { Text("Buscar dispositivos") }
                }
                scan.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        items(scan.entries, key = { it.address }) { entry ->
            ScanRow(entry, onInspect)
            HorizontalDivider()
        }
        if (settings.debug) {
            item {
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Depuración: eventos de conexión (${events.size})", fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = onShareEvents, modifier = Modifier.fillMaxWidth()) {
                        Text("Exportar eventos (JSON)")
                    }
                }
            }
            items(events.asReversed()) { event ->
                Text(
                    "${format.format(Date(event.timestampMs))}  ${event.source}: ${event.text}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SelectedDevice(label: String, name: String?, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ${name ?: "sin elegir"}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
        if (name != null) TextButton(onClick = onClear) { Text("Quitar") }
    }
}

@Composable
private fun ScanRow(entry: ScanEntry, onInspect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onInspect(entry.address) }.padding(vertical = 10.dp)) {
        val tags = buildList {
            if (entry.hasFtms) add("máquina")
            if ("0x180D" in entry.serviceUuids) add("pulso")
            if (!entry.connectable) add("no conectable")
        }
        Text(
            (entry.name ?: "(sin nombre)") + if (tags.isEmpty()) "" else "  ·  " + tags.joinToString(" · "),
            fontWeight = if (tags.isNotEmpty() && entry.connectable) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            "${entry.address}  ${entry.rssi} dBm",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun SettingsTab(settings: DeviceSettings, versionName: String, onDebug: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Modo depuración", fontWeight = FontWeight.Bold)
                Text(
                    "Muestra el volcado GATT completo, el registro de conexiones y sus exportaciones.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = settings.debug, onCheckedChange = onDebug)
        }
        HorizontalDivider()
        Text("Política de privacidad", style = MaterialTheme.typography.titleMedium)
        Text(
            "fs2ftms funciona íntegramente en este dispositivo. No tiene acceso a Internet, " +
                "no crea cuentas y no envía datos a ningún servidor.",
        )
        Text(
            "Usa Bluetooth para buscar dispositivos cercanos y leer los datos de la elíptica y el pulso " +
                "que emite el reloj. No usa esos datos para obtener tu ubicación.",
        )
        Text(
            "Las sesiones (tiempo, distancia, cadencia, potencia, resistencia, calorías y frecuencia cardíaca) " +
                "se guardan solo en este dispositivo. Salen de él únicamente cuando exportas un fichero y eliges " +
                "con qué aplicación compartirlo. Puedes borrar cada sesión desde el historial.",
        )
        Text("Versión $versionName", style = MaterialTheme.typography.bodySmall)
    }
}

internal fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun formatDistance(meters: Int): String =
    if (meters >= 1000) "%.2f km".format(meters / 1000.0) else "$meters m"
