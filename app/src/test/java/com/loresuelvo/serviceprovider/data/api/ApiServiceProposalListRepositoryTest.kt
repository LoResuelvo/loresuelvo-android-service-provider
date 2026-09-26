package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.di.NetworkModule
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ApiServiceProposalListRepositoryTest {
    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun repository(): ApiServiceProposalRepository {
        val session = mockk<AuthSessionStore>()
        every { session.getSession() } returns AuthSession(User("provider", "provider@example.test"), "test-token")
        val client = NetworkModule.provideOkHttpClient(AuthInterceptor(session))
        return ApiServiceProposalRepository(
            NetworkModule.createServiceProposalApi(server.url("/").toString(), client, json),
            json,
        )
    }

    @Test fun `GET uses authenticated bodyless request and decodes distinct summary`() = runTest {
        server.enqueue(MockResponse().setBody("[${item(12)}]"))

        val result = repository().list() as ServiceProposalListOutcome.Success
        val proposal = result.proposals.single()
        assertEquals(12, proposal.id)
        assertEquals(93, proposal.conversationId)
        assertEquals(1500050L, proposal.amountCents)
        assertEquals(ServiceProposalStatus.Pending, proposal.status)
        assertEquals("Ana Pérez", "${proposal.counterpart.name} ${proposal.counterpart.surname}")
        assertEquals("2026-09-21T12:00:00Z", java.time.Instant.ofEpochMilli(proposal.createdOnEpochMillis).toString())
        assertEquals(1500550L, proposal.bookingTerms.contractTotalCents)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/service-proposals", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        assertNull(request.getHeader("Content-Length"))
        assertEquals(0, request.bodySize)
    }

    @Test fun `empty and failed responses stay typed`() = runTest {
        val repository = repository()
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(ServiceProposalListOutcome.Success(emptyList()), repository.list())
        assertEquals(ServiceProposalListOutcome.Failure.SessionExpired, repository.list())
        assertEquals(ServiceProposalListOutcome.Failure.Unavailable, repository.list())
    }

    @Test fun `unknown status and malformed timestamps are invalid responses`() = runTest {
        val repository = repository()
        server.enqueue(MockResponse().setBody("[${item(12).replace("pending", "unknown")}]"))
        server.enqueue(MockResponse().setBody("[${item(12).replace("2026-09-21T12:00:00Z", "bad")}]"))
        assertEquals(ServiceProposalListOutcome.Failure.InvalidResponse, repository.list())
        assertEquals(ServiceProposalListOutcome.Failure.InvalidResponse, repository.list())
    }

    @Test fun `invalid amount and duration are invalid responses`() = runTest {
        val repository = repository()
        server.enqueue(MockResponse().setBody("[${item(12).replace("\"amount_cents\":1500050", "\"amount_cents\":0")}]"))
        server.enqueue(MockResponse().setBody("[${item(12).replace("\"estimated_duration_minutes\":45", "\"estimated_duration_minutes\":14")}]"))
        server.enqueue(MockResponse().setBody("[${item(12).replace("\"estimated_duration_minutes\":45,", "")}]"))
        assertEquals(ServiceProposalListOutcome.Failure.InvalidResponse, repository.list())
        assertEquals(ServiceProposalListOutcome.Failure.InvalidResponse, repository.list())
        assertEquals(ServiceProposalListOutcome.Failure.InvalidResponse, repository.list())
    }

    private fun item(id: Int) = """{
      "id":$id,"conversation_id":93,"amount_cents":1500050,
      "scheduled_on":"2026-10-06T12:00:00Z","description":"Inspect sink",
      "estimated_duration_minutes":45,"status":"pending","created_on":"2026-09-21T12:00:00Z",
      "counterpart":{"id":7,"role":"consumer","name":"Ana","surname":"Pérez",
        "category_name":null,"profile_photo_url":null},
      "booking_terms":{"currency":"ARS","service_total_cents":1500050,"deposit_cents":1000,
        "remaining_service_balance_cents":1499050,"platform_fee_total_cents":500,
        "platform_fee_due_now_cents":100,"remaining_platform_fee_cents":400,
        "amount_due_now_cents":1100,"remaining_amount_due_cents":1499450,
        "contract_total_cents":1500550,"booking_payment_deadline":"2026-09-30T12:00:00Z"}
    }"""
}
