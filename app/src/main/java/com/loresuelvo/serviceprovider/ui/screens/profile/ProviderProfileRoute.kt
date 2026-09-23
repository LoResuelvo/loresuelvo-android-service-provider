package com.loresuelvo.serviceprovider.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.loresuelvo.serviceprovider.platform.identity.IdentityVerificationLauncher
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileUiState
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileViewModel
import com.loresuelvo.serviceprovider.ui.screens.identity.findActivity

@Composable
fun ProviderProfileRoute(
    onBack: () -> Unit,
    identityLauncher: IdentityVerificationLauncher,
    returnRefreshKey: Int = 0,
    onIncompleteProfile: () -> Unit = {},
    onAccountMismatch: () -> Unit = {},
    onConnectMercadoPago: () -> Unit = {},
    viewModel: ProviderProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val identityState by viewModel.identityState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(activity, identityLauncher, viewModel) {
        identityLauncher.attach(activity)
        onDispose {
            identityLauncher.detach(activity)
            if (!activity.isChangingConfigurations) viewModel.leaveProfile()
        }
    }
    LaunchedEffect(viewModel, identityLauncher, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.identityLaunches.collect { launch ->
                if (viewModel.claimIdentityLaunch(launch.attemptId)) {
                    identityLauncher.launch(launch.credential) { result ->
                        viewModel.onIdentityResult(launch.attemptId, result)
                    }
                }
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onProfileResumed()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onProfilePaused() }
    LaunchedEffect(returnRefreshKey) {
        if (returnRefreshKey > 0) viewModel.refresh()
    }
    LaunchedEffect(state) {
        when (state) {
            ProviderProfileUiState.IncompleteProfile -> onIncompleteProfile()
            ProviderProfileUiState.AccountMismatch -> onAccountMismatch()
            else -> Unit
        }
    }
    BackHandler(onBack = onBack)

    ProviderProfileScreen(
        state = state,
        identityState = identityState,
        onVerifyIdentity = viewModel::verifyIdentity,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onRetryPaymentStatus = viewModel::retryPaymentStatus,
        onConnectMercadoPago = onConnectMercadoPago,
    )
}
