package com.loresuelvo.serviceprovider.bdd.profile

import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.category.CategoryRepository
import com.loresuelvo.serviceprovider.domain.provider.ProviderRegistrationCommand
import com.loresuelvo.serviceprovider.domain.provider.ProviderRepository
import com.loresuelvo.serviceprovider.domain.provider.RegistrationOutcome
import com.loresuelvo.serviceprovider.domain.usecase.category.GetCategoriesUseCase
import com.loresuelvo.serviceprovider.domain.usecase.provider.RegisterProviderUseCase
import com.loresuelvo.serviceprovider.ui.profile.CategoriesLoadState
import com.loresuelvo.serviceprovider.ui.profile.CompleteProviderProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Deterministic world for the provider profile BDD scenarios.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompleteProviderProfileWorld : AutoCloseable {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    private val categoryRepository = FakeCategoryRepository()
    private val providerRepository = FakeProviderRepository()
    private val sessionStore = FakeAuthSessionStore()

    private val getCategories = GetCategoriesUseCase(categoryRepository)
    private val registerProvider = RegisterProviderUseCase(providerRepository)

    lateinit var viewModel: CompleteProviderProfileViewModel
        private set

    init {
        Dispatchers.setMain(dispatcher)
    }

    fun seedAuthenticatedSession() {
        sessionStore.saveSession(
            AuthSession(
                user = User(id = "auth0|provider-1", email = "provider@loresuelvo.test"),
                accessToken = "valid-token-123",
            ),
        )
    }

    fun configureApiCategories(categories: List<Category>) {
        categoryRepository.outcome = CategoriesOutcome.Success(categories)
    }

    fun configureCategoryApiFailure() {
        categoryRepository.outcome = CategoriesOutcome.Failure.Network(RuntimeException("Network error"))
    }

    fun navigateToProfileDestination() {
        viewModel = CompleteProviderProfileViewModel(
            getCategories = getCategories,
            registerProvider = registerProvider,
            sessionStore = sessionStore,
        )
        scheduler.advanceUntilIdle()
    }

    fun assertFormDisplaysFields() {
        check(::viewModel.isInitialized)
        val state = viewModel.uiState.value
        assertNotNull("Name field state must be present", state.name)
        assertNotNull("Surname field state must be present", state.surname)
    }

    fun assertCategoriesLoadedFromApi() {
        check(::viewModel.isInitialized)
        val state = viewModel.uiState.value
        assertTrue(
            "Expected CategoriesLoadState.Ready but got ${state.categoriesState}",
            state.categoriesState is CategoriesLoadState.Ready,
        )
        val ready = state.categoriesState as CategoriesLoadState.Ready
        assertTrue("Categories list should not be empty", ready.categories.isNotEmpty())
    }

    fun assertFriendlyCategoriesError() {
        check(::viewModel.isInitialized)
        val state = viewModel.uiState.value
        assertEquals(
            "Expected CategoriesLoadState.Error",
            CategoriesLoadState.Error,
            state.categoriesState,
        )
    }

    fun retryCategoryLoading() {
        check(::viewModel.isInitialized)
        categoryRepository.outcome = CategoriesOutcome.Success(
            listOf(Category(1, "Plomería"), Category(2, "Gas")),
        )
        viewModel.retryLoadingCategories()
        scheduler.advanceUntilIdle()
    }

    fun assertCategoryRetryAvailable() {
        check(::viewModel.isInitialized)
        val state = viewModel.uiState.value
        assertTrue(
            "Categories should be loaded after retry",
            state.categoriesState is CategoriesLoadState.Ready,
        )
    }

    override fun close() {
        scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private class FakeCategoryRepository : CategoryRepository {
        var outcome: CategoriesOutcome = CategoriesOutcome.Success(
            listOf(
                Category(1, "Plomería"),
                Category(2, "Electricidad"),
                Category(3, "Pintura"),
            ),
        )

        override suspend fun getCategories(): CategoriesOutcome = outcome
    }

    private class FakeProviderRepository : ProviderRepository {
        var outcome: RegistrationOutcome = RegistrationOutcome.Success(providerId = 1)

        override suspend fun register(command: ProviderRegistrationCommand): RegistrationOutcome = outcome
    }

    private class FakeAuthSessionStore : AuthSessionStore {
        private val _session = MutableStateFlow<AuthSession?>(null)
        override val sessionFlow: StateFlow<AuthSession?> = _session

        override fun getSession(): AuthSession? = _session.value

        override fun saveSession(session: AuthSession) {
            _session.value = session
        }

        override fun clearSession() {
            _session.value = null
        }
    }
}
