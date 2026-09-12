package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format for POST /providers.
 */
@Serializable
data class RegisterProviderRequestDto(
    @SerialName("email") val email: String,
    @SerialName("name") val name: String,
    @SerialName("surname") val surname: String,
    @SerialName("category_id") val categoryId: Int,
    @SerialName("coverage_zone_ids") val coverageZoneIds: List<Int> = emptyList(),
    @SerialName("profile_photo_file_id") val profilePhotoFileId: String = "",
)
