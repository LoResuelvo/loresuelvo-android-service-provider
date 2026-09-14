package com.loresuelvo.serviceprovider.ui.screens.jobrequest

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.loresuelvo.serviceprovider.ui.jobrequest.JobRequestDetailViewModel
import com.loresuelvo.serviceprovider.ui.jobrequest.JobRequestDetailEffect
import androidx.compose.runtime.LaunchedEffect

@Composable
fun JobRequestDetailRoute(
    navController: NavHostController,
    onAccepted: (requestId: Int, conversationId: Int) -> Unit,
    viewModel: JobRequestDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedImageIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(viewModel, onAccepted) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is JobRequestDetailEffect.Accepted -> onAccepted(
                    effect.requestId,
                    effect.conversationId,
                )
            }
        }
    }

    JobRequestDetailScreen(
        uiState = uiState,
        onClose = { navController.popBackStack() },
        onRetry = viewModel::retry,
        onAccept = viewModel::accept,
        onRetryAccept = viewModel::retryAccept,
        selectedImageIndex = selectedImageIndex,
        onImageSelected = { selectedImageIndex = it },
        onImageViewerClose = { selectedImageIndex = null },
    )
}
