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
