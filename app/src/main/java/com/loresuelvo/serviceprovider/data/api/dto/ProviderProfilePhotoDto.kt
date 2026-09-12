package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format for the profile photo nested in GET /providers/{providerID}.
 */
@Serializable
data class ProviderProfilePhotoDto(
    @SerialName("original_name") val originalName: String,
    @SerialName("url") val url: String,
)
