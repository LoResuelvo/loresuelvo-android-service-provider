package com.loresuelvo.serviceprovider.ui.jobrequest

import com.loresuelvo.serviceprovider.domain.activity.JobRequest

sealed interface JobRequestDetailUiState {
    data object Loading : JobRequestDetailUiState
    data class Ready(val request: JobRequest) : JobRequestDetailUiState
    data class Accepting(val request: JobRequest) : JobRequestDetailUiState
    data class AcceptError(val request: JobRequest) : JobRequestDetailUiState
    data object NotFound : JobRequestDetailUiState
    data object Error : JobRequestDetailUiState
}
