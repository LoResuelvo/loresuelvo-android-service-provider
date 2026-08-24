package com.loresuelvo.serviceprovider.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.auth.AuthProvider
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
import com.loresuelvo.serviceprovider.domain.usecase.category.GetCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UDF ViewModel for the `Welcome` screen. Drives the IdP signup flow
 * via [AuthProvider] and fetches the public service categories
 * through [GetCategoriesUseCase] to populate the illustrative chips.
 *
 * The Activity [Context] is supplied per-call by the Composable
 * (via `LocalContext.current`) rather than captured at construction
 * time, so the VM is `@HiltViewModel`-clean.
 */
@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val authProvider: AuthProvider,
    private val getCategories: GetCategoriesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    /**
     * Loads the platform's service categories. An empty response or
     * any failure collapses to [WelcomeCategoriesUiState.Error] so
     * the screen shows the error state (per product decision) instead
     * of an empty row.
     */
    fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(categories = WelcomeCategoriesUiState.Loading) }
            val next = when (val outcome = getCategories()) {
                is CategoriesOutcome.Success ->
                    if (outcome.categories.isEmpty()) {
                        WelcomeCategoriesUiState.Error
                    } else {
                        WelcomeCategoriesUiState.Ready(outcome.categories)
                    }
                is CategoriesOutcome.Failure -> WelcomeCategoriesUiState.Error
            }
            _uiState.update { it.copy(categories = next) }
        }
    }

    fun signup(activityContext: Context) {
        authenticate(activityContext, authProvider::signup)
    }

    fun login(activityContext: Context) {
        authenticate(activityContext, authProvider::login)
    }

    fun loginWithGoogle(activityContext: Context) {
        authenticate(activityContext, authProvider::loginWithGoogle)
    }

    private fun authenticate(
        activityContext: Context,
        launch: suspend (Context) -> AuthenticationOutcome,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            when (val outcome = launch(activityContext)) {
                AuthenticationOutcome.Cancelled -> {
                    _uiState.update { it.copy(loading = false, error = null) }
                }
                is AuthenticationOutcome.Failure -> {
                    _uiState.update { it.copy(loading = false, error = WelcomeError.Authentication) }
                }
                is AuthenticationOutcome.Success -> {
                    // Sync against GET /me will land alongside the provider
                    // onboarding feature; for now we just clear the
                    // spinner.
                    _uiState.update { it.copy(loading = false, error = null) }
                }
            }
        }
    }
}