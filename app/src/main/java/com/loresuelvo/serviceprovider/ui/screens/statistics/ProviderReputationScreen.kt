package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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

@Composable
fun ProviderReputationRoute(viewModel: ProviderReputationViewModel = hiltViewModel(),
    onSection: (PerformanceSection) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.open() }
    ProviderReputationScreen(state, viewModel::retry, onSection)
}

@Composable
fun ProviderReputationScreen(state: ProviderReputationUiState, onRetry: () -> Unit,
    onSection: (PerformanceSection) -> Unit = {}) {
    ProvideTextStyle(TextStyle(textDirection = TextDirection.ContentOrLtr)) {
        LazyColumn(modifier = Modifier.fillMaxSize().statusBarsPadding().testTag("provider_reputation"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(stringResource(R.string.activity_title), style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }) }
            item { PerformanceTabs(PerformanceSection.REPUTATION, onSection) }
            item { Text(stringResource(R.string.reputation_lifetime), color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()) }
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
                    item { ReputationCoverage(reputation) }
                    item { Text(stringResource(R.string.reputation_paid_note), style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr)) }
                    item { Text(stringResource(R.string.reputation_reviews), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }) }
                    if (reputation.reviews.isEmpty()) item { Text(stringResource(R.string.reputation_empty_reviews)) }
                    items(reputation.reviews, key = { it.workOrderId }) { review ->
                        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.reputation_work, review.workOrderId),
                                style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.reputation_review_rating, review.rating))
                            if (review.description.isNotEmpty()) Text(review.description)
                            HorizontalDivider()
                        }
                    }
                    item { Text(stringResource(R.string.reputation_order_note), style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr)) }
                    item { Text(stringResource(R.string.reputation_calculated, formatActivityInstant(reputation.calculatedAt)),
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr)) }
                }
            }
        }
    }
}

@Composable
private fun ReputationSummary(reputation: ProviderReputation) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(reputation.averageRating?.let { stringResource(R.string.reputation_average, formatReputationNumber(it)) }
            ?: stringResource(R.string.reputation_no_rating), style = MaterialTheme.typography.headlineLarge.copy(textDirection = TextDirection.ContentOrLtr))
        Text(stringResource(R.string.reputation_sample, reputation.reviewCount))
    }
}

@Composable
private fun RatingBucket(bucket: RatingCount, total: Long) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.reputation_bucket, bucket.rating, bucket.count))
        LinearProgressIndicator(progress = { if (total == 0L) 0f else (bucket.count.toDouble() / total).toFloat() },
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {})
    }
}

@Composable
private fun ReputationCoverage(reputation: ProviderReputation) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
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
