package com.loresuelvo.serviceprovider.ui.screens.jobrequest

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.loresuelvo.serviceprovider.ui.home.ProviderHomeViewModel
import com.loresuelvo.serviceprovider.ui.navigation.Route

@Composable
fun JobRequestListRoute(navController: NavHostController, viewModel: ProviderHomeViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedRequestId by rememberSaveable { mutableStateOf<Int?>(null) }
    LifecycleResumeEffect(viewModel) {
        viewModel.retryJobRequests()
        onPauseOrDispose { }
    }
    JobRequestListScreen(state.jobRequests, state.refreshingRequests,
        onBack = { navController.popBackStack() }, onRefresh = viewModel::retryJobRequests,
        onRequestClick = { selectedRequestId = it.id })
    selectedRequestId?.let { id ->
        JobRequestDetailSheetRoute(
            requestId = id,
            onClose = { selectedRequestId = null },
            onUnavailable = {
                viewModel.removeJobRequest(id)
                selectedRequestId = null
            },
            onAccepted = { requestId, conversationId ->
                viewModel.removeJobRequest(requestId)
                selectedRequestId = null
                navController.navigate(Route.Conversation.buildPath(conversationId)) { launchSingleTop = true }
            },
        )
    }
}
