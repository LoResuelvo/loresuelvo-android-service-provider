package com.loresuelvo.serviceprovider.ui.jobrequest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.activity.JobRequestDetailOutcome
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetPendingJobRequestUseCase
import com.loresuelvo.serviceprovider.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class JobRequestDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPendingJobRequest: GetPendingJobRequestUseCase,
) : ViewModel() {

    private val requestId: Int = savedStateHandle[Route.JobRequestDetail.argument] ?: -1
    private val _uiState = MutableStateFlow<JobRequestDetailUiState>(JobRequestDetailUiState.Loading)
    val uiState: StateFlow<JobRequestDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        if (_uiState.value !is JobRequestDetailUiState.Loading) load()
    }

    private fun load() {
        _uiState.value = JobRequestDetailUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val outcome = getPendingJobRequest(requestId)) {
                is JobRequestDetailOutcome.Success -> JobRequestDetailUiState.Ready(outcome.request)
                JobRequestDetailOutcome.NotFound -> JobRequestDetailUiState.NotFound
                is JobRequestDetailOutcome.Failure -> JobRequestDetailUiState.Error
            }
        }
    }
}
