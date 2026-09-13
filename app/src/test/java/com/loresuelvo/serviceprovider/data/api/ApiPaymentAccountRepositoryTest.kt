package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.PaymentAccountAuthorizationDto
import com.loresuelvo.serviceprovider.data.api.dto.PaymentAccountStatusDto
import com.loresuelvo.serviceprovider.domain.paymentaccount.ConnectionStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountAuthorizationOutcome
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatusOutcome
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

class ApiPaymentAccountRepositoryTest {

    private val backendApi = mockk<BackendApi>()
    private val repository = ApiPaymentAccountRepository(backendApi)

    @Test
    fun getStatus_returns_success_when_backend_returns_status_dto() = runTest {
        val dto = PaymentAccountStatusDto(
            status = "connected",
            accountId = "acc_123",
            canReceivePayments = true,
            canSendServiceProposals = true,
        )
        coEvery { backendApi.getPaymentAccountStatus() } returns dto

        val result = repository.getStatus()

        assertTrue(result is PaymentAccountStatusOutcome.Success)
        val success = result as PaymentAccountStatusOutcome.Success
        assertEquals(ConnectionStatus.CONNECTED, success.status.status)
        assertEquals("acc_123", success.status.accountId)
        assertEquals(true, success.status.canReceivePayments)
        assertEquals(true, success.status.canSendServiceProposals)
    }

    @Test
    fun getStatus_returns_unauthorized_when_backend_throws_401() = runTest {
        val response = Response.error<PaymentAccountStatusDto>(
            401,
            "{\"error\":\"unauthorized\"}".toResponseBody("application/json".toMediaType()),
        )
        coEvery { backendApi.getPaymentAccountStatus() } throws HttpException(response)

        val result = repository.getStatus()

        assertEquals(PaymentAccountStatusOutcome.Failure.Unauthorized, result)
    }

    @Test
    fun getStatus_returns_forbidden_when_backend_throws_403() = runTest {
        val response = Response.error<PaymentAccountStatusDto>(
            403,
            "{\"error\":\"forbidden\"}".toResponseBody("application/json".toMediaType()),
        )
        coEvery { backendApi.getPaymentAccountStatus() } throws HttpException(response)

        val result = repository.getStatus()

        assertEquals(PaymentAccountStatusOutcome.Failure.Forbidden, result)
    }

    @Test
    fun getStatus_returns_server_error_when_backend_throws_500() = runTest {
        val response = Response.error<PaymentAccountStatusDto>(
            500,
            "{\"error\":\"server_error\"}".toResponseBody("application/json".toMediaType()),
        )
        coEvery { backendApi.getPaymentAccountStatus() } throws HttpException(response)

        val result = repository.getStatus()

        assertTrue(result is PaymentAccountStatusOutcome.Failure.Server)
        assertEquals(500, (result as PaymentAccountStatusOutcome.Failure.Server).code)
    }

    @Test
    fun getStatus_returns_network_failure_when_ioexception_thrown() = runTest {
        coEvery { backendApi.getPaymentAccountStatus() } throws IOException("Connection reset")

        val result = repository.getStatus()

        assertTrue(result is PaymentAccountStatusOutcome.Failure.Network)
    }

    @Test
    fun getStatus_propagates_cancellation() = runTest {
        coEvery { backendApi.getPaymentAccountStatus() } throws CancellationException("Cancelled")

        assertFailsWith<CancellationException> {
            repository.getStatus()
        }
    }

    @Test
    fun requestAuthorization_returns_success_when_backend_returns_dto() = runTest {
        val dto = PaymentAccountAuthorizationDto(
            authorizationUrl = "https://auth.mercadopago.com/authorization?client_id=123",
            state = "opaque-state",
        )
        coEvery { backendApi.requestPaymentAccountAuthorization() } returns dto

        val result = repository.requestAuthorization()

        assertTrue(result is PaymentAccountAuthorizationOutcome.Success)
        val success = result as PaymentAccountAuthorizationOutcome.Success
        assertEquals("https://auth.mercadopago.com/authorization?client_id=123", success.authorizationUrl)
    }

    @Test
    fun requestAuthorization_returns_unauthorized_when_backend_throws_401() = runTest {
        val response = Response.error<PaymentAccountAuthorizationDto>(
            401,
            "{\"error\":\"unauthorized\"}".toResponseBody("application/json".toMediaType()),
        )
        coEvery { backendApi.requestPaymentAccountAuthorization() } throws HttpException(response)

        val result = repository.requestAuthorization()

        assertEquals(PaymentAccountAuthorizationOutcome.Failure.Unauthorized, result)
    }

    @Test
    fun requestAuthorization_returns_forbidden_when_backend_throws_403() = runTest {
        val response = Response.error<PaymentAccountAuthorizationDto>(
            403,
            "{\"error\":\"forbidden\"}".toResponseBody("application/json".toMediaType()),
        )
        coEvery { backendApi.requestPaymentAccountAuthorization() } throws HttpException(response)

        val result = repository.requestAuthorization()

        assertEquals(PaymentAccountAuthorizationOutcome.Failure.Forbidden, result)
    }

    @Test
    fun requestAuthorization_returns_server_error_when_backend_throws_500() = runTest {
        val response = Response.error<PaymentAccountAuthorizationDto>(
            500,
            "{\"error\":\"server_error\"}".toResponseBody("application/json".toMediaType()),
        )
        coEvery { backendApi.requestPaymentAccountAuthorization() } throws HttpException(response)

        val result = repository.requestAuthorization()

        assertTrue(result is PaymentAccountAuthorizationOutcome.Failure.Server)
        assertEquals(500, (result as PaymentAccountAuthorizationOutcome.Failure.Server).code)
    }

    @Test
    fun requestAuthorization_returns_network_failure_when_ioexception_thrown() = runTest {
        coEvery { backendApi.requestPaymentAccountAuthorization() } throws IOException("Connection reset")

        val result = repository.requestAuthorization()

        assertTrue(result is PaymentAccountAuthorizationOutcome.Failure.Network)
    }

    @Test
    fun requestAuthorization_propagates_cancellation() = runTest {
        coEvery { backendApi.requestPaymentAccountAuthorization() } throws CancellationException("Cancelled")

        assertFailsWith<CancellationException> {
            repository.requestAuthorization()
        }
    }
}
