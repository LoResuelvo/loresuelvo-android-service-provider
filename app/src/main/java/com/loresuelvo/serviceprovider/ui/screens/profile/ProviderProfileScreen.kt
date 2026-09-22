package com.loresuelvo.serviceprovider.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.ui.components.ProviderAvatar
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderProfileScreen(
    state: ProviderProfileUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(PROVIDER_PROFILE_SCREEN_TAG),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.provider_profile_view_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.provider_profile_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        when (state) {
            ProviderProfileUiState.Loading -> ProfileLoadingState(contentPadding, modifier)
            ProviderProfileUiState.Unavailable -> ProfileUnavailableState(
                contentPadding,
                R.string.provider_profile_view_unavailable,
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
            -> ProfileUnavailableState(contentPadding, R.string.provider_profile_view_unavailable)
            is ProviderProfileUiState.Ready -> ProfileReadyState(contentPadding, state.provider)
        }
    }
}

@Composable
private fun ProfileUnavailableState(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    messageRes: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = stringResource(messageRes))
    }
}

@Composable
private fun ProfileLoadingState(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .testTag(PROVIDER_PROFILE_LOADING_TAG),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ProfileReadyState(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    provider: CurrentAccount.Provider,
) {
    val fullName = "${provider.name} ${provider.surname}".trim()
    val photoDescription = if (provider.profilePhotoUrl.isNullOrBlank()) {
        stringResource(R.string.provider_profile_avatar_description, fullName)
    } else {
        stringResource(R.string.provider_profile_photo_description, fullName)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 96.dp)
            .testTag(PROVIDER_PROFILE_DATA_TAG),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProviderAvatar(
            name = provider.name,
            surname = provider.surname,
            profilePhotoUrl = provider.profilePhotoUrl,
            contentDescription = photoDescription,
            size = 96.dp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            text = fullName,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .semantics { heading() }
                .testTag(PROVIDER_PROFILE_NAME_TAG),
        )
        ProfileDetail(R.string.provider_profile_name_label, provider.name)
        ProfileDetail(R.string.provider_profile_surname_label, provider.surname)
        ProfileDetail(R.string.provider_profile_email_label, provider.email)
        ProfileDetail(R.string.provider_profile_category_label, provider.category.name)
    }
}

@Composable
private fun ProfileDetail(labelRes: Int, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

const val PROVIDER_PROFILE_SCREEN_TAG = "provider-profile-screen"
const val PROVIDER_PROFILE_LOADING_TAG = "provider-profile-loading"
const val PROVIDER_PROFILE_DATA_TAG = "provider-profile-data"
const val PROVIDER_PROFILE_NAME_TAG = "provider-profile-name"
