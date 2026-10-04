package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.ui.statistics.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProviderConversionRoute(viewModel: ProviderConversionViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val expanded by viewModel.advancesExpanded.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.open() }
    ProviderConversionScreen(state, expanded, viewModel::expandAdvances, viewModel::retry, onBack)
}

@Composable
fun ProviderConversionScreen(state: ProviderConversionUiState, advancesExpanded: Boolean = false,
    onExpansion: (Boolean) -> Unit = {}, onRetry: () -> Unit = {}, onBack: () -> Unit = {}) {
    ProvideTextStyle(TextStyle(textDirection = TextDirection.ContentOrLtr)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gutter = if (maxWidth > 600.dp) ((maxWidth - 568.dp) / 2) else 16.dp
            LazyColumn(Modifier.fillMaxSize().statusBarsPadding().testTag("provider_conversion"),
                contentPadding = PaddingValues(start = gutter, end = gutter, top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) {
                        Text(stringResource(R.string.conversion_back))
                    }
                    Text(stringResource(R.string.conversion_title), style = MaterialTheme.typography.headlineMedium.copy(textDirection = TextDirection.ContentOrLtr),
                        modifier = Modifier.semantics { heading() })
                }
                when (state) {
                    ProviderConversionUiState.Loading -> item {
                        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.conversion_loading))
                        }
                    }
                    ProviderConversionUiState.SessionExpired -> item { Text(stringResource(R.string.activity_session_expired)) }
                    is ProviderConversionUiState.Error -> item {
                        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                            Text(stringResource(R.string.conversion_error))
                            Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.activity_retry))
                            }
                        }
                    }
                    is ProviderConversionUiState.Ready -> {
                        val result = state.conversion
                        val proposals = result.proposals
                        item {
                            ConversionCard {
                                Text(stringResource(R.string.conversion_period_label), style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.ContentOrLtr))
                                Text(stringResource(R.string.activity_period, formatActivityInstant(result.period.from),
                                    formatActivityInstant(result.period.to)), style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr))
                                Text(stringResource(R.string.conversion_time_zone), style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr))
                            }
                        }
                        item {
                            if (proposals.stages.issued == 0L) Text(stringResource(R.string.conversion_empty))
                            else ConversionCard(hero = true) {
                                Text(stringResource(R.string.conversion_hero))
                                Text(conversionPercentage(proposals.rates.contracted.cohort.percentage),
                                    style = MaterialTheme.typography.displaySmall.copy(textDirection = TextDirection.ContentOrLtr))
                                Text(stringResource(R.string.conversion_sample, proposals.stages.contracted, proposals.stages.issued))
                            }
                        }
                        item {
                            Text(stringResource(R.string.conversion_stages), style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.ContentOrLtr),
                                modifier = Modifier.semantics { heading() })
                            Text(stringResource(R.string.conversion_cohort_note, proposals.stages.issued))
                        }
                        item { ConversionStage(stringResource(R.string.conversion_issued), proposals.stages.issued,
                            ConversionRatio(proposals.stages.issued, proposals.stages.issued,
                                if (proposals.stages.issued > 0) 100.0 else null)) }
                        item { ConversionStage(stringResource(R.string.conversion_contracted), proposals.stages.contracted,
                            proposals.rates.contracted.cohort) }
                        item { ConversionStage(stringResource(R.string.conversion_reported), proposals.stages.reported,
                            proposals.rates.reported.cohort) }
                        item { ConversionStage(stringResource(R.string.conversion_paid), proposals.stages.paid,
                            proposals.rates.paid.cohort) }
                        item {
                            val expansionLabel = stringResource(if (advancesExpanded) R.string.conversion_collapse else R.string.conversion_expand)
                            OutlinedButton(onClick = { onExpansion(!advancesExpanded) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { stateDescription = expansionLabel },
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) {
                                Text(expansionLabel)
                            }
                        }
                        if (advancesExpanded) {
                            item { ConversionRate(stringResource(R.string.conversion_first_advance), proposals.rates.contracted.previousStage) }
                            item { ConversionRate(stringResource(R.string.conversion_second_advance), proposals.rates.reported.previousStage) }
                            item { ConversionRate(stringResource(R.string.conversion_third_advance), proposals.rates.paid.previousStage) }
                        }
                        item {
                            ConversionCard {
                                Text(stringResource(R.string.conversion_uncontracted, proposals.uncontracted), style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.ContentOrLtr))
                                Text(stringResource(R.string.conversion_uncontracted_note))
                            }
                        }
                        item { Text(stringResource(R.string.conversion_observed, formatActivityInstant(result.observedAt)),
                            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr)) }
                        item { ConversionRequests(result.requests) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversionCard(hero: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = if (hero) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (hero) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
@Composable
private fun ConversionStage(label: String, count: Long, ratio: ConversionRatio) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.ContentOrLtr))
        Text(count.toString(), style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.ContentOrLtr))
        ratio.percentage?.let {
            LinearProgressIndicator(progress = { (it / 100).toFloat() }, modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                color = MaterialTheme.colorScheme.secondary)
        }
        Text(conversionPercentage(ratio.percentage))
        Text(stringResource(R.string.conversion_sample, ratio.numerator, ratio.denominator), style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr))
    }
}
@Composable
private fun ConversionRate(label: String, ratio: ConversionRatio) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.ContentOrLtr))
        Text(conversionPercentage(ratio.percentage), style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.ContentOrLtr))
        Text(stringResource(R.string.conversion_sample, ratio.numerator, ratio.denominator))
    }
}
@Composable
private fun ConversionRequests(requests: RequestAcceptance) {
    ConversionCard {
        Text(stringResource(R.string.conversion_requests), style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.ContentOrLtr),
            modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.conversion_requests_note))
        Text(stringResource(R.string.conversion_received, requests.received))
        Text(stringResource(R.string.conversion_accepted, requests.accepted))
        Text(stringResource(R.string.conversion_pending, requests.pending))
        Text(conversionPercentage(requests.acceptanceRate.percentage), style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.ContentOrLtr))
        Text(stringResource(R.string.conversion_request_sample, requests.acceptanceRate.numerator, requests.acceptanceRate.denominator))
        Text(stringResource(R.string.conversion_acceptance_note), style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr))
    }
}
@Composable
internal fun conversionPercentage(value: Double?): String = value?.let {
    NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 2 }.format(it) + " %"
} ?: stringResource(R.string.activity_unavailable)
