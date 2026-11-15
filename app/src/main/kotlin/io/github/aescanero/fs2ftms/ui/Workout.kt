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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.aescanero.fs2ftms.DeviceSettings
import io.github.aescanero.fs2ftms.RecorderState
import io.github.aescanero.fs2ftms.protocol.DemoWorkout
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
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
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
                    if (heartRateSource != null) {
                        PulseSlot(recorder, settings, staleSeconds, Modifier.weight(0.42f).fillMaxHeight())
                    }
                    Metrics(recorder.sample, wide = true, Modifier.weight(0.58f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (heartRateSource != null) {
                        PulseSlot(recorder, settings, staleSeconds, Modifier.weight(0.42f).fillMaxWidth())
                    }
                    Metrics(recorder.sample, wide = false, Modifier.weight(0.58f).fillMaxWidth())
                }
            }
        }

        when {
            !hasPermissions -> PermissionRequest(onRequestPermissions)
            recorder.recording -> FilledTonalButton(
                onClick = { confirmFinish = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("Finalizar", style = MaterialTheme.typography.titleMedium) }
            machine == null -> Text("Elige la elíptica en la pestaña Dispositivos para poder grabar.")
            else -> Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("Iniciar sesión", style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (confirmFinish && recorder.recording) {
        val elapsed = recorder.sample?.let { formatDuration(it.elapsedSeconds) }
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("¿Finalizar la sesión?") },
            text = {
                Text(
                    if (elapsed != null) {
                        "Se guardará la sesión de $elapsed."
                    } else {
                        "Todavía no hay datos; no se guardará nada."
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmFinish = false
                        onStop()
                    },
                ) { Text("Finalizar y guardar") }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Seguir entrenando") } },
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
                        Icon(Icons.Filled.Warning, contentDescription = "Error")
                        Text(
                            error,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        if (machine != null) {
                            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Reintentar")
                            }
                        }
                    }
                }
            }
            Text(
                "Elíptica: ${machine ?: "sin elegir"}  ·  Pulso: ${heartRateSource ?: "sin elegir"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val receiving = recorder.machineConnected && recorder.sample != null
    val status = when {
        !recorder.machineConnected -> "Conectando con la elíptica…"
        recorder.sample == null -> "Esperando datos: mueve la elíptica"
        machine == DemoWorkout.NAME -> "Grabando · demostración"
        else -> "Grabando"
    }
    val dotColor = if (receiving) Color(0xFFE53935) else MaterialTheme.colorScheme.outline
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(Modifier.size(14.dp)) { drawCircle(dotColor) }
            Text(status, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (heartRateSource != null && !recorder.heartRateConnected) {
            Text(
                "Sin pulso: inicia el entrenamiento en el reloj con la emisión activada.",
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
                Text("Pulso", style = MaterialTheme.typography.titleSmall)
                Text(
                    recorder.heartRate?.let { "$it ppm" } ?: NO_VALUE,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Indica tu edad o tu FC máxima en Ajustes para ver las zonas.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Metrics(sample: WorkoutSample?, wide: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1.3f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(
                "Tiempo",
                sample?.let { formatDuration(it.elapsedSeconds) } ?: NO_VALUE,
                null,
                PRIMARY_MAX_SIZE,
                Modifier.weight(1.4f).fillMaxHeight(),
            )
            MetricTile(
                "Resistencia",
                sample?.resistanceLevel?.let { "%.0f".format(it) } ?: NO_VALUE,
                null,
                PRIMARY_MAX_SIZE,
                Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(
                "Cadencia",
                sample?.cadencePerMinute?.toString() ?: NO_VALUE,
                "/min",
                SECONDARY_MAX_SIZE,
                Modifier.weight(1f).fillMaxHeight(),
            )
            MetricTile(
                "Velocidad",
                sample?.let { "%.1f".format(it.speedKmh) } ?: NO_VALUE,
                "km/h",
                SECONDARY_MAX_SIZE,
                Modifier.weight(1f).fillMaxHeight(),
            )
            MetricTile(
                "Calorías",
                sample?.energyKcal?.toString() ?: NO_VALUE,
                "kcal",
                SECONDARY_MAX_SIZE,
                Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactTile(
                "Distancia",
                sample?.let { formatDistance(it.distanceMeters) } ?: NO_VALUE,
                wide,
                Modifier.weight(1f),
            )
            CompactTile("Potencia", sample?.let { "${it.powerWatts} W" } ?: NO_VALUE, wide, Modifier.weight(1f))
            CompactTile(
                "Zancadas",
                sample?.let { "%.0f".format(it.strideCount) } ?: NO_VALUE,
                wide,
                Modifier.weight(1f),
            )
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
