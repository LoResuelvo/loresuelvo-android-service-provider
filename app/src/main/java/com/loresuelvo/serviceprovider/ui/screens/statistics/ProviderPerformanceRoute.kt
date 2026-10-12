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
import com.loresuelvo.serviceprovider.ui.statistics.ProviderReputationViewModel

// This route coordinates the shared period and tab lifetime; it owns no repository or money rules.
// Reading metadata remains owned by the existing Activity and Collections ViewModels.
@Composable
fun ProviderPerformanceRoute(activity: ProviderActivityViewModel = hiltViewModel(),
    collections: ProviderCollectionsViewModel = hiltViewModel(),
    transactions: CollectionTransactionsViewModel = hiltViewModel(),
    reputation: ProviderReputationViewModel = hiltViewModel(),
    onWorkOrder: (Int) -> Unit = {}, onConversation: (Int) -> Unit = {}) {
    var selectedSection by rememberSaveable { mutableStateOf(PerformanceSection.ACTIVITY) }
    var showingConversion by rememberSaveable { mutableStateOf(false) }
    var selectedProposal by rememberSaveable { mutableStateOf<Int?>(null) }
    androidx.activity.compose.BackHandler(selectedSection == PerformanceSection.ACTIVITY && showingConversion) {
        showingConversion = false
    }
    val showingCollections = selectedSection == PerformanceSection.COLLECTIONS
    val filters by activity.filters.collectAsStateWithLifecycle()
    val transactionState by transactions.uiState.collectAsStateWithLifecycle()
    val collectionState by collections.uiState.collectAsStateWithLifecycle()
    val evolutionExpanded by collections.evolutionExpanded.collectAsStateWithLifecycle()
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
    var readingFilter by remember { mutableStateOf(Pair(filters.query.from, filters.query.to)) }
    LaunchedEffect(filters.query.from, filters.query.to) {
        val selected = Pair(filters.query.from, filters.query.to)
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
    if (selectedSection == PerformanceSection.REPUTATION) {
        ProviderReputationRoute(reputation, onSection = { selectedSection = it }, onWorkOrder = onWorkOrder)
    } else if (showingCollections) {
        val visibleState = if (collections.query == filters.query) collectionState else ProviderCollectionsUiState.Loading
        ProviderCollectionsScreen(visibleState, { collections.retry(); transactions.refresh() }, onActivity = { selectedSection = PerformanceSection.ACTIVITY },
            onReputation = { selectedSection = PerformanceSection.REPUTATION },
            filters = filters, onEditDates = activity::editDates, onApplyDates = activity::applyDates,
            onGranularity = activity::selectGranularity, onComparison = activity::comparePrevious,
            periodExpanded = periodExpanded, onPeriodExpansion = activity::expandPeriod,
            transactions = transactionState.let { state ->
                val period = (visibleState as? ProviderCollectionsUiState.Ready)?.collections?.period
                if (state.page != null && (state.page.from != period?.from || state.page.to != period?.to))
                    CollectionTransactionsUiState(purpose = state.purpose, loading = true)
                else state
            }, onPurpose = transactions::selectPurpose,
            onLoadMore = transactions::loadMore, onTransactionsRetry = transactions::retry, listState = collectionList,
            evolutionExpansion = evolutionExpanded, onEvolutionExpansion = collections::expandEvolution,
            onWorkOrder = onWorkOrder, onProposal = { selectedProposal = it })
    } else if (showingConversion) {
        ProviderConversionRoute(onBack = { showingConversion = false }, onSection = { selectedSection = it })
    } else {
        ProviderActivityRoute(activity, onCollections = { selectedSection = PerformanceSection.COLLECTIONS },
            onReputation = { selectedSection = PerformanceSection.REPUTATION }, onConversion = { showingConversion = true })
    }
    selectedProposal?.let { id ->
        com.loresuelvo.serviceprovider.ui.screens.proposals.CollectionProposalSheet(id,
            onDismiss = { selectedProposal = null }, onConversation = { conversationId ->
                selectedProposal = null
                onConversation(conversationId)
            })
    }
}
