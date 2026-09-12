package com.loresuelvo.serviceprovider.ui.profile

import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.profile.SelectedProfilePhoto

/**
 * UI state for the complete provider profile screen.
 */
data class CompleteProviderProfileUiState(
    val name: String = "",
    val surname: String = "",
    val selectedCategory: Category? = null,
    val categoriesState: CategoriesLoadState = CategoriesLoadState.Loading,
    val selectedPhoto: SelectedProfilePhoto? = null,
    val isPhotoConfirmed: Boolean = false,
    val confirmedPhotoFileId: String? = null,
    val photoLoading: Boolean = false,
    val photoError: PhotoFormError? = null,
    val selectedCoverageZoneIds: List<Int> = emptyList(),
    val loading: Boolean = false,
    val error: ProfileFormError? = null,
)

/**
 * Typed validation and processing errors for the provider profile photo.
 */
sealed interface PhotoFormError {
    data object UnsupportedFormat : PhotoFormError
    data object ExceedsMaxSize : PhotoFormError
    data object EmptyFile : PhotoFormError
    data object Unreadable : PhotoFormError
    data object CorruptContent : PhotoFormError
    data object UploadFailed : PhotoFormError
}

/**
 * State of service categories fetching.
 */
sealed interface CategoriesLoadState {
    data object Loading : CategoriesLoadState
    data class Ready(val categories: List<Category>) : CategoriesLoadState
    data object Error : CategoriesLoadState
}

/**
 * Typed failures and validation errors for the provider profile form.
 */
sealed interface ProfileFormError {
    data object MissingName : ProfileFormError
    data object MissingSurname : ProfileFormError
    data object MissingCategory : ProfileFormError
    data object MissingPhoto : ProfileFormError
    data object MissingCoverageZones : ProfileFormError
    data class Network(val message: String) : ProfileFormError
    data class Server(val code: Int, val message: String) : ProfileFormError
    data object Unauthorized : ProfileFormError
    data object AlreadyRegistered : ProfileFormError
}

/**
 * One-shot navigation side effects for the provider profile flow.
 */
sealed interface CompleteProviderProfileEffect {
    data object NavigateToMercadoPago : CompleteProviderProfileEffect
    data object NavigateToWelcome : CompleteProviderProfileEffect
}
