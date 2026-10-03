package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loresuelvo.serviceprovider.ui.statistics.CollectionTransactionsViewModel
import com.loresuelvo.serviceprovider.ui.statistics.CollectionTransactionsUiState
import com.loresuelvo.serviceprovider.ui.statistics.ProviderActivityViewModel
import com.loresuelvo.serviceprovider.ui.statistics.ProviderCollectionsViewModel
import com.loresuelvo.serviceprovider.ui.statistics.ProviderCollectionsUiState

// This route coordinates the shared period and tab lifetime; it owns no repository or money rules.
// Its next extraction seam is reading-state restoration if another performance tab is introduced.
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
        if (showingCollections) {
            collections.selectQuery(filters.query)
            transactions.invalidatePeriod(filters.query)
        }
    }
    LaunchedEffect(showingCollections, collectionState) {
        val ready = collectionState as? ProviderCollectionsUiState.Ready
        if (showingCollections && ready != null && collections.query == filters.query) {
            transactions.selectPeriod(ready.collections.period)
        }
    }
    val collectionList = rememberLazyListState(transactions.readingIndex, transactions.readingOffset)
    var restored by remember { mutableStateOf(false) }
    var readingFilter by remember { mutableStateOf(Triple(filters.query.from, filters.query.to, transactionState.purpose)) }
    LaunchedEffect(filters.query.from, filters.query.to, transactionState.purpose) {
        val selected = Triple(filters.query.from, filters.query.to, transactionState.purpose)
        if (selected != readingFilter) {
            readingFilter = selected
            collectionList.scrollToItem(0)
            transactions.rememberReadingPosition(0, 0)
        }
    }
    LaunchedEffect(showingCollections, collectionState, transactionState.page) {
        if (showingCollections && collectionState is ProviderCollectionsUiState.Ready && transactionState.page != null && !restored) {
            collectionList.scrollToItem(transactions.readingIndex, transactions.readingOffset)
            restored = true
        }
    }
    LaunchedEffect(collectionList, restored) {
        if (restored) snapshotFlow { collectionList.firstVisibleItemIndex to collectionList.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> transactions.rememberReadingPosition(index, offset) }
    }
    DisposableEffect(collectionList) {
        onDispose {
            if (restored) transactions.rememberReadingPosition(collectionList.firstVisibleItemIndex,
                collectionList.firstVisibleItemScrollOffset)
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
            onLoadMore = transactions::loadMore, onTransactionsRetry = transactions::retry, listState = collectionList)
    } else {
        ProviderActivityRoute(activity, onCollections = { showingCollections = true })
    }
}
