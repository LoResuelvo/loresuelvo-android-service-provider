package com.loresuelvo.serviceprovider.domain.usecase.activity

import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.JobRequestRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetPendingJobRequestsUseCase @Inject constructor(
    private val repository: JobRequestRepository,
) {
    suspend operator fun invoke(): ActivityLoadOutcome<JobRequest> =
        repository.getPendingJobRequests()
}
