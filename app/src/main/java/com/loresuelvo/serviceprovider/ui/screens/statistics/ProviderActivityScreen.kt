package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.statistics.ProviderActivity
import com.loresuelvo.serviceprovider.domain.statistics.ActivityGranularity
import com.loresuelvo.serviceprovider.ui.statistics.ActivityFilters
import com.loresuelvo.serviceprovider.ui.statistics.ProviderActivityUiState
import com.loresuelvo.serviceprovider.ui.statistics.ProviderActivityViewModel
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

val ACTIVITY_READING_POSITION = SemanticsPropertyKey<Pair<Int, Int>>("ActivityReadingPosition")

@Composable
fun ProviderActivityRoute(viewModel: ProviderActivityViewModel = hiltViewModel(), onCollections: (() -> Unit)? = null) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val periodExpanded by viewModel.periodExpanded.collectAsStateWithLifecycle()
    val evolutionExpanded by viewModel.evolutionExpanded.collectAsStateWithLifecycle()
    val listState = rememberLazyListState(viewModel.readingIndex, viewModel.readingOffset)
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is ProviderActivityUiState.Ready && !restored) {
            listState.scrollToItem(viewModel.readingIndex, viewModel.readingOffset)
            restored = true
        }
    }
    LaunchedEffect(listState, state, restored) {
        if (state is ProviderActivityUiState.Ready && restored) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    viewModel.rememberReadingPosition(index, offset)
                }
        }
    }
    DisposableEffect(viewModel, listState) {
        onDispose {
            if (restored) viewModel.rememberReadingPosition(
                listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }
    ProviderActivityScreen(state, viewModel::retry, filters, viewModel::editDates, viewModel::applyDates,
        viewModel::selectGranularity, viewModel::comparePrevious, listState,
        periodExpanded, evolutionExpanded, viewModel::expandPeriod, viewModel::expandEvolution, onCollections)
}

@Composable
fun ProviderActivityScreen(state: ProviderActivityUiState, onRetry: () -> Unit,
    filters: ActivityFilters? = null, onEditDates: (String, String) -> Unit = { _, _ -> },
    onApplyDates: () -> Unit = {}, onGranularity: (ActivityGranularity) -> Unit = {},
    onComparison: (Boolean) -> Unit = {}, listState: LazyListState = rememberLazyListState(),
    periodExpansion: Boolean? = null, evolutionExpansion: Boolean? = null,
    onPeriodExpansion: ((Boolean) -> Unit)? = null, onEvolutionExpansion: ((Boolean) -> Unit)? = null, onCollections: (() -> Unit)? = null) {
    var showValues by rememberSaveable { mutableStateOf(false) }
    var periodExpanded by rememberSaveable { mutableStateOf(false) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().statusBarsPadding().testTag("provider_activity").semantics {
        this[ACTIVITY_READING_POSITION] = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
    },
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.activity_title), style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }) }
        item {
            if (onCollections != null) PerformanceTabs(false, {}, onCollections)
            else {
                Text(stringResource(R.string.activity_section), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp)
            }
        }
        if (filters != null && state != ProviderActivityUiState.SessionExpired) {
            item { ActivityPeriodControls(filters, onEditDates, onApplyDates, onGranularity, onComparison,
                periodExpansion ?: periodExpanded) {
                    if (onPeriodExpansion != null) onPeriodExpansion(it) else periodExpanded = it
                } }
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
                val result = activity.results
                item {
                    MetricPair(stringResource(R.string.activity_bookings), result.confirmedBookings,
                        stringResource(R.string.activity_completions), result.reportedCompletions)
                }
                item {
                    MetricPair(stringResource(R.string.activity_paid), result.fullyPaidWorkOrders,
                        stringResource(R.string.activity_clients), result.clientsServed)
                }
                item { Text(stringResource(R.string.activity_client_breakdown, result.newClients, result.returningClients)) }
                item { ValueRow(stringResource(R.string.activity_agreed_value), formatActivityMoney(result.agreedValueCents)) }
                item { ValueRow(stringResource(R.string.activity_average), result.averageValueCents?.let(::formatActivityMoney)
                    ?: stringResource(R.string.activity_unavailable)) }
                item { Text(stringResource(R.string.activity_money_note), style = MaterialTheme.typography.bodySmall) }
                activity.comparison?.let { comparison ->
                    item { ActivityComparisonSection(activity.results, comparison) }
                }
                item { ActivityEvolution(activity.evolution, evolutionExpansion ?: showValues) {
                    if (onEvolutionExpansion != null) onEvolutionExpansion(!(evolutionExpansion ?: showValues))
                    else showValues = !showValues
                } }
                if (evolutionExpansion ?: showValues) {
                    activity.evolution.forEach { bucket ->
                        item(key = "activity_bucket_${bucket.from}") { ActivityEvolutionBucket(bucket) }
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
            Text(stringResource(R.string.activity_effective_period), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.activity_period, formatActivityInstant(activity.period.from),
                formatActivityInstant(activity.period.to)), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.activity_calculated, formatActivityInstant(activity.calculatedAt)),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MetricPair(firstLabel: String, firstValue: Long, secondLabel: String, secondValue: Long) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 328.dp || LocalDensity.current.fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(firstLabel, firstValue, Modifier.fillMaxWidth())
                Metric(secondLabel, secondValue, Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(firstLabel, firstValue, Modifier.weight(1f))
                Metric(secondLabel, secondValue, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: Long, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.semantics(mergeDescendants = true) {}.padding(16.dp)) {
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
