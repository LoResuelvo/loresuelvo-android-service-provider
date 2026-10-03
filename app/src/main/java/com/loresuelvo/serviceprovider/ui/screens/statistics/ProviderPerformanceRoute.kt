package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loresuelvo.serviceprovider.ui.statistics.CollectionTransactionsViewModel
import com.loresuelvo.serviceprovider.ui.statistics.CollectionTransactionsUiState
import com.loresuelvo.serviceprovider.ui.statistics.ProviderActivityViewModel
import com.loresuelvo.serviceprovider.ui.statistics.ProviderCollectionsViewModel
import com.loresuelvo.serviceprovider.ui.statistics.ProviderCollectionsUiState

@Composable
fun ProviderPerformanceRoute(activity: ProviderActivityViewModel = hiltViewModel(),
    collections: ProviderCollectionsViewModel = hiltViewModel(),
    transactions: CollectionTransactionsViewModel = hiltViewModel()) {
    var showingCollections by rememberSaveable { mutableStateOf(false) }
    val filters by activity.filters.collectAsStateWithLifecycle()
    val transactionState by transactions.uiState.collectAsStateWithLifecycle()
    val collectionState by collections.uiState.collectAsStateWithLifecycle()
    val periodExpanded by activity.periodExpanded.collectAsStateWithLifecycle()
    LaunchedEffect(showingCollections, filters.query) {
        if (showingCollections) collections.selectQuery(filters.query)
    }
    LaunchedEffect(showingCollections, collectionState) {
        val ready = collectionState as? ProviderCollectionsUiState.Ready
        if (showingCollections && ready != null && collections.query == filters.query) {
            transactions.selectPeriod(ready.collections.period)
        }
    }
    if (showingCollections) {
        val visibleState = if (collections.query == filters.query) collectionState else ProviderCollectionsUiState.Loading
        ProviderCollectionsScreen(visibleState, collections::retry, onActivity = { showingCollections = false },
            filters = filters, onEditDates = activity::editDates, onApplyDates = activity::applyDates,
            onGranularity = activity::selectGranularity, onComparison = activity::comparePrevious,
            periodExpanded = periodExpanded, onPeriodExpansion = activity::expandPeriod,
            transactions = transactionState.let { state ->
                val period = (visibleState as? ProviderCollectionsUiState.Ready)?.collections?.period
                if (state.page != null && (state.page.from != period?.from || state.page.to != period?.to))
                    CollectionTransactionsUiState(purpose = state.purpose, loading = true)
                else state
            }, onPurpose = transactions::selectPurpose,
            onLoadMore = transactions::loadMore, onTransactionsRetry = transactions::retry)
    } else {
        ProviderActivityRoute(activity, onCollections = { showingCollections = true })
    }
}
