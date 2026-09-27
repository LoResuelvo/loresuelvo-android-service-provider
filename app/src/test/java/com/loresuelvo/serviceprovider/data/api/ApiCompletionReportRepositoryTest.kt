package com.loresuelvo.serviceprovider.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.loresuelvo.serviceprovider.domain.activity.PostCompletionReportOutcome
import com.loresuelvo.serviceprovider.di.NetworkModule
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class ApiCompletionReportRepositoryTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { runCatching { server.shutdown() } }

    @Test fun posts_ordered_confirmed_ids_and_reads_report_id_from_201() = runTest {
        server.enqueue(MockResponse().setResponseCode(201)
            .setHeader("Content-Type", "application/json")
            .setHeader("Location", "/work-orders/42")
            .setBody("""{"id":17,"description":"Done","reported_on":"2026-09-27T15:00:00Z","images":[{"file_id":"file-3","original_name":"three.jpg","url":"https://storage/three.jpg"},{"file_id":"file-1","original_name":"one.jpg","url":"https://storage/one.jpg"}]}"""))

        val outcome = repository().postCompletionReport(42, "Done", listOf("file-3", "file-1"))

        assertEquals(PostCompletionReportOutcome.Success(17), outcome)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/work-orders/42/completion-reports", request.path)
        assertEquals("application/json", request.getHeader("Content-Type")?.substringBefore(';'))
        assertEquals("""{"description":"Done","image_file_ids":["file-3","file-1"]}""",
            request.body.readUtf8())
        assertEquals(1, server.requestCount)
    }

    @Test fun maps_rejections_without_automatic_retry() = runTest {
        val cases = listOf(
            400 to PostCompletionReportOutcome.Rejected.InvalidData,
            401 to PostCompletionReportOutcome.Rejected.Unauthorized,
            403 to PostCompletionReportOutcome.Rejected.Forbidden,
            404 to PostCompletionReportOutcome.Rejected.NotFound,
            409 to PostCompletionReportOutcome.Rejected.Conflict,
            503 to PostCompletionReportOutcome.Uncertain.Server(503),
        )
        for ((code, expected) in cases) {
            server.enqueue(MockResponse().setResponseCode(code).setBody("{}"))
            assertEquals(expected, repository().postCompletionReport(42, "Done", listOf("file-1")))
            assertEquals("POST", server.takeRequest().method)
        }
        assertEquals(cases.size, server.requestCount)
    }

    @Test fun transport_failure_does_not_claim_success() = runTest {
        server.shutdown()
        assertTrue(repository().postCompletionReport(42, "Done", listOf("file-1")) is
            PostCompletionReportOutcome.Uncertain.Network)
    }

    @Test fun authenticated_client_sends_one_post_after_connection_drop() = runTest {
        val session = mockk<AuthSessionStore>()
        every { session.getSession() } returns AuthSession(User("provider", "provider@example.test"), "test-token")
        val client = NetworkModule.provideOkHttpClient(AuthInterceptor(session))
        assertFalse(client.retryOnConnectionFailure)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        assertEquals(PostCompletionReportOutcome.Uncertain.Network,
            repository(client).postCompletionReport(42, "Done", listOf("file-1")))
        assertEquals("Bearer test-token", server.takeRequest().getHeader("Authorization"))
        assertEquals(1, server.requestCount)
    }

    @Test fun malformed_201_is_uncertain_because_report_may_exist() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        assertEquals(PostCompletionReportOutcome.Uncertain.InvalidResponse,
            repository().postCompletionReport(42, "Done", listOf("file-1")))
    }

    private fun repository(client: OkHttpClient = OkHttpClient()) = ApiWorkOrderRepository(Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(client)
        .addConverterFactory(Json { ignoreUnknownKeys = true; explicitNulls = false }
            .asConverterFactory("application/json".toMediaType()))
        .build().create(BackendApi::class.java))
}
