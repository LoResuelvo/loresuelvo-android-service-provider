package com.loresuelvo.serviceprovider.domain.usecase.provider

import com.loresuelvo.serviceprovider.domain.provider.ProviderRegistrationCommand
import com.loresuelvo.serviceprovider.domain.provider.ProviderRepository
import com.loresuelvo.serviceprovider.domain.provider.RegistrationOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RegisterProviderUseCaseTest {

    private val repository = mockk<ProviderRepository>()
    private val useCase = RegisterProviderUseCase(repository)

    private val sampleCommand = ProviderRegistrationCommand(
        email = "provider@example.com",
        name = "Juan",
        surname = "Pérez",
        categoryId = 3,
    )

    @Test
    fun `delegates command to repository and returns success outcome`() = runTest {
        val expected = RegistrationOutcome.Success(providerId = 42)
        coEvery { repository.register(sampleCommand) } returns expected

        val result = useCase(sampleCommand)

        assertEquals(expected, result)
        coVerify(exactly = 1) { repository.register(sampleCommand) }
    }

    @Test
    fun `propagates already registered failure`() = runTest {
        coEvery { repository.register(sampleCommand) } returns RegistrationOutcome.Failure.AlreadyRegistered

        val result = useCase(sampleCommand)

        assertEquals(RegistrationOutcome.Failure.AlreadyRegistered, result)
    }

    @Test
    fun `propagates server failure`() = runTest {
        val expected = RegistrationOutcome.Failure.Server(500, "Internal Server Error")
        coEvery { repository.register(sampleCommand) } returns expected

        val result = useCase(sampleCommand)

        assertEquals(expected, result)
    }
}
