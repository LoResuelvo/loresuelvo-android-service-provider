package com.loresuelvo.serviceprovider.ui.jobrequest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.activity.AcceptJobRequestOutcome
import com.loresuelvo.serviceprovider.domain.activity.JobRequestDetailOutcome
import com.loresuelvo.serviceprovider.domain.usecase.activity.AcceptJobRequestUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetPendingJobRequestUseCase
import com.loresuelvo.serviceprovider.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class JobRequestDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPendingJobRequest: GetPendingJobRequestUseCase,
    private val acceptJobRequest: AcceptJobRequestUseCase,
) : ViewModel() {

    private val requestId: Int = savedStateHandle[Route.JobRequestDetail.argument] ?: -1
    private val _uiState = MutableStateFlow<JobRequestDetailUiState>(JobRequestDetailUiState.Loading)
    val uiState: StateFlow<JobRequestDetailUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<JobRequestDetailEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<JobRequestDetailEffect> = _effects

    init {
        load()
    }

    fun retry() {
        if (_uiState.value is JobRequestDetailUiState.Error ||
            _uiState.value is JobRequestDetailUiState.NotFound
        ) {
            load()
        }
    }

    fun accept() {
        val request = when (val state = _uiState.value) {
            is JobRequestDetailUiState.Ready -> state.request
            is JobRequestDetailUiState.AcceptError -> state.request
            else -> return
        }
        _uiState.value = JobRequestDetailUiState.Accepting(request)
        viewModelScope.launch {
            when (val outcome = acceptJobRequest(request.id)) {
                is AcceptJobRequestOutcome.Success -> {
                    _effects.emit(
                        JobRequestDetailEffect.Accepted(
                            requestId = outcome.requestId,
                            conversationId = outcome.conversationId,
                        ),
                    )
                }
                is AcceptJobRequestOutcome.Failure -> {
                    _uiState.value = JobRequestDetailUiState.AcceptError(request)
                }
            }
        }
    }

    fun retryAccept() {
        if (_uiState.value is JobRequestDetailUiState.AcceptError) accept()
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
