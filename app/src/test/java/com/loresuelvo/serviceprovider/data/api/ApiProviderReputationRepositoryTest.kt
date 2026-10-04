package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.ProviderReputationDto
import com.loresuelvo.serviceprovider.di.StatisticsModule
import com.loresuelvo.serviceprovider.domain.statistics.ReputationOutcome
import com.loresuelvo.serviceprovider.ui.statistics.ActivityTestSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import org.junit.Assert.*
import retrofit2.Retrofit

class ApiProviderReputationRepositoryTest {
    private fun repository(server: MockWebServer) = ApiProviderReputationRepository(StatisticsModule.createReputationApi(
        Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().addInterceptor(AuthInterceptor(ActivityTestSessionStore())).build()).build()))

    @Test fun `private lifetime request uses only limit and preserves aggregate sample and actual review content`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Cache-Control", "private, no-store").setBody(payload))
            val result = (repository(server).getReputation() as ReputationOutcome.Success).reputation
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/providers/me/statistics/reputation?limit=20", request.path)
            assertEquals("Bearer test-token", request.getHeader("Authorization"))
            assertEquals("no-store", request.getHeader("Cache-Control"))
            assertEquals(24L, result.reviewCount); assertEquals(24L, result.reviewedPaidOrders)
            assertEquals(30L, result.eligiblePaidOrders); assertEquals(80.0, result.coveragePercentage!!, 0.0)
            assertEquals(24L, result.ratingDistribution.sumOf { it.count })
            assertEquals(4.63, result.averageRating!!, 0.0)
            assertEquals(listOf(184, 179), result.reviews.map { it.workOrderId })
            assertEquals(listOf("Actual comment", ""), result.reviews.map { it.description })
            assertEquals("opaque+/cursor=", result.nextCursor); assertEquals(1, server.requestCount)
        }
    }
    @Test fun `explicit null averages and unavailable or zero coverage remain distinct`() = runTest {
        MockWebServer().use { server ->
            val repo = repository(server)
            for (eligible in listOf(0, 3)) {
                val body = emptyPayload.replace("ELIGIBLE", eligible.toString())
                    .replace("COVERAGE", if (eligible == 0) "null" else "0")
                server.enqueue(MockResponse().setBody(body))
                val result = (repo.getReputation() as ReputationOutcome.Success).reputation
                assertNull(result.averageRating); assertEquals(0L, result.reviewCount)
                assertTrue(result.reviews.isEmpty()); assertTrue(result.ratingDistribution.all { it.count == 0L })
                if (eligible == 0) assertNull(result.coveragePercentage)
                else assertEquals(0.0, result.coveragePercentage!!, 0.0)
            }
        }
    }
    @Test fun `HTTP failures stay typed and never become invented empty results`() = runTest {
        MockWebServer().use { server ->
            val repo = repository(server)
            listOf(400 to ReputationOutcome.Failure.InvalidQuery, 401 to ReputationOutcome.Failure.Unauthorized,
                403 to ReputationOutcome.Failure.Forbidden, 404 to ReputationOutcome.Failure.NotFound,
                500 to ReputationOutcome.Failure.Server(500)).forEach { (code, expected) ->
                server.enqueue(MockResponse().setResponseCode(code)); assertEquals(expected, repo.getReputation())
            }
        }
    }
    @Test fun `malformed required fields counts distribution ratings order timestamp and cursor are rejected`() = runTest {
        MockWebServer().use { server ->
            val repo = repository(server)
            val malformed = listOf("{}", payload.replace("2026-10-03T12:00:00Z", "2026-10-03"),
                payload.replace("2026-10-03T12:00:00Z", "2026-10-03T12:00:00-03:00"),
                payload.replace("4.63", "null"), payload.replace("4.63", "5.1"),
                payload.replace("4.63", "0.9"), payload.replace("4.63", "\"invalid\""),
                payload.replace("\"review_count\":24", "\"review_count\":-1"),
                payload.replace("\"review_count\":24", "\"review_count\":25"),
                payload.replace("\"eligible_paid_orders\":30", "\"eligible_paid_orders\":23"),
                payload.replace("\"reviewed_paid_orders\":24", "\"reviewed_paid_orders\":23"),
                payload.replace("\"coverage_percentage\":80", "\"coverage_percentage\":null"),
                payload.replace("\"coverage_percentage\":80", "\"coverage_percentage\":101"),
                payload.replace("\"coverage_percentage\":80", "\"coverage_percentage\":-1"),
                payload.replace("{\"rating\":1,\"count\":0},", ""),
                payload.replace("{\"rating\":1,\"count\":0}", "{\"rating\":2,\"count\":0}"),
                payload.replace("\"count\":17", "\"count\":18"),
                payload.replace("\"count\":17", "\"count\":-1"),
                payload.replace("\"count\":17", "\"count\":1.5"),
                payload.replace("\"work_order_id\":184", "\"work_order_id\":0"),
                payload.replace("\"work_order_id\":179", "\"work_order_id\":184"),
                payload.replace("\"work_order_id\":179", "\"work_order_id\":185"),
                payload.replace("\"rating\":4,\"description\"", "\"rating\":6,\"description\""),
                payload.replace("\"description\":\"\"", "\"description\":null"),
                payload.replace("opaque+/cursor=", ""),
                payload.replace("opaque+/cursor=", "c".repeat(4097)),
                payload.replace("Actual comment", "d".repeat(501)),
                payload.replace("\"average_rating\":4.63,", ""),
                payload.replace("\"next_cursor\":\"opaque+/cursor=\"", "\"ignored\":null"))
            malformed.forEachIndexed { index, body ->
                server.enqueue(MockResponse().setBody(body))
                assertEquals("Malformed case $index", ReputationOutcome.Failure.Malformed, repo.getReputation())
            }
            val overflow = payload.replace("\"count\":0", "\"count\":9223372036854775807")
            server.enqueue(MockResponse().setBody(overflow))
            assertEquals(ReputationOutcome.Failure.Malformed, repo.getReputation())
        }
    }
    @Test fun `no eligible orders reject supplied coverage and zero reviews reject supplied average`() = runTest {
        MockWebServer().use { server ->
            val repo = repository(server)
            listOf(emptyPayload.replace("ELIGIBLE", "0").replace("COVERAGE", "0"),
                emptyPayload.replace("ELIGIBLE", "3").replace("COVERAGE", "20"),
                emptyPayload.replace("ELIGIBLE", "0").replace("COVERAGE", "null")
                    .replace("\"average_rating\":null", "\"average_rating\":4")).forEach { body ->
                server.enqueue(MockResponse().setBody(body)); assertEquals(ReputationOutcome.Failure.Malformed, repo.getReputation())
            }
        }
    }
    @Test fun `network failures remain typed and cancellation propagates`() = runTest {
        val api = object : ProviderReputationApi {
            var cancelled = false
            override suspend fun getReputation(limit: Int): ProviderReputationDto {
                if (cancelled) throw CancellationException("Cancelled test request")
                throw java.io.IOException("Offline test transport")
            }
        }
        val repo = ApiProviderReputationRepository(api)
        assertEquals(ReputationOutcome.Failure.Network, repo.getReputation())
        api.cancelled = true
        try { repo.getReputation(); fail("Cancellation must propagate") } catch (_: CancellationException) { }
    }
    private val payload = """{
      "calculated_at":"2026-10-03T12:00:00Z","average_rating":4.63,"review_count":24,
      "rating_distribution":[{"rating":1,"count":0},{"rating":2,"count":0},{"rating":3,"count":2},
        {"rating":4,"count":5},{"rating":5,"count":17}],
      "eligible_paid_orders":30,"reviewed_paid_orders":24,"coverage_percentage":80,
      "reviews":[{"work_order_id":184,"rating":5,"description":"Actual comment"},
        {"work_order_id":179,"rating":4,"description":""}],"next_cursor":"opaque+/cursor="
    }"""
    private val emptyPayload = """{
      "calculated_at":"2026-10-03T12:00:00Z","average_rating":null,"review_count":0,
      "rating_distribution":[{"rating":1,"count":0},{"rating":2,"count":0},{"rating":3,"count":0},
        {"rating":4,"count":0},{"rating":5,"count":0}],
      "eligible_paid_orders":ELIGIBLE,"reviewed_paid_orders":0,"coverage_percentage":COVERAGE,
      "reviews":[],"next_cursor":null
    }"""
}
