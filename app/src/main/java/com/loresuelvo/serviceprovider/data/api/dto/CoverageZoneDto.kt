package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CoverageZoneDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("boundary") val boundary: CoverageZoneBoundaryDto,
)

@Serializable
data class CoverageZoneBoundaryDto(
    @SerialName("type") val type: String,
    @SerialName("place_id") val placeId: String,
)
