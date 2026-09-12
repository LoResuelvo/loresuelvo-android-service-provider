package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format for the response returned by POST /providers.
 */
@Serializable
data class ProviderSummaryDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("surname") val surname: String,
    @SerialName("category_name") val categoryName: String,
    @SerialName("profile_photo_url") val profilePhotoUrl: String? = null,
)
