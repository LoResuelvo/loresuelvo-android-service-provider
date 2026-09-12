package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.RegisterProviderRequestDto
import com.loresuelvo.serviceprovider.domain.api.ApiError
import com.loresuelvo.serviceprovider.domain.provider.ProviderRegistrationCommand
import com.loresuelvo.serviceprovider.domain.provider.ProviderRepository
import com.loresuelvo.serviceprovider.domain.provider.RegistrationOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Implementation of [ProviderRepository] port backed by [BackendApi].
 */
@Singleton
class ApiProviderRepository @Inject constructor(
    private val backendApi: BackendApi,
) : ProviderRepository {

    override suspend fun register(command: ProviderRegistrationCommand): RegistrationOutcome =
        try {
            val requestDto = RegisterProviderRequestDto(
                email = command.email,
                name = command.name,
                surname = command.surname,
                categoryId = command.categoryId,
                coverageZoneIds = command.coverageZoneIds,
                profilePhotoFileId = command.profilePhotoFileId,
            )
            val response = backendApi.registerProvider(requestDto)
            RegistrationOutcome.Success(providerId = response.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            mapToFailure(e)
        }

    private fun mapToFailure(e: Throwable): RegistrationOutcome.Failure =
        when (val error = e.toApiError()) {
            is ApiError.Network ->
                RegistrationOutcome.Failure.Network(error.networkCause)
            is ApiError.Unauthorized ->
                RegistrationOutcome.Failure.Unauthorized
            is ApiError.Server ->
                if (error.code == 409) {
                    RegistrationOutcome.Failure.AlreadyRegistered
                } else {
                    RegistrationOutcome.Failure.Server(error.code, error.errorMessage)
                }
            is ApiError.Unknown ->
                RegistrationOutcome.Failure.Server(0, error.message ?: "Unknown error")
        }
}
