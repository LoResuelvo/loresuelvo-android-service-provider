package com.loresuelvo.serviceprovider.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import com.loresuelvo.serviceprovider.ui.components.ProviderAvatar
import com.loresuelvo.serviceprovider.ui.screens.proposals.labelRes
import com.loresuelvo.serviceprovider.ui.screens.proposals.proposalAmount
import com.loresuelvo.serviceprovider.ui.screens.turns.formatTurnDate
import com.loresuelvo.serviceprovider.ui.screens.turns.providerTurnStatusBadge
import java.util.TimeZone

@Composable
internal fun HomeTurnCard(order: WorkOrder, modifier: Modifier, onDetails: () -> Unit) {
    HomeActivityCard(order.consumerGivenName, order.consumerSurname, order.consumerPhotoUrl,
        stringResource(providerTurnStatusBadge(order.status)?.label ?: R.string.provider_turns_status_unknown),
        order.description, proposalAmount(order.amountCents), homeDate(order.scheduledOn),
        stringResource(R.string.provider_turns_view_details), "provider_turn_details_${order.id}",
        modifier.testTag("provider_turn_${order.id}"), onDetails)
}

@Composable
internal fun HomeProposalCard(proposal: ServiceProposalSummary, modifier: Modifier, onDetails: () -> Unit) {
    HomeActivityCard(proposal.counterpart.name, proposal.counterpart.surname, proposal.counterpart.profilePhotoUrl,
        stringResource(proposal.status.labelRes()), proposal.description, proposalAmount(proposal.amountCents),
        homeDate(proposal.scheduledOnEpochMillis), stringResource(R.string.provider_home_view_proposal),
        "home_proposal_details_${proposal.id}", modifier.testTag("home_proposal_${proposal.id}"), onDetails)
}

@Composable
internal fun HomeRequestCard(request: JobRequest, modifier: Modifier, onDetails: () -> Unit) {
    HomeActivityCard(request.consumerName, "", null, stringResource(R.string.home_request_pending),
        request.description, request.title, stringResource(R.string.home_request_chat_hint),
        stringResource(R.string.provider_home_view_request), "home_request_details_${request.id}",
        modifier.testTag("home_request_${request.id}"), onDetails)
}

@Composable
private fun homeDate(timestamp: Long): String = formatTurnDate(timestamp,
    stringResource(R.string.provider_home_visit_pattern), LocalConfiguration.current.locales[0], TimeZone.getDefault())

@Composable
private fun HomeActivityCard(
    name: String, surname: String, photo: String?, status: String,
    description: String, summary: String, detail: String, action: String, actionTag: String,
    modifier: Modifier, onDetails: () -> Unit,
) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProviderAvatar(name, surname, photo, stringResource(R.string.proposal_list_avatar, name), size = 48.dp)
                Text("$name $surname".trim(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                Text(status, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(summary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(description, style = MaterialTheme.typography.bodyLarge,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            OutlinedButton(onClick = onDetails, modifier = Modifier.fillMaxWidth().testTag(actionTag)) {
                Text(action, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
