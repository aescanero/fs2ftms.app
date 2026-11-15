package io.github.aescanero.fs2ftms.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.aescanero.fs2ftms.protocol.HeartRateZone
import io.github.aescanero.fs2ftms.protocol.HeartRateZones
import kotlin.math.cos
import kotlin.math.sin

internal fun zoneColor(zone: HeartRateZone): Color = when (zone) {
    HeartRateZone.RECOVERY -> Color(0xFF90A4AE)
    HeartRateZone.LIGHT_AEROBIC -> Color(0xFF42A5F5)
    HeartRateZone.AEROBIC -> Color(0xFF66BB6A)
    HeartRateZone.ANAEROBIC -> Color(0xFFFFA726)
    HeartRateZone.MAXIMUM -> Color(0xFFEF5350)
}

private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f
private const val GAP_ANGLE = 2f

/**
 * Indicador circular del pulso: cinco arcos, uno por zona, con la zona actual resaltada y un
 * marcador en la posición exacta. El centro muestra las ppm y el nombre de la zona, de modo que
 * la lectura no depende solo del color.
 */
@Composable
internal fun HeartRateGauge(bpm: Int?, maxHeartRate: Int, modifier: Modifier = Modifier) {
    val zones = HeartRateZones(maxHeartRate)
    val current = bpm?.let { zones.zoneOf(it) }
    val markerColor = MaterialTheme.colorScheme.onSurface
    val fraction by animateFloatAsState(
        targetValue = bpm?.let { zones.fraction(it) } ?: 0f,
        animationSpec = tween(400),
        label = "pulso",
    )
    val description = when {
        bpm == null -> "Sin pulso"
        current == null -> "Pulso $bpm pulsaciones por minuto, por debajo de la zona 1"
        else -> "Pulso $bpm pulsaciones por minuto, zona ${current.number} ${current.label}"
    }
    Box(
        modifier.aspectRatio(1f).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val stroke = size.minDimension * 0.09f
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            val perZone = SWEEP_ANGLE / HeartRateZone.entries.size
            HeartRateZone.entries.forEachIndexed { index, zone ->
                val active = zone == current
                drawArc(
                    color = zoneColor(zone).copy(alpha = if (active) 1f else if (bpm == null) 0.6f else 0.3f),
                    startAngle = START_ANGLE + index * perZone + GAP_ANGLE / 2f,
                    sweepAngle = perZone - GAP_ANGLE,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = if (active) stroke * 1.35f else stroke, cap = StrokeCap.Butt),
                )
            }
            if (bpm != null) {
                val angle = Math.toRadians((START_ANGLE + SWEEP_ANGLE * fraction).toDouble())
                val radius = diameter / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                val position = Offset(
                    center.x + radius * cos(angle).toFloat(),
                    center.y + radius * sin(angle).toFloat(),
                )
                drawCircle(markerColor, radius = stroke * 0.95f, center = position)
                drawCircle(current?.let { zoneColor(it) } ?: Color.Gray, radius = stroke * 0.6f, center = position)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                bpm?.toString() ?: "—",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("ppm", style = MaterialTheme.typography.labelLarge)
            val label = when {
                bpm == null -> "Sin pulso"
                current == null -> "Por debajo de Z1"
                else -> "Z${current.number} · ${current.label}"
            }
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = current?.let { zoneColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (bpm != null) {
                Text(
                    "${bpm * 100 / maxHeartRate} % de $maxHeartRate",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
