package com.loresuelvo.serviceprovider.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.loresuelvo.serviceprovider.data.api.dto.CurrentAccountDto
import com.loresuelvo.serviceprovider.data.api.mapper.toDomain
import com.loresuelvo.serviceprovider.domain.account.CalendarConnectionStatus
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.calendar.ConnectCalendarOutcome
import com.loresuelvo.serviceprovider.domain.usecase.calendar.ConnectCalendarUseCase
import com.loresuelvo.serviceprovider.ui.profile.calendar.ProfileCalendarFixture
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit

class ApiCalendarConnectionRepositoryTest {
    @Test
    fun authenticated_post_uses_exact_code_body_and_only_201_is_submitted() = runTest {
        MockWebServer().use { server ->
            val store = ProfileCalendarFixture.SessionStore()
            val session = requireNotNull(store.getSession())
            val repository = ApiCalendarConnectionRepository(api(server, OkHttpClient.Builder().addInterceptor(AuthInterceptor(store)).build()))
            val outcomes = mapOf(
                201 to ConnectCalendarOutcome.Submitted,
                200 to ConnectCalendarOutcome.Unavailable,
                204 to ConnectCalendarOutcome.Unavailable,
                400 to ConnectCalendarOutcome.Rejected,
                401 to ConnectCalendarOutcome.Unauthorized,
                404 to ConnectCalendarOutcome.Rejected,
                500 to ConnectCalendarOutcome.Unavailable,
            )
            outcomes.forEach { (status, expected) ->
                server.enqueue(MockResponse().setResponseCode(status))
                assertEquals(expected, repository.connect("server-code", session))
                val request = server.takeRequest()
                assertEquals("POST", request.method)
                assertEquals("/me/calendar-connection", request.path)
                assertEquals("Bearer test-access-token", request.getHeader("Authorization"))
                assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
                assertEquals("{\"server_auth_code\":\"server-code\"}", request.body.readUtf8())
            }
        }
    }

    @Test
    fun queued_request_cannot_use_replacement_session_credentials() = runTest {
        MockWebServer().use { server ->
            val store = ProfileCalendarFixture.SessionStore()
            val previous = requireNotNull(store.getSession())
            val replacement = AuthSession(User("other", "other@example.com"), "other-token")
            val client = OkHttpClient.Builder()
                .addInterceptor { chain -> store.saveSession(replacement); chain.proceed(chain.request()) }
                .addInterceptor(AuthInterceptor(store)).build()
            val useCase = ConnectCalendarUseCase(ApiCalendarConnectionRepository(api(server, client)), store)
            assertEquals(ConnectCalendarOutcome.Unauthorized, useCase("obsolete-code", previous))
            assertEquals(0, server.requestCount)
            assertEquals(replacement, store.getSession())
        }
    }

    @Test
    fun transport_failure_is_recoverable_and_cancellation_propagates() = runTest {
        val api = mockk<BackendApi>()
        val session = requireNotNull(ProfileCalendarFixture.SessionStore().getSession())
        val repository = ApiCalendarConnectionRepository(api)
        coEvery { api.connectCalendar(any(), any()) } throws java.io.IOException("offline")
        assertEquals(ConnectCalendarOutcome.Unavailable, repository.connect("code", session))
        val cancelled = CancellationException("cancelled")
        coEvery { api.connectCalendar(any(), any()) } throws cancelled
        try {
            repository.connect("code", session)
            org.junit.Assert.fail("Cancellation must propagate")
        } catch (error: CancellationException) {
            assertTrue(error === cancelled)
        }
    }

    @Test
    fun current_account_maps_calendar_wire_status_and_defaults_fail_closed() {
        val json = Json { ignoreUnknownKeys = true }
        val cases = mapOf(
            "disconnected" to CalendarConnectionStatus.Disconnected,
            "connected" to CalendarConnectionStatus.Connected,
            "action_required" to CalendarConnectionStatus.ActionRequired,
            "unexpected" to CalendarConnectionStatus.Unavailable,
        )
        cases.forEach { (wire, expected) ->
            val dto = json.decodeFromString<CurrentAccountDto>(profileJson("\"$wire\""))
            assertEquals(wire, dto.calendarConnectionStatus)
            assertEquals(expected, (dto.toDomain() as CurrentAccount.Provider).calendarConnectionStatus)
        }
        listOf(profileJson("null"), profileJson("null").replace(",\"calendar_connection_status\":null", "")).forEach { body ->
            val provider = json.decodeFromString<CurrentAccountDto>(body).toDomain() as CurrentAccount.Provider
            assertEquals(CalendarConnectionStatus.Unavailable, provider.calendarConnectionStatus)
            assertTrue(!provider.calendarConnectionStatus.canAuthorize)
        }
    }

    private fun profileJson(status: String) = """{"id":20,"name":"Juan","surname":"Gomez","email":"juan@example.com","role":"provider","category":{"id":1,"name":"Plumbing"},"calendar_connection_status":$status}"""

    private fun api(server: MockWebServer, client: OkHttpClient): BackendApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(client)
        .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
        .build().create(BackendApi::class.java)
}
