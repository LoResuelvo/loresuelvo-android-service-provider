package com.loresuelvo.serviceprovider.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.profile.ProfileIdentityUiState
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileUiState

@Composable
fun ProviderProfileScreen(
    state: ProviderProfileUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
    onRetryPaymentStatus: () -> Unit = {},
    onConnectMercadoPago: () -> Unit = {},
    modifier: Modifier = Modifier,
    identityState: ProfileIdentityUiState = ProfileIdentityUiState(),
    onVerifyIdentity: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(PROVIDER_PROFILE_SCREEN_TAG),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ProfileTopBar(onBack) },
    ) { contentPadding ->
        when (state) {
            ProviderProfileUiState.Loading -> ProfileLoadingState(contentPadding)
            ProviderProfileUiState.Unavailable -> ProfileUnavailableState(
                contentPadding,
                R.string.provider_profile_view_unavailable,
                onRetry,
            )
            ProviderProfileUiState.IncompleteProfile -> ProfileUnavailableState(
                contentPadding,
                R.string.provider_profile_incomplete,
            )
            ProviderProfileUiState.AccountMismatch -> ProfileUnavailableState(
                contentPadding,
                R.string.provider_profile_account_mismatch,
            )
            ProviderProfileUiState.SessionExpired,
            ProviderProfileUiState.Unauthenticated,
            -> ProfileUnavailableState(contentPadding, R.string.welcome_auth_unauthorized_error)
            is ProviderProfileUiState.Ready -> ProfileReadyState(
                contentPadding,
                state,
                onConnectMercadoPago,
                onRetryPaymentStatus,
                identityState,
                onVerifyIdentity,
                onRetry,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.provider_profile_my_profile)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.provider_profile_back),
                )
            }
        },
    )
}

@Composable
private fun ProfileUnavailableState(
    contentPadding: PaddingValues,
    messageRes: Int,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(messageRes))
        if (onRetry != null) {
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.provider_profile_view_retry))
            }
        }
    }
}

@Composable
private fun ProfileLoadingState(
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .testTag(PROVIDER_PROFILE_LOADING_TAG),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(text = stringResource(R.string.provider_profile_view_loading))
    }
}

@Composable
private fun ProfileReadyState(
    contentPadding: PaddingValues,
    state: ProviderProfileUiState.Ready,
    onConnectMercadoPago: () -> Unit,
    onRetryPaymentStatus: () -> Unit,
    identityState: ProfileIdentityUiState,
    onVerifyIdentity: () -> Unit,
    onReloadProfile: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp)
                .testTag(PROVIDER_PROFILE_DATA_TAG),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileSummaryCard(state.provider)
            ProfileAccountCard(state.provider)
            ProfileIdentityCard(state.provider, identityState, onVerifyIdentity, onReloadProfile)
            Text(
                text = stringResource(R.string.provider_profile_connections_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            ProfilePaymentCard(state.payment, onConnectMercadoPago, onRetryPaymentStatus)
            ProfileCalendarCard()
        }
    }
}

const val PROVIDER_PROFILE_SCREEN_TAG = "provider-profile-screen"
const val PROVIDER_PROFILE_LOADING_TAG = "provider-profile-loading"
const val PROVIDER_PROFILE_DATA_TAG = "provider-profile-data"
const val PROVIDER_PROFILE_NAME_TAG = "provider-profile-name"
