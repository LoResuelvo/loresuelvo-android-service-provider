package com.loresuelvo.serviceprovider.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import com.loresuelvo.serviceprovider.ui.components.ProviderAvatar
import com.loresuelvo.serviceprovider.ui.screens.proposals.labelRes
import com.loresuelvo.serviceprovider.ui.screens.proposals.proposalAmount
import com.loresuelvo.serviceprovider.ui.screens.turns.formatTurnDate
import java.util.TimeZone
import com.loresuelvo.serviceprovider.ui.screens.turns.providerTurnStatusBadge

@Composable
internal fun HomeTurnCard(order: WorkOrder, modifier: Modifier, onDetails: () -> Unit) {
    Surface(modifier.testTag("provider_turn_${order.id}"), shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeClientHeader(order.consumerGivenName, order.consumerSurname, order.consumerPhotoUrl,
                stringResource(providerTurnStatusBadge(order.status)?.label ?: R.string.provider_turns_status_unknown))
            Text(order.description, style = MaterialTheme.typography.bodyLarge,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(proposalAmount(order.amountCents), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            HomeCardFooter(formatTurnDate(order.scheduledOn,
                stringResource(R.string.provider_home_visit_pattern), LocalConfiguration.current.locales[0],
                TimeZone.getDefault()),
                stringResource(R.string.provider_turns_view_details),
                "provider_turn_details_${order.id}", onDetails)
        }
    }
}

@Composable
internal fun HomeProposalCard(proposal: ServiceProposalSummary, modifier: Modifier, onDetails: () -> Unit) {
    Surface(modifier.testTag("home_proposal_${proposal.id}"), shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeClientHeader(proposal.counterpart.name, proposal.counterpart.surname,
                proposal.counterpart.profilePhotoUrl, stringResource(proposal.status.labelRes()))
            HomeCardFooter(proposalAmount(proposal.amountCents),
                stringResource(R.string.provider_home_view_proposal),
                "home_proposal_details_${proposal.id}", onDetails)
        }
    }
}

@Composable
private fun HomeClientHeader(name: String, surname: String, photoUrl: String?, status: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stackStatus = maxWidth < 300.dp || LocalConfiguration.current.fontScale > 1.2f
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProviderAvatar(name, surname, photoUrl,
                    stringResource(R.string.proposal_list_avatar, name), size = 48.dp)
                Text("$name $surname".trim(), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!stackStatus) HomeStatusBadge(status)
            }
            if (stackStatus) HomeStatusBadge(status)
        }
    }
}

@Composable
private fun HomeStatusBadge(status: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary) {
        Text(status, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeCardFooter(summary: String, action: String, tag: String, onClick: () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(summary, modifier = Modifier.align(Alignment.CenterVertically).padding(end = 8.dp),
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = onClick, modifier = Modifier.testTag(tag)) { Text(action) }
    }
}
