package com.loresuelvo.serviceprovider.testing

import com.loresuelvo.serviceprovider.domain.coverage.CoverageZone
import com.loresuelvo.serviceprovider.domain.coverage.CoverageZoneRepository
import com.loresuelvo.serviceprovider.domain.coverage.CoverageZonesOutcome

class FakeCoverageZoneRepository : CoverageZoneRepository {
    var outcome: CoverageZonesOutcome = CoverageZonesOutcome.Success(
        listOf(CoverageZone(id = 1, name = "Comuna 1", boundaryPlaceId = "place-1")),
    )
    var calls = 0
        private set
    var beforeReturn: suspend () -> Unit = {}

    override suspend fun getCoverageZones(): CoverageZonesOutcome {
        calls += 1
        beforeReturn()
        return outcome
    }
}
