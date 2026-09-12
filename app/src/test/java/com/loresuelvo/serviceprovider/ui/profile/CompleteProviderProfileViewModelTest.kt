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
import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadRequest
import com.loresuelvo.serviceprovider.domain.file.ConfirmedFile
import com.loresuelvo.serviceprovider.domain.file.FileRepository
import com.loresuelvo.serviceprovider.domain.file.PresignUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.PresignUploadRequest
import com.loresuelvo.serviceprovider.domain.file.PresignUploadResult
import com.loresuelvo.serviceprovider.domain.file.UploadBytesOutcome
import com.loresuelvo.serviceprovider.domain.profile.SelectedProfilePhoto
import com.loresuelvo.serviceprovider.domain.usecase.category.GetCategoriesUseCase
import com.loresuelvo.serviceprovider.domain.usecase.profile.PrepareProfilePhotoUseCase
import com.loresuelvo.serviceprovider.domain.usecase.profile.UploadProfilePhotoUseCase
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
import kotlinx.coroutines.test.runCurrent
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
    private lateinit var fileRepository: FakeFileRepository
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
        fileRepository = FakeFileRepository()
    }

    private fun newViewModel(): CompleteProviderProfileViewModel =
        CompleteProviderProfileViewModel(
            getCategories = GetCategoriesUseCase(categoryRepository),
            registerProvider = RegisterProviderUseCase(providerRepository),
            sessionStore = sessionStore,
            prepareProfilePhoto = PrepareProfilePhotoUseCase(photoPreparer),
            uploadProfilePhoto = UploadProfilePhotoUseCase(fileRepository),
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

    @Test
    fun `onPhotoConfirmed sets isPhotoConfirmed and confirmedPhotoFileId`() = runTest(scheduler) {
        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel!!.onPhotoConfirmed("file-12345")
        advanceUntilIdle()

        assertEquals(true, viewModel!!.uiState.value.isPhotoConfirmed)
        assertEquals("file-12345", viewModel!!.uiState.value.confirmedPhotoFileId)
        assertNull(viewModel!!.uiState.value.photoError)
    }

    @Test
    fun `replacing confirmed photo with new valid photo resets confirmed state`() = runTest(scheduler) {
        viewModel = newViewModel()
        advanceUntilIdle()

        val initialPhoto = SelectedProfilePhoto("initial.jpg", "image/jpeg", 1024L, "/path/initial.jpg")
        photoPreparer.outcome = PhotoValidationOutcome.Valid(initialPhoto)
        viewModel!!.onPhotoSelected("content://media/initial")
        viewModel!!.onPhotoConfirmed("file-initial-1")
        advanceUntilIdle()

        val replacementPhoto = SelectedProfilePhoto("new.jpg", "image/jpeg", 2048L, "/path/new.jpg")
        photoPreparer.outcome = PhotoValidationOutcome.Valid(replacementPhoto)
        viewModel!!.onPhotoSelected("content://media/replacement")
        advanceUntilIdle()

        assertEquals(replacementPhoto, viewModel!!.uiState.value.selectedPhoto)
        assertEquals(false, viewModel!!.uiState.value.isPhotoConfirmed)
        assertNull(viewModel!!.uiState.value.confirmedPhotoFileId)
    }

    @Test
    fun `onUploadPhoto with no selected photo does nothing`() = runTest(scheduler) {
        viewModel = newViewModel()

        viewModel!!.onUploadPhoto()
        advanceUntilIdle()

        assertEquals(0, fileRepository.presignCalls)
        assertEquals(false, viewModel!!.uiState.value.photoLoading)
    }

    @Test
    fun `onUploadPhoto with selected photo uploads and confirms photo`() = runTest(scheduler) {
        val temp = java.io.File.createTempFile("photo", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val photo = SelectedProfilePhoto("profile.jpg", "image/jpeg", 3L, temp.absolutePath)
        photoPreparer.outcome = PhotoValidationOutcome.Valid(photo)
        viewModel = newViewModel()
        viewModel!!.onPhotoSelected("content://photo")
        advanceUntilIdle()

        viewModel!!.onUploadPhoto()
        advanceUntilIdle()

        assertEquals(1, fileRepository.presignCalls)
        assertEquals(1, fileRepository.uploadCalls)
        assertEquals(1, fileRepository.confirmCalls)
        assertEquals(true, viewModel!!.uiState.value.isPhotoConfirmed)
        assertEquals("file-uploaded-id", viewModel!!.uiState.value.confirmedPhotoFileId)
        assertEquals(false, viewModel!!.uiState.value.photoLoading)
        assertNull(viewModel!!.uiState.value.photoError)
    }

    @Test
    fun `onUploadPhoto failure sets UploadFailed error`() = runTest(scheduler) {
        val temp = java.io.File.createTempFile("photo", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val photo = SelectedProfilePhoto("profile.jpg", "image/jpeg", 3L, temp.absolutePath)
        photoPreparer.outcome = PhotoValidationOutcome.Valid(photo)
        fileRepository.presignOutcome = PresignUploadOutcome.Failure.Server(500, "Server error")
        viewModel = newViewModel()
        viewModel!!.onPhotoSelected("content://photo")
        advanceUntilIdle()

        viewModel!!.onUploadPhoto()
        advanceUntilIdle()

        assertEquals(false, viewModel!!.uiState.value.isPhotoConfirmed)
        assertEquals(false, viewModel!!.uiState.value.photoLoading)
        assertEquals(PhotoFormError.UploadFailed, viewModel!!.uiState.value.photoError)
    }

    @Test
    fun `onUploadPhoto ignores duplicate call when upload is in flight`() = runTest(scheduler) {
        val temp = java.io.File.createTempFile("photo", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val photo = SelectedProfilePhoto("profile.jpg", "image/jpeg", 3L, temp.absolutePath)
        photoPreparer.outcome = PhotoValidationOutcome.Valid(photo)
        val gate = CompletableDeferred<Unit>()
        fileRepository.presignGate = gate
        viewModel = newViewModel()
        viewModel!!.onPhotoSelected("content://photo")
        advanceUntilIdle()

        viewModel!!.onUploadPhoto()
        runCurrent()
        assertEquals(true, viewModel!!.uiState.value.photoLoading)
        assertEquals(1, fileRepository.presignCalls)

        viewModel!!.onUploadPhoto()
        runCurrent()
        assertEquals(1, fileRepository.presignCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(false, viewModel!!.uiState.value.photoLoading)
        assertEquals(true, viewModel!!.uiState.value.isPhotoConfirmed)
    }

    @Test
    fun `onPhotoSelected and submit are ignored when photo upload is in flight`() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("user-1", "user@test.com"), "token"))
        val temp = java.io.File.createTempFile("photo", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val photo = SelectedProfilePhoto("profile.jpg", "image/jpeg", 3L, temp.absolutePath)
        photoPreparer.outcome = PhotoValidationOutcome.Valid(photo)
        val gate = CompletableDeferred<Unit>()
        fileRepository.presignGate = gate
        viewModel = newViewModel()
        viewModel!!.onNameChanged("Carlos")
        viewModel!!.onSurnameChanged("Gómez")
        viewModel!!.onCategorySelected(defaultCategories[0])
        viewModel!!.onPhotoSelected("content://photo")
        advanceUntilIdle()

        viewModel!!.onUploadPhoto()
        runCurrent()
        assertEquals(true, viewModel!!.uiState.value.photoLoading)

        val anotherPhoto = SelectedProfilePhoto("another.jpg", "image/jpeg", 5L, "/cache/another.jpg")
        photoPreparer.outcome = PhotoValidationOutcome.Valid(anotherPhoto)
        viewModel!!.onPhotoSelected("content://another")
        runCurrent()
        assertEquals("profile.jpg", viewModel!!.uiState.value.selectedPhoto?.originalName)

        viewModel!!.submit()
        runCurrent()
        assertEquals(0, providerRepository.registerCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(true, viewModel!!.uiState.value.isPhotoConfirmed)
    }

    @Test
    fun `onUploadPhoto retry succeeds after previous failure`() = runTest(scheduler) {
        val temp = java.io.File.createTempFile("photo", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val photo = SelectedProfilePhoto("profile.jpg", "image/jpeg", 3L, temp.absolutePath)
        photoPreparer.outcome = PhotoValidationOutcome.Valid(photo)
        fileRepository.uploadBytesOutcome = UploadBytesOutcome.Failure.Server(500, "Server down")
        viewModel = newViewModel()
        viewModel!!.onPhotoSelected("content://photo")
        advanceUntilIdle()

        viewModel!!.onUploadPhoto()
        advanceUntilIdle()
        assertEquals(PhotoFormError.UploadFailed, viewModel!!.uiState.value.photoError)
        assertEquals(false, viewModel!!.uiState.value.isPhotoConfirmed)

        fileRepository.uploadBytesOutcome = UploadBytesOutcome.Success
        viewModel!!.onUploadPhoto()
        advanceUntilIdle()

        assertEquals(true, viewModel!!.uiState.value.isPhotoConfirmed)
        assertEquals(false, viewModel!!.uiState.value.photoLoading)
        assertNull(viewModel!!.uiState.value.photoError)
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

    private class FakeFileRepository : FileRepository {
        var presignCalls = 0
            private set
        var uploadCalls = 0
            private set
        var confirmCalls = 0
            private set

        var presignGate: CompletableDeferred<Unit>? = null
        var uploadGate: CompletableDeferred<Unit>? = null
        var confirmGate: CompletableDeferred<Unit>? = null

        var presignOutcome: PresignUploadOutcome = PresignUploadOutcome.Success(
            PresignUploadResult(
                fileId = "file-uploaded-id",
                key = "keys/profile.jpg",
                uploadUrl = "https://storage.example/upload",
                headers = mapOf("Content-Type" to "image/jpeg"),
            ),
        )
        var uploadBytesOutcome: UploadBytesOutcome = UploadBytesOutcome.Success
        var confirmOutcome: ConfirmUploadOutcome = ConfirmUploadOutcome.Success(
            ConfirmedFile(
                id = "file-uploaded-id",
                originalName = "profile.jpg",
                mimeType = "image/jpeg",
            ),
        )

        override suspend fun presign(request: PresignUploadRequest): PresignUploadOutcome {
            presignCalls += 1
            presignGate?.await()
            return presignOutcome
        }

        override suspend fun uploadBytes(
            uploadUrl: String,
            headers: Map<String, String>,
            bytes: ByteArray,
        ): UploadBytesOutcome {
            uploadCalls += 1
            uploadGate?.await()
            return uploadBytesOutcome
        }

        override suspend fun confirm(
            fileId: String,
            request: ConfirmUploadRequest,
        ): ConfirmUploadOutcome {
            confirmCalls += 1
            confirmGate?.await()
            return confirmOutcome
        }
    }
}
