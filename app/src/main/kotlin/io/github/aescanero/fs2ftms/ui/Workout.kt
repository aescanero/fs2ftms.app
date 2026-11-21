package io.github.aescanero.fs2ftms.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.aescanero.fs2ftms.Comparison
import io.github.aescanero.fs2ftms.DeviceSettings
import io.github.aescanero.fs2ftms.R
import io.github.aescanero.fs2ftms.RecorderState
import io.github.aescanero.fs2ftms.protocol.DemoWorkout
import io.github.aescanero.fs2ftms.protocol.MachineProfile
import io.github.aescanero.fs2ftms.protocol.MachineType
import io.github.aescanero.fs2ftms.protocol.Metric
import io.github.aescanero.fs2ftms.protocol.WorkoutSample
import kotlinx.coroutines.delay
import kotlin.math.min

private const val NO_VALUE = "—"
private const val STALE_PULSE_SECONDS = 5
private const val PRIMARY_MAX_SIZE = 144f
private const val SECONDARY_MAX_SIZE = 88f
private const val MIN_VALUE_SIZE = 24f

/**
 * Pantalla de entreno pensada para leerse a un metro: sin desplazamiento, con el pulso, el tiempo
 * y la resistencia en primer plano. En horizontal el indicador de pulso va a la izquierda.
 */
@Composable
internal fun WorkoutTab(
    recorder: RecorderState,
    settings: DeviceSettings,
    profile: MachineProfile?,
    comparison: Comparison?,
    onToggleCycle: (Boolean) -> Unit,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val view = LocalView.current
    DisposableEffect(recorder.recording) {
        view.keepScreenOn = recorder.recording
        onDispose { view.keepScreenOn = false }
    }

    val demo = settings.demoActive
    val machine = if (demo) DemoWorkout.NAME else settings.machineName ?: settings.machineAddress
    val heartRateSource = if (demo) DemoWorkout.NAME else settings.heartRateName ?: settings.heartRateAddress
    var confirmFinish by remember { mutableStateOf(false) }
    val now by produceState(SystemClock.elapsedRealtime(), recorder.recording) {
        while (true) {
            value = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val pulseAge = recorder.heartRateAtMs?.let { ((now - it) / 1000).toInt() }
    val staleSeconds = pulseAge?.takeIf { it > STALE_PULSE_SECONDS }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusStrip(recorder, machine, heartRateSource, onRetry = onStart)

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 600.dp && maxWidth > maxHeight
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (heartRateSource != null || comparison != null) {
                        Column(Modifier.weight(0.42f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (heartRateSource != null) {
                                PulseSlot(recorder, settings, staleSeconds, Modifier.weight(1f).fillMaxWidth())
                            }
                            if (comparison != null) {
                                ComparisonCard(comparison, settings.cycle, onToggleCycle, Modifier.fillMaxWidth())
                            }
                        }
                    }
                    Metrics(recorder.sample, wide = true, profile, Modifier.weight(0.58f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (heartRateSource != null) {
                        PulseSlot(recorder, settings, staleSeconds, Modifier.weight(0.42f).fillMaxWidth())
                    }
                    if (comparison != null) {
                        ComparisonCard(comparison, settings.cycle, onToggleCycle, Modifier.fillMaxWidth())
                    }
                    Metrics(recorder.sample, wide = false, profile, Modifier.weight(0.58f).fillMaxWidth())
                }
            }
        }

        when {
            !hasPermissions -> PermissionRequest(onRequestPermissions)
            recorder.recording -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recorder.paused) {
                    Button(onClick = onResume, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.workout_resume), style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    FilledTonalButton(onClick = onPause, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.workout_pause), style = MaterialTheme.typography.titleMedium)
                    }
                }
                OutlinedButton(
                    onClick = { confirmFinish = true },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.workout_finish), style = MaterialTheme.typography.titleMedium) }
            }
            machine == null -> Text(stringResource(R.string.workout_choose_machine))
            else -> Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.workout_start), style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (confirmFinish && recorder.recording) {
        val elapsed = recorder.sample?.let { formatDuration(it.elapsedSeconds) }
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(stringResource(R.string.workout_finish_title)) },
            text = {
                Text(
                    if (elapsed != null) {
                        stringResource(R.string.workout_finish_body, elapsed)
                    } else {
                        stringResource(R.string.workout_finish_empty)
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmFinish = false
                        onStop()
                    },
                ) { Text(stringResource(R.string.workout_finish_save)) }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.workout_keep_going)) } },
        )
    }
}

@Composable
private fun StatusStrip(recorder: RecorderState, machine: String?, heartRateSource: String?, onRetry: () -> Unit) {
    val error = recorder.error
    if (!recorder.recording) {
        Column {
            if (error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = stringResource(R.string.common_error))
                        Text(
                            error,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        if (machine != null) {
                            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.common_retry))
                            }
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.workout_devices_line, machine?.let { machineLabel(it) } ?: stringResource(R.string.common_not_set), heartRateSource?.let { machineLabel(it) } ?: stringResource(R.string.common_not_set)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val receiving = recorder.machineConnected && recorder.sample != null && !recorder.paused
    val status = when {
        recorder.paused -> stringResource(R.string.status_paused)
        !recorder.machineConnected -> stringResource(R.string.status_connecting)
        recorder.sample == null -> stringResource(R.string.status_waiting)
        machine == DemoWorkout.NAME -> stringResource(R.string.status_recording_demo)
        else -> stringResource(R.string.status_recording)
    }
    val dotColor = if (receiving) Color(0xFFE53935) else MaterialTheme.colorScheme.outline
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(Modifier.size(14.dp)) { drawCircle(dotColor) }
            Text(status, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (heartRateSource != null && !recorder.heartRateConnected) {
            Text(
                stringResource(R.string.status_no_pulse),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PulseSlot(recorder: RecorderState, settings: DeviceSettings, staleSeconds: Int?, modifier: Modifier) {
    val maxHeartRate = settings.maxHeartRate
    Box(modifier, contentAlignment = Alignment.Center) {
        if (maxHeartRate != null) {
            HeartRateGauge(
                bpm = recorder.heartRate,
                maxHeartRate = maxHeartRate,
                modifier = Modifier.sizeIn(maxWidth = 560.dp, maxHeight = 560.dp),
                staleSeconds = staleSeconds,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.label_heart_rate), style = MaterialTheme.typography.titleSmall)
                Text(
                    recorder.heartRate?.let { stringResource(R.string.value_bpm, it) } ?: NO_VALUE,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.workout_zones_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Valor y unidad de una métrica para mostrarla en una tarjeta. */
@Composable
private fun metricValue(metric: Metric, sample: WorkoutSample?, type: MachineType?): Pair<String, String?> {
    if (sample == null) return NO_VALUE to null
    return when (metric) {
        Metric.SPEED -> "%.1f".format(sample.speedKmh) to "km/h"
        Metric.CADENCE -> sample.cadencePerMinute.toString() to
            if (type == MachineType.INDOOR_BIKE) stringResource(R.string.unit_rpm) else "/min"
        Metric.DISTANCE -> distanceParts(sample.distanceMeters)
        Metric.STRIDES -> "%.0f".format(sample.strideCount) to null
        Metric.RESISTANCE -> (sample.resistanceLevel?.let { "%.0f".format(it) } ?: NO_VALUE) to null
        Metric.POWER -> sample.powerWatts.toString() to "W"
        Metric.ENERGY -> sample.energyKcal.toString() to "kcal"
        Metric.ELAPSED_TIME -> formatDuration(sample.elapsedSeconds) to null
    }
}

/**
 * Métricas según el perfil de la máquina: el tiempo siempre; junto a él la resistencia (o la
 * distancia); después hasta tres medianas y hasta tres compactas, solo de lo que la máquina envía.
 */
@Composable
private fun Metrics(sample: WorkoutSample?, wide: Boolean, profile: MachineProfile?, modifier: Modifier) {
    val available = profile?.metrics ?: Metric.entries.toSet()
    val type = profile?.type
    val second = listOf(Metric.RESISTANCE, Metric.DISTANCE).firstOrNull { it in available }
    val medium = listOf(Metric.CADENCE, Metric.SPEED, Metric.ENERGY, Metric.POWER, Metric.DISTANCE)
        .filter { it in available && it != second }
        .take(3)
    val compact = listOf(Metric.DISTANCE, Metric.POWER, Metric.STRIDES, Metric.ENERGY)
        .filter { it in available && it != second && it !in medium }
        .take(3)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1.3f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(
                stringResource(R.string.metric_time),
                sample?.let { formatDuration(it.elapsedSeconds) } ?: NO_VALUE,
                null,
                PRIMARY_MAX_SIZE,
                Modifier.weight(1.4f).fillMaxHeight(),
            )
            if (second != null) {
                val (value, unit) = metricValue(second, sample, type)
                MetricTile(stringResource(metricRes(second)), value, unit, PRIMARY_MAX_SIZE, Modifier.weight(1f).fillMaxHeight())
            }
        }
        if (medium.isNotEmpty()) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                medium.forEach { metric ->
                    val (value, unit) = metricValue(metric, sample, type)
                    MetricTile(
                        stringResource(metricRes(metric)),
                        value,
                        unit,
                        SECONDARY_MAX_SIZE,
                        Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
        if (compact.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                compact.forEach { metric ->
                    val (value, unit) = metricValue(metric, sample, type)
                    CompactTile(
                        stringResource(metricRes(metric)),
                        if (unit == null || value == NO_VALUE) value else "$value $unit",
                        wide,
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Tarjeta cuyo valor crece hasta llenar el hueco disponible, con un tope por nivel. */
@Composable
private fun MetricTile(label: String, value: String, unit: String?, maxSize: Float, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val byHeight = maxHeight.value / 1.2f
                val byWidth = maxWidth.value / (value.length.coerceAtLeast(2) * 0.62f)
                val sizeDp = min(byHeight, byWidth).coerceIn(MIN_VALUE_SIZE, maxSize)
                val fontSize = with(LocalDensity.current) { sizeDp.dp.toSp() }
                Text(
                    value,
                    fontSize = fontSize,
                    lineHeight = fontSize * 1.1f,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            if (unit != null) {
                Text(unit, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CompactTile(label: String, value: String, wide: Boolean, modifier: Modifier) {
    Card(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = if (wide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
