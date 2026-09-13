package com.loresuelvo.serviceprovider.ui.entry

import com.loresuelvo.serviceprovider.domain.account.CurrentAccount

sealed interface ProviderEntryUiState {
    data object Loading : ProviderEntryUiState
    data object Welcome : ProviderEntryUiState
    data object CompleteProviderProfile : ProviderEntryUiState
    data class Home(val account: CurrentAccount.Provider) : ProviderEntryUiState
    data object AccountMismatch : ProviderEntryUiState
    data object RetryableError : ProviderEntryUiState
}
