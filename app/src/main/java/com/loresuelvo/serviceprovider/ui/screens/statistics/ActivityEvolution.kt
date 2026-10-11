package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.statistics.ActivityBucket

@Composable
internal fun ActivityEvolution(buckets: List<ActivityBucket>, showValues: Boolean, onToggleValues: () -> Unit) {
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface,
        MaterialTheme.colorScheme.tertiary)
    val expansion = stringResource(if (showValues) R.string.activity_expanded else R.string.activity_collapsed)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val maximum = buckets.maxOfOrNull { maxOf(it.confirmedBookings, it.reportedCompletions, it.fullyPaidWorkOrders) }?.coerceAtLeast(2)?.let { it + it % 2 } ?: 2L
    val patterns = listOf(null, PathEffect.dashPathEffect(floatArrayOf(16f, 10f)),
        PathEffect.dashPathEffect(floatArrayOf(3f, 8f)))
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.activity_evolution), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.activity_evolution_note), style = MaterialTheme.typography.bodySmall)
        val singleInterval = if (buckets.size == 1) stringResource(R.string.activity_evolution_single_interval,
            buckets.single().confirmedBookings, buckets.single().reportedCompletions, buckets.single().fullyPaidWorkOrders) else null
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.height(192.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(maximum.toString(), style = MaterialTheme.typography.labelSmall)
            Text((maximum / 2).toString(), style = MaterialTheme.typography.labelSmall)
            Text("0", style = MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.weight(1f).height(192.dp).semantics {
            if (singleInterval != null) contentDescription = singleInterval
        }) {
            repeat(3) { step ->
                val y = 4.dp.toPx() + step * (size.height - 8.dp.toPx()) / 2
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            repeat(3) { series ->
                val path = Path()
                buckets.forEachIndexed { index, bucket ->
                    val value = when (series) {
                        0 -> bucket.confirmedBookings
                        1 -> bucket.reportedCompletions
                        else -> bucket.fullyPaidWorkOrders
                    }
                    val x = if (buckets.size == 1) size.width / 2 else index * size.width / (buckets.size - 1)
                    val y = size.height - 4.dp.toPx() - (value.toDouble() / maximum * (size.height - 8.dp.toPx())).toFloat()
                    if (buckets.size <= 31) drawCircle(colors[series], radius = 3.dp.toPx(), center = Offset(x, y))
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, colors[series], style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 3.dp.toPx(), pathEffect = patterns[series]))
            }
        }
        }
        if (buckets.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(buckets.first().from.atZone(java.time.ZoneId.of("America/Argentina/Buenos_Aires")).toLocalDate().toString(), style = MaterialTheme.typography.labelSmall)
            Text(buckets.last().to.atZone(java.time.ZoneId.of("America/Argentina/Buenos_Aires")).toLocalDate().toString(), style = MaterialTheme.typography.labelSmall)
        }
        listOf(R.string.activity_bookings, R.string.activity_completions, R.string.activity_paid).forEachIndexed { index, label ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Canvas(Modifier.width(28.dp).height(12.dp)) {
                    drawLine(colors[index], Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                        strokeWidth = 3.dp.toPx(), pathEffect = patterns[index])
                }
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
            }
        }
        TextButton(onClick = onToggleValues, modifier = Modifier.semantics { stateDescription = expansion }) {
            Text(stringResource(R.string.activity_evolution_values))
        }
    }
    }
}

@Composable
internal fun ActivityEvolutionBucket(bucket: ActivityBucket) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = 8.dp)) {
        Text(stringResource(R.string.activity_period, formatActivityInstant(bucket.from), formatActivityInstant(bucket.to)),
            style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.activity_bucket_values, bucket.confirmedBookings,
            bucket.reportedCompletions, bucket.fullyPaidWorkOrders))
    }
}
