package com.loresuelvo.serviceprovider.ui.screens.paymentaccount

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import com.loresuelvo.serviceprovider.platform.paymentaccount.ExternalPaymentAccountBrowserLauncher
import com.loresuelvo.serviceprovider.platform.paymentaccount.PaymentAccountBrowserLauncher
import com.loresuelvo.serviceprovider.platform.paymentaccount.PaymentAccountReturnHint
import com.loresuelvo.serviceprovider.ui.paymentaccount.MercadoPagoConnectEffect
import com.loresuelvo.serviceprovider.ui.paymentaccount.MercadoPagoConnectViewModel

@Composable
fun MercadoPagoConnectRoute(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    browserLauncher: PaymentAccountBrowserLauncher = remember { ExternalPaymentAccountBrowserLauncher() },
    fromProfile: Boolean = false,
    returnHint: PaymentAccountReturnHint? = null,
    onReturnHintConsumed: () -> Unit = {},
    onProfileRequested: () -> Unit = {},
    viewModel: MercadoPagoConnectViewModel = hiltViewModel(),
    onHomeRequested: () -> Unit = {},
    onWelcomeRequested: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var browserLaunched by rememberSaveable { mutableStateOf(false) }
    var browserReturnPending by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (browserLaunched) {
            browserLaunched = false
            browserReturnPending = true
        }
    }
    LaunchedEffect(browserReturnPending) {
        if (!browserReturnPending) return@LaunchedEffect
        browserReturnPending = false
        if (fromProfile) onProfileRequested() else if (returnHint == null) viewModel.onResumeFromBrowser()
    }
    LaunchedEffect(returnHint) {
        when (returnHint) {
            PaymentAccountReturnHint.Success -> viewModel.onReturnViaSuccessLink()
            PaymentAccountReturnHint.Cancelled -> viewModel.onReturnViaCancellationLink()
            null -> return@LaunchedEffect
        }
        browserLaunched = false
        onReturnHintConsumed()
    }

    LaunchedEffect(viewModel, navController, browserLauncher) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MercadoPagoConnectEffect.NavigateToWelcome -> {
                    onWelcomeRequested()
                }
                is MercadoPagoConnectEffect.NavigateToHome -> {
                    if (fromProfile) onProfileRequested() else onHomeRequested()
                }
                is MercadoPagoConnectEffect.LaunchBrowser -> {
                    browserLaunched = true
                    val success = browserLauncher.launch(context, effect.url)
                    if (!success) {
                        browserLaunched = false
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
        returnToProfile = fromProfile,
        modifier = modifier,
    )
}
