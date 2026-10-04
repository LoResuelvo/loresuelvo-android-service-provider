package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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
    onApply: () -> Unit, expanded: Boolean, onExpansion: (Boolean) -> Unit) {
    val expansion = stringResource(if (expanded) R.string.activity_expanded else R.string.activity_collapsed)
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onExpansion(!expanded) }, modifier = Modifier.heightIn(min = 48.dp)
                .testTag("conversion_period_options").semantics { stateDescription = expansion }) {
                Text(stringResource(R.string.activity_period_options))
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
