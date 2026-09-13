package com.loresuelvo.serviceprovider.ui.screens.paymentaccount

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.paymentaccount.MercadoPagoConnectEffect
import com.loresuelvo.serviceprovider.ui.paymentaccount.MercadoPagoConnectViewModel

@Composable
fun MercadoPagoConnectRoute(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: MercadoPagoConnectViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, navController) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MercadoPagoConnectEffect.NavigateToWelcome -> {
                    navController.navigate(Route.Welcome.path) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is MercadoPagoConnectEffect.NavigateToHome -> {
                    navController.navigate(Route.Home.path) {
                        popUpTo(Route.MercadoPagoConnect.path) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    MercadoPagoConnectScreen(
        uiState = uiState,
        onConnectClick = {},
        onContinueWithoutConnecting = viewModel::onContinueWithoutConnecting,
        onContinueHome = viewModel::onContinueHome,
        onRetry = viewModel::checkSessionAndLoadStatus,
        modifier = modifier,
    )
}
