package com.loresuelvo.serviceprovider.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.loresuelvo.serviceprovider.ui.screens.jobrequest.JobRequestDetailSheetRoute
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.ui.home.ProviderHomeViewModel
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.proposals.ServiceProposalListViewModel

@Composable
fun ProviderHomeRoute(
    navController: NavHostController,
    provider: CurrentAccount.Provider,
    modifier: Modifier = Modifier,
    viewModel: ProviderHomeViewModel = hiltViewModel(),
    proposalsViewModel: ServiceProposalListViewModel = hiltViewModel(),
) {
    var selectedRequestId by rememberSaveable { mutableStateOf<Int?>(null) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val proposalsState by proposalsViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(navController, viewModel) {
        val homeEntry = navController.currentBackStackEntry ?: return@LaunchedEffect
        homeEntry.savedStateHandle
            .getStateFlow<Int?>(Route.JobRequestDetail.resolvedRequestId, null)
            .collect { resolvedRequestId ->
                resolvedRequestId?.let {
                    viewModel.removeJobRequest(it)
                    homeEntry.savedStateHandle[Route.JobRequestDetail.resolvedRequestId] = null
                }
            }
    }
    LaunchedEffect(navController, viewModel) {
        val homeEntry = navController.currentBackStackEntry ?: return@LaunchedEffect
        homeEntry.savedStateHandle.getStateFlow<Int?>(Route.ProviderCompletion.reportedOrderId, null)
            .collect { reportedOrderId ->
                reportedOrderId?.let {
                    viewModel.retryScheduledWork()
                    homeEntry.savedStateHandle[Route.ProviderCompletion.reportedOrderId] = null
                }
            }
    }

    ProviderHomeScreen(
        provider = provider,
        proposalsState = proposalsState,
        onRetryProposals = proposalsViewModel::load,
        onProposalConversation = { id ->
            navController.navigate(Route.Conversation.buildPath(id)) { launchSingleTop = true }
        },
        uiState = uiState,
        onRefresh = { viewModel.refresh(); proposalsViewModel.load() },
        onRetryJobRequests = viewModel::retryJobRequests,
        onRetryScheduledWork = viewModel::retryScheduledWork,
        onJobRequestClick = { selectedRequestId = it.id },
        onAllProposalsClick = { navController.navigate(Route.ServiceProposals.path) { launchSingleTop = true } },
        onAllTurnsClick = { navController.navigate(Route.ProviderTurns.path) { launchSingleTop = true } },
        onTurnDetailsClick = { order ->
            navController.navigate(Route.ProviderTurnDetail.buildPath(order.id)) { launchSingleTop = true }
        },
        onMercadoPagoClick = {
            navController.navigate(Route.MercadoPagoConnect.path) {
                launchSingleTop = true
            }
        },
        modifier = modifier,
    )
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
