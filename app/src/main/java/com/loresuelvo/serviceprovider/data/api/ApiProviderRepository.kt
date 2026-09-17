package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.RegisterProviderRequestDto
import com.loresuelvo.serviceprovider.domain.api.ApiError
import com.loresuelvo.serviceprovider.domain.provider.GetProviderProfileOutcome
import com.loresuelvo.serviceprovider.domain.provider.CoverageRejectionReason
import com.loresuelvo.serviceprovider.domain.provider.ProviderProfile
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

    override suspend fun getProfile(providerId: Int): GetProviderProfileOutcome =
        try {
            val response = backendApi.getProviderProfile(providerId)
            GetProviderProfileOutcome.Success(
                profile = ProviderProfile(
                    id = response.id,
                    name = response.name,
                    surname = response.surname,
                    profilePhotoUrl = response.profilePhoto?.url,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            mapToGetProfileFailure(e)
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
                    mapServerFailure(error.code, error.errorMessage)
                }
            is ApiError.Unknown ->
                RegistrationOutcome.Failure.Server(0, error.message ?: "Unknown error")
        }

    private fun mapServerFailure(code: Int, message: String): RegistrationOutcome.Failure {
        if (code != 400) return RegistrationOutcome.Failure.Server(code, message)
        val reason = when (message) {
            "At least one coverage zone must be selected" -> CoverageRejectionReason.Missing
            "Coverage zone does not exist" -> CoverageRejectionReason.NotFound
            "Coverage zone is not available" -> CoverageRejectionReason.Unavailable
            "Coverage zone cannot be selected more than once" -> CoverageRejectionReason.Duplicate
            else -> return RegistrationOutcome.Failure.Server(code, message)
        }
        return RegistrationOutcome.Failure.CoverageRejected(reason)
    }

    private fun mapToGetProfileFailure(e: Throwable): GetProviderProfileOutcome.Failure =
        when (val error = e.toApiError()) {
            is ApiError.Network ->
                GetProviderProfileOutcome.Failure.Network(error.networkCause)
            is ApiError.Unauthorized ->
                GetProviderProfileOutcome.Failure.Unauthorized
            is ApiError.Server ->
                if (error.code == 404) {
                    GetProviderProfileOutcome.Failure.NotFound
                } else {
                    GetProviderProfileOutcome.Failure.Server(error.code, error.errorMessage)
                }
            is ApiError.Unknown ->
                GetProviderProfileOutcome.Failure.Server(0, error.message ?: "Unknown error")
        }
}
