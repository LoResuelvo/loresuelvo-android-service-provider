package com.loresuelvo.serviceprovider.domain.coverage

data class CoverageZone(
    val id: Int,
    val name: String,
    val boundaryPlaceId: String,
)

sealed interface CoverageZonesOutcome {
    data class Success(val zones: List<CoverageZone>) : CoverageZonesOutcome

    sealed interface Failure : CoverageZonesOutcome {
        data class Network(val cause: Throwable) : Failure
        data object Unauthorized : Failure
        data class Server(val code: Int) : Failure
    }
}

interface CoverageZoneRepository {
    suspend fun getCoverageZones(): CoverageZonesOutcome
}
