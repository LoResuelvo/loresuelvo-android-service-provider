package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.CoverageZoneDto
import com.loresuelvo.serviceprovider.domain.coverage.CoverageZone

internal fun List<CoverageZoneDto>.toCoverageZones(): List<CoverageZone> = map { zone ->
    CoverageZone(
        id = zone.id,
        name = zone.name,
        boundaryPlaceId = zone.boundary.placeId,
    )
}
