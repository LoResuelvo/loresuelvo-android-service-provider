package com.loresuelvo.serviceprovider.ui.auth

import com.loresuelvo.serviceprovider.domain.category.Category

/**
 * UDF state for the `Welcome` screen. `loading` / `error` belong to
 * the IdP signup flow; [categories] is the independent state of the
 * public `GET /categories` fetch shown as illustrative chips.
 */
data class WelcomeUiState(
    val loading: Boolean = false,
    val error: WelcomeError? = null,
    val categories: WelcomeCategoriesUiState = WelcomeCategoriesUiState.Loading,
)

sealed interface WelcomeError {
    data object Authentication : WelcomeError
    data object Network : WelcomeError
    data class Server(val code: Int) : WelcomeError
    data object Unauthorized : WelcomeError
}

/**
 * State of the category chips section. Modelled as a sealed type so
 * the screen renders exactly one of loading / ready / error without
 * boolean flags. An empty backend response is treated as [Error] by
 * the ViewModel (nothing meaningful to show).
 */
sealed interface WelcomeCategoriesUiState {

    data object Loading : WelcomeCategoriesUiState

    data class Ready(val categories: List<Category>) : WelcomeCategoriesUiState

    data object Error : WelcomeCategoriesUiState
}