package com.loresuelvo.serviceprovider.ui.screens.proposals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposalDetailSheet(
    proposal: ServiceProposalSummary,
    onDismiss: () -> Unit,
    onConversation: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.proposal_list_item, proposal.id), style = MaterialTheme.typography.titleLarge)
            Text("${proposal.counterpart.name} ${proposal.counterpart.surname}".trim())
            Text(proposal.description, modifier = Modifier.testTag("proposal_detail_reason"))
            Text(proposalAmount(proposal.amountCents))
            Text(proposalVisit(proposal.scheduledOnEpochMillis))
            Text(stringResource(proposal.status.labelRes()))
            Text(stringResource(R.string.proposal_detail_duration))
            Text(durationText(proposal.estimatedDurationMinutes))
            Button(onClick = onConversation) { Text(stringResource(R.string.proposal_detail_conversation)) }
        }
    }
}

@Composable
internal fun proposalAmount(cents: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatted = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(BigDecimal.valueOf(cents, 2))
    return stringResource(R.string.proposal_list_amount, formatted)
}

@Composable
internal fun proposalVisit(epochMillis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return SimpleDateFormat(stringResource(R.string.proposal_list_visit_pattern), locale).format(Date(epochMillis))
}

@Composable
internal fun durationText(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.proposal_detail_minutes, minutes)
    minutes % 60 == 0 -> stringResource(R.string.proposal_detail_hours, minutes / 60)
    else -> stringResource(R.string.proposal_detail_hours_minutes, minutes / 60, minutes % 60)
}

internal fun ServiceProposalStatus.labelRes(): Int = when (this) {
    ServiceProposalStatus.Pending -> R.string.proposal_status_pending
    ServiceProposalStatus.Accepted -> R.string.proposal_status_accepted
    ServiceProposalStatus.Rejected -> R.string.proposal_status_rejected
}
