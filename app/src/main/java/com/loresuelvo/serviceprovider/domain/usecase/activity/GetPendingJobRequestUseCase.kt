package com.loresuelvo.serviceprovider.domain.usecase.activity

import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.JobRequestDetailOutcome
import com.loresuelvo.serviceprovider.domain.activity.JobRequestRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetPendingJobRequestUseCase @Inject constructor(
    private val repository: JobRequestRepository,
) {
    suspend operator fun invoke(id: Int): JobRequestDetailOutcome {
        if (id <= 0) return JobRequestDetailOutcome.Failure.Invalid

        return when (val outcome = repository.getPendingJobRequests()) {
            is ActivityLoadOutcome.Success -> outcome.items
                .firstOrNull { it.id == id }
                ?.let(JobRequestDetailOutcome::Success)
                ?: JobRequestDetailOutcome.NotFound
            ActivityLoadOutcome.Failure.Unauthorized -> JobRequestDetailOutcome.Failure.Unauthorized
            is ActivityLoadOutcome.Failure.Network ->
                JobRequestDetailOutcome.Failure.Network(outcome.cause)
            is ActivityLoadOutcome.Failure.Server ->
                JobRequestDetailOutcome.Failure.Server(outcome.code)
            ActivityLoadOutcome.Failure.Invalid -> JobRequestDetailOutcome.Failure.Invalid
        }
    }
}
