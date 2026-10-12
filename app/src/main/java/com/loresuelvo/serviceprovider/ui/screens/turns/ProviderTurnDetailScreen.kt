package com.loresuelvo.serviceprovider.ui.screens.turns

import com.loresuelvo.serviceprovider.ui.components.NavigationBackButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Subject
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.TaskAlt
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
    detail: WorkOrderDetail? = null, onPhotoClick: ((String) -> Unit)? = null,
    onPhotoRetry: ((String) -> Unit)? = null, refreshFailed: Boolean = false) {
    Scaffold(
        topBar = { TopAppBar(
            title = { Text(stringResource(R.string.provider_turns_detail_title)) },
            navigationIcon = { NavigationBackButton(onClick = onBack, contentDescription = stringResource(R.string.provider_turns_back)) },
        ) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (refreshFailed) Text(stringResource(R.string.ui_refresh_failed))
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
                    ProviderCompletionEvidenceSection(it.completionReport, onPhotoClick, onPhotoRetry)
                }
                if (it.status == WorkOrderStatus.Paid) {
                    ProviderPaidHistorySection(it.paidOn, it.review)
                }
            }
            if (onCompletion != null) {
                Button(onClick = onCompletion, modifier = Modifier.fillMaxWidth().testTag("provider_turn_completion")) {
                    Icon(Icons.Outlined.TaskAlt, null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.provider_completion_title))
                }
            }
            OutlinedButton(onClick = onConversation, modifier = Modifier.fillMaxWidth().testTag("provider_turn_conversation")) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.padding(end = 8.dp))
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
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(when (label) {
                R.string.provider_turns_detail_consumer -> Icons.Outlined.PersonOutline
                R.string.provider_turns_detail_amount -> Icons.Outlined.Payments
                R.string.provider_turns_detail_date -> Icons.Outlined.EventAvailable
                R.string.provider_turns_detail_status -> Icons.Outlined.Info
                else -> Icons.Outlined.Subject
            }, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
