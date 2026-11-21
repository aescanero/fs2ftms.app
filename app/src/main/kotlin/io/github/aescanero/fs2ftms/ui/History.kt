package io.github.aescanero.fs2ftms.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.aescanero.fs2ftms.R
import io.github.aescanero.fs2ftms.SessionDetail
import io.github.aescanero.fs2ftms.data.KcalSource
import io.github.aescanero.fs2ftms.data.SampleEntity
import io.github.aescanero.fs2ftms.data.SessionEntity
import io.github.aescanero.fs2ftms.protocol.DemoWorkout
import io.github.aescanero.fs2ftms.protocol.HeartRateZone
import io.github.aescanero.fs2ftms.protocol.HeartRateZones
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun dateFormat() = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault())

private fun belowZoneColor(dark: Boolean) = if (dark) Color(0xFF37474F) else Color(0xFFCFD8DC)

// ---- Lista -------------------------------------------------------------------------------------

@Composable
internal fun HistoryList(sessions: List<SessionEntity>, onOpen: (Long) -> Unit, onGoToWorkout: () -> Unit) {
    if (sessions.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.history_empty_body), style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = onGoToWorkout, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.history_go_to_workout))
            }
        }
        return
    }
    val format = remember { dateFormat() }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Text(stringResource(R.string.tab_history), style = MaterialTheme.typography.headlineSmall)
                Text(
                    pluralStringResource(R.plurals.history_sessions, sessions.size, sessions.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(sessions, key = { it.id }) { session ->
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                SessionRow(session, format.format(Date(session.startedAtMs)), onOpen)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionEntity, date: String, onOpen: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(session.id) }.heightIn(min = 72.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(date, style = MaterialTheme.typography.titleMedium)
                if (session.machineName == DemoWorkout.NAME) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            stringResource(R.string.demo),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val (distance, distanceUnit) = distanceParts(session.distanceMeters)
                MiniFigure(formatDuration(session.durationSeconds), null)
                MiniFigure(distance, distanceUnit)
                MiniFigure(session.kcal.toString(), "kcal")
                session.averageHeartRate?.let { MiniFigure(it.toString(), stringResource(R.string.unit_bpm)) }
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.history_open_session),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MiniFigure(value: String, unit: String?) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (unit != null) {
            Text(
                " $unit",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Detalle -----------------------------------------------------------------------------------

@Composable
internal fun SessionDetailScreen(
    detail: SessionDetail,
    maxHeartRate: Int?,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onSetManualKcal: (Int?) -> Unit,
) {
    val session = detail.session
    val format = remember { dateFormat() }
    var confirmDelete by remember { mutableStateOf(false) }
    var editKcal by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.history_back))
            }
            Column(Modifier.weight(1f)) {
                Text(format.format(Date(session.startedAtMs)), style = MaterialTheme.typography.titleMedium)
                session.machineName?.let {
                    Text(
                        machineLabel(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.history_delete), tint = MaterialTheme.colorScheme.error)
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 600.dp && maxWidth > maxHeight
            if (wide) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(0.42f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Summary(detail, maxHeartRate) { editKcal = true }
                        }
                        ExportButton(onExport)
                    }
                    Column(Modifier.weight(0.58f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Charts(detail.samples, maxHeartRate, fill = true)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Summary(detail, maxHeartRate) { editKcal = true }
                        Charts(detail.samples, maxHeartRate, fill = false)
                    }
                    Box(Modifier.padding(top = 8.dp)) { ExportButton(onExport) }
                }
            }
        }
    }

    if (editKcal) {
        var text by remember { mutableStateOf(session.manualKcal?.toString().orEmpty()) }
        AlertDialog(
            onDismissRequest = { editKcal = false },
            title = { Text(stringResource(R.string.history_kcal_edit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.history_kcal_edit_body))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.metric_calories)) },
                        suffix = { Text("kcal") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        editKcal = false
                        onSetManualKcal(text.toIntOrNull())
                    },
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { editKcal = false }) { Text("Cancelar") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_body)) },
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
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ExportButton(onExport: () -> Unit) {
    Button(onClick = onExport, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.history_export))
    }
}

@Composable
private fun Summary(detail: SessionDetail, maxHeartRate: Int?, onEditKcal: () -> Unit) {
    val session = detail.session
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyFigure(stringResource(R.string.metric_time), formatDuration(session.durationSeconds), Modifier.weight(1f))
        KeyFigure(stringResource(R.string.metric_distance), formatDistance(session.distanceMeters), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val kcalSource = when (session.kcalSource) {
            KcalSource.MANUAL -> stringResource(R.string.kcal_source_manual)
            KcalSource.HEART_RATE -> stringResource(R.string.kcal_source_heart_rate)
            KcalSource.MACHINE -> stringResource(R.string.kcal_source_machine)
        }
        KeyFigure(stringResource(R.string.history_kcal_with_source, kcalSource), "${session.kcal} kcal", Modifier.weight(1f))
        KeyFigure(
            stringResource(R.string.history_avg_heart_rate),
            session.averageHeartRate?.let { stringResource(R.string.value_bpm, it) } ?: "—",
            Modifier.weight(1f),
        )
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            session.maxHeartRate?.let { AverageRow(stringResource(R.string.history_max_heart_rate), stringResource(R.string.value_bpm, it)) }
            AverageRow(stringResource(R.string.history_avg_cadence), "${session.averageCadence} /min")
            AverageRow(stringResource(R.string.history_avg_speed), "${"%.1f".format(session.averageSpeedKmh)} km/h")
            AverageRow(stringResource(R.string.history_avg_power), "${session.averagePowerWatts} W")
            session.averageResistance?.let { AverageRow(stringResource(R.string.history_avg_resistance), "%.1f".format(it)) }
            AverageRow(stringResource(R.string.metric_strides), "%.0f".format(session.strideCount))
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            AverageRow(stringResource(R.string.history_kcal_machine), "${session.machineKcal} kcal")
            AverageRow(
                stringResource(R.string.history_kcal_estimated),
                session.estimatedKcal?.let { "$it kcal" } ?: stringResource(R.string.common_no_data),
            )
            session.manualKcal?.let { AverageRow(stringResource(R.string.history_kcal_manual), "$it kcal") }
            if (session.estimatedKcal == null) {
                Text(
                    stringResource(R.string.history_kcal_needs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onEditKcal, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.history_kcal_edit))
            }
        }
    }

    val hasPulse = detail.samples.any { it.heartRate != null }
    if (hasPulse && maxHeartRate != null) {
        ZoneBreakdown(detail.samples, maxHeartRate)
    } else if (hasPulse) {
        Text(
            stringResource(R.string.history_zones_hint),
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

@Composable
private fun AverageRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
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
    val dark = isSystemInDarkTheme()
    val outline = MaterialTheme.colorScheme.outline
    val ordered: List<HeartRateZone?> = listOf<HeartRateZone?>(null) + HeartRateZone.entries
    val swatch = RoundedCornerShape(4.dp)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.history_time_in_zones), style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.fillMaxWidth().height(20.dp).clip(MaterialTheme.shapes.small),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ordered.forEach { zone ->
                    val seconds = totals[zone] ?: 0
                    // Los tramos de menos del 1 % no se dibujan en la barra, pero sí en la leyenda.
                    if (seconds * 100 >= total) {
                        val color = if (zone != null) zoneColor(zone, dark) else belowZoneColor(dark)
                        Box(Modifier.weight(seconds.toFloat()).fillMaxHeight().background(color))
                    }
                }
            }
            ordered.forEach { zone ->
                val seconds = totals[zone] ?: 0
                if (seconds > 0) {
                    val color = if (zone != null) zoneColor(zone, dark) else belowZoneColor(dark)
                    val name = if (zone != null) {
                        stringResource(R.string.zone_name, zone.number, stringResource(zoneLabelRes(zone)))
                    } else {
                        stringResource(R.string.zone_below)
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(16.dp)
                                .background(color, swatch)
                                .then(if (zone == null) Modifier.border(1.5.dp, outline, swatch) else Modifier),
                        )
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        Text(
                            "${formatDuration(seconds)}  ·  ${seconds * 100 / total} %",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

// ---- Gráficas ----------------------------------------------------------------------------------

@Composable
private fun ColumnScope.Charts(samples: List<SampleEntity>, maxHeartRate: Int?, fill: Boolean) {
    val pulse = samples.mapNotNull { s -> s.heartRate?.let { s.elapsedSeconds to it.toFloat() } }
    val resistance = samples.mapNotNull { s -> s.resistance?.let { s.elapsedSeconds to it.toFloat() } }
    val modifier = if (fill) Modifier.weight(1f) else Modifier
    if (pulse.size > 1) {
        Chart(
            title = stringResource(R.string.chart_heart_rate),
            points = pulse,
            color = Color(0xFFEF5350),
            modifier = modifier,
            fill = fill,
            fromZero = false,
            zones = maxHeartRate?.let { HeartRateZones(it) },
        )
    }
    Chart(stringResource(R.string.chart_cadence), samples.map { it.elapsedSeconds to it.cadence.toFloat() }, Color(0xFF42A5F5), modifier, fill)
    Chart(stringResource(R.string.chart_power), samples.map { it.elapsedSeconds to it.powerWatts.toFloat() }, Color(0xFFFFA726), modifier, fill)
    if (resistance.size > 1) {
        Chart(stringResource(R.string.metric_resistance), resistance, Color(0xFF66BB6A), modifier, fill, stepped = true)
    }
}

/**
 * Gráfico de línea sobre el tiempo activo. El eje vertical parte de cero (salvo en el pulso) para
 * que una variación pequeña no parezca un desplome, y muestra sus valores.
 */
@Composable
private fun Chart(
    title: String,
    points: List<Pair<Int, Float>>,
    color: Color,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    fromZero: Boolean = true,
    stepped: Boolean = false,
    zones: HeartRateZones? = null,
) {
    if (points.size < 2) return
    val dataMin = points.minOf { it.second }
    val dataMax = points.maxOf { it.second }
    val low = if (fromZero) 0f else (dataMin - 10f).coerceAtLeast(0f)
    val high = if (fromZero) (dataMax * 1.1f).coerceAtLeast(1f) else dataMax + 10f
    val start = points.first().first
    val end = points.last().first
    val grid = MaterialTheme.colorScheme.outlineVariant
    val dark = isSystemInDarkTheme()
    val axisStyle = MaterialTheme.typography.labelSmall
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val plotModifier = if (fill) Modifier.heightIn(min = 60.dp) else Modifier.height(150.dp)

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp).then(if (fill) Modifier.fillMaxHeight() else Modifier)) {
            Row {
                Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.chart_min_max, "%.0f".format(dataMin), "%.0f".format(dataMax)),
                    style = MaterialTheme.typography.labelMedium,
                    color = axisColor,
                )
            }
            Row(
                (if (fill) plotModifier.weight(1f) else plotModifier).fillMaxWidth().padding(vertical = 6.dp),
            ) {
                Column(
                    Modifier.fillMaxHeight().padding(end = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    Text("%.0f".format(high), style = axisStyle, color = axisColor)
                    Text("%.0f".format((high + low) / 2f), style = axisStyle, color = axisColor)
                    Text("%.0f".format(low), style = axisStyle, color = axisColor)
                }
                Canvas(Modifier.weight(1f).fillMaxHeight()) {
                    val range = high - low
                    val span = (end - start).coerceAtLeast(1).toFloat()
                    val height = size.height
                    val width = size.width
                    if (zones != null) {
                        HeartRateZone.entries.forEach { zone ->
                            val bottom = zones.lowerBound(zone).toFloat().coerceIn(low, high)
                            val top = zones.upperBound(zone).toFloat().coerceIn(low, high)
                            if (top > bottom) {
                                val yTop = height - (top - low) / range * height
                                val yBottom = height - (bottom - low) / range * height
                                drawRect(
                                    color = zoneColor(zone, dark).copy(alpha = 0.18f),
                                    topLeft = Offset(0f, yTop),
                                    size = Size(width, yBottom - yTop),
                                )
                            }
                        }
                    }
                    for (line in 0..2) {
                        val y = height * line / 2f
                        drawLine(grid, Offset(0f, y), Offset(width, y))
                    }
                    val path = Path()
                    var previousY = 0f
                    points.forEachIndexed { index, point ->
                        val x = (point.first - start) / span * width
                        val y = height - (point.second - low) / range * height
                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            if (stepped) path.lineTo(x, previousY)
                            path.lineTo(x, y)
                        }
                        previousY = y
                    }
                    drawPath(path, color, style = Stroke(width = 2.5.dp.toPx()))
                }
            }
            Row {
                Text(formatDuration(start), style = axisStyle, color = axisColor, modifier = Modifier.weight(1f))
                Text(formatDuration((start + end) / 2), style = axisStyle, color = axisColor)
                Text(
                    formatDuration(end),
                    style = axisStyle,
                    color = axisColor,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
