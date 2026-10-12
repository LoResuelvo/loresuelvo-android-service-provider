package com.loresuelvo.serviceprovider.ui.screens.jobrequest

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.loresuelvo.serviceprovider.ui.jobrequest.JobRequestDetailViewModel
import com.loresuelvo.serviceprovider.ui.jobrequest.JobRequestDetailEffect
import com.loresuelvo.serviceprovider.ui.jobrequest.JobRequestDetailUiState
import com.loresuelvo.serviceprovider.ui.navigation.Route
import androidx.compose.runtime.LaunchedEffect

@Composable
fun JobRequestDetailRoute(
    navController: NavHostController,
    onAccepted: (requestId: Int, conversationId: Int) -> Unit,
    viewModel: JobRequestDetailViewModel = hiltViewModel(),
) {
    JobRequestDetailSheetRoute(
        onClose = { navController.popBackStack() },
        onUnavailable = { id ->
            navController.previousBackStackEntry?.savedStateHandle
                ?.set(Route.JobRequestDetail.resolvedRequestId, id)
            navController.popBackStack()
        },
        onAccepted = onAccepted,
        viewModel = viewModel,
    )
}

@Composable
fun JobRequestDetailSheetRoute(
    onClose: () -> Unit,
    onUnavailable: (Int?) -> Unit,
    onAccepted: (Int, Int) -> Unit,
    requestId: Int? = null,
    viewModel: JobRequestDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedImageIndex by rememberSaveable(requestId) { mutableStateOf<Int?>(null) }
    val accepted by rememberUpdatedState(onAccepted)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, requestId) {
        requestId?.let(viewModel::open)
    }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is JobRequestDetailEffect.Accepted -> accepted(effect.requestId, effect.conversationId)
                }
            }
        }
    }
    JobRequestDetailScreen(
        uiState = uiState,
        onClose = onClose,
        onUnavailable = { onUnavailable(uiState.requestId()) },
        onRetry = viewModel::retry,
        onAccept = viewModel::accept,
        onRetryAccept = viewModel::retryAccept,
        selectedImageIndex = selectedImageIndex,
        onImageSelected = { selectedImageIndex = it },
        onImageViewerClose = { selectedImageIndex = null },
    )
}

private fun JobRequestDetailUiState.requestId(): Int? = when (this) {
    is JobRequestDetailUiState.AcceptUnavailable -> request.id
    else -> null
}
