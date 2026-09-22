package com.loresuelvo.serviceprovider.ui.profile

import com.loresuelvo.serviceprovider.domain.account.CurrentAccount

sealed interface ProviderProfileUiState {
    data object Loading : ProviderProfileUiState
    data class Ready(val provider: CurrentAccount.Provider) : ProviderProfileUiState
    data object Unavailable : ProviderProfileUiState
    data object IncompleteProfile : ProviderProfileUiState
    data object AccountMismatch : ProviderProfileUiState
    data object SessionExpired : ProviderProfileUiState
    data object Unauthenticated : ProviderProfileUiState
}
