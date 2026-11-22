package io.github.aescanero.ftmslab.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aescanero.ftmslab.DeviceSettings
import io.github.aescanero.ftmslab.R
import io.github.aescanero.ftmslab.ble.central.DumpState
import io.github.aescanero.ftmslab.ble.central.LogEntry
import io.github.aescanero.ftmslab.ble.central.ScanEntry
import io.github.aescanero.ftmslab.ble.central.ScanState
import io.github.aescanero.ftmslab.protocol.MachineProfile
import io.github.aescanero.ftmslab.protocol.MachineType
import io.github.aescanero.ftmslab.protocol.Metric
import io.github.aescanero.ftmslab.protocol.ProfileStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@StringRes
internal fun machineTypeRes(type: MachineType): Int = when (type) {
    MachineType.TREADMILL -> R.string.type_treadmill
    MachineType.CROSS_TRAINER -> R.string.type_cross_trainer
    MachineType.STEP_CLIMBER -> R.string.type_step_climber
    MachineType.STAIR_CLIMBER -> R.string.type_stair_climber
    MachineType.ROWER -> R.string.type_rower
    MachineType.INDOOR_BIKE -> R.string.type_indoor_bike
}

@StringRes
internal fun metricRes(metric: Metric): Int = when (metric) {
    Metric.SPEED -> R.string.metric_speed
    Metric.CADENCE -> R.string.metric_cadence
    Metric.DISTANCE -> R.string.metric_distance
    Metric.STRIDES -> R.string.metric_strides
    Metric.RESISTANCE -> R.string.metric_resistance
    Metric.POWER -> R.string.metric_power
    Metric.ENERGY -> R.string.metric_calories
    Metric.ELAPSED_TIME -> R.string.metric_time
    Metric.HEART_RATE -> R.string.label_heart_rate
}

/** Estado del perfil en una frase: qué es la máquina y si se puede grabar. */
@Composable
internal fun profileStatusText(profile: MachineProfile): String {
    val type = profile.type?.let { stringResource(machineTypeRes(it)) }.orEmpty()
    return when (profile.status) {
        ProfileStatus.SUPPORTED -> stringResource(R.string.profile_supported, type)
        ProfileStatus.NO_FTMS -> stringResource(R.string.profile_no_ftms)
        ProfileStatus.UNKNOWN_TYPE -> stringResource(R.string.profile_unknown_type)
        ProfileStatus.TYPE_NOT_SUPPORTED -> stringResource(R.string.profile_type_not_supported, type)
        ProfileStatus.NO_DATA -> stringResource(R.string.profile_no_data, type)
    }
}

@Composable
internal fun DevicesTab(
    settings: DeviceSettings,
    profiles: List<MachineProfile>,
    selected: MachineProfile?,
    dumpProfile: MachineProfile?,
    scan: ScanState,
    dump: DumpState,
    events: List<LogEntry>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onInspect: (String) -> Unit,
    onCancelDump: () -> Unit,
    onClearDump: () -> Unit,
    onSaveProfile: () -> Unit,
    onSelectProfile: (MachineProfile) -> Unit,
    onDeleteProfile: (MachineProfile) -> Unit,
    onUseAsHeartRate: () -> Unit,
    onClearHeartRate: () -> Unit,
    onShareDump: () -> Unit,
    onShareEvents: () -> Unit,
) {
    val result = dump.result
    if (dump.running || result != null) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (dump.running) {
                    stringResource(R.string.devices_querying)
                } else {
                    result?.scan?.name ?: result?.address.orEmpty()
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(dump.status)
            if (dump.running) {
                Text(stringResource(R.string.devices_capture_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onCancelDump, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_cancel))
                }
            } else if (result != null) {
                val isHeartRate = result.services.any { it.uuid == "0x180D" }
                val machine = dumpProfile?.takeIf { it.status != ProfileStatus.NO_FTMS || !isHeartRate }
                if (machine != null) {
                    ProfileCard(machine, highlighted = machine.supported)
                    if (machine.supported) {
                        Button(onClick = onSaveProfile, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.devices_save_profile))
                        }
                    }
                }
                if (isHeartRate) {
                    Button(onClick = onUseAsHeartRate, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.devices_use_as_heart_rate))
                    }
                }
                if (settings.debug) {
                    OutlinedButton(onClick = onShareDump, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.devices_export_dump))
                    }
                }
                OutlinedButton(onClick = onClearDump, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_back))
                }
                if (settings.debug) {
                    Text(result.toText(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
        return
    }

    val format = SimpleDateFormat("HH:mm:ss", Locale.US)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Text(
                stringResource(R.string.devices_machines),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
        }
        if (profiles.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.devices_no_machines),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        items(profiles, key = { it.address }) { profile ->
            val inUse = selected?.address == profile.address
            Column(Modifier.widthIn(max = 720.dp).padding(bottom = 8.dp)) {
                ProfileCard(profile, highlighted = inUse)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (inUse) {
                        Text(
                            stringResource(R.string.devices_in_use),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(12.dp),
                        )
                    } else if (profile.supported) {
                        TextButton(onClick = { onSelectProfile(profile) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.devices_use))
                        }
                    }
                    TextButton(onClick = { onDeleteProfile(profile) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectedDevice(
                    stringResource(R.string.label_heart_rate),
                    settings.heartRateName ?: settings.heartRateAddress,
                    onClearHeartRate,
                )
                Text(stringResource(R.string.devices_help), style = MaterialTheme.typography.bodySmall)
                if (scan.scanning) {
                    OutlinedButton(onClick = onStopScan, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.devices_stop_scan, scan.entries.size))
                    }
                } else {
                    Button(onClick = onStartScan, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.devices_scan))
                    }
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
                    Text(stringResource(R.string.devices_debug_events, events.size), fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = onShareEvents, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.devices_export_events))
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

/** Ficha de un perfil: nombre, qué es, si se puede grabar y qué datos aporta. */
@Composable
internal fun ProfileCard(profile: MachineProfile, highlighted: Boolean) {
    val colors = if (highlighted) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                machineLabel(profile.name ?: profile.address),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                profileStatusText(profile),
                style = MaterialTheme.typography.bodyMedium,
                color = if (profile.supported) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
            val device = listOfNotNull(
                profile.manufacturer,
                profile.model,
                profile.firmware?.let { stringResource(R.string.profile_firmware, it) },
            )
            if (device.isNotEmpty()) {
                Text(device.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            if (profile.metrics.isNotEmpty()) {
                val names = Metric.entries.filter { it in profile.metrics }.map { stringResource(metricRes(it)) }
                Text(
                    stringResource(R.string.profile_metrics, names.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val min = profile.resistanceMin
            val max = profile.resistanceMax
            if (min != null && max != null && profile.has(Metric.RESISTANCE)) {
                Text(
                    stringResource(R.string.profile_resistance_range, "%.0f".format(min), "%.0f".format(max)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SelectedDevice(label: String, name: String?, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.devices_selected, label, name ?: stringResource(R.string.common_not_set)),
            Modifier.weight(1f),
            fontWeight = FontWeight.Bold,
        )
        if (name != null) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.devices_remove)) }
        }
    }
}

@Composable
private fun ScanRow(entry: ScanEntry, onInspect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onInspect(entry.address) }.padding(vertical = 10.dp)) {
        val machineTag = stringResource(R.string.devices_tag_machine)
        val heartRateTag = stringResource(R.string.devices_tag_heart_rate)
        val notConnectableTag = stringResource(R.string.devices_tag_not_connectable)
        val tags = buildList {
            if (entry.hasFtms) add(machineTag)
            if ("0x180D" in entry.serviceUuids) add(heartRateTag)
            if (!entry.connectable) add(notConnectableTag)
        }
        Text(
            (entry.name ?: stringResource(R.string.devices_unnamed)) +
                if (tags.isEmpty()) "" else "  ·  " + tags.joinToString(" · "),
            fontWeight = if (tags.isNotEmpty() && entry.connectable) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            "${entry.address}  ${entry.rssi} dBm",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}
