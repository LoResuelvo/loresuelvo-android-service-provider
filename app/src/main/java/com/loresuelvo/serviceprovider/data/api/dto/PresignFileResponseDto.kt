package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PresignFileResponseDto(
    @SerialName("file_id") val fileId: String,
    @SerialName("key") val key: String,
    @SerialName("upload_url") val uploadUrl: String,
    @SerialName("headers") val headers: Map<String, String>,
)
