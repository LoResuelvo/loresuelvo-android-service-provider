package com.loresuelvo.serviceprovider.ui.screens.proposals

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.proposals.ServiceProposalListViewModel
import com.loresuelvo.serviceprovider.ui.proposals.ServiceProposalListUiState

@Composable
fun CollectionProposalSheet(proposalId: Int, onDismiss: () -> Unit, onConversation: (Int) -> Unit,
    viewModel: ServiceProposalListViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(proposalId) { viewModel.load() }
    CollectionProposalContent(proposalId, state, onDismiss, onConversation, viewModel::load)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionProposalContent(proposalId: Int, state: ServiceProposalListUiState,
    onDismiss: () -> Unit, onConversation: (Int) -> Unit, onRetry: () -> Unit) {
    val proposal = state.proposals.firstOrNull { it.id == proposalId }
    if (proposal != null) {
        ProposalDetailSheet(proposal, onDismiss) { onConversation(proposal.conversationId) }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.proposal_detail_title), style = MaterialTheme.typography.titleLarge)
                when {
                    state.loading -> { CircularProgressIndicator(); Text(stringResource(R.string.proposal_list_loading)) }
                    state.failure != null -> {
                        Text(stringResource(R.string.proposal_list_error))
                        Button(onClick = onRetry) { Text(stringResource(R.string.activity_retry)) }
                    }
                    else -> Text(stringResource(R.string.performance_proposal_unavailable))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_close)) }
            }
        }
    }
}
