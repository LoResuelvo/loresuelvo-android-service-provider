package com.loresuelvo.serviceprovider.bdd.profilephoto

import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.category.CategoryRepository
import com.loresuelvo.serviceprovider.domain.profile.PhotoValidationOutcome
import com.loresuelvo.serviceprovider.domain.profile.ProfilePhotoPreparer
import com.loresuelvo.serviceprovider.domain.profile.SelectedProfilePhoto
import com.loresuelvo.serviceprovider.domain.provider.ProviderRegistrationCommand
import com.loresuelvo.serviceprovider.domain.provider.ProviderRepository
import com.loresuelvo.serviceprovider.domain.provider.RegistrationOutcome
import com.loresuelvo.serviceprovider.domain.usecase.category.GetCategoriesUseCase
import com.loresuelvo.serviceprovider.domain.usecase.profile.PrepareProfilePhotoUseCase
import com.loresuelvo.serviceprovider.domain.usecase.provider.RegisterProviderUseCase
import com.loresuelvo.serviceprovider.ui.profile.CategoriesLoadState
import com.loresuelvo.serviceprovider.ui.profile.CompleteProviderProfileEffect
import com.loresuelvo.serviceprovider.ui.profile.CompleteProviderProfileViewModel
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
import org.junit.Assert.assertNull

/**
 * Deterministic test world for US-35.4 provider profile photo BDD scenarios.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderProfilePhotoWorld : AutoCloseable {

    private val scheduler = TestCoroutineScheduler()
    val dispatcher = StandardTestDispatcher(scheduler)

    val categoryRepository = FakeCategoryRepository()
    val providerRepository = FakeProviderRepository()
    val sessionStore = FakeAuthSessionStore()
    val photoPreparer = FakeProfilePhotoPreparer()

    val getCategories = GetCategoriesUseCase(categoryRepository)
    val registerProvider = RegisterProviderUseCase(providerRepository)
    val prepareProfilePhoto = PrepareProfilePhotoUseCase(photoPreparer)

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

    fun navigateToProfileDestination() {
        viewModel = CompleteProviderProfileViewModel(
            getCategories = getCategories,
            registerProvider = registerProvider,
            sessionStore = sessionStore,
            prepareProfilePhoto = prepareProfilePhoto,
        )
        effectsJob?.cancel()
        effectsJob = CoroutineScope(dispatcher).launch {
            viewModel.effects.collect { effect ->
                latestEffect = effect
            }
        }
        scheduler.advanceUntilIdle()
    }

    fun fillValidProfileData() {
        if (!::viewModel.isInitialized) {
            navigateToProfileDestination()
        }
        viewModel.onNameChanged("Carlos")
        viewModel.onSurnameChanged("Gómez")
        val ready = viewModel.uiState.value.categoriesState as CategoriesLoadState.Ready
        viewModel.onCategorySelected(ready.categories.first())
    }

    fun selectValidJpegPhoto() {
        val photo = SelectedProfilePhoto("profile.jpg", "image/jpeg", 2048L, "/cache/profile.jpg")
        photoPreparer.outcome = PhotoValidationOutcome.Valid(photo)
        viewModel.onPhotoSelected("content://media/profile.jpg")
        scheduler.advanceUntilIdle()
    }

    fun assertPhotoPreviewDisplayed() {
        assertNotNull("Selected photo must be present in UI state", viewModel.uiState.value.selectedPhoto)
    }

    fun assertProfileDataPreserved() {
        val state = viewModel.uiState.value
        assertEquals("Carlos", state.name)
        assertEquals("Gómez", state.surname)
        assertNotNull("Selected category must be preserved", state.selectedCategory)
    }

    fun assertUploadOrReplaceAvailable() {
        val state = viewModel.uiState.value
        assertNotNull(state.selectedPhoto)
        assertEquals(false, state.photoLoading)
        assertNull(state.photoError)
    }

    fun cancelPhotoSelection() {
        viewModel.onPhotoSelectionCancelled()
        scheduler.advanceUntilIdle()
    }

    fun assertNoPhotoValidationError() {
        assertNull(viewModel.uiState.value.photoError)
    }

    fun selectImageWithFormatAndSize(format: String, sizeBytes: Long) {
        val mime = when (format.uppercase()) {
            "JPEG", "JPG" -> "image/jpeg"
            "PNG" -> "image/png"
            "WEBP" -> "image/webp"
            else -> "application/octet-stream"
        }
        val photo = SelectedProfilePhoto("photo.${format.lowercase()}", mime, sizeBytes, "/cache/photo.${format.lowercase()}")
        photoPreparer.outcome = PhotoValidationOutcome.Valid(photo)
        viewModel.onPhotoSelected("content://media/photo")
        scheduler.advanceUntilIdle()
    }

    private var arrangedFormat: String = "JPEG"
    private var arrangedSizeBytes: Long = 1024L

    fun arrangeImageWithFormatAndSize(format: String, sizeBytes: Long) {
        arrangedFormat = format
        arrangedSizeBytes = sizeBytes
    }

    fun selectArrangedImage() {
        selectImageWithFormatAndSize(arrangedFormat, arrangedSizeBytes)
    }

    fun assertImageAcceptedWithPreview() {
        val state = viewModel.uiState.value
        assertNotNull(state.selectedPhoto)
        assertNull(state.photoError)
    }

    fun assertUploadActionAvailable() {
        val state = viewModel.uiState.value
        assertNotNull(state.selectedPhoto)
        assertEquals(false, state.isPhotoConfirmed)
        assertEquals(false, state.photoLoading)
    }

    override fun close() {
        effectsJob?.cancel()
        providerRepository.registerGate?.complete(Unit)
        scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    class FakeCategoryRepository : CategoryRepository {
        var outcome: CategoriesOutcome = CategoriesOutcome.Success(
            listOf(
                Category(1, "Plomería"),
                Category(2, "Electricidad"),
                Category(3, "Pintura"),
            ),
        )

        override suspend fun getCategories(): CategoriesOutcome = outcome
    }

    class FakeProviderRepository : ProviderRepository {
        var outcome: RegistrationOutcome = RegistrationOutcome.Success(providerId = 1)
        var registerCalls: Int = 0
            private set
        var lastCommand: ProviderRegistrationCommand? = null
            private set
        var registerGate: CompletableDeferred<Unit>? = null

        override suspend fun register(command: ProviderRegistrationCommand): RegistrationOutcome {
            registerCalls += 1
            lastCommand = command
            registerGate?.await()
            return outcome
        }
    }

    class FakeAuthSessionStore : AuthSessionStore {
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

    class FakeProfilePhotoPreparer : ProfilePhotoPreparer {
        var outcome: PhotoValidationOutcome = PhotoValidationOutcome.Valid(
            SelectedProfilePhoto(
                originalName = "valid_profile.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 1024L,
                localPath = "/cache/valid_profile.jpg",
            ),
        )

        override suspend fun preparePhoto(source: String): PhotoValidationOutcome = outcome

        override suspend fun cleanPhoto(photo: SelectedProfilePhoto) {}
    }
}
