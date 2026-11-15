package io.github.aescanero.fs2ftms.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.aescanero.fs2ftms.protocol.HeartRateZone
import io.github.aescanero.fs2ftms.protocol.HeartRateZones
import kotlin.math.cos
import kotlin.math.sin

/** Paleta fija de zonas: tonos claros sobre fondo oscuro y tonos oscuros sobre fondo claro. */
internal fun zoneColor(zone: HeartRateZone, dark: Boolean): Color = if (dark) {
    when (zone) {
        HeartRateZone.RECOVERY -> Color(0xFF90A4AE)
        HeartRateZone.LIGHT_AEROBIC -> Color(0xFF42A5F5)
        HeartRateZone.AEROBIC -> Color(0xFF66BB6A)
        HeartRateZone.ANAEROBIC -> Color(0xFFFFA726)
        HeartRateZone.MAXIMUM -> Color(0xFFEF5350)
    }
} else {
    when (zone) {
        HeartRateZone.RECOVERY -> Color(0xFF607D8B)
        HeartRateZone.LIGHT_AEROBIC -> Color(0xFF1E88E5)
        HeartRateZone.AEROBIC -> Color(0xFF2E7D32)
        HeartRateZone.ANAEROBIC -> Color(0xFFEF6C00)
        HeartRateZone.MAXIMUM -> Color(0xFFC62828)
    }
}

private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f
private const val GAP_ANGLE = 2f

/**
 * Indicador circular del pulso: cinco arcos, uno por zona, con la zona actual resaltada y una
 * aguja en la posición exacta. Los textos del centro escalan con el diámetro, y la lectura no
 * depende solo del color (ppm, número y nombre de zona).
 */
@Composable
internal fun HeartRateGauge(
    bpm: Int?,
    maxHeartRate: Int,
    modifier: Modifier = Modifier,
    staleSeconds: Int? = null,
) {
    val zones = HeartRateZones(maxHeartRate)
    val current = bpm?.let { zones.zoneOf(it) }
    val dark = isSystemInDarkTheme()
    val needleColor = MaterialTheme.colorScheme.onSurface
    val backgroundColor = MaterialTheme.colorScheme.background
    val dim = if (staleSeconds != null) 0.4f else 1f
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
    BoxWithConstraints(
        modifier.aspectRatio(1f).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val diameter = minOf(maxWidth, maxHeight).value
        val density = LocalDensity.current
        fun scaled(factor: Float, minimum: Float): TextUnit =
            with(density) { (diameter * factor).coerceAtLeast(minimum).dp.toSp() }

        Canvas(Modifier.fillMaxSize().padding(12.dp).alpha(dim)) {
            val stroke = size.minDimension * 0.08f
            val arcDiameter = size.minDimension - stroke * 2f
            val topLeft = Offset((size.width - arcDiameter) / 2f, (size.height - arcDiameter) / 2f)
            val arcSize = Size(arcDiameter, arcDiameter)
            val perZone = SWEEP_ANGLE / HeartRateZone.entries.size
            HeartRateZone.entries.forEachIndexed { index, zone ->
                val active = zone == current
                drawArc(
                    color = zoneColor(zone, dark).copy(alpha = if (active) 1f else if (bpm == null) 0.8f else 0.65f),
                    startAngle = START_ANGLE + index * perZone + GAP_ANGLE / 2f,
                    sweepAngle = perZone - GAP_ANGLE,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = if (active) stroke * 1.5f else stroke, cap = StrokeCap.Butt),
                )
            }
            if (bpm != null) {
                val angle = Math.toRadians((START_ANGLE + SWEEP_ANGLE * fraction).toDouble())
                val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = arcDiameter / 2f
                val from = center + direction * (radius - stroke * 0.9f)
                val to = center + direction * (radius + stroke * 0.9f)
                drawLine(backgroundColor, from, to, strokeWidth = 10.dp.toPx(), cap = StrokeCap.Round)
                drawLine(needleColor, from, to, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val ppmSize = scaled(0.24f, 40f)
            Text(
                bpm?.toString() ?: "—",
                fontSize = ppmSize,
                lineHeight = ppmSize * 1.05f,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                maxLines = 1,
            )
            Text("ppm", fontSize = scaled(0.055f, 14f))
            val label = when {
                bpm == null -> "Sin pulso"
                current == null -> "Por debajo de Z1"
                else -> "Z${current.number} · ${current.label}"
            }
            val labelSize = scaled(0.055f, 16f)
            Text(
                label,
                fontSize = labelSize,
                lineHeight = labelSize * 1.2f,
                fontWeight = FontWeight.Bold,
                color = current?.let { zoneColor(it, dark) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            if (staleSeconds != null) {
                Text(
                    "Última lectura hace $staleSeconds s",
                    fontSize = scaled(0.042f, 14f),
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (bpm != null) {
                Text(
                    "${bpm * 100 / maxHeartRate} % de $maxHeartRate",
                    fontSize = scaled(0.042f, 14f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
