package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.ui.Alignment
import com.loresuelvo.serviceprovider.domain.statistics.ConversionPeriod
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.statistics.*

@Composable
internal fun ConversionPeriodControls(filters: ConversionFilters, onEdit: (String, String) -> Unit,
    onApply: () -> Unit, expanded: Boolean, onExpansion: (Boolean) -> Unit, period: ConversionPeriod? = null) {
    val expansion = stringResource(if (expanded) R.string.activity_expanded else R.string.activity_collapsed)
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onExpansion(!expanded) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .testTag("conversion_period_options").semantics { stateDescription = expansion }) {
                Icon(Icons.Outlined.DateRange, null, Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(stringResource(R.string.activity_period_options), style = MaterialTheme.typography.labelLarge)
                    period?.let {
                        val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
                        val format = DateTimeFormatter.ofPattern("d MMM yyyy", locale).withZone(ZoneId.of("America/Argentina/Buenos_Aires"))
                        Text(stringResource(R.string.ui_period_summary, format.format(it.from), format.format(it.to.minusNanos(1))),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            if (expanded) {
                Text(stringResource(R.string.activity_dates_hint), style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr))
                OutlinedTextField(filters.fromDay, { onEdit(it, filters.throughDay) },
                    label = { Text(stringResource(R.string.activity_from_day)) }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.ContentOrLtr),
                    isError = filters.dateError != null, modifier = Modifier.fillMaxWidth().testTag("conversion_from_day"))
                OutlinedTextField(filters.throughDay, { onEdit(filters.fromDay, it) },
                    label = { Text(stringResource(R.string.activity_through_day)) }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.ContentOrLtr),
                    isError = filters.dateError != null, modifier = Modifier.fillMaxWidth().testTag("conversion_through_day"))
                filters.dateError?.let { error ->
                    Text(stringResource(when (error) {
                        ConversionDateError.FORMAT -> R.string.activity_date_format_error
                        ConversionDateError.INCOMPLETE -> R.string.conversion_date_incomplete
                        ConversionDateError.REVERSED -> R.string.activity_date_order_error
                        ConversionDateError.FUTURE -> R.string.activity_date_future_error
                        ConversionDateError.TOO_LONG -> R.string.activity_date_length_error
                    }), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                Button(onClick = onApply, modifier = Modifier.heightIn(min = 48.dp).testTag("conversion_apply_period")) {
                    Text(stringResource(R.string.activity_apply_period))
                }
            }
        }
    }
}
