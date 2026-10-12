package com.loresuelvo.serviceprovider.ui.screens.statistics

import com.loresuelvo.serviceprovider.ui.components.RefreshableContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.statistics.ActivityGranularity
import com.loresuelvo.serviceprovider.domain.statistics.PendingCollectionBalance
import com.loresuelvo.serviceprovider.domain.statistics.CollectionPurpose
import com.loresuelvo.serviceprovider.ui.statistics.CollectionTransactionsUiState
import com.loresuelvo.serviceprovider.ui.statistics.ActivityFilters
import com.loresuelvo.serviceprovider.ui.statistics.ProviderCollectionsUiState

val COLLECTIONS_READING_POSITION = SemanticsPropertyKey<Pair<Int, Int>>("CollectionsReadingPosition")

// Explicit callbacks keep the shared Activity period controls and independent transaction events visible.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProviderCollectionsScreen(state: ProviderCollectionsUiState, onRetry: () -> Unit,
    onActivity: () -> Unit = {}, onReputation: () -> Unit = {}, filters: ActivityFilters? = null,
    onEditDates: (String, String) -> Unit = { _, _ -> }, onApplyDates: () -> Unit = {},
    onGranularity: (ActivityGranularity) -> Unit = {}, onComparison: (Boolean) -> Unit = {},
    periodExpanded: Boolean = false, onPeriodExpansion: (Boolean) -> Unit = {},
    transactions: CollectionTransactionsUiState =
        CollectionTransactionsUiState(),
    onPurpose: (CollectionPurpose?) -> Unit = {},
    onLoadMore: () -> Unit = {}, onTransactionsRetry: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(), evolutionExpansion: Boolean? = null,
    onEvolutionExpansion: ((Boolean) -> Unit)? = null,
    onWorkOrder: (Int) -> Unit = {}, onProposal: (Int) -> Unit = {}) {
    var localExpansion by rememberSaveable { mutableStateOf(false) }
    val evolutionExpanded = evolutionExpansion ?: localExpansion
    RefreshableContent((state as? ProviderCollectionsUiState.Ready)?.refreshing == true || transactions.refreshing, onRetry) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().statusBarsPadding().testTag("provider_collections").semantics {
            this[COLLECTIONS_READING_POSITION] = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        },
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { PerformanceHeader(stringResource(R.string.performance_collections_hint)) }
            stickyHeader { PerformanceTabs(PerformanceSection.COLLECTIONS) { section ->
                when (section) {
                    PerformanceSection.ACTIVITY -> onActivity()
                    PerformanceSection.REPUTATION -> onReputation()
                    PerformanceSection.COLLECTIONS -> Unit
                }
            } }
            if (filters != null && state != ProviderCollectionsUiState.SessionExpired) {
                item { ActivityPeriodControls(filters, onEditDates, onApplyDates, onGranularity, onComparison,
                    periodExpanded, showEvolutionOptions = true, onExpansion = onPeriodExpansion) }
            }
            when (state) {
                ProviderCollectionsUiState.Loading -> item {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.collections_loading))
                }
                is ProviderCollectionsUiState.Error -> item {
                    Text(stringResource(R.string.collections_error))
                    Button(onClick = onRetry) { Text(stringResource(R.string.activity_retry)) }
                }
                ProviderCollectionsUiState.SessionExpired -> item { Text(stringResource(R.string.collections_session_expired)) }
                is ProviderCollectionsUiState.Ready -> {
                    if (state.refreshFailed) item { Text(stringResource(R.string.ui_refresh_failed), color = MaterialTheme.colorScheme.error) }
                    val collections = state.collections
                    item { CollectionMoney(stringResource(R.string.collections_verified_total), collections.results.totalCents, highlighted = true) }
                    item { Text(stringResource(R.string.collections_money_note), style = MaterialTheme.typography.bodySmall) }
                    if (collections.results.totalCents == 0L) {
                        item { Text(stringResource(R.string.collections_empty)) }
                    }
                    item {
                        PerformanceCard {
                            CollectionAmount(stringResource(R.string.collections_deposits), collections.results.bookingDepositCents)
                            HorizontalDivider()
                            CollectionAmount(stringResource(R.string.collections_balances), collections.results.serviceBalanceCents)
                        }
                    }
                    item {
                        PerformanceCard(attention = collections.currentPending.scheduled.orders > 0 || collections.currentPending.awaitingPayment.orders > 0) {
                            Text(stringResource(R.string.activity_pending), style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() })
                            Text(stringResource(R.string.collections_pending_note), style = MaterialTheme.typography.bodySmall)
                            PendingBalance(stringResource(R.string.activity_scheduled), collections.currentPending.scheduled)
                            HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
                            PendingBalance(stringResource(R.string.activity_awaiting_payment), collections.currentPending.awaitingPayment)
                        }
                    }
                    collectionTransactionsItems(transactions, onPurpose, onLoadMore, onTransactionsRetry, onWorkOrder, onProposal)
                    collections.comparison?.let { comparison ->
                        item { CollectionComparisonSection(collections.results, comparison) }
                    }
                    item(key = "collection_evolution") { CollectionEvolutionHeading(evolutionExpanded) {
                        if (onEvolutionExpansion != null) onEvolutionExpansion(it) else localExpansion = it
                    } }
                    if (evolutionExpanded) collections.evolution.forEach { bucket ->
                        item(key = "collection_bucket_${bucket.from}") { CollectionEvolutionBucket(bucket) }
                    }
                    item {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(stringResource(R.string.activity_effective_period), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.activity_period, formatActivityInstant(collections.period.from),
                                    formatActivityInstant(collections.period.to)), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.activity_calculated, formatActivityInstant(collections.calculatedAt)),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                }
            }
        }
    }
}

@Composable
private fun CollectionMoney(label: String, cents: Long, highlighted: Boolean = false) {
    Surface(shape = MaterialTheme.shapes.medium,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (highlighted) Icon(Icons.Outlined.AccountBalanceWallet, null)
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(formatActivityMoney(cents), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun PendingBalance(label: String, pending: PendingCollectionBalance) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.collections_pending_orders, pending.orders))
        Text(formatActivityMoney(pending.amountCents), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun CollectionAmount(label: String, cents: Long) {
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(formatActivityMoney(cents), style = MaterialTheme.typography.titleLarge)
    }
}
