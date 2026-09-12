package com.loresuelvo.serviceprovider.bdd.profilephoto

import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.category.CategoryRepository
import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.PresignUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.UploadBytesOutcome
import com.loresuelvo.serviceprovider.domain.profile.PhotoValidationOutcome
import com.loresuelvo.serviceprovider.domain.profile.ProfilePhotoPreparer
import com.loresuelvo.serviceprovider.domain.profile.SelectedProfilePhoto
import com.loresuelvo.serviceprovider.domain.provider.ProviderRegistrationCommand
import com.loresuelvo.serviceprovider.domain.provider.ProviderRepository
import com.loresuelvo.serviceprovider.domain.provider.RegistrationOutcome
import com.loresuelvo.serviceprovider.domain.usecase.category.GetCategoriesUseCase
import com.loresuelvo.serviceprovider.domain.usecase.profile.PrepareProfilePhotoUseCase
import com.loresuelvo.serviceprovider.domain.usecase.profile.UploadProfilePhotoUseCase
import com.loresuelvo.serviceprovider.domain.usecase.provider.RegisterProviderUseCase
import com.loresuelvo.serviceprovider.ui.profile.CategoriesLoadState
import com.loresuelvo.serviceprovider.ui.profile.CompleteProviderProfileEffect
import com.loresuelvo.serviceprovider.ui.profile.CompleteProviderProfileViewModel
import com.loresuelvo.serviceprovider.ui.profile.PhotoFormError
import com.loresuelvo.serviceprovider.ui.profile.ProfileFormError
import java.io.IOException
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
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderProfilePhotoWorld : AutoCloseable {

    private val scheduler = TestCoroutineScheduler()
    val dispatcher = StandardTestDispatcher(scheduler)

    val categoryRepository = FakeCategoryRepository()
    val providerRepository = FakeProviderRepository()
    val sessionStore = FakeAuthSessionStore()
    val photoPreparer = FakeProfilePhotoPreparer()
    val fileRepository = FakeFileRepository()

    val getCategories = GetCategoriesUseCase(categoryRepository)
    val registerProvider = RegisterProviderUseCase(providerRepository)
    val prepareProfilePhoto = PrepareProfilePhotoUseCase(photoPreparer)
    val uploadProfilePhoto = UploadProfilePhotoUseCase(fileRepository)

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
        val tempFile = java.io.File.createTempFile("profile", ".jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
            deleteOnExit()
        }
        val photo = SelectedProfilePhoto("profile.jpg", "image/jpeg", 2048L, tempFile.absolutePath)
        photoPreparer.outcome = PhotoValidationOutcome.Valid(photo)
        viewModel.onPhotoSelected("content://media/profile.jpg")
        scheduler.advanceUntilIdle()
    }

    fun configurePhotoUploadSuccess() {
        fileRepository.uploadBytesOutcome = UploadBytesOutcome.Success
    }

    fun triggerPhotoUpload() {
        viewModel.onUploadPhoto()
        scheduler.advanceUntilIdle()
    }

    fun assertUploadProgressDisplayed() {
        assertEquals(1, fileRepository.uploadCalls)
    }

    fun assertPhotoReadyForRegistration() {
        val state = viewModel.uiState.value
        assertEquals(true, state.isPhotoConfirmed)
        assertEquals("file_uploaded_123", state.confirmedPhotoFileId)
        assertEquals(false, state.photoLoading)
        assertNull(state.photoError)
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

    fun arrangePhotoSelectedOrConfirmed() {
        seedAuthenticatedSession()
        navigateToProfileDestination()
        fillValidProfileData()
        val initialPhoto = SelectedProfilePhoto("initial_photo.jpg", "image/jpeg", 1024L, "/cache/initial.jpg")
        photoPreparer.outcome = PhotoValidationOutcome.Valid(initialPhoto)
        viewModel.onPhotoSelected("content://media/initial_photo.jpg")
        viewModel.onPhotoConfirmed("file_initial_123")
        scheduler.advanceUntilIdle()
    }

    fun assertRegistrationNotCompleted() {
        assertEquals(0, providerRepository.registerCalls)
    }

    fun replaceWithAnotherValidPhoto() {
        val replacement = SelectedProfilePhoto("new_photo.jpg", "image/jpeg", 2048L, "/cache/new.jpg")
        photoPreparer.outcome = PhotoValidationOutcome.Valid(replacement)
        viewModel.onPhotoSelected("content://media/new_photo.jpg")
        scheduler.advanceUntilIdle()
    }

    fun assertNewPhotoPreviewDisplayed() {
        assertEquals("new_photo.jpg", viewModel.uiState.value.selectedPhoto?.originalName)
    }

    fun assertPhotoMustBeUploadedAndConfirmed() {
        val state = viewModel.uiState.value
        assertEquals(false, state.isPhotoConfirmed)
        assertNull(state.confirmedPhotoFileId)
        assertEquals(false, state.photoLoading)
    }

    private var arrangedInvalidCondition: String = ""

    fun arrangeInvalidCondition(condition: String) {
        arrangedInvalidCondition = condition
    }

    fun selectArrangedInvalidFile() {
        val outcome = when {
            arrangedInvalidCondition.contains("GIF") -> PhotoValidationOutcome.Invalid.UnsupportedFormat
            arrangedInvalidCondition.contains("5242881") -> PhotoValidationOutcome.Invalid.ExceedsMaxSize
            arrangedInvalidCondition.contains("cero bytes") -> PhotoValidationOutcome.Invalid.EmptyFile
            arrangedInvalidCondition.contains("no puede leerse") -> PhotoValidationOutcome.Invalid.Unreadable
            else -> PhotoValidationOutcome.Invalid.CorruptContent
        }
        photoPreparer.outcome = outcome
        viewModel.onPhotoSelected("content://media/invalid_file")
        scheduler.advanceUntilIdle()
    }

    fun assertPhotoErrorMessageDisplayed() {
        assertNotNull("Photo validation error must be displayed", viewModel.uiState.value.photoError)
    }

    fun assertNoUploadAttempted() {
        assertEquals(false, viewModel.uiState.value.photoLoading)
    }

    fun assertOriginalPhotoStillSelected() {
        assertEquals("profile.jpg", viewModel.uiState.value.selectedPhoto?.originalName)
    }

    fun arrangePhotoState(state: String) {
        selectValidJpegPhoto()
        if (state.contains("Confirmada")) {
            viewModel.onPhotoConfirmed("file_confirmed_999")
            scheduler.advanceUntilIdle()
        }
    }

    fun recreateScreenPreservingViewModel() {
        effectsJob?.cancel()
        effectsJob = CoroutineScope(dispatcher).launch {
            viewModel.effects.collect { latestEffect = it }
        }
        scheduler.advanceUntilIdle()
    }

    fun assertProfileDataAndPhotoPreviewPreserved() {
        assertProfileDataPreserved()
        assertPhotoPreviewDisplayed()
    }

    fun assertPhotoConfirmationStatePreserved(expectedRestored: String) {
        val state = viewModel.uiState.value
        if (expectedRestored.contains("Confirmada")) {
            assertEquals(true, state.isPhotoConfirmed)
            assertEquals("file_confirmed_999", state.confirmedPhotoFileId)
        } else {
            assertEquals(false, state.isPhotoConfirmed)
            assertNull(state.confirmedPhotoFileId)
        }
    }

    fun assertNoAutomaticUploadOrRegistration() {
        assertEquals(false, viewModel.uiState.value.photoLoading)
        assertEquals(0, providerRepository.registerCalls)
    }

    fun arrangeOperationInProgress(operation: String) {
        seedAuthenticatedSession()
        navigateToProfileDestination()
        fillValidProfileData()
        selectValidJpegPhoto()

        when (operation) {
            "Solicitud de URL firmada" -> {
                val gate = CompletableDeferred<Unit>()
                fileRepository.presignGate = gate
                viewModel.onUploadPhoto()
                dispatcher.scheduler.runCurrent()
            }
            "Solicitud PUT firmada" -> {
                val gate = CompletableDeferred<Unit>()
                fileRepository.uploadGate = gate
                viewModel.onUploadPhoto()
                dispatcher.scheduler.runCurrent()
            }
            "Confirmación de la foto" -> {
                val gate = CompletableDeferred<Unit>()
                fileRepository.confirmGate = gate
                viewModel.onUploadPhoto()
                dispatcher.scheduler.runCurrent()
            }
            "Registro del prestador" -> {
                viewModel.onPhotoConfirmed("file_123")
                viewModel.onCoverageZonesSelected(listOf(1))
                val gate = CompletableDeferred<Unit>()
                providerRepository.registerGate = gate
                viewModel.submit()
                dispatcher.scheduler.runCurrent()
            }
        }
    }

    fun repeatUploadOrRegistrationAction() {
        viewModel.onUploadPhoto()
        viewModel.submit()
        viewModel.onPhotoSelected("content://media/another.jpg")
        dispatcher.scheduler.runCurrent()
    }

    fun assertNoDuplicateOperation() {
        if (providerRepository.registerGate != null) {
            assertEquals(1, providerRepository.registerCalls)
            assertEquals(0, fileRepository.uploadCalls)
        } else {
            assertEquals(1, fileRepository.presignCalls)
            assertEquals(0, providerRepository.registerCalls)
        }
    }

    fun assertControlsRemainDisabled() {
        val state = viewModel.uiState.value
        assertEquals(true, state.loading || state.photoLoading)
    }

    fun assertOperationProgressContinues() {
        val state = viewModel.uiState.value
        assertEquals(true, state.loading || state.photoLoading)
    }

    fun arrangeRecoverableFailure(stage: String, failureDescription: String) {
        when (stage) {
            "Solicitud de URL firmada" -> {
                fileRepository.presignOutcome = PresignUploadOutcome.Failure.Network(
                    IOException("Network failure"),
                )
            }
            "Solicitud PUT firmada" -> {
                fileRepository.uploadBytesOutcome = UploadBytesOutcome.Failure.Network(
                    IOException("Network failure"),
                )
            }
            "Confirmación" -> {
                fileRepository.confirmOutcome = ConfirmUploadOutcome.Failure.Network(
                    IOException("Network failure"),
                )
            }
        }
    }

    fun assertRemainsOnFormWithFriendlyPhotoError() {
        val state = viewModel.uiState.value
        assertEquals(PhotoFormError.UploadFailed, state.photoError)
        assertNull(latestEffect)
    }

    fun assertPhotoRetainedForRetryOrReplace() {
        val state = viewModel.uiState.value
        assertNotNull(state.selectedPhoto)
        assertEquals(false, state.isPhotoConfirmed)
        assertEquals(false, state.photoLoading)
    }

    fun arrangeRecoverablePhotoFailure() {
        seedAuthenticatedSession()
        navigateToProfileDestination()
        fillValidProfileData()
        selectValidJpegPhoto()
        fileRepository.uploadBytesOutcome = UploadBytesOutcome.Failure.Network(
            IOException("Network failure"),
        )
        triggerPhotoUpload()
        assertEquals(PhotoFormError.UploadFailed, viewModel.uiState.value.photoError)
    }

    fun configureRetrySuccess() {
        fileRepository.resetDefaults()
    }

    fun assertUploadProgressResumed() {
        assertEquals(2, fileRepository.presignCalls)
    }

    fun arrangeDependencyCondition(condition: String) {
        when (condition) {
            "No hay una foto válida seleccionada" -> {
                viewModel.onCoverageZonesSelected(listOf(1))
            }
            "La foto seleccionada todavía no se cargó" -> {
                selectValidJpegPhoto()
                viewModel.onCoverageZonesSelected(listOf(1))
            }
            "La carga o confirmación de la foto falló" -> {
                selectValidJpegPhoto()
                fileRepository.uploadBytesOutcome = UploadBytesOutcome.Failure.Network(
                    IOException("Network failure"),
                )
                triggerPhotoUpload()
                viewModel.onCoverageZonesSelected(listOf(1))
            }
            "La foto de reemplazo todavía no se confirmó" -> {
                selectValidJpegPhoto()
                viewModel.onPhotoConfirmed("file_old_123")
                replaceWithAnotherValidPhoto()
                viewModel.onCoverageZonesSelected(listOf(1))
            }
            "La foto está confirmada pero no se seleccionó ninguna zona de cobertura real" -> {
                selectValidJpegPhoto()
                viewModel.onPhotoConfirmed("file_confirmed_123")
                viewModel.onCoverageZonesSelected(emptyList())
            }
        }
    }

    fun requestRegistration() {
        viewModel.submit()
        scheduler.advanceUntilIdle()
    }

    fun assertIncompleteRequirementIdentified() {
        val state = viewModel.uiState.value
        val isRequirementIdentified = state.error is ProfileFormError.MissingPhoto ||
            state.error is ProfileFormError.MissingCoverageZones
        assertTrue(
            "Expected form error to identify incomplete photo or zones, but was ${state.error}",
            isRequirementIdentified,
        )
    }

    override fun close() {
        effectsJob?.cancel()
        fileRepository.presignGate?.complete(Unit)
        fileRepository.uploadGate?.complete(Unit)
        fileRepository.confirmGate?.complete(Unit)
        providerRepository.registerGate?.complete(Unit)
        scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }
}
