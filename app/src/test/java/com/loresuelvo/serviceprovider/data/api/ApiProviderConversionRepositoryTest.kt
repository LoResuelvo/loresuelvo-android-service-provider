package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.ProviderConversionDto
import com.loresuelvo.serviceprovider.di.StatisticsModule
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.ui.statistics.ActivityTestSessionStore
import java.time.OffsetDateTime
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import org.junit.Assert.*
import retrofit2.Retrofit

class ApiProviderConversionRepositoryTest {
    private fun repository(server: MockWebServer) = ApiProviderConversionRepository(StatisticsModule.createConversionApi(
        Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().addInterceptor(AuthInterceptor(ActivityTestSessionStore())).build()).build()))
    @Test fun `default private authenticated query has no extraneous parameters and preserves backend ratios and nanos`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Cache-Control", "private, no-store").setBody(payload))
            val result = (repository(server).getConversion(ConversionQuery()) as ConversionOutcome.Success).conversion
            val request = server.takeRequest()
            assertEquals("GET", request.method); assertEquals("/providers/me/statistics/conversion", request.path)
            assertEquals("Bearer test-token", request.getHeader("Authorization")); assertEquals("no-store", request.getHeader("Cache-Control"))
            assertEquals(ProposalStages(20, 12, 9, 8), result.proposals.stages)
            assertEquals(ConversionRatio(8, 9, 88.89), result.proposals.rates.paid.previousStage)
            assertEquals(Instant.parse("2026-10-04T12:00:00.123456789Z"), result.observedAt)
            assertEquals(RequestAcceptance(5, 3, 2, ConversionRatio(3, 5, 60.0)), result.requests)
        }
    }
    @Test fun `explicit boundaries encode positive offset and preserve only one from and to`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(payload))
            val query = ConversionQuery(OffsetDateTime.parse("2026-09-04T15:00:00.123456789+03:00"),
                OffsetDateTime.parse("2026-10-04T15:00:00+03:00"))
            repository(server).getConversion(query)
            val request = server.takeRequest(); val url = requireNotNull(request.requestUrl)
            assertEquals(setOf("from", "to"), url.queryParameterNames)
            assertEquals(listOf("2026-09-04T15:00:00.123456789+03:00"), url.queryParameterValues("from"))
            assertEquals(listOf("2026-10-04T15:00:00+03:00"), url.queryParameterValues("to"))
            assertTrue(requireNotNull(request.path).contains("%2B03"))
        }
    }
    @Test fun `minute aligned UTC query includes seconds required by the API`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(payload))
            repository(server).getConversion(ConversionQuery(OffsetDateTime.parse("2026-09-04T12:00:00Z"),
                OffsetDateTime.parse("2026-10-04T12:00:00Z")))
            val url = requireNotNull(server.takeRequest().requestUrl)
            assertEquals("2026-09-04T12:00:00Z", url.queryParameter("from"))
            assertEquals("2026-10-04T12:00:00Z", url.queryParameter("to"))
        }
    }
    @Test fun `HTTP errors remain typed without fabricated counts`() = runTest {
        MockWebServer().use { server ->
            val repo = repository(server)
            listOf(400 to ConversionOutcome.Failure.InvalidQuery, 401 to ConversionOutcome.Failure.Unauthorized,
                403 to ConversionOutcome.Failure.Forbidden, 404 to ConversionOutcome.Failure.NotFound,
                500 to ConversionOutcome.Failure.Server(500)).forEach { (code, expected) ->
                server.enqueue(MockResponse().setResponseCode(code)); assertEquals(expected, repo.getConversion(ConversionQuery()))
            }
        }
    }
    @Test fun `required nulls malformed counts nested stages ratios times and overflow are rejected`() = runTest {
        MockWebServer().use { server ->
            val repo = repository(server)
            val malformed = listOf("{}", payload.replace("\"issued\":20", "\"issued\":null"),
                payload.replace("\"issued\":20,", ""), payload.replace("\"paid\":8", "\"paid\":-1"),
                payload.replace("\"contracted\":12", "\"contracted\":21"),
                payload.replace("\"reported\":9", "\"reported\":13"),
                payload.replace("\"uncontracted\":8", "\"uncontracted\":7"),
                payload.replace("\"pending\":2", "\"pending\":9223372036854775807"),
                payload.replace("\"numerator\":8", "\"numerator\":7"),
                payload.replace("\"denominator\":9", "\"denominator\":8"),
                payload.replace("\"percentage\":88.89", "\"percentage\":null"),
                payload.replace("\"percentage\":88.89", "\"percentage\":101"),
                payload.replace("\"percentage\":88.89", "\"percentage\":-1"),
                payload.replace("\"percentage\":88.89", "\"ignored\":88.89"),
                payload.replace("America/Argentina/Buenos_Aires", "UTC"),
                payload.replace("2026-09-04T12:00:00Z", "2026-10-05T12:00:00Z"),
                payload.replace("2026-09-04T12:00:00Z", "2025-09-04T12:00:00Z"),
                payload.replace("2026-10-04T12:00:00.123456789Z", "2026-10-03T12:00:00Z"),
                payload.replace("2026-10-04T12:00:00.123456789Z", "2026-10-04T09:00:00-03:00"))
            malformed.forEachIndexed { index, body ->
                server.enqueue(MockResponse().setBody(body))
                assertEquals("Malformed case $index", ConversionOutcome.Failure.Malformed, repo.getConversion(ConversionQuery()))
            }
        }
    }
    @Test fun `zero proposals preserve independent requests and distinguish null from zero rates`() = runTest {
        MockWebServer().use { server ->
            val repo = repository(server)
            server.enqueue(MockResponse().setBody(emptyPayload))
            val empty = (repo.getConversion(ConversionQuery()) as ConversionOutcome.Success).conversion
            assertNull(empty.proposals.rates.contracted.cohort.percentage)
            assertEquals(5L, empty.requests.received); assertEquals(60.0, empty.requests.acceptanceRate.percentage!!, 0.0)
            val positive = emptyPayload.replace("\"issued\":0", "\"issued\":5")
                .replace("\"uncontracted\":0", "\"uncontracted\":5")
                .replace("\"denominator\":0,\"percentage\":null", "\"denominator\":5,\"percentage\":0")
                .replace("\"previous_stage\":{\"numerator\":0,\"denominator\":5,\"percentage\":0}",
                    "\"previous_stage\":{\"numerator\":0,\"denominator\":0,\"percentage\":null}")
                .replaceFirst("\"previous_stage\":{\"numerator\":0,\"denominator\":0,\"percentage\":null}",
                    "\"previous_stage\":{\"numerator\":0,\"denominator\":5,\"percentage\":0}")
            server.enqueue(MockResponse().setBody(positive))
            val zero = (repo.getConversion(ConversionQuery()) as ConversionOutcome.Success).conversion
            assertEquals(0.0, zero.proposals.rates.contracted.cohort.percentage!!, 0.0)
            assertNull(zero.proposals.rates.reported.previousStage.percentage)
            server.enqueue(MockResponse().setBody(emptyPayload.replace("\"percentage\":null", "\"percentage\":0")))
            assertEquals(ConversionOutcome.Failure.Malformed, repo.getConversion(ConversionQuery()))
        }
    }
    @Test fun `backend HALF UP one of thirty two is preserved without recomputation`() = runTest {
        MockWebServer().use { server ->
            val body = emptyPayload.replace("\"issued\":0", "\"issued\":32")
                .replace("\"contracted\":0", "\"contracted\":1")
                .replace("\"uncontracted\":0", "\"uncontracted\":31")
                .replace("\"cohort\":{\"numerator\":0,\"denominator\":0,\"percentage\":null}",
                    "\"cohort\":{\"numerator\":0,\"denominator\":32,\"percentage\":0}")
                .replaceFirst("\"cohort\":{\"numerator\":0,\"denominator\":32,\"percentage\":0}",
                    "\"cohort\":{\"numerator\":1,\"denominator\":32,\"percentage\":3.13}")
                .replaceFirst("\"previous_stage\":{\"numerator\":0,\"denominator\":0,\"percentage\":null}",
                    "\"previous_stage\":{\"numerator\":1,\"denominator\":32,\"percentage\":3.13}")
                .replaceFirst("\"previous_stage\":{\"numerator\":0,\"denominator\":0,\"percentage\":null}",
                    "\"previous_stage\":{\"numerator\":0,\"denominator\":1,\"percentage\":0}")
            server.enqueue(MockResponse().setBody(body))
            assertEquals(3.13, (repository(server).getConversion(ConversionQuery()) as ConversionOutcome.Success)
                .conversion.proposals.rates.contracted.cohort.percentage!!, 0.0)
        }
    }
    @Test fun `network failure is typed and cancellation propagates`() = runTest {
        val api = object : ProviderConversionApi {
            var cancel = false
            override suspend fun getConversion(from: String?, to: String?): ProviderConversionDto {
                if (cancel) throw CancellationException("Cancelled test request")
                throw java.io.IOException("Offline test transport")
            }
        }
        val repo = ApiProviderConversionRepository(api)
        assertEquals(ConversionOutcome.Failure.Network, repo.getConversion(ConversionQuery()))
        api.cancel = true
        try { repo.getConversion(ConversionQuery()); fail("Cancellation must propagate") } catch (_: CancellationException) { }
    }
    private val payload = """{
      "period":{"from":"2026-09-04T12:00:00Z","to":"2026-10-04T12:00:00Z","time_zone":"America/Argentina/Buenos_Aires"},
      "observed_at":"2026-10-04T12:00:00.123456789Z",
      "proposals":{"stages":{"issued":20,"contracted":12,"reported":9,"paid":8},"uncontracted":8,
        "rates":{"contracted":{"cohort":{"numerator":12,"denominator":20,"percentage":60},"previous_stage":{"numerator":12,"denominator":20,"percentage":60}},
          "reported":{"cohort":{"numerator":9,"denominator":20,"percentage":45},"previous_stage":{"numerator":9,"denominator":12,"percentage":75}},
          "paid":{"cohort":{"numerator":8,"denominator":20,"percentage":40},"previous_stage":{"numerator":8,"denominator":9,"percentage":88.89}}}},
      "requests":{"received":5,"accepted":3,"pending":2,"acceptance_rate":{"numerator":3,"denominator":5,"percentage":60}}
    }"""
    private val emptyPayload = """{
      "period":{"from":"2026-09-04T12:00:00Z","to":"2026-10-04T12:00:00Z","time_zone":"America/Argentina/Buenos_Aires"},
      "observed_at":"2026-10-04T12:00:00.123456789Z",
      "proposals":{"stages":{"issued":0,"contracted":0,"reported":0,"paid":0},"uncontracted":0,
        "rates":{"contracted":{"cohort":{"numerator":0,"denominator":0,"percentage":null},"previous_stage":{"numerator":0,"denominator":0,"percentage":null}},
          "reported":{"cohort":{"numerator":0,"denominator":0,"percentage":null},"previous_stage":{"numerator":0,"denominator":0,"percentage":null}},
          "paid":{"cohort":{"numerator":0,"denominator":0,"percentage":null},"previous_stage":{"numerator":0,"denominator":0,"percentage":null}}}},
      "requests":{"received":5,"accepted":3,"pending":2,"acceptance_rate":{"numerator":3,"denominator":5,"percentage":60}}
    }"""

}
