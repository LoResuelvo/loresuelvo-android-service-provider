package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetail
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.ui.components.ProviderAvatar
import com.loresuelvo.serviceprovider.ui.screens.proposals.proposalAmount
import java.util.TimeZone

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProviderTurnDetailScreen(order: WorkOrder, onBack: () -> Unit, onConversation: () -> Unit,
    missingConversation: Boolean = false, proposalFailure: ServiceProposalListOutcome.Failure? = null,
    resolvingConversation: Boolean = false,
    onRetryConversation: () -> Unit = {}, onCompletion: (() -> Unit)? = null,
    detail: WorkOrderDetail? = null, onPhotoClick: ((String) -> Unit)? = null) {
    Scaffold(
        topBar = { TopAppBar(
            title = { Text(stringResource(R.string.provider_turns_detail_title)) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.provider_turns_back)) } },
        ) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProviderAvatar(
                name = order.consumerGivenName,
                surname = order.consumerSurname,
                profilePhotoUrl = order.consumerPhotoUrl,
                contentDescription = stringResource(R.string.proposal_list_avatar, order.consumerGivenName),
            )
            DetailField(R.string.provider_turns_detail_consumer, order.consumerName)
            DetailField(R.string.provider_turns_detail_amount, proposalAmount(order.amountCents))
            DetailField(R.string.provider_turns_detail_date, formatTurnDate(order.scheduledOn,
                stringResource(R.string.provider_turns_visit_pattern), LocalConfiguration.current.locales[0], TimeZone.getDefault()))
            DetailField(R.string.provider_turns_detail_status,
                providerTurnStatusBadge(order.status)?.label?.let { stringResource(it) }
                    ?: stringResource(R.string.provider_turns_status_unknown))
            DetailField(R.string.provider_turns_detail_reason, order.description)
            detail?.let {
                if (it.status == WorkOrderStatus.AwaitingPayment || it.status == WorkOrderStatus.Paid) {
                    ProviderCompletionEvidenceSection(it.completionReport, onPhotoClick)
                }
            }
            if (onCompletion != null) {
                Button(onClick = onCompletion, modifier = Modifier.testTag("provider_turn_completion")) {
                    Text(stringResource(R.string.provider_completion_title))
                }
            }
            Button(onClick = onConversation, modifier = Modifier.testTag("provider_turn_conversation")) {
                Text(stringResource(R.string.provider_turns_view_conversation))
            }
            if (missingConversation) {
                Text(stringResource(if (proposalFailure == null) R.string.provider_turns_conversation_missing
                    else R.string.proposal_list_error))
                Button(onClick = onRetryConversation, enabled = !resolvingConversation) {
                    Text(stringResource(R.string.provider_home_retry))
                }
            }
        }
    }
}

@Composable
private fun DetailField(label: Int, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(label), style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold)
        Text(value, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
    }
}
