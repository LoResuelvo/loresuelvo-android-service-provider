package com.loresuelvo.serviceprovider.domain.usecase.activity

import com.loresuelvo.serviceprovider.domain.activity.AcceptJobRequestOutcome
import com.loresuelvo.serviceprovider.domain.activity.JobRequestRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AcceptJobRequestUseCase @Inject constructor(
    private val repository: JobRequestRepository,
) {
    suspend operator fun invoke(id: Int): AcceptJobRequestOutcome {
        if (id <= 0) return AcceptJobRequestOutcome.Failure.Invalid
        return repository.acceptJobRequest(id)
    }
}
