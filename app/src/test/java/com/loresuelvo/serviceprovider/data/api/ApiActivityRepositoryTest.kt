package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.JobRequestRequesterDto
import com.loresuelvo.serviceprovider.data.api.dto.JobRequestImageDto
import com.loresuelvo.serviceprovider.data.api.dto.JobRequestSummaryDto
import com.loresuelvo.serviceprovider.data.api.dto.WorkOrderCounterpartDto
import com.loresuelvo.serviceprovider.data.api.dto.WorkOrderSummaryDto
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.AcceptJobRequestOutcome
import com.loresuelvo.serviceprovider.domain.activity.JobRequestImage
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import kotlin.test.assertFailsWith

class ApiActivityRepositoryTest {

    private val backendApi = mockk<BackendApi>()

    @Test
    fun job_requests_maps_successful_response() = runTest {
        coEvery { backendApi.getJobRequests() } returns listOf(
            JobRequestSummaryDto(
                id = 1,
                conversationId = 2,
                title = "Reparar pérdida",
                description = "Debajo de la pileta",
                status = "pending",
                requester = JobRequestRequesterDto("Ana", "Pérez"),
                images = listOf(JobRequestImageDto("image-1", "https://cdn.example/image-1", "pileta.jpg")),
            ),
        )

        val result = ApiJobRequestRepository(backendApi).getPendingJobRequests()

        val request = (result as ActivityLoadOutcome.Success).items.single()
        assertEquals("Ana Pérez", request.consumerName)
        assertEquals(
            listOf(JobRequestImage("image-1", "https://cdn.example/image-1", "pileta.jpg")),
            request.images,
        )
    }

    @Test
    fun work_orders_maps_awaiting_payment_without_treating_it_as_transport_failure() = runTest {
        coEvery { backendApi.getWorkOrders() } returns listOf(workOrder("awaiting_payment"))

        val result = ApiWorkOrderRepository(backendApi).getWorkOrders()

        assertEquals(1, (result as ActivityLoadOutcome.Success).items.size)
    }

    @Test
    fun server_failures_are_typed_for_both_activity_sources() = runTest {
        val response = Response.error<List<JobRequestSummaryDto>>(
            503,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        coEvery { backendApi.getJobRequests() } throws HttpException(response)
        coEvery { backendApi.getWorkOrders() } throws HttpException(
            Response.error<List<WorkOrderSummaryDto>>(
                500,
                "{}".toResponseBody("application/json".toMediaType()),
            ),
        )

        assertEquals(
            ActivityLoadOutcome.Failure.Server(503),
            ApiJobRequestRepository(backendApi).getPendingJobRequests(),
        )
        assertEquals(
            ActivityLoadOutcome.Failure.Server(500),
            ApiWorkOrderRepository(backendApi).getWorkOrders(),
        )
    }

    @Test
    fun accepts_a_pending_request_and_maps_the_confirmed_conversation() = runTest {
        coEvery { backendApi.acceptJobRequest(7) } returns acceptedRequest()

        val result = ApiJobRequestRepository(backendApi).acceptJobRequest(7)

        assertEquals(AcceptJobRequestOutcome.Success(7, 11), result)
    }

    @Test
    fun maps_an_accept_conflict_without_exposing_http_details_to_the_domain() = runTest {
        coEvery { backendApi.acceptJobRequest(7) } throws HttpException(
            Response.error<JobRequestSummaryDto>(
                409,
                "{}".toResponseBody("application/json".toMediaType()),
            ),
        )

        assertEquals(
            AcceptJobRequestOutcome.Failure.Conflict,
            ApiJobRequestRepository(backendApi).acceptJobRequest(7),
        )
    }

    @Test
    fun network_failures_are_preserved_and_cancellation_is_rethrown() = runTest {
        val network = IOException("offline")
        coEvery { backendApi.getJobRequests() } throws network
        coEvery { backendApi.getWorkOrders() } throws CancellationException("cancelled")

        val result = ApiJobRequestRepository(backendApi).getPendingJobRequests()
        assertTrue(result is ActivityLoadOutcome.Failure.Network)
        assertEquals(network, (result as ActivityLoadOutcome.Failure.Network).cause)
        assertFailsWith<CancellationException> {
            ApiWorkOrderRepository(backendApi).getWorkOrders()
        }
    }

    private fun workOrder(status: String) = WorkOrderSummaryDto(
        id = 3,
        serviceProposalId = 4,
        amountCents = 15000,
        scheduledOn = "2026-09-20T15:00:00Z",
        description = "Revisar instalación",
        status = status,
        acceptedOn = "2026-09-19T10:00:00Z",
        counterpart = WorkOrderCounterpartDto(
            id = 5,
            role = "consumer",
            name = "Ana",
            surname = "Pérez",
        ),
    )

    private fun acceptedRequest() = JobRequestSummaryDto(
        id = 7,
        conversationId = 11,
        title = "Reparar pérdida",
        description = "Debajo de la pileta",
        status = "accepted",
        requester = JobRequestRequesterDto("Ana", "Pérez"),
    )
}
