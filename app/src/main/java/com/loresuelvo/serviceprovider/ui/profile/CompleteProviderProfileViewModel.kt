package com.loresuelvo.serviceprovider.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.provider.ProviderRegistrationCommand
import com.loresuelvo.serviceprovider.domain.provider.RegistrationOutcome
import com.loresuelvo.serviceprovider.domain.profile.PhotoValidationOutcome
import com.loresuelvo.serviceprovider.domain.usecase.category.GetCategoriesUseCase
import com.loresuelvo.serviceprovider.domain.usecase.profile.PrepareProfilePhotoUseCase
import com.loresuelvo.serviceprovider.domain.usecase.profile.UploadProfilePhotoOutcome
import com.loresuelvo.serviceprovider.domain.usecase.profile.UploadProfilePhotoUseCase
import com.loresuelvo.serviceprovider.domain.usecase.provider.RegisterProviderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel managing the provider profile completion form, categories loading,
 * input validation, and submission flow.
 */
@HiltViewModel
class CompleteProviderProfileViewModel @Inject constructor(
    private val getCategories: GetCategoriesUseCase,
    private val registerProvider: RegisterProviderUseCase,
    private val sessionStore: AuthSessionStore,
    private val prepareProfilePhoto: PrepareProfilePhotoUseCase,
    private val uploadProfilePhoto: UploadProfilePhotoUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompleteProviderProfileUiState())
    val uiState: StateFlow<CompleteProviderProfileUiState> = _uiState.asStateFlow()

    private val _effects = Channel<CompleteProviderProfileEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        loadCategories()
    }

    fun loadCategories() {
        _uiState.update { it.copy(categoriesState = CategoriesLoadState.Loading) }
        viewModelScope.launch {
            when (val outcome = getCategories()) {
                is CategoriesOutcome.Success -> {
                    _uiState.update { it.copy(categoriesState = CategoriesLoadState.Ready(outcome.categories)) }
                }
                is CategoriesOutcome.Failure -> {
                    _uiState.update { it.copy(categoriesState = CategoriesLoadState.Error) }
                }
            }
        }
    }

    fun retryLoadingCategories() {
        loadCategories()
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, error = null) }
    }

    fun onSurnameChanged(surname: String) {
        _uiState.update { it.copy(surname = surname, error = null) }
    }

    fun onCategorySelected(category: Category) {
        _uiState.update { it.copy(selectedCategory = category, error = null) }
    }

    fun onPhotoSelected(source: String) {
        if (_uiState.value.photoLoading || _uiState.value.loading) return

        _uiState.update { it.copy(photoLoading = true) }
        viewModelScope.launch {
            when (val outcome = prepareProfilePhoto(source)) {
                is PhotoValidationOutcome.Valid -> {
                    _uiState.update {
                        it.copy(
                            selectedPhoto = outcome.photo,
                            isPhotoConfirmed = false,
                            confirmedPhotoFileId = null,
                            photoLoading = false,
                            photoError = null,
                        )
                    }
                }
                is PhotoValidationOutcome.Invalid.UnsupportedFormat -> {
                    _uiState.update {
                        it.copy(
                            photoLoading = false,
                            photoError = PhotoFormError.UnsupportedFormat,
                        )
                    }
                }
                is PhotoValidationOutcome.Invalid.ExceedsMaxSize -> {
                    _uiState.update {
                        it.copy(
                            photoLoading = false,
                            photoError = PhotoFormError.ExceedsMaxSize,
                        )
                    }
                }
                is PhotoValidationOutcome.Invalid.EmptyFile -> {
                    _uiState.update {
                        it.copy(
                            photoLoading = false,
                            photoError = PhotoFormError.EmptyFile,
                        )
                    }
                }
                is PhotoValidationOutcome.Invalid.Unreadable -> {
                    _uiState.update {
                        it.copy(
                            photoLoading = false,
                            photoError = PhotoFormError.Unreadable,
                        )
                    }
                }
                is PhotoValidationOutcome.Invalid.CorruptContent -> {
                    _uiState.update {
                        it.copy(
                            photoLoading = false,
                            photoError = PhotoFormError.CorruptContent,
                        )
                    }
                }
            }
        }
    }

    fun onPhotoSelectionCancelled() {
        // Preserves previous photo selection, confirmation state, and form inputs
    }

    fun onUploadPhoto() {
        val photo = _uiState.value.selectedPhoto ?: return
        if (_uiState.value.photoLoading || _uiState.value.loading) return

        _uiState.update { it.copy(photoLoading = true, photoError = null) }
        viewModelScope.launch {
            when (val outcome = uploadProfilePhoto(photo)) {
                is UploadProfilePhotoOutcome.Success -> {
                    onPhotoConfirmed(outcome.confirmedFile.id)
                }
                is UploadProfilePhotoOutcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            photoLoading = false,
                            photoError = PhotoFormError.UploadFailed,
                        )
                    }
                }
            }
        }
    }

    fun onPhotoConfirmed(fileId: String) {
        _uiState.update {
            it.copy(
                isPhotoConfirmed = true,
                confirmedPhotoFileId = fileId,
                photoLoading = false,
                photoError = null,
            )
        }
    }

    fun submit() {
        if (_uiState.value.loading || _uiState.value.photoLoading) return

        val state = _uiState.value
        val name = state.name.trim()
        val surname = state.surname.trim()
        val category = state.selectedCategory

        if (name.isEmpty()) {
            _uiState.update { it.copy(error = ProfileFormError.MissingName) }
            return
        }
        if (surname.isEmpty()) {
            _uiState.update { it.copy(error = ProfileFormError.MissingSurname) }
            return
        }
        if (category == null) {
            _uiState.update { it.copy(error = ProfileFormError.MissingCategory) }
            return
        }

        val session = sessionStore.getSession()
        if (session == null) {
            viewModelScope.launch {
                _effects.send(CompleteProviderProfileEffect.NavigateToWelcome)
            }
            return
        }

        _uiState.update { it.copy(loading = true, error = null) }

        viewModelScope.launch {
            try {
                val command = ProviderRegistrationCommand(
                    email = session.user.email,
                    name = name,
                    surname = surname,
                    categoryId = category.id,
                )
                when (val outcome = registerProvider(command)) {
                    is RegistrationOutcome.Success -> {
                        _effects.send(CompleteProviderProfileEffect.NavigateToMercadoPago)
                    }
                    is RegistrationOutcome.Failure.AlreadyRegistered -> {
                        _uiState.update { it.copy(error = ProfileFormError.AlreadyRegistered) }
                    }
                    is RegistrationOutcome.Failure.Unauthorized -> {
                        sessionStore.clearSession()
                        _effects.send(CompleteProviderProfileEffect.NavigateToWelcome)
                    }
                    is RegistrationOutcome.Failure.Network -> {
                        _uiState.update { it.copy(error = ProfileFormError.Network(outcome.cause.message.orEmpty())) }
                    }
                    is RegistrationOutcome.Failure.Server -> {
                        _uiState.update { it.copy(error = ProfileFormError.Server(outcome.code, outcome.message)) }
                    }
                }
            } finally {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }
}
