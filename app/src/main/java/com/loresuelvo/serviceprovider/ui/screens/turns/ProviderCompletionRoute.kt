package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.activity.CompletionEligibility
import com.loresuelvo.serviceprovider.ui.turns.ProviderCompletionUiState
import com.loresuelvo.serviceprovider.ui.turns.ProviderCompletionViewModel
import com.loresuelvo.serviceprovider.ui.turns.EvidenceSelectionStatus
import com.loresuelvo.serviceprovider.ui.turns.EvidenceUploadStatus
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsUiState

@Composable
fun ProviderCompletionRoute(
    orderId: Int,
    turnsState: ProviderTurnsUiState,
    onBack: () -> Unit,
    onRetryTurns: () -> Unit,
    viewModel: ProviderCompletionViewModel = hiltViewModel(),
    pickPhotos: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val evidence by viewModel.evidence.collectAsStateWithLifecycle()
    val evidenceIssue by viewModel.evidenceIssue.collectAsStateWithLifecycle()
    val validationIssue by viewModel.validationIssue.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { uris ->
        onCompletionImagesPicked(viewModel, uris)
    }
    val exit = {
        viewModel.discardDraft()
        onBack()
    }
    BackHandler(onBack = exit)
    val order = (turnsState as? ProviderTurnsUiState.Ready)?.orders?.firstOrNull { it.id == orderId }
    LaunchedEffect(order) { if (order != null) viewModel.open(order) }
    LaunchedEffect(evidence, state) {
        evidence.filter { it.status is EvidenceSelectionStatus.Ready && it.uploadStatus == EvidenceUploadStatus.NotStarted }
            .forEach { viewModel.uploadEvidence(it.id) }
    }

    when {
        state == ProviderCompletionUiState.SessionExpired -> CompletionFallback(
            R.string.provider_completion_session_expired, exit)
        turnsState == ProviderTurnsUiState.Loading -> CompletionFallback(
            R.string.provider_turns_loading, exit, loading = true)
        turnsState == ProviderTurnsUiState.Error -> CompletionFallback(
            R.string.provider_turns_error, exit, onRetryTurns)
        order == null -> CompletionFallback(R.string.provider_completion_missing, exit, onRetryTurns)
        else -> {
            val availability = when (val current = state) {
                is ProviderCompletionUiState.Ready -> if (current.order.id == orderId)
                    current.eligibility.toFormAvailability() else CompletionFormAvailability.Checking
                else -> CompletionFormAvailability.Checking
            }
            ProviderCompletionFormScreen(order, availability, description, viewModel::onDescriptionChange,
                { if (pickPhotos != null) pickPhotos() else picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                exit, onRetry = viewModel::retry, canAddPhotos = availability == CompletionFormAvailability.Eligible,
                evidence = evidence, evidenceIssue = evidenceIssue,
                onRemoveEvidence = viewModel::removeEvidence,
                onRetryEvidence = viewModel::retryEvidence,
                validationIssue = validationIssue, onSubmitAttempt = { viewModel.attemptSubmit() },
                canAttemptSubmit = availability == CompletionFormAvailability.Eligible)
        }
    }
}

internal fun onCompletionImagesPicked(viewModel: ProviderCompletionViewModel, uris: List<Uri>) {
    if (uris.isNotEmpty()) viewModel.selectEvidence(uris.map(Uri::toString))
}

internal fun CompletionEligibility.toFormAvailability(): CompletionFormAvailability = when (this) {
    CompletionEligibility.Eligible -> CompletionFormAvailability.Eligible
    CompletionEligibility.TooEarly -> CompletionFormAvailability.TooEarly
    CompletionEligibility.AlreadyReported -> CompletionFormAvailability.AlreadyReported
    CompletionEligibility.Forbidden -> CompletionFormAvailability.Forbidden
    CompletionEligibility.ChangedOrder -> CompletionFormAvailability.ChangedOrder
    CompletionEligibility.Unavailable -> CompletionFormAvailability.Unavailable
    CompletionEligibility.Failure.NotFound -> CompletionFormAvailability.NotFound
    is CompletionEligibility.Failure -> CompletionFormAvailability.Error
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CompletionFallback(message: Int, onBack: () -> Unit, onRetry: (() -> Unit)? = null,
    loading: Boolean = false) {
    Scaffold(topBar = { TopAppBar(
        title = { Text(stringResource(R.string.provider_completion_title)) },
        navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.provider_turns_back)) } },
    ) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            if (loading) CircularProgressIndicator()
            Text(stringResource(message))
            if (onRetry != null) Button(onClick = onRetry) { Text(stringResource(R.string.provider_home_retry)) }
        }
    }
}
