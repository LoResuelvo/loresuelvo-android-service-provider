package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loresuelvo.serviceprovider.ui.statistics.ProviderActivityViewModel
import com.loresuelvo.serviceprovider.ui.statistics.ProviderCollectionsViewModel
import com.loresuelvo.serviceprovider.ui.statistics.ProviderCollectionsUiState

@Composable
fun ProviderPerformanceRoute(activity: ProviderActivityViewModel = hiltViewModel(),
    collections: ProviderCollectionsViewModel = hiltViewModel()) {
    var showingCollections by rememberSaveable { mutableStateOf(false) }
    val filters by activity.filters.collectAsStateWithLifecycle()
    val collectionState by collections.uiState.collectAsStateWithLifecycle()
    val periodExpanded by activity.periodExpanded.collectAsStateWithLifecycle()
    LaunchedEffect(showingCollections, filters.query) {
        if (showingCollections) collections.selectQuery(filters.query)
    }
    if (showingCollections) {
        val visibleState = if (collections.query == filters.query) collectionState else ProviderCollectionsUiState.Loading
        ProviderCollectionsScreen(visibleState, collections::retry, onActivity = { showingCollections = false },
            filters = filters, onEditDates = activity::editDates, onApplyDates = activity::applyDates,
            onGranularity = activity::selectGranularity, onComparison = activity::comparePrevious,
            periodExpanded = periodExpanded, onPeriodExpansion = activity::expandPeriod)
    } else {
        ProviderActivityRoute(activity, onCollections = { showingCollections = true })
    }
}
