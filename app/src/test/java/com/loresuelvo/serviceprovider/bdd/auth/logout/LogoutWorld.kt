package com.loresuelvo.serviceprovider.bdd.auth.logout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModel
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.LogoutOutcome
import com.loresuelvo.serviceprovider.domain.auth.SessionClearOutcome
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.usecase.account.ResolveProviderEntryUseCase
import com.loresuelvo.serviceprovider.domain.usecase.auth.EstablishAuthSessionUseCase
import com.loresuelvo.serviceprovider.ui.entry.ProviderEntryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** One production root, with deterministic persistence and browser boundaries. */
@OptIn(ExperimentalCoroutinesApi::class)
class LogoutWorld : AutoCloseable {
    val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val owners = ViewModelStore()
    private var ownerCount = 0
    val session = AuthSession(User("auth0|logout-provider", "provider@example.test"), "synthetic-token")
    val store = LogoutSessionStore()
    val savedState = SavedStateHandle()
    var browserCalls = 0
        private set
    var nextBrowserOutcome: LogoutOutcome = LogoutOutcome.Success
    var accountOutcome: suspend () -> CurrentAccountOutcome = {
        CurrentAccountOutcome.Success(CurrentAccount.Provider(
            1, "Carlos", "Gomez", "provider@example.test", Category(1, "Plumbing"), null,
        ))
    }
    private val repository = object : CurrentAccountRepository {
        override suspend fun getCurrentAccount() = accountOutcome()
    }
    val viewModel: ProviderEntryViewModel

    init {
        Dispatchers.setMain(dispatcher)
        EstablishAuthSessionUseCase(store)(session)
        viewModel = createRoot(savedState)
        drain()
    }

    fun createRoot(state: SavedStateHandle = SavedStateHandle()): ProviderEntryViewModel =
        ProviderEntryViewModel(store, ResolveProviderEntryUseCase(store, repository), state).also {
            own(it)
        }

    fun own(model: ViewModel) { owners.put("owner-${ownerCount++}", model) }

    fun drain() = scheduler.advanceUntilIdle()
    fun openConfirmation() = viewModel.requestLogout()
    fun confirm() {
        viewModel.confirmLogout()
        drain()
        completeBrowser()
    }
    fun completeBrowser() {
        val id = viewModel.logoutState.value.launchId ?: return
        if (viewModel.claimLogoutLaunch(id)) {
            browserCalls += 1
            viewModel.onLogoutResult(id, nextBrowserOutcome)
        }
        drain()
    }
    override fun close() {
        owners.clear()
        drain()
        Dispatchers.resetMain()
    }
}

class LogoutSessionStore : AuthSessionStore {
    private val state = MutableStateFlow<AuthSession?>(null)
    override val sessionFlow = state
    var persisted: AuthSession? = null
    var removalFails = false
    var clearCalls = 0
    override fun getSession() = state.value
    override fun saveSession(session: AuthSession) {
        persisted = session
        state.value = session
    }
    override fun clearSession() { clearSessionDurably() }
    override fun clearSessionDurably(): SessionClearOutcome {
        clearCalls += 1
        state.value = null
        if (removalFails) return SessionClearOutcome.PersistenceFailure
        persisted = null
        return SessionClearOutcome.Cleared
    }
}
