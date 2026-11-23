package io.github.aescanero.ftmslab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.aescanero.ftmslab.Comparison
import io.github.aescanero.ftmslab.R
import io.github.aescanero.ftmslab.data.StatParam
import io.github.aescanero.ftmslab.protocol.MachineType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val CYCLE_MS = 6000L

@Composable
private fun paramLabel(param: StatParam): String = stringResource(
    when (param) {
        StatParam.CADENCE -> R.string.metric_cadence
        StatParam.SPEED -> R.string.metric_speed
        StatParam.POWER -> R.string.metric_power
        StatParam.RESISTANCE -> R.string.metric_resistance
        StatParam.HEART_RATE -> R.string.label_heart_rate
    },
)

@Composable
private fun paramValue(param: StatParam, value: Double?, type: MachineType?): String {
    if (value == null) return "—"
    return when (param) {
        StatParam.CADENCE ->
            "${value.roundToInt()} " + if (type == MachineType.INDOOR_BIKE) stringResource(R.string.unit_rpm) else "/min"
        StatParam.SPEED -> "%.1f km/h".format(value)
        StatParam.POWER -> "${value.roundToInt()} W"
        StatParam.RESISTANCE -> "%.1f".format(value)
        StatParam.HEART_RATE -> stringResource(R.string.value_bpm, value.roundToInt())
    }
}

/**
 * Comparativa de un parámetro, solo con medias: la de la sesión (en curso o última) frente a la media de los últimos 7 y 30 días, en barras
 * que parten de cero. Rota sola por los parámetros si el ciclo está activo; un toque pasa al siguiente.
 */
@Composable
internal fun ComparisonCard(
    comparison: Comparison,
    cycle: Boolean,
    onToggleCycle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val params = comparison.params
    var index by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(cycle, params.size) {
        while (cycle && params.size > 1) {
            delay(CYCLE_MS)
            index = (index + 1) % params.size
        }
    }
    val param = params[index % params.size]
    val current = comparison.current[param]
    val monthReference = comparison.month[param]
    val reference = monthReference ?: comparison.week[param]
    val deltaRes = if (monthReference != null) R.string.compare_delta_month else R.string.compare_delta_week
    val nowLabel = stringResource(if (comparison.live) R.string.compare_now else R.string.compare_last_session)
    val rows = listOf(
        nowLabel to current,
        stringResource(R.string.compare_week_avg) to comparison.week[param],
        stringResource(R.string.compare_month_avg) to comparison.month[param],
    )
    val max = rows.mapNotNull { it.second }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outline

    Card(modifier.clickable(enabled = params.size > 1) { index = (index + 1) % params.size }) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(paramLabel(param), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (current != null && reference != null && reference > 0) {
                        val delta = ((current - reference) / reference * 100).roundToInt()
                        Text(
                            stringResource(deltaRes, (if (delta > 0) "+" else "") + delta),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FilterChip(
                    selected = cycle,
                    onClick = { onToggleCycle(!cycle) },
                    label = { Text(stringResource(R.string.compare_cycle)) },
                )
            }
            rows.forEachIndexed { row, (label, value) ->
                val text = paramValue(param, value, comparison.type)
                Row(
                    Modifier.fillMaxWidth().semantics { contentDescription = "$label: $text" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(128.dp), maxLines = 1)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(14.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (value != null) {
                            Box(
                                Modifier
                                    .fillMaxWidth((value / max).toFloat().coerceIn(0.02f, 1f))
                                    .fillMaxHeight()
                                    .background(if (row == 0) accent else muted.copy(alpha = 0.8f)),
                            )
                        }
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (row == 0) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(96.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

