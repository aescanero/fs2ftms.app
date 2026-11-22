package io.github.aescanero.ftmslab.ui

import androidx.annotation.StringRes
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.aescanero.ftmslab.R
import io.github.aescanero.ftmslab.protocol.HeartRateZone
import io.github.aescanero.ftmslab.protocol.HeartRateZones
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

/** Nombre localizado de cada zona; el `label` del enum es solo para registros. */
@StringRes
internal fun zoneLabelRes(zone: HeartRateZone): Int = when (zone) {
    HeartRateZone.RECOVERY -> R.string.zone_recovery
    HeartRateZone.LIGHT_AEROBIC -> R.string.zone_light_aerobic
    HeartRateZone.AEROBIC -> R.string.zone_aerobic
    HeartRateZone.ANAEROBIC -> R.string.zone_anaerobic
    HeartRateZone.MAXIMUM -> R.string.zone_maximum
}

private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f

/**
 * Indicador circular del pulso: arco continuo con degradado por zonas, la zona actual resaltada y una
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
        bpm == null -> stringResource(R.string.gauge_no_pulse)
        current == null -> stringResource(R.string.gauge_desc_below, bpm)
        else -> stringResource(R.string.gauge_desc_zone, bpm, current.number, stringResource(zoneLabelRes(current)))
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
            // Arco continuo con degradado entre zonas; encima, la zona actual en color pleno y más gruesa.
            val circle = 360f
            val stops = buildList {
                add(0f to zoneColor(HeartRateZone.entries.first(), dark))
                HeartRateZone.entries.forEachIndexed { index, zone ->
                    add((index + 0.5f) * perZone / circle to zoneColor(zone, dark))
                }
                add(SWEEP_ANGLE / circle to zoneColor(HeartRateZone.entries.last(), dark))
                add(1f to zoneColor(HeartRateZone.entries.last(), dark))
            }.toTypedArray()
            rotate(START_ANGLE) {
                drawArc(
                    brush = Brush.sweepGradient(*stops, center = center),
                    startAngle = 0f,
                    sweepAngle = SWEEP_ANGLE,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    alpha = if (bpm == null) 0.85f else 0.55f,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                if (current != null) {
                    drawArc(
                        color = zoneColor(current, dark),
                        startAngle = current.ordinal * perZone,
                        sweepAngle = perZone,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke * 1.6f, cap = StrokeCap.Butt),
                    )
                }
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
            Text(stringResource(R.string.unit_bpm), fontSize = scaled(0.055f, 14f))
            val label = when {
                bpm == null -> stringResource(R.string.gauge_no_pulse)
                current == null -> stringResource(R.string.zone_below)
                else -> stringResource(R.string.gauge_zone, current.number, stringResource(zoneLabelRes(current)))
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
                    stringResource(R.string.gauge_stale, staleSeconds),
                    fontSize = scaled(0.042f, 14f),
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (bpm != null) {
                Text(
                    stringResource(R.string.gauge_percent, bpm * 100 / maxHeartRate, maxHeartRate),
                    fontSize = scaled(0.042f, 14f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
