package io.github.aescanero.fs2ftms.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aescanero.fs2ftms.SessionDetail
import io.github.aescanero.fs2ftms.data.SessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun dateFormat() = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.forLanguageTag("es-ES"))

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
    onBack: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val session = detail.session
    val format = remember { dateFormat() }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Historial") }
        Text(format.format(Date(session.startedAtMs)), style = MaterialTheme.typography.titleMedium)
        session.machineName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val kcalSource = if (session.manualKcal != null) "corregidas" else "máquina"
                val pulse = session.averageHeartRate
                    ?.let { "$it ppm de media, ${session.maxHeartRate} máx." }
                    ?: "sin datos"
                Text("Tiempo activo: ${formatDuration(session.durationSeconds)}")
                Text("Distancia: ${formatDistance(session.distanceMeters)}")
                Text("Calorías: ${session.kcal} kcal ($kcalSource)")
                Text("Cadencia media: ${session.averageCadence} /min")
                Text("Velocidad media: ${"%.1f".format(session.averageSpeedKmh)} km/h")
                Text("Potencia media: ${session.averagePowerWatts} W")
                session.averageResistance?.let { Text("Resistencia media: ${"%.1f".format(it)}") }
                Text("Zancadas: ${"%.0f".format(session.strideCount)}")
                Text("Pulso: $pulse")
            }
        }

        val heartRates = detail.samples.mapNotNull { it.heartRate?.toFloat() }
        if (heartRates.size > 1) Chart("Pulso (ppm)", heartRates, Color(0xFFD32F2F))
        Chart("Cadencia (/min)", detail.samples.map { it.cadence.toFloat() }, Color(0xFF1976D2))
        Chart("Potencia (W)", detail.samples.map { it.powerWatts.toFloat() }, Color(0xFFF57C00))
        if (detail.samples.any { it.resistance != null }) {
            Chart("Resistencia", detail.samples.map { (it.resistance ?: 0.0).toFloat() }, Color(0xFF388E3C))
        }

        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Exportar sesión (JSON)") }
        if (confirmDelete) {
            Text("¿Borrar esta sesión? No se puede deshacer.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Sí, borrar") }
            TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
        } else {
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Borrar sesión")
            }
        }
    }
}

/** Gráfico de línea mínimo: eje temporal uniforme y escala entre el mínimo y el máximo. */
@Composable
private fun Chart(title: String, values: List<Float>, color: Color) {
    if (values.size < 2) return
    val min = values.min()
    val max = values.max()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "$title · mín. ${"%.0f".format(min)} · máx. ${"%.0f".format(max)}",
                style = MaterialTheme.typography.labelMedium,
            )
            Canvas(Modifier.fillMaxWidth().height(96.dp).padding(top = 8.dp)) {
                val range = (max - min).takeIf { it > 0f } ?: 1f
                val step = size.width / (values.size - 1)
                val path = Path()
                values.forEachIndexed { index, value ->
                    val x = index * step
                    val y = size.height - (value - min) / range * size.height
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}
