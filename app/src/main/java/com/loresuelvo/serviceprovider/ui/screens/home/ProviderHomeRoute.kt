package com.loresuelvo.serviceprovider.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
    onJobRequestClick: (com.loresuelvo.serviceprovider.domain.activity.JobRequest) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProviderHomeViewModel = hiltViewModel(),
    proposalsViewModel: ServiceProposalListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val proposalsState by proposalsViewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { proposalsViewModel.onResume() }

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
        onRetryJobRequests = viewModel::retryJobRequests,
        onRetryScheduledWork = viewModel::retryScheduledWork,
        onJobRequestClick = onJobRequestClick,
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
}
