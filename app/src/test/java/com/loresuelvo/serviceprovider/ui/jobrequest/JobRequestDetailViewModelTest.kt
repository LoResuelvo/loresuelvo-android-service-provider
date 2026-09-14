package com.loresuelvo.serviceprovider.ui.jobrequest

import androidx.lifecycle.SavedStateHandle
import com.loresuelvo.serviceprovider.domain.activity.AcceptJobRequestOutcome
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.JobRequestRepository
import com.loresuelvo.serviceprovider.domain.usecase.activity.AcceptJobRequestUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetPendingJobRequestUseCase
import com.loresuelvo.serviceprovider.ui.navigation.Route
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JobRequestDetailViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loads_the_pending_request_identified_by_the_restored_route_argument() = runTest(scheduler) {
        val request = JobRequest(7, "Ana Pérez", "Reparar pérdida", "En la cocina")
        val viewModel = createViewModel(ActivityLoadOutcome.Success(listOf(request)))

        advanceUntilIdle()

        assertEquals(JobRequestDetailUiState.Ready(request), viewModel.uiState.value)
    }

    @Test
    fun exposes_unavailable_when_the_request_is_no_longer_pending() = runTest(scheduler) {
        val viewModel = createViewModel(ActivityLoadOutcome.Success(emptyList()))

        advanceUntilIdle()

        assertEquals(JobRequestDetailUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun retry_reloads_after_a_temporary_failure() = runTest(scheduler) {
        val request = JobRequest(7, "Ana Pérez", "Reparar pérdida", "En la cocina")
        val repository = FakeRepository(
            ActivityLoadOutcome.Failure.Server(503),
            ActivityLoadOutcome.Success(listOf(request)),
        )
        val viewModel = createViewModel(repository)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is JobRequestDetailUiState.Error)

        viewModel.retry()
        advanceUntilIdle()

        assertEquals(JobRequestDetailUiState.Ready(request), viewModel.uiState.value)
        assertEquals(2, repository.calls)
    }

    @Test
    fun accepts_once_and_emits_the_confirmed_conversation() = runTest(scheduler) {
        val request = JobRequest(7, "Ana Pérez", "Reparar pérdida", "En la cocina")
        val repository = FakeRepository(ActivityLoadOutcome.Success(listOf(request)))
        repository.acceptOutcomes += AcceptJobRequestOutcome.Success(7, 11)
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        val effect = async { viewModel.effects.first() }

        viewModel.accept()
        viewModel.accept()
        advanceUntilIdle()

        assertEquals(1, repository.acceptCalls)
        assertEquals(JobRequestDetailEffect.Accepted(7, 11), effect.await())
        assertTrue(viewModel.uiState.value is JobRequestDetailUiState.Accepting)
    }

    @Test
    fun retries_a_failed_acceptance_without_reloading_the_detail() = runTest(scheduler) {
        val request = JobRequest(7, "Ana Pérez", "Reparar pérdida", "En la cocina")
        val repository = FakeRepository(ActivityLoadOutcome.Success(listOf(request)))
        repository.acceptOutcomes += AcceptJobRequestOutcome.Failure.Network(IllegalStateException("offline"))
        repository.acceptOutcomes += AcceptJobRequestOutcome.Success(7, 11)
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.accept()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is JobRequestDetailUiState.AcceptError)

        val effect = async { viewModel.effects.first() }
        viewModel.retryAccept()
        advanceUntilIdle()

        assertEquals(2, repository.acceptCalls)
        assertEquals(JobRequestDetailEffect.Accepted(7, 11), effect.await())
    }

    private fun createViewModel(
        outcome: ActivityLoadOutcome<JobRequest>,
    ): JobRequestDetailViewModel = createViewModel(FakeRepository(outcome))

    private fun createViewModel(repository: FakeRepository) = JobRequestDetailViewModel(
        SavedStateHandle(mapOf(Route.JobRequestDetail.argument to 7)),
        GetPendingJobRequestUseCase(repository),
        AcceptJobRequestUseCase(repository),
    )

    private class FakeRepository(
        private vararg val outcomes: ActivityLoadOutcome<JobRequest>,
    ) : JobRequestRepository {
        var calls = 0
        var acceptCalls = 0
        val acceptOutcomes = ArrayDeque<AcceptJobRequestOutcome>()

        override suspend fun getPendingJobRequests(): ActivityLoadOutcome<JobRequest> {
            return outcomes.getOrElse(calls++) { outcomes.last() }
        }

        override suspend fun acceptJobRequest(id: Int): AcceptJobRequestOutcome {
            acceptCalls++
            return if (acceptOutcomes.isEmpty()) {
                AcceptJobRequestOutcome.Failure.Invalid
            } else {
                acceptOutcomes.removeFirst()
            }
        }
    }
}
