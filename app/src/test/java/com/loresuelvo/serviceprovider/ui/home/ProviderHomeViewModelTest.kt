package com.loresuelvo.serviceprovider.ui.home

import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.AcceptJobRequestOutcome
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.JobRequestRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetPendingJobRequestsUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetScheduledWorkUseCase
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderHomeViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private lateinit var jobRequests: FakeJobRequestRepository
    private lateinit var workOrders: FakeWorkOrderRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        jobRequests = FakeJobRequestRepository()
        workOrders = FakeWorkOrderRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loads_both_activity_sections_independently_and_derives_counts() = runTest(scheduler) {
        jobRequests.next = ActivityLoadOutcome.Success(listOf(jobRequest()))
        workOrders.next = ActivityLoadOutcome.Success(listOf(workOrder()))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            listOf("Reparar pérdida"),
            (viewModel.uiState.value.jobRequests as ActivitySectionState.Ready)
                .items.map { it.title },
        )
        assertEquals(
            1,
            (viewModel.uiState.value.scheduledWork as ActivitySectionState.Ready)
                .items.size,
        )
        assertEquals(1, jobRequests.calls)
        assertEquals(1, workOrders.calls)
    }

    @Test
    fun keeps_the_other_section_when_one_request_fails_and_retries_only_that_section() =
        runTest(scheduler) {
            jobRequests.outcomes += ActivityLoadOutcome.Failure.Server(503)
            jobRequests.outcomes += ActivityLoadOutcome.Success(listOf(jobRequest()))
            workOrders.next = ActivityLoadOutcome.Success(listOf(workOrder()))

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.jobRequests is ActivitySectionState.Error)
            assertTrue(viewModel.uiState.value.scheduledWork is ActivitySectionState.Ready)

            viewModel.retryJobRequests()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.jobRequests is ActivitySectionState.Ready)
            assertTrue(viewModel.uiState.value.scheduledWork is ActivitySectionState.Ready)
            assertEquals(2, jobRequests.calls)
            assertEquals(1, workOrders.calls)
        }

    @Test
    fun removes_a_resolved_request_from_the_current_pending_section() = runTest(scheduler) {
        val first = jobRequest()
        val second = jobRequest().copy(id = 2)
        jobRequests.next = ActivityLoadOutcome.Success(listOf(first, second))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.removeJobRequest(first.id)

        assertEquals(listOf(second), (viewModel.uiState.value.jobRequests as ActivitySectionState.Ready).items)
    }

    private fun createViewModel() = ProviderHomeViewModel(
        getPendingJobRequests = GetPendingJobRequestsUseCase(jobRequests),
        getScheduledWork = GetScheduledWorkUseCase(workOrders),
    )

    private fun jobRequest() = JobRequest(
        id = 1,
        consumerName = "Ana Pérez",
        title = "Reparar pérdida",
        description = "Pérdida debajo de la pileta",
    )

    private fun workOrder() = WorkOrder(
        id = 2,
        consumerName = "Ana Pérez",
        description = "Reparar pérdida",
        scheduledOn = Instant.parse("2026-09-20T15:00:00Z").toEpochMilli(),
        status = WorkOrderStatus.Scheduled,
    )

    private class FakeJobRequestRepository : JobRequestRepository {
        var next: ActivityLoadOutcome<JobRequest> = ActivityLoadOutcome.Success(emptyList())
        val outcomes = ArrayDeque<ActivityLoadOutcome<JobRequest>>()
        var calls = 0

        override suspend fun getPendingJobRequests(): ActivityLoadOutcome<JobRequest> {
            calls++
            return if (outcomes.isEmpty()) next else outcomes.removeFirst()
        }

        override suspend fun acceptJobRequest(id: Int): AcceptJobRequestOutcome =
            AcceptJobRequestOutcome.Failure.Invalid
    }

    private class FakeWorkOrderRepository : WorkOrderRepository {
        var next: ActivityLoadOutcome<WorkOrder> = ActivityLoadOutcome.Success(emptyList())
        var calls = 0

        override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> {
            calls++
            return next
        }
    }
}
