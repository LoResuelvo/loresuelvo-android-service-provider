package com.loresuelvo.serviceprovider.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.ui.home.ProviderHomeViewModel
import com.loresuelvo.serviceprovider.ui.navigation.Route

@Composable
fun ProviderHomeRoute(
    navController: NavHostController,
    provider: CurrentAccount.Provider,
    onJobRequestClick: (com.loresuelvo.serviceprovider.domain.activity.JobRequest) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProviderHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ProviderHomeScreen(
        provider = provider,
        uiState = uiState,
        onRetryJobRequests = viewModel::retryJobRequests,
        onRetryScheduledWork = viewModel::retryScheduledWork,
        onJobRequestClick = onJobRequestClick,
        onMercadoPagoClick = {
            navController.navigate(Route.MercadoPagoConnect.path) {
                launchSingleTop = true
            }
        },
        modifier = modifier,
    )
}
