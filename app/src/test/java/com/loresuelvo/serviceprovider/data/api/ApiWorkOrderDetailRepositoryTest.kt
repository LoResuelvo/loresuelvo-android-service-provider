package com.loresuelvo.serviceprovider.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class ApiWorkOrderDetailRepositoryTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    @Test fun reads_current_detail_without_inventing_a_counterpart() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(detail()))

        val result = repository().getWorkOrder(42) as WorkOrderDetailOutcome.Success

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/work-orders/42", request.path)
        assertEquals(42, result.order.id)
        assertEquals(10, result.order.serviceProposalId)
        assertEquals(3, result.order.consumerId)
        assertEquals(7, result.order.providerId)
        assertEquals(Instant.parse("2026-08-15T15:00:00Z").toEpochMilli(), result.order.scheduledOn)
        assertEquals(null, result.order.completionReportId)
    }

    @Test fun maps_existing_report_presence_and_current_status() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(detail(
            status = "awaiting_payment",
            report = ",\"completion_report\":{\"id\":17,\"description\":\"Done\",\"reported_on\":\"2026-08-15T16:00:00Z\",\"images\":[]}",
        )))

        val result = repository().getWorkOrder(42) as WorkOrderDetailOutcome.Success
        assertEquals(17, result.order.completionReportId)
        assertEquals(com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus.AwaitingPayment, result.order.status)
    }

    @Test fun maps_completion_description_time_and_images_in_received_order() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(detail(status = "paid", report = """,
            "completion_report":{"id":17,"description":"Installed the new valve","reported_on":"2026-08-15T13:00:00-03:00",
            "images":[{"file_id":"first","original_name":"one.jpg","url":"https://storage.test/one"},
            {"file_id":"second","original_name":"two.jpg","url":"https://storage.test/two"}]}
        """.trimIndent())))

        val current = (repository().getWorkOrder(42) as WorkOrderDetailOutcome.Success).order
        assertEquals(17, current.completionReportId)
        assertEquals("Installed the new valve", current.completionReport?.description)
        assertEquals(Instant.parse("2026-08-15T16:00:00Z").toEpochMilli(), current.completionReport?.reportedOn)
        assertEquals(listOf("first", "second"), current.completionReport?.images?.map { it.fileId })
        assertEquals(listOf("one.jpg", "two.jpg"), current.completionReport?.images?.map { it.originalName })
    }

    @Test fun malformed_optional_report_time_does_not_discard_valid_order_or_report_identity() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(detail(status = "awaiting_payment",
            report = ",\"completion_report\":{\"id\":17,\"description\":\"Done\",\"reported_on\":\"bad-date\",\"images\":[]}")))

        val current = (repository().getWorkOrder(42) as WorkOrderDetailOutcome.Success).order
        assertEquals(17, current.completionReportId)
        assertEquals(null, current.completionReport?.reportedOn)
        assertEquals(emptyList<String>(), current.completionReport?.images?.map { it.fileId })
    }

    @Test fun maps_auth_permission_missing_server_and_network_failures() = runTest {
        val expected = listOf(
            401 to WorkOrderDetailOutcome.Failure.Unauthorized,
            403 to WorkOrderDetailOutcome.Failure.Forbidden,
            404 to WorkOrderDetailOutcome.Failure.NotFound,
            503 to WorkOrderDetailOutcome.Failure.Server(503),
        )
        for ((code, failure) in expected) {
            server.enqueue(MockResponse().setResponseCode(code).setBody("{}"))
            assertEquals(failure, repository().getWorkOrder(42))
            server.takeRequest()
        }
        server.shutdown()
        assertTrue(repository().getWorkOrder(42) is WorkOrderDetailOutcome.Failure.Network)
    }

    private fun repository() = ApiWorkOrderRepository(Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(Json { ignoreUnknownKeys = true; explicitNulls = false }
            .asConverterFactory("application/json".toMediaType()))
        .build().create(BackendApi::class.java))

    private fun detail(status: String = "scheduled", report: String = "") = """
        {"id":42,"service_proposal_id":10,"consumer_id":3,"provider_id":7,
        "amount_cents":10000000,"scheduled_on":"2026-08-15T15:00:00Z",
        "description":"Repair the tap","status":"$status","accepted_on":"2026-08-01T13:00:00Z"$report}
    """.trimIndent()
}
