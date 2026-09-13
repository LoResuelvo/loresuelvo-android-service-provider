package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.mapper.toDomain
import com.loresuelvo.serviceprovider.domain.api.ApiError
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountRepository
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatusOutcome
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiPaymentAccountRepository @Inject constructor(
    private val backendApi: BackendApi,
) : PaymentAccountRepository {

    override suspend fun getStatus(): PaymentAccountStatusOutcome =
        try {
            PaymentAccountStatusOutcome.Success(backendApi.getPaymentAccountStatus().toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> PaymentAccountStatusOutcome.Failure.Unauthorized
                403 -> PaymentAccountStatusOutcome.Failure.Forbidden
                else -> PaymentAccountStatusOutcome.Failure.Server(e.code(), e.message())
            }
        } catch (e: Throwable) {
            when (val error = e.toApiError()) {
                is ApiError.Network -> PaymentAccountStatusOutcome.Failure.Network(error.networkCause)
                is ApiError.Unauthorized -> PaymentAccountStatusOutcome.Failure.Unauthorized
                is ApiError.Server -> {
                    if (error.code == 403) PaymentAccountStatusOutcome.Failure.Forbidden
                    else PaymentAccountStatusOutcome.Failure.Server(error.code, error.errorMessage)
                }
                is ApiError.Unknown -> PaymentAccountStatusOutcome.Failure.Unknown(e)
            }
        }
}
