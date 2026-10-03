package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.di.StatisticsModule
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.ui.statistics.ActivityTestSessionStore
import java.time.Instant
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit

class ApiCollectionTransactionsRepositoryTest {
    private val query = CollectionTransactionsQuery(Instant.parse("2026-10-03T10:00:00Z"), Instant.parse("2026-10-03T12:00:00Z"))
    private fun repository(server: MockWebServer) = ApiCollectionTransactionsRepository(StatisticsModule.createTransactionsApi(
        Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(ActivityTestSessionStore())).build()).build()))

    @Test fun `private GET keeps opaque cursor filter and exact int64 contractual amount with nullable order`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(payload))
            val requested = query.copy(purpose = CollectionPurpose.BOOKING_DEPOSIT, cursor = "opaque+/=&?token")
            val result = repository(server).getTransactions(requested) as CollectionTransactionsOutcome.Success
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/providers/me/statistics/collections/transactions", request.requestUrl!!.encodedPath)
            assertEquals(requested.from.toString(), request.requestUrl!!.queryParameter("from"))
            assertEquals(requested.to.toString(), request.requestUrl!!.queryParameter("to"))
            assertEquals("booking_deposit", request.requestUrl!!.queryParameter("purpose"))
            assertEquals("20", request.requestUrl!!.queryParameter("limit"))
            assertEquals(requested.cursor, request.requestUrl!!.queryParameter("cursor"))
            assertNull(request.requestUrl!!.queryParameter("granularity"))
            assertNull(request.requestUrl!!.queryParameter("compare_previous"))
            assertEquals("no-store", request.getHeader("Cache-Control"))
            assertEquals("Bearer test-token", request.getHeader("Authorization"))
            val row = result.page.transactions.single()
            assertEquals(9007199254740993L, row.sellerAmountCents)
            assertEquals(17, row.serviceProposalId); assertNull(row.workOrderId)
            assertEquals(50L, result.page.totalCount); assertEquals(9007199254740994L, result.page.totalAmountCents)
            assertEquals("next+/=", result.page.nextCursor)
        }
    }
    @Test fun `all filter omits purpose and terminal cursor is nullable with order reference`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(payload.replace("\"work_order_id\":null", "\"work_order_id\":18")
                .replace("\"next_cursor\":\"next+/=\"", "\"next_cursor\":null")))
            val result = repository(server).getTransactions(query) as CollectionTransactionsOutcome.Success
            assertNull(server.takeRequest().requestUrl!!.queryParameter("purpose"))
            assertEquals(18, result.page.transactions.single().workOrderId); assertNull(result.page.nextCursor)
        }
    }
    @Test fun `HTTP failures stay typed including invalid cursor and never synthesize zero totals`() = runTest {
        MockWebServer().use { server ->
            val repo = repository(server)
            listOf(400 to CollectionsOutcome.Failure.InvalidQuery, 401 to CollectionsOutcome.Failure.Unauthorized,
                403 to CollectionsOutcome.Failure.Forbidden, 404 to CollectionsOutcome.Failure.NotFound,
                500 to CollectionsOutcome.Failure.Server(500)).forEach { (status, expected) ->
                server.enqueue(MockResponse().setResponseCode(status))
                assertEquals(CollectionTransactionsOutcome.Failure(expected), repo.getTransactions(query.copy(cursor = "invalid")))
            }
        }
    }
    @Test fun `missing fields malformed currency purpose dates totals ids and filter mismatch are rejected`() = runTest {
        MockWebServer().use { server ->
            val repo = repository(server)
            listOf("{}", payload.replace("ARS", "USD"), payload.replace("booking_deposit", "unknown"),
                payload.replace("\"seller_amount_cents\":9007199254740993,", ""),
                payload.replace("\"work_order_id\":null", "\"work_order_id\":0"),
                payload.replace("\"service_proposal_id\":17", "\"service_proposal_id\":-1"),
                payload.replace("\"id\":3", "\"id\":0"),
                payload.replace("\"total_count\":50", "\"total_count\":0"),
                payload.replace("9007199254740994", "1"),
                payload.replace("9007199254740993", "-1"),
                payload.replace("2026-10-03T11:00:00Z", "2026-10-03T12:00:00Z"),
                payload.replace("\"next_cursor\":\"next+/=\"", "\"next_cursor\":\"\""),
                payload.replace("2026-10-03T10:00:00Z", "2026-10-03")).forEach { body ->
                server.enqueue(MockResponse().setBody(body))
                assertEquals(CollectionTransactionsOutcome.Failure(CollectionsOutcome.Failure.Malformed), repo.getTransactions(query))
            }
            server.enqueue(MockResponse().setBody(payload))
            assertEquals(CollectionTransactionsOutcome.Failure(CollectionsOutcome.Failure.Malformed),
                repo.getTransactions(query.copy(purpose = CollectionPurpose.SERVICE_BALANCE)))
            server.enqueue(MockResponse().setBody(payload))
            assertEquals(CollectionTransactionsOutcome.Failure(CollectionsOutcome.Failure.Malformed),
                repo.getTransactions(query.copy(cursor = "next+/=")))
        }
    }
    @Test fun `network failure is typed and coroutine cancellation propagates`() = runTest {
        val api = object : CollectionTransactionsApi {
            var cancel = false
            override suspend fun getTransactions(from: String, to: String, purpose: String?, limit: Int,
                cursor: String?): com.loresuelvo.serviceprovider.data.api.dto.CollectionTransactionsDto {
                if (cancel) throw kotlinx.coroutines.CancellationException("Cancelled test request")
                throw java.io.IOException("Offline test transport")
            }
        }
        assertEquals(CollectionTransactionsOutcome.Failure(CollectionsOutcome.Failure.Network),
            ApiCollectionTransactionsRepository(api).getTransactions(query))
        api.cancel = true
        try { ApiCollectionTransactionsRepository(api).getTransactions(query); fail("Cancellation must propagate") }
        catch (_: kotlinx.coroutines.CancellationException) { }
    }
    private val payload = """{
      "period":{"from":"2026-10-03T10:00:00Z","to":"2026-10-03T12:00:00Z","time_zone":"America/Argentina/Buenos_Aires"},
      "calculated_at":"2026-10-03T12:00:00Z","currency":"ARS","total_count":50,"total_amount_cents":9007199254740994,
      "transactions":[{"id":3,"verified_on":"2026-10-03T11:00:00Z","purpose":"booking_deposit",
        "seller_amount_cents":9007199254740993,"currency":"ARS","service_proposal_id":17,"work_order_id":null}],
      "next_cursor":"next+/="
    }"""
}
