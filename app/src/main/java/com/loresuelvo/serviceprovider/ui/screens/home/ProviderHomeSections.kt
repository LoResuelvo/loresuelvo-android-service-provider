package com.loresuelvo.serviceprovider.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.ui.home.ActivitySectionState
import java.text.DateFormat
import java.util.Date

@Composable
internal fun JobRequestsSection(
    state: ActivitySectionState<JobRequest>,
    onRetry: () -> Unit,
    onRequestClick: (JobRequest) -> Unit,
) {
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

@Composable
internal fun ScheduledWorkSection(
    state: ActivitySectionState<WorkOrder>,
    onRetry: () -> Unit,
    onAllTurnsClick: () -> Unit,
) {
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
            state.items.forEach { WorkOrderCard(it) }
        }
    }
}

@Composable
internal fun ProposalsSection(onAllProposalsClick: () -> Unit, onAllTurnsClick: () -> Unit) {
    Text(
        text = stringResource(R.string.proposal_home_jobs),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
    OutlinedButton(onClick = onAllProposalsClick, modifier = Modifier.testTag("jobs_view_all_proposals")) {
        Text(stringResource(R.string.proposal_home_view_all))
    }
    OutlinedButton(onClick = onAllTurnsClick, modifier = Modifier.testTag("jobs_view_turns")) {
        Text(stringResource(R.string.provider_turns_title))
    }
}

@Composable
private fun ActivitySectionHeader(title: String, count: String, onViewAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(text = count, style = MaterialTheme.typography.titleLarge)
        if (onViewAll != null) {
            TextButton(onClick = onViewAll, modifier = Modifier.testTag("scheduled_view_all_turns")) {
                Text(stringResource(R.string.provider_turns_view_all))
            }
        }
    }
}

@Composable
private fun JobRequestCard(
    request: JobRequest,
    onRequestClick: (JobRequest) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
private fun WorkOrderCard(workOrder: WorkOrder) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = workOrder.consumerName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = workOrder.description,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(
                    R.string.provider_home_scheduled_on,
                    formatScheduledDate(workOrder),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

private fun formatScheduledDate(workOrder: WorkOrder): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(workOrder.scheduledOn))
