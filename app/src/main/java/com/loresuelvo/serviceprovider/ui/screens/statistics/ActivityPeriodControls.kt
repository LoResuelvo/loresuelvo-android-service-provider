package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.statistics.ActivityGranularity
import com.loresuelvo.serviceprovider.ui.statistics.ActivityDateError
import com.loresuelvo.serviceprovider.ui.statistics.ActivityFilters

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActivityPeriodControls(filters: ActivityFilters, onEditDates: (String, String) -> Unit,
    onApplyDates: () -> Unit, onGranularity: (ActivityGranularity) -> Unit, onComparison: (Boolean) -> Unit,
    expanded: Boolean, showEvolutionOptions: Boolean = true, onExpansion: (Boolean) -> Unit) {
    val comparisonLabel = stringResource(R.string.activity_compare_previous)
    val expansion = stringResource(if (expanded) R.string.activity_expanded else R.string.activity_collapsed)
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onExpansion(!expanded) }, modifier = Modifier.semantics { stateDescription = expansion }) {
                Text(stringResource(R.string.activity_period_options))
            }
            if (expanded) {
                Text(stringResource(R.string.activity_dates_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = filters.fromDay, onValueChange = { onEditDates(it, filters.throughDay) },
                    label = { Text(stringResource(R.string.activity_from_day)) }, singleLine = true,
                    isError = filters.dateError != null, modifier = Modifier.fillMaxWidth().testTag("activity_from_day"))
                OutlinedTextField(value = filters.throughDay, onValueChange = { onEditDates(filters.fromDay, it) },
                    label = { Text(stringResource(R.string.activity_through_day)) }, singleLine = true,
                    isError = filters.dateError != null, modifier = Modifier.fillMaxWidth().testTag("activity_through_day"))
                filters.dateError?.let {
                    val message = when (it) {
                        ActivityDateError.FORMAT -> R.string.activity_date_format_error
                        ActivityDateError.REVERSED -> R.string.activity_date_order_error
                        ActivityDateError.TOO_LONG -> R.string.activity_date_length_error
                        ActivityDateError.FUTURE -> R.string.activity_date_future_error
                    }
                    Text(stringResource(message), color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = onApplyDates) { Text(stringResource(R.string.activity_apply_period)) }
                if (showEvolutionOptions) {
                    Text(stringResource(R.string.activity_group_by))
                    // Wrapping controls retain their labels at large font sizes and narrow widths.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActivityGranularity.entries.forEach { granularity ->
                            FilterChip(selected = filters.query.granularity == granularity,
                                onClick = { onGranularity(granularity) }, label = {
                                    Text(stringResource(when (granularity) {
                                        ActivityGranularity.DAY -> R.string.activity_day
                                        ActivityGranularity.WEEK -> R.string.activity_week
                                        ActivityGranularity.MONTH -> R.string.activity_month
                                    }))
                                }, modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(checked = filters.query.comparePrevious, onCheckedChange = onComparison,
                            modifier = Modifier.semantics { contentDescription = comparisonLabel })
                        Text(stringResource(R.string.activity_compare_previous), modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
