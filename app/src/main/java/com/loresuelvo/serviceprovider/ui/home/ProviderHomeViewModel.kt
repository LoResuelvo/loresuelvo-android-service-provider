package com.loresuelvo.serviceprovider.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetPendingJobRequestsUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetScheduledWorkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ProviderHomeViewModel @Inject constructor(
    private val getPendingJobRequests: GetPendingJobRequestsUseCase,
    private val getScheduledWork: GetScheduledWorkUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProviderHomeUiState())
    val uiState: StateFlow<ProviderHomeUiState> = _uiState.asStateFlow()

    init {
        loadJobRequests()
        loadScheduledWork()
    }

    fun retryJobRequests() {
        if (_uiState.value.jobRequests !is ActivitySectionState.Loading) {
            loadJobRequests()
        }
    }

    fun retryScheduledWork() {
        if (_uiState.value.scheduledWork !is ActivitySectionState.Loading) {
            loadScheduledWork()
        }
    }

    fun removeJobRequest(id: Int) {
        _uiState.update { state ->
            val requests = state.jobRequests
            if (requests is ActivitySectionState.Ready) {
                state.copy(
                    jobRequests = ActivitySectionState.Ready(
                        requests.items.filterNot { it.id == id },
                    ),
                )
            } else {
                state
            }
        }
    }

    private fun loadJobRequests() {
        _uiState.update { it.copy(jobRequests = ActivitySectionState.Loading) }
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(jobRequests = getSectionState(getPendingJobRequests()))
            }
        }
    }

    private fun loadScheduledWork() {
        _uiState.update { it.copy(scheduledWork = ActivitySectionState.Loading) }
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(scheduledWork = getSectionState(getScheduledWork()))
            }
        }
    }

    private fun <T> getSectionState(
        outcome: ActivityLoadOutcome<T>,
    ): ActivitySectionState<T> = when (outcome) {
        is ActivityLoadOutcome.Success -> ActivitySectionState.Ready(outcome.items)
        is ActivityLoadOutcome.Failure -> ActivitySectionState.Error
    }
}
