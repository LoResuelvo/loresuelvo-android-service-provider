package com.loresuelvo.serviceprovider.ui.home

import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder

data class ProviderHomeUiState(
    val jobRequests: ActivitySectionState<JobRequest> = ActivitySectionState.Loading,
    val scheduledWork: ActivitySectionState<WorkOrder> = ActivitySectionState.Loading,
    val refreshingRequests: Boolean = false,
    val refreshingWork: Boolean = false,
)

sealed interface ActivitySectionState<out T> {
    data object Loading : ActivitySectionState<Nothing>
    data class Ready<T>(val items: List<T>, val refreshFailed: Boolean = false) : ActivitySectionState<T>
    data object Error : ActivitySectionState<Nothing>
}
