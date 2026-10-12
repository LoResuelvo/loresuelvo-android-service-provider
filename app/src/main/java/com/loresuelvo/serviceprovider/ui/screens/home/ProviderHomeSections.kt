package com.loresuelvo.serviceprovider.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
    onViewAll: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActivitySectionHeader(
            title = stringResource(R.string.provider_home_requests_title),
            count = state.countOrPlaceholder(),
            onViewAll = onViewAll,
            actionTag = "requests_view_all",
            actionLabel = R.string.requests_view_all,
        )
        if (state is ActivitySectionState.Ready && state.refreshFailed) SectionError(onRetry)
        when (state) {
            ActivitySectionState.Loading -> SectionLoading()
            ActivitySectionState.Error -> SectionError(onRetry)
            is ActivitySectionState.Ready -> if (state.items.isEmpty()) {
                SectionEmpty(stringResource(R.string.provider_home_requests_empty))
            } else {
                HomeActivityCarousel(state.items, { it.id }, "home_requests_row") { request, modifier ->
                    HomeRequestCard(request, modifier) { onRequestClick(request) }
                }
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
        if (state is ActivitySectionState.Ready && state.refreshFailed) SectionError(onRetry)
        when (state) {
            ActivitySectionState.Loading -> SectionLoading()
            ActivitySectionState.Error -> SectionError(onRetry)
            is ActivitySectionState.Ready -> if (state.items.isEmpty()) {
                SectionEmpty(stringResource(R.string.provider_home_scheduled_empty))
            } else {
                HomeActivityCarousel(state.items, { it.id }, "home_turns_row") { order, modifier ->
                    HomeTurnCard(order, modifier) { onTurnDetailsClick(order) }
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
            count = if (state.proposals.isEmpty() && (state.loading || state.failure != null)) "—" else pendingProposals.size.toString(),
            onViewAll = onViewAll,
            actionTag = "jobs_view_all_proposals",
            actionLabel = R.string.proposal_home_view_all,
        )
        if (state.failure != null && state.proposals.isNotEmpty()) SectionError(onRetry)
        when {
            state.loading && state.proposals.isEmpty() -> SectionLoading()
            state.failure != null && state.proposals.isEmpty() -> SectionError(onRetry)
            pendingProposals.isEmpty() -> SectionEmpty(stringResource(R.string.provider_home_proposals_empty))
            else -> HomeActivityCarousel(pendingProposals, { it.id }, "home_proposals_row") { proposal, modifier ->
                HomeProposalCard(proposal, modifier) { onDetails(proposal) }
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
            Icon(if (actionTag == "jobs_view_all_proposals") Icons.Outlined.WorkOutline
                else if (actionTag == "scheduled_view_all_turns") Icons.Outlined.EventAvailable else Icons.Outlined.Inbox,
                contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Inbox, null, tint = MaterialTheme.colorScheme.primary)
            Text(message, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun <T> ActivitySectionState<T>.countOrPlaceholder(): String = when (this) {
    is ActivitySectionState.Ready -> items.size.toString()
    ActivitySectionState.Error, ActivitySectionState.Loading -> "—"
}
