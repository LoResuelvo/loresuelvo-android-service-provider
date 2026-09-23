package com.loresuelvo.serviceprovider.ui.profile

import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationCredential
import com.loresuelvo.serviceprovider.ui.identity.IdentityVerificationFeedback

sealed interface ProviderProfileUiState {
    data object Loading : ProviderProfileUiState
    data class Ready(
        val provider: CurrentAccount.Provider,
        val payment: ProfilePaymentState = ProfilePaymentState.Loading,
    ) : ProviderProfileUiState
    data object Unavailable : ProviderProfileUiState
    data object IncompleteProfile : ProviderProfileUiState
    data object AccountMismatch : ProviderProfileUiState
    data object SessionExpired : ProviderProfileUiState
    data object Unauthenticated : ProviderProfileUiState
}

sealed interface ProfilePaymentState {
    data object Loading : ProfilePaymentState
    data object Pending : ProfilePaymentState
    data object Connected : ProfilePaymentState
    data object Unavailable : ProfilePaymentState
}

data class ProfileIdentityUiState(
    val loading: Boolean = false,
    val feedback: IdentityVerificationFeedback? = null,
)

class ProfileIdentityLaunch(
    val attemptId: Long,
    val credential: IdentityVerificationCredential,
)
