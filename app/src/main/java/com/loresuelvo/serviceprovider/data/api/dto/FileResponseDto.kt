package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileResponseDto(
    @SerialName("id") val id: String,
    @SerialName("url") val url: String? = null,
    @SerialName("original_name") val originalName: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("type") val type: String = "image",
)
