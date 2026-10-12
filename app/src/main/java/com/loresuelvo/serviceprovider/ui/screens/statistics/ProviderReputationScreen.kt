package com.loresuelvo.serviceprovider.ui.screens.statistics

import com.loresuelvo.serviceprovider.ui.components.RefreshableContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.statistics.ProviderReputation
import com.loresuelvo.serviceprovider.domain.statistics.RatingCount
import com.loresuelvo.serviceprovider.ui.statistics.ProviderReputationUiState
import com.loresuelvo.serviceprovider.ui.statistics.ProviderReputationViewModel
import java.text.NumberFormat
import java.util.Locale

val REPUTATION_READING_POSITION = SemanticsPropertyKey<Pair<Int, Int>>("ReputationReadingPosition")

@Composable
fun ProviderReputationRoute(viewModel: ProviderReputationViewModel = hiltViewModel(),
    onSection: (PerformanceSection) -> Unit = {}, onWorkOrder: (Int) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.open() }
    val list = rememberLazyListState(viewModel.readingIndex, viewModel.readingOffset)
    var restored by remember { mutableStateOf(false) }
    var restoredVersion by remember { mutableStateOf<Long?>(null) }
    val ready = state as? ProviderReputationUiState.Ready
    LaunchedEffect(ready?.restoring, ready?.readingVersion) {
        if (ready != null && !ready.restoring) {
            list.scrollToItem(viewModel.readingIndex, viewModel.readingOffset)
            restoredVersion = ready.readingVersion
            restored = true
        }
    }
    LaunchedEffect(list, restored) {
        if (restored) snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val current = viewModel.uiState.value as? ProviderReputationUiState.Ready
                if (current != null && !current.restoring && current.readingVersion == restoredVersion)
                    viewModel.rememberReadingPosition(index, offset)
            }
    }
    DisposableEffect(list) {
        onDispose {
            val current = viewModel.uiState.value as? ProviderReputationUiState.Ready
            if (restored && current != null && !current.restoring && current.readingVersion == restoredVersion)
                viewModel.rememberReadingPosition(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
        }
    }
    ProviderReputationScreen(state, viewModel::retry, onSection, viewModel::loadMore, viewModel::refresh, list, onWorkOrder)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProviderReputationScreen(state: ProviderReputationUiState, onRetry: () -> Unit,
    onSection: (PerformanceSection) -> Unit = {}, onLoadMore: () -> Unit = {},
    onRefresh: () -> Unit = {}, listState: LazyListState = rememberLazyListState(),
    onWorkOrder: (Int) -> Unit = {}) {
    RefreshableContent((state as? ProviderReputationUiState.Ready)?.loading == true, onRefresh) {
        ProvideTextStyle(TextStyle(textDirection = TextDirection.ContentOrLtr)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().statusBarsPadding().testTag("provider_reputation")
                .semantics { this[REPUTATION_READING_POSITION] = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset },
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { PerformanceHeader(stringResource(R.string.reputation_lifetime)) }
                stickyHeader { Surface(color = MaterialTheme.colorScheme.background) {
                    PerformanceTabs(PerformanceSection.REPUTATION, onSection)
                } }
                when (state) {
                    ProviderReputationUiState.Loading -> item {
                        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.reputation_loading))
                        }
                    }
                    ProviderReputationUiState.SessionExpired -> item { Text(stringResource(R.string.reputation_session_expired)) }
                    is ProviderReputationUiState.Error -> item {
                        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.reputation_error))
                            Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.activity_retry))
                            }
                        }
                    }
                    is ProviderReputationUiState.Ready -> {
                        val reputation = state.reputation
                        item { ReputationSummary(reputation) }
                        item {
                            Column {
                                Text(stringResource(R.string.reputation_distribution), style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.semantics { heading() })
                                reputation.ratingDistribution.asReversed().forEach { RatingBucket(it, reputation.reviewCount) }
                            }
                        }
                        item { Text(stringResource(R.string.reputation_paid_note), style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr)) }
                        item { Text(stringResource(R.string.reputation_reviews), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() }) }
                        if (reputation.reviews.isEmpty()) item { Text(stringResource(R.string.reputation_empty_reviews)) }
                        items(reputation.reviews, key = { it.workOrderId }) { review ->
                            Surface(Modifier.fillMaxWidth().testTag("reputation_review_${review.workOrderId}"),
                                shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ReviewStars(review.rating)
                                    if (review.description.isNotEmpty()) Text(review.description)
                                    TextButton(onClick = { onWorkOrder(review.workOrderId) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text(stringResource(R.string.performance_view_work))
                                    }
                                }
                            }
                        }
                        item {
                            Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (state.loading) {
                                    CircularProgressIndicator()
                                    Text(stringResource(R.string.reputation_loading))
                                } else if (state.failure != null) {
                                    Text(stringResource(if (state.restartRequired) R.string.reputation_restart_error else R.string.reputation_more_error))
                                    Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text(stringResource(if (state.restartRequired) R.string.reputation_restart else R.string.activity_retry))
                                    }
                                } else if (reputation.nextCursor != null) {
                                    Button(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                        Text(stringResource(R.string.reputation_load_more))
                                    }
                                }
                            }
                        }
                        item { ReputationCoverage(reputation) }
                        item { Text(stringResource(R.string.reputation_order_note), style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr)) }
                        item { Text(stringResource(R.string.reputation_calculated, formatActivityInstant(reputation.calculatedAt)),
                            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReputationSummary(reputation: ProviderReputation) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.StarOutline, null)
        Text(reputation.averageRating?.let { stringResource(R.string.reputation_average, formatReputationNumber(it)) }
            ?: stringResource(R.string.reputation_no_rating), style = MaterialTheme.typography.headlineLarge.copy(textDirection = TextDirection.ContentOrLtr))
        Text(stringResource(R.string.reputation_sample, reputation.reviewCount))
    }
    }
}

@Composable
private fun RatingBucket(bucket: RatingCount, total: Long) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.reputation_bucket, bucket.rating, bucket.count))
        LinearProgressIndicator(progress = { if (total == 0L) 0f else (bucket.count.toDouble() / total).toFloat() },
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun ReputationCoverage(reputation: ProviderReputation) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp).semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reputation_coverage), style = MaterialTheme.typography.titleMedium)
            Text(reputation.coveragePercentage?.let { stringResource(R.string.reputation_percentage, formatReputationNumber(it)) }
                ?: stringResource(R.string.activity_unavailable), style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.ContentOrLtr))
            Text(stringResource(R.string.reputation_coverage_counts, reputation.reviewedPaidOrders, reputation.eligiblePaidOrders))
        }
    }
}

internal fun formatReputationNumber(value: Double): String = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-AR"))
    .apply { maximumFractionDigits = 2; roundingMode = java.math.RoundingMode.HALF_UP }.format(value)

@Composable
internal fun ReviewStars(rating: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { index ->
                Icon(if (index < rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
            }
        }
        Text(stringResource(R.string.reputation_review_rating, rating), style = MaterialTheme.typography.labelMedium)
    }
}
