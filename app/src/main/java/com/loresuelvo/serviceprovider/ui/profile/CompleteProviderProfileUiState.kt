package com.loresuelvo.serviceprovider.ui.profile

import com.loresuelvo.serviceprovider.domain.category.Category

/**
 * UI state for the complete provider profile screen.
 */
data class CompleteProviderProfileUiState(
    val name: String = "",
    val surname: String = "",
    val selectedCategory: Category? = null,
    val categoriesState: CategoriesLoadState = CategoriesLoadState.Loading,
    val loading: Boolean = false,
    val error: ProfileFormError? = null,
)

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
