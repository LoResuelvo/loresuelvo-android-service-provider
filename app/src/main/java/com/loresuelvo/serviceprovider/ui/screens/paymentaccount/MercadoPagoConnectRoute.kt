package com.loresuelvo.serviceprovider.ui.screens.paymentaccount

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.loresuelvo.serviceprovider.platform.paymentaccount.ExternalPaymentAccountBrowserLauncher
import com.loresuelvo.serviceprovider.platform.paymentaccount.PaymentAccountBrowserLauncher
import com.loresuelvo.serviceprovider.ui.paymentaccount.MercadoPagoConnectEffect
import com.loresuelvo.serviceprovider.ui.paymentaccount.MercadoPagoConnectViewModel

@Composable
fun MercadoPagoConnectRoute(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    browserLauncher: PaymentAccountBrowserLauncher = remember { ExternalPaymentAccountBrowserLauncher() },
    viewModel: MercadoPagoConnectViewModel = hiltViewModel(),
    onHomeRequested: () -> Unit = {},
    onWelcomeRequested: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel, navController, browserLauncher) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MercadoPagoConnectEffect.NavigateToWelcome -> {
                    onWelcomeRequested()
                }
                is MercadoPagoConnectEffect.NavigateToHome -> {
                    onHomeRequested()
                }
                is MercadoPagoConnectEffect.LaunchBrowser -> {
                    val success = browserLauncher.launch(context, effect.url)
                    if (!success) {
                        viewModel.onBrowserLaunchFailed()
                    }
                }
            }
        }
    }

    MercadoPagoConnectScreen(
        uiState = uiState,
        onConnectClick = viewModel::onConnectClick,
        onContinueWithoutConnecting = viewModel::onContinueWithoutConnecting,
        onContinueHome = viewModel::onContinueHome,
        onRetry = viewModel::checkSessionAndLoadStatus,
        modifier = modifier,
    )
}
