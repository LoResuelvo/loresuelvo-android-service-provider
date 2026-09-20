package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.IdentityVerificationSessionDto
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.identity.StartIdentityVerificationOutcome
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import kotlin.test.assertFailsWith

class ApiIdentityVerificationRepositoryTest {
    private val api = mockk<BackendApi>()
    private val sessionStore = mockk<AuthSessionStore>(relaxed = true)
    private val repository = ApiIdentityVerificationRepository(api, sessionStore)

    @Test
    fun `valid reusable session returns only the ephemeral credential`() = runTest {
        coEvery { api.startIdentityVerification() } returns validDto(status = "in_progress")

        val result = repository.start()

        assertTrue(result is StartIdentityVerificationOutcome.Success)
        assertEquals("temporary-token", (result as StartIdentityVerificationOutcome.Success).credential.token)
    }

    @Test
    fun `invalid response never returns a credential`() = runTest {
        coEvery { api.startIdentityVerification() } returns validDto(sessionToken = " ")

        assertEquals(StartIdentityVerificationOutcome.Failure.InvalidResponse, repository.start())
    }

    @Test
    fun `unauthorized clears the shared session`() = runTest {
        coEvery { api.startIdentityVerification() } throws httpError(401)

        assertEquals(StartIdentityVerificationOutcome.Failure.Unauthorized, repository.start())
        verify(exactly = 1) { sessionStore.clearSession() }
    }

    @Test
    fun `conflict is authoritative already approved`() = runTest {
        coEvery { api.startIdentityVerification() } throws httpError(409)

        assertEquals(StartIdentityVerificationOutcome.AlreadyApproved, repository.start())
    }

    @Test
    fun `transport and server failures remain typed`() = runTest {
        coEvery { api.startIdentityVerification() } throws IOException("offline")
        assertEquals(StartIdentityVerificationOutcome.Failure.Network, repository.start())

        coEvery { api.startIdentityVerification() } throws httpError(503)
        assertEquals(StartIdentityVerificationOutcome.Failure.Server(503), repository.start())

        coEvery { api.startIdentityVerification() } throws httpError(403)
        assertEquals(StartIdentityVerificationOutcome.Failure.Forbidden, repository.start())
    }

    @Test
    fun `cancellation propagates`() = runTest {
        coEvery { api.startIdentityVerification() } throws CancellationException("cancelled")

        assertFailsWith<CancellationException> { repository.start() }
    }

    private fun validDto(
        sessionToken: String = "temporary-token",
        status: String = "not_started",
    ) = IdentityVerificationSessionDto(
        sessionId = "9df42484-61d1-4b40-b195-1e2d5e10298f",
        sessionToken = sessionToken,
        verificationUrl = "https://verify.didit.me/session",
        status = status,
    )

    private fun httpError(code: Int) = HttpException(
        Response.error<IdentityVerificationSessionDto>(
            code,
            "{}".toResponseBody("application/json".toMediaType()),
        ),
    )
}
