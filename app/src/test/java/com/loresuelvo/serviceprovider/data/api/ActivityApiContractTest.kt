package com.loresuelvo.serviceprovider.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.loresuelvo.serviceprovider.data.api.dto.JobRequestSummaryDto
import com.loresuelvo.serviceprovider.data.api.dto.WorkOrderSummaryDto
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class ActivityApiContractTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun requests_job_requests_with_bearer_and_decodes_requester() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [{
                  "id": 1,
                  "conversation_id": 2,
                  "title": "Reparar pérdida",
                  "description": "Debajo de la pileta",
                  "status": "pending",
                  "requester": {"name": "Ana", "surname": "Pérez"},
                  "images": []
                }]
                """.trimIndent(),
            ),
        )

        val requests = api().getJobRequests()
        val request = server.takeRequest()

        assertEquals("/job-requests", request.path)
        assertEquals("Bearer synthetic-token", request.getHeader("Authorization"))
        assertEquals("Ana", requests.single().requester.name)
        assertEquals("pending", requests.single().status)
    }

    @Test
    fun requests_work_orders_and_decodes_counterpart_and_tolerated_status() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [{
                  "id": 3,
                  "service_proposal_id": 4,
                  "amount_cents": 15000,
                  "scheduled_on": "2026-09-20T15:00:00Z",
                  "description": "Revisar instalación",
                  "status": "awaiting_payment",
                  "accepted_on": "2026-09-19T10:00:00Z",
                  "counterpart": {
                    "id": 5,
                    "role": "consumer",
                    "name": "Carlos",
                    "surname": "López"
                  }
                }]
                """.trimIndent(),
            ),
        )

        val workOrders = api().getWorkOrders()
        val request = server.takeRequest()

        assertEquals("/work-orders", request.path)
        assertEquals("Bearer synthetic-token", request.getHeader("Authorization"))
        assertWorkOrder(workOrders.single())
    }

    private fun api(): BackendApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(
            OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(SessionStore()))
                .build(),
        )
        .addConverterFactory(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }.asConverterFactory("application/json".toMediaType()),
        )
        .build()
        .create(BackendApi::class.java)

    private fun assertWorkOrder(dto: WorkOrderSummaryDto) {
        assertEquals(3, dto.id)
        assertEquals(15000, dto.amountCents)
        assertEquals("awaiting_payment", dto.status)
        assertNotNull(dto.counterpart)
        assertEquals("Carlos", dto.counterpart.name)
    }

    private class SessionStore : AuthSessionStore {
        private val state = MutableStateFlow<AuthSession?>(
            AuthSession(User("auth0|provider", "provider@example.com"), "synthetic-token"),
        )
        override val sessionFlow: StateFlow<AuthSession?> = state
        override fun getSession(): AuthSession? = state.value
        override fun saveSession(session: AuthSession) { state.value = session }
        override fun clearSession() { state.value = null }
    }
}
