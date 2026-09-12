package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format for the response returned by GET /providers/{providerID}.
 */
@Serializable
data class ProviderProfileDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("surname") val surname: String,
    @SerialName("profile_photo") val profilePhoto: ProviderProfilePhotoDto? = null,
)
