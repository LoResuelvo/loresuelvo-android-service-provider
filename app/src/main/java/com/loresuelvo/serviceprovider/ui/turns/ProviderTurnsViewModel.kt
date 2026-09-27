package com.loresuelvo.serviceprovider.ui.turns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProviderTurnsUiState {
    data object Loading : ProviderTurnsUiState
    data class Ready(val orders: List<WorkOrder>) : ProviderTurnsUiState
    data object Error : ProviderTurnsUiState
}

@HiltViewModel
class ProviderTurnsViewModel @Inject constructor(
    private val workOrders: WorkOrderRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ProviderTurnsUiState>(ProviderTurnsUiState.Loading)
    val uiState: StateFlow<ProviderTurnsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.value = ProviderTurnsUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = workOrders.getWorkOrders()) {
                is ActivityLoadOutcome.Success -> ProviderTurnsUiState.Ready(result.items)
                is ActivityLoadOutcome.Failure -> ProviderTurnsUiState.Error
            }
        }
    }
}
