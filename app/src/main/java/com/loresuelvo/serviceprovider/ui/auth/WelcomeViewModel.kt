package com.loresuelvo.serviceprovider.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.auth.AuthProvider
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
import com.loresuelvo.serviceprovider.domain.usecase.category.GetCategoriesUseCase
import com.loresuelvo.serviceprovider.domain.usecase.auth.EstablishAuthSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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
    private val establishAuthSession: EstablishAuthSessionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    private val _effects = Channel<WelcomeEffect>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: Flow<WelcomeEffect> = _effects.receiveAsFlow()

    private val authenticationInFlight = AtomicBoolean(false)

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
        if (!authenticationInFlight.compareAndSet(false, true)) return

        // Publish the busy state before launching the suspend operation. This
        // closes the gap where two UI actions could be queued before the first
        // coroutine had a chance to update StateFlow.
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                when (val outcome = launch(activityContext)) {
                    AuthenticationOutcome.Cancelled -> {
                        _uiState.update { it.copy(error = null) }
                    }
                    is AuthenticationOutcome.Failure -> {
                        _uiState.update { it.copy(error = WelcomeError.Authentication) }
                    }
                    is AuthenticationOutcome.Success -> {
                        establishAuthSession(outcome.session)
                        _uiState.update { it.copy(error = null) }
                        _effects.trySend(WelcomeEffect.NavigateToProfessionalProfile)
                    }
                }
            } finally {
                authenticationInFlight.set(false)
                _uiState.update { it.copy(loading = false) }
            }
        }
    }
}
