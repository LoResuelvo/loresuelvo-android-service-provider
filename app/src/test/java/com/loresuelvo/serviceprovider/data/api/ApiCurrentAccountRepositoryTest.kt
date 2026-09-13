package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.CategoryDto
import com.loresuelvo.serviceprovider.data.api.dto.CurrentAccountDto
import com.loresuelvo.serviceprovider.data.api.dto.CurrentAccountProfilePhotoDto
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
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

class ApiCurrentAccountRepositoryTest {

    private val backendApi = mockk<BackendApi>()
    private val repository = ApiCurrentAccountRepository(backendApi)

    @Test
    fun maps_provider_identity_category_and_photo() = runTest {
        coEvery { backendApi.getCurrentAccount() } returns providerDto()

        val result = repository.getCurrentAccount()

        assertTrue(result is CurrentAccountOutcome.Success)
        val provider = (result as CurrentAccountOutcome.Success).account as CurrentAccount.Provider
        assertEquals("Juan Gómez", "${provider.name} ${provider.surname}")
        assertEquals(CategoryDto(1, "Plomería"), CategoryDto(provider.category.id, provider.category.name))
        assertEquals("https://cdn.example/profile.jpg", provider.profilePhotoUrl)
    }

    @Test
    fun maps_consumer_role_without_provider_fields() = runTest {
        coEvery { backendApi.getCurrentAccount() } returns providerDto().copy(
            role = "consumer",
            category = null,
            profilePhoto = null,
        )

        val result = repository.getCurrentAccount()

        assertEquals(
            CurrentAccount.Consumer,
            (result as CurrentAccountOutcome.Success).account,
        )
    }

    @Test
    fun maps_404_to_not_found() = runTest {
        coEvery { backendApi.getCurrentAccount() } throws httpException(404)

        assertEquals(CurrentAccountOutcome.Failure.NotFound, repository.getCurrentAccount())
    }

    @Test
    fun maps_401_to_unauthorized() = runTest {
        coEvery { backendApi.getCurrentAccount() } throws httpException(401)

        assertEquals(CurrentAccountOutcome.Failure.Unauthorized, repository.getCurrentAccount())
    }

    @Test
    fun maps_5xx_to_server_failure_without_exposing_body() = runTest {
        coEvery { backendApi.getCurrentAccount() } throws httpException(500)

        assertEquals(CurrentAccountOutcome.Failure.Server(500), repository.getCurrentAccount())
    }

    @Test
    fun maps_network_failure() = runTest {
        val cause = IOException("offline")
        coEvery { backendApi.getCurrentAccount() } throws cause

        assertEquals(CurrentAccountOutcome.Failure.Network(cause), repository.getCurrentAccount())
    }

    @Test
    fun propagates_cancellation() = runTest {
        coEvery { backendApi.getCurrentAccount() } throws CancellationException("cancelled")

        assertFailsWith<CancellationException> { repository.getCurrentAccount() }
    }

    @Test
    fun maps_provider_without_category_to_invalid() = runTest {
        coEvery { backendApi.getCurrentAccount() } returns providerDto().copy(category = null)

        assertEquals(CurrentAccountOutcome.Failure.Invalid, repository.getCurrentAccount())
    }

    private fun providerDto() = CurrentAccountDto(
        id = 20,
        name = "Juan",
        surname = "Gómez",
        email = "juan@example.com",
        role = "provider",
        profilePhoto = CurrentAccountProfilePhotoDto("profile.jpg", "https://cdn.example/profile.jpg"),
        category = CategoryDto(1, "Plomería"),
    )

    private fun httpException(code: Int): HttpException = HttpException(
        Response.error<CurrentAccountDto>(
            code,
            "{\"error\":\"synthetic\"}".toResponseBody("application/json".toMediaType()),
        ),
    )
}
