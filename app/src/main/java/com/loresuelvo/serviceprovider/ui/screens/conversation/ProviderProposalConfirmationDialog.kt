package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome

@Composable
fun ProviderProposalConfirmationDialog(
    reviewing: ProposalUiState.Reviewing,
    onConfirm: () -> Unit = {},
    onAcknowledgeRisk: () -> Unit = {},
    sending: Boolean = false,
    onCancel: () -> Unit,
) {
    val form = reviewing.form
    val proposal = reviewing.proposal
    AlertDialog(
        onDismissRequest = { if (!sending) onCancel() },
        title = { Text(stringResource(R.string.provider_proposal_review_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.provider_proposal_consumer, form.consumerName))
                Text(stringResource(R.string.provider_proposal_review_amount, proposal.amountPesos))
                Text(stringResource(R.string.provider_proposal_review_schedule,
                    form.date, form.time, form.zoneId, formatOffset(proposal.offsetMinutes)))
                Text(stringResource(R.string.provider_proposal_duration_minutes, proposal.durationMinutes))
                Text(proposal.reason)
                reviewing.failure?.let { failure ->
                    Text(stringResource(when (failure) {
                        is CreateServiceProposalOutcome.Failure.Invalid -> R.string.provider_proposal_rejected
                        CreateServiceProposalOutcome.Failure.Rejected -> R.string.provider_proposal_rejected
                        CreateServiceProposalOutcome.Failure.SessionExpired -> R.string.provider_proposal_session_expired
                        CreateServiceProposalOutcome.Failure.ProviderIneligible -> R.string.provider_proposal_provider_ineligible
                        CreateServiceProposalOutcome.Failure.ConsumerUnavailable -> R.string.provider_proposal_consumer_unavailable
                        CreateServiceProposalOutcome.Failure.PaymentRequired -> R.string.provider_proposal_payment_required
                        CreateServiceProposalOutcome.Failure.InactiveConversation -> R.string.provider_proposal_inactive_conversation
                        CreateServiceProposalOutcome.Failure.Conflict -> R.string.provider_proposal_conflict
                        CreateServiceProposalOutcome.Failure.Uncertain -> R.string.provider_proposal_uncertain
                    }))
                }
            }
        },
        confirmButton = {
            val uncertain = reviewing.failure == CreateServiceProposalOutcome.Failure.Uncertain
            TextButton(
                onClick = if (uncertain && !reviewing.duplicateRiskAcknowledged) onAcknowledgeRisk else onConfirm,
                enabled = !sending && (reviewing.failure == null || uncertain),
            ) {
                Text(stringResource(when {
                    sending -> R.string.provider_proposal_sending
                    uncertain && !reviewing.duplicateRiskAcknowledged -> R.string.provider_proposal_acknowledge_risk
                    else -> R.string.provider_proposal_confirm_send
                }))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !sending) {
                Text(stringResource(R.string.provider_proposal_cancel_review))
            }
        },
        modifier = Modifier.testTag(PROPOSAL_CONFIRMATION_TAG),
    )
}
