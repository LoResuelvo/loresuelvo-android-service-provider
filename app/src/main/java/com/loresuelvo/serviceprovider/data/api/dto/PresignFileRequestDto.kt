package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PresignFileRequestDto(
    @SerialName("original_name") val originalName: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("purpose") val purpose: String,
)
