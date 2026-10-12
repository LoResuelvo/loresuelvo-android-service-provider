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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@HiltViewModel
class JobRequestDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPendingJobRequest: GetPendingJobRequestUseCase,
    private val acceptJobRequest: AcceptJobRequestUseCase,
) : ViewModel() {

    private var requestId: Int = savedStateHandle[Route.JobRequestDetail.argument] ?: -1
    private val _uiState = MutableStateFlow<JobRequestDetailUiState>(JobRequestDetailUiState.Loading)
    val uiState: StateFlow<JobRequestDetailUiState> = _uiState.asStateFlow()
    private val _effects = Channel<JobRequestDetailEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var loadJob: Job? = null
    private var acceptanceJob: Job? = null

    init {
        if (savedStateHandle.contains(Route.JobRequestDetail.argument)) load()
    }

    fun open(id: Int) {
        if (acceptanceJob?.isActive == true) return
        requestId = id
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
        acceptanceJob = viewModelScope.launch {
            when (val outcome = acceptJobRequest(request.id)) {
                is AcceptJobRequestOutcome.Success -> {
                    _effects.send(
                        JobRequestDetailEffect.Accepted(
                            requestId = outcome.requestId,
                            conversationId = outcome.conversationId,
                        ),
                    )
                }
                AcceptJobRequestOutcome.Failure.Conflict,
                AcceptJobRequestOutcome.Failure.NotFound ->
                    _uiState.value = JobRequestDetailUiState.AcceptUnavailable(request)
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
        loadJob?.cancel()
        _uiState.value = JobRequestDetailUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = when (val outcome = getPendingJobRequest(requestId)) {
                is JobRequestDetailOutcome.Success -> JobRequestDetailUiState.Ready(outcome.request)
                JobRequestDetailOutcome.NotFound -> JobRequestDetailUiState.NotFound
                is JobRequestDetailOutcome.Failure -> JobRequestDetailUiState.Error
            }
        }
    }
}
