package com.loresuelvo.serviceprovider.ui.home

import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder

data class ProviderHomeUiState(
    val jobRequests: ActivitySectionState<JobRequest> = ActivitySectionState.Loading,
    val scheduledWork: ActivitySectionState<WorkOrder> = ActivitySectionState.Loading,
)

sealed interface ActivitySectionState<out T> {
    data object Loading : ActivitySectionState<Nothing>
    data class Ready<T>(val items: List<T>) : ActivitySectionState<T>
    data object Error : ActivitySectionState<Nothing>
}
