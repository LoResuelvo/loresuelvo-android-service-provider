package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.statistics.ProviderActivity
import com.loresuelvo.serviceprovider.ui.statistics.ProviderActivityUiState
import com.loresuelvo.serviceprovider.ui.statistics.ProviderActivityViewModel
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

@Composable
fun ProviderActivityRoute(viewModel: ProviderActivityViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProviderActivityScreen(state, viewModel::retry)
}

@Composable
fun ProviderActivityScreen(state: ProviderActivityUiState, onRetry: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.activity_title), style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }) }
        item {
            Text(stringResource(R.string.activity_section), color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp)
        }
        when (state) {
            ProviderActivityUiState.Loading -> item {
                CircularProgressIndicator()
                Text(stringResource(R.string.activity_loading))
            }
            is ProviderActivityUiState.Error -> item {
                Text(stringResource(R.string.activity_error))
                Button(onClick = onRetry) { Text(stringResource(R.string.activity_retry)) }
            }
            ProviderActivityUiState.SessionExpired -> item { Text(stringResource(R.string.activity_session_expired)) }
            is ProviderActivityUiState.Ready -> {
                val activity = state.activity
                item { Period(activity) }
                item { Text(stringResource(R.string.activity_results), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }) }
                if (activity.results.confirmedBookings == 0L && activity.results.reportedCompletions == 0L &&
                    activity.results.fullyPaidWorkOrders == 0L) {
                    item { Text(stringResource(R.string.activity_empty)) }
                }
                item {
                    val result = activity.results
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Metric(stringResource(R.string.activity_bookings), result.confirmedBookings, Modifier.weight(1f))
                            Metric(stringResource(R.string.activity_completions), result.reportedCompletions, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Metric(stringResource(R.string.activity_paid), result.fullyPaidWorkOrders, Modifier.weight(1f))
                            Metric(stringResource(R.string.activity_clients), result.clientsServed, Modifier.weight(1f))
                        }
                        Text(stringResource(R.string.activity_client_breakdown, result.newClients, result.returningClients))
                        ValueRow(stringResource(R.string.activity_agreed_value), formatActivityMoney(result.agreedValueCents))
                        ValueRow(stringResource(R.string.activity_average), result.averageValueCents?.let(::formatActivityMoney)
                            ?: stringResource(R.string.activity_unavailable))
                        Text(stringResource(R.string.activity_money_note), style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    HorizontalDivider()
                    Text(stringResource(R.string.activity_pending), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.activity_pending_note), style = MaterialTheme.typography.bodySmall)
                    ValueRow(stringResource(R.string.activity_pending_requests), activity.currentPending.requests.toString())
                    ValueRow(stringResource(R.string.activity_scheduled), activity.currentPending.scheduledOrders.toString())
                    ValueRow(stringResource(R.string.activity_awaiting_payment), activity.currentPending.awaitingPaymentOrders.toString())
                }
            }
        }
    }
}

@Composable
private fun Period(activity: ProviderActivity) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.activity_last_30_days), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.activity_period, formatActivityInstant(activity.period.from),
                formatActivityInstant(activity.period.to)), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.activity_calculated, formatActivityInstant(activity.calculatedAt)),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Metric(label: String, value: Long, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

internal fun formatActivityMoney(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR")).apply {
    currency = Currency.getInstance("ARS")
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}.format(BigDecimal.valueOf(cents, 2))

internal fun formatActivityInstant(instant: Instant): String = DateTimeFormatter
    .ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("es-AR"))
    .withZone(ZoneId.of("America/Argentina/Buenos_Aires")).format(instant)
