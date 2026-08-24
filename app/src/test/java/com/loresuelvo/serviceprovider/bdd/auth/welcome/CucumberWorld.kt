package com.loresuelvo.serviceprovider.bdd.auth.welcome

import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.ui.auth.WelcomeUiState
import com.loresuelvo.serviceprovider.ui.auth.WelcomeViewModel
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val context = mockk<android.content.Context>(relaxed = true)

    val authProvider = FakeAuthProvider()
    val categoryRepository = FakeCategoryRepository()
    private val getCategories = com.loresuelvo.serviceprovider.domain.usecase.category.GetCategoriesUseCase(
        categoryRepository,
    )

    private lateinit var viewModel: WelcomeViewModel

    init {
        Dispatchers.setMain(dispatcher)
    }

    fun buildViewModel() {
        viewModel = WelcomeViewModel(authProvider, getCategories)
    }

    fun seedNoSession() {
        buildViewModel()
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
        viewModel.signup(context)
        scheduler.advanceUntilIdle()
    }

    fun triggerLogin() {
        viewModel.login(context)
        scheduler.advanceUntilIdle()
    }

    fun triggerGoogle() {
        viewModel.loginWithGoogle(context)
        scheduler.advanceUntilIdle()
    }

    fun queuedSignupOutcome(): AuthenticationOutcome {
        // The Fake returns `nextOutcome`; we only assert what the VM
        // delegated (calls counter) — this helper exists for future
        // steps that want to peek at the next scripted outcome.
        return authProvider.nextOutcome
    }

    override fun close() {
        Dispatchers.resetMain()
    }

    @Suppress("unused")
    private fun sampleSession(): AuthSession = AuthSession(
        user = User(id = "auth0|seed", email = "seed@example.com"),
        accessToken = "seed-token",
    )
}