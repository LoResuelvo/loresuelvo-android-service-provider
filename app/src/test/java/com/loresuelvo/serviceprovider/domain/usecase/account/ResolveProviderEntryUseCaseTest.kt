package com.loresuelvo.serviceprovider.domain.usecase.account

import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.account.ProviderEntryOutcome
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveProviderEntryUseCaseTest {

    @Test
    fun returns_unauthenticated_without_calling_current_account() = runTest {
        val repository = RecordingCurrentAccountRepository()
        val store = RecordingSessionStore(null)

        val outcome = ResolveProviderEntryUseCase(store, repository)()

        assertEquals(ProviderEntryOutcome.Unauthenticated, outcome)
        assertEquals(0, repository.calls)
    }

    @Test
    fun returns_provider_account_for_complete_provider() = runTest {
        val account = providerAccount()
        val repository = RecordingCurrentAccountRepository(CurrentAccountOutcome.Success(account))

        val outcome = ResolveProviderEntryUseCase(RecordingSessionStore(session()), repository)()

        assertEquals(ProviderEntryOutcome.Provider(account), outcome)
    }

    @Test
    fun routes_missing_account_to_incomplete_profile_and_keeps_session() = runTest {
        val store = RecordingSessionStore(session())
        val outcome = ResolveProviderEntryUseCase(
            store,
            RecordingCurrentAccountRepository(CurrentAccountOutcome.Failure.NotFound),
        )()

        assertEquals(ProviderEntryOutcome.IncompleteProfile, outcome)
        assertEquals(session(), store.getSession())
    }

    @Test
    fun rejects_consumer_account() = runTest {
        val outcome = ResolveProviderEntryUseCase(
            RecordingSessionStore(session()),
            RecordingCurrentAccountRepository(CurrentAccountOutcome.Success(CurrentAccount.Consumer)),
        )()

        assertEquals(ProviderEntryOutcome.AccountMismatch, outcome)
    }

    @Test
    fun clears_session_once_for_unauthorized_account_lookup() = runTest {
        val store = RecordingSessionStore(session())
        val outcome = ResolveProviderEntryUseCase(
            store,
            RecordingCurrentAccountRepository(CurrentAccountOutcome.Failure.Unauthorized),
        )()

        assertEquals(ProviderEntryOutcome.SessionExpired, outcome)
        assertEquals(1, store.clearCalls)
        assertEquals(null, store.getSession())
    }

    @Test
    fun preserves_session_for_retryable_failure() = runTest {
        val store = RecordingSessionStore(session())
        val outcome = ResolveProviderEntryUseCase(
            store,
            RecordingCurrentAccountRepository(CurrentAccountOutcome.Failure.Server(503)),
        )()

        assertEquals(ProviderEntryOutcome.RetryableFailure, outcome)
        assertEquals(0, store.clearCalls)
        assertEquals(session(), store.getSession())
    }

    private fun providerAccount() = CurrentAccount.Provider(
        id = 20,
        name = "Juan",
        surname = "Gómez",
        email = "juan@example.com",
        category = Category(1, "Plomería"),
        profilePhotoUrl = "https://cdn.example/profile.jpg",
    )

    private fun session() = AuthSession(
        user = User("auth0|provider", "provider@example.com"),
        accessToken = "synthetic-token",
    )

    private class RecordingCurrentAccountRepository(
        private val next: CurrentAccountOutcome = CurrentAccountOutcome.Failure.NotFound,
    ) : CurrentAccountRepository {
        var calls = 0

        override suspend fun getCurrentAccount(): CurrentAccountOutcome {
            calls += 1
            return next
        }
    }

    private class RecordingSessionStore(initial: AuthSession?) : AuthSessionStore {
        private val state = MutableStateFlow(initial)
        override val sessionFlow: StateFlow<AuthSession?> = state
        var clearCalls = 0

        override fun getSession(): AuthSession? = state.value
        override fun saveSession(session: AuthSession) { state.value = session }
        override fun clearSession() {
            clearCalls += 1
            state.value = null
        }
    }
}
