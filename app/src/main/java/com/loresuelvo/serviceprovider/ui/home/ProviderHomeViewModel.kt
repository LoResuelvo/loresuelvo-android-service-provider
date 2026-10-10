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

    fun refresh() {
        retryJobRequests()
        retryScheduledWork()
    }

    fun retryJobRequests() {
        if (!_uiState.value.refreshingRequests) loadJobRequests()
    }

    fun retryScheduledWork() {
        if (!_uiState.value.refreshingWork) loadScheduledWork()
    }

    fun removeJobRequest(id: Int) {
        _uiState.update { state ->
            val requests = state.jobRequests
            if (requests is ActivitySectionState.Ready) {
                state.copy(
                    jobRequests = requests.copy(items = requests.items.filterNot { it.id == id }),
                )
            } else {
                state
            }
        }
    }

    private fun loadJobRequests() {
        _uiState.update { it.copy(refreshingRequests = true) }
        viewModelScope.launch {
            val outcome = getPendingJobRequests()
            _uiState.update { state ->
                state.copy(jobRequests = getSectionState(outcome, state.jobRequests), refreshingRequests = false)
            }
        }
    }

    private fun loadScheduledWork() {
        _uiState.update { it.copy(refreshingWork = true) }
        viewModelScope.launch {
            val outcome = getScheduledWork()
            _uiState.update { state ->
                state.copy(scheduledWork = getSectionState(outcome, state.scheduledWork), refreshingWork = false)
            }
        }
    }

    private fun <T> getSectionState(
        outcome: ActivityLoadOutcome<T>,
        previous: ActivitySectionState<T>,
    ): ActivitySectionState<T> = when (outcome) {
        is ActivityLoadOutcome.Success -> ActivitySectionState.Ready(outcome.items)
        is ActivityLoadOutcome.Failure -> {
            val expired = outcome == ActivityLoadOutcome.Failure.Unauthorized ||
                outcome is ActivityLoadOutcome.Failure.Server && outcome.code == 403
            if (previous is ActivitySectionState.Ready && !expired) previous.copy(refreshFailed = true)
            else ActivitySectionState.Error
        }
    }
}
