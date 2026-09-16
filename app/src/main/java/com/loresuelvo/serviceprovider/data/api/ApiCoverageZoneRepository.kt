package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.mapper.toCoverageZones
import com.loresuelvo.serviceprovider.domain.api.ApiError
import com.loresuelvo.serviceprovider.domain.coverage.CoverageZoneRepository
import com.loresuelvo.serviceprovider.domain.coverage.CoverageZonesOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class ApiCoverageZoneRepository @Inject constructor(
    private val backendApi: BackendApi,
) : CoverageZoneRepository {
    override suspend fun getCoverageZones(): CoverageZonesOutcome =
        try {
            CoverageZonesOutcome.Success(backendApi.getCoverageZones().toCoverageZones())
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            when (val error = exception.toApiError()) {
                is ApiError.Network -> CoverageZonesOutcome.Failure.Network(error.networkCause)
                is ApiError.Unauthorized -> CoverageZonesOutcome.Failure.Unauthorized
                is ApiError.Server -> CoverageZonesOutcome.Failure.Server(error.code)
                is ApiError.Unknown -> CoverageZonesOutcome.Failure.Server(0)
            }
        }
}
