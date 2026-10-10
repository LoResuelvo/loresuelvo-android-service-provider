package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val periodFormat = remember(locale) { DateTimeFormatter.ofPattern("d MMM yyyy", locale) }
    val zone = remember { ZoneId.of("America/Argentina/Buenos_Aires") }
    val comparisonLabel = stringResource(R.string.activity_compare_previous)
    val expansion = stringResource(if (expanded) R.string.activity_expanded else R.string.activity_collapsed)
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onExpansion(!expanded) }, modifier = Modifier.fillMaxWidth()
                .semantics { stateDescription = expansion }) {
                Icon(Icons.Outlined.DateRange, null, Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.activity_period_options), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.ui_period_summary,
                        filters.query.from.atZone(zone).format(periodFormat),
                        filters.query.to.minusNanos(1).atZone(zone).format(periodFormat)),
                        style = MaterialTheme.typography.bodyMedium)
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            if (expanded) {
                Text(stringResource(R.string.activity_dates_hint), style = MaterialTheme.typography.bodySmall)
                PeriodDateField(filters.fromDay, R.string.activity_from_day, "activity_from_day",
                    filters.dateError != null, filters.query.from.atZone(zone).toLocalDate()) {
                    onEditDates(it, filters.throughDay)
                }
                PeriodDateField(filters.throughDay, R.string.activity_through_day, "activity_through_day",
                    filters.dateError != null, filters.query.to.atZone(zone).toLocalDate()) {
                    onEditDates(filters.fromDay, it)
                }
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = filters.query.comparePrevious, onCheckedChange = onComparison,
                            modifier = Modifier.semantics { contentDescription = comparisonLabel })
                        Text(stringResource(R.string.activity_compare_previous), modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodDateField(value: String, label: Int, tag: String, isError: Boolean,
    fallback: LocalDate, onChange: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedTextField(value = value, onValueChange = onChange,
        label = { Text(stringResource(label)) }, singleLine = true, isError = isError,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        trailingIcon = {
            IconButton(onClick = {
                val selected = runCatching { LocalDate.parse(value) }.getOrDefault(fallback)
                android.app.DatePickerDialog(context, { _, year, month, day ->
                    onChange(LocalDate.of(year, month + 1, day).toString())
                }, selected.year, selected.monthValue - 1, selected.dayOfMonth).show()
            }) { Icon(Icons.Outlined.DateRange, stringResource(R.string.ui_choose_date, stringResource(label))) }
        })
}
