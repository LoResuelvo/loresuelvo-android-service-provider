package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.di.NetworkModule
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ProposalValidationError
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ApiServiceProposalRepositoryTest {
    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private val proposal = ValidatedServiceProposal(7, "100.5", 1_780_000_000_000L, 0, "Inspect sink", 45)

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun repository(): ApiServiceProposalRepository {
        val session = mockk<AuthSessionStore>()
        every { session.getSession() } returns AuthSession(User("provider", "provider@example.test"), "test-token")
        val client = NetworkModule.provideOkHttpClient(AuthInterceptor(session))
        return ApiServiceProposalRepository(
            NetworkModule.createServiceProposalApi(server.url("/").toString(), client, json), json,
        )
    }

    @Test fun `201 pending validates response and sends exact peso request`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(pendingResponse()))

        assertEquals(CreateServiceProposalOutcome.Created(9), repository().create(proposal))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/service-proposals", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        val body = json.parseToJsonElement(request.body.readUtf8()) as JsonObject
        assertEquals(setOf("consumer_id", "amount", "scheduled_on", "description", "estimated_duration_minutes"), body.keys)
        assertEquals(JsonPrimitive(7), body.getValue("consumer_id"))
        assertEquals(JsonPrimitive("100.5"), body.getValue("amount"))
        assertEquals(JsonPrimitive("2026-05-28T20:26:40.000Z"), body.getValue("scheduled_on"))
        assertEquals(JsonPrimitive("Inspect sink"), body.getValue("description"))
        assertEquals(JsonPrimitive(45), body.getValue("estimated_duration_minutes"))
    }

    @Test fun `malformed or unexpected success never confirms creation`() = runTest {
        listOf(200 to pendingResponse(), 201 to "{}", 201 to pendingResponse().replace("pending", "accepted"),
            500 to "{}")
            .forEach { (code, body) ->
                server.enqueue(MockResponse().setResponseCode(code).setBody(body))
                assertEquals(CreateServiceProposalOutcome.Failure.Uncertain, repository().create(proposal))
            }
        assertEquals(4, server.requestCount)
    }

    @Test fun `minimum peso and duration bounds reach the wire without rounding`() = runTest {
        listOf(15, 1440).forEach { minutes ->
            val candidate = proposal.copy(amountPesos = "0.01", durationMinutes = minutes)
            server.enqueue(MockResponse().setResponseCode(201).setBody(
                pendingResponse().replace("10050", "1").replace("\"deposit_cents\":1000", "\"deposit_cents\":0")
                    .replace("\"remaining_service_balance_cents\":9050", "\"remaining_service_balance_cents\":1")
                    .replace("\"estimated_duration_minutes\":45", "\"estimated_duration_minutes\":$minutes")))
            assertEquals(CreateServiceProposalOutcome.Created(9), repository().create(candidate))
            val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
            assertEquals(JsonPrimitive("0.01"), body.getValue("amount"))
            assertEquals(JsonPrimitive(minutes), body.getValue("estimated_duration_minutes"))
        }
    }

    @Test fun `known field and payment errors are typed but unknown conflict stays generic`() = runTest {
        val cases = listOf(
            Triple(400, "Amount must be greater than 0", CreateServiceProposalOutcome.Failure.Invalid(setOf(ProposalValidationError.Amount))),
            Triple(409, "A connected payment account is required before creating a service proposal", CreateServiceProposalOutcome.Failure.PaymentRequired),
            Triple(409, "other", CreateServiceProposalOutcome.Failure.Conflict),
        )
        cases.forEach { (code, error, expected) ->
            server.enqueue(MockResponse().setResponseCode(code).setBody("{\"error\":\"$error\"}"))
            assertEquals(expected, repository().create(proposal))
        }
    }

    @Test fun `disconnect and recoverable statuses never replay the POST`() = runTest {
        val cases = listOf(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) to CreateServiceProposalOutcome.Failure.Uncertain,
            MockResponse().setResponseCode(408) to CreateServiceProposalOutcome.Failure.Uncertain,
            MockResponse().setResponseCode(503).addHeader("Retry-After", "0") to CreateServiceProposalOutcome.Failure.Uncertain,
            MockResponse().setResponseCode(401) to CreateServiceProposalOutcome.Failure.SessionExpired,
            MockResponse().setResponseCode(307).addHeader("Location", "/service-proposals") to CreateServiceProposalOutcome.Failure.Uncertain,
        )
        cases.forEach { (response, expected) ->
            server.shutdown()
            server = MockWebServer().apply { start() }
            val before = server.requestCount
            server.enqueue(response)
            server.enqueue(MockResponse().setResponseCode(201).setBody(pendingResponse()))
            assertEquals(expected, repository().create(proposal))
            assertEquals(before + 1, server.requestCount)
        }
    }

    private fun pendingResponse() = """{
      "id":9,"conversation_id":42,"consumer_id":7,"provider_id":3,
      "amount_cents":10050,"scheduled_on":"2026-05-28T20:26:40Z",
      "description":"Inspect sink","status":"pending","estimated_duration_minutes":45,
      "booking_terms":{"currency":"ARS","service_total_cents":10050,"deposit_cents":1000,
        "remaining_service_balance_cents":9050,"platform_fee_total_cents":500,
        "platform_fee_due_now_cents":100,"remaining_platform_fee_cents":400,
        "amount_due_now_cents":1100,"remaining_amount_due_cents":9450,
        "contract_total_cents":10550,"booking_payment_deadline":"2026-05-29T20:26:40Z"}
    }"""
}
