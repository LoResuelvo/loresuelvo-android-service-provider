package com.loresuelvo.serviceprovider.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.loresuelvo.serviceprovider.domain.coverage.CoverageZonesOutcome
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class ApiCoverageZoneRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: ApiCoverageZoneRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(
                Json { ignoreUnknownKeys = true }
                    .asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(BackendApi::class.java)
        repository = ApiCoverageZoneRepository(api)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `returns zones in server order with boundary place ids`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[
                    {"id":14,"name":"Comuna 14","boundary":{"type":"google_place","place_id":"place-14"}},
                    {"id":6,"name":"Comuna 6","boundary":{"type":"google_place","place_id":"place-6"}}
                ]""".trimIndent(),
            ),
        )

        val outcome = repository.getCoverageZones() as CoverageZonesOutcome.Success

        assertEquals(listOf(14, 6), outcome.zones.map { it.id })
        assertEquals(listOf("place-14", "place-6"), outcome.zones.map { it.boundaryPlaceId })
        assertEquals("/coverage-zones", server.takeRequest().path)
    }

    @Test
    fun `returns empty success for an empty catalog`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))

        val outcome = repository.getCoverageZones() as CoverageZonesOutcome.Success

        assertTrue(outcome.zones.isEmpty())
    }

    @Test
    fun `maps unauthorized response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid_token"}"""))

        assertEquals(CoverageZonesOutcome.Failure.Unauthorized, repository.getCoverageZones())
    }

    @Test
    fun `maps server response without exposing its body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"secret payload"}"""))

        assertEquals(CoverageZonesOutcome.Failure.Server(500), repository.getCoverageZones())
    }

    @Test
    fun `maps transport failure and preserves cancellation`() = runTest {
        val networkRepository = ApiCoverageZoneRepository(
            backendApi = object : BackendApi by repositoryBackendApi() {
                override suspend fun getCoverageZones() = throw IOException("offline")
            },
        )
        val cancellationRepository = ApiCoverageZoneRepository(
            backendApi = object : BackendApi by repositoryBackendApi() {
                override suspend fun getCoverageZones() = throw CancellationException("cancelled")
            },
        )

        assertTrue(networkRepository.getCoverageZones() is CoverageZonesOutcome.Failure.Network)
        try {
            cancellationRepository.getCoverageZones()
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            // Expected.
        }
    }

    private fun repositoryBackendApi(): BackendApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(
            Json { ignoreUnknownKeys = true }
                .asConverterFactory("application/json".toMediaType()),
        )
        .build()
        .create(BackendApi::class.java)
}
