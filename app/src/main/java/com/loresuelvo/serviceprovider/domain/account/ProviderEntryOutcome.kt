package com.loresuelvo.serviceprovider.domain.account

sealed interface ProviderEntryOutcome {
    data object Unauthenticated : ProviderEntryOutcome
    data class Provider(val account: CurrentAccount.Provider) : ProviderEntryOutcome
    data object IncompleteProfile : ProviderEntryOutcome
    data object AccountMismatch : ProviderEntryOutcome
    data object SessionExpired : ProviderEntryOutcome
    data object RetryableFailure : ProviderEntryOutcome
}
