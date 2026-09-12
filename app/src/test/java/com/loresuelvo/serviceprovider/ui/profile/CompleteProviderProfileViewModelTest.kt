package com.loresuelvo.serviceprovider.ui.profile

import app.cash.turbine.test
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.category.CategoryRepository
import com.loresuelvo.serviceprovider.domain.provider.ProviderRegistrationCommand
import com.loresuelvo.serviceprovider.domain.provider.ProviderRepository
import com.loresuelvo.serviceprovider.domain.provider.RegistrationOutcome
import com.loresuelvo.serviceprovider.domain.profile.PhotoValidationOutcome
import com.loresuelvo.serviceprovider.domain.profile.ProfilePhotoPreparer
import com.loresuelvo.serviceprovider.domain.profile.SelectedProfilePhoto
import com.loresuelvo.serviceprovider.domain.usecase.category.GetCategoriesUseCase
import com.loresuelvo.serviceprovider.domain.usecase.profile.PrepareProfilePhotoUseCase
import com.loresuelvo.serviceprovider.domain.usecase.provider.RegisterProviderUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CompleteProviderProfileViewModel].
 *
 * Verifies category loading, form input changes, validation rules,
 * submission deduplication, and effect emission across success/failure paths.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompleteProviderProfileViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var providerRepository: FakeProviderRepository
    private lateinit var sessionStore: RecordingAuthSessionStore
    private lateinit var photoPreparer: FakeProfilePhotoPreparer
    private var viewModel: CompleteProviderProfileViewModel? = null

    private val defaultCategories = listOf(
        Category(id = 1, name = "Plomería"),
        Category(id = 2, name = "Electricidad"),
        Category(id = 3, name = "Gas"),
    )

    private val defaultSession = AuthSession(
        user = User(id = "auth0|provider-1", email = "provider@loresuelvo.test"),
        accessToken = "valid-token-123",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        categoryRepository = FakeCategoryRepository()
        categoryRepository.outcome = CategoriesOutcome.Success(defaultCategories)
        providerRepository = FakeProviderRepository()
        sessionStore = RecordingAuthSessionStore()
        sessionStore.saveSession(defaultSession)
        photoPreparer = FakeProfilePhotoPreparer()
    }

    private fun newViewModel(): CompleteProviderProfileViewModel =
        CompleteProviderProfileViewModel(
            getCategories = GetCategoriesUseCase(categoryRepository),
            registerProvider = RegisterProviderUseCase(providerRepository),
            sessionStore = sessionStore,
            prepareProfilePhoto = PrepareProfilePhotoUseCase(photoPreparer),
        )

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should start with loading state before categories call resolves`() = runTest(scheduler) {
        viewModel = newViewModel()

        viewModel!!.uiState.test {
            assertEquals(CategoriesLoadState.Loading, awaitItem().categoriesState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should load categories into Ready state when backend succeeds`() = runTest(scheduler) {
        viewModel = newViewModel()
        advanceUntilIdle()

        val state = viewModel!!.uiState.value
        assertTrue(state.categoriesState is CategoriesLoadState.Ready)
        val ready = state.categoriesState as CategoriesLoadState.Ready
        assertEquals(3, ready.categories.size)
        assertEquals("Plomería", ready.categories[0].name)
        assertEquals(1, categoryRepository.getCategoriesCalls)
    }

    @Test
    fun `should set categories state to Error when backend fails`() = runTest(scheduler) {
        categoryRepository.outcome = CategoriesOutcome.Failure.Network(RuntimeException("Connection timeout"))

        viewModel = newViewModel()
        advanceUntilIdle()

        val state = viewModel!!.uiState.value
        assertEquals(CategoriesLoadState.Error, state.categoriesState)
    }

    @Test
    fun `should reload categories when retryLoadingCategories is called`() = runTest(scheduler) {
        categoryRepository.outcome = CategoriesOutcome.Failure.Network(RuntimeException("Failed"))

        viewModel = newViewModel()
        advanceUntilIdle()
        assertEquals(CategoriesLoadState.Error, viewModel!!.uiState.value.categoriesState)

        categoryRepository.outcome = CategoriesOutcome.Success(defaultCategories)
        viewModel!!.retryLoadingCategories()
        advanceUntilIdle()

        val state = viewModel!!.uiState.value
        assertTrue(state.categoriesState is CategoriesLoadState.Ready)
        assertEquals(2, categoryRepository.getCategoriesCalls)
    }

    @Test
    fun `should update name and clear previous error when onNameChanged is called`() = runTest(scheduler) {
        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel!!.submit() // triggers MissingName
        assertEquals(ProfileFormError.MissingName, viewModel!!.uiState.value.error)

        viewModel!!.onNameChanged("Carlos")
        assertEquals("Carlos", viewModel!!.uiState.value.name)
        assertNull(viewModel!!.uiState.value.error)
    }

    @Test
    fun `should update surname and clear previous error when onSurnameChanged is called`() = runTest(scheduler) {
        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel!!.onNameChanged("Carlos")
        viewModel!!.submit() // triggers MissingSurname
        assertEquals(ProfileFormError.MissingSurname, viewModel!!.uiState.value.error)

        viewModel!!.onSurnameChanged("Gómez")
        assertEquals("Gómez", viewModel!!.uiState.value.surname)
        assertNull(viewModel!!.uiState.value.error)
    }

    @Test
    fun `should update selected category and clear error when onCategorySelected is called`() = runTest(scheduler) {
        viewModel = newViewModel()
        advanceUntilIdle()

        val category1 = defaultCategories[0]
        val category2 = defaultCategories[1]

        viewModel!!.onCategorySelected(category1)
        assertEquals(category1, viewModel!!.uiState.value.selectedCategory)

        viewModel!!.onCategorySelected(category2)
        assertEquals(category2, viewModel!!.uiState.value.selectedCategory)
    }

    @Test
    fun `should fail validation with MissingName when name is empty`() = runTest(scheduler) {
        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel!!.onSurnameChanged("Gómez")
        viewModel!!.onCategorySelected(defaultCategories[0])

        viewModel!!.submit()

        assertEquals(ProfileFormError.MissingName, viewModel!!.uiState.value.error)
        assertEquals(0, providerRepository.registerCalls)
    }

    @Test
    fun `should fail validation with MissingName when name is whitespace only`() = runTest(scheduler) {
        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel!!.onNameChanged("   ")
        viewModel!!.onSurnameChanged("Gómez")
        viewModel!!.onCategorySelected(defaultCategories[0])

        viewModel!!.submit()

        assertEquals(ProfileFormError.MissingName, viewModel!!.uiState.value.error)
        assertEquals(0, providerRepository.registerCalls)
    }

    @Test
    fun `should fail validation with MissingSurname when surname is empty`() = runTest(scheduler) {
        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel!!.onNameChanged("Carlos")
        viewModel!!.onCategorySelected(defaultCategories[0])

        viewModel!!.submit()

        assertEquals(ProfileFormError.MissingSurname, viewModel!!.uiState.value.error)
        assertEquals(0, providerRepository.registerCalls)
    }

    @Test
    fun `should fail validation with MissingSurname when surname is whitespace only`() = runTest(scheduler) {
        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel!!.onNameChanged("Carlos")
        viewModel!!.onSurnameChanged("   ")
        viewModel!!.onCategorySelected(defaultCategories[0])

        viewModel!!.submit()

        assertEquals(ProfileFormError.MissingSurname, viewModel!!.uiState.value.error)
        assertEquals(0, providerRepository.registerCalls)
    }

    @Test
    fun `should fail validation with MissingCategory when category is not selected`() = runTest(scheduler) {
        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel!!.onNameChanged("Carlos")
        viewModel!!.onSurnameChanged("Gómez")

        viewModel!!.submit()

        assertEquals(ProfileFormError.MissingCategory, viewModel!!.uiState.value.error)
        assertEquals(0, providerRepository.registerCalls)
    }

    @Test
    fun `should emit NavigateToWelcome when submitting without an active session`() = runTest(scheduler) {
        sessionStore.clearSession()

        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel!!.onNameChanged("Carlos")
        viewModel!!.onSurnameChanged("Gómez")
        viewModel!!.onCategorySelected(defaultCategories[0])

        viewModel!!.effects.test {
            viewModel!!.submit()
            advanceUntilIdle()

            assertEquals(CompleteProviderProfileEffect.NavigateToWelcome, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, providerRepository.registerCalls)
    }

    @Test
    fun `should submit successfully and emit NavigateToMercadoPago effect`() = runTest(scheduler) {
        providerRepository.outcome = RegistrationOutcome.Success(providerId = 42)

        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel!!.onNameChanged("Carlos")
        viewModel!!.onSurnameChanged("Gómez")
        viewModel!!.onCategorySelected(defaultCategories[0])

        viewModel!!.effects.test {
            viewModel!!.submit()
            advanceUntilIdle()

            assertEquals(CompleteProviderProfileEffect.NavigateToMercadoPago, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        val state = viewModel!!.uiState.value
        assertEquals(false, state.loading)
        assertNull(state.error)
        assertEquals(1, providerRepository.registerCalls)
        assertEquals("provider@loresuelvo.test", providerRepository.lastCommand?.email)
        assertEquals("Carlos", providerRepository.lastCommand?.name)
        assertEquals("Gómez", providerRepository.lastCommand?.surname)
        assertEquals(1, providerRepository.lastCommand?.categoryId)
    }

    @Test
    fun `should set AlreadyRegistered error when backend reports 409 conflict`() = runTest(scheduler) {
        providerRepository.outcome = RegistrationOutcome.Failure.AlreadyRegistered

        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel!!.onNameChanged("Carlos")
        viewModel!!.onSurnameChanged("Gómez")
        viewModel!!.onCategorySelected(defaultCategories[0])

        viewModel!!.submit()
        advanceUntilIdle()

        val state = viewModel!!.uiState.value
        assertEquals(false, state.loading)
        assertEquals(ProfileFormError.AlreadyRegistered, state.error)
        assertEquals(1, providerRepository.registerCalls)
    }

    @Test
    fun `should clear the session and navigate to Welcome when first registration returns 401`() = runTest(scheduler) {
        providerRepository.outcome = RegistrationOutcome.Failure.Unauthorized

        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel!!.onNameChanged("Carlos")
        viewModel!!.onSurnameChanged("Gómez")
        viewModel!!.onCategorySelected(defaultCategories[0])

        viewModel!!.effects.test {
            viewModel!!.submit()
            advanceUntilIdle()

            assertEquals(CompleteProviderProfileEffect.NavigateToWelcome, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(false, viewModel!!.uiState.value.loading)
        assertNull(sessionStore.getSession())
        assertEquals(1, sessionStore.clearSessionCalls)
    }

    @Test
    fun `should set Network error when registration fails due to network`() = runTest(scheduler) {
        providerRepository.outcome = RegistrationOutcome.Failure.Network(RuntimeException("No connection"))

        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel!!.onNameChanged("Carlos")
        viewModel!!.onSurnameChanged("Gómez")
        viewModel!!.onCategorySelected(defaultCategories[0])

        viewModel!!.submit()
        advanceUntilIdle()

        val state = viewModel!!.uiState.value
        assertEquals(false, state.loading)
        assertTrue(state.error is ProfileFormError.Network)
    }

    @Test
    fun `should set Server error when backend returns 500 error`() = runTest(scheduler) {
        providerRepository.outcome = RegistrationOutcome.Failure.Server(code = 500, message = "Internal Server Error")

        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel!!.onNameChanged("Carlos")
        viewModel!!.onSurnameChanged("Gómez")
        viewModel!!.onCategorySelected(defaultCategories[0])

        viewModel!!.submit()
        advanceUntilIdle()

        val state = viewModel!!.uiState.value
        assertEquals(false, state.loading)
        assertTrue(state.error is ProfileFormError.Server)
        val serverErr = state.error as ProfileFormError.Server
        assertEquals(500, serverErr.code)
    }

    @Test
    fun `should avoid duplicate submission while a request is in flight`() = runTest(scheduler) {
        val inFlightGate = CompletableDeferred<Unit>()
        providerRepository.registerGate = inFlightGate
        providerRepository.outcome = RegistrationOutcome.Success(providerId = 1)

        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel!!.onNameChanged("Carlos")
        viewModel!!.onSurnameChanged("Gómez")
        viewModel!!.onCategorySelected(defaultCategories[0])

        viewModel!!.submit()
        advanceUntilIdle()

        assertTrue(viewModel!!.uiState.value.loading)
        assertEquals(1, providerRepository.registerCalls)

        // Attempt second submission while loading
        viewModel!!.submit()
        advanceUntilIdle()

        assertEquals(1, providerRepository.registerCalls)

        inFlightGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(false, viewModel!!.uiState.value.loading)
    }

    @Test
    fun `should release submission loading when registration is cancelled`() = runTest(scheduler) {
        providerRepository.throwable = CancellationException("Request cancelled")

        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel!!.onNameChanged("Carlos")
        viewModel!!.onSurnameChanged("Gómez")
        viewModel!!.onCategorySelected(defaultCategories[0])

        viewModel!!.submit()
        advanceUntilIdle()

        assertEquals(false, viewModel!!.uiState.value.loading)
        assertNull(viewModel!!.uiState.value.error)
        assertEquals(1, providerRepository.registerCalls)
    }

    @Test
    fun `selecting valid photo updates selectedPhoto and clears error`() = runTest(scheduler) {
        viewModel = newViewModel()
        advanceUntilIdle()

        val validPhoto = SelectedProfilePhoto("photo.jpg", "image/jpeg", 1024L, "/path/photo.jpg")
        photoPreparer.outcome = PhotoValidationOutcome.Valid(validPhoto)

        viewModel!!.onPhotoSelected("content://media/1")
        advanceUntilIdle()

        assertEquals(validPhoto, viewModel!!.uiState.value.selectedPhoto)
        assertNull(viewModel!!.uiState.value.photoError)
        assertEquals(false, viewModel!!.uiState.value.photoLoading)
    }

    @Test
    fun `cancelling photo picker preserves previous selection and causes no error`() = runTest(scheduler) {
        viewModel = newViewModel()
        advanceUntilIdle()

        val validPhoto = SelectedProfilePhoto("photo.jpg", "image/jpeg", 1024L, "/path/photo.jpg")
        photoPreparer.outcome = PhotoValidationOutcome.Valid(validPhoto)
        viewModel!!.onPhotoSelected("content://media/1")
        advanceUntilIdle()

        viewModel!!.onPhotoSelectionCancelled()
        advanceUntilIdle()

        assertEquals(validPhoto, viewModel!!.uiState.value.selectedPhoto)
        assertNull(viewModel!!.uiState.value.photoError)
    }

    @Test
    fun `selecting invalid photo preserves previous photo and sets photo error`() = runTest(scheduler) {
        viewModel = newViewModel()
        advanceUntilIdle()

        val validPhoto = SelectedProfilePhoto("photo.jpg", "image/jpeg", 1024L, "/path/photo.jpg")
        photoPreparer.outcome = PhotoValidationOutcome.Valid(validPhoto)
        viewModel!!.onPhotoSelected("content://media/1")
        advanceUntilIdle()

        photoPreparer.outcome = PhotoValidationOutcome.Invalid.ExceedsMaxSize
        viewModel!!.onPhotoSelected("content://media/too_large")
        advanceUntilIdle()

        assertEquals(validPhoto, viewModel!!.uiState.value.selectedPhoto)
        assertEquals(PhotoFormError.ExceedsMaxSize, viewModel!!.uiState.value.photoError)
    }

    private class FakeCategoryRepository : CategoryRepository {
        var outcome: CategoriesOutcome = CategoriesOutcome.Success(emptyList())
        var getCategoriesCalls: Int = 0
            private set

        override suspend fun getCategories(): CategoriesOutcome {
            getCategoriesCalls += 1
            return outcome
        }
    }

    private class FakeProviderRepository : ProviderRepository {
        var outcome: RegistrationOutcome = RegistrationOutcome.Success(providerId = 1)
        var registerCalls: Int = 0
            private set
        var lastCommand: ProviderRegistrationCommand? = null
            private set
        var registerGate: CompletableDeferred<Unit>? = null
        var throwable: Throwable? = null

        override suspend fun register(command: ProviderRegistrationCommand): RegistrationOutcome {
            registerCalls += 1
            lastCommand = command
            registerGate?.await()
            throwable?.let { throw it }
            return outcome
        }
    }

    private class RecordingAuthSessionStore : AuthSessionStore {
        private val state = MutableStateFlow<AuthSession?>(null)
        var clearSessionCalls: Int = 0
            private set
        override val sessionFlow: StateFlow<AuthSession?> = state

        override fun getSession(): AuthSession? = state.value

        override fun saveSession(session: AuthSession) {
            state.value = session
        }

        override fun clearSession() {
            clearSessionCalls += 1
            state.value = null
        }
    }

    private class FakeProfilePhotoPreparer : ProfilePhotoPreparer {
        var outcome: PhotoValidationOutcome = PhotoValidationOutcome.Valid(
            SelectedProfilePhoto(
                originalName = "test_photo.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 1024L,
                localPath = "/cache/test_photo.jpg",
            ),
        )

        override suspend fun preparePhoto(source: String): PhotoValidationOutcome = outcome

        override suspend fun cleanPhoto(photo: SelectedProfilePhoto) {}
    }
}
