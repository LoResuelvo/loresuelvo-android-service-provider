package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.di.StatisticsModule
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.ui.statistics.ActivityTestSessionStore
import java.time.Instant
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import org.junit.Assert.*
import retrofit2.Retrofit

class ApiProviderCollectionsRepositoryTest {
    private val query = ActivityQuery(Instant.parse("2026-10-03T10:00:00Z"), Instant.parse("2026-10-03T12:00:00Z"))
    private fun repository(server: MockWebServer): ApiProviderCollectionsRepository {
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(ActivityTestSessionStore())).build()
        return ApiProviderCollectionsRepository(StatisticsModule.createCollectionsApi(
            Retrofit.Builder().baseUrl(server.url("/")).client(client).build()))
    }
    @Test fun `private authenticated request preserves exact contractual cents without querying payment account`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Cache-Control", "private, no-store").setBody(payload))
            val result = repository(server).getCollections(query) as CollectionsOutcome.Success
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/providers/me/statistics/collections", request.requestUrl!!.encodedPath)
            assertEquals(query.from.toString(), request.requestUrl!!.queryParameter("from"))
            assertEquals(query.to.toString(), request.requestUrl!!.queryParameter("to"))
            assertEquals("day", request.requestUrl!!.queryParameter("granularity"))
            assertEquals("false", request.requestUrl!!.queryParameter("compare_previous"))
            assertNull(request.requestUrl!!.queryParameter("provider_id"))
            assertEquals("Bearer test-token", request.getHeader("Authorization"))
            assertEquals("no-store", request.getHeader("Cache-Control"))
            assertEquals(9007199254740993L, result.collections.results.totalCents)
            assertEquals(CollectionAmounts(9007199254740990L, 3, 9007199254740993L), result.collections.results)
            assertEquals(CurrentCollectionPending(PendingCollectionBalance(3, 210000), PendingCollectionBalance(2, 90000)),
                result.collections.currentPending)
            assertEquals(1, server.requestCount)
            assertNull(result.collections.comparison)
        }
    }
    @Test fun `zero period does not erase current pending service balances`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(payload.replace("9007199254740990", "0")
                .replace("9007199254740993", "0").replace("\"service_balance_cents\":3", "\"service_balance_cents\":0")))
            val result = repository(server).getCollections(query) as CollectionsOutcome.Success
            assertEquals(CollectionAmounts(0, 0, 0), result.collections.results)
            assertEquals(210000L, result.collections.currentPending.scheduled.amountCents)
        }
    }
    @Test fun `HTTP failures remain typed and never become empty collections`() = runTest {
        MockWebServer().use { server ->
            val repo = repository(server)
            listOf(400 to CollectionsOutcome.Failure.InvalidQuery, 401 to CollectionsOutcome.Failure.Unauthorized,
                403 to CollectionsOutcome.Failure.Forbidden, 404 to CollectionsOutcome.Failure.NotFound,
                500 to CollectionsOutcome.Failure.Server(500)).forEach { (status, expected) ->
                server.enqueue(MockResponse().setResponseCode(status))
                assertEquals(expected, repo.getCollections(query))
            }
        }
    }
    @Test fun `malformed missing money dates currency pending and inconsistent totals are rejected`() = runTest {
        MockWebServer().use { server ->
            val repo = repository(server)
            listOf("{}", payload.replace("ARS", "USD"), payload.replace("9007199254740990", "-1"),
                payload.replace("9007199254740993", "9007199254740994"),
                payload.replace("\"orders\":3", "\"orders\":-1"),
                payload.replace("\"amount_cents\":90000", "\"amount_cents\":-1"),
                payload.replace("2026-10-03T10:00:00Z", "2026-10-03"),
                payload.replace("\"currency\":\"ARS\",", ""),
                payload.replace("\"booking_deposit_cents\":9007199254740990,", ""),
                payload.replace("\"evolution\":[{", "\"evolution\":[],\"ignored\":[{"),
                payload.replace("9007199254740990", "9223372036854775807"),
                payload.replace("9007199254740990", "1.5")).forEach { body ->
                server.enqueue(MockResponse().setBody(body))
                assertEquals(CollectionsOutcome.Failure.Malformed, repo.getCollections(query))
            }
            server.enqueue(MockResponse().setBody(payload))
            assertEquals(CollectionsOutcome.Failure.Malformed, repo.getCollections(query.copy(from = query.from.minusSeconds(1))))
        }
    }
    @Test fun `shared granularity comparison query accepts null percentage without rounding money`() = runTest {
        MockWebServer().use { server ->
            val repo = repository(server)
            ActivityGranularity.entries.forEach { grouping ->
                val name = grouping.name.lowercase()
                val body = payload.replace("\"granularity\":\"day\"", "\"granularity\":\"$name\"").trimEnd().dropLast(1) +
                    """, "comparison":{"period":{"from":"2026-10-03T08:00:00Z","to":"2026-10-03T10:00:00Z",
                    "granularity":"$name","time_zone":"America/Argentina/Buenos_Aires"},
                    "results":{"booking_deposit_cents":0,"service_balance_cents":0,"total_cents":0},
                    "changes":{"booking_deposit_cents":{"absolute":9007199254740990,"percentage":null},
                    "service_balance_cents":{"absolute":3,"percentage":null},
                    "total_cents":{"absolute":9007199254740993,"percentage":null}}}}"""
                server.enqueue(MockResponse().setBody(body))
                val result = repo.getCollections(query.copy(granularity = grouping, comparePrevious = true)) as CollectionsOutcome.Success
                val request = server.takeRequest()
                assertEquals(name, request.requestUrl!!.queryParameter("granularity"))
                assertEquals("true", request.requestUrl!!.queryParameter("compare_previous"))
                assertEquals(9007199254740993L, result.collections.comparison!!.changes.totalCents.absolute)
                assertNull(result.collections.comparison!!.changes.totalCents.percentage)
            }
            server.enqueue(MockResponse().setBody(payload))
            assertEquals(CollectionsOutcome.Failure.Malformed, repo.getCollections(query.copy(comparePrevious = true)))
        }
    }
    @Test fun `network IO remains a failure and cancellation is propagated`() = runTest {
        val api = object : ProviderCollectionsApi {
            var cancel = false
            override suspend fun getCollections(from: String, to: String, granularity: String,
                comparePrevious: Boolean): com.loresuelvo.serviceprovider.data.api.dto.ProviderCollectionsDto {
                if (cancel) throw kotlinx.coroutines.CancellationException("Cancelled test request")
                throw java.io.IOException("Offline test transport")
            }
        }
        val repo = ApiProviderCollectionsRepository(api)
        assertEquals(CollectionsOutcome.Failure.Network, repo.getCollections(query))
        api.cancel = true
        try { repo.getCollections(query); fail("Cancellation must propagate") }
        catch (_: kotlinx.coroutines.CancellationException) { }
    }
    private val payload = """{
      "period":{"from":"2026-10-03T10:00:00Z","to":"2026-10-03T12:00:00Z",
        "granularity":"day","time_zone":"America/Argentina/Buenos_Aires"},
      "calculated_at":"2026-10-03T12:00:00Z","currency":"ARS",
      "results":{"booking_deposit_cents":9007199254740990,"service_balance_cents":3,"total_cents":9007199254740993},
      "evolution":[{"from":"2026-10-03T10:00:00Z","to":"2026-10-03T12:00:00Z",
        "booking_deposit_cents":9007199254740990,"service_balance_cents":3,"total_cents":9007199254740993}],
      "current_pending":{"scheduled":{"orders":3,"amount_cents":210000},
        "awaiting_payment":{"orders":2,"amount_cents":90000}}
    }"""
}
