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

class ApiProviderActivityRepositoryTest {
    private val query = ActivityQuery(Instant.parse("2026-10-03T10:00:00Z"), Instant.parse("2026-10-03T12:00:00Z"))
    private fun repository(server: MockWebServer): ApiProviderActivityRepository {
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(ActivityTestSessionStore())).build()
        return ApiProviderActivityRepository(StatisticsModule.createActivityApi(
            Retrofit.Builder().baseUrl(server.url("/")).client(client).build()))
    }
    @Test fun `authenticated private request preserves exact period cents and pending totals`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Cache-Control", "private, no-store").setBody(payload))
            val result = repository(server).getActivity(query) as ActivityOutcome.Success
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/providers/me/statistics/activity", request.requestUrl!!.encodedPath)
            assertEquals("day", request.requestUrl!!.queryParameter("granularity"))
            assertEquals("false", request.requestUrl!!.queryParameter("compare_previous"))
            assertEquals(query.from.toString(), request.requestUrl!!.queryParameter("from"))
            assertEquals(query.to.toString(), request.requestUrl!!.queryParameter("to"))
            assertEquals("Bearer test-token", request.getHeader("Authorization"))
            assertEquals("no-store", request.getHeader("Cache-Control"))
            assertEquals(9007199254740993L, result.activity.results.agreedValueCents)
            assertEquals(9007199254740993L, result.activity.results.averageValueCents)
            assertEquals(CurrentPending(2, 7, 4), result.activity.currentPending)
            assertEquals(query.from, result.activity.period.from)
        }
    }
    @Test fun `zero completions require zero agreed amount and an undefined average`() = runTest {
        MockWebServer().use { server ->
            val zero = payload.replace("9007199254740993", "0")
                .replace("\"reported_completions\":1", "\"reported_completions\":0")
                .replace("\"clients_served\":1", "\"clients_served\":0")
                .replace("\"new_clients\":1", "\"new_clients\":0")
                .replace("\"average_value_cents\":0", "\"average_value_cents\":null")
            server.enqueue(MockResponse().setBody(zero))
            val repo = repository(server)
            val result = repo.getActivity(query) as ActivityOutcome.Success
            assertNull(result.activity.results.averageValueCents)
            assertEquals(0L, result.activity.results.agreedValueCents)
            server.enqueue(MockResponse().setBody(zero.replace("\"average_value_cents\":null", "\"average_value_cents\":0")))
            assertEquals(ActivityOutcome.Failure.Malformed, repo.getActivity(query))
        }
    }
    @Test fun `HTTP failures remain typed`() = runTest {
        MockWebServer().use { server ->
            val repo = repository(server)
            listOf(400 to ActivityOutcome.Failure.InvalidQuery, 401 to ActivityOutcome.Failure.Unauthorized,
                403 to ActivityOutcome.Failure.Forbidden, 404 to ActivityOutcome.Failure.NotFound,
                500 to ActivityOutcome.Failure.Server(500)).forEach { (status, expected) ->
                server.enqueue(MockResponse().setResponseCode(status))
                assertEquals(expected, repo.getActivity(query))
            }
        }
    }
    @Test fun `malformed required fields values and timestamps never become zero activity`() = runTest {
        MockWebServer().use { server ->
            val repo = repository(server)
            listOf("{}", payload.replace("9007199254740993", "-1"), payload.replace("ARS", "USD"),
                payload.replace("\"clients_served\":1", "\"clients_served\":2"),
                payload.replace("\"reported_completions\":1", "\"reported_completions\":0"),
                payload.replace("\"average_value_cents\":9007199254740993", "\"average_value_cents\":null"),
                payload.replace("\"average_value_cents\":9007199254740993", "\"average_value_cents\":1"),
                payload.replace("2026-10-03T07:00:00-03:00", "2026-09-03"),
                payload.replace("\"average_value_cents\":9007199254740993,", ""),
                payload.replace("\"evolution\":[{", "\"evolution\":[],\"ignored\":[{")).forEach { body ->
                server.enqueue(MockResponse().setBody(body))
                assertEquals(ActivityOutcome.Failure.Malformed, repo.getActivity(query))
            }
        }
    }

    @Test fun `all groupings and comparison are sent and undefined changes survive DTO mapping`() = runTest {
        MockWebServer().use { server ->
            val repo = repository(server)
            ActivityGranularity.entries.forEach { grouping ->
                val name = grouping.name.lowercase(java.util.Locale.ROOT)
                server.enqueue(MockResponse().setBody(comparisonPayload(name)))
                val result = repo.getActivity(query.copy(granularity = grouping, comparePrevious = true)) as ActivityOutcome.Success
                val request = server.takeRequest()
                assertEquals(name, request.requestUrl!!.queryParameter("granularity"))
                assertEquals("true", request.requestUrl!!.queryParameter("compare_previous"))
                val comparison = requireNotNull(result.activity.comparison)
                assertEquals(query.from, comparison.period.to)
                assertEquals(java.time.Duration.between(query.from, query.to),
                    java.time.Duration.between(comparison.period.from, comparison.period.to))
                assertEquals(9007199254740993L, comparison.changes.agreedValueCents.absolute)
                assertNull(comparison.changes.reportedCompletions.percentage)
                assertNull(comparison.results.averageValueCents)
                assertNull(comparison.changes.averageValueCents.absolute)
                assertNull(comparison.changes.averageValueCents.percentage)
                assertEquals(CurrentPending(2, 7, 4), result.activity.currentPending)
            }
        }
    }

    @Test fun `missing comparison invalid previous periods and nullable count deltas are rejected`() = runTest {
        MockWebServer().use { server ->
            val repo = repository(server)
            val withComparison = comparisonPayload("day")
            listOf(payload, withComparison.replace("2026-10-03T08:00:00Z", "2026-10-03T07:00:00Z"),
                withComparison.replace("\"absolute\":0", "\"absolute\":null")).forEach { body ->
                server.enqueue(MockResponse().setBody(body))
                assertEquals(ActivityOutcome.Failure.Malformed, repo.getActivity(query.copy(comparePrevious = true)))
            }
        }
    }


    @Test fun `signed differences and nonzero percentages remain distinct from unavailable changes`() = runTest {
        MockWebServer().use { server ->
            val body = comparisonPayload("day").replace("\"confirmed_bookings\":0,\"reported_completions\":0",
                "\"confirmed_bookings\":2,\"reported_completions\":0")
                .replace("\"confirmed_bookings\":{\"absolute\":0,\"percentage\":null}",
                    "\"confirmed_bookings\":{\"absolute\":-2,\"percentage\":-100.0}")
            server.enqueue(MockResponse().setBody(body))
            val result = repository(server).getActivity(query.copy(comparePrevious = true)) as ActivityOutcome.Success
            assertEquals(2L, result.activity.comparison!!.results.confirmedBookings)
            assertEquals(-2L, result.activity.comparison!!.changes.confirmedBookings.absolute)
            assertEquals(-100.0, result.activity.comparison!!.changes.confirmedBookings.percentage!!, 0.0)
        }
    }

    private fun comparisonPayload(granularity: String): String {
        val current = payload.replace("\"granularity\":\"day\"", "\"granularity\":\"$granularity\"")
        val previousResults = """{"confirmed_bookings":0,"reported_completions":0,"fully_paid_work_orders":0,
            "clients_served":0,"new_clients":0,"returning_clients":0,"agreed_value_cents":0,
            "average_value_cents":null,"currency":"ARS"}"""
        val changes = """{"confirmed_bookings":{"absolute":0,"percentage":null},
            "reported_completions":{"absolute":1,"percentage":null},
            "fully_paid_work_orders":{"absolute":0,"percentage":null},
            "clients_served":{"absolute":1,"percentage":null},"new_clients":{"absolute":1,"percentage":null},
            "returning_clients":{"absolute":0,"percentage":null},
            "agreed_value_cents":{"absolute":9007199254740993,"percentage":null},
            "average_value_cents":{"absolute":null,"percentage":null}}"""
        return current.trimEnd().dropLast(1) + """, "comparison": {
            "period":{"from":"2026-10-03T08:00:00Z","to":"2026-10-03T10:00:00Z",
            "granularity":"$granularity","time_zone":"America/Argentina/Buenos_Aires"},
            "results":$previousResults,"changes":$changes}}"""
    }
    private val payload = """{
      "period":{"from":"2026-10-03T07:00:00-03:00","to":"2026-10-03T12:00:00Z",
        "granularity":"day","time_zone":"America/Argentina/Buenos_Aires"},
      "calculated_at":"2026-10-03T12:00:00Z",
      "results":{"confirmed_bookings":0,"reported_completions":1,"fully_paid_work_orders":0,
        "clients_served":1,"new_clients":1,"returning_clients":0,"agreed_value_cents":9007199254740993,
        "average_value_cents":9007199254740993,"currency":"ARS"},
      "evolution":[{"from":"2026-10-03T07:00:00-03:00","to":"2026-10-03T12:00:00Z",
        "confirmed_bookings":0,"reported_completions":1,"fully_paid_work_orders":0}],
      "current_pending":{"requests":2,"scheduled_orders":7,"awaiting_payment_orders":4}
    }"""
}
