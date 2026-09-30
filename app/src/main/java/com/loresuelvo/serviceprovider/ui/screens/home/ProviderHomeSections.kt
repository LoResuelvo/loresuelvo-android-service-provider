package com.loresuelvo.serviceprovider.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import com.loresuelvo.serviceprovider.ui.home.ActivitySectionState
import com.loresuelvo.serviceprovider.ui.proposals.ServiceProposalListUiState

@Composable
internal fun JobRequestsSection(
    state: ActivitySectionState<JobRequest>,
    onRetry: () -> Unit,
    onRequestClick: (JobRequest) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActivitySectionHeader(
            title = stringResource(R.string.provider_home_requests_title),
            count = state.countOrPlaceholder(),
        )
        when (state) {
            ActivitySectionState.Loading -> SectionLoading()
            ActivitySectionState.Error -> SectionError(onRetry)
            is ActivitySectionState.Ready -> if (state.items.isEmpty()) {
                SectionEmpty(stringResource(R.string.provider_home_requests_empty))
            } else {
                state.items.forEach { JobRequestCard(it, onRequestClick) }
            }
        }
    }
}

@Composable
internal fun ScheduledWorkSection(
    state: ActivitySectionState<WorkOrder>,
    onRetry: () -> Unit,
    onAllTurnsClick: () -> Unit,
    onTurnDetailsClick: (WorkOrder) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActivitySectionHeader(
            title = stringResource(R.string.provider_home_scheduled_title),
            count = state.countOrPlaceholder(),
            onViewAll = onAllTurnsClick,
        )
        when (state) {
            ActivitySectionState.Loading -> SectionLoading()
            ActivitySectionState.Error -> SectionError(onRetry)
            is ActivitySectionState.Ready -> if (state.items.isEmpty()) {
                SectionEmpty(stringResource(R.string.provider_home_scheduled_empty))
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cardWidth = minOf(440.dp, if (state.items.size == 1) maxWidth else maxWidth - 16.dp)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().testTag("home_turns_row"),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.items, key = { it.id }) { order ->
                            HomeTurnCard(order, Modifier.width(cardWidth)) { onTurnDetailsClick(order) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProposalsSection(
    state: ServiceProposalListUiState,
    onRetry: () -> Unit,
    onViewAll: () -> Unit,
    onDetails: (ServiceProposalSummary) -> Unit,
) {
    val pendingProposals = state.proposals.filter { it.status == ServiceProposalStatus.Pending }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActivitySectionHeader(
            title = stringResource(R.string.provider_home_proposals_title),
            count = if (state.loading || state.failure != null) "—" else pendingProposals.size.toString(),
            onViewAll = onViewAll,
            actionTag = "jobs_view_all_proposals",
            actionLabel = R.string.proposal_home_view_all,
        )
        when {
            state.loading -> SectionLoading()
            state.failure != null -> SectionError(onRetry)
            pendingProposals.isEmpty() -> SectionEmpty(stringResource(R.string.provider_home_proposals_empty))
            else -> BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cardWidth = minOf(440.dp, if (pendingProposals.size == 1) maxWidth else maxWidth - 16.dp)
                LazyRow(
                    modifier = Modifier.fillMaxWidth().testTag("home_proposals_row"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(pendingProposals, key = { it.id }) { proposal ->
                        HomeProposalCard(proposal, Modifier.width(cardWidth)) { onDetails(proposal) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivitySectionHeader(
    title: String,
    count: String,
    onViewAll: (() -> Unit)? = null,
    actionTag: String = "scheduled_view_all_turns",
    actionLabel: Int = R.string.provider_turns_view_all,
) {
    val stackLink = onViewAll != null && LocalConfiguration.current.fontScale > 1.3f
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Row(Modifier.fillMaxWidth().semantics { heading() },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).then(
                    if (onViewAll != null && actionTag == "scheduled_view_all_turns") Modifier.testTag("scheduled_section_title") else Modifier))
            Text(text = count, style = MaterialTheme.typography.labelLarge,
                modifier = if (onViewAll != null && actionTag == "scheduled_view_all_turns") Modifier.testTag("scheduled_section_count") else Modifier)
            if (onViewAll != null && !stackLink) TextButton(onClick = onViewAll,
                modifier = Modifier.testTag(actionTag)) {
                Text(stringResource(actionLabel))
            }
        }
        if (stackLink) TextButton(onClick = onViewAll!!,
            modifier = Modifier.testTag(actionTag)) {
            Text(stringResource(actionLabel))
        }
    }
}

@Composable
private fun JobRequestCard(
    request: JobRequest,
    onRequestClick: (JobRequest) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.extraLarge) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = request.consumerName, style = MaterialTheme.typography.titleMedium)
            Text(text = request.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = request.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { onRequestClick(request) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.provider_home_view_request))
            }
        }
    }
}

@Composable
private fun SectionLoading() {
    CircularProgressIndicator()
}

@Composable
private fun SectionError(onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.provider_home_section_error))
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.provider_home_retry))
        }
    }
}

@Composable
private fun SectionEmpty(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

private fun <T> ActivitySectionState<T>.countOrPlaceholder(): String = when (this) {
    is ActivitySectionState.Ready -> items.size.toString()
    ActivitySectionState.Error, ActivitySectionState.Loading -> "—"
}
