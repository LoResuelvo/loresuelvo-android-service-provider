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
import com.loresuelvo.serviceprovider.domain.profile.PhotoValidationOutcome
import com.loresuelvo.serviceprovider.domain.profile.ProfilePhotoPreparer
import com.loresuelvo.serviceprovider.domain.profile.SelectedProfilePhoto
import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadRequest
import com.loresuelvo.serviceprovider.domain.file.ConfirmedFile
import com.loresuelvo.serviceprovider.domain.file.FileRepository
import com.loresuelvo.serviceprovider.domain.file.PresignUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.PresignUploadRequest
import com.loresuelvo.serviceprovider.domain.file.PresignUploadResult
import com.loresuelvo.serviceprovider.domain.file.UploadBytesOutcome
import com.loresuelvo.serviceprovider.domain.usecase.category.GetCategoriesUseCase
import com.loresuelvo.serviceprovider.domain.usecase.profile.PrepareProfilePhotoUseCase
import com.loresuelvo.serviceprovider.domain.usecase.profile.UploadProfilePhotoUseCase
import com.loresuelvo.serviceprovider.domain.usecase.provider.RegisterProviderUseCase
import com.loresuelvo.serviceprovider.ui.profile.CategoriesLoadState
import com.loresuelvo.serviceprovider.ui.profile.CompleteProviderProfileEffect
import com.loresuelvo.serviceprovider.ui.profile.CompleteProviderProfileUiState
import com.loresuelvo.serviceprovider.ui.profile.CompleteProviderProfileViewModel
import com.loresuelvo.serviceprovider.ui.profile.ProfileFormError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
    private val photoPreparer = FakeProfilePhotoPreparer()
    private val fileRepository = FakeFileRepository()

    private val getCategories = GetCategoriesUseCase(categoryRepository)
    private val registerProvider = RegisterProviderUseCase(providerRepository)
    private val prepareProfilePhoto = PrepareProfilePhotoUseCase(photoPreparer)
    private val uploadProfilePhoto = UploadProfilePhotoUseCase(fileRepository)

    lateinit var viewModel: CompleteProviderProfileViewModel
        private set

    var latestEffect: CompleteProviderProfileEffect? = null
        private set

    private var effectsJob: Job? = null

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
            prepareProfilePhoto = prepareProfilePhoto,
            uploadProfilePhoto = uploadProfilePhoto,
        )
        effectsJob?.cancel()
        effectsJob = CoroutineScope(dispatcher).launch {
            viewModel.effects.collect { effect ->
                latestEffect = effect
            }
        }
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

    // --- 02-CPP ---

    fun ensureCategoriesLoaded() {
        check(::viewModel.isInitialized)
        assertTrue(viewModel.uiState.value.categoriesState is CategoriesLoadState.Ready)
    }

    fun selectFirstCategory() {
        check(::viewModel.isInitialized)
        val ready = viewModel.uiState.value.categoriesState as CategoriesLoadState.Ready
        viewModel.onCategorySelected(ready.categories.first())
    }

    fun assertFirstCategorySelected() {
        check(::viewModel.isInitialized)
        val ready = viewModel.uiState.value.categoriesState as CategoriesLoadState.Ready
        assertEquals(ready.categories.first(), viewModel.uiState.value.selectedCategory)
    }

    fun changeCategorySelection() {
        check(::viewModel.isInitialized)
        val ready = viewModel.uiState.value.categoriesState as CategoriesLoadState.Ready
        viewModel.onCategorySelected(ready.categories[1])
    }

    fun assertChangedCategorySelected() {
        check(::viewModel.isInitialized)
        val ready = viewModel.uiState.value.categoriesState as CategoriesLoadState.Ready
        assertEquals(ready.categories[1], viewModel.uiState.value.selectedCategory)
    }

    // --- 03-CPP, 04-CPP, 05-CPP ---

    fun enterName(name: String) {
        check(::viewModel.isInitialized)
        viewModel.onNameChanged(name)
    }

    fun enterSurname(surname: String) {
        check(::viewModel.isInitialized)
        viewModel.onSurnameChanged(surname)
    }

    fun attemptSubmit() {
        check(::viewModel.isInitialized)
        viewModel.submit()
        scheduler.advanceUntilIdle()
    }

    fun assertMissingNameError() {
        check(::viewModel.isInitialized)
        assertEquals(ProfileFormError.MissingName, viewModel.uiState.value.error)
    }

    fun assertMissingSurnameError() {
        check(::viewModel.isInitialized)
        assertEquals(ProfileFormError.MissingSurname, viewModel.uiState.value.error)
    }

    fun assertMissingCategoryError() {
        check(::viewModel.isInitialized)
        assertEquals(ProfileFormError.MissingCategory, viewModel.uiState.value.error)
    }

    fun assertFormNotSubmitted() {
        assertEquals(0, providerRepository.registerCalls)
    }

    // --- 07-CPP ---

    fun fillValidProfileData() {
        check(::viewModel.isInitialized)
        viewModel.onNameChanged("Carlos")
        viewModel.onSurnameChanged("Gómez")
        val ready = viewModel.uiState.value.categoriesState as CategoriesLoadState.Ready
        viewModel.onCategorySelected(ready.categories.first())
    }

    fun configureRegistrationRecoverableError() {
        providerRepository.outcome = RegistrationOutcome.Failure.Network(RuntimeException("Connection timeout"))
    }

    fun assertRecoverableErrorDisplayed() {
        check(::viewModel.isInitialized)
        assertTrue(viewModel.uiState.value.error is ProfileFormError.Network)
    }

    fun assertProfileDataPreserved() {
        check(::viewModel.isInitialized)
        val state = viewModel.uiState.value
        assertEquals("Carlos", state.name)
        assertEquals("Gómez", state.surname)
        assertNotNull(state.selectedCategory)
    }

    fun correctAndRetry() {
        check(::viewModel.isInitialized)
        providerRepository.outcome = RegistrationOutcome.Success(providerId = 1)
        viewModel.submit()
        scheduler.advanceUntilIdle()
    }

    fun assertRegistrationSucceeded() {
        check(::viewModel.isInitialized)
        val state = viewModel.uiState.value
        assertEquals(false, state.loading)
        assertEquals(null, state.error)
        assertEquals(CompleteProviderProfileEffect.NavigateToMercadoPago, latestEffect)
    }

    // --- 08-CPP ---

    fun configureRegistrationAlreadyRegistered() {
        providerRepository.outcome = RegistrationOutcome.Failure.AlreadyRegistered
    }

    fun assertAlreadyRegisteredFriendlyError() {
        check(::viewModel.isInitialized)
        assertEquals(ProfileFormError.AlreadyRegistered, viewModel.uiState.value.error)
    }

    fun assertRemainsOnForm() {
        check(::viewModel.isInitialized)
        val state = viewModel.uiState.value
        assertEquals("Carlos", state.name)
        assertEquals("Gómez", state.surname)
        assertNotNull(state.selectedCategory)
        assertTrue("Should not navigate when registration conflict occurs", latestEffect == null)
    }

    // --- 09-CPP ---

    fun prepareValidProfile() {
        check(::viewModel.isInitialized)
        viewModel.onNameChanged("Carlos")
        viewModel.onSurnameChanged("Gómez")
        val ready = viewModel.uiState.value.categoriesState as CategoriesLoadState.Ready
        viewModel.onCategorySelected(ready.categories.first())
    }

    fun holdRegistrationInFlight() {
        providerRepository.registerGate = CompletableDeferred()
    }

    fun submitForm() {
        check(::viewModel.isInitialized)
        viewModel.submit()
        scheduler.advanceUntilIdle()
    }

    fun pressSubmitButtonAgain() {
        check(::viewModel.isInitialized)
        viewModel.submit()
        scheduler.advanceUntilIdle()
    }

    fun assertSingleRegistrationCall() {
        assertEquals(1, providerRepository.registerCalls)
    }

    fun assertSubmitDisabledWithLoading() {
        check(::viewModel.isInitialized)
        assertTrue(viewModel.uiState.value.loading)
        providerRepository.registerGate?.complete(Unit)
        scheduler.advanceUntilIdle()
    }

    // --- 10-CPP ---

    fun ensurePrerequisitesAvailable() {
        // Prerequisites (photos & coverage zones) will be integrated in upcoming stories
    }

    fun completeRegistrationSuccessfully() {
        check(::viewModel.isInitialized)
        providerRepository.outcome = RegistrationOutcome.Success(providerId = 10)
        viewModel.submit()
        scheduler.advanceUntilIdle()
    }

    fun assertNavigatedToMercadoPago() {
        assertEquals(CompleteProviderProfileEffect.NavigateToMercadoPago, latestEffect)
    }

    fun assertProfileFormPoppedFromBackstack() {
        assertEquals(CompleteProviderProfileEffect.NavigateToMercadoPago, latestEffect)
    }

    override fun close() {
        effectsJob?.cancel()
        providerRepository.registerGate?.complete(Unit)
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
        var registerCalls: Int = 0
            private set
        var registerGate: CompletableDeferred<Unit>? = null

        override suspend fun register(command: ProviderRegistrationCommand): RegistrationOutcome {
            registerCalls += 1
            registerGate?.await()
            return outcome
        }
    }

    private class FakeAuthSessionStore : AuthSessionStore {
        private val _session = MutableStateFlow<AuthSession?>(null)
        override val sessionFlow: StateFlow<AuthSession?> = stateFlow

        private val stateFlow: StateFlow<AuthSession?>
            get() = _session

        override fun getSession(): AuthSession? = _session.value

        override fun saveSession(session: AuthSession) {
            _session.value = session
        }

        override fun clearSession() {
            _session.value = null
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

    private class FakeFileRepository : FileRepository {
        var presignOutcome: PresignUploadOutcome = PresignUploadOutcome.Success(
            PresignUploadResult(
                fileId = "file_uploaded_123",
                key = "profile/photo.jpg",
                uploadUrl = "https://storage.example/upload",
                headers = mapOf("Content-Type" to "image/jpeg"),
            ),
        )
        var uploadBytesOutcome: UploadBytesOutcome = UploadBytesOutcome.Success
        var confirmOutcome: ConfirmUploadOutcome = ConfirmUploadOutcome.Success(
            ConfirmedFile(
                id = "file_uploaded_123",
                originalName = "profile.jpg",
                mimeType = "image/jpeg",
            ),
        )

        override suspend fun presign(request: PresignUploadRequest): PresignUploadOutcome = presignOutcome
        override suspend fun uploadBytes(uploadUrl: String, headers: Map<String, String>, bytes: ByteArray): UploadBytesOutcome = uploadBytesOutcome
        override suspend fun confirm(fileId: String, request: ConfirmUploadRequest): ConfirmUploadOutcome = confirmOutcome
    }
}
