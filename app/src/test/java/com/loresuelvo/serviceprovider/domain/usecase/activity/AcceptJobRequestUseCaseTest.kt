package com.loresuelvo.serviceprovider.domain.usecase.activity

import com.loresuelvo.serviceprovider.domain.activity.AcceptJobRequestOutcome
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.JobRequestRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AcceptJobRequestUseCaseTest {

    @Test
    fun delegates_a_valid_request_id() = runTest {
        val repository = FakeRepository()
        val expected = AcceptJobRequestOutcome.Success(7, 11)
        repository.acceptOutcome = expected

        assertEquals(expected, AcceptJobRequestUseCase(repository)(7))
        assertEquals(7, repository.acceptedId)
    }

    @Test
    fun rejects_a_non_positive_request_id_without_calling_the_repository() = runTest {
        val repository = FakeRepository()

        assertEquals(
            AcceptJobRequestOutcome.Failure.Invalid,
            AcceptJobRequestUseCase(repository)(0),
        )
        assertEquals(null, repository.acceptedId)
    }

    private class FakeRepository : JobRequestRepository {
        var acceptOutcome: AcceptJobRequestOutcome = AcceptJobRequestOutcome.Failure.Invalid
        var acceptedId: Int? = null

        override suspend fun getPendingJobRequests(): ActivityLoadOutcome<JobRequest> =
            ActivityLoadOutcome.Success(emptyList())

        override suspend fun acceptJobRequest(id: Int): AcceptJobRequestOutcome {
            acceptedId = id
            return acceptOutcome
        }
    }
}
