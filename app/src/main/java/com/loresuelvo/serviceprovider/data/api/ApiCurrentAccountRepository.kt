package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.mapper.toDomain
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.api.ApiError
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class ApiCurrentAccountRepository @Inject constructor(
    private val backendApi: BackendApi,
) : CurrentAccountRepository {

    override suspend fun getCurrentAccount(): CurrentAccountOutcome = try {
        CurrentAccountOutcome.Success(backendApi.getCurrentAccount().toDomain())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        when (val error = e.toApiError()) {
            is ApiError.Network -> CurrentAccountOutcome.Failure.Network(error.networkCause)
            is ApiError.Unauthorized -> CurrentAccountOutcome.Failure.Unauthorized
            is ApiError.Server -> if (error.code == 404) {
                CurrentAccountOutcome.Failure.NotFound
            } else {
                CurrentAccountOutcome.Failure.Server(error.code)
            }
            is ApiError.Unknown -> CurrentAccountOutcome.Failure.Invalid
        }
    }
}
