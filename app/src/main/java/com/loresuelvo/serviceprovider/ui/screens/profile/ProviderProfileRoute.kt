package com.loresuelvo.serviceprovider.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileUiState
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileViewModel

@Composable
fun ProviderProfileRoute(
    onBack: () -> Unit,
    onIncompleteProfile: () -> Unit = {},
    onAccountMismatch: () -> Unit = {},
    viewModel: ProviderProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
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
        onBack = onBack,
        onRetry = viewModel::refresh,
    )
}
