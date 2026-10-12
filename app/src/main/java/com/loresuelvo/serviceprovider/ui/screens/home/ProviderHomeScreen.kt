package com.loresuelvo.serviceprovider.ui.screens.home

import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import com.loresuelvo.serviceprovider.ui.components.RefreshableContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.ui.components.ProviderAvatar
import com.loresuelvo.serviceprovider.ui.home.ProviderHomeUiState
import com.loresuelvo.serviceprovider.ui.proposals.ServiceProposalListUiState
import com.loresuelvo.serviceprovider.ui.screens.proposals.ProposalDetailSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderHomeScreen(
    provider: CurrentAccount.Provider,
    uiState: ProviderHomeUiState,
    onRetryJobRequests: () -> Unit,
    onRetryScheduledWork: () -> Unit,
    onJobRequestClick: (com.loresuelvo.serviceprovider.domain.activity.JobRequest) -> Unit,
    onMercadoPagoClick: () -> Unit,
    onAllProposalsClick: () -> Unit = {},
    onAllTurnsClick: () -> Unit = {},
    onTurnDetailsClick: (com.loresuelvo.serviceprovider.domain.activity.WorkOrder) -> Unit = {},
    modifier: Modifier = Modifier,
    proposalsState: ServiceProposalListUiState = ServiceProposalListUiState(),
    onRetryProposals: () -> Unit = {},
    onProposalConversation: (Int) -> Unit = {},
    onRefresh: () -> Unit = {},
    onAllRequestsClick: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    var selectedProposalId by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(proposalsState.loading, proposalsState.proposals) {
        if (!proposalsState.loading && proposalsState.proposals.none { it.id == selectedProposalId }) {
            selectedProposalId = null
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        RefreshableContent(
            refreshing = uiState.refreshingRequests || uiState.refreshingWork || proposalsState.loading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().testTag("home_refresh"),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("home_header"),
                    shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().statusBarsPadding().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ProviderIdentityHeader(provider)
                            Text(stringResource(R.string.provider_home_overview), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    ScheduledWorkSection(
                        state = uiState.scheduledWork,
                        onRetry = onRetryScheduledWork,
                        onAllTurnsClick = onAllTurnsClick,
                        onTurnDetailsClick = onTurnDetailsClick,
                    )
                    ProposalsSection(proposalsState, onRetryProposals, onAllProposalsClick) {
                        selectedProposalId = it.id
                    }
                    JobRequestsSection(
                        state = uiState.jobRequests,
                        onRetry = onRetryJobRequests,
                        onRequestClick = onJobRequestClick,
                        onViewAll = onAllRequestsClick,
                    )
                    OutlinedButton(onClick = onMercadoPagoClick, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.AccountBalanceWallet, null, Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.provider_home_mercadopago_action))
                    }
                }
            }
        }
    }
    proposalsState.proposals.firstOrNull { it.id == selectedProposalId }?.let { proposal ->
        ProposalDetailSheet(proposal, onDismiss = { selectedProposalId = null }, onConversation = {
            selectedProposalId = null
            onProposalConversation(proposal.conversationId)
        })
    }
}

@Composable
private fun ProviderIdentityHeader(provider: CurrentAccount.Provider) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderAvatar(
            name = provider.name,
            surname = provider.surname,
            profilePhotoUrl = provider.profilePhotoUrl,
            contentDescription = stringResource(
                R.string.provider_home_photo_description,
                provider.name,
            ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_greeting, provider.name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
            )
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    text = provider.category.name,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
