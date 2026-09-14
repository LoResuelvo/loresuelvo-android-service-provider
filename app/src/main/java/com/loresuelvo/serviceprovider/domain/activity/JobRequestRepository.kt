package com.loresuelvo.serviceprovider.domain.activity

interface JobRequestRepository {
    suspend fun getPendingJobRequests(): ActivityLoadOutcome<JobRequest>

    suspend fun acceptJobRequest(id: Int): AcceptJobRequestOutcome
}
