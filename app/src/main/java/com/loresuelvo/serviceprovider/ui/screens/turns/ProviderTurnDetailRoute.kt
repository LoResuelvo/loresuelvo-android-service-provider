package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnDetailUiState
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnDetailViewModel

@Composable
fun ProviderTurnDetailRoute(
    onBack: () -> Unit,
    onConversation: (Int) -> Unit,
    onCompletion: (Int) -> Unit,
    viewModel: ProviderTurnDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var missingConversation by rememberSaveable(viewModel.orderId) { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    when (val current = state) {
        is ProviderTurnDetailUiState.Ready -> {
            val detail = (current.result.detail as WorkOrderDetailOutcome.Success).order
            val consumer = current.result.consumer
            val fallback = stringResource(R.string.provider_order_consumer_unavailable)
            val displayOrder = current.toDisplayOrder(fallback)
            ProviderTurnDetailScreen(displayOrder, onBack = onBack,
                onConversation = {
                    val conversationId = current.conversationId
                    if (conversationId == null) missingConversation = true else onConversation(conversationId)
                },
                missingConversation = missingConversation && current.conversationId == null,
                onRetryConversation = viewModel::load,
                onCompletion = if (consumer != null) ({ onCompletion(detail.id) }) else null)
        }
        ProviderTurnDetailUiState.Loading -> DetailFallback(onBack, R.string.provider_turns_loading, loading = true)
        is ProviderTurnDetailUiState.Error -> DetailFallback(onBack, R.string.provider_order_detail_error,
            onRetry = viewModel::load)
    }
}

internal fun ProviderTurnDetailUiState.Ready.toDisplayOrder(fallbackName: String): WorkOrder {
    val detail = (result.detail as WorkOrderDetailOutcome.Success).order
    return (result.consumer ?: WorkOrder(detail.id, fallbackName, detail.description,
        detail.scheduledOn, detail.status)).copy(
            description = detail.description,
            scheduledOn = detail.scheduledOn,
            status = detail.status,
            amountCents = detail.amountCents,
            serviceProposalId = detail.serviceProposalId,
            consumerId = detail.consumerId,
        )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DetailFallback(onBack: () -> Unit, message: Int, loading: Boolean = false,
    onRetry: (() -> Unit)? = null) {
    Scaffold(topBar = { TopAppBar(
        title = { Text(stringResource(R.string.provider_turns_detail_title)) },
        navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.provider_turns_back)) } },
    ) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            if (loading) CircularProgressIndicator()
            Text(stringResource(message))
            if (onRetry != null) Button(onClick = onRetry) { Text(stringResource(R.string.provider_home_retry)) }
        }
    }
}
