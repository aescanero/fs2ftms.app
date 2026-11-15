package io.github.aescanero.fs2ftms.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aescanero.fs2ftms.SessionDetail
import io.github.aescanero.fs2ftms.data.SampleEntity
import io.github.aescanero.fs2ftms.data.SessionEntity
import io.github.aescanero.fs2ftms.protocol.DemoWorkout
import io.github.aescanero.fs2ftms.protocol.HeartRateZone
import io.github.aescanero.fs2ftms.protocol.HeartRateZones
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun dateFormat() = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.forLanguageTag("es-ES"))

private val belowZoneColor = Color(0xFFB0BEC5)

@Composable
internal fun HistoryList(sessions: List<SessionEntity>, onOpen: (Long) -> Unit) {
    if (sessions.isEmpty()) {
        Text("Todavía no hay sesiones grabadas.", Modifier.padding(16.dp))
        return
    }
    val format = remember { dateFormat() }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(sessions, key = { it.id }) { session ->
            Column(Modifier.fillMaxWidth().clickable { onOpen(session.id) }.padding(vertical = 12.dp)) {
                Text(format.format(Date(session.startedAtMs)), fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(
                        formatDuration(session.durationSeconds),
                        formatDistance(session.distanceMeters),
                        "${session.kcal} kcal",
                        session.averageHeartRate?.let { "$it ppm" },
                        session.machineName?.takeIf { it == DemoWorkout.NAME },
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
internal fun SessionDetailScreen(
    detail: SessionDetail,
    maxHeartRate: Int?,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp && maxWidth > maxHeight
        if (wide) {
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Summary(detail, maxHeartRate, onBack)
                    Actions(onExport) { confirmDelete = true }
                }
                Column(
                    Modifier.weight(0.58f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Charts(detail.samples)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Summary(detail, maxHeartRate, onBack)
                Charts(detail.samples)
                Actions(onExport) { confirmDelete = true }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar esta sesión?") },
            text = { Text("Se eliminarán sus datos de este dispositivo. No se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Summary(detail: SessionDetail, maxHeartRate: Int?, onBack: () -> Unit) {
    val session = detail.session
    val format = remember { dateFormat() }
    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("← Historial") }
    Text(format.format(Date(session.startedAtMs)), style = MaterialTheme.typography.titleMedium)
    session.machineName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyFigure("Tiempo", formatDuration(session.durationSeconds), Modifier.weight(1f))
        KeyFigure("Distancia", formatDistance(session.distanceMeters), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val kcalSource = if (session.manualKcal != null) "corregidas" else "máquina"
        KeyFigure("Calorías ($kcalSource)", "${session.kcal} kcal", Modifier.weight(1f))
        KeyFigure("Pulso medio", session.averageHeartRate?.let { "$it ppm" } ?: "—", Modifier.weight(1f))
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            session.maxHeartRate?.let { Text("Pulso máximo: $it ppm") }
            Text("Cadencia media: ${session.averageCadence} /min")
            Text("Velocidad media: ${"%.1f".format(session.averageSpeedKmh)} km/h")
            Text("Potencia media: ${session.averagePowerWatts} W")
            session.averageResistance?.let { Text("Resistencia media: ${"%.1f".format(it)}") }
            Text("Zancadas: ${"%.0f".format(session.strideCount)}")
        }
    }

    val hasPulse = detail.samples.any { it.heartRate != null }
    if (hasPulse && maxHeartRate != null) {
        ZoneBreakdown(detail.samples, maxHeartRate)
    } else if (hasPulse) {
        Text(
            "Indica tu edad o tu FC máxima en Ajustes para ver el tiempo por zona.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun KeyFigure(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Barra apilada con el reparto del tiempo por zona y su leyenda en texto. */
@Composable
private fun ZoneBreakdown(samples: List<SampleEntity>, maxHeartRate: Int) {
    val totals = remember(samples, maxHeartRate) {
        HeartRateZones(maxHeartRate).timeInZones(samples.map { it.elapsedSeconds to it.heartRate })
    }
    val total = totals.values.sum()
    if (total <= 0) return
    val ordered: List<HeartRateZone?> = listOf<HeartRateZone?>(null) + HeartRateZone.entries
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tiempo por zona de pulso", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().height(20.dp).clip(MaterialTheme.shapes.small)) {
                ordered.forEach { zone ->
                    val seconds = totals[zone] ?: 0
                    if (seconds > 0) {
                        Box(
                            Modifier
                                .weight(seconds.toFloat())
                                .fillMaxHeight()
                                .background(zone?.let { zoneColor(it) } ?: belowZoneColor),
                        )
                    }
                }
            }
            ordered.forEach { zone ->
                val seconds = totals[zone] ?: 0
                if (seconds > 0) {
                    val name = zone?.let { "Z${it.number} ${it.label}" } ?: "Por debajo de Z1"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .padding(end = 8.dp)
                                .size(12.dp)
                                .background(zone?.let { zoneColor(it) } ?: belowZoneColor),
                        )
                        Text(
                            "$name: ${formatDuration(seconds)} (${seconds * 100 / total} %)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Charts(samples: List<SampleEntity>) {
    val pulse = samples.mapNotNull { s -> s.heartRate?.let { s.elapsedSeconds to it.toFloat() } }
    if (pulse.size > 1) Chart("Pulso (ppm)", pulse, Color(0xFFEF5350))
    Chart("Cadencia (/min)", samples.map { it.elapsedSeconds to it.cadence.toFloat() }, Color(0xFF42A5F5))
    Chart("Potencia (W)", samples.map { it.elapsedSeconds to it.powerWatts.toFloat() }, Color(0xFFFFA726))
    val resistance = samples.mapNotNull { s -> s.resistance?.let { s.elapsedSeconds to it.toFloat() } }
    if (resistance.size > 1) Chart("Resistencia", resistance, Color(0xFF66BB6A))
}

@Composable
private fun Actions(onExport: () -> Unit, onAskDelete: () -> Unit) {
    Button(onClick = onExport, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("Exportar sesión (JSON)")
    }
    OutlinedButton(onClick = onAskDelete, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("Borrar sesión")
    }
}

/** Gráfico de línea sobre el tiempo activo, con mínimo, máximo y eje temporal. */
@Composable
private fun Chart(title: String, points: List<Pair<Int, Float>>, color: Color) {
    if (points.size < 2) return
    val min = points.minOf { it.second }
    val max = points.maxOf { it.second }
    val start = points.first().first
    val end = points.last().first
    val grid = MaterialTheme.colorScheme.outlineVariant
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(
                    "mín. ${"%.0f".format(min)} · máx. ${"%.0f".format(max)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Canvas(Modifier.fillMaxWidth().height(150.dp).padding(vertical = 8.dp)) {
                val range = (max - min).takeIf { it > 0f } ?: 1f
                val span = (end - start).coerceAtLeast(1).toFloat()
                for (line in 0..2) {
                    val y = size.height * line / 2f
                    drawLine(grid, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y))
                }
                val path = Path()
                points.forEachIndexed { index, (elapsed, value) ->
                    val x = (elapsed - start) / span * size.width
                    val y = size.height - (value - min) / range * size.height
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
            }
            Row {
                Text(formatDuration(start), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text(formatDuration((start + end) / 2), style = MaterialTheme.typography.labelSmall)
                Text(
                    formatDuration(end),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}
