package io.github.aescanero.fs2ftms.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aescanero.fs2ftms.DeviceSettings
import io.github.aescanero.fs2ftms.MainViewModel
import io.github.aescanero.fs2ftms.R
import io.github.aescanero.fs2ftms.RecorderState
import io.github.aescanero.fs2ftms.ble.central.DumpState
import io.github.aescanero.fs2ftms.ble.central.LogEntry
import io.github.aescanero.fs2ftms.ble.central.ScanEntry
import io.github.aescanero.fs2ftms.ble.central.ScanState
import io.github.aescanero.fs2ftms.protocol.DemoWorkout
import io.github.aescanero.fs2ftms.protocol.HeartRateZone
import io.github.aescanero.fs2ftms.protocol.HeartRateZones
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val tabs = listOf(
    R.string.tab_workout,
    R.string.tab_history,
    R.string.tab_devices,
    R.string.tab_settings,
)
private val tabIcons = listOf(
    Icons.Filled.PlayArrow,
    Icons.AutoMirrored.Filled.List,
    Icons.Filled.Search,
    Icons.Filled.Settings,
)

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
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val selectedProfile by vm.selectedProfile.collectAsStateWithLifecycle()
    val dumpProfile by vm.dumpProfile.collectAsStateWithLifecycle()
    val comparison by vm.comparison.collectAsStateWithLifecycle()
    val summary by vm.historySummary.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Mientras se graba solo está disponible la pantalla de entreno: evita salir de ella por un toque.
    val locked = recorder.recording
    LaunchedEffect(locked) { if (locked) tab = 0 }
    BackHandler(enabled = tab == 1 && detail != null) { vm.closeSession() }

    val content: @Composable () -> Unit = {
        when (tab) {
            0 -> WorkoutTab(
                recorder = recorder,
                settings = settings,
                profile = selectedProfile,
                comparison = comparison,
                onToggleCycle = { vm.setCycle(it) },
                hasPermissions = hasPermissions,
                onRequestPermissions = onRequestPermissions,
                onStart = { vm.startRecording() },
                onStop = { vm.stopRecording() },
                onPause = { vm.pauseRecording() },
                onResume = { vm.resumeRecording() },
            )
            1 -> {
                val current = detail
                if (current != null) {
                    SessionDetailScreen(
                        detail = current,
                        maxHeartRate = settings.maxHeartRate,
                        onBack = { vm.closeSession() },
                        onExport = { onShare(vm.exportSessionIntent()) },
                        onDelete = { vm.deleteSession(current.session.id) },
                        onSetManualKcal = { vm.setManualKcal(it) },
                    )
                } else {
                    HistoryList(sessions, summary, onOpen = { vm.openSession(it) }, onGoToWorkout = { tab = 0 })
                }
            }
            2 -> if (hasPermissions) {
                DevicesTab(
                    settings = settings,
                    profiles = profiles,
                    selected = selectedProfile,
                    dumpProfile = dumpProfile,
                    scan = scan,
                    dump = dump,
                    events = events,
                    onStartScan = { vm.startScan() },
                    onStopScan = { vm.stopScan() },
                    onInspect = { vm.inspect(it) },
                    onCancelDump = { vm.cancelDump() },
                    onClearDump = { vm.clearDump() },
                    onSaveProfile = { vm.saveProfileFromDump() },
                    onSelectProfile = { vm.selectProfile(it) },
                    onDeleteProfile = { vm.deleteProfile(it) },
                    onUseAsHeartRate = { vm.useDumpAsHeartRate() },
                    onClearHeartRate = { vm.clearHeartRate() },
                    onShareDump = { onShare(vm.exportDumpIntent()) },
                    onShareEvents = { onShare(vm.exportEventsIntent()) },
                )
            } else {
                PermissionRequest(onRequestPermissions)
            }
            else -> SettingsTab(
                settings = settings,
                versionName = vm.versionName,
                onDebug = { vm.setDebug(it) },
                onDemo = { vm.setDemo(it) },
                onCreateDemoSession = { vm.createDemoSession() },
                onAge = { vm.setAge(it) },
                onMaxHeartRate = { vm.setMeasuredMaxHeartRate(it) },
                onWeight = { vm.setWeight(it) },
                onMale = { vm.setMale(it) },
            )
        }
    }

    Scaffold { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val wide = maxWidth >= 600.dp && maxWidth > maxHeight
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(Modifier.width(96.dp)) {
                        tabs.forEachIndexed { index, title ->
                            NavigationRailItem(
                                selected = tab == index,
                                enabled = !locked || index == 0,
                                onClick = { tab = index },
                                icon = { Icon(tabIcons[index], contentDescription = null) },
                                label = { Text(stringResource(title), maxLines = 1, softWrap = false) },
                            )
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) { content() }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = tab) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = tab == index,
                                enabled = !locked || index == 0,
                                onClick = { tab = index },
                                text = { Text(stringResource(title), maxLines = 1) },
                            )
                        }
                    }
                    content()
                }
            }
        }
    }
}

@Composable
internal fun PermissionRequest(onRequestPermissions: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.permissions_missing))
        Button(onClick = onRequestPermissions) { Text(stringResource(R.string.permissions_grant)) }
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

/** Nombre de máquina para mostrar: el marcador interno de las sesiones simuladas se traduce. */
@Composable
internal fun machineLabel(name: String): String = if (name == DemoWorkout.NAME) stringResource(R.string.demo) else name

/** Distancia separada en valor y unidad, para mostrarlos con tamaños distintos. */
internal fun distanceParts(meters: Int): Pair<String, String> =
    if (meters >= 1000) "%.2f".format(meters / 1000.0) to "km" else meters.toString() to "m"
