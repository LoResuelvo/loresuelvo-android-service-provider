package com.loresuelvo.serviceprovider.bdd.auth.welcome

import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.usecase.auth.EstablishAuthSessionUseCase
import com.loresuelvo.serviceprovider.ui.auth.WelcomeUiState
import com.loresuelvo.serviceprovider.ui.auth.WelcomeViewModel
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Owns the BDD fixtures and the coroutine machinery for the
 * `provider-welcome.feature` scenarios. Each scenario uses the same
 * single [WelcomeViewModel] instance, so any state built by a `Given`
 * step is observable by the subsequent `When` / `Then` steps.
 *
 * The `TestCoroutineScheduler` is the single source of truth for
 * `viewModelScope`; `Dispatchers.setMain(dispatcher)` redirects the
 * Main dispatcher to it so `viewModelScope.launch { ... }` runs
 * deterministically. `advanceUntilIdle()` flushes pending coroutines
 * after every action so the assertions in the `Then` step always see
 * the post-action state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CucumberWorld : AutoCloseable {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val effectScope = CoroutineScope(dispatcher)
    val authenticationLauncher = FakeAuthenticationLauncher()
    val categoryRepository = FakeCategoryRepository()
    private val sessionStore = mockk<AuthSessionStore>(relaxed = true)
    private val getCategories = com.loresuelvo.serviceprovider.domain.usecase.category.GetCategoriesUseCase(
        categoryRepository,
    )

    private lateinit var viewModel: WelcomeViewModel

    init {
        Dispatchers.setMain(dispatcher)
    }

    fun buildViewModel() {
        viewModel = WelcomeViewModel(
            getCategories = getCategories,
            establishAuthSession = EstablishAuthSessionUseCase(sessionStore),
        )
    }

    fun seedNoSession() {
        buildViewModel()
        // This collector is the test equivalent of WelcomeRoute: it owns the
        // route/platform seam and passes only pure outcomes back to the VM.
        effectScope.launch {
            viewModel.effects.collect { effect ->
                if (effect is com.loresuelvo.serviceprovider.ui.auth.WelcomeEffect.LaunchAuthentication) {
                    viewModel.onAuthenticationResult(authenticationLauncher.launch(effect.action))
                }
            }
        }
    }

    fun configureBackendCategories(outcome: com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome) {
        categoryRepository.nextOutcome = outcome
        // The VM already loaded once in `init {}`; trigger a second
        // load so the configured outcome is consumed by the VM.
        viewModel.loadCategories()
        scheduler.advanceUntilIdle()
    }

    fun openApp() {
        // The VM constructor already kicks off `loadCategories()`. Wait
        // for the scheduler to drain so any `init` work is observable.
        scheduler.advanceUntilIdle()
    }

    fun state(): WelcomeUiState = viewModel.uiState.value

    fun triggerSignup() {
        viewModel.signup()
        scheduler.advanceUntilIdle()
    }

    fun triggerLogin() {
        viewModel.login()
        scheduler.advanceUntilIdle()
    }

    fun triggerGoogle() {
        viewModel.loginWithGoogle()
        scheduler.advanceUntilIdle()
    }

    fun queuedSignupOutcome(): AuthenticationOutcome {
        // The Fake returns `nextOutcome`; we only assert what the VM
        // delegated (calls counter) — this helper exists for future
        // steps that want to peek at the next scripted outcome.
        return authenticationLauncher.nextOutcome
    }

    override fun close() {
        effectScope.cancel()
        Dispatchers.resetMain()
    }

    @Suppress("unused")
    private fun sampleSession(): AuthSession = AuthSession(
        user = User(id = "auth0|seed", email = "seed@example.com"),
        accessToken = "seed-token",
    )
}
