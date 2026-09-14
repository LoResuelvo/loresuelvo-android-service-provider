package com.loresuelvo.serviceprovider.domain.usecase.activity

import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.JobRequestDetailOutcome
import com.loresuelvo.serviceprovider.domain.activity.JobRequestRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetPendingJobRequestUseCaseTest {

    @Test
    fun returns_the_requested_pending_item() = runTest {
        val expected = JobRequest(7, "Ana Pérez", "Reparar pérdida", "En la cocina")

        val result = GetPendingJobRequestUseCase(FakeRepository(
            ActivityLoadOutcome.Success(listOf(expected)),
        ))(7)

        assertEquals(JobRequestDetailOutcome.Success(expected), result)
    }

    @Test
    fun returns_not_found_when_the_item_is_not_pending() = runTest {
        val result = GetPendingJobRequestUseCase(FakeRepository(
            ActivityLoadOutcome.Success(emptyList()),
        ))(7)

        assertEquals(JobRequestDetailOutcome.NotFound, result)
    }

    @Test
    fun preserves_repository_failures() = runTest {
        val result = GetPendingJobRequestUseCase(FakeRepository(
            ActivityLoadOutcome.Failure.Server(503),
        ))(7)

        assertTrue(result is JobRequestDetailOutcome.Failure.Server)
        assertEquals(503, (result as JobRequestDetailOutcome.Failure.Server).code)
    }

    @Test
    fun rejects_non_positive_ids_without_calling_the_repository() = runTest {
        val repository = FakeRepository(ActivityLoadOutcome.Success(emptyList()))

        val result = GetPendingJobRequestUseCase(repository)(0)

        assertEquals(JobRequestDetailOutcome.Failure.Invalid, result)
        assertEquals(0, repository.calls)
    }

    private class FakeRepository(
        private val outcome: ActivityLoadOutcome<JobRequest>,
    ) : JobRequestRepository {
        var calls = 0

        override suspend fun getPendingJobRequests(): ActivityLoadOutcome<JobRequest> {
            calls++
            return outcome
        }
    }
}
