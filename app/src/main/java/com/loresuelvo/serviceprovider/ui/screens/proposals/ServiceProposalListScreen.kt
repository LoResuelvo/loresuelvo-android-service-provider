package com.loresuelvo.serviceprovider.ui.screens.proposals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.proposals.ProposalTab
import com.loresuelvo.serviceprovider.ui.proposals.ServiceProposalListUiState
import com.loresuelvo.serviceprovider.ui.proposals.ServiceProposalListViewModel
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import com.loresuelvo.serviceprovider.ui.components.ProviderAvatar
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.math.BigDecimal

@Composable
fun ServiceProposalListRoute(
    onBack: () -> Unit,
    viewModel: ServiceProposalListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ServiceProposalListScreen(state, viewModel::select, viewModel::load, onBack)
}

@Composable
fun ServiceProposalListScreen(
    state: ServiceProposalListUiState,
    onSelectTab: (ProposalTab) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.proposal_list_title), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text(stringResource(R.string.proposal_list_back)) }
            }
            val tabs = ProposalTab.entries
            TabRow(selectedTabIndex = tabs.indexOf(state.selectedTab)) {
                tabs.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { onSelectTab(tab) },
                        text = { Text(stringResource(tab.labelRes())) },
                    )
                }
            }
            when {
                state.loading -> Text(stringResource(R.string.proposal_list_loading), modifier = Modifier.padding(16.dp))
                state.failure != null -> {
                    Text(stringResource(R.string.proposal_list_error), modifier = Modifier.padding(16.dp))
                    Button(onClick = onRetry, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(stringResource(R.string.provider_home_retry))
                    }
                }
                state.visibleProposals.isEmpty() -> Text(
                    stringResource(R.string.proposal_list_empty), modifier = Modifier.padding(16.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("proposal_list_items"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                ) {
                    items(state.visibleProposals, key = { it.id }) { proposal ->
                        ProposalCard(proposal)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProposalCard(proposal: ServiceProposalSummary) {
    val locale = LocalConfiguration.current.locales[0]
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProviderAvatar(
                    proposal.counterpart.name, proposal.counterpart.surname,
                    proposal.counterpart.profilePhotoUrl,
                    stringResource(R.string.proposal_list_avatar, proposal.counterpart.name),
                )
                Column {
                    Text(
                        "${proposal.counterpart.name} ${proposal.counterpart.surname}".trim(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(R.string.proposal_list_item, proposal.id))
                }
            }
            Text(proposal.description)
            Text(stringResource(
                R.string.proposal_list_amount,
                NumberFormat.getNumberInstance(locale).apply {
                    minimumFractionDigits = 2
                    maximumFractionDigits = 2
                }.format(BigDecimal.valueOf(proposal.amountCents, 2)),
            ))
            Text(SimpleDateFormat(stringResource(R.string.proposal_list_visit_pattern), locale)
                .format(Date(proposal.scheduledOnEpochMillis)))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    stringResource(proposal.status.labelRes()),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("proposal_status_badge"),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

private fun ProposalTab.labelRes(): Int = when (this) {
    ProposalTab.Pending -> R.string.proposal_list_pending
    ProposalTab.Accepted -> R.string.proposal_list_accepted
    ProposalTab.Rejected -> R.string.proposal_list_rejected
}

private fun com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus.labelRes(): Int = when (this) {
    com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus.Pending -> R.string.proposal_status_pending
    com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus.Accepted -> R.string.proposal_status_accepted
    com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus.Rejected -> R.string.proposal_status_rejected
}
