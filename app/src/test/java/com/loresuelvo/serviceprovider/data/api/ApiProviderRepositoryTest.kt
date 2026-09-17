package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.ProviderSummaryDto
import com.loresuelvo.serviceprovider.data.api.dto.RegisterProviderRequestDto
import com.loresuelvo.serviceprovider.domain.provider.CoverageRejectionReason
import com.loresuelvo.serviceprovider.domain.provider.ProviderRegistrationCommand
import com.loresuelvo.serviceprovider.domain.provider.RegistrationOutcome
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import kotlin.test.assertFailsWith

class ApiProviderRepositoryTest {

    private val backendApi = mockk<BackendApi>()
    private val repository = ApiProviderRepository(backendApi)

    private val sampleCommand = ProviderRegistrationCommand(
        email = "provider@example.com",
        name = "Carlos",
        surname = "García",
        categoryId = 4,
    )

    private val expectedDto = RegisterProviderRequestDto(
        email = "provider@example.com",
        name = "Carlos",
        surname = "García",
        categoryId = 4,
    )

    @Test
    fun `register returns Success when backend returns 201 ProviderSummaryDto`() = runTest {
        val summary = ProviderSummaryDto(
            id = 42,
            name = "Carlos",
            surname = "García",
            categoryName = "Plomería",
        )
        coEvery { backendApi.registerProvider(expectedDto) } returns summary

        val result = repository.register(sampleCommand)

        assertEquals(RegistrationOutcome.Success(providerId = 42), result)
    }

    @Test
    fun `register returns Unauthorized when backend throws 401 HttpException`() = runTest {
        val response = Response.error<ProviderSummaryDto>(
            401,
            "{\"error\":\"unauthorized\",\"message\":\"Token expired\"}".toResponseBody("application/json".toMediaType()),
        )
        coEvery { backendApi.registerProvider(expectedDto) } throws HttpException(response)

        val result = repository.register(sampleCommand)

        assertEquals(RegistrationOutcome.Failure.Unauthorized, result)
    }

    @Test
    fun `register returns AlreadyRegistered when backend throws 409 HttpException`() = runTest {
        val response = Response.error<ProviderSummaryDto>(
            409,
            "{\"error\":\"conflict\",\"message\":\"Email already registered\"}".toResponseBody("application/json".toMediaType()),
        )
        coEvery { backendApi.registerProvider(expectedDto) } throws HttpException(response)

        val result = repository.register(sampleCommand)

        assertEquals(RegistrationOutcome.Failure.AlreadyRegistered, result)
    }

    @Test
    fun `register returns Server failure with message when backend throws 400 HttpException`() = runTest {
        val response = Response.error<ProviderSummaryDto>(
            400,
            "{\"error\":\"validation_error\",\"message\":\"Nombre inválido\"}".toResponseBody("application/json".toMediaType()),
        )
        coEvery { backendApi.registerProvider(expectedDto) } throws HttpException(response)

        val result = repository.register(sampleCommand)

        assertTrue(result is RegistrationOutcome.Failure.Server)
        val serverFailure = result as RegistrationOutcome.Failure.Server
        assertEquals(400, serverFailure.code)
        assertEquals("Nombre inválido", serverFailure.message)
    }

    @Test
    fun `register maps exact coverage validation messages`() = runTest {
        val cases = mapOf(
            "At least one coverage zone must be selected" to CoverageRejectionReason.Missing,
            "Coverage zone does not exist" to CoverageRejectionReason.NotFound,
            "Coverage zone is not available" to CoverageRejectionReason.Unavailable,
            "Coverage zone cannot be selected more than once" to CoverageRejectionReason.Duplicate,
        )

        cases.forEach { (message, reason) ->
            val response = Response.error<ProviderSummaryDto>(
                400,
                "{\"error\":\"$message\"}".toResponseBody("application/json".toMediaType()),
            )
            coEvery { backendApi.registerProvider(expectedDto) } throws HttpException(response)

            assertEquals(
                RegistrationOutcome.Failure.CoverageRejected(reason),
                repository.register(sampleCommand),
            )
        }
    }

    @Test
    fun `register returns Network failure when backend throws IOException`() = runTest {
        val ioException = IOException("Connection reset")
        coEvery { backendApi.registerProvider(expectedDto) } throws ioException

        val result = repository.register(sampleCommand)

        assertTrue(result is RegistrationOutcome.Failure.Network)
        assertEquals(ioException, (result as RegistrationOutcome.Failure.Network).cause)
    }

    @Test
    fun `register propagates cancellation thrown by backend request`() = runTest {
        coEvery { backendApi.registerProvider(expectedDto) } throws CancellationException("Request cancelled")

        assertFailsWith<CancellationException> { repository.register(sampleCommand) }
    }

    @Test
    fun `getProfile returns Success when backend returns 200 ProviderProfileDto`() = runTest {
        val dto = com.loresuelvo.serviceprovider.data.api.dto.ProviderProfileDto(
            id = 42,
            name = "Carlos",
            surname = "Gómez",
            profilePhoto = com.loresuelvo.serviceprovider.data.api.dto.ProviderProfilePhotoDto(
                originalName = "carlos.jpg",
                url = "https://cdn.example/carlos.jpg",
            ),
        )
        coEvery { backendApi.getProviderProfile(42) } returns dto

        val result = repository.getProfile(42)

        assertTrue(result is com.loresuelvo.serviceprovider.domain.provider.GetProviderProfileOutcome.Success)
        val success = result as com.loresuelvo.serviceprovider.domain.provider.GetProviderProfileOutcome.Success
        assertEquals(42, success.profile.id)
        assertEquals("Carlos", success.profile.name)
        assertEquals("Gómez", success.profile.surname)
        assertEquals("https://cdn.example/carlos.jpg", success.profile.profilePhotoUrl)
    }

    @Test
    fun `getProfile returns NotFound when backend throws 404 HttpException`() = runTest {
        val response = Response.error<com.loresuelvo.serviceprovider.data.api.dto.ProviderProfileDto>(
            404,
            "{\"error\":\"not_found\",\"message\":\"Provider not found\"}".toResponseBody("application/json".toMediaType()),
        )
        coEvery { backendApi.getProviderProfile(42) } throws HttpException(response)

        val result = repository.getProfile(42)

        assertEquals(com.loresuelvo.serviceprovider.domain.provider.GetProviderProfileOutcome.Failure.NotFound, result)
    }

    @Test
    fun `getProfile returns Unauthorized when backend throws 401 HttpException`() = runTest {
        val response = Response.error<com.loresuelvo.serviceprovider.data.api.dto.ProviderProfileDto>(
            401,
            "{\"error\":\"unauthorized\",\"message\":\"Token expired\"}".toResponseBody("application/json".toMediaType()),
        )
        coEvery { backendApi.getProviderProfile(42) } throws HttpException(response)

        val result = repository.getProfile(42)

        assertEquals(com.loresuelvo.serviceprovider.domain.provider.GetProviderProfileOutcome.Failure.Unauthorized, result)
    }

    @Test
    fun `getProfile returns Network when backend throws IOException`() = runTest {
        val ioException = IOException("Network unreachable")
        coEvery { backendApi.getProviderProfile(42) } throws ioException

        val result = repository.getProfile(42)

        assertTrue(result is com.loresuelvo.serviceprovider.domain.provider.GetProviderProfileOutcome.Failure.Network)
        assertEquals(ioException, (result as com.loresuelvo.serviceprovider.domain.provider.GetProviderProfileOutcome.Failure.Network).cause)
    }

    @Test
    fun `getProfile propagates cancellation thrown by backend request`() = runTest {
        coEvery { backendApi.getProviderProfile(42) } throws CancellationException("Request cancelled")

        assertFailsWith<CancellationException> { repository.getProfile(42) }
    }
}
