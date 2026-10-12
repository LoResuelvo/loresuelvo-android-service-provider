package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

internal data class EvolutionPoint(val from: Instant, val to: Instant, val values: List<Long>)

// Both charts share scale, interval selection and exact accessible values.
@Composable
internal fun EvolutionChart(title: Int, points: List<EvolutionPoint>, labels: List<Int>,
    money: Boolean, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.tertiary)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val selectionColor = MaterialTheme.colorScheme.outline
    val selected = selectedIndex.coerceIn(0, (points.size - 1).coerceAtLeast(0))
    val maximum = (points.maxOfOrNull { it.values.maxOrNull() ?: 0 } ?: 0).coerceAtLeast(if (money) 100 else 2)
    val locale = LocalConfiguration.current.locales[0]
    val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = if (money) 2 else 0 }
    val date = DateTimeFormatter.ofPattern("d MMM yyyy", locale).withZone(ZoneId.of("America/Argentina/Buenos_Aires"))
    fun value(amount: Long) = if (money) formatActivityMoney(amount) else number.format(amount)
    val patterns = listOf(null, PathEffect.dashPathEffect(floatArrayOf(16f, 10f)), PathEffect.dashPathEffect(floatArrayOf(3f, 8f)))
    PerformanceCard {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (points.isEmpty()) {
            Text(stringResource(R.string.performance_evolution_empty))
        } else {
            Text(stringResource(R.string.performance_evolution_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.height(144.dp).widthIn(max = 80.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(if (money) number.format(BigDecimal.valueOf(maximum, 2)) else number.format(maximum),
                        style = MaterialTheme.typography.labelSmall)
                    Text("0", style = MaterialTheme.typography.labelSmall)
                }
                Canvas(Modifier.weight(1f).height(144.dp).testTag("evolution_plot")
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            onSelect(if (points.size == 1) 0 else
                                (offset.x / size.width * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex))
                        }
                    }.clearAndSetSemantics {}) {
                    val inset = 4.dp.toPx()
                    repeat(3) { step ->
                        val y = inset + step * (size.height - 2 * inset) / 2
                        drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                    fun x(index: Int) = if (points.size == 1) size.width / 2 else index * size.width / (points.size - 1)
                    drawLine(selectionColor, Offset(x(selected), 0f), Offset(x(selected), size.height), strokeWidth = 1.dp.toPx())
                    labels.indices.forEach { series ->
                        val path = Path()
                        points.forEachIndexed { index, point ->
                            val y = size.height - inset - (point.values[series].toDouble() / maximum * (size.height - 2 * inset)).toFloat()
                            if (index == 0) path.moveTo(x(index), y) else path.lineTo(x(index), y)
                            if (points.size <= 31 || index == selected) drawCircle(colors[series],
                                radius = if (index == selected) 4.dp.toPx() else 2.dp.toPx(), center = Offset(x(index), y))
                        }
                        drawPath(path, colors[series], style = Stroke(width = 2.dp.toPx(), pathEffect = patterns[series]))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(date.format(points.first().from), style = MaterialTheme.typography.labelSmall)
                Text(date.format(points.last().to.minusNanos(1)), style = MaterialTheme.typography.labelSmall)
            }
            HorizontalDivider()
            val point = points[selected]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSelect(selected - 1) }, enabled = selected > 0) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.performance_previous_interval))
                }
                val from = date.format(point.from)
                val through = date.format(point.to.minusNanos(1))
                Text(if (from == through) from else stringResource(R.string.ui_period_summary, from, through),
                    modifier = Modifier.weight(1f).testTag("evolution_interval"), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { onSelect(selected + 1) }, enabled = selected < points.lastIndex) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.performance_next_interval))
                }
            }
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { index, label ->
                    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Canvas(Modifier.width(24.dp).height(12.dp)) {
                            drawLine(colors[index], Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                                strokeWidth = 2.dp.toPx(), pathEffect = patterns[index])
                        }
                        Column(Modifier.weight(1f)) {
                            Text(value(point.values[index]), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(label), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
