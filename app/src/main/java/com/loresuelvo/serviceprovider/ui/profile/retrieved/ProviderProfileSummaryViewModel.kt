package com.loresuelvo.serviceprovider.ui.profile.retrieved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.provider.GetProviderProfileOutcome
import com.loresuelvo.serviceprovider.domain.usecase.provider.GetProviderProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ProviderProfileSummaryViewModel @Inject constructor(
    private val getProviderProfile: GetProviderProfileUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProviderProfileSummaryUiState())
    val uiState: StateFlow<ProviderProfileSummaryUiState> = _uiState.asStateFlow()

    fun loadProfile(providerId: Int) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val outcome = getProviderProfile(providerId)) {
                is GetProviderProfileOutcome.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            providerId = outcome.profile.id,
                            name = outcome.profile.name,
                            surname = outcome.profile.surname,
                            profilePhotoUrl = outcome.profile.profilePhotoUrl,
                        )
                    }
                }
                is GetProviderProfileOutcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "No se pudo cargar el perfil del prestador",
                        )
                    }
                }
            }
        }
    }
}
