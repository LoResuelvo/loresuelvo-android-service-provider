package com.loresuelvo.serviceprovider.domain.usecase.account

import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.account.ProviderEntryOutcome
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import javax.inject.Inject

class ResolveProviderEntryUseCase @Inject constructor(
    private val sessionStore: AuthSessionStore,
    private val currentAccountRepository: CurrentAccountRepository,
) {
    suspend operator fun invoke(): ProviderEntryOutcome {
        val activeSession = sessionStore.getSession() ?: return ProviderEntryOutcome.Unauthenticated
        val outcome = currentAccountRepository.getCurrentAccount()
        if (sessionStore.getSession() != activeSession) return ProviderEntryOutcome.Unauthenticated

        return when (outcome) {
            is CurrentAccountOutcome.Success -> when (val account = outcome.account) {
                is CurrentAccount.Provider -> ProviderEntryOutcome.Provider(account)
                is CurrentAccount.Consumer -> ProviderEntryOutcome.AccountMismatch
            }
            CurrentAccountOutcome.Failure.NotFound -> ProviderEntryOutcome.IncompleteProfile
            CurrentAccountOutcome.Failure.Unauthorized -> {
                sessionStore.clearSession()
                ProviderEntryOutcome.SessionExpired
            }
            is CurrentAccountOutcome.Failure.Network,
            is CurrentAccountOutcome.Failure.Server,
            CurrentAccountOutcome.Failure.Invalid,
            -> ProviderEntryOutcome.RetryableFailure
        }
    }
}
