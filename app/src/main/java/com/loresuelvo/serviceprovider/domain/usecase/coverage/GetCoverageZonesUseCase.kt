package com.loresuelvo.serviceprovider.domain.usecase.coverage

import com.loresuelvo.serviceprovider.domain.coverage.CoverageZoneRepository
import com.loresuelvo.serviceprovider.domain.coverage.CoverageZonesOutcome
import javax.inject.Inject

class GetCoverageZonesUseCase @Inject constructor(
    private val repository: CoverageZoneRepository,
) {
    suspend operator fun invoke(): CoverageZonesOutcome = repository.getCoverageZones()
}
